package com.pblock.app.vpn

import org.junit.Assert.*
import org.junit.Test

class IpUtilsTest {

    @Test
    fun testExtractDnsQueryName() {
        // Mock a DNS query packet for "example.com"
        // 12 bytes header + qname + qtype + qclass
        // qname "example.com" = 7 'e' 'x' 'a' 'm' 'p' 'l' 'e' 3 'c' 'o' 'm' 0
        val payload = ByteArray(12 + 13 + 4)
        
        // Populate header with zeros for test
        var idx = 12
        payload[idx++] = 7
        for (c in "example") payload[idx++] = c.code.toByte()
        payload[idx++] = 3
        for (c in "com") payload[idx++] = c.code.toByte()
        payload[idx++] = 0

        val hostname = IpUtils.extractDnsQueryName(payload)
        assertEquals("example.com", hostname)
    }

    @Test
    fun testCalculateChecksum() {
        val header = ByteArray(20)
        // 45 00 00 3c 1c 46 40 00 40 06 b1 e6 ac 10 0a 63 ac 10 0a 0c
        // known checksum is b1 e6 -> 0xB1E6 -> 45542
        val data = intArrayOf(
            0x45, 0x00, 0x00, 0x3c, 0x1c, 0x46, 0x40, 0x00, 
            0x40, 0x06, 0x00, 0x00, 0xac, 0x10, 0x0a, 0x63, 
            0xac, 0x10, 0x0a, 0x0c
        )
        for (i in data.indices) {
            header[i] = data[i].toByte()
        }

        val checksum = IpUtils.calculateChecksum(header, 0, 20)
        assertEquals(0xB1E6, checksum)
    }
}
