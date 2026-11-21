package com.burak.olumlama

import android.Manifest
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var listContainer: LinearLayout

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (!isGranted) {
            Toast.makeText(this, "Bildirim izni olmadan bildirimler görünmez.", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        checkPermissions()

        val prefs = getSharedPreferences("OlumlamaPrefs", Context.MODE_PRIVATE)

        val scrollView = ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#121212"))
            isFillViewport = true
        }

        val rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(56, 72, 56, 72)
            gravity = Gravity.CENTER_HORIZONTAL
        }

        val titleText = TextView(this).apply {
            text = "Günlük Olumlama"
            textSize = 26f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            setPadding(0, 0, 0, 12)
        }

        val statusText = TextView(this).apply {
            val active = prefs.getBoolean("is_active", false)
            text = if (active) "Durum: AKTİF (Sırayla çalışıyor)" else "Durum: DURDURULDU"
            textSize = 15f
            setTextColor(if (active) Color.parseColor("#4CAF50") else Color.parseColor("#FF5252"))
            setPadding(0, 0, 0, 36)
        }

        val labelInterval = TextView(this).apply {
            text = "Bildirim Aralığı (Dakika):"
            textSize = 15f
            setTextColor(Color.LTGRAY)
        }

        val intervalInput = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(prefs.getLong("interval_minutes", 60L).toString())
            textSize = 18f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#1E1E1E"))
            setPadding(24, 20, 24, 20)
            gravity = Gravity.CENTER
        }

        val btnStart = Button(this).apply {
            text = "Sistemi Başlat / Güncelle"
            setBackgroundColor(Color.parseColor("#4CAF50"))
            setTextColor(Color.WHITE)
            setOnClickListener {
                val minutes = intervalInput.text.toString().toLongOrNull() ?: 60L
                if (minutes < 1) {
                    Toast.makeText(context, "En az 1 dakika giriniz.", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                prefs.edit()
                    .putBoolean("is_active", true)
                    .putLong("interval_minutes", minutes)
                    .apply()

                AlarmReceiver().scheduleNextAlarm(context, minutes)
                statusText.text = "Durum: AKTİF ($minutes dk aralıkla)"
                statusText.setTextColor(Color.parseColor("#4CAF50"))
                Toast.makeText(context, "$minutes dakikada bir sırayla bildirim gelecek.", Toast.LENGTH_SHORT).show()
            }
        }

        val btnStop = Button(this).apply {
            text = "Durdur"
            setBackgroundColor(Color.parseColor("#D32F2F"))
            setTextColor(Color.WHITE)
            setOnClickListener {
                prefs.edit().putBoolean("is_active", false).apply()
                cancelAlarm()
                statusText.text = "Durum: DURDURULDU"
                statusText.setTextColor(Color.parseColor("#FF5252"))
                Toast.makeText(context, "Bildirim döngüsü durduruldu.", Toast.LENGTH_SHORT).show()
            }
        }

        val btnTest = Button(this).apply {
            text = "Şimdi Test Bildirimi Gönder"
            setBackgroundColor(Color.parseColor("#1976D2"))
            setTextColor(Color.WHITE)
            setOnClickListener {
                val list = AlarmReceiver.getAffirmations(context)
                if (list.isEmpty()) return@setOnClickListener
                val currentIndex = prefs.getInt("current_index", 0)
                val msg = list[currentIndex % list.size]
                prefs.edit().putInt("current_index", (currentIndex + 1) % list.size).apply()
                AlarmReceiver().showHeadsUpNotification(context, msg)
            }
        }

        val btnResetOrder = Button(this).apply {
            text = "Sırayı Başa Al (1. Cümleden Başla)"
            setBackgroundColor(Color.parseColor("#424242"))
            setTextColor(Color.WHITE)
            setOnClickListener {
                prefs.edit().putInt("current_index", 0).apply()
                Toast.makeText(context, "Sıra 1. cümleye sıfırlandı.", Toast.LENGTH_SHORT).show()
            }
        }

        val addTitle = TextView(this).apply {
            text = "Yeni Olumlama Ekle"
            textSize = 18f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            setPadding(0, 48, 0, 12)
        }

        val newAffirmationInput = EditText(this).apply {
            hint = "Yeni olumlama cümleni buraya yaz..."
            setHintTextColor(Color.GRAY)
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#1E1E1E"))
            setPadding(28, 28, 28, 28)
            minLines = 2
        }

        val btnAdd = Button(this).apply {
            text = "+ Listeye Ekle"
            setBackgroundColor(Color.parseColor("#7B1FA2"))
            setTextColor(Color.WHITE)
            setOnClickListener {
                val text = newAffirmationInput.text.toString().trim()
                if (text.isEmpty()) {
                    Toast.makeText(context, "Lütfen bir cümle yazın.", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                val list = AlarmReceiver.getAffirmations(context)
                list.add(text)
                AlarmReceiver.saveAffirmations(context, list)
                newAffirmationInput.text.clear()
                refreshAffirmationList()
                Toast.makeText(context, "Olumlama eklendi!", Toast.LENGTH_SHORT).show()
            }
        }

        val listHeader = TextView(this).apply {
            text = "Kayıtlı Olumlamalar (Silmek için yanındaki X'e bas)"
            textSize = 14f
            setTextColor(Color.LTGRAY)
            setPadding(0, 40, 0, 16)
        }

        listContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        val params = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            setMargins(0, 16, 0, 0)
        }

        rootLayout.addView(titleText)
        rootLayout.addView(statusText)
        rootLayout.addView(labelInterval)
        rootLayout.addView(intervalInput, params)
        rootLayout.addView(btnStart, params)
        rootLayout.addView(btnStop, params)
        rootLayout.addView(btnTest, params)
        rootLayout.addView(btnResetOrder, params)

        rootLayout.addView(addTitle)
        rootLayout.addView(newAffirmationInput, params)
        rootLayout.addView(btnAdd, params)
        rootLayout.addView(listHeader)
        rootLayout.addView(listContainer, params)

        scrollView.addView(rootLayout)
        setContentView(scrollView)

        refreshAffirmationList()
    }

    private fun refreshAffirmationList() {
        listContainer.removeAllViews()
        val list = AlarmReceiver.getAffirmations(this)

        list.forEachIndexed { index, item ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setBackgroundColor(Color.parseColor("#1E1E1E"))
                setPadding(24, 20, 16, 20)
                gravity = Gravity.CENTER_VERTICAL
            }

            val tv = TextView(this).apply {
                text = "${index + 1}. $item"
                setTextColor(Color.WHITE)
                textSize = 14f
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }

            val btnDelete = Button(this).apply {
                text = "X"
                setTextColor(Color.parseColor("#FF5252"))
                setBackgroundColor(Color.TRANSPARENT)
                layoutParams = LinearLayout.LayoutParams(110, LinearLayout.LayoutParams.WRAP_CONTENT)
                setOnClickListener {
                    if (list.size <= 1) {
                        Toast.makeText(context, "Listede en az 1 olumlama kalmalıdır.", Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }
                    list.removeAt(index)
                    AlarmReceiver.saveAffirmations(context, list)
                    refreshAffirmationList()
                    Toast.makeText(context, "Silindi.", Toast.LENGTH_SHORT).show()
                }
            }

            val rowParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, 0, 12)
            }

            row.addView(tv)
            row.addView(btnDelete)
            listContainer.addView(row, rowParams)
        }
    }

    private fun checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
            if (!alarmManager.canScheduleExactAlarms()) {
                startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM))
            }
        }
    }

    private fun cancelAlarm() {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(this, AlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            this, 1, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
    }
}
