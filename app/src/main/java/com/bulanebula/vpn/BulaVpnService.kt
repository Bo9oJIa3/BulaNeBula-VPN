package com.bulanebula.vpn

import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor

class BulaVpnService : VpnService() {
    private var vpnInterface: ParcelFileDescriptor? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startVpnTunnel()
        return START_STICKY
    }

    private fun startVpnTunnel() {
        try {
            val builder = Builder()
                .setSession("BulaNeBula")
                .addAddress("10.0.0.2", 24)
                .addDnsServer("8.8.8.8")
                .addRoute("0.0.0.0", 0)
            vpnInterface = builder.establish()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            vpnInterface?.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
