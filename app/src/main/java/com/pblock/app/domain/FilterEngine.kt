package com.pblock.app.domain

/**
 * FilterEngine: DNS blocklist matcher.
 *
 * Uses a HashSet for O(1) exact domain lookups plus suffix-tree style matching
 * so subdomains of blocked domains are also caught (e.g. "cdn.pornhub.com"
 * when "pornhub.com" is in the blocklist).
 *
 * Allowlist takes absolute precedence over everything.
 */
class FilterEngine {
    // HashSets for O(1) average lookup
    private val blocklist = HashSet<String>(500_000)
    private val allowlist = HashSet<String>(100)
    private val keywords = HashSet<String>(200)

    fun loadBlocklist(domains: List<String>) {
        blocklist.clear()
        domains.mapTo(blocklist) { it.lowercase().trim() }
    }

    fun addToBlocklist(domain: String) {
        blocklist.add(domain.lowercase().trim())
    }

    fun loadKeywords(kws: List<String>) {
        keywords.clear()
        kws.mapTo(keywords) { it.lowercase().trim() }
    }

    fun loadAllowlist(domains: List<String>) {
        allowlist.clear()
        domains.mapTo(allowlist) { it.lowercase().trim() }
    }

    fun addAllowlistDomain(domain: String) {
        allowlist.add(domain.lowercase().trim())
    }

    /**
     * Returns true if the hostname should be blocked.
     *
     * Logic:
     * 1. Allowlist — exact or any parent domain match → ALLOW
     * 2. Blocklist — exact or any parent domain match → BLOCK
     * 3. Keywords — substring in hostname → BLOCK
     * 4. Default → ALLOW
     */
    fun shouldBlock(hostname: String): Boolean {
        val query = hostname.lowercase().trim()

        // 1. Allowlist check (exact + suffix)
        if (matchesDomainList(query, allowlist)) return false

        // 2. Blocklist check (exact + suffix)
        if (matchesDomainList(query, blocklist)) return true

        // 3. Keyword substring check
        if (keywords.any { query.contains(it) }) return true

        // 4. Default allow
        return false
    }

    /**
     * Checks if a hostname or any of its parent domains is in the given set.
     * e.g. "cdn.sub.pornhub.com" will match if "pornhub.com" is in the set.
     */
    private fun matchesDomainList(hostname: String, set: HashSet<String>): Boolean {
        if (set.contains(hostname)) return true
        // Walk up the domain hierarchy
        var dotIndex = hostname.indexOf('.')
        while (dotIndex != -1) {
            val parent = hostname.substring(dotIndex + 1)
            if (set.contains(parent)) return true
            dotIndex = hostname.indexOf('.', dotIndex + 1)
        }
        return false
    }
}
