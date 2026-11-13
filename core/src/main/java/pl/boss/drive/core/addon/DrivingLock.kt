package pl.boss.drive.core.addon

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.view.Gravity
import android.widget.FrameLayout
import pl.boss.drive.core.R
import pl.boss.drive.core.ui.MATCH
import pl.boss.drive.core.ui.W
import pl.boss.drive.core.ui.dpi
import pl.boss.drive.core.ui.glyph
import pl.boss.drive.core.ui.lp
import pl.boss.drive.core.ui.text
import pl.boss.drive.core.ui.vstack

/**
 * Games are for parking only: covers the board whenever GPS says the car is moving
 * (> 8 km/h) and lifts the cover once it has stood still for a few seconds.
 */
class DrivingLock(private val a: AddonActivity) : LocationListener {
    private var cover: FrameLayout? = null
    private var stillSince = 0L

    @SuppressLint("MissingPermission")
    fun start() {
        if (a.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return
        try { a.getSystemService(LocationManager::class.java).requestLocationUpdates(LocationManager.GPS_PROVIDER, 2000L, 0f, this) } catch (_: Throwable) {}
    }

    fun stop() { try { a.getSystemService(LocationManager::class.java).removeUpdates(this) } catch (_: Throwable) {} }

    override fun onLocationChanged(l: Location) {
        val kmh = if (l.hasSpeed()) l.speed * 3.6f else 0f
        if (kmh > 8f) { stillSince = 0; show() }
        else { if (stillSince == 0L) stillSince = System.currentTimeMillis(); if (System.currentTimeMillis() - stillSince > 4000) hide() }
    }

    private fun show() {
        if (cover != null) return
        val c = FrameLayout(a)
        c.setBackgroundColor(if (a.pal.dark) 0xF2000000.toInt() else 0xF2F2F2F7.toInt())
        c.isClickable = true
        val v = a.vstack(Gravity.CENTER)
        v.addView(a.glyph(R.drawable.ic_car, a.pal.orange), a.lp(a.dpi(72f), a.dpi(72f)))
        v.addView(a.text("Gra wstrzymana na czas jazdy", 26f, W.BOLD, a.pal.label, gravity = Gravity.CENTER), a.lp(MATCH, -2).apply { topMargin = a.dpi(16f) })
        v.addView(a.text("Wróci sama, gdy auto się zatrzyma.", 17f, W.REGULAR, a.pal.label2, gravity = Gravity.CENTER), a.lp(MATCH, -2).apply { topMargin = a.dpi(6f) })
        c.addView(v, FrameLayout.LayoutParams(MATCH, MATCH))
        a.contentFrame().addView(c, FrameLayout.LayoutParams(MATCH, MATCH))
        cover = c
    }

    private fun hide() { cover?.let { a.contentFrame().removeView(it) }; cover = null }

    @Deprecated("Deprecated in Java") override fun onStatusChanged(p: String?, s: Int, e: Bundle?) {}
    override fun onProviderEnabled(p: String) {}
    override fun onProviderDisabled(p: String) {}
}
