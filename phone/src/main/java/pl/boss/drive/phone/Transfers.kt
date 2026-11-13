package pl.boss.drive.phone

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Size
import org.json.JSONArray
import pl.boss.drive.core.net.Link
import pl.boss.drive.core.net.Packet
import pl.boss.drive.core.net.Proto
import pl.boss.drive.core.net.pkt
import pl.boss.drive.core.util.Img
import java.io.InputStream

object Artwork {
    fun of(ctx: Context, uri: Uri, px: Int): Bitmap? {
        if (Build.VERSION.SDK_INT >= 29) try { return ctx.contentResolver.loadThumbnail(uri, Size(px, px), null) } catch (_: Throwable) {}
        val r = MediaMetadataRetriever()
        return try { r.setDataSource(ctx, uri); r.embeddedPicture?.let { Img.decode(it, px) } } catch (_: Throwable) { null } finally { try { r.release() } catch (_: Throwable) {} }
    }
}

data class XItem(
    val id: String, val uri: String, val title: String, val artist: String, val album: String, val dur: Long,
    val size: Long, val ext: String, @Volatile var state: Int, @Volatile var sent: Long, @Volatile var err: String, val added: Long,
) {
    companion object { const val PENDING = 0; const val SENDING = 1; const val DONE = 2; const val FAILED = 3 }
}

data class RadioTrack(val id: Long, val xid: String, val title: String, val artist: String, val dur: Long, val size: Long)

/**
 * "Send to car" queue. Survives app restarts and lost connections: the head unit keeps a
 * .part file and tells us the offset to resume from, so a song half-sent at the petrol
 * station finishes when you're back in the car.
 */
class Transfers(private val ctx: Context) {
    private val h = CarLink.handler
    private val db = object : SQLiteOpenHelper(ctx, "transfers.db", null, 1) {
        override fun onCreate(d: SQLiteDatabase) {
            d.execSQL("CREATE TABLE x(id TEXT PRIMARY KEY, uri TEXT, title TEXT, artist TEXT, album TEXT, dur INTEGER, size INTEGER, ext TEXT," +
                " state INTEGER, sent INTEGER, err TEXT, added INTEGER)")
        }
        override fun onUpgrade(d: SQLiteDatabase, o: Int, n: Int) {}
    }
    private val items = ArrayList<XItem>()
    @Volatile var current: XItem? = null; private set
    private var retries = HashMap<String, Int>()
    @Volatile var radioTracks: List<RadioTrack> = emptyList(); private set
    @Volatile var radioUsed = 0L; private set
    @Volatile var radioFree = 0L; private set
    @Volatile var radioKnown = false; private set
    private var lastEmit = 0L
    @Volatile var startedAt = 0L; private set
    @Volatile var startedBytes = 0L; private set

    init {
        h.post {
            db.readableDatabase.rawQuery("SELECT id,uri,title,artist,album,dur,size,ext,state,sent,err,added FROM x ORDER BY added", null).use { c ->
                while (c.moveToNext()) {
                    val st = c.getInt(8).let { if (it == XItem.SENDING) XItem.PENDING else it }
                    items.add(XItem(c.getString(0), c.getString(1), c.getString(2) ?: "", c.getString(3) ?: "", c.getString(4) ?: "",
                        c.getLong(5), c.getLong(6), c.getString(7) ?: "mp3", st, c.getLong(9), c.getString(10) ?: "", c.getLong(11)))
                }
            }
            Events.emit(Ev.TRANSFERS)
        }
    }

    fun snapshot(): List<XItem> = synchronized(items) { items.toList() }
    fun pendingCount() = synchronized(items) { items.count { it.state == XItem.PENDING || it.state == XItem.SENDING } }
    fun isOnRadio(xid: String) = radioTracks.any { it.xid == xid }

    // ------------------------------------------------------------------ enqueue

    fun enqueueSongs(songs: List<Song>) = h.post {
        for (s in songs) add(XItem("ms${s.id}_${s.size}", s.uri.toString(), s.title, s.artist, s.album, s.dur, s.size, extFor(s.mime, s.title),
            XItem.PENDING, 0, "", System.currentTimeMillis()))
        Events.emit(Ev.TRANSFERS); kick()
    }

    fun enqueueUris(uris: List<Uri>, done: (Int) -> Unit) = h.post {
        var n = 0
        for (u in uris) describe(u)?.let { add(it); n++ }
        Events.emit(Ev.TRANSFERS); kick()
        android.os.Handler(android.os.Looper.getMainLooper()).post { done(n) }
    }

    private fun add(x: XItem) {
        synchronized(items) {
            val existing = items.firstOrNull { it.id == x.id }
            if (existing != null) { if (existing.state == XItem.FAILED) { existing.state = XItem.PENDING; existing.err = ""; save(existing) }; return }
            if (isOnRadio(x.id)) return
            items.add(x)
        }
        save(x)
    }

    private fun describe(u: Uri): XItem? {
        var name = ""; var size = 0L
        try {
            ctx.contentResolver.query(u, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
                if (c.moveToFirst()) { name = c.getString(0) ?: ""; size = c.getLong(1) }
            }
        } catch (_: Throwable) {}
        if (size <= 0) return null
        val r = MediaMetadataRetriever()
        var title = name.substringBeforeLast('.'); var artist = ""; var album = ""; var dur = 0L
        try {
            r.setDataSource(ctx, u)
            r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)?.let { title = it }
            artist = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST) ?: ""
            album = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM) ?: ""
            dur = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0
        } catch (_: Throwable) {} finally { try { r.release() } catch (_: Throwable) {} }
        val mime = ctx.contentResolver.getType(u) ?: ""
        val msId = if (u.authority == MediaStore.AUTHORITY) u.lastPathSegment?.toLongOrNull() else null
        val id = if (msId != null) "ms${msId}_$size" else "u" + (name + size).hashCode().toUInt().toString(16)
        val ext = name.substringAfterLast('.', "").lowercase().takeIf { it.length in 2..4 } ?: extFor(mime, name)
        return XItem(id, u.toString(), title, artist, album, dur, size, ext, XItem.PENDING, 0, "", System.currentTimeMillis())
    }

    private fun extFor(mime: String, name: String): String = when {
        mime.contains("flac") -> "flac"; mime.contains("mp4") || mime.contains("aac") || mime.contains("m4a") -> "m4a"
        mime.contains("ogg") -> "ogg"; mime.contains("opus") -> "opus"; mime.contains("wav") -> "wav"
        mime.contains("mpeg") || mime.contains("mp3") -> "mp3"
        else -> name.substringAfterLast('.', "mp3").lowercase().take(4)
    }

    private fun save(x: XItem) {
        db.writableDatabase.insertWithOnConflict("x", null, ContentValues().apply {
            put("id", x.id); put("uri", x.uri); put("title", x.title); put("artist", x.artist); put("album", x.album); put("dur", x.dur)
            put("size", x.size); put("ext", x.ext); put("state", x.state); put("sent", x.sent); put("err", x.err); put("added", x.added)
        }, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun remove(id: String) = h.post {
        if (current?.id == id) { CarLink.link?.setBulk(null); current = null }
        synchronized(items) { items.removeAll { it.id == id } }
        db.writableDatabase.delete("x", "id = ?", arrayOf(id))
        Events.emit(Ev.TRANSFERS); kick()
    }

    fun clearFinished() = h.post {
        synchronized(items) { items.removeAll { it.state == XItem.DONE } }
        db.writableDatabase.delete("x", "state = ?", arrayOf(XItem.DONE.toString()))
        Events.emit(Ev.TRANSFERS)
    }

    fun retryFailed() = h.post {
        synchronized(items) { items.filter { it.state == XItem.FAILED }.forEach { it.state = XItem.PENDING; it.err = ""; save(it) } }
        retries.clear(); Events.emit(Ev.TRANSFERS); kick()
    }

    // ------------------------------------------------------------------ sending

    fun kick() = h.post {
        if (current != null || !CarLink.connected) return@post
        val l = CarLink.link ?: return@post
        val next = synchronized(items) { items.firstOrNull { it.state == XItem.PENDING } } ?: return@post
        current = next
        next.state = XItem.SENDING
        Events.emit(Ev.TRANSFERS)
        val art = try { Artwork.of(ctx, Uri.parse(next.uri), 500)?.let { Img.jpeg(it, 500, 85) } } catch (_: Throwable) { null }
        l.send(pkt(Proto.X_OFFER, "id" to next.id, "size" to next.size, "title" to next.title, "artist" to next.artist,
            "album" to next.album, "dur" to next.dur, "ext" to next.ext, "queued" to (pendingCount() - 1), body = art))
    }

    fun onRadio(p: Packet) = h.post {
        val cur = current
        val id = p.s("id")
        when (p.type) {
            Proto.X_ACK -> if (cur != null && cur.id == id) startBulk(cur, p.l("off"))
            Proto.X_DONE -> {
                val x = cur?.takeIf { it.id == id } ?: synchronized(items) { items.firstOrNull { it.id == id } }
                x?.let { it.state = XItem.DONE; it.sent = it.size; save(it) }
                if (cur?.id == id) current = null
                Events.emit(Ev.TRANSFERS); kick()
            }
            Proto.X_FAIL -> {
                val x = cur?.takeIf { it.id == id }
                if (x != null) {
                    val n = (retries[id] ?: 0) + 1; retries[id] = n
                    if (p.b("retry") && n < 3) { x.state = XItem.PENDING } else { x.state = XItem.FAILED; x.err = p.s("reason", "Błąd") }
                    save(x); current = null
                    CarLink.link?.setBulk(null)
                }
                Events.emit(Ev.TRANSFERS)
                if (p.s("reason").contains("miejsca")) return@post   // radio is full: stop the queue
                kick()
            }
        }
    }

    private fun startBulk(x: XItem, offset: Long) {
        val l = CarLink.link ?: return
        val chunk = if (l.isWifi) Proto.CHUNK else 16 * 1024
        var stream: InputStream? = null
        try {
            stream = ctx.contentResolver.openInputStream(Uri.parse(x.uri)) ?: throw IllegalStateException("no stream")
            var skip = offset
            while (skip > 0) { val s = stream.skip(skip); if (s <= 0) break; skip -= s }
        } catch (e: Throwable) {
            stream?.close()
            x.state = XItem.FAILED; x.err = "Plik niedostępny"; save(x); current = null
            Events.emit(Ev.TRANSFERS); kick(); return
        }
        var off = offset
        var ended = false
        startedAt = SystemClock.elapsedRealtime(); startedBytes = offset
        val input = stream
        l.setBulk(Link.BulkSource {
            if (ended) return@BulkSource null
            if (current !== x) { input.close(); return@BulkSource null }
            val buf = ByteArray(chunk)
            var n = 0
            while (n < chunk) { val r = input.read(buf, n, chunk - n); if (r < 0) break; n += r }
            if (n <= 0) {
                input.close(); ended = true
                return@BulkSource pkt(Proto.X_END, "id" to x.id)
            }
            val body = if (n == chunk) buf else buf.copyOf(n)
            val p = pkt(Proto.X_CHUNK, "id" to x.id, "off" to off, body = body)
            off += n
            x.sent = off
            val now = SystemClock.elapsedRealtime()
            if (now - lastEmit > 300) { lastEmit = now; Events.emit(Ev.TRANSFERS) }
            p
        })
    }

    fun onLinkLost() {
        current?.let { it.state = XItem.PENDING; save(it) }
        current = null
        radioKnown = false
        Events.emit(Ev.TRANSFERS)
    }

    // ------------------------------------------------------------------ radio library

    fun onRadioLibrary(p: Packet) {
        val out = ArrayList<RadioTrack>()
        val arr = p.arr("items")
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            out.add(RadioTrack(o.optLong("id"), o.optString("xid"), o.optString("title"), o.optString("artist"), o.optLong("dur"), o.optLong("size")))
        }
        radioTracks = out; radioUsed = p.l("used"); radioFree = p.l("free"); radioKnown = true
        // anything the radio already has is done
        synchronized(items) { items.filter { it.state == XItem.PENDING && isOnRadio(it.id) }.forEach { it.state = XItem.DONE; it.sent = it.size; save(it) } }
        Events.emit(Ev.TRANSFERS)
    }

    fun deleteOnRadio(ids: List<Long>) {
        val a = JSONArray(); ids.forEach { a.put(it) }
        CarLink.send(pkt(Proto.R_DELETE, "ids" to a))
    }

    fun requestRadioLibrary() = CarLink.send(Packet(Proto.R_LIB_REQ))

    /** Bytes per second of the running transfer, for the ETA. */
    fun speed(): Long {
        val x = current ?: return 0
        val dt = SystemClock.elapsedRealtime() - startedAt
        return if (dt > 800) (x.sent - startedBytes) * 1000 / dt else 0
    }
}
