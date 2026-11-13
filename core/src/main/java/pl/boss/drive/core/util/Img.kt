package pl.boss.drive.core.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.min

/** Small, allocation-conscious bitmap helpers (the head unit has 1 GB RAM). */
object Img {
    fun scaled(src: Bitmap, maxPx: Int): Bitmap {
        val w = src.width; val h = src.height
        if (w <= maxPx && h <= maxPx) return src
        val s = maxPx.toFloat() / max(w, h)
        return Bitmap.createScaledBitmap(src, max(1, (w * s).toInt()), max(1, (h * s).toInt()), true)
    }

    fun jpeg(src: Bitmap, maxPx: Int, quality: Int = 82): ByteArray {
        val b = scaled(src, maxPx)
        val os = ByteArrayOutputStream(32 * 1024)
        b.compress(Bitmap.CompressFormat.JPEG, quality, os)
        if (b !== src) b.recycle()
        return os.toByteArray()
    }

    fun png(src: Bitmap, maxPx: Int): ByteArray {
        val b = scaled(src, maxPx)
        val os = ByteArrayOutputStream(8 * 1024)
        b.compress(Bitmap.CompressFormat.PNG, 100, os)
        if (b !== src) b.recycle()
        return os.toByteArray()
    }

    /** Decode with power-of-two subsampling so we never inflate a huge image into RAM. */
    fun decode(bytes: ByteArray, maxPx: Int): Bitmap? {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, o)
        if (o.outWidth <= 0) return null
        var sample = 1
        while (max(o.outWidth, o.outHeight) / (sample * 2) >= maxPx) sample *= 2
        val o2 = BitmapFactory.Options().apply { inSampleSize = sample; inPreferredConfig = Bitmap.Config.ARGB_8888 }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, o2)
    }

    fun decodeFile(path: String, maxPx: Int): Bitmap? {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, o)
        if (o.outWidth <= 0) return null
        var sample = 1
        while (max(o.outWidth, o.outHeight) / (sample * 2) >= maxPx) sample *= 2
        return BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
    }

    /** Cheap "Apple Music" backdrop: tiny downscale + stack blur, drawn stretched with filtering. */
    fun backdrop(src: Bitmap): Bitmap {
        val small = Bitmap.createScaledBitmap(src, 40, 40, true).copy(Bitmap.Config.ARGB_8888, true)
        stackBlur(small, 6)
        return small
    }

    /** Average colour, biased away from near-black/near-white, for gradients and tints. */
    fun dominant(src: Bitmap): Int {
        val s = Bitmap.createScaledBitmap(src, 16, 16, true)
        var r = 0L; var g = 0L; var b = 0L; var n = 0L
        for (y in 0 until 16) for (x in 0 until 16) {
            val c = s.getPixel(x, y)
            val mx = max(Color.red(c), max(Color.green(c), Color.blue(c)))
            val mn = min(Color.red(c), min(Color.green(c), Color.blue(c)))
            val w = 1 + (mx - mn) / 16   // weight saturated pixels more
            r += Color.red(c) * w; g += Color.green(c) * w; b += Color.blue(c) * w; n += w
        }
        if (s !== src) s.recycle()
        if (n == 0L) return Color.DKGRAY
        return Color.rgb((r / n).toInt(), (g / n).toInt(), (b / n).toInt())
    }

    fun isLight(c: Int): Boolean = (0.299 * Color.red(c) + 0.587 * Color.green(c) + 0.114 * Color.blue(c)) > 170

    /** Mario Klingemann's stack blur, in place. */
    fun stackBlur(bmp: Bitmap, radius: Int) {
        if (radius < 1) return
        val w = bmp.width; val h = bmp.height
        val pix = IntArray(w * h)
        bmp.getPixels(pix, 0, w, 0, 0, w, h)
        val wm = w - 1; val hm = h - 1; val wh = w * h; val div = radius + radius + 1
        val r = IntArray(wh); val g = IntArray(wh); val b = IntArray(wh)
        val vmin = IntArray(max(w, h))
        var divsum = (div + 1) shr 1; divsum *= divsum
        val dv = IntArray(256 * divsum) { it / divsum }
        var yi = 0; var yw = 0
        val stack = Array(div) { IntArray(3) }
        val r1 = radius + 1
        for (y in 0 until h) {
            var rinsum = 0; var ginsum = 0; var binsum = 0; var routsum = 0; var goutsum = 0; var boutsum = 0
            var rsum = 0; var gsum = 0; var bsum = 0
            for (i in -radius..radius) {
                val p = pix[yi + min(wm, max(i, 0))]
                val sir = stack[i + radius]
                sir[0] = (p and 0xff0000) shr 16; sir[1] = (p and 0x00ff00) shr 8; sir[2] = p and 0x0000ff
                val rbs = r1 - kotlin.math.abs(i)
                rsum += sir[0] * rbs; gsum += sir[1] * rbs; bsum += sir[2] * rbs
                if (i > 0) { rinsum += sir[0]; ginsum += sir[1]; binsum += sir[2] } else { routsum += sir[0]; goutsum += sir[1]; boutsum += sir[2] }
            }
            var stackpointer = radius
            for (x in 0 until w) {
                r[yi] = dv[rsum]; g[yi] = dv[gsum]; b[yi] = dv[bsum]
                rsum -= routsum; gsum -= goutsum; bsum -= boutsum
                val stackstart = stackpointer - radius + div
                var sir = stack[stackstart % div]
                routsum -= sir[0]; goutsum -= sir[1]; boutsum -= sir[2]
                if (y == 0) vmin[x] = min(x + radius + 1, wm)
                val p = pix[yw + vmin[x]]
                sir[0] = (p and 0xff0000) shr 16; sir[1] = (p and 0x00ff00) shr 8; sir[2] = p and 0x0000ff
                rinsum += sir[0]; ginsum += sir[1]; binsum += sir[2]
                rsum += rinsum; gsum += ginsum; bsum += binsum
                stackpointer = (stackpointer + 1) % div
                sir = stack[stackpointer % div]
                routsum += sir[0]; goutsum += sir[1]; boutsum += sir[2]
                rinsum -= sir[0]; ginsum -= sir[1]; binsum -= sir[2]
                yi++
            }
            yw += w
        }
        for (x in 0 until w) {
            var rinsum = 0; var ginsum = 0; var binsum = 0; var routsum = 0; var goutsum = 0; var boutsum = 0
            var rsum = 0; var gsum = 0; var bsum = 0
            var yp = -radius * w
            for (i in -radius..radius) {
                yi = max(0, yp) + x
                val sir = stack[i + radius]
                sir[0] = r[yi]; sir[1] = g[yi]; sir[2] = b[yi]
                val rbs = r1 - kotlin.math.abs(i)
                rsum += r[yi] * rbs; gsum += g[yi] * rbs; bsum += b[yi] * rbs
                if (i > 0) { rinsum += sir[0]; ginsum += sir[1]; binsum += sir[2] } else { routsum += sir[0]; goutsum += sir[1]; boutsum += sir[2] }
                if (i < hm) yp += w
            }
            yi = x
            var stackpointer = radius
            for (y in 0 until h) {
                pix[yi] = (0xff000000.toInt() and pix[yi]) or (dv[rsum] shl 16) or (dv[gsum] shl 8) or dv[bsum]
                rsum -= routsum; gsum -= goutsum; bsum -= boutsum
                val stackstart = stackpointer - radius + div
                var sir = stack[stackstart % div]
                routsum -= sir[0]; goutsum -= sir[1]; boutsum -= sir[2]
                if (x == 0) vmin[y] = min(y + r1, hm) * w
                val p = x + vmin[y]
                sir[0] = r[p]; sir[1] = g[p]; sir[2] = b[p]
                rinsum += sir[0]; ginsum += sir[1]; binsum += sir[2]
                rsum += rinsum; gsum += ginsum; bsum += binsum
                stackpointer = (stackpointer + 1) % div
                sir = stack[stackpointer]
                routsum += sir[0]; goutsum += sir[1]; boutsum += sir[2]
                rinsum -= sir[0]; ginsum -= sir[1]; binsum -= sir[2]
                yi += w
            }
        }
        bmp.setPixels(pix, 0, w, 0, 0, w, h)
    }
}
