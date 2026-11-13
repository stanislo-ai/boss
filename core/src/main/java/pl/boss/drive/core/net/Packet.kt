package pl.boss.drive.core.net

import org.json.JSONArray
import org.json.JSONObject
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException

/**
 * One frame on the wire:  [int jsonLen][int bodyLen][json utf8][body bytes]
 * The JSON always carries "t" (message type). Body is optional binary payload
 * (album art, contact photos, file chunks).
 */
class Packet(val type: String, val json: JSONObject = JSONObject(), val body: ByteArray? = null) {
    init { json.put("t", type) }

    fun s(k: String, d: String = ""): String = if (json.has(k) && !json.isNull(k)) json.optString(k, d) else d
    fun i(k: String, d: Int = 0): Int = json.optInt(k, d)
    fun l(k: String, d: Long = 0L): Long = json.optLong(k, d)
    fun b(k: String, d: Boolean = false): Boolean = json.optBoolean(k, d)
    fun d(k: String, d: Double = 0.0): Double = json.optDouble(k, d)
    fun arr(k: String): JSONArray = json.optJSONArray(k) ?: JSONArray()

    override fun toString(): String = "Packet($type, ${body?.size ?: 0}B)"

    companion object {
        private const val MAX_JSON = 4 shl 20
        private const val MAX_BODY = 8 shl 20

        fun write(out: DataOutputStream, p: Packet) {
            val js = p.json.toString().toByteArray(Charsets.UTF_8)
            out.writeInt(js.size)
            out.writeInt(p.body?.size ?: 0)
            out.write(js)
            p.body?.let { out.write(it) }
            out.flush()
        }

        fun read(inp: DataInputStream): Packet {
            val jl = inp.readInt()
            val bl = inp.readInt()
            if (jl < 2 || jl > MAX_JSON || bl < 0 || bl > MAX_BODY) throw IOException("bad frame $jl/$bl")
            val jb = ByteArray(jl)
            inp.readFully(jb)
            val body = if (bl > 0) ByteArray(bl).also { inp.readFully(it) } else null
            val o = JSONObject(String(jb, Charsets.UTF_8))
            return Packet(o.optString("t"), o, body)
        }
    }
}

/** Small builder: obj("a" to 1, "b" to null) — null values are skipped. */
fun obj(vararg pairs: Pair<String, Any?>): JSONObject {
    val o = JSONObject()
    for ((k, v) in pairs) if (v != null) o.put(k, v)
    return o
}

fun pkt(type: String, vararg pairs: Pair<String, Any?>, body: ByteArray? = null) = Packet(type, obj(*pairs), body)

inline fun JSONArray.forEachObj(fn: (JSONObject) -> Unit) {
    for (i in 0 until length()) optJSONObject(i)?.let(fn)
}

fun JSONObject.str(k: String, d: String = ""): String = if (has(k) && !isNull(k)) optString(k, d) else d
