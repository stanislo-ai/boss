package pl.boss.drive.radio

import android.content.Context

object BuildConfigCompat {
    fun version(c: Context): String = try { c.packageManager.getPackageInfo(c.packageName, 0).versionName ?: "1.0" } catch (_: Throwable) { "1.0" }
}
