package pl.boss.drive.phone

import android.app.Notification
import android.app.RemoteInput
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Bundle
import android.os.Parcelable
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import pl.boss.drive.core.net.Proto
import pl.boss.drive.core.net.pkt
import java.util.Locale

/**
 * Notification access gives us two things:
 *  1. permission to read/control every media session (MediaBridge),
 *  2. incoming messages (SMS, WhatsApp, Messenger, Telegram…) with their reply actions.
 */
class NotifListener : NotificationListenerService() {

    override fun onListenerConnected() {
        instance = this
        CarLink.init(this)
        if (CarLink.running) CarLink.media.refresh()
        Events.emit(Ev.PERMS)
    }

    override fun onListenerDisconnected() { if (instance === this) instance = null }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (!CarLink.running || !CarLink.prefs.getBoolean("forwardMessages", true)) return
        val m = parse(sbn) ?: return
        synchronized(recent) {
            val prev = recent[m.key]
            if (prev != null && prev.text == m.text) return     // same content re-posted (WhatsApp does this a lot)
            recent[m.key] = m
            while (recent.size > 40) recent.remove(recent.keys.first())
        }
        send(m, silent = false)
    }

    data class Msg(val key: String, val pkg: String, val app: String, val title: String, val text: String, val time: Long,
                   val reply: Notification.Action?, val markRead: Notification.Action?)

    private fun parse(sbn: StatusBarNotification): Msg? {
        val n = sbn.notification ?: return null
        if (sbn.packageName == packageName) return null
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return null
        if (n.flags and Notification.FLAG_ONGOING_EVENT != 0) return null
        val isMsg = n.category == Notification.CATEGORY_MESSAGE || sbn.packageName in MESSENGERS
        if (!isMsg) return null
        val ex = n.extras
        var title = ex.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)?.toString()
            ?: ex.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: return null
        var text = ex.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
        // MessagingStyle: use the newest message and its sender
        val msgs: Array<Parcelable>? = ex.getParcelableArray(Notification.EXTRA_MESSAGES)
        if (msgs != null && msgs.isNotEmpty()) {
            val last = msgs.last() as? Bundle
            last?.getCharSequence("text")?.toString()?.let { text = it }
            val sender = last?.getCharSequence("sender")?.toString()
            if (sender != null && ex.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE) != null) title = "$title: $sender"
        }
        if (text.isBlank()) return null
        val actions = n.actions ?: emptyArray()
        val reply = actions.firstOrNull { it.semanticAction == Notification.Action.SEMANTIC_ACTION_REPLY && !it.remoteInputs.isNullOrEmpty() }
            ?: actions.firstOrNull { !it.remoteInputs.isNullOrEmpty() }
        val read = actions.firstOrNull { it.semanticAction == Notification.Action.SEMANTIC_ACTION_MARK_AS_READ }
        val app = try { packageManager.getApplicationLabel(packageManager.getApplicationInfo(sbn.packageName, 0)).toString() } catch (_: Throwable) { sbn.packageName }
        return Msg(sbn.key, sbn.packageName, app, title, text.take(600), sbn.postTime, reply, read)
    }

    companion object {
        @Volatile var instance: NotifListener? = null
        private val recent = LinkedHashMap<String, Msg>()
        private var tts: TextToSpeech? = null
        private var ttsReady = false
        private val pendingSpeech = ArrayList<String>()

        val MESSENGERS = setOf(
            "com.whatsapp", "com.whatsapp.w4b", "com.facebook.orca", "com.facebook.mlite", "org.telegram.messenger",
            "com.google.android.apps.messaging", "com.samsung.android.messaging", "com.android.mms", "com.viber.voip",
            "org.thoughtcrime.securesms", "com.discord", "com.instagram.android", "com.snapchat.android", "com.microsoft.teams",
            "com.Slack", "com.skype.raider", "jp.naver.line.android", "com.oneplus.mms", "com.miui.smsextra",
        )

        private fun send(m: Msg, silent: Boolean) {
            CarLink.send(pkt(Proto.MESSAGE, "key" to m.key, "pkg" to m.pkg, "app" to m.app, "title" to m.title, "text" to m.text,
                "time" to m.time, "canReply" to (m.reply != null), "silent" to silent))
        }

        fun pushRecent() {
            val list = synchronized(recent) { recent.values.toList() }
            list.takeLast(15).forEach { send(it, silent = true) }
        }

        fun command(ctx: Context, key: String, a: String, text: String) {
            val m = synchronized(recent) { recent[key] } ?: return
            when (a) {
                "read" -> speak(ctx, "Wiadomość od ${m.title}. ${m.text}")
                "reply" -> {
                    val act = m.reply ?: return
                    try {
                        val intent = Intent()
                        val b = Bundle()
                        for (ri in act.remoteInputs) b.putCharSequence(ri.resultKey, text)
                        RemoteInput.addResultsToIntent(act.remoteInputs, intent, b)
                        act.actionIntent.send(ctx, 0, intent)
                        CarLink.send(pkt(Proto.TOAST, "text" to "Wysłano do: ${m.title}"))
                    } catch (e: Throwable) {
                        CarLink.send(pkt(Proto.TOAST, "text" to "Nie udało się wysłać odpowiedzi"))
                    }
                }
                "markRead" -> try { m.markRead?.actionIntent?.send() } catch (_: Throwable) {}
            }
        }

        /** Read aloud through the car speakers (Bluetooth), ducking the music like navigation prompts do. */
        fun speak(ctx: Context, text: String) {
            val app = ctx.applicationContext
            val t = tts
            if (t == null) {
                pendingSpeech.add(text)
                tts = TextToSpeech(app) { status ->
                    ttsReady = status == TextToSpeech.SUCCESS
                    tts?.language = Locale("pl", "PL")
                    tts?.setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                    tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(id: String?) {}
                        override fun onDone(id: String?) { abandonFocus(app) }
                        @Deprecated("Deprecated in Java") override fun onError(id: String?) { abandonFocus(app) }
                    })
                    val q = ArrayList(pendingSpeech); pendingSpeech.clear()
                    q.forEach { s -> say(app, s) }
                }
                return
            }
            if (!ttsReady) { pendingSpeech.add(text); return }
            say(app, text)
        }

        private var focus: AudioFocusRequest? = null

        private fun say(ctx: Context, text: String) {
            val am = ctx.getSystemService(AudioManager::class.java)
            val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE).build()).build()
            focus = req
            am.requestAudioFocus(req)
            tts?.speak(text, TextToSpeech.QUEUE_ADD, null, "m" + System.currentTimeMillis())
        }

        private fun abandonFocus(ctx: Context) {
            if (tts?.isSpeaking == true) return
            focus?.let { ctx.getSystemService(AudioManager::class.java).abandonAudioFocusRequest(it) }
        }
    }
}
