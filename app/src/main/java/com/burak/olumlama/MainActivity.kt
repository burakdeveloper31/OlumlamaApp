package com.burak.olumlama

import android.Manifest
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
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
            val savedMinutes = prefs.getLong("interval_minutes", 60L)
            text = if (active) "Durum: AKTİF ($savedMinutes dk aralıkla)" else "Durum: DURDURULDU"
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

                // Android'in arka planda servisi kapatmasını önlemek için pil optimizasyon muafiyeti iste
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
                    if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                        try {
                            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                data = Uri.parse("package:$packageName")
                            }
                            startActivity(intent)
                        } catch (e: Exception) {
                            // Cihaz doğrudan diyaloğu desteklemiyorsa genel ayarlara yönlendir
                        }
                    }
                }

                // Eski servis veya alarm varsa temizle
                stopService(Intent(context, OlumlamaService::class.java))
                cancelAlarm()

                // 1. cümleden (index 0) başlat ve aktif olarak kaydet
                prefs.edit()
                    .putBoolean("is_active", true)
                    .putLong("interval_minutes", minutes)
                    .putInt("current_index", 0)
                    .apply()

                val serviceIntent = Intent(context, OlumlamaService::class.java)
                ContextCompat.startForegroundService(context, serviceIntent)

                statusText.text = "Durum: AKTİF ($minutes dk aralıkla)"
                statusText.setTextColor(Color.parseColor("#4CAF50"))
                Toast.makeText(context, "1. cümleden başlatıldı. Durdur diyene kadar kesintisiz çalışacak.", Toast.LENGTH_SHORT).show()
            }
        }

        val btnStop = Button(this).apply {
            text = "Durdur"
            setBackgroundColor(Color.parseColor("#D32F2F"))
            setTextColor(Color.WHITE)
            setOnClickListener {
                prefs.edit().putBoolean("is_active", false).apply()
                stopService(Intent(context, OlumlamaService::class.java))
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
                val safeIndex = currentIndex % list.size
                val msg = list[safeIndex]
                prefs.edit().putInt("current_index", (safeIndex + 1) % list.size).apply()
                AlarmReceiver().showHeadsUpNotification(context, "(${safeIndex + 1}/${list.size}) $msg")
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
                Toast.makeText(context, "Olumlama eklendi (Toplam: ${list.size})", Toast.LENGTH_SHORT).show()
            }
        }

        val listTitle = TextView(this).apply {
            text = "Kayıtlı Olumlama Cümleleri"
            textSize = 18f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            setPadding(0, 48, 0, 16)
        }

        listContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val spaceParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            setMargins(0, 16, 0, 0)
        }

        rootLayout.addView(titleText)
        rootLayout.addView(statusText)
        rootLayout.addView(labelInterval)
        rootLayout.addView(intervalInput, spaceParams)
        rootLayout.addView(btnStart, spaceParams)
        rootLayout.addView(btnStop, spaceParams)
        rootLayout.addView(btnTest, spaceParams)
        rootLayout.addView(btnResetOrder, spaceParams)
        rootLayout.addView(addTitle)
        rootLayout.addView(newAffirmationInput, spaceParams)
        rootLayout.addView(btnAdd, spaceParams)
        rootLayout.addView(listTitle)
        rootLayout.addView(listContainer)

        scrollView.addView(rootLayout)
        setContentView(scrollView)

        refreshAffirmationList()
    }

    private fun refreshAffirmationList() {
        listContainer.removeAllViews()
        val list = AlarmReceiver.getAffirmations(this)

        list.forEachIndexed { index, affirmation ->
            val itemLayout = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setBackgroundColor(Color.parseColor("#1E1E1E"))
                setPadding(24, 20, 24, 20)
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(0, 0, 0, 16)
                }
            }

            val textView = TextView(this).apply {
                text = "${index + 1}. $affirmation"
                textSize = 14f
                setTextColor(Color.WHITE)
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
                ).apply {
                    setMargins(0, 0, 16, 0)
                }
            }

            val btnDelete = Button(this).apply {
                text = "Sil"
                setBackgroundColor(Color.parseColor("#B71C1C"))
                setTextColor(Color.WHITE)
                textSize = 12f
                setOnClickListener {
                    if (list.size <= 1) {
                        Toast.makeText(context, "Listede en az 1 cümle kalmalıdır.", Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }
                    list.removeAt(index)
                    AlarmReceiver.saveAffirmations(context, list)
                    // Silme işleminden sonra indeks taşmasını önle
                    val prefs = getSharedPreferences("OlumlamaPrefs", Context.MODE_PRIVATE)
                    val currentIdx = prefs.getInt("current_index", 0)
                    if (currentIdx >= list.size) {
                        prefs.edit().putInt("current_index", 0).apply()
                    }
                    refreshAffirmationList()
                    Toast.makeText(context, "Cümle silindi.", Toast.LENGTH_SHORT).show()
                }
            }

            itemLayout.addView(textView)
            itemLayout.addView(btnDelete)
            listContainer.addView(itemLayout)
        }
    }

    private fun cancelAlarm() {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(this, AlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            this,
            1,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
    }

    private fun checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}
