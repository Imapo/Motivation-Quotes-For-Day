package com.example.motivationalquotefortheday

import android.app.Service
import android.content.Intent
import android.content.SharedPreferences
import android.os.Handler
import android.os.IBinder
import android.util.Log
import android.widget.Toast
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.preference.PreferenceManager

class TimerService : Service() {

    private val handler = Handler()
    private var remainingTimeInSeconds = 0 // Оставшееся время до смены фразы
    private lateinit var preferences: SharedPreferences
    private var timerRunnable: Runnable? = null
    private var elapsedTime = 0
    private var savedInterval = 60000

    private val delayInterval: Int
        get() = preferences.getInt("notificationInterval", 60000) // Интервал в миллисекундах

    override fun onCreate() {
        super.onCreate()
        preferences = PreferenceManager.getDefaultSharedPreferences(this)
        savedInterval = preferences.getInt("notificationInterval", 60000)
        // Определяем оставшееся время на основе текущего времени
        val savedStartTime = preferences.getLong("savedStartTime", System.currentTimeMillis())
        val currentTime = System.currentTimeMillis()
        elapsedTime = (currentTime - savedStartTime).toInt()

        if(savedInterval == 24 * 60 * 60000) {
            remainingTimeInSeconds = preferences.getInt("everydayinterval", 60000) / 1000
            Log.d("TimeService", remainingTimeInSeconds.toString())
        } else {
            remainingTimeInSeconds = if (elapsedTime >= delayInterval) {
                delayInterval / 1000 // Полный интервал
            } else {
                (delayInterval - elapsedTime) / 1000 // Оставшееся время
            }
        }

        startTimer()
        Log.d("MainActivityService", "Service Timer created")
    }

    private fun startTimer() {
        stopTimer() // Останавливаем любой предыдущий таймер, чтобы не было конфликтов
        timerRunnable = object : Runnable {
            override fun run() {
                if (remainingTimeInSeconds > 0) {
                    remainingTimeInSeconds--
                    sendTimeUpdate()
                } else {
                    // Сбрасываем таймер
                    remainingTimeInSeconds = delayInterval / 1000
                    Log.d(
                        "MainActivityService",
                        "Saved: " + (System.currentTimeMillis() - preferences.getLong(
                            "savedStartTime",
                            System.currentTimeMillis()
                        )) + " "
                    )
                }
                handler.postDelayed(this, 1000) // Запуск через 1 секунду
            }
        }
        handler.post(timerRunnable!!)
    }

    private fun sendTimeUpdate() {
        val formattedTime = formatMillisecondsWithDays(remainingTimeInSeconds * 1000L) // Преобразуем в миллисекунды
        val intent = Intent("com.example.TIMER_UPDATE").apply {
            putExtra("remainingTime", formattedTime) // Передаём отформатированное время
        }
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
        Log.d(
            "TimeService",
            "Time update sent($savedInterval): $formattedTime seconds remaining"
        )
    }

    fun formatMillisecondsWithDays(milliseconds: Long): String {
        val days = milliseconds / (1000 * 60 * 60 * 24) // Дни
        val hours = (milliseconds / (1000 * 60 * 60)) % 24 // Часы
        val minutes = (milliseconds / (1000 * 60)) % 60 // Минуты
        val seconds = (milliseconds / 1000) % 60 // Секунды
        return if (days > 0) {
            String.format("%d:%02d:%02d:%02d", days, hours, minutes, seconds)
        } else {
            String.format("%02d:%02d:%02d", hours, minutes, seconds)
        }
    }

    private fun stopTimer() {
        timerRunnable?.let { handler.removeCallbacks(it) }
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
        Log.d("MainActivityService", "Timer Service destroyed")
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
