package com.dreamsounds.app

import android.app.*
import android.content.*
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.core.content.ContextCompat
import org.json.JSONObject
import java.util.Calendar

class MainActivity : Activity() {
    private val prefs by lazy { getSharedPreferences("dream_sounds", MODE_PRIVATE) }
    private val soundNames = listOf("rain","campfire","birds","wind","ocean","white_noise")
    private val labels = mapOf("rain" to "المطر 🌧","campfire" to "نار المخيم 🔥","birds" to "أصوات الطيور 🐦","wind" to "الرياح 🍃","ocean" to "أمواج البحر 🌊","white_noise" to "الضوضاء البيضاء")
    private val active = mutableMapOf<String, Boolean>()
    private val volumes = mutableMapOf<String, Float>()
    private lateinit var status: TextView
    private val bg = Color.rgb(9,15,32)
    private val card = Color.rgb(20,30,55)
    private val accent = Color.rgb(169,184,255)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        soundNames.forEach { active[it] = false; volumes[it] = 0.65f }
        requestNotificationPermission()
        buildUi()
    }
    private fun buildUi() {
        val root = LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setBackgroundColor(bg); setPadding(18,16,18,20) }
        val scroll = ScrollView(this)
        val content = LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
        content.addView(TextView(this).apply { text="🌙  Dream Sounds"; textSize=27f; setTextColor(Color.WHITE); gravity=Gravity.CENTER; setPadding(0,12,0,4) })
        content.addView(TextView(this).apply { text="اصنع أجواءك الخاصة للنوم"; textSize=14f; setTextColor(0xFFAFB9D0.toInt()); gravity=Gravity.CENTER; setPadding(0,0,0,20) })
        status = TextView(this).apply { text="اختر الأصوات التي تحبها"; textSize=14f; setTextColor(accent); gravity=Gravity.CENTER; setPadding(8,10,8,14) }
        content.addView(status)
        soundNames.forEach { name -> content.addView(soundCard(name)) }
        val controls = LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setBackgroundColor(card); setPadding(14,12,14,12) }
        controls.addView(sectionTitle("أدوات النوم"))
        val timerRow = LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL; gravity=Gravity.CENTER_VERTICAL }
        val timer = Spinner(this)
        timer.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, listOf("بدون مؤقت","15 دقيقة","30 دقيقة","45 دقيقة","60 دقيقة","90 دقيقة","120 دقيقة"))
        timerRow.addView(timer, LinearLayout.LayoutParams(0,-2,1f))
        val timerBtn = button("بدء المؤقت")
        timerBtn.setOnClickListener {
            val mins = listOf(0,15,30,45,60,90,120)[timer.selectedItemPosition]
            service("SET_TIMER") { putExtra("minutes", mins) }
            status.text = if(mins==0) "المؤقت متوقف" else "سيتم خفض الصوت ثم إيقافه بعد $mins دقيقة"
        }
        timerRow.addView(timerBtn)
        controls.addView(timerRow)
        controls.addView(button("حفظ الخلطة الحالية").apply { setOnClickListener { savePreset() } })
        controls.addView(button("استعادة آخر خلطة").apply { setOnClickListener { loadPreset() } })
        controls.addView(button("إيقاف جميع الأصوات").apply { setOnClickListener { soundNames.forEach { active[it]=false }; service("ALL_OFF"); buildUi() } })
        controls.addView(button("جدولة التشغيل اليومي").apply { setOnClickListener { scheduleAlarm() } })
        content.addView(controls)
        scroll.addView(content)
        root.addView(scroll)
        setContentView(root)
    }
    private fun soundCard(name: String): View {
        val box = LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setBackgroundColor(card); setPadding(14,10,14,10) }
        val top = LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL; gravity=Gravity.CENTER_VERTICAL }
        val check = CheckBox(this).apply {
            text=labels[name] ?: name; textSize=17f; setTextColor(Color.WHITE); buttonTintList=android.content.res.ColorStateList.valueOf(accent)
            isChecked=active[name] == true
            setOnCheckedChangeListener { _, checked ->
                active[name]=checked
                service("PLAY") { putExtra("name",name); putExtra("enabled",checked); putExtra("volume",volumes[name] ?: 0.65f) }
                refreshStatus()
            }
        }
        top.addView(check, LinearLayout.LayoutParams(0,-2,1f))
        val percent = TextView(this).apply { text="${((volumes[name]?:0.65f)*100).toInt()}%"; setTextColor(accent) }
        top.addView(percent)
        box.addView(top)
        val seek = SeekBar(this).apply {
            max=100; progress=((volumes[name]?:0.65f)*100).toInt()
            progressTintList=android.content.res.ColorStateList.valueOf(accent)
            thumbTintList=android.content.res.ColorStateList.valueOf(Color.WHITE)
            setOnSeekBarChangeListener(object: SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, p: Int, user: Boolean) {
                    volumes[name]=p/100f; percent.text="$p%"
                    if(active[name]==true) service("PLAY") { putExtra("name",name); putExtra("enabled",true); putExtra("volume",p/100f) }
                }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })
        }
        box.addView(seek)
        return box.apply { layoutParams=LinearLayout.LayoutParams(-1,-2).apply { bottomMargin=9 } }
    }
    private fun sectionTitle(s:String)=TextView(this).apply { text=s; textSize=19f; setTextColor(Color.WHITE); setPadding(0,0,0,10) }
    private fun button(text:String)=Button(this).apply { this.text=text; textSize=13f; setTextColor(Color.WHITE); backgroundTintList=android.content.res.ColorStateList.valueOf(0xFF35466F.toInt()); isAllCaps=false }
    private fun service(action:String, extras: Intent.()->Unit = {}) {
        val i=Intent(this,SoundService::class.java).apply { this.action=action; extras() }
        ContextCompat.startForegroundService(this,i)
    }
    private fun refreshStatus() {
        val count=active.values.count { it }
        status.text=if(count==0) "اختر الأصوات التي تحبها" else "يعمل الآن $count أصوات"
    }
    private fun savePreset() {
        val json=JSONObject()
        soundNames.forEach { json.put(it, JSONObject().put("on",active[it] == true).put("volume",volumes[it] ?: 0.65f)) }
        prefs.edit().putString("last_preset",json.toString()).apply()
        Toast.makeText(this,"تم حفظ الخلطة",Toast.LENGTH_SHORT).show()
    }
    private fun loadPreset() {
        val raw=prefs.getString("last_preset",null) ?: run { Toast.makeText(this,"لا توجد خلطة محفوظة",Toast.LENGTH_SHORT).show(); return }
        val json=JSONObject(raw)
        soundNames.forEach { name ->
            val item=json.optJSONObject(name) ?: return@forEach
            active[name]=item.optBoolean("on",false); volumes[name]=item.optDouble("volume",0.65).toFloat()
            service("PLAY") { putExtra("name",name); putExtra("enabled",active[name] == true); putExtra("volume",volumes[name] ?: 0.65f) }
        }
        buildUi()
    }
    private fun scheduleAlarm() {
        val cal=Calendar.getInstance()
        TimePickerDialog(this,{ _, hour, minute ->
            val target=Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY,hour); set(Calendar.MINUTE,minute); set(Calendar.SECOND,0)
                if(timeInMillis<=System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR,1)
            }
            val alarm= getSystemService(ALARM_SERVICE) as AlarmManager
            val intent=Intent(this,AlarmReceiver::class.java)
            val pending=PendingIntent.getBroadcast(this,91,intent,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            if(Build.VERSION.SDK_INT>=31 && !alarm.canScheduleExactAlarms()) {
                Toast.makeText(this,"فعّل إذن المنبهات الدقيقة من إعدادات التطبيق",Toast.LENGTH_LONG).show()
                startActivity(Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM))
            } else {
                alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,target.timeInMillis,pending)
                Toast.makeText(this,"تمت جدولة التشغيل في ${String.format("%02d:%02d",hour,minute)}",Toast.LENGTH_LONG).show()
            }
        },cal.get(Calendar.HOUR_OF_DAY),cal.get(Calendar.MINUTE),true).show()
    }
    private fun requestNotificationPermission() {
        if(Build.VERSION.SDK_INT>=33 && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED)
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),7)
    }
}
