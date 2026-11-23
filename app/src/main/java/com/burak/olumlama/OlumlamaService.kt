package com.burak.olumlama

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.*
import androidx.core.app.NotificationCompat

class OlumlamaService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private var wakeLock: PowerManager.WakeLock? = null
    private var isRunning = false

    companion object {
        const val SERVICE_CHANNEL_ID = "olumlama_service_channel"
        const val SERVICE_NOTIF_ID = 9999
    }

    private val loopRunnable = object : Runnable {
        override fun run() {
            val prefs = getSharedPreferences("OlumlamaPrefs", Context.MODE_PRIVATE)
            if (!prefs.getBoolean("is_active", false)) {
                stopSelf()
                return
            }

            val list = AlarmReceiver.getAffirmations(this@OlumlamaService)
            if (list.isNotEmpty()) {
                val currentIndex = prefs.getInt("current_index", 0)
                val safeIndex = currentIndex % list.size
                val textToShow = list[safeIndex]

                // Sıradaki cümleye geç (sonuncudaysa 0'a yani 1. cümleye döner)
                val nextIndex = (safeIndex + 1) % list.size
                prefs.edit().putInt("current_index", nextIndex).apply()

                AlarmReceiver().showHeadsUpNotification(
                    this@OlumlamaService,
                    "(${safeIndex + 1}/${list.size}) $textToShow"
                )
            }

            val intervalMinutes = prefs.getLong("interval_minutes", 60L)
            val delayMillis = intervalMinutes * 60 * 1000L
            handler.postDelayed(this, delayMillis)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createServiceChannel()
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "OlumlamaApp::LoopWakeLock")
        wakeLock?.acquire(24 * 60 * 60 * 1000L) // CPU uyumasını önler
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = NotificationCompat.Builder(this, SERVICE_CHANNEL_ID)
            .setContentTitle("Günlük Olumlama Aktif")
            .setContentText("Durdur diyene kadar cümleler sırayla gönderiliyor...")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        startForeground(SERVICE_NOTIF_ID, notification)

        if (!isRunning) {
            isRunning = true
            handler.removeCallbacks(loopRunnable)
            // Başlatır başlatmaz 1. cümleyi hemen gönderir ve döngüyü başlatır
            handler.post(loopRunnable)
        }

        return START_STICKY // Sistem öldürmeye kalkarsa otomatik yeniden başlatır
    }

    override fun onDestroy() {
        isRunning = false
        handler.removeCallbacks(loopRunnable)
        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createServiceChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                SERVICE_CHANNEL_ID,
                "Arka Plan Çalışma Servisi",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }
}
