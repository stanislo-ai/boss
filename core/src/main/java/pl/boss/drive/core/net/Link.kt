package pl.boss.drive.core.net

import android.os.SystemClock
import android.util.Log
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.Closeable
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.LinkedBlockingDeque
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * A framed, full-duplex connection over any byte stream (TCP socket or RFCOMM).
 *
 * - Control packets go through a queue and always win over bulk data,
 *   so the UI stays responsive while a song is being transferred.
 * - Bulk data (file chunks) is pulled lazily from a [BulkSource] only when
 *   the control queue is empty, so memory stays flat no matter the file size.
 * - Keep-alive ping every 4s, peer considered dead after 14s of silence.
 */
class Link(
    val transport: String,
    val peer: String,
    input: InputStream,
    output: OutputStream,
    private val closeable: Closeable,
    private val listener: Listener,
) {
    interface Listener {
        fun onPacket(link: Link, p: Packet)
        fun onClosed(link: Link)
    }

    fun interface BulkSource {
        /** Next packet to send, or null when finished. Called on the writer thread. */
        fun next(): Packet?
    }

    private val inp = DataInputStream(BufferedInputStream(input, 32 * 1024))
    private val out = DataOutputStream(BufferedOutputStream(output, 72 * 1024))
    private val queue = LinkedBlockingDeque<Packet>()
    private val open = AtomicBoolean(true)
    @Volatile private var bulk: BulkSource? = null
    @Volatile private var lastRx = SystemClock.elapsedRealtime()
    @Volatile var remoteName: String = ""
    @Volatile var remoteId: String = ""
    @Volatile var authed = false
    val isOpen get() = open.get()
    val isWifi get() = transport == "wifi"

    fun start(): Link {
        Thread({ readLoop() }, "link-rx-$transport").apply { priority = Thread.NORM_PRIORITY; isDaemon = true }.start()
        Thread({ writeLoop() }, "link-tx-$transport").apply { priority = Thread.NORM_PRIORITY - 1; isDaemon = true }.start()
        return this
    }

    fun send(p: Packet) { if (open.get()) queue.offer(p) }

    fun sendFirst(p: Packet) { if (open.get()) queue.offerFirst(p) }

    fun setBulk(source: BulkSource?) {
        bulk = source
        queue.offer(WAKE)
    }

    val busyWithBulk get() = bulk != null

    private fun readLoop() {
        try {
            while (open.get()) {
                val p = Packet.read(inp)
                lastRx = SystemClock.elapsedRealtime()
                if (p.type == Proto.PING) continue
                listener.onPacket(this, p)
            }
        } catch (e: Throwable) {
            if (open.get()) Log.i(TAG, "rx end ($transport): ${e.message}")
        }
        close()
    }

    private fun writeLoop() {
        var lastTx = SystemClock.elapsedRealtime()
        try {
            while (open.get()) {
                var p: Packet? = queue.poll()
                if (p == null) {
                    val b = bulk
                    if (b != null) {
                        p = b.next()
                        if (p == null) { if (bulk === b) bulk = null; continue }
                    } else {
                        p = queue.poll(1, TimeUnit.SECONDS)
                    }
                }
                val now = SystemClock.elapsedRealtime()
                if (now - lastRx > DEAD_MS) throw java.io.IOException("peer timeout")
                if (p == null) {
                    if (now - lastTx > PING_MS) { Packet.write(out, Packet(Proto.PING)); lastTx = now }
                    continue
                }
                if (p === WAKE) continue
                Packet.write(out, p)
                lastTx = now
            }
        } catch (e: Throwable) {
            if (open.get()) Log.i(TAG, "tx end ($transport): ${e.message}")
        }
        close()
    }

    fun close() {
        if (!open.compareAndSet(true, false)) return
        bulk = null
        queue.clear()
        queue.offer(WAKE)
        try { closeable.close() } catch (_: Throwable) {}
        listener.onClosed(this)
    }

    companion object {
        private const val TAG = "Link"
        private const val PING_MS = 4_000L
        private const val DEAD_MS = 14_000L
        private val WAKE = Packet("_wake")
    }
}
