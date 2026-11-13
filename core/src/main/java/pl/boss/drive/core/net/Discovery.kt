package pl.boss.drive.core.net

import android.util.Log
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.SocketTimeoutException

/** UDP discovery on the local network (phone hotspot or shared Wi-Fi). Zero config. */
object Discovery {
    private const val TAG = "Discovery"

    fun broadcastAddresses(): List<InetAddress> {
        val out = LinkedHashSet<InetAddress>()
        try {
            for (ni in NetworkInterface.getNetworkInterfaces()) {
                if (!ni.isUp || ni.isLoopback) continue
                for (ia in ni.interfaceAddresses) {
                    val b = ia.broadcast ?: continue
                    if (ia.address is Inet4Address) out.add(b)
                }
            }
        } catch (_: Throwable) {}
        out.add(InetAddress.getByName("255.255.255.255"))
        return out.toList()
    }

    fun localIpv4(): List<String> {
        val out = ArrayList<String>()
        try {
            for (ni in NetworkInterface.getNetworkInterfaces()) {
                if (!ni.isUp || ni.isLoopback) continue
                for (a in ni.inetAddresses) if (a is Inet4Address && !a.isLoopbackAddress) out.add(a.hostAddress ?: continue)
            }
        } catch (_: Throwable) {}
        return out
    }

    data class Beacon(val ip: String, val port: Int, val id: String, val name: String)

    fun encode(id: String, name: String, port: Int) = "${Proto.MAGIC}|$id|${name.replace('|', ' ')}|$port".toByteArray()

    fun decode(data: ByteArray, len: Int, from: InetAddress): Beacon? {
        val parts = String(data, 0, len).split('|')
        if (parts.size < 4 || parts[0] != Proto.MAGIC) return null
        return Beacon(from.hostAddress ?: return null, parts[3].toIntOrNull() ?: return null, parts[1], parts[2])
    }

    /** Head-unit side: announce ourselves and answer probes. */
    class Announcer(private val id: String, private val name: () -> String) {
        @Volatile private var running = false
        @Volatile var paused = false
        private var probeSock: DatagramSocket? = null

        fun start() {
            if (running) return
            running = true
            Thread({ announceLoop() }, "beacon").apply { isDaemon = true; priority = Thread.MIN_PRIORITY }.start()
            Thread({ probeLoop() }, "probe-rx").apply { isDaemon = true; priority = Thread.MIN_PRIORITY }.start()
        }

        fun stop() { running = false; try { probeSock?.close() } catch (_: Throwable) {} }

        private fun announceLoop() {
            var sock: DatagramSocket? = null
            while (running) {
                try {
                    if (!paused) {
                        if (sock == null) sock = DatagramSocket().apply { broadcast = true }
                        val data = encode(id, name(), Proto.PORT_TCP)
                        for (a in broadcastAddresses()) {
                            try { sock.send(DatagramPacket(data, data.size, a, Proto.PORT_BEACON)) } catch (_: Throwable) {}
                        }
                    }
                } catch (e: Throwable) {
                    Log.w(TAG, "beacon: ${e.message}"); try { sock?.close() } catch (_: Throwable) {}; sock = null
                }
                try { Thread.sleep(if (paused) 3000 else 2000) } catch (_: InterruptedException) {}
            }
            try { sock?.close() } catch (_: Throwable) {}
        }

        private fun probeLoop() {
            val buf = ByteArray(512)
            while (running) {
                try {
                    val s = DatagramSocket(null).apply { reuseAddress = true; broadcast = true; bind(InetSocketAddress(Proto.PORT_PROBE)) }
                    probeSock = s
                    while (running) {
                        val pk = DatagramPacket(buf, buf.size)
                        s.receive(pk)
                        if (String(buf, 0, pk.length).startsWith(Proto.MAGIC)) {
                            val data = encode(id, name(), Proto.PORT_TCP)
                            s.send(DatagramPacket(data, data.size, pk.address, Proto.PORT_BEACON))
                        }
                    }
                } catch (e: Throwable) {
                    if (running) try { Thread.sleep(3000) } catch (_: InterruptedException) {}
                }
            }
        }
    }

    /** Phone side: listen for beacons and send probes. */
    class Finder(private val onFound: (Beacon) -> Unit) {
        @Volatile private var running = false
        private var sock: DatagramSocket? = null

        fun start() {
            if (running) return
            running = true
            Thread({ loop() }, "finder").apply { isDaemon = true; priority = Thread.MIN_PRIORITY }.start()
        }

        fun stop() { running = false; try { sock?.close() } catch (_: Throwable) {} }

        private fun loop() {
            val buf = ByteArray(512)
            val probe = "${Proto.MAGIC}|probe".toByteArray()
            while (running) {
                try {
                    val s = DatagramSocket(null).apply {
                        reuseAddress = true; broadcast = true; soTimeout = 2500; bind(InetSocketAddress(Proto.PORT_BEACON))
                    }
                    sock = s
                    var lastProbe = 0L
                    while (running) {
                        val now = System.currentTimeMillis()
                        if (now - lastProbe > 2500) {
                            lastProbe = now
                            for (a in broadcastAddresses()) try { s.send(DatagramPacket(probe, probe.size, a, Proto.PORT_PROBE)) } catch (_: Throwable) {}
                        }
                        try {
                            val pk = DatagramPacket(buf, buf.size)
                            s.receive(pk)
                            decode(buf, pk.length, pk.address)?.let(onFound)
                        } catch (_: SocketTimeoutException) {}
                    }
                    s.close()
                } catch (e: Throwable) {
                    if (running) try { Thread.sleep(2000) } catch (_: InterruptedException) {}
                }
            }
        }
    }
}
