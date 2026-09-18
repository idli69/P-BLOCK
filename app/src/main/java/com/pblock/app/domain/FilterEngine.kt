package com.pblock.app.domain

class FilterEngine {
    private val blocklist = mutableSetOf<String>()
    private val allowlist = mutableSetOf<String>()

    fun loadBlocklist(domains: List<String>) {
        blocklist.clear()
        blocklist.addAll(domains.map { it.lowercase() })
    }

    fun loadAllowlist(domains: List<String>) {
        allowlist.clear()
        allowlist.addAll(domains.map { it.lowercase() })
    }
    
    fun addAllowlistDomain(domain: String) {
        allowlist.add(domain.lowercase())
    }

    // Returns true if the query should be blocked
    fun shouldBlock(hostname: String): Boolean {
        val query = hostname.lowercase()

        // 1. Check Allowlist (exact or suffix match)
        if (allowlist.any { query == it || query.endsWith(".$it") }) {
            return false
        }

        // 2. Check Blocklist (exact or suffix match)
        if (blocklist.any { query == it || query.endsWith(".$it") }) {
            return true
        }

        // 3. Default allow
        return false
    }
}
