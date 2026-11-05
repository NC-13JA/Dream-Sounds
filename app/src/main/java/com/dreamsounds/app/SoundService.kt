package com.dreamsounds.app

import android.app.*
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.*
import androidx.core.app.NotificationCompat
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

class SoundService : Service() {
    private val players = mutableMapOf<String, MediaPlayer>()
    private val volumes = mutableMapOf<String, Float>()
    private val pending = mutableMapOf<String, Boolean>()
    private val executor = Executors.newFixedThreadPool(3)
    private val handler = Handler(Looper.getMainLooper())
    private var timerRunnable: Runnable? = null
    private val urls = mapOf(
        "birds" to "https://opengameart.org/sites/default/files/park_ambience_birds.wav",
        "river" to "https://opengameart.org/sites/default/files/park_ambience_river.wav",
        "wind" to "https://opengameart.org/sites/default/files/park_ambience_wind.wav",
        "ocean" to "https://opengameart.org/sites/default/files/wave_01_cc0-18363__jasinski__alkaibeach.flac",
        "crickets" to "https://opengameart.org/sites/default/files/cricketsounds090613.mp3",
        "rain" to "https://opengameart.org/sites/default/files/Rain%20MP3.zip"
    )

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startForeground(41, notification("جاهز — تسجيلات أصلية"))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "PLAY" -> {
                val name = intent.getStringExtra("name") ?: return START_STICKY
                if (name !in urls) return START_STICKY
                val enabled = intent.getBooleanExtra("enabled", true)
                val volume = intent.getFloatExtra("volume", volumes[name] ?: 0.65f).coerceIn(0f, 1f)
                volumes[name] = volume
                pending[name] = enabled
                if (enabled) startTrack(name) else stopTrack(name)
                updateNotification()
            }
            "ALL_OFF" -> {
                urls.keys.forEach { pending[it] = false; stopTrack(it) }
                updateNotification()
            }
            "SET_TIMER" -> setTimer(intent.getIntExtra("minutes", 0))
            "STOP_TIMER" -> stopTimer()
        }
        return START_STICKY
    }

    private fun startTrack(name: String) {
        players[name]?.let {
            val v = volumes[name] ?: 0.65f
            it.setVolume(v, v)
            return
        }
        if (pending[name] != true) return
        updateNotification("يتم تجهيز التسجيل الصوتي...")
        executor.execute {
            try {
                val extension = when (name) { "ocean" -> "flac"; "crickets", "rain" -> "mp3"; else -> "wav" }
                val file = File(filesDir, "$name.$extension")
                if (!file.exists() || file.length() < 1024L) download(urls.getValue(name), file)
                handler.post {
                    if (pending[name] != true || players.containsKey(name)) return@post
                    try {
                        val player = MediaPlayer()
                        player.setAudioAttributes(AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                        player.setDataSource(file.absolutePath)
                        player.isLooping = true
                        player.setOnPreparedListener {
                            val v = volumes[name] ?: 0.65f
                            it.setVolume(v, v)
                            it.start()
                            updateNotification()
                        }
                        player.setOnErrorListener { mp, _, _ ->
                            players.remove(name)
                            mp.release()
                            updateNotification()
                            true
                        }
                        players[name] = player
                        player.prepareAsync()
                    } catch (_: Exception) {
                        players.remove(name)
                        updateNotification()
                    }
                }
            } catch (_: Exception) {
                handler.post {
                    pending[name] = false
                    updateNotification("تعذر تنزيل التسجيل — تحقق من الإنترنت")
                }
            }
        }
    }

    private fun download(link: String, destination: File) {
        val temp = File(filesDir, destination.name + ".download")
        val connection = URL(link).openConnection() as HttpURLConnection
        connection.connectTimeout = 25000
        connection.readTimeout = 180000
        connection.instanceFollowRedirects = true
        try {
            connection.connect()
            if (connection.responseCode !in 200..299) throw Exception("HTTP error")
            connection.inputStream.use { input ->
                temp.outputStream().use { output -> input.copyTo(output) }
            }
            if (temp.length() < 1024L) throw Exception("Invalid download")
            if (link.lowercase().endsWith(".zip")) {
                java.util.zip.ZipInputStream(temp.inputStream()).use { zip ->
                    var entry = zip.nextEntry
                    while (entry != null && (entry.isDirectory || !entry.name.lowercase().endsWith(".mp3"))) {
                        entry = zip.nextEntry
                    }
                    if (entry == null) throw Exception("No MP3 in archive")
                    destination.outputStream().use { output -> zip.copyTo(output) }
                }
            } else {
                if (destination.exists()) destination.delete()
                if (!temp.renameTo(destination)) throw Exception("Cannot save audio")
            }
            if (!destination.exists() || destination.length() < 1024L) throw Exception("Audio extraction failed")
        } finally {
            connection.disconnect()
            if (temp.exists()) temp.delete()
        }
    }

    private fun stopTrack(name: String) {
        players.remove(name)?.let { player ->
            try { if (player.isPlaying) player.stop() } catch (_: Exception) {}
            player.release()
        }
    }

    private fun setTimer(minutes: Int) {
        stopTimer()
        if (minutes <= 0) return
        val endAt = System.currentTimeMillis() + minutes * 60_000L
        timerRunnable = object : Runnable {
            override fun run() {
                val left = endAt - System.currentTimeMillis()
                if (left <= 0L) {
                    fadeOutAndStop()
                    return
                }
                if (left <= 30_000L) {
                    val factor = (left / 30_000f).coerceIn(0f, 1f)
                    players.forEach { (name, player) ->
                        val v = (volumes[name] ?: 0.65f) * factor
                        try { player.setVolume(v, v) } catch (_: Exception) {}
                    }
                }
                handler.postDelayed(this, 1000L)
            }
        }
        handler.post(timerRunnable!!)
    }

    private fun fadeOutAndStop() {
        val active = players.toMap()
        for (step in 10 downTo 0) {
            handler.postDelayed({
                val factor = step / 10f
                active.forEach { (name, player) ->
                    val v = (volumes[name] ?: 0.65f) * factor
                    try { player.setVolume(v, v) } catch (_: Exception) {}
                }
                if (step == 0) {
                    urls.keys.forEach { pending[it] = false; stopTrack(it) }
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
            }, (10 - step) * 250L)
        }
    }

    private fun stopTimer() {
        timerRunnable?.let { handler.removeCallbacks(it) }
        timerRunnable = null
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel("sleep_audio", "تشغيل أصوات النوم", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun notification(text: String) = NotificationCompat.Builder(this, "sleep_audio")
        .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
        .setContentTitle("Dream Sounds").setContentText(text)
        .setOngoing(true).setPriority(NotificationCompat.PRIORITY_LOW).build()

    private fun updateNotification(overrideText: String? = null) {
        val count = players.size
        val text = overrideText ?: if (count == 0) "متوقف" else "يعمل الآن: $count تسجيلات طبيعية"
        getSystemService(NotificationManager::class.java).notify(41, notification(text))
    }

    override fun onDestroy() {
        stopTimer()
        players.values.forEach { it.release() }
        players.clear()
        executor.shutdownNow()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?) = null
}
