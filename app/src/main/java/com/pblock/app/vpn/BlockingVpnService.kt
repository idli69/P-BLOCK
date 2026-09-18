package com.pblock.app.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import com.pblock.app.MainActivity
import com.pblock.app.data.BlocklistLoader
import com.pblock.app.data.PreferencesManager
import com.pblock.app.domain.FilterEngine
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.nio.ByteBuffer

class BlockingVpnService : VpnService() {
    companion object {
        private const val TAG = "BlockingVpnService"
        const val UPSTREAM_DNS = "8.8.8.8"
    }

    private var vpnInterface: ParcelFileDescriptor? = null
    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())
    
    private lateinit var prefs: PreferencesManager
    private lateinit var filterEngine: FilterEngine
    private lateinit var blocklistLoader: BlocklistLoader
    
    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())

    override fun onCreate() {
        super.onCreate()
        prefs = PreferencesManager(this)
        blocklistLoader = BlocklistLoader(this)
        filterEngine = FilterEngine().apply {
            val data = blocklistLoader.loadSampleBlocklist()
            loadBlocklist(data.domains)
            loadKeywords(data.keywords)
        }
        
        // Fetch massive blocklist via OTA in background
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            val otaDomains = blocklistLoader.downloadOisdBlocklist()
            if (otaDomains.isNotEmpty()) {
                filterEngine.loadBlocklist(otaDomains) // Overwrites sample list with 100k+ real domains
            }
        }
        
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        serviceScope.launch {
            if (prefs.isProtected.first()) {
                startVpn()
            } else {
                stopSelf()
            }
        }
        return START_STICKY
    }

    private fun startVpn() {
        if (vpnInterface != null) return

        val builder = Builder()
            .addAddress("10.0.0.2", 32)
            .addDnsServer("10.0.0.2") // Route DNS to TUN
            .addRoute("10.0.0.2", 32) // Only route DNS to our TUN
            .setSession("P-BLOCK")
            .setBlocking(true)

        // Only intercept DNS by using dummy route, wait, if we only route 10.0.0.2, then ONLY traffic to 10.0.0.2 goes to TUN.
        // And since we set DNS to 10.0.0.2, Android will send DNS queries to 10.0.0.2!
        // This is a common Android VPN trick to intercept only DNS.
        
        vpnInterface = builder.establish()

        startForeground(1, createNotification())

        vpnInterface?.let { vpn ->
            serviceScope.launch {
                val inputStream = FileInputStream(vpn.fileDescriptor)
                val outputStream = FileOutputStream(vpn.fileDescriptor)
                val buffer = ByteArray(32767)

                while (isActive) {
                    try {
                        val length = inputStream.read(buffer)
                        if (length > 0) {
                            val packet = buffer.copyOf(length)
                            handleIpPacket(packet, outputStream)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error reading from TUN", e)
                        break
                    }
                }
            }
        }
    }

    private suspend fun handleIpPacket(packet: ByteArray, out: FileOutputStream) {
        if (packet.size < 20) return
        val version = (packet[0].toInt() shr 4) and 0x0F
        if (version != 4) return // IPv4 only

        val protocol = packet[9].toInt()
        if (protocol == 17) { // UDP
            val ipHeaderLen = (packet[0].toInt() and 0x0F) * 4
            if (packet.size < ipHeaderLen + 8) return

            val srcIp = packet.copyOfRange(12, 16)
            val destIp = packet.copyOfRange(16, 20)
            
            val udpSrcPort = ((packet[ipHeaderLen].toInt() and 0xFF) shl 8) or (packet[ipHeaderLen + 1].toInt() and 0xFF)
            val udpDestPort = ((packet[ipHeaderLen + 2].toInt() and 0xFF) shl 8) or (packet[ipHeaderLen + 3].toInt() and 0xFF)

            if (udpDestPort == 53) {
                val dnsPayload = packet.copyOfRange(ipHeaderLen + 8, packet.size)
                handleDnsQuery(dnsPayload, srcIp, destIp, udpSrcPort, udpDestPort, out)
            }
        } else if (protocol == 6) { // TCP
            // We just drop TCP DNS queries for simplicity, forcing clients to fallback to UDP.
            // Or return a TCP RST. For minimal implementation, dropping is enough.
        }
    }

    private suspend fun handleDnsQuery(
        dnsPayload: ByteArray,
        srcIp: ByteArray, destIp: ByteArray,
        srcPort: Int, destPort: Int,
        out: FileOutputStream
    ) {
        val hostname = extractDnsQueryName(dnsPayload) ?: return
        
        val isBlocked = filterEngine.shouldBlock(hostname)
        prefs.incrementQueries(isBlocked)
        
        if (isBlocked) {
            blocklistLoader.logEvent("Blocked: $hostname")
            mainHandler.post {
                android.widget.Toast.makeText(this@BlockingVpnService, "Shield Active: Blocked $hostname", android.widget.Toast.LENGTH_SHORT).show()
            }
            // Send NXDOMAIN
            val response = buildNxDomainResponse(dnsPayload)
            sendUdpResponse(response, srcIp, destIp, srcPort, destPort, out)
        } else {
            // Forward to upstream
            val response = forwardDnsQuery(dnsPayload)
            if (response != null) {
                sendUdpResponse(response, srcIp, destIp, srcPort, destPort, out)
            }
        }
    }

    private suspend fun forwardDnsQuery(payload: ByteArray): ByteArray? = withContext(Dispatchers.IO) {
        try {
            val socket = DatagramSocket()
            socket.soTimeout = 3000
            val address = InetAddress.getByName(UPSTREAM_DNS)
            val packet = DatagramPacket(payload, payload.size, address, 53)
            socket.send(packet)

            val receiveData = ByteArray(1024)
            val receivePacket = DatagramPacket(receiveData, receiveData.size)
            socket.receive(receivePacket)
            socket.close()
            return@withContext receiveData.copyOf(receivePacket.length)
        } catch (e: Exception) {
            null
        }
    }

    private fun extractDnsQueryName(payload: ByteArray): String? {
        try {
            if (payload.size < 12) return null
            var offset = 12
            val sb = java.lang.StringBuilder()
            while (offset < payload.size) {
                val len = payload[offset].toInt() and 0xFF
                if (len == 0) break
                if (sb.isNotEmpty()) sb.append(".")
                offset++
                for (i in 0 until len) {
                    sb.append(payload[offset + i].toInt().toChar())
                }
                offset += len
            }
            return sb.toString()
        } catch (e: Exception) {
            return null
        }
    }

    private fun buildNxDomainResponse(query: ByteArray): ByteArray {
        val response = query.copyOf()
        if (response.size >= 4) {
            response[2] = 0x81.toByte() // Standard query response, No error
            response[3] = 0x83.toByte() // Recursion available, NXDOMAIN (3)
        }
        return response
    }

    private fun sendUdpResponse(
        payload: ByteArray,
        origSrcIp: ByteArray, origDestIp: ByteArray,
        origSrcPort: Int, origDestPort: Int,
        out: FileOutputStream
    ) {
        val totalLength = 20 + 8 + payload.size
        val packet = ByteArray(totalLength)
        val bb = ByteBuffer.wrap(packet)

        // IPv4 Header
        bb.put(0x45.toByte()) // Version and IHL
        bb.put(0.toByte())    // TOS
        bb.putShort(totalLength.toShort()) // Total length
        bb.putShort(0.toShort()) // ID
        bb.putShort(0.toShort()) // Flags & Fragment Offset
        bb.put(64.toByte())   // TTL
        bb.put(17.toByte())   // Protocol (UDP)
        bb.putShort(0.toShort()) // Checksum (0 for now)
        bb.put(origDestIp)    // Source IP (Swapped)
        bb.put(origSrcIp)     // Dest IP (Swapped)
        
        // Calculate IP checksum
        val ipChecksum = calculateChecksum(packet, 0, 20)
        packet[10] = (ipChecksum shr 8).toByte()
        packet[11] = ipChecksum.toByte()

        // UDP Header
        bb.position(20)
        bb.putShort(origDestPort.toShort()) // Source Port (Swapped)
        bb.putShort(origSrcPort.toShort())  // Dest Port (Swapped)
        bb.putShort((8 + payload.size).toShort()) // Length
        bb.putShort(0.toShort()) // Checksum (optional in IPv4, keep 0)

        // Payload
        bb.put(payload)

        try {
            out.write(packet)
        } catch (e: Exception) {
            Log.e(TAG, "Error writing to TUN", e)
        }
    }

    private fun calculateChecksum(buf: ByteArray, offset: Int, length: Int): Int {
        var sum = 0
        var i = offset
        while (i < offset + length - 1) {
            sum += ((buf[i].toInt() and 0xFF) shl 8) or (buf[i + 1].toInt() and 0xFF)
            i += 2
        }
        if (length % 2 != 0) {
            sum += (buf[offset + length - 1].toInt() and 0xFF) shl 8
        }
        while ((sum shr 16) > 0) {
            sum = (sum and 0xFFFF) + (sum shr 16)
        }
        return sum.inv() and 0xFFFF
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "vpn_channel",
                "VPN Protection",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        val builder = Notification.Builder(this, "vpn_channel")

        return builder
            .setContentTitle("P-BLOCK is Active")
            .setContentText("DNS filtering is enabled.")
            .setContentIntent(pendingIntent)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        vpnInterface?.close()
        vpnInterface = null
    }
}
