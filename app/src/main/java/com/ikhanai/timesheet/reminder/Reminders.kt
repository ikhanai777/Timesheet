package com.ikhanai.timesheet.reminder

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
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.ikhanai.timesheet.MainActivity
import com.ikhanai.timesheet.R
import com.ikhanai.timesheet.appContainer
import com.ikhanai.timesheet.data.AppSettings
import kotlinx.coroutines.flow.first
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

/** FR-7: daily "you haven't logged today" reminder. */
object ReminderScheduler {

    private const val WORK_NAME = "daily-reminder"
    const val CHANNEL_ID = "reminders"

    fun createChannel(context: Context) {
        val channel = NotificationChannel(CHANNEL_ID, "Daily reminder", NotificationManager.IMPORTANCE_DEFAULT)
        channel.description = "Reminds you to log your hours"
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun apply(context: Context, settings: AppSettings) {
        val workManager = WorkManager.getInstance(context)
        if (!settings.reminderEnabled) {
            workManager.cancelUniqueWork(WORK_NAME)
            return
        }
        val now = LocalDateTime.now()
        var next = now.toLocalDate().atTime(settings.reminderTime)
        if (!next.isAfter(now)) next = next.plusDays(1)
        val request = PeriodicWorkRequestBuilder<ReminderWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(Duration.between(now, next).toMillis(), TimeUnit.MILLISECONDS)
            .build()
        workManager.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE, request)
    }
}

class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val container = applicationContext.appContainer
        val settings = container.settings.settings.first()
        val today = LocalDate.now()
        val shouldNotify = settings.reminderEnabled &&
            today.dayOfWeek !in settings.weekendDays &&
            container.repository.getSheet(today) == null
        if (shouldNotify && canNotify()) notify(today)
        return Result.success()
    }

    private fun canNotify(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    @Suppress("MissingPermission") // checked in canNotify()
    private fun notify(today: LocalDate) {
        val intent = Intent(applicationContext, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_OPEN_DATE, today.toEpochDay())
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pending = PendingIntent.getActivity(
            applicationContext,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(applicationContext, ReminderScheduler.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Timesheet")
            .setContentText("You haven't logged today's hours.")
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(applicationContext).notify(1, notification)
    }
}
