// SPDX-License-Identifier: GPL-3.0-or-later
package app.projecthaven.data.checkin

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import app.projecthaven.HavenApp
import app.projecthaven.MainActivity
import app.projecthaven.R
import app.projecthaven.data.settings.SettingsRepository
import app.projecthaven.domain.sos.CheckIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Schedules the check-in deadline with [AlarmManager]. When precise alarms are not allowed
 * (Android 12+ setting), an inexact alarm is used and the UI says the reminder may be late.
 * Nothing is ever sent automatically: the deadline only produces a notification.
 */
class CheckInScheduler(
    private val context: Context,
    private val settings: SettingsRepository,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val alarms: AlarmManager? = ContextCompat.getSystemService(context, AlarmManager::class.java)

    fun canScheduleExact(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarms?.canScheduleExactAlarms() == true

    fun notificationsAllowed(): Boolean =
        (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
            NotificationManagerCompat.from(context).areNotificationsEnabled()

    suspend fun start(durationMs: Long) {
        val now = clock()
        val deadline = CheckIn.deadline(now, durationMs)
        settings.setCheckIn(deadline, now)
        schedule(deadline)
    }

    suspend fun extend(byMs: Long) {
        val current = settings.checkIn.first()
        val deadline = CheckIn.extend(current.deadlineEpochMs ?: clock(), clock(), byMs)
        settings.setCheckIn(deadline, current.startedAtEpochMs ?: clock())
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
        schedule(deadline)
    }

    suspend fun cancel() {
        settings.setCheckIn(null, null)
        alarms?.cancel(alarmIntent())
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }

    /** Re-arms after reboot or app update. Overdue check-ins notify immediately. */
    suspend fun restore() {
        val deadline = settings.checkIn.first().deadlineEpochMs ?: return
        if (deadline <= clock()) showOverdueNotification() else schedule(deadline)
    }

    private fun schedule(deadline: Long) {
        val am = alarms ?: return
        val pi = alarmIntent()
        try {
            if (canScheduleExact()) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, deadline, pi)
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, deadline, pi)
        } catch (e: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, deadline, pi)
        }
    }

    private fun alarmIntent(): PendingIntent = PendingIntent.getBroadcast(
        context, 1, Intent(context, CheckInAlarmReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    fun showOverdueNotification() {
        ensureChannel(context)
        if (!notificationsAllowed()) return
        val open = PendingIntent.getActivity(
            context, 2,
            Intent(context, MainActivity::class.java)
                .setAction(MainActivity.ACTION_OPEN_CHECK_IN)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val safe = PendingIntent.getBroadcast(
            context, 3, Intent(context, CheckInActionReceiver::class.java).setAction(CheckInActionReceiver.ACTION_SAFE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_haven)
            .setContentTitle("Check-in overdue")
            .setContentText("Are you safe? Tap to respond.")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setContentIntent(open)
            .setAutoCancel(false)
            .setOngoing(true)
            .addAction(0, "I'm safe", safe)
            .addAction(0, "Open", open)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, n)
        } catch (e: SecurityException) {
            // Notification permission revoked between the check and the call.
        }
    }

    companion object {
        const val CHANNEL_ID = "check_in"
        const val NOTIFICATION_ID = 1001

        fun ensureChannel(context: Context) {
            val nm = ContextCompat.getSystemService(context, NotificationManager::class.java) ?: return
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(CHANNEL_ID, "Safety check-in", NotificationManager.IMPORTANCE_HIGH).apply {
                        description = "Alerts when a safety check-in deadline passes"
                    },
                )
            }
        }
    }
}

private val receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

private fun Context.container() = (applicationContext as HavenApp).container

class CheckInAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        receiverScope.launch {
            try {
                val c = context.container()
                if (c.settings.checkIn.first().deadlineEpochMs != null) c.checkIn.showOverdueNotification()
            } finally {
                pending.finish()
            }
        }
    }
}

class CheckInActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_SAFE) return
        val pending = goAsync()
        receiverScope.launch {
            try { context.container().checkIn.cancel() } finally { pending.finish() }
        }
    }

    companion object {
        const val ACTION_SAFE = "app.projecthaven.action.CHECK_IN_SAFE"
    }
}

/** Restores an active check-in after reboot, app update or a change to the exact-alarm permission. */
class CheckInRestoreReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED,
            AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED -> Unit
            else -> return
        }
        val pending = goAsync()
        receiverScope.launch {
            try { context.container().checkIn.restore() } finally { pending.finish() }
        }
    }
}
