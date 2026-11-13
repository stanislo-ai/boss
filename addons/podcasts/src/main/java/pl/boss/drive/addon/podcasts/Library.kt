package pl.boss.drive.addon.podcasts

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.util.Xml
import org.json.JSONArray
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import pl.boss.drive.core.addon.Net
import pl.boss.drive.core.util.Bg
import java.io.File
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Locale

data class Show(val feed: String, val title: String, val author: String, val art: String)

data class Episode(
    val id: String, val feed: String, val title: String, val url: String, val date: Long, val dur: Long,
    var file: String = "", var pos: Long = 0, var played: Boolean = false,
) {
    val downloaded get() = file.isNotEmpty() && File(file).exists()
}

/** Subscriptions, episode index, RSS refresh and automatic downloads. */
object Library {
    private lateinit var ctx: Context
    val shows = ArrayList<Show>()
    val episodes = ArrayList<Episode>()
    val downloading = java.util.concurrent.ConcurrentHashMap<String, Int>()   // id -> percent
    @Volatile var refreshing = false; private set
    private val listeners = ArrayList<() -> Unit>()
    private const val KEEP_DOWNLOADED_PER_SHOW = 2
    private const val MAX_DOWNLOADED = 20

    fun init(c: Context) {
        if (::ctx.isInitialized) return
        ctx = c.applicationContext
        load()
    }

    fun listen(f: () -> Unit): () -> Unit { listeners.add(f); return { listeners.remove(f) } }
    private fun changed() = Bg.ui { listeners.toList().forEach { it() } }

    private fun dir() = (ctx.getExternalFilesDir("podcasts") ?: File(ctx.filesDir, "podcasts")).also { it.mkdirs() }
    private fun indexFile() = File(ctx.filesDir, "podcasts.json")

    @Synchronized private fun load() {
        try {
            val o = JSONObject(indexFile().readText())
            val sa = o.getJSONArray("shows")
            for (i in 0 until sa.length()) sa.getJSONObject(i).let { shows.add(Show(it.getString("feed"), it.optString("title"), it.optString("author"), it.optString("art"))) }
            val ea = o.getJSONArray("eps")
            for (i in 0 until ea.length()) ea.getJSONObject(i).let {
                episodes.add(Episode(it.getString("id"), it.getString("feed"), it.optString("title"), it.optString("url"), it.optLong("date"),
                    it.optLong("dur"), it.optString("file"), it.optLong("pos"), it.optBoolean("played")))
            }
        } catch (_: Throwable) {}
    }

    @Synchronized fun save() {
        val o = JSONObject()
        o.put("shows", JSONArray().apply { shows.forEach { put(JSONObject().put("feed", it.feed).put("title", it.title).put("author", it.author).put("art", it.art)) } })
        o.put("eps", JSONArray().apply { episodes.forEach {
            put(JSONObject().put("id", it.id).put("feed", it.feed).put("title", it.title).put("url", it.url).put("date", it.date)
                .put("dur", it.dur).put("file", it.file).put("pos", it.pos).put("played", it.played))
        } })
        val tmp = File(indexFile().path + ".tmp"); tmp.writeText(o.toString()); tmp.renameTo(indexFile())
    }

    fun show(feed: String) = shows.firstOrNull { it.feed == feed }

    fun list(): List<Episode> = synchronized(this) { episodes.filter { !it.played }.sortedByDescending { it.date } }

    // ------------------------------------------------------------------ search (iTunes directory, no key)

    fun search(term: String): List<Show> {
        val js = JSONObject(Net.text("https://itunes.apple.com/search?media=podcast&country=PL&limit=30&term=" + URLEncoder.encode(term, "UTF-8")))
        val r = js.getJSONArray("results")
        val out = ArrayList<Show>()
        for (i in 0 until r.length()) r.getJSONObject(i).let {
            val feed = it.optString("feedUrl"); if (feed.isNotEmpty()) out.add(Show(feed, it.optString("collectionName"), it.optString("artistName"), it.optString("artworkUrl100")))
        }
        return out
    }

    fun subscribe(s: Show) {
        synchronized(this) { if (shows.none { it.feed == s.feed }) shows.add(s); save() }
        changed()
        Bg.io { refreshShow(s); autoDownload() }
    }

    fun unsubscribe(s: Show) {
        synchronized(this) {
            shows.removeAll { it.feed == s.feed }
            episodes.filter { it.feed == s.feed }.forEach { if (it.file.isNotEmpty()) File(it.file).delete() }
            episodes.removeAll { it.feed == s.feed }
            save()
        }
        changed()
    }

    // ------------------------------------------------------------------ refresh

    fun refreshAll() {
        if (refreshing) return
        refreshing = true; changed()
        try { shows.toList().forEach { try { refreshShow(it) } catch (_: Throwable) {} }; autoDownload() }
        finally { refreshing = false; changed() }
    }

    private fun refreshShow(s: Show) {
        val items = parse(Net.text(s.feed), s.feed)
        synchronized(this) {
            for (e in items.take(15)) if (episodes.none { it.id == e.id }) episodes.add(e)
            // keep the index small: newest 15 per show + anything downloaded or in progress
            val mine = episodes.filter { it.feed == s.feed }.sortedByDescending { it.date }
            mine.drop(15).filter { !it.downloaded && it.pos == 0L }.forEach { episodes.remove(it) }
            save()
        }
        changed()
    }

    private val dateFormats = listOf("EEE, dd MMM yyyy HH:mm:ss Z", "EEE, d MMM yyyy HH:mm:ss Z", "EEE, dd MMM yyyy HH:mm Z", "dd MMM yyyy HH:mm:ss Z")
        .map { SimpleDateFormat(it, Locale.US) }

    private fun parse(xml: String, feed: String): List<Episode> {
        val out = ArrayList<Episode>()
        val p = Xml.newPullParser()
        p.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        p.setInput(xml.reader())
        var inItem = false
        var title = ""; var url = ""; var guid = ""; var date = 0L; var dur = 0L
        var ev = p.eventType
        while (ev != XmlPullParser.END_DOCUMENT) {
            when (ev) {
                XmlPullParser.START_TAG -> when (p.name) {
                    "item" -> { inItem = true; title = ""; url = ""; guid = ""; date = 0; dur = 0 }
                    "title" -> if (inItem) title = txt(p)
                    "guid" -> if (inItem) guid = txt(p)
                    "enclosure" -> if (inItem) url = p.getAttributeValue(null, "url") ?: url
                    "pubDate" -> if (inItem) { val t = txt(p); date = dateFormats.firstNotNullOfOrNull { f -> try { f.parse(t)?.time } catch (_: Throwable) { null } } ?: 0L }
                    "itunes:duration" -> if (inItem) dur = parseDur(txt(p))
                }
                XmlPullParser.END_TAG -> if (p.name == "item") {
                    inItem = false
                    if (url.isNotEmpty()) out.add(Episode((guid.ifEmpty { url }).hashCode().toUInt().toString(16) + feed.hashCode().toUInt().toString(16), feed, title, url, date, dur))
                }
            }
            ev = p.next()
        }
        return out.sortedByDescending { it.date }
    }

    private fun txt(p: XmlPullParser): String = try { p.nextText().trim() } catch (_: Throwable) { "" }

    private fun parseDur(s: String): Long {
        val parts = s.split(':').mapNotNull { it.toLongOrNull() }
        val sec = when (parts.size) { 3 -> parts[0] * 3600 + parts[1] * 60 + parts[2]; 2 -> parts[0] * 60 + parts[1]; 1 -> parts[0]; else -> 0 }
        return sec * 1000
    }

    // ------------------------------------------------------------------ downloads

    fun download(e: Episode) {
        if (downloading.containsKey(e.id) || e.downloaded) return
        downloading[e.id] = 0; changed()
        Bg.io {
            try {
                val f = File(dir(), e.id + ".mp3")
                Net.download(e.url, f) { d, t -> if (t > 0) { downloading[e.id] = (d * 100 / t).toInt(); changed() } }
                synchronized(this) { e.file = f.path; save() }
            } catch (_: Throwable) {} finally { downloading.remove(e.id); changed() }
        }
    }

    /** Newest unplayed episodes of every show land on the head unit by themselves. */
    fun autoDownload() {
        var want: List<Episode>
        synchronized(this) {
            val all = ArrayList<Episode>()
            for (s in shows) all += episodes.filter { it.feed == s.feed && !it.played }.sortedByDescending { it.date }.take(KEEP_DOWNLOADED_PER_SHOW)
            val room = MAX_DOWNLOADED - episodes.count { it.downloaded }
            want = all.filter { !it.downloaded }.take(maxOf(0, room))
        }
        for (e in want) {
            try {
                val f = File(dir(), e.id + ".mp3")
                downloading[e.id] = 0; changed()
                Net.download(e.url, f) { d, t -> if (t > 0) { downloading[e.id] = (d * 100 / t).toInt(); changed() } }
                synchronized(this) { e.file = f.path; save() }
            } catch (_: Throwable) {} finally { downloading.remove(e.id); changed() }
        }
    }

    fun position(id: String, pos: Long) = synchronized(this) { episodes.firstOrNull { it.id == id }?.pos = pos; save() }

    fun markPlayed(id: String) {
        synchronized(this) {
            episodes.firstOrNull { it.id == id }?.let { it.played = true; it.pos = 0; if (it.file.isNotEmpty()) { File(it.file).delete(); it.file = "" } }
            save()
        }
        changed()
    }

    fun schedule(c: Context) {
        val js = c.getSystemService(JobScheduler::class.java)
        if (js.allPendingJobs.any { it.id == 42 }) return
        js.schedule(JobInfo.Builder(42, ComponentName(c, RefreshJob::class.java))
            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
            .setPeriodic(3 * 3600_000L)
            .setPersisted(true)
            .build())
    }
}

/** Background refresh + auto-download every few hours whenever the head unit has internet. */
class RefreshJob : JobService() {
    override fun onStartJob(params: JobParameters): Boolean {
        Library.init(this)
        Bg.io { try { Library.refreshAll() } finally { jobFinished(params, false) } }
        return true
    }
    override fun onStopJob(params: JobParameters) = true
}
