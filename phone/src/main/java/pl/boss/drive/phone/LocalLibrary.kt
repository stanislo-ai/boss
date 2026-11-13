package pl.boss.drive.phone

import android.content.ContentUris
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.MediaPlayer
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.net.Uri
import android.provider.MediaStore
import org.json.JSONArray
import pl.boss.drive.core.net.Proto
import pl.boss.drive.core.net.obj
import pl.boss.drive.core.net.pkt

data class Song(val id: Long, val title: String, val artist: String, val album: String, val albumId: Long, val dur: Long, val size: Long, val mime: String) {
    val uri: Uri get() = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
}

/** Music files on the phone: browsable from the car and playable through our own MediaSession. */
class LocalLibrary(private val ctx: Context) {

    fun songs(selection: String? = null, args: Array<String>? = null, order: String = MediaStore.Audio.Media.TITLE + " COLLATE LOCALIZED ASC", limit: Int = 3000): List<Song> {
        if (!Perms.has(ctx, Perms.AUDIO)) return emptyList()
        val out = ArrayList<Song>()
        val sel = "${MediaStore.Audio.Media.IS_MUSIC} != 0" + (selection?.let { " AND ($it)" } ?: "")
        try {
            ctx.contentResolver.query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.ALBUM,
                    MediaStore.Audio.Media.ALBUM_ID, MediaStore.Audio.Media.DURATION, MediaStore.Audio.Media.SIZE, MediaStore.Audio.Media.MIME_TYPE),
                sel, args, order)?.use { c ->
                while (c.moveToNext() && out.size < limit) {
                    out.add(Song(c.getLong(0), c.getString(1) ?: "", (c.getString(2) ?: "").let { if (it == "<unknown>") "" else it },
                        c.getString(3) ?: "", c.getLong(4), c.getLong(5), c.getLong(6), c.getString(7) ?: "audio/mpeg"))
                }
            }
        } catch (_: Throwable) {}
        return out
    }

    private fun songsFor(req: String): List<Song> {
        val base = req.substringBefore('#')
        val kind = base.substringBefore(':'); val id = base.substringAfter(':', "")
        return when (kind) {
            "album" -> songs("${MediaStore.Audio.Media.ALBUM_ID} = ?", arrayOf(id), "${MediaStore.Audio.Media.TRACK}, ${MediaStore.Audio.Media.TITLE}")
            "artist" -> songs("${MediaStore.Audio.Media.ARTIST_ID} = ?", arrayOf(id), "${MediaStore.Audio.Media.ALBUM}, ${MediaStore.Audio.Media.TRACK}")
            "playlist" -> playlistSongs(id.toLongOrNull() ?: -1)
            else -> songs()
        }
    }

    @Suppress("DEPRECATION")
    private fun playlistSongs(pid: Long): List<Song> {
        val ids = ArrayList<Long>()
        try {
            ctx.contentResolver.query(MediaStore.Audio.Playlists.Members.getContentUri("external", pid),
                arrayOf(MediaStore.Audio.Playlists.Members.AUDIO_ID), null, null, MediaStore.Audio.Playlists.Members.PLAY_ORDER)?.use { c ->
                while (c.moveToNext()) ids.add(c.getLong(0))
            }
        } catch (_: Throwable) {}
        if (ids.isEmpty()) return emptyList()
        val byId = songs("${MediaStore.Audio.Media._ID} IN (${ids.joinToString(",")})").associateBy { it.id }
        return ids.mapNotNull { byId[it] }
    }

    /** Answer a browse request from the head unit. */
    @Suppress("DEPRECATION")
    fun answer(req: String) {
        val items = JSONArray()
        var title = ""
        if (!Perms.has(ctx, Perms.AUDIO)) {
            CarLink.send(pkt(Proto.LIBRARY, "req" to req, "items" to items, "title" to ""))
            CarLink.send(pkt(Proto.TOAST, "text" to "Zezwól aplikacji na telefonie na dostęp do muzyki"))
            return
        }
        try {
            when (req) {
                "albums" -> ctx.contentResolver.query(MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI,
                    arrayOf(MediaStore.Audio.Albums._ID, MediaStore.Audio.Albums.ALBUM, MediaStore.Audio.Albums.ARTIST, MediaStore.Audio.Albums.NUMBER_OF_SONGS),
                    null, null, MediaStore.Audio.Albums.ALBUM + " COLLATE LOCALIZED ASC")?.use { c ->
                    while (c.moveToNext() && items.length() < 1500) items.put(obj("id" to c.getLong(0).toString(), "title" to (c.getString(1) ?: ""),
                        "sub" to "${c.getString(2) ?: ""} • ${c.getInt(3)} utw.", "kind" to "album"))
                }
                "artists" -> ctx.contentResolver.query(MediaStore.Audio.Artists.EXTERNAL_CONTENT_URI,
                    arrayOf(MediaStore.Audio.Artists._ID, MediaStore.Audio.Artists.ARTIST, MediaStore.Audio.Artists.NUMBER_OF_TRACKS),
                    null, null, MediaStore.Audio.Artists.ARTIST + " COLLATE LOCALIZED ASC")?.use { c ->
                    while (c.moveToNext() && items.length() < 1500) {
                        val n = c.getString(1) ?: continue
                        if (n == "<unknown>") continue
                        items.put(obj("id" to c.getLong(0).toString(), "title" to n, "sub" to "${c.getInt(2)} utw.", "kind" to "artist"))
                    }
                }
                "playlists" -> ctx.contentResolver.query(MediaStore.Audio.Playlists.EXTERNAL_CONTENT_URI,
                    arrayOf(MediaStore.Audio.Playlists._ID, MediaStore.Audio.Playlists.NAME), null, null, MediaStore.Audio.Playlists.NAME)?.use { c ->
                    while (c.moveToNext()) items.put(obj("id" to c.getLong(0).toString(), "title" to (c.getString(1) ?: ""), "sub" to "Playlista", "kind" to "playlist"))
                }
                else -> {
                    for (s in songsFor(req).take(1500)) items.put(obj("id" to s.id.toString(), "title" to s.title,
                        "sub" to listOf(s.artist, s.album).filter { it.isNotEmpty() }.joinToString(" — "), "kind" to "song", "dur" to s.dur))
                }
            }
        } catch (_: Throwable) {}
        CarLink.send(pkt(Proto.LIBRARY, "req" to req, "items" to items, "title" to title))
    }

    fun play(req: String, index: Int) {
        val list = songsFor(req)
        if (list.isEmpty()) return
        val p = player ?: LocalPlayer(ctx).also { player = it }
        p.play(list, index.coerceIn(0, list.size - 1), req.endsWith("#shuffle"))
    }

    companion object { @Volatile var player: LocalPlayer? = null }
}

/** Plays phone music files; audio goes to the car over Bluetooth like any other app. */
class LocalPlayer(private val ctx: Context) {
    private val am = ctx.getSystemService(AudioManager::class.java)
    private var mp: MediaPlayer? = null
    private var list: List<Song> = emptyList()
    private var order = IntArray(0)
    private var idx = 0
    private var prepared = false
    private val attrs = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()
    private val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN).setAudioAttributes(attrs)
        .setOnAudioFocusChangeListener { f -> if (f == AudioManager.AUDIOFOCUS_LOSS || f == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) pause() }.build()
    val session = MediaSession(ctx, "BossDriveLocal").apply {
        setCallback(object : MediaSession.Callback() {
            override fun onPlay() = resume()
            override fun onPause() = pause()
            override fun onSkipToNext() = next()
            override fun onSkipToPrevious() = prev()
            override fun onSeekTo(pos: Long) { mp?.seekTo(pos.toInt()); publish() }
            override fun onSkipToQueueItem(id: Long) { val i = order.indexOfFirst { list[it].id == id }; if (i >= 0) { idx = i; load(true) } }
        })
    }

    fun token() = session.sessionToken

    fun play(songs: List<Song>, start: Int, shuffle: Boolean) {
        list = songs
        order = if (shuffle) (listOf(start) + songs.indices.filter { it != start }.shuffled()).toIntArray() else IntArray(songs.size) { (start + it) % songs.size }
        idx = 0
        session.setQueue(order.take(200).map { i -> val s = list[i]
            MediaSession.QueueItem(android.media.MediaDescription.Builder().setMediaId(s.id.toString()).setTitle(s.title).setSubtitle(s.artist).build(), s.id) })
        load(true)
    }

    private fun load(autoplay: Boolean) {
        val s = list.getOrNull(order.getOrElse(idx) { -1 }) ?: return
        mp?.release()
        prepared = false
        mp = MediaPlayer().apply {
            setAudioAttributes(attrs)
            setDataSource(ctx, s.uri)
            setOnPreparedListener { prepared = true; if (autoplay) resume() else publish() }
            setOnCompletionListener { next() }
            setOnErrorListener { _, _, _ -> next(); true }
            prepareAsync()
        }
        val art = Artwork.of(ctx, s.uri, 400)
        session.setMetadata(MediaMetadata.Builder()
            .putString(MediaMetadata.METADATA_KEY_TITLE, s.title).putString(MediaMetadata.METADATA_KEY_ARTIST, s.artist)
            .putString(MediaMetadata.METADATA_KEY_ALBUM, s.album).putLong(MediaMetadata.METADATA_KEY_DURATION, s.dur)
            .apply { if (art != null) putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, art) }.build())
        session.isActive = true
        publish()
    }

    fun resume() {
        val p = mp ?: return
        if (!prepared) return
        am.requestAudioFocus(focus)
        p.start(); publish()
    }

    fun pause() { mp?.takeIf { prepared && it.isPlaying }?.pause(); publish() }
    fun next() { if (list.isEmpty()) return; idx = (idx + 1) % order.size; load(true) }
    fun prev() {
        val p = mp
        if (p != null && prepared && p.currentPosition > 3000) { p.seekTo(0); publish(); return }
        if (list.isEmpty()) return
        idx = if (idx == 0) order.size - 1 else idx - 1; load(true)
    }

    private fun publish() {
        val p = mp
        val playing = p != null && prepared && p.isPlaying
        val cur = list.getOrNull(order.getOrElse(idx) { -1 })
        session.setPlaybackState(PlaybackState.Builder()
            .setActions(PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_PLAY_PAUSE or PlaybackState.ACTION_SKIP_TO_NEXT or
                PlaybackState.ACTION_SKIP_TO_PREVIOUS or PlaybackState.ACTION_SEEK_TO or PlaybackState.ACTION_SKIP_TO_QUEUE_ITEM)
            .setState(if (playing) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED, if (p != null && prepared) p.currentPosition.toLong() else 0L, 1f)
            .setActiveQueueItemId(cur?.id ?: -1)
            .build())
    }

    fun release() { mp?.release(); mp = null; session.release(); am.abandonAudioFocusRequest(focus) }
}
