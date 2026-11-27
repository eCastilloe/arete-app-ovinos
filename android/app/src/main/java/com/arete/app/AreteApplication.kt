package com.arete.app

import android.app.Application
import com.arete.app.alerts.AlertScheduler
import com.arete.app.di.AppContainer

class AreteApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        // HU-39: el canal debe existir antes del primer aviso; la tarea corre una vez al día.
        container.alertPresenter.createChannel()
        AlertScheduler.scheduleDaily(this)
    }
}