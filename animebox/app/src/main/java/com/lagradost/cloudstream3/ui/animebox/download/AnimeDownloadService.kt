package com.lagradost.cloudstream3.ui.animebox.download

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.ui.animebox.AnimeBoxMainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class AnimeDownloadService : Service() {

    companion object {
        const val CHANNEL_ID = "animebox_download_service_channel"
        const val CHANNEL_NAME = "AnimeBox Downloads"
        const val NOTIFICATION_ID = 45291

        @Volatile
        var isServiceRunning = false
            private set

        fun startService(context: Context) {
            try {
                val intent = Intent(context, AnimeDownloadService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        fun stopService(context: Context) {
            try {
                val intent = Intent(context, AnimeDownloadService::class.java)
                context.stopService(intent)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private var wakeLock: PowerManager.WakeLock? = null
    private var observeJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        isServiceRunning = true
        createNotificationChannel()
        acquireWakeLock()
        startInForeground(createInitialNotification())
        observeDownloads()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        isServiceRunning = true
        acquireWakeLock()
        return START_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows live download progress and speed for AnimeBox"
                setShowBadge(false)
                setSound(null, null)
                enableVibration(false)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            manager?.createNotificationChannel(channel)
        }
    }

    @SuppressLint("WakelockTimeout")
    private fun acquireWakeLock() {
        if (wakeLock == null) {
            val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
            wakeLock = powerManager?.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "AnimeBox:DownloadWakeLock"
            )?.apply {
                setReferenceCounted(false)
            }
        }
        if (wakeLock?.isHeld == false) {
            // Keep CPU awake up to 6 hours for long download queues
            wakeLock?.acquire(6 * 60 * 60 * 1000L)
        }
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (_: Exception) {}
    }

    private fun startInForeground(notification: android.app.Notification) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun createInitialNotification(): android.app.Notification {
        val tapIntent = Intent(this, AnimeBoxMainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("AnimeBox Downloader")
            .setContentText("Downloading anime in background...")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setShowWhen(false)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setProgress(100, 0, true)
            .build()
    }

    private fun observeDownloads() {
        observeJob?.cancel()
        val downloadManager = AnimeDownloadManager.getInstance(applicationContext)
        observeJob = serviceScope.launch {
            downloadManager.episodesState.collectLatest { episodes ->
                val activeDownloads = episodes.filter {
                    it.status == DownloadStatus.DOWNLOADING || it.status == DownloadStatus.PENDING
                }

                if (activeDownloads.isEmpty()) {
                    // No active downloads left
                    releaseWakeLock()
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        stopForeground(STOP_FOREGROUND_REMOVE)
                    } else {
                        @Suppress("DEPRECATION")
                        stopForeground(true)
                    }
                    stopSelf()
                } else {
                    acquireWakeLock()
                    updateNotification(activeDownloads)
                }
            }
        }
    }

    private fun updateNotification(activeDownloads: List<DownloadedEpisode>) {
        val primary = activeDownloads.firstOrNull { it.status == DownloadStatus.DOWNLOADING }
            ?: activeDownloads.first()

        val progress = primary.downloadProgress.coerceIn(0, 100)
        val speed = primary.downloadSpeedText.ifBlank { "Downloading" }
        val title = if (activeDownloads.size > 1) {
            "${primary.animeTitle} (${activeDownloads.size} active)"
        } else {
            primary.animeTitle
        }
        val content = "Ep ${primary.episodeNumber} • $progress% ($speed)"

        val tapIntent = Intent(this, AnimeBoxMainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(content)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setShowWhen(false)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setProgress(100, progress, false)
            .build()

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        notificationManager?.notify(NOTIFICATION_ID, notification)
    }

    override fun onDestroy() {
        isServiceRunning = false
        observeJob?.cancel()
        serviceScope.cancel()
        releaseWakeLock()
        super.onDestroy()
    }
}
