package com.pblock.app.domain

import org.junit.Assert.*
import org.junit.Test

class FilterEngineTest {

    @Test
    fun testBlocklistMatching() {
        val engine = FilterEngine()
        engine.loadBlocklist(listOf("pornhub.com", "xvideos.com"))

        assertTrue(engine.shouldBlock("pornhub.com"))
        assertTrue(engine.shouldBlock("www.pornhub.com")) // Subdomain
        assertTrue(engine.shouldBlock("api.xvideos.com"))
        
        assertFalse(engine.shouldBlock("google.com"))
        assertFalse(engine.shouldBlock("notpornhub.com")) // Shouldn't block if not suffix match. Wait, "notpornhub.com".endsWith(".pornhub.com") is false, but equals is false.
    }

    @Test
    fun testAllowlistOverridesBlocklist() {
        val engine = FilterEngine()
        engine.loadBlocklist(listOf("example.com"))
        engine.loadAllowlist(listOf("good.example.com"))

        assertTrue(engine.shouldBlock("example.com"))
        assertTrue(engine.shouldBlock("bad.example.com"))
        assertFalse(engine.shouldBlock("good.example.com"))
    }
}
