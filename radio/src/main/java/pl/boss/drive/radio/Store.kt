package pl.boss.drive.radio

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.LruCache
import java.util.EnumMap

enum class Topic { LINK, NP, ART, SOURCES, QUEUE, LIBRARY, CALL, CONTACTS, RECENTS, STATUS, WEATHER, MESSAGES, TRANSFER, OFFLINE, THEME, PAIRING, STORE }

data class NowPlaying(
    val title: String = "", val artist: String = "", val album: String = "",
    val duration: Long = 0, val position: Long = 0, val posAt: Long = SystemClock.elapsedRealtime(),
    val playing: Boolean = false, val speed: Float = 1f,
    val pkg: String = "", val appName: String = "", val artKey: String = "",
    val canSeek: Boolean = true, val canSkip: Boolean = true,
    val offline: Boolean = false, val shuffle: Boolean = false, val repeat: Boolean = false,
) {
    val isEmpty get() = title.isEmpty()
    fun livePosition(): Long {
        if (!playing) return position
        val p = position + ((SystemClock.elapsedRealtime() - posAt) * speed).toLong()
        return if (duration > 0) p.coerceAtMost(duration) else p
    }
}

data class Source(val pkg: String, val name: String, val active: Boolean, val playing: Boolean)
data class QueueItem(val id: Long, val title: String, val sub: String)
data class LibItem(val id: String, val title: String, val sub: String, val kind: String, val dur: Long)
data class Contact(val name: String, val number: String, val label: String = "", val photo: String = "")
data class Recent(val name: String, val number: String, val type: Int, val time: Long)
data class Message(val key: String, val app: String, val appName: String, val title: String, val text: String, val time: Long, val canReply: Boolean)
data class CallState(val state: String = "idle", val number: String = "", val name: String = "", val photo: String = "", val since: Long = 0) {
    val active get() = state != "idle"
}
data class PhoneStatus(val battery: Int = -1, val charging: Boolean = false, val signal: Int = 0, val net: String = "", val carrier: String = "")
data class Weather(val temp: Int, val code: Int, val hi: Int, val lo: Int, val place: String, val isDay: Boolean, val lat: Double, val lon: Double)
data class Transfer(val id: String, val title: String, val artist: String, val size: Long, val done: Long, val queued: Int)

/**
 * Single source of truth for the UI. Mutated from the link / player threads,
 * listeners always run on the main thread, emissions are coalesced per frame.
 */
object Store {
    private val main = Handler(Looper.getMainLooper())
    private val subs = EnumMap<Topic, MutableList<() -> Unit>>(Topic::class.java)
    private val pending = java.util.EnumSet.noneOf(Topic::class.java)
    private var flushPosted = false

    // ---- link ----
    @Volatile var connected = false
    @Volatile var transport = ""
    @Volatile var phoneName = ""
    @Volatile var pendingPhone: Pair<String, String>? = null   // id,name of an unknown phone asking to pair

    // ---- media ----
    @Volatile var phoneNp = NowPlaying()
    @Volatile var localNp = NowPlaying(offline = true)
    @Volatile var localActive = false
    // media apps on the radio itself (YouTube Music, audio add-ons)
    @Volatile var appNp = NowPlaying()
    @Volatile var appActive = false
    @Volatile var appArt: Bitmap? = null
    @Volatile var appBackdrop: Bitmap? = null
    @Volatile var appTint: Int = 0xFF3A3A3C.toInt()
    @Volatile var radioSources: List<Source> = emptyList()
    val np get() = when { localActive -> localNp; appActive -> appNp; else -> phoneNp }
    @Volatile var phoneArt: Bitmap? = null
    @Volatile var phoneBackdrop: Bitmap? = null
    @Volatile var phoneTint: Int = 0xFF3A3A3C.toInt()
    @Volatile var localArt: Bitmap? = null
    @Volatile var localBackdrop: Bitmap? = null
    @Volatile var localTint: Int = 0xFF3A3A3C.toInt()
    val art get() = when { localActive -> localArt; appActive -> appArt; else -> phoneArt }
    val backdrop get() = when { localActive -> localBackdrop; appActive -> appBackdrop; else -> phoneBackdrop }
    val tint get() = when { localActive -> localTint; appActive -> appTint; else -> phoneTint }

    @Volatile var sources: List<Source> = emptyList()
    val icons = LruCache<String, Bitmap>(24)
    @Volatile var queue: List<QueueItem> = emptyList()
    @Volatile var queueActive: Long = -1
    val library = LruCache<String, Pair<String, List<LibItem>>>(12)   // req -> (title, items)

    // ---- phone ----
    @Volatile var call = CallState()
    @Volatile var callStartedAt = 0L
    @Volatile var favorites: List<Contact> = emptyList()
    @Volatile var contacts: List<Contact> = emptyList()
    @Volatile var recents: List<Recent> = emptyList()
    val photos = LruCache<String, Bitmap>(40)
    @Volatile var status = PhoneStatus()
    @Volatile var weather: Weather? = null
    val messages = ArrayList<Message>()
    @Volatile var unread = 0

    // ---- transfers ----
    @Volatile var transfer: Transfer? = null
    @Volatile var lastReceived: String = ""

    fun on(t: Topic, f: () -> Unit): () -> Unit {
        subs.getOrPut(t) { ArrayList() }.add(f)
        return { subs[t]?.remove(f) }
    }

    fun emit(vararg ts: Topic) {
        synchronized(pending) { pending.addAll(ts); if (flushPosted) return; flushPosted = true }
        main.post {
            val now: List<Topic>
            synchronized(pending) { now = pending.toList(); pending.clear(); flushPosted = false }
            for (t in now) subs[t]?.toList()?.forEach { try { it() } catch (e: Throwable) { android.util.Log.e("Store", "listener $t", e) } }
        }
    }

    fun ui(r: () -> Unit) { if (Looper.myLooper() == Looper.getMainLooper()) r() else main.post(r) }
}
