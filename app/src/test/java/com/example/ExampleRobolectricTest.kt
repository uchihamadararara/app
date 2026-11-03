package com.example

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.example.data.AegisDatabase
import com.example.data.AegisRepository
import com.example.data.SecurityPreferencesManager
import com.example.system.AegisNotificationListenerService
import com.example.system.AppLockMonitorService
import com.example.system.BootCompletedReceiver
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `verify custom lock setup, 5-attempt 30s cooldown, reboot persistence, notification masking, and intruder logs`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Aegis Lock", appName)

        // 1. Verify no default password on first launch
        val prefs = SecurityPreferencesManager(context)
        prefs.clearAllCredentialsForReSetup()
        assertFalse(prefs.state.value.isSetupCompleted)

        // 2. Test 4-digit PIN, 6-digit PIN, Pattern, and Mixed Password
        prefs.setupOrUpdatePin("1234", 4)
        assertTrue(prefs.state.value.isSetupCompleted)
        assertTrue(prefs.verifyPin("1234"))
        assertFalse(prefs.verifyPin("9999"))

        prefs.setupOrUpdatePin("258025", 6)
        assertEquals(6, prefs.state.value.pinLength)
        assertTrue(prefs.verifyPin("258025"))

        prefs.setupOrUpdatePattern(listOf(0, 1, 2, 5, 8))
        assertTrue(prefs.verifyPattern(listOf(0, 1, 2, 5, 8)))
        assertFalse(prefs.verifyPattern(listOf(0, 3, 6, 7)))

        prefs.setupOrUpdateMixedPassword("Rahul@123")
        assertTrue(prefs.verifyMixedPassword("Rahul@123"))

        // 3. Test 5 failed attempts -> exact 30-second (30,000ms) cooldown
        val baseTime = 1_700_000_000_000L
        for (i in 1..4) {
            val (_, cooldownTriggered) = prefs.recordFailedAttempt(baseTime)
            assertFalse(cooldownTriggered)
        }
        val (fifthCount, fifthCooldown) = prefs.recordFailedAttempt(baseTime)
        assertEquals(5, fifthCount)
        assertTrue(fifthCooldown)
        assertEquals(baseTime + 30_000L, prefs.state.value.cooldownUntilEpochMs)

        // 4. Test Reboot Persistence via BootCompletedReceiver
        val bootReceiver = BootCompletedReceiver()
        bootReceiver.onReceive(context, Intent(Intent.ACTION_BOOT_COMPLETED))
        prefs.refreshState()
        assertTrue(prefs.state.value.isSetupCompleted)
        assertTrue(prefs.verifyMixedPassword("Rahul@123"))
        assertTrue(prefs.state.value.bootRestoreCount >= 1)

        // 5. Test Locked App Notification Masking ("New notification" only)
        AegisNotificationListenerService.postMaskedNewNotification(
            context = context,
            targetPackage = "com.whatsapp",
            targetAppName = "WhatsApp"
        )
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val activeNotifs = shadowOf(nm).allNotifications
        assertTrue(activeNotifs.isNotEmpty())
        val maskedNotif = activeNotifs.last()
        assertEquals("WhatsApp", maskedNotif.extras.getString("android.title"))
        assertEquals("New notification", maskedNotif.extras.getString("android.text"))

        // 6. Test Home Screen Unlock Gate state
        AppLockMonitorService.setHomeScreenGatePending(true)
        assertTrue(AppLockMonitorService.isHomeScreenGatePending())
        AppLockMonitorService.setHomeScreenGatePending(false)
        assertFalse(AppLockMonitorService.isHomeScreenGatePending())

        // 7. Test Repository & Intruder Selfie log attachment
        val db = AegisDatabase.getInstance(context)
        val repo = AegisRepository(context, db.aegisDao(), prefs)
        val (_, _, logId) = repo.logAuthEvent(
            success = false,
            targetAppName = "Phone Lock Screen",
            targetPackage = context.packageName,
            authMethod = "PIN",
            photoPath = "/data/user/0/intruder_test.jpg"
        )
        assertNotNull(logId)
    }
}
