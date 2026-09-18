package com.pblock.app.vpn

object IpUtils {
    fun calculateChecksum(buf: ByteArray, offset: Int, length: Int): Int {
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

    fun extractDnsQueryName(payload: ByteArray): String? {
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
}
