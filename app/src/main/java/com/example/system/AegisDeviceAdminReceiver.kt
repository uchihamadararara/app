package com.example.system

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.example.R
import com.example.data.AegisDatabase
import com.example.data.SecurityLogEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * System-level Device Administrator Receiver.
 * When active, Android OS prevents Aegis Lock from being uninstalled until
 * Device Administrator privileges are explicitly revoked.
 */
class AegisDeviceAdminReceiver : DeviceAdminReceiver() {

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        Toast.makeText(
            context,
            "Aegis Uninstall Protection (Device Admin) Activated",
            Toast.LENGTH_SHORT
        ).show()
        logAdminEvent(
            context = context,
            eventType = "ADMIN_SHIELD",
            details = "Device Administrator enabled. App uninstallation is now blocked at OS level."
        )
    }

    override fun onDisableRequested(context: Context, intent: Intent): CharSequence {
        return context.getString(R.string.device_admin_disable_warning)
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        Toast.makeText(
            context,
            "Aegis Device Admin Deactivated",
            Toast.LENGTH_SHORT
        ).show()
        logAdminEvent(
            context = context,
            eventType = "ADMIN_SHIELD",
            details = "Device Administrator privilege was revoked in system settings."
        )
    }

    private fun logAdminEvent(context: Context, eventType: String, details: String) {
        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                val dao = AegisDatabase.getInstance(context).aegisDao()
                dao.insertSecurityLog(
                    SecurityLogEntity(
                        eventType = eventType,
                        targetAppName = "Device Administrator",
                        targetPackage = context.packageName,
                        authMethod = "SYSTEM",
                        details = details
                    )
                )
            }
        }
    }
}
