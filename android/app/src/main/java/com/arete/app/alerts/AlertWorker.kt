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
import java.time.Duration
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
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
    private val MORNING = LocalTime.of(7, 0)
    private val JALISCO = ZoneId.of("America/Mexico_City")

    /**
     * Programa la tarea una vez al día, a partir de las 7:00 de Jalisco, para que los avisos
     * lleguen en la mañana y no a la hora en que se instaló la app. Si ya estaba programada,
     * se deja igual. WorkManager puede correrla un poco después, nunca antes.
     */
    fun scheduleDaily(context: Context) {
        val request = PeriodicWorkRequestBuilder<AlertWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(delayUntilNext(MORNING, ZonedDateTime.now(JALISCO)).toMillis(), TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(DAILY, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    /** Recorre los avisos ya, por ejemplo al iniciar sesión o al conceder el permiso. */
    fun runNow(context: Context) {
        WorkManager.getInstance(context)
            .enqueueUniqueWork(NOW, ExistingWorkPolicy.REPLACE, OneTimeWorkRequestBuilder<AlertWorker>().build())
    }

    /** Tiempo que falta para la próxima vez que el reloj marque [time] (hoy o mañana). */
    fun delayUntilNext(time: LocalTime, now: ZonedDateTime): Duration {
        var next = now.with(time).withSecond(0).withNano(0)
        if (!next.isAfter(now)) next = next.plusDays(1)
        return Duration.between(now, next)
    }
}
