package com.example.motivationalquotefortheday

import android.Manifest
import android.annotation.SuppressLint
import android.app.ActivityManager
import android.app.TimePickerDialog
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isVisible
import androidx.preference.PreferenceManager
import java.util.Calendar
import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import android.os.Handler
import androidx.localbroadcastmanager.content.LocalBroadcastManager

@Suppress("DEPRECATION")
class MainActivity : ComponentActivity() {

    private lateinit var intervalSpinner: Spinner
    private lateinit var showNextQuoteButton: Button
    private lateinit var selectTimeButton: Button
    private lateinit var timeDisplayTextView: TextView
    private lateinit var quoteField: TextView
    private lateinit var preferences: SharedPreferences
    private val quoteReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val quote = intent.getStringExtra("QUOTE")
            quote?.let {
                findViewById<TextView>(R.id.quoteField).text = it
            }
        }
    }
    private val requestNotificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                Toast.makeText(this, getString(R.string.access_allowed), Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, getString(R.string.access_denied), Toast.LENGTH_SHORT).show()
            }
        }
    private lateinit var timeLeftTextView: TextView
    private val timeUpdateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val remainingTime = intent.getStringExtra("remainingTime") ?: "00:00:00"
            timeLeftTextView.text = getString(R.string.time_left, remainingTime)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        preferences = PreferenceManager.getDefaultSharedPreferences(this)
        intervalSpinner = findViewById(R.id.intervalSpinner)
        showNextQuoteButton = findViewById(R.id.showNextQuoteButton)
        selectTimeButton = findViewById(R.id.selectTimeButton)
        timeDisplayTextView = findViewById(R.id.timeDisplayTextView)
        quoteField = findViewById(R.id.quoteField)
        timeLeftTextView = findViewById(R.id.timeLeftTextView)

        val layoutTime: LinearLayout = findViewById(R.id.layoutTime)
        val intervals = resources.getIntArray(R.array.notification_intervals_values)
        val currentInterval = preferences.getInt("notificationInterval", 60000)
        val currentPosition = intervals.indexOf(currentInterval)
        var isFirstRun = true
        if (currentPosition >= 0) {
            intervalSpinner.setSelection(currentPosition)
        }

        // Регистрируем ресивер
        LocalBroadcastManager.getInstance(this)
            .registerReceiver(timeUpdateReceiver, IntentFilter("com.example.TIMER_UPDATE"))

        // Запускаем сервис
        //startService(Intent(this, TimerService::class.java))
        // Регистрируем ресивер для получения данных
        LocalBroadcastManager.getInstance(this)
            .registerReceiver(quoteReceiver, IntentFilter("com.example.QUOTE_UPDATE"))
        // Запрос разрешения на уведомления для Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        updateTimeDisplay()
        intervalSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: android.view.View?, position: Int, id: Long) {
                if (isFirstRun) {
                    isFirstRun = false
                    return
                }
                val interval = when (position) {
                    0 -> 60000
                    1 -> 2 * 60000
                    2 -> 5 * 60000
                    3 -> 15 * 60000
                    4 -> 30 * 60000
                    5 -> 60 * 60000
                    6 -> 2 * 60 * 60000
                    7 -> 6 * 60 * 60000
                    else -> 24 * 60 * 60000
                }
                preferences.edit().putInt("notificationInterval", interval).apply()
                preferences.edit()
                    .putLong("savedStartTime", System.currentTimeMillis())
                    .apply()
                selectTimeButton.isEnabled = interval == 24 * 60 * 60000
                layoutTime.isVisible = interval == 24 * 60 * 60000
                android.util.Log.e("MainActivityService", "Time Button changed")
                updateTimeDisplay()
                restartService()
            }
            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

        selectTimeButton.setOnClickListener {
            showTimePicker()
        }

        showNextQuoteButton.setOnClickListener {
            val intent = Intent(this, QuoteNotificationService::class.java).apply {
                action = "SHOW_NEXT_QUOTE"
            }
            startService(intent)
        }

        selectTimeButton.isEnabled = currentInterval == 24 * 60 * 60000
        layoutTime.isVisible = currentInterval == 24 * 60 * 60000

        // Запуск сервиса с проверкой
        restartServiceIfNotRunning()
    }

    @SuppressLint("DefaultLocale")
    private fun updateTimeDisplay() {
        val hour = preferences.getInt("dailyNotificationHour", 9)
        val minute = preferences.getInt("dailyNotificationMinute", 0)

        if (hour == 9 && minute == 0) {
            timeDisplayTextView.text = buildString {
                append(getString(R.string.default_time))
                append(": 09:00")
            }
        } else {
            timeDisplayTextView.text = buildString {
                append(getString(R.string.default_time))
                append(": ")
                append(String.format("%02d:%02d", hour, minute))
            }
        }
        android.util.Log.e("MainActivityService", "Timer Updated")
    }

    private fun showTimePicker() {
        val calendar = Calendar.getInstance()
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val minute = calendar.get(Calendar.MINUTE)

        TimePickerDialog(this, { _, selectedHour, selectedMinute ->
            preferences.edit().putInt("dailyNotificationHour", selectedHour).apply()
            preferences.edit().putInt("dailyNotificationMinute", selectedMinute).apply()
            // Обновляем отображаемое время
            updateTimeDisplay()
            // Перезапускаем сервис
            restartService()
            android.util.Log.e("MainActivityService", "Timer set. Hours: $selectedHour and Minutes: $selectedMinute")
        }, hour, minute, true).show()
    }

    private fun restartService() {
        val intent = Intent(this, QuoteNotificationService::class.java)
        try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                // Временный ресивер для запуска сервиса после его остановки
                val serviceStoppedReceiver = object : BroadcastReceiver() {
                    override fun onReceive(context: Context, intent: Intent) {
                        if (intent.action == "SERVICE_STOPPED") {
                            // Запускаем сервис после получения сигнала об остановке
                            startService(Intent(this@MainActivity, QuoteNotificationService::class.java))
                            android.util.Log.e("MainActivityService", "Service Started")
                            // Отменяем регистрацию ресивера
                            LocalBroadcastManager.getInstance(this@MainActivity)
                                .unregisterReceiver(this)
                        }
                    }
                }
                // Регистрируем ресивер
                LocalBroadcastManager.getInstance(this)
                    .registerReceiver(serviceStoppedReceiver, IntentFilter("SERVICE_STOPPED"))
                // Останавливаем сервис
                stopService(intent)
                android.util.Log.e("MainActivityService", "Service Stopped")
            } else {
                android.util.Log.e("MainActivityService", getString(R.string.no_access_for_restart))
            }
        } catch (e: SecurityException) {
            android.util.Log.e("MainActivityService", getString(R.string.error_while_restart, e.message))
        }
    }

    private fun restartServiceIfNotRunning() {
        val intent = Intent(this, QuoteNotificationService::class.java)
        if (!isServiceRunning(QuoteNotificationService::class.java)) {
            startService(intent)
            android.util.Log.e("MainActivityService", "Service Started")
        } else {
            android.util.Log.e("MainActivityService", "Always started")
        }
    }

    private fun isServiceRunning(serviceClass: Class<*>): Boolean {
        val manager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        return manager.getRunningServices(Int.MAX_VALUE).any { it.service.className == serviceClass.name }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Отменяем регистрацию ресивера
        LocalBroadcastManager.getInstance(this)
            .unregisterReceiver(quoteReceiver)
        LocalBroadcastManager.getInstance(this)
            //.unregisterReceiver(timeUpdateReceiver)
        //stopService(Intent(this, TimerService::class.java)) // Останавливаем сервис
        android.util.Log.e("MainActivityService", "Activity destroyed")
    }

    override fun onPause() {
        super.onPause()
        //stopService(Intent(this, TimerService::class.java)) // Останавливаем сервис
        android.util.Log.e("MainActivityService", "Activity destroyed")
    }

    @SuppressLint("InlinedApi")
    override fun onResume() {
        super.onResume()
        //startService(Intent(this, TimerService::class.java)) // Останавливаем сервис
        if (intent.action == "REQUEST_NOTIFICATION_PERMISSION") {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 100)
        }
        android.util.Log.e("MainActivityService", "Activity resumed")
    }

}
