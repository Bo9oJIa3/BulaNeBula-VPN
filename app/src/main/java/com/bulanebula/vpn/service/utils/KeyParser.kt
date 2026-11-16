package com.bulanebula.vpn.utils

import java.net.URLDecoder

data class VpnConfig(
    val protocol: String,
    val host: String,
    val port: Int,
    val uuid: String?,
    val name: String
)

object KeyParser {
    fun parse(uriString: String): VpnConfig? {
        return try {
            val cleanUri = uriString.trim()
            if (!cleanUri.contains("://")) return null
            val parts = cleanUri.split("://", limit = 2)
            val protocol = parts[0].lowercase()
            val rest = parts[1]

            val nameParts = rest.split("#", limit = 2)
            val name = if (nameParts.size > 1 && nameParts[1].isNotBlank()) {
                URLDecoder.decode(nameParts[1], "UTF-8")
            } else {
                "Народный узел"
            }
            val mainPart = nameParts[0]

            val atParts = mainPart.split("@", limit = 2)
            val uuid = if (atParts.size > 1) atParts[0] else null
            val addressPart = if (atParts.size > 1) atParts[1] else atParts[0]

            val colonParts = addressPart.split(":", limit = 2)
            val host = colonParts[0]
            if (host.isBlank()) return null

            val port = if (colonParts.size > 1) {
                colonParts[1].substringBefore("/").substringBefore("?").toIntOrNull() ?: 443
            } else {
                443
            }

            VpnConfig(protocol, host, port, uuid, name)
        } catch (e: Exception) {
            null
        }
    }
}
