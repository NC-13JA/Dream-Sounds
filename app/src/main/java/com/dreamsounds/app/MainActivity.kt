package com.dreamsounds.app

import android.app.*
import android.content.*
import android.graphics.*
import android.graphics.drawable.GradientDrawable
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
    private val soundNames = listOf(
        "rain","heavy_rain","thunder","campfire","birds","crickets","owl","forest",
        "wind","leaves","ocean","river","waterfall","underwater",
        "white_noise","pink_noise","brown_noise","fan","air_conditioner",
        "train","airplane","cafe","heartbeat","singing_bowl"
    )
    private val labels = mapOf(
        "rain" to "مطر هادئ 🌧️","heavy_rain" to "مطر غزير 🌧️","thunder" to "رعد وعواصف ⛈️",
        "campfire" to "نار المخيم 🔥","birds" to "زقزقة الطيور 🐦","crickets" to "صراصير الليل 🦗",
        "owl" to "بومة الليل 🦉","forest" to "أجواء الغابة 🌲","wind" to "نسيم الرياح 🍃",
        "leaves" to "حفيف أوراق الشجر 🍂","ocean" to "أمواج البحر 🌊","river" to "جدول ماء 🏞️",
        "waterfall" to "شلال 💧","underwater" to "أعماق البحر 🫧","white_noise" to "ضوضاء بيضاء",
        "pink_noise" to "ضوضاء وردية","brown_noise" to "ضوضاء بنية","fan" to "مروحة كهربائية",
        "air_conditioner" to "مكيف الهواء ❄️","train" to "قطار بعيد 🚆","airplane" to "طائرة في الجو ✈️",
        "cafe" to "ضجيج مقهى هادئ ☕","heartbeat" to "نبضات قلب ❤️","singing_bowl" to "وعاء تأمل 🔔"
    )
    private val groups = linkedMapOf(
        "🌧️ الطبيعة والطقس" to listOf("rain","heavy_rain","thunder","wind","leaves"),
        "🌲 الغابة والحياة البرية" to listOf("campfire","birds","crickets","owl","forest"),
        "🌊 الماء والبحر" to listOf("ocean","river","waterfall","underwater"),
        "🌙 أصوات تساعد على النوم" to listOf("white_noise","pink_noise","brown_noise","fan","air_conditioner"),
        "🚆 أجواء وأصوات أخرى" to listOf("train","airplane","cafe","heartbeat","singing_bowl")
    )
    private val active = mutableMapOf<String, Boolean>()
    private val volumes = mutableMapOf<String, Float>()
    private lateinit var status: TextView
    private val bg = Color.rgb(5,10,29)
    private val card = Color.rgb(17,27,55)
    private val accent = Color.rgb(151,177,255)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        soundNames.forEach { active[it] = false; volumes[it] = 0.65f }
        requestNotificationPermission()
        buildUi()
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setBackgroundColor(bg); setPadding(14,10,14,18) }
        val scroll = ScrollView(this)
        val content = LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
        content.addView(NightSkyView(this), LinearLayout.LayoutParams(-1,190).apply { bottomMargin=12 })
        content.addView(TextView(this).apply {
            text="اختر أصواتك وامزجها كما تحب"
            textSize=15f; setTextColor(0xFFDCE5FF.toInt()); gravity=Gravity.CENTER
            setPadding(0,2,0,10)
        })
        status = TextView(this).apply {
            text=active.values.count { it }.let { if(it==0) "24 صوتاً متاحاً دون إنترنت" else "يعمل الآن $it أصوات" }
            textSize=14f; setTextColor(accent); gravity=Gravity.CENTER
            background=rounded(0xFF17264A.toInt(),18); setPadding(12,12,12,12)
        }
        content.addView(status, LinearLayout.LayoutParams(-1,-2).apply { bottomMargin=14 })
        groups.forEach { (heading, names) ->
            content.addView(sectionTitle(heading))
            names.forEach { name -> content.addView(soundCard(name)) }
        }
        val controls = LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; background=rounded(card,20); setPadding(14,14,14,14) }
        controls.addView(sectionTitle("🌙 أدوات النوم"))
        val timerRow = LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL; gravity=Gravity.CENTER_VERTICAL }
        val timer = Spinner(this)
        timer.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, listOf("بدون مؤقت","15 دقيقة","30 دقيقة","45 دقيقة","60 دقيقة","90 دقيقة","120 دقيقة","180 دقيقة"))
        timerRow.addView(timer, LinearLayout.LayoutParams(0,-2,1f))
        timerRow.addView(button("بدء المؤقت").apply {
            setOnClickListener {
                val mins=listOf(0,15,30,45,60,90,120,180)[timer.selectedItemPosition]
                service("SET_TIMER") { putExtra("minutes",mins) }
                status.text=if(mins==0) "المؤقت متوقف" else "سيتم خفض الصوت تدريجياً بعد $mins دقيقة"
            }
        })
        controls.addView(timerRow)
        controls.addView(button("♡  حفظ الخلطة الحالية").apply { setOnClickListener { savePreset() } })
        controls.addView(button("استعادة آخر خلطة").apply { setOnClickListener { loadPreset() } })
        controls.addView(button("إيقاف جميع الأصوات").apply {
            setOnClickListener { soundNames.forEach { active[it]=false }; service("ALL_OFF"); buildUi() }
        })
        controls.addView(button("جدولة التشغيل اليومي").apply { setOnClickListener { scheduleAlarm() } })
        content.addView(controls, LinearLayout.LayoutParams(-1,-2).apply { topMargin=10 })
        content.addView(TextView(this).apply {
            text="Dream Sounds  •  استرخِ، تنفّس، ونَم بسلام"
            textSize=12f; setTextColor(0xFF8494BD.toInt()); gravity=Gravity.CENTER; setPadding(0,20,0,10)
        })
        scroll.addView(content)
        root.addView(scroll)
        setContentView(root)
    }

    private fun soundCard(name:String): View {
        val box=LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL; background=rounded(card,16); setPadding(12,7,12,7)
        }
        val top=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL; gravity=Gravity.CENTER_VERTICAL }
        val check=CheckBox(this).apply {
            text=labels[name] ?: name; textSize=15f; setTextColor(Color.WHITE)
            buttonTintList=android.content.res.ColorStateList.valueOf(accent)
            isChecked=active[name]==true
            setOnCheckedChangeListener { _, checked ->
                active[name]=checked
                service("PLAY") { putExtra("name",name); putExtra("enabled",checked); putExtra("volume",volumes[name] ?: 0.65f) }
                refreshStatus()
            }
        }
        top.addView(check,LinearLayout.LayoutParams(0,-2,1f))
        val percent=TextView(this).apply { text="${((volumes[name]?:0.65f)*100).toInt()}%"; setTextColor(accent); textSize=12f }
        top.addView(percent)
        box.addView(top)
        box.addView(SeekBar(this).apply {
            max=100; progress=((volumes[name]?:0.65f)*100).toInt()
            progressTintList=android.content.res.ColorStateList.valueOf(accent)
            thumbTintList=android.content.res.ColorStateList.valueOf(Color.WHITE)
            setOnSeekBarChangeListener(object:SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb:SeekBar?,p:Int,user:Boolean) {
                    volumes[name]=p/100f; percent.text="$p%"
                    if(active[name]==true) service("PLAY") { putExtra("name",name); putExtra("enabled",true); putExtra("volume",p/100f) }
                }
                override fun onStartTrackingTouch(sb:SeekBar?) {}
                override fun onStopTrackingTouch(sb:SeekBar?) {}
            })
        })
        return box.apply { layoutParams=LinearLayout.LayoutParams(-1,-2).apply { bottomMargin=7 } }
    }

    private fun rounded(color:Int,radius:Int)=GradientDrawable().apply {
        setColor(color)
        cornerRadius=radius.toFloat()*resources.displayMetrics.density
        setStroke((1*resources.displayMetrics.density).toInt(),0xFF293B68.toInt())
    }
    private fun sectionTitle(s:String)=TextView(this).apply {
        text=s; textSize=17f; setTextColor(Color.WHITE); setPadding(4,12,4,8)
    }
    private fun button(text:String)=Button(this).apply {
        this.text=text; textSize=13f; setTextColor(Color.WHITE)
        backgroundTintList=android.content.res.ColorStateList.valueOf(0xFF354C83.toInt()); isAllCaps=false
    }
    private fun service(action:String,extras:Intent.()->Unit={}) {
        val i=Intent(this,SoundService::class.java).apply { this.action=action; extras() }
        ContextCompat.startForegroundService(this,i)
    }
    private fun refreshStatus() {
        val count=active.values.count { it }
        status.text=if(count==0) "24 صوتاً متاحاً دون إنترنت" else "يعمل الآن $count أصوات"
    }
    private fun savePreset() {
        val json=JSONObject()
        soundNames.forEach { json.put(it,JSONObject().put("on",active[it]==true).put("volume",volumes[it]?:0.65f)) }
        prefs.edit().putString("last_preset",json.toString()).apply()
        Toast.makeText(this,"تم حفظ الخلطة",Toast.LENGTH_SHORT).show()
    }
    private fun loadPreset() {
        val raw=prefs.getString("last_preset",null) ?: run {
            Toast.makeText(this,"لا توجد خلطة محفوظة",Toast.LENGTH_SHORT).show(); return
        }
        val json=JSONObject(raw)
        soundNames.forEach { name ->
            val item=json.optJSONObject(name) ?: return@forEach
            active[name]=item.optBoolean("on",false)
            volumes[name]=item.optDouble("volume",0.65).toFloat()
            service("PLAY") { putExtra("name",name); putExtra("enabled",active[name]==true); putExtra("volume",volumes[name]?:0.65f) }
        }
        buildUi()
    }
    private fun scheduleAlarm() {
        val cal=Calendar.getInstance()
        TimePickerDialog(this,{ _,hour,minute ->
            val target=Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY,hour); set(Calendar.MINUTE,minute); set(Calendar.SECOND,0)
                if(timeInMillis<=System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR,1)
            }
            val alarm=getSystemService(ALARM_SERVICE) as AlarmManager
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

    private class NightSkyView(context:Context):View(context) {
        private val p=Paint(Paint.ANTI_ALIAS_FLAG)
        override fun onDraw(canvas:Canvas) {
            super.onDraw(canvas)
            val w=width.toFloat(); val h=height.toFloat()
            p.shader=LinearGradient(0f,0f,w,h, intArrayOf(0xFF07112F.toInt(),0xFF182D61.toInt(),0xFF402B68.toInt()),null,Shader.TileMode.CLAMP)
            canvas.drawRoundRect(0f,0f,w,h,24f,24f,p); p.shader=null
            p.color=0xFFFFE9A6.toInt(); canvas.drawCircle(w*.78f,h*.25f,h*.13f,p)
            p.color=0xFF182D61.toInt(); canvas.drawCircle(w*.83f,h*.20f,h*.12f,p)
            val random=java.util.Random(22)
            p.color=Color.WHITE
            repeat(45) { canvas.drawCircle(random.nextFloat()*w,random.nextFloat()*h*.68f,random.nextFloat()*1.7f+.5f,p) }
            p.color=0xFF101D43.toInt()
            val back=Path().apply { moveTo(0f,h*.75f); lineTo(w*.23f,h*.38f); lineTo(w*.43f,h*.74f); lineTo(w*.68f,h*.42f); lineTo(w,h*.77f); lineTo(w,h); lineTo(0f,h); close() }
            canvas.drawPath(back,p)
            p.color=0xFF080F27.toInt()
            val front=Path().apply { moveTo(0f,h*.86f); lineTo(w*.27f,h*.58f); lineTo(w*.48f,h*.87f); lineTo(w*.76f,h*.60f); lineTo(w,h*.87f); lineTo(w,h); lineTo(0f,h); close() }
            canvas.drawPath(front,p)
            p.color=0xFFFFD27A.toInt()
            canvas.drawRoundRect(w*.56f,h*.63f,w*.79f,h*.91f,5f,5f,p)
            p.color=0xFF152449.toInt()
            val roof=Path().apply { moveTo(w*.53f,h*.64f); lineTo(w*.675f,h*.49f); lineTo(w*.82f,h*.64f); close() }
            canvas.drawPath(roof,p)
            p.color=0xFFFFE6A5.toInt()
            canvas.drawRect(w*.61f,h*.70f,w*.65f,h*.76f,p); canvas.drawRect(w*.70f,h*.70f,w*.74f,h*.76f,p)
            p.color=Color.WHITE; p.textSize=23f; p.typeface=Typeface.create("sans-serif",Typeface.BOLD)
            canvas.drawText("Dream Sounds",18f,34f,p)
            p.textSize=13f; p.typeface=Typeface.DEFAULT
            canvas.drawText("أصوات هادئة لنوم أعمق",20f,56f,p)
        }
    }
}
