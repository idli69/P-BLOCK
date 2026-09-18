package com.pblock.app.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SecureKeyManagerTest {

    @Test
    fun testEncryptDecrypt() {
        val manager = SecureKeyManager()
        val originalCode = "12345678"
        
        val encrypted = manager.encryptPartnerCode(originalCode)
        assertNotEquals(originalCode, encrypted)
        
        val isValid = manager.verifyPartnerCode(encrypted, originalCode)
        assertTrue(isValid)
        
        val isInvalid = manager.verifyPartnerCode(encrypted, "87654321")
        assertFalse(isInvalid)
    }
}
