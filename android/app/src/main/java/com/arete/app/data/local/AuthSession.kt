package com.arete.app.data.local

import com.arete.app.data.remote.dto.LoginResponse
import com.arete.app.data.remote.dto.CurrentUserDto
import com.arete.app.data.remote.dto.RanchAccessDto
import com.google.gson.JsonParser
import java.util.Base64
import java.util.UUID

data class AuthSession(
    val accessToken: String,
    val refreshToken: String,
    val accessExpiresAt: Long,
    val refreshExpiresAt: Long,
    val isDemo: Boolean = false,
    val userId: String? = null,
    val ranches: List<RanchAccessDto> = emptyList(),
    // Identifica un inicio de sesión, incluso cuando cambian sus tokens.
    val sessionId: String = UUID.randomUUID().toString()
) {
    override fun toString() = "AuthSession(redacted)"
    fun isValid(now: Long): Boolean = isDemo || refreshExpiresAt > now

    fun withUser(user: CurrentUserDto): AuthSession {
        require(!isDemo && user.userId == payload(accessToken)["sub"].asString)
        require(user.ranches.all { it.ranchId.isNotBlank() && it.name.isNotBlank() &&
            it.role.isNotBlank() && it.origin.isNotBlank() })
        return copy(userId = user.userId, ranches = user.ranches.toList())
    }

    companion object {
        fun demo() = AuthSession("", "", Long.MAX_VALUE, Long.MAX_VALUE, isDemo = true, sessionId = "demo")

        private fun payload(token: String) = JsonParser.parseString(
            String(Base64.getUrlDecoder().decode(token.split('.').also { require(it.size == 3) }[1]), Charsets.UTF_8)
        ).asJsonObject

        fun fromResponse(response: LoginResponse, now: Long): AuthSession {
            require(response.tokenType.equals("bearer", ignoreCase = true))
            val access = response.accessToken
            val refresh = requireNotNull(response.refreshToken)
            // Se lee la caducidad para gestionar la sesión local; la firma y los permisos los valida la API.
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
