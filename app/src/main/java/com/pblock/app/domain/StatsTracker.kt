package com.pblock.app.domain

import java.util.concurrent.ConcurrentHashMap

object StatsTracker {
    private val allowedDomains = ConcurrentHashMap<String, Int>()
    private val blockedDomains = ConcurrentHashMap<String, Int>()

    private const val MAX_TRACKED_DOMAINS = 5000

    fun recordQuery(domain: String, blocked: Boolean) {
        val map = if (blocked) blockedDomains else allowedDomains
        if (map.size > MAX_TRACKED_DOMAINS && !map.containsKey(domain)) {
            // Prevent memory leak by capping map size. Ignore new domains if full.
            return
        }
        map[domain] = (map[domain] ?: 0) + 1
    }

    fun getTopAllowed(): Map<String, Int> {
        return allowedDomains.entries
            .sortedByDescending { it.value }
            .take(10)
            .associate { it.key to it.value }
    }

    fun getTopBlocked(): Map<String, Int> {
        return blockedDomains.entries
            .sortedByDescending { it.value }
            .take(10)
            .associate { it.key to it.value }
    }
}
