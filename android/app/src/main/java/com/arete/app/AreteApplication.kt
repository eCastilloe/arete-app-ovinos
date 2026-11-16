package com.arete.app

import android.app.Application
import com.arete.app.di.AppContainer

class AreteApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}