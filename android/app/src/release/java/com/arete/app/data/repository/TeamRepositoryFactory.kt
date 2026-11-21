package com.arete.app.data.repository

import android.content.Context
import com.arete.app.data.local.TokenStorage

object TeamRepositoryFactory {
    @Suppress("UNUSED_PARAMETER")
    fun create(context: Context, tokens: TokenStorage): TeamRepository = UnavailableTeamRepository()
}
