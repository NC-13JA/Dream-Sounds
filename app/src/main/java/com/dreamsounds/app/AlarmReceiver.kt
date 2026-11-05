package com.dreamsounds.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val service = Intent(context, SoundService::class.java).apply {
            action = "PLAY"
            putExtra("name", "birds")
            putExtra("enabled", true)
            putExtra("volume", 0.45f)
        }
        ContextCompat.startForegroundService(context, service)
    }
}
