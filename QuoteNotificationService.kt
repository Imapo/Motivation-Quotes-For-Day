package com.example.motivationalquotefortheday

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import android.Manifest
import android.app.Notification
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.preference.PreferenceManager
import java.util.Calendar
import androidx.localbroadcastmanager.content.LocalBroadcastManager

@Suppress("SameParameterValue")
class QuoteNotificationService : Service() {

    private lateinit var handler: Handler
    private lateinit var quoteUpdaterRunnable: Runnable
    private lateinit var preferences: SharedPreferences
    private lateinit var currentQuote: String

    private val quotes: List<String> by lazy {
        val quoteIds = listOf(
            R.string.quote_1, R.string.quote_2, R.string.quote_3, R.string.quote_4, R.string.quote_5,
            R.string.quote_6, R.string.quote_7, R.string.quote_8, R.string.quote_9, R.string.quote_10,
            R.string.quote_11, R.string.quote_12, R.string.quote_13, R.string.quote_14, R.string.quote_15,
            R.string.quote_16, R.string.quote_17, R.string.quote_18, R.string.quote_19, R.string.quote_20,
            R.string.quote_21, R.string.quote_22, R.string.quote_23, R.string.quote_24, R.string.quote_25,
            R.string.quote_26, R.string.quote_27, R.string.quote_28, R.string.quote_29, R.string.quote_30,
            R.string.quote_31, R.string.quote_32, R.string.quote_33, R.string.quote_34, R.string.quote_35,
            R.string.quote_36, R.string.quote_37, R.string.quote_38, R.string.quote_39, R.string.quote_40,
            R.string.quote_41, R.string.quote_42, R.string.quote_43, R.string.quote_44, R.string.quote_45,
            R.string.quote_46, R.string.quote_47, R.string.quote_48, R.string.quote_49, R.string.quote_50,
            R.string.quote_51, R.string.quote_52, R.string.quote_53, R.string.quote_54, R.string.quote_55,
            R.string.quote_56, R.string.quote_57, R.string.quote_58, R.string.quote_59, R.string.quote_60,
            R.string.quote_61, R.string.quote_62, R.string.quote_63, R.string.quote_64, R.string.quote_65,
            R.string.quote_66, R.string.quote_67, R.string.quote_68, R.string.quote_69, R.string.quote_70,
            R.string.quote_71, R.string.quote_72, R.string.quote_73, R.string.quote_74, R.string.quote_75,
            R.string.quote_76, R.string.quote_77, R.string.quote_78, R.string.quote_79, R.string.quote_80,
            R.string.quote_81, R.string.quote_82, R.string.quote_83, R.string.quote_84, R.string.quote_85,
            R.string.quote_86, R.string.quote_87, R.string.quote_88, R.string.quote_89, R.string.quote_90,
            R.string.quote_91, R.string.quote_92, R.string.quote_93, R.string.quote_94, R.string.quote_95,
            R.string.quote_96, R.string.quote_97, R.string.quote_98, R.string.quote_99, R.string.quote_100
        )
        quoteIds.map { getString(it) }
    }


    override fun onCreate() {
        super.onCreate()

        preferences = PreferenceManager.getDefaultSharedPreferences(this)
        handler = Handler(Looper.getMainLooper())

        // Инициализация Foreground Service
        val channelId = "status_bar_channel"
        createNotificationChannel(channelId)

        // Проверяем, есть ли уже сохранённая цитата в SharedPreferences
        val savedQuote = preferences.getString("current_quote", null)
        android.util.Log.e("MainActivityService", "Last Quote on start: $savedQuote")
        if (savedQuote == null) {
            val randomQuote = quotes.random()
            currentQuote = randomQuote
            saveQuoteToPreferences(randomQuote)
            //sendQuoteToMainActivity(randomQuote)
        } else {
            currentQuote = savedQuote
            //sendQuoteToMainActivity(currentQuote)
        }
        val notification = createNotification(channelId, currentQuote)
        startForeground(1, notification)
        android.util.Log.e("MainActivityService", "Foreground Service Started")

        // Настраиваем Runnable для обновления цитат
        quoteUpdaterRunnable = object : Runnable {
            override fun run() {
                val interval = preferences.getInt("notificationInterval", 60000)
                if (interval == 24 * 60 * 60000) { // Раз в сутки
                    android.util.Log.e("MainActivityService", "ONCE PER DAY")
                    scheduleDailyNotification()
                } else {
                    handler.postDelayed(this, interval.toLong())
                    showNextQuote()
                }
            }
        }
    }

    private fun createNotification(channelId: String, contentText: String): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.motivation_for_you))
            .setContentText(contentText)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .build()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "SHOW_NEXT_QUOTE") {
            showNextQuote()
            startTimerWithInterval()
            android.util.Log.e("MainActivityService", "Timer started Manually")
        } else if (intent?.action != "STOP_SERVICE") {
            // Запускаем таймер, только если это не ручной вызов и не остановка сервиса
            startTimerWithInterval()
            android.util.Log.e("MainActivityService", "Timer started cycled")
        }
        stopService(Intent(this, TimerService::class.java)) // Останавливаем сервис
        preferences.edit()
            .putLong("savedStartTime", System.currentTimeMillis())
            .apply()
        Handler(mainLooper).postDelayed({
            startService(Intent(this, TimerService::class.java)) // Останавливаем сервис
        }, 500)
        return START_STICKY
    }

    private fun startTimerWithInterval() {
        handler.removeCallbacks(quoteUpdaterRunnable) // Удаляем старые вызовы
        val savedQuote = preferences.getString("current_quote", null)
        val dayInterval = preferences.getInt("everydayinterval", 60000)
        val interval = preferences.getInt("notificationInterval", 60000)
        val calendar = Calendar.getInstance()
        val hour = preferences.getInt("dailyNotificationHour", 9)
        val minute = preferences.getInt("dailyNotificationMinute", 0)
        calendar.apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
        }
        if(interval == 24 * 60 * 60000) {
            android.util.Log.e("MainActivityService", "Calendar: " + calendar.timeInMillis + " System: " + System.currentTimeMillis())
            if (calendar.timeInMillis <= System.currentTimeMillis()) {
                val currentDay = interval - (System.currentTimeMillis() - calendar.timeInMillis)
                preferences.edit().putInt("everydayinterval", currentDay.toInt()).apply()
                handler.postDelayed(quoteUpdaterRunnable, currentDay)
                android.util.Log.e("MainActivityService", "NextDay timer started with interval $currentDay")
            } else {
                val nextDay = calendar.timeInMillis - System.currentTimeMillis()
                preferences.edit().putInt("everydayinterval", nextDay.toInt()).apply()
                handler.postDelayed(quoteUpdaterRunnable, nextDay)
                android.util.Log.e("MainActivityService", "CurrentDay timer started with interval $nextDay")
            }
        } else {
            handler.postDelayed(quoteUpdaterRunnable, interval.toLong()) // Стартуем с интервалом
            android.util.Log.e("MainActivityService", "Simple timer started with interval $interval")
        }
        sendQuoteToMainActivity(savedQuote.toString())
    }

    private fun scheduleDailyNotification() {
        val calendar = Calendar.getInstance()
        val hour = preferences.getInt("dailyNotificationHour", 9)
        val minute = preferences.getInt("dailyNotificationMinute", 0)
        val dayInterval = preferences.getInt("everydayinterval", 60000)
        calendar.apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
        }
        if (calendar.timeInMillis <= System.currentTimeMillis()) {
            calendar.add(Calendar.DAY_OF_YEAR, 1) // Переносим на следующий день
        }
        showNextQuote()
        //handler.postDelayed(quoteUpdaterRunnable, delayInterval.toLong())
        android.util.Log.e("MainActivityService", "Every day timer: $dayInterval")
    }

    private fun showNextQuote() {
        val notificationId = 1
        val channelId = "status_bar_channel"
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE
        )
        val newQuote = quotes.random()
        // Making Notification
        createNotificationChannel(channelId)
        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.motivation_for_you))
            .setContentText(newQuote)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .build()

        // Sending new quote to mainActivity
        //sendQuoteToMainActivity(newQuote)
        saveQuoteToPreferences(newQuote)
        // Check for permissions
        with(NotificationManagerCompat.from(this)) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                notify(notificationId, notification)
                android.util.Log.e("MainActivityService", "Noty sent")
            }
        }
    }

    private fun formatMillisecondsWithDays(milliseconds: Long): String {
        val days = milliseconds / (1000 * 60 * 60 * 24) // Дни
        val hours = (milliseconds / (1000 * 60 * 60)) % 24 // Часы
        val minutes = (milliseconds / (1000 * 60)) % 60 // Минуты
        val seconds = (milliseconds / 1000) % 60 // Секунды
        return if (days > 0) {
            String.format(getString(R.string.d_02d_02d_02d), days, hours, minutes, seconds)
        } else {
            String.format(getString(R.string._02d_02d_02d), hours, minutes, seconds)
        }
    }

    private fun createNotificationChannel(channelId: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                getString(R.string.channel_name),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = getString(R.string.channel_description)
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager?.createNotificationChannel(channel)
            android.util.Log.e("MainActivityService", "Notify Channel Created")
        }
    }

    private fun sendQuoteToMainActivity(quote: String) {
        val intent = Intent("com.example.QUOTE_UPDATE")
        val dayInterval = formatMillisecondsWithDays(preferences.getInt("everydayinterval", 60000).toLong())
        android.util.Log.e("MainActivityService", "DayInterval: $dayInterval")
        val interval = preferences.getInt("notificationInterval", 60000)
        /*
        if(interval == 24 * 60 * 60000) {
            intent.putExtra("QUOTE", quote + "\n" + getString(R.string.time_to_next_quote) + ": " + dayInterval)
        } else {
            intent.putExtra("QUOTE", quote + "\n" + getString(R.string.time_to_next_quote) + ": " + formatMillisecondsWithDays(interval.toLong()))
        }
        */
        intent.putExtra("QUOTE", quote)
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
        android.util.Log.e("MainActivityService", "Quote sended to Main Activity")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        handler.removeCallbacks(quoteUpdaterRunnable)
        LocalBroadcastManager.getInstance(this).sendBroadcast(Intent("SERVICE_STOPPED"))
        super.onDestroy()
    }

    private fun saveQuoteToPreferences(quote: String) {
        val editor = preferences.edit()
        editor.putString("current_quote", quote) // Сохраняем единственную цитату
        editor.apply()
        android.util.Log.e("MainActivityService", "Quote saved to SharedPrefs: $quote")
    }
}
