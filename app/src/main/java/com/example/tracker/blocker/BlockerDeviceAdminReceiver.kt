package com.example.tracker.blocker

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast

class BlockerDeviceAdminReceiver : DeviceAdminReceiver() {

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        Toast.makeText(context, com.example.tracker.ui.i18n.tr("Anti-uninstall protection enabled"), Toast.LENGTH_SHORT).show()
    }

    override fun onDisableRequested(context: Context, intent: Intent): CharSequence {
        return "Disabling Device Admin will remove Dinrekha anti-cheat protection against premature uninstalls."
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        Toast.makeText(context, com.example.tracker.ui.i18n.tr("Anti-uninstall protection disabled"), Toast.LENGTH_SHORT).show()
    }
}
