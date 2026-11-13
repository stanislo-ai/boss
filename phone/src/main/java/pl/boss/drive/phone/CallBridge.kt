package pl.boss.drive.phone

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.provider.CallLog
import android.provider.ContactsContract
import android.telecom.TelecomManager
import android.telephony.SmsManager
import android.telephony.TelephonyManager
import org.json.JSONArray
import pl.boss.drive.core.net.Proto
import pl.boss.drive.core.net.obj
import pl.boss.drive.core.net.pkt
import pl.boss.drive.core.util.Img

/**
 * Calls through the car: the audio itself goes over the radio's Bluetooth hands-free profile
 * (best quality, echo cancelling in the head unit), Boss Drive adds the CarPlay-style call
 * screen, one-tap answer/decline, "I'm driving" SMS, favourites, recents and contacts.
 */
/** System broadcasts; Android 14 wants the exported flag spelled out. */
fun registerSystem(ctx: Context, rx: BroadcastReceiver, f: IntentFilter): Intent? =
    if (Build.VERSION.SDK_INT >= 33) ctx.registerReceiver(rx, f, Context.RECEIVER_EXPORTED) else ctx.registerReceiver(rx, f)

@SuppressLint("MissingPermission")
class CallBridge(private val ctx: Context) {
    private val h = CarLink.handler
    private val telecom = ctx.getSystemService(TelecomManager::class.java)
    private var state = "idle"
    private var number = ""
    private var name = ""
    private var photoKey = ""
    private var since = 0L
    private var pendingDial = ""
    private var started = false
    private val nameCache = HashMap<String, Pair<String, Long>>()

    private val rx = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            val st = i.getStringExtra(TelephonyManager.EXTRA_STATE) ?: return
            @Suppress("DEPRECATION") val num = i.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)
            h.post { onState(st, num) }
        }
    }

    private val logObserver = object : ContentObserver(h) {
        override fun onChange(selfChange: Boolean) { h.removeCallbacks(pushRecents); h.postDelayed(pushRecents, 1500) }
    }
    private val pushRecents = Runnable { sendRecents() }

    fun start() {
        if (started) return
        started = true
        try { registerSystem(ctx, rx, IntentFilter(TelephonyManager.ACTION_PHONE_STATE_CHANGED)) } catch (_: Throwable) {}
        if (Perms.has(ctx, Perms.CALL_LOG)) try { ctx.contentResolver.registerContentObserver(CallLog.Calls.CONTENT_URI, true, logObserver) } catch (_: Throwable) {}
    }

    fun stop() {
        if (!started) return
        started = false
        try { ctx.unregisterReceiver(rx) } catch (_: Throwable) {}
        try { ctx.contentResolver.unregisterContentObserver(logObserver) } catch (_: Throwable) {}
    }

    private fun onState(st: String, num: String?) {
        when (st) {
            TelephonyManager.EXTRA_STATE_RINGING -> {
                if (state != "ringing") { state = "ringing"; since = 0 }
                if (!num.isNullOrEmpty()) number = num
            }
            TelephonyManager.EXTRA_STATE_OFFHOOK -> {
                if (state == "idle") {           // outgoing
                    state = "dialing"; number = pendingDial; since = SystemClock.elapsedRealtime()
                    // we can't see the remote side pick up without being the dialer app: switch to the timer after a moment
                    h.postDelayed({ if (state == "dialing") { state = "active"; publish() } }, 6000)
                } else if (state == "ringing") { state = "active"; since = SystemClock.elapsedRealtime() }
            }
            TelephonyManager.EXTRA_STATE_IDLE -> { state = "idle"; number = ""; pendingDial = ""; since = 0 }
        }
        resolveName()
        publish()
    }

    private fun resolveName() {
        if (number.isEmpty()) { name = ""; photoKey = ""; return }
        val cached = nameCache[number]
        if (cached != null) { name = cached.first; photoKey = if (cached.second > 0) "c${cached.second}" else ""; return }
        name = ""; photoKey = ""
        if (!Perms.has(ctx, Perms.CONTACTS)) return
        try {
            val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
            ctx.contentResolver.query(uri, arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME, ContactsContract.PhoneLookup._ID), null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    name = c.getString(0) ?: ""; val id = c.getLong(1)
                    nameCache[number] = name to id
                    photoKey = "c$id"
                    sendPhoto(id, photoKey)
                }
            }
        } catch (_: Throwable) {}
    }

    private fun publish() {
        val elapsed = if (since > 0) SystemClock.elapsedRealtime() - since else 0
        CarLink.send(pkt(Proto.CALL, "state" to state, "number" to number, "name" to name, "photo" to photoKey, "since" to elapsed))
    }

    fun command(a: String, num: String, text: String) = h.post {
        try {
            when (a) {
                "accept" -> if (Perms.has(ctx, arrayOf(android.Manifest.permission.ANSWER_PHONE_CALLS))) {
                    @Suppress("DEPRECATION") telecom.acceptRingingCall()
                }
                "reject", "end" -> endCall()
                "rejectSms" -> {
                    val to = num.ifEmpty { number }
                    endCall()
                    if (to.isNotEmpty() && Perms.has(ctx, Perms.SMS)) {
                        val sms = if (Build.VERSION.SDK_INT >= 31) ctx.getSystemService(SmsManager::class.java) else @Suppress("DEPRECATION") SmsManager.getDefault()
                        sms.sendTextMessage(to, null, text.ifEmpty { "Prowadzę samochód, oddzwonię niedługo." }, null, null)
                    }
                }
                "dial" -> if (num.isNotEmpty()) {
                    pendingDial = num
                    if (Perms.has(ctx, arrayOf(android.Manifest.permission.CALL_PHONE))) telecom.placeCall(Uri.fromParts("tel", num, null), Bundle())
                }
            }
        } catch (e: Throwable) {
            CarLink.send(pkt(Proto.TOAST, "text" to "Telefon nie pozwolił: ${e.message ?: "brak uprawnień"}"))
        }
    }

    private fun endCall() {
        if (Build.VERSION.SDK_INT >= 28 && Perms.has(ctx, arrayOf(android.Manifest.permission.ANSWER_PHONE_CALLS))) {
            @Suppress("DEPRECATION") telecom.endCall()
        }
    }

    // ------------------------------------------------------------------ contacts & recents

    fun pushAll() = h.post { publish(); sendContacts(); sendRecents() }

    private fun sendContacts() {
        if (!Perms.has(ctx, Perms.CONTACTS)) return
        val fav = JSONArray(); val all = JSONArray()
        val seen = HashSet<String>()
        val favIds = ArrayList<Long>()
        try {
            ctx.contentResolver.query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(ContactsContract.CommonDataKinds.Phone.CONTACT_ID, ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.NUMBER, ContactsContract.CommonDataKinds.Phone.TYPE,
                    ContactsContract.CommonDataKinds.Phone.LABEL, ContactsContract.CommonDataKinds.Phone.STARRED,
                    ContactsContract.CommonDataKinds.Phone.PHOTO_THUMBNAIL_URI),
                null, null, ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " COLLATE LOCALIZED ASC")?.use { c ->
                while (c.moveToNext() && all.length() < 3000) {
                    val id = c.getLong(0); val n = c.getString(1) ?: continue; val num = c.getString(2) ?: continue
                    val norm = num.filter { it.isDigit() || it == '+' }.takeLast(9)
                    if (!seen.add("$id|$norm")) continue
                    val lbl = ContactsContract.CommonDataKinds.Phone.getTypeLabel(ctx.resources, c.getInt(3), c.getString(4)).toString().lowercase()
                    val hasPhoto = c.getString(6) != null
                    val o = obj("n" to n, "num" to num, "lbl" to lbl, "ph" to (if (hasPhoto) "c$id" else null))
                    all.put(o)
                    if (c.getInt(5) == 1 && fav.length() < 15) { fav.put(o); if (hasPhoto) favIds.add(id) }
                }
            }
        } catch (_: Throwable) {}
        CarLink.send(pkt(Proto.CONTACTS, "fav" to fav, "all" to all))
        favIds.distinct().forEach { sendPhoto(it, "c$it") }
    }

    private val sentPhotos = HashSet<String>()

    private fun sendPhoto(contactId: Long, key: String) {
        if (!sentPhotos.add(key + (CarLink.link?.hashCode() ?: 0))) return
        try {
            val uri = ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, contactId)
            ContactsContract.Contacts.openContactPhotoInputStream(ctx.contentResolver, uri, true)?.use { s ->
                val bmp = Img.decode(s.readBytes(), 240) ?: return
                CarLink.send(pkt(Proto.PHOTO, "key" to key, body = Img.jpeg(bmp, 200, 85)))
            }
        } catch (_: Throwable) {}
    }

    private fun sendRecents() {
        if (!Perms.has(ctx, Perms.CALL_LOG)) return
        val items = JSONArray()
        try {
            ctx.contentResolver.query(CallLog.Calls.CONTENT_URI,
                arrayOf(CallLog.Calls.CACHED_NAME, CallLog.Calls.NUMBER, CallLog.Calls.TYPE, CallLog.Calls.DATE),
                null, null, CallLog.Calls.DATE + " DESC")?.use { c ->
                while (c.moveToNext() && items.length() < 60) {
                    items.put(obj("n" to (c.getString(0) ?: ""), "num" to (c.getString(1) ?: ""), "type" to c.getInt(2), "time" to c.getLong(3)))
                }
            }
        } catch (_: Throwable) {}
        CarLink.send(pkt(Proto.RECENTS, "items" to items))
    }
}
