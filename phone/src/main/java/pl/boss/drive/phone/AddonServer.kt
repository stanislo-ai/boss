package pl.boss.drive.phone

import android.content.Context
import org.json.JSONArray
import pl.boss.drive.core.addon.Addons
import pl.boss.drive.core.net.Proto
import pl.boss.drive.core.net.obj
import pl.boss.drive.core.net.pkt

/** Serves the add-on APKs bundled inside the phone app to the head unit's store. */
object AddonServer {
    private fun assetName(pkg: String) = "addons/$pkg.apk"

    fun catalog(ctx: Context) {
        val items = JSONArray()
        for (a in Addons.ALL) {
            if (a.external) continue
            val size = try { ctx.assets.openFd(assetName(a.pkg)).use { it.length } } catch (_: Throwable) {
                try { ctx.assets.open(assetName(a.pkg)).use { s -> var n = 0L; val b = ByteArray(65536); while (true) { val r = s.read(b); if (r < 0) break; n += r }; n } } catch (_: Throwable) { -1L }
            }
            if (size > 0) items.put(obj("pkg" to a.pkg, "size" to size, "ver" to a.version))
        }
        CarLink.send(pkt(Proto.STORE, "items" to items))
    }

    fun send(ctx: Context, pkg: String) {
        if (Addons.byPkg(pkg)?.external != false) return
        try {
            val bytes = ctx.assets.open(assetName(pkg)).use { it.readBytes() }
            var off = 0
            while (off < bytes.size) {
                val n = minOf(Proto.CHUNK, bytes.size - off)
                CarLink.send(pkt(Proto.APP_CHUNK, "pkg" to pkg, "off" to off.toLong(), body = bytes.copyOfRange(off, off + n)))
                off += n
            }
            CarLink.send(pkt(Proto.APP_END, "pkg" to pkg, "size" to bytes.size.toLong()))
        } catch (e: Throwable) {
            CarLink.send(pkt(Proto.TOAST, "text" to "Telefon nie ma tego dodatku"))
        }
    }
}
