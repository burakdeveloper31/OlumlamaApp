package com.burak.olumlama

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import org.json.JSONArray

class AlarmReceiver : BroadcastReceiver() {

    companion object {
        const val CHANNEL_ID = "olumlama_channel_high"
        const val NOTIFICATION_ID = 1001
        const val ACTION_MARK_READ = "com.burak.olumlama.ACTION_MARK_READ"

        val DEFAULT_AFFIRMATIONS = listOf(
            "Ben her zaman finansal özgürlük ve bolluk içindeyim. Maddi açıdan tamamen güvendeyim ve zenginlik hayatımın doğal bir parçası.",
            "Para bana beklediğim ve beklemediğim her yerden, çok büyük miktarlarda, hızla ve kesintisiz bir akışla geliyor. Ben gerçek bir para mıknatısıyım.",
            "Attığım her adım hayrıma sonuçlanıyor; bütün işlerim tam istediğim gibi, kolaylıkla ve kusursuzca yolunda gidiyor.",
            "Bedenim baştan ayağa sağlık, enerji ve canlılıkla dolu. Her geçen gün daha da dinç ve güçlüyüm.",
            "Zihnim her zaman berrak, kıvrak ve yaratıcı çözümlerle dolu. Fırsatları herkesten önce görüyor ve en doğru kararları hızla alıyorum.",
            "İnsanlar beni olduğum gibi seviyor, takdir ediyor ve bana değer veriyor. Çevremdeki herkesle son derece uyumlu, sıcak ve yapıcı ilişkiler kuruyorum."
        )

        fun getAffirmations(context: Context): MutableList<String> {
            val prefs = context.getSharedPreferences("OlumlamaPrefs", Context.MODE_PRIVATE)
            val jsonStr = prefs.getString("affirmations_list", null)
            return if (jsonStr != null) {
                val jsonArray = JSONArray(jsonStr)
                val list = mutableListOf<String>()
                for (i in 0 until jsonArray.length()) {
                    list.add(jsonArray.getString(i))
                }
                if (list.isEmpty()) DEFAULT_AFFIRMATIONS.toMutableList() else list
            } else {
                saveAffirmations(context, DEFAULT_AFFIRMATIONS)
                DEFAULT_AFFIRMATIONS.toMutableList()
            }
        }

        fun saveAffirmations(context: Context, list: List<String>) {
            val prefs = context.getSharedPreferences("OlumlamaPrefs", Context.MODE_PRIVATE)
            val jsonArray = JSONArray()
            list.forEach { jsonArray.put(it) }
            prefs.edit().putString("affirmations_list", jsonArray.toString()).apply()
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        val prefs = context.getSharedPreferences("OlumlamaPrefs", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("is_active", false)) return

        val list = getAffirmations(context)
        if (list.isEmpty()) return

        val currentIndex = prefs.getInt("current_index", 0)
        val textToShow = list[currentIndex % list.size]
        prefs.edit().putInt("current_index", (currentIndex + 1) % list.size).apply()

        showHeadsUpNotification(context, textToShow)

        val intervalMinutes = prefs.getLong("interval_minutes", 60L)
        scheduleNextAlarm(context, intervalMinutes)
    }

    fun showHeadsUpNotification(context: Context, message: String) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Günlük Olumlamalar",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Belirlenen aralıklarla gelen olumlama bildirimleri"
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(channel)
        }

        val readIntent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = ACTION_MARK_READ
        }
        val readPendingIntent = PendingIntent.getBroadcast(
            context,
            0,
            readIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Günün Olumlaması")
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .addAction(android.R.drawable.ic_menu_view, "Okundu", readPendingIntent)
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    fun scheduleNextAlarm(context: Context, minutes: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            1,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val triggerAtMillis = System.currentTimeMillis() + (minutes * 60 * 1000L)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
        } else {
            alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
        }
    }
}
