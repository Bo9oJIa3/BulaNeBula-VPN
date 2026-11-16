package com.bulanebula.vpn.service

import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor

class VpnConnectionService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_DISCONNECT) {
            disconnect()
            return START_NOT_STICKY
        }
        startVpn()
        return START_STICKY
    }

    private fun startVpn() {
        if (vpnInterface != null) return

        try {
            val builder = Builder()
                .addAddress("10.0.0.2", 24)
                .addRoute("0.0.0.0", 0)
                .addDnsServer("8.8.8.8")
                .setSession("BulaNeBula")
                .setMtu(1500)

            vpnInterface = builder.establish()
        } catch (e: Exception) {
            e.printStackTrace()
            disconnect()
        }
    }

    private fun disconnect() {
        try {
            vpnInterface?.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        vpnInterface = null
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        disconnect()
    }

    companion object {
        const val ACTION_DISCONNECT = "com.bulanebula.vpn.DISCONNECT"
    }
}
