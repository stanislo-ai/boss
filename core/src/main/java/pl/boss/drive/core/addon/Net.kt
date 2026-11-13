package pl.boss.drive.core.addon

import android.content.Context
import android.graphics.Bitmap
import android.util.LruCache
import pl.boss.drive.core.util.Bg
import pl.boss.drive.core.util.Img
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

/** Minimal HTTP for add-ons (no OkHttp — every KB counts on the head unit). */
object Net {
    private const val UA = "BossDrive/1.0 (Android head unit)"

    fun open(url: String, timeout: Int = 15_000): HttpURLConnection {
        var u = URL(url)
        repeat(6) {
            val c = u.openConnection() as HttpURLConnection
            c.connectTimeout = timeout; c.readTimeout = timeout
            c.instanceFollowRedirects = false
            c.setRequestProperty("User-Agent", UA)
            val code = c.responseCode
            if (code in 300..399) {
                val loc = c.getHeaderField("Location") ?: return c
                c.disconnect(); u = URL(u, loc); return@repeat
            }
            if (code >= 400) { c.disconnect(); throw java.io.IOException("HTTP $code") }
            return c
        }
        throw java.io.IOException("too many redirects")
    }

    fun text(url: String): String = open(url).let { c -> try { c.inputStream.bufferedReader().readText() } finally { c.disconnect() } }

    fun bytes(url: String, max: Int = 4 shl 20): ByteArray = open(url).let { c ->
        try { c.inputStream.use { readLimited(it, max) } } finally { c.disconnect() }
    }

    /** Download to [dest] via a .part file; [progress] gets (done, total). */
    fun download(url: String, dest: File, progress: (Long, Long) -> Unit = { _, _ -> }) {
        val part = File(dest.path + ".part")
        val c = open(url, 30_000)
        try {
            val total = c.contentLengthLong
            c.inputStream.use { inp ->
                part.outputStream().use { out ->
                    val buf = ByteArray(32 * 1024); var done = 0L; var last = 0L
                    while (true) {
                        val n = inp.read(buf); if (n < 0) break
                        out.write(buf, 0, n); done += n
                        if (done - last > 256 * 1024) { last = done; progress(done, total) }
                    }
                }
            }
            part.renameTo(dest)
        } finally { c.disconnect(); part.delete() }
    }

    private fun readLimited(s: InputStream, max: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(16 * 1024)
        while (true) { val n = s.read(buf); if (n < 0) break; out.write(buf, 0, n); if (out.size() > max) break }
        return out.toByteArray()
    }
}

/** Remote artwork: disk cache (small JPEGs) + small in-memory LRU. */
object RemoteImages {
    private val mem = object : LruCache<String, Bitmap>(2 * 1024 * 1024) { override fun sizeOf(k: String, v: Bitmap) = v.byteCount }
    private val loading = HashSet<String>()

    fun get(ctx: Context, url: String, px: Int = 128, onLoaded: () -> Unit): Bitmap? {
        if (url.isEmpty()) return null
        mem.get(url)?.let { return it }
        if (!loading.add(url)) return null
        val f = File(ctx.cacheDir, "img_" + url.hashCode().toUInt().toString(16) + ".jpg")
        Bg.load({
            try {
                if (!f.exists()) { val b = Img.decode(Net.bytes(url, 2 shl 20), px * 2); if (b != null) f.writeBytes(Img.jpeg(b, px * 2, 85)) }
                Img.decodeFile(f.path, px)
            } catch (_: Throwable) { null }
        }) { b -> loading.remove(url); if (b != null) { mem.put(url, b); onLoaded() } }
        return null
    }

    fun clear() = mem.evictAll()
}
