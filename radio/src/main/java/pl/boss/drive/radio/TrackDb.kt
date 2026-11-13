package pl.boss.drive.radio

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.io.File

data class Track(
    val id: Long, val xid: String, val title: String, val artist: String, val album: String,
    val duration: Long, val path: String, val art: String, val size: Long, val added: Long, val plays: Int,
)

/** Offline library index on the head unit. Files live in the app's external music dir. */
class TrackDb(ctx: Context) : SQLiteOpenHelper(ctx, "tracks.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE t(id INTEGER PRIMARY KEY AUTOINCREMENT, xid TEXT UNIQUE, title TEXT, artist TEXT, album TEXT," +
                " dur INTEGER, path TEXT, art TEXT, size INTEGER, added INTEGER, plays INTEGER DEFAULT 0)"
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, o: Int, n: Int) {}

    @Volatile private var cache: List<Track>? = null

    fun all(): List<Track> {
        cache?.let { return it }
        val out = ArrayList<Track>()
        readableDatabase.rawQuery("SELECT id,xid,title,artist,album,dur,path,art,size,added,plays FROM t ORDER BY title COLLATE NOCASE", null).use { c ->
            while (c.moveToNext()) out.add(
                Track(c.getLong(0), c.getString(1), c.getString(2) ?: "", c.getString(3) ?: "", c.getString(4) ?: "",
                    c.getLong(5), c.getString(6), c.getString(7) ?: "", c.getLong(8), c.getLong(9), c.getInt(10))
            )
        }
        // drop rows whose file vanished (SD card removed, cleared storage)
        val (ok, gone) = out.partition { File(it.path).exists() }
        if (gone.isNotEmpty()) gone.forEach { delete(it.id, removeFile = false) }
        cache = ok
        return ok
    }

    fun byXid(xid: String): Track? = all().firstOrNull { it.xid == xid }

    fun insert(xid: String, title: String, artist: String, album: String, dur: Long, path: String, art: String, size: Long) {
        writableDatabase.insertWithOnConflict("t", null, ContentValues().apply {
            put("xid", xid); put("title", title); put("artist", artist); put("album", album); put("dur", dur)
            put("path", path); put("art", art); put("size", size); put("added", System.currentTimeMillis())
        }, SQLiteDatabase.CONFLICT_REPLACE)
        cache = null
    }

    fun played(id: Long) {
        writableDatabase.execSQL("UPDATE t SET plays = plays + 1 WHERE id = ?", arrayOf<Any>(id))
    }

    fun delete(id: Long, removeFile: Boolean = true) {
        val t = if (removeFile) all().firstOrNull { it.id == id } else null
        if (removeFile && t != null) { File(t.path).delete(); if (t.art.isNotEmpty()) File(t.art).delete() }
        writableDatabase.delete("t", "id = ?", arrayOf(id.toString()))
        cache = null
    }

    fun totalBytes(): Long = all().sumOf { it.size }
}
