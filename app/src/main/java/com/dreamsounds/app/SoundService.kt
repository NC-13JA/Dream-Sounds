package com.dreamsounds.app

import android.app.*
import android.content.*
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.*
import androidx.core.app.NotificationCompat

class SoundService : Service() {
    private lateinit var pool: SoundPool
    private val ids = mutableMapOf<String, Int>()
    private val streams = mutableMapOf<String, Int>()
    private val volumes = mutableMapOf<String, Float>()
    private val pending = mutableMapOf<String, Boolean>()
    private var timer: Handler? = null
    private var timerRunnable: Runnable? = null
    private val names = listOf("rain","campfire","birds","wind","ocean","white_noise")

    override fun onCreate() {
        super.onCreate()
        val attrs = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()
        pool = SoundPool.Builder().setMaxStreams(6).setAudioAttributes(attrs).build()
        pool.setOnLoadCompleteListener { soundPool, sampleId, status ->
            if (status == 0) {
                val name = ids.entries.firstOrNull { it.value == sampleId }?.key
                if (name != null && pending[name] == true) playLoaded(name)
            }
        }
        names.forEach { name ->
            assets.openFd("sounds/$name.wav").use { afd -> ids[name] = pool.load(afd, 1) }
            volumes[name] = 0.65f
            pending[name] = false
        }
        createChannel()
        startForeground(41, notification("جاهز لتشغيل أصوات النوم"))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "PLAY" -> {
                val name = intent.getStringExtra("name") ?: return START_STICKY
                if (name !in names) return START_STICKY
                val enabled = intent.getBooleanExtra("enabled", true)
                volumes[name] = intent.getFloatExtra("volume", volumes[name] ?: 0.65f).coerceIn(0f,1f)
                pending[name] = enabled
                if (enabled) {
                    if (ids[name] != null) playLoaded(name)
                } else stop(name)
                updateNotification()
            }
            "ALL_OFF" -> { names.forEach { pending[it] = false; stop(it) }; updateNotification() }
            "SET_TIMER" -> {
                val minutes = intent.getIntExtra("minutes", 0)
                timer?.removeCallbacks(timerRunnable ?: Runnable {})
                if (minutes > 0) {
                    timer = Handler(Looper.getMainLooper())
                    val start = System.currentTimeMillis()
                    timerRunnable = object : Runnable {
                        override fun run() {
                            val left = minutes*60_000L - (System.currentTimeMillis()-start)
                            if (left <= 0) { fadeOutAndStop(); return }
                            if (left < 30_000L) {
                                val factor = (left/30_000f).coerceIn(0f,1f)
                                streams.forEach { (key, stream) -> val v=(volumes[key] ?: 0.6f)*factor; pool.setVolume(stream,v,v) }
                            }
                            timer?.postDelayed(this, 1000)
                        }
                    }
                    timer?.post(timerRunnable!!)
                }
            }
            "STOP_TIMER" -> timer?.removeCallbacks(timerRunnable ?: Runnable {})
        }
        return START_STICKY
    }

    private fun playLoaded(name: String) {
        if (streams.containsKey(name)) return
        val v = volumes[name] ?: 0.65f
        val stream = pool.play(ids[name] ?: return, v, v, 1, -1, 1f)
        if (stream != 0) streams[name] = stream
    }
    private fun stop(name: String) { streams.remove(name)?.let { pool.stop(it) } }
    private fun fadeOutAndStop() {
        val active = streams.toMap()
        val handler = Handler(Looper.getMainLooper())
        for (step in 10 downTo 0) handler.postDelayed({
            val f = step / 10f
            active.forEach { (name, stream) ->
                val v = (volumes[name] ?: 0.65f) * f
                pool.setVolume(stream, v, v)
            }
            if (step == 0) { names.forEach { pending[it] = false; stop(it) }; stopSelf() }
        }, (10-step)*250L)
    }
    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel("sleep_audio","تشغيل أصوات النوم",NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }
    private fun notification(text: String) = NotificationCompat.Builder(this,"sleep_audio")
        .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
        .setContentTitle("Dream Sounds").setContentText(text).setOngoing(true).setPriority(NotificationCompat.PRIORITY_LOW).build()
    private fun updateNotification() {
        val count = streams.size
        val text = if (count == 0) "متوقف" else "يعمل الآن: $count أصوات"
        getSystemService(NotificationManager::class.java).notify(41, notification(text))
    }
    override fun onDestroy() { timer?.removeCallbacks(timerRunnable ?: Runnable {}); pool.release(); super.onDestroy() }
    override fun onBind(intent: Intent?) = null
}
