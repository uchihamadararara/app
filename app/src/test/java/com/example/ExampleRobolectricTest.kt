package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.SecurityPreferencesManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `verify no default lock on first launch and 5-attempt 30s cooldown persistence`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Aegis Lock", appName)

        val prefs = SecurityPreferencesManager(context)
        prefs.clearAllCredentialsForReSetup()
        assertFalse(prefs.state.value.isSetupCompleted)

        // User sets up a 6-digit PIN or Mixed Password
        prefs.setupOrUpdatePin("258025", 6)
        assertTrue(prefs.state.value.isSetupCompleted)
        assertTrue(prefs.verifyPin("258025"))
        assertFalse(prefs.verifyPin("000000"))

        prefs.setupOrUpdateMixedPassword("Rahul@123")
        assertTrue(prefs.verifyMixedPassword("Rahul@123"))

        // Record 5 failed attempts -> triggers exact 30-second (30,000ms) cooldown persisted on disk
        val baseTime = 1_700_000_000_000L
        for (i in 1..4) {
            val (_, cooldownTriggered) = prefs.recordFailedAttempt(baseTime)
            assertFalse(cooldownTriggered)
        }
        val (fifthCount, fifthCooldown) = prefs.recordFailedAttempt(baseTime)
        assertEquals(5, fifthCount)
        assertTrue(fifthCooldown)
        assertEquals(baseTime + 30_000L, prefs.state.value.cooldownUntilEpochMs)
    }
}
