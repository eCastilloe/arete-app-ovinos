package com.arete.app.data.local

import com.arete.app.data.remote.dto.LoginResponse
import com.google.gson.JsonParser
import java.util.Base64

data class AuthSession(
    val accessToken: String,
    val refreshToken: String,
    val accessExpiresAt: Long,
    val refreshExpiresAt: Long,
    val isDemo: Boolean = false
) {
    override fun toString() = "AuthSession(redacted)"
    fun isValid(now: Long): Boolean = isDemo || refreshExpiresAt > now

    companion object {
        fun demo() = AuthSession("", "", Long.MAX_VALUE, Long.MAX_VALUE, isDemo = true)

        fun fromResponse(response: LoginResponse, now: Long): AuthSession {
            require(response.tokenType.equals("bearer", ignoreCase = true))
            val access = response.accessToken
            val refresh = requireNotNull(response.refreshToken)
            // Se lee la caducidad para gestionar la sesión local; la firma y los permisos los valida la API.
            fun payload(token: String) = JsonParser.parseString(
                String(Base64.getUrlDecoder().decode(token.split('.').also { require(it.size == 3) }[1]), Charsets.UTF_8)
            ).asJsonObject
            val accessPayload = payload(access)
            val refreshPayload = payload(refresh)
            require(accessPayload["tipo"].asString == "access" && refreshPayload["tipo"].asString == "refresh")
            require(accessPayload["sub"].asString.isNotBlank() && accessPayload["sub"] == refreshPayload["sub"])
            val accessExpiry = Math.multiplyExact(accessPayload["exp"].asLong, 1000L)
            val refreshExpiry = Math.multiplyExact(refreshPayload["exp"].asLong, 1000L)
            require(accessExpiry > now && refreshExpiry > now)
            return AuthSession(access, refresh, accessExpiry, refreshExpiry)
        }
    }
}
