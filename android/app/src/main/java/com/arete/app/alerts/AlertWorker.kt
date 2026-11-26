package com.arete.app.alerts

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.arete.app.AreteApplication
import java.util.concurrent.TimeUnit

/** Tarea diaria de avisos (CU-18). No pide red: usa los datos que ya están en el celular. */
class AlertWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = try {
        (applicationContext as AreteApplication).container.alertRunner.run()
        Result.success()
    } catch (_: Exception) {
        Result.retry()   // por ejemplo, si la fuente de datos falló; WorkManager reintenta con espera
    }
}

object AlertScheduler {
    private const val DAILY = "avisos-diarios"
    private const val NOW = "avisos-ahora"

    /** Programa la tarea una vez al día; si ya estaba programada, la deja igual. */
    fun scheduleDaily(context: Context) {
        val request = PeriodicWorkRequestBuilder<AlertWorker>(1, TimeUnit.DAYS).build()
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(DAILY, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    /** Recorre los avisos ya, por ejemplo al iniciar sesión o al conceder el permiso. */
    fun runNow(context: Context) {
        WorkManager.getInstance(context)
            .enqueueUniqueWork(NOW, ExistingWorkPolicy.REPLACE, OneTimeWorkRequestBuilder<AlertWorker>().build())
    }
}
