package com.arete.app.alerts

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.arete.app.MainActivity
import com.arete.app.R

/** Notificaciones locales del sistema: funcionan sin internet (RF-11, RF-21). */
class NotificationAlertPresenter(context: Context) : AlertPresenter {
    private val context = context.applicationContext
    private val manager = NotificationManagerCompat.from(this.context)

    /** Se llama al iniciar la app; crear un canal que ya existe no hace nada. */
    fun createChannel() {
        val channel = NotificationChannel(CHANNEL_ID, "Avisos del rancho", NotificationManager.IMPORTANCE_DEFAULT)
            .apply { description = "Partos próximos o atrasados y destetes pendientes" }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    override fun show(alert: Alert): Boolean {
        if (!canNotify()) return false
        // Tocar el aviso abre la app. Abrir "Confirmar destete" directo (CA-11.4) es de HU-35.
        // Cada aviso lleva su propio requestCode para que, cuando lleve datos de su cría,
        // FLAG_UPDATE_CURRENT no los reemplace por los del último aviso.
        val open = PendingIntent.getActivity(
            context, alert.key.hashCode(),
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_aviso)
            .setContentTitle(alert.title)
            .setContentText(alert.body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(alert.body))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        return try {
            manager.notify(alert.key, NOTIFICATION_ID, notification)
            true
        } catch (_: SecurityException) {
            false   // el permiso se retiró entre la revisión y el aviso
        }
    }

    override fun cancel(key: String) = manager.cancel(key, NOTIFICATION_ID)

    private fun canNotify(): Boolean {
        if (!manager.areNotificationsEnabled()) return false
        // Android 13+ pide permiso explícito para notificar.
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    }

    companion object {
        const val CHANNEL_ID = "avisos_rancho"
        // Cada aviso se distingue por su tag (Alert.key); el id es el mismo para todos.
        private const val NOTIFICATION_ID = 1
    }
}
