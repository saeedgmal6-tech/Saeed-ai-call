package com.saeed.aicall

import android.Manifest
import android.app.Activity
import android.app.role.RoleManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import java.util.Locale

class MainActivity : Activity(), TextToSpeech.OnInitListener {

    private lateinit var root: LinearLayout
    private lateinit var content: LinearLayout
    private lateinit var status: TextView
    private lateinit var tts: TextToSpeech
    private var ttsReady = false
    private val prefs by lazy { getSharedPreferences("saeed_ai_call", MODE_PRIVATE) }
    private val bg = Color.rgb(10, 15, 29)
    private val panel = Color.rgb(22, 31, 51)
    private val accent = Color.rgb(72, 190, 180)
    private val white = Color.rgb(240, 245, 250)
    private val muted = Color.rgb(160, 176, 194)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tts = TextToSpeech(this, this)
        showHome()
    }

    override fun onInit(result: Int) {
        ttsReady = result == TextToSpeech.SUCCESS
        if (ttsReady) tts.language = Locale("ar", "EG")
    }

    private fun base(title: String, subtitle: String) {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bg)
            setPadding(dp(18), dp(18), dp(18), dp(14))
        }
        val header = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        header.addView(label("SAEED AI", 13, accent, true))
        header.addView(label(title, 27, white, true))
        header.addView(label(subtitle, 13, muted, false))
        root.addView(header)
        root.addView(space(16))
        content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val scroll = ScrollView(this)
        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        val nav = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, dp(10), 0, 0)
        }
        nav.addView(navButton("الرئيسية") { showHome() }, weight())
        nav.addView(navButton("المحادثة") { showChat() }, weight())
        nav.addView(navButton("الذاكرة") { showMemory() }, weight())
        nav.addView(navButton("الإعدادات") { showSettings() }, weight())
        root.addView(nav)
        setContentView(root)
    }

    private fun showHome() {
        base("مساعدك الشخصي", "نسخة الهاتف • واجهة عربية")
        content.addView(card {
            addView(label("مساء الخير 👋", 21, white, true))
            addView(label("أنا SAEED AI. مركز التحكم الخاص بمساعدك الشخصي.", 14, muted, false))
            addView(space(12))
            addView(label("حالة المحرك الذكي", 13, muted, true))
            addView(label("غير متصل بعد — الواجهة والخدمات المحلية جاهزة للتطوير.", 14, Color.rgb(255, 205, 120), true))
        })
        content.addView(space(12))
        content.addView(actionCard("ابدأ محادثة", "تحدث أو اكتب للمساعد", "فتح المحادثة") { showChat() })
        content.addView(actionCard("الذاكرة الشخصية", "إدارة الملاحظات التي تحفظها على الجهاز", "فتح الذاكرة") { showMemory() })
        content.addView(actionCard("مساعد المكالمات", "إعداد رقم محدد وفحص المكالمات الواردة", "إعداد المكالمات") { showSettings() })
        content.addView(space(10))
        content.addView(label("تنبيه: لا يوجد استماع خفي أو تسجيل تلقائي. الميكروفون يعمل فقط بعد ضغطك على زر التحدث.", 12, muted, false))
    }

    private fun showChat() {
        base("المحادثة", "اكتب رسالتك أو اضغط على الميكروفون")
        val history = prefs.getString("chat_history", "").orEmpty()
        val transcript = TextView(this).apply {
            text = if (history.isBlank()) "SAEED AI\nأهلاً بيك. واجهة المحادثة جاهزة. محرك الذكاء الاصطناعي السحابي/المحلي لم يتم ربطه بعد." else history
            textSize = 14f
            setTextColor(white)
            setPadding(dp(14), dp(14), dp(14), dp(14))
            background = rounded(panel, 16)
        }
        content.addView(transcript)
        content.addView(space(12))
        val input = EditText(this).apply {
            hint = "اكتب رسالتك هنا..."
            setHintTextColor(muted)
            setTextColor(white)
            textSize = 15f
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = rounded(panel, 12)
        }
        content.addView(input)
        content.addView(space(8))
        val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        actions.addView(button("إرسال", accent) {
            val message = input.text.toString().trim()
            if (message.isNotEmpty()) {
                val old = prefs.getString("chat_history", "").orEmpty()
                val updated = (old + "\n\nأنت: " + message + "\nSAEED AI: تم استلام رسالتك. ربط نموذج الذكاء الاصطناعي لم يكتمل بعد، لذلك لن أختلق إجابة.").takeLast(7000)
                prefs.edit().putString("chat_history", updated).apply()
                showChat()
            }
        }, 1f)
        actions.addView(button("🎙 تحدث", panel) { startVoiceInput() }, 1f)
        content.addView(actions)
        content.addView(space(10))
        content.addView(button("مسح سجل المحادثة", Color.rgb(110, 55, 65)) {
            prefs.edit().remove("chat_history").apply()
            showChat()
        })
    }

    private fun startVoiceInput() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_AUDIO)
            return
        }
        try {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ar-EG")
                putExtra(RecognizerIntent.EXTRA_PROMPT, "اتكلم مع SAEED AI")
            }
            startActivityForResult(intent, REQUEST_SPEECH)
        } catch (_: Exception) {
            statusMessage("خدمة تحويل الكلام إلى نص غير متاحة على الجهاز.")
        }
    }

    private fun showMemory() {
        base("الذاكرة", "ملاحظات محلية محفوظة على هاتفك")
        val notes = prefs.getString("personal_memory", "").orEmpty()
        content.addView(label("محتوى الذاكرة", 15, white, true))
        val list = TextView(this).apply {
            text = if (notes.isBlank()) "لسه مفيش ملاحظات محفوظة." else notes
            textSize = 14f
            setTextColor(white)
            setPadding(dp(14), dp(14), dp(14), dp(14))
            background = rounded(panel, 14)
        }
        content.addView(list)
        content.addView(space(12))
        val entry = EditText(this).apply {
            hint = "اكتب معلومة تريد حفظها..."
            setHintTextColor(muted)
            setTextColor(white)
            background = rounded(panel, 12)
            setPadding(dp(14), dp(12), dp(14), dp(12))
        }
        content.addView(entry)
        content.addView(space(8))
        content.addView(button("حفظ في الذاكرة", accent) {
            val value = entry.text.toString().trim()
            if (value.isNotEmpty()) {
                val updated = (notes + "\n• " + value).takeLast(10000)
                prefs.edit().putString("personal_memory", updated).apply()
                showMemory()
            }
        })
        content.addView(space(8))
        content.addView(button("مسح الذاكرة المحلية", Color.rgb(110, 55, 65)) {
            prefs.edit().remove("personal_memory").apply()
            showMemory()
        })
        content.addView(space(10))
        content.addView(label("الذاكرة الحالية محفوظة داخل التطبيق على هذا الهاتف فقط. لم يتم بعد بناء استرجاع ذكي دلالي أو مزامنة مشفرة.", 12, muted, false))
    }

    private fun showSettings() {
        base("الإعدادات", "الخصوصية والصوت والمكالمات")
        content.addView(actionCard("الصوت", "النطق العربي عبر محرك تحويل النص إلى كلام في Android", "اختبار الصوت") {
            if (ttsReady) tts.speak("أهلاً، أنا سعيد إيه آي", TextToSpeech.QUEUE_FLUSH, null, "saeed-test")
            else statusMessage("محرك الصوت لم يجهز بعد.")
        })
        content.addView(space(10))
        content.addView(label("رقم المكالمة المستهدف", 15, white, true))
        val number = EditText(this).apply {
            hint = "أدخل رقم الهاتف"
            inputType = InputType.TYPE_CLASS_PHONE
            setText(prefs.getString("target_caller", ""))
            setTextColor(white)
            setHintTextColor(muted)
            background = rounded(panel, 12)
            setPadding(dp(14), dp(12), dp(14), dp(12))
        }
        content.addView(number)
        content.addView(space(8))
        content.addView(button("حفظ الرقم", accent) {
            prefs.edit().putString("target_caller", number.text.toString().trim()).apply()
            statusMessage("تم حفظ الرقم على الهاتف.")
        })
        content.addView(space(8))
        content.addView(button("تفعيل فحص المكالمات", panel) {
            val manager = getSystemService(RoleManager::class.java)
            if (manager != null && manager.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING)) {
                startActivityForResult(manager.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING), REQUEST_SCREENING)
            } else statusMessage("ميزة فحص المكالمات غير متاحة على هذا الجهاز.")
        })
        content.addView(space(12))
        content.addView(label("الخصوصية", 16, white, true))
        content.addView(label("• لا يتم تشغيل الميكروفون في الخلفية.\n• لا يتم إرسال جهات الاتصال أو الملاحظات لأي خادم.\n• صلاحية فحص المكالمات لا تعني أن التطبيق يستطيع سماع صوت الطرف الآخر.\n• الرد التلقائي أو التحكم في صوت المكالمات يخضعان لقيود النظام وشركة الاتصالات.", 13, muted, false))
        content.addView(space(10))
        content.addView(label("الإصدار التجريبي 0.2.0 • SAEED AI", 12, muted, false))
    }

    private fun actionCard(title: String, desc: String, action: String, click: () -> Unit): View {
        return card {
            addView(label(title, 17, white, true))
            addView(label(desc, 13, muted, false))
            addView(space(8))
            addView(button(action, accent, click))
        }
    }

    private fun card(build: LinearLayout.() -> Unit): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
            background = rounded(panel, 18)
            build()
        }
    }

    private fun navButton(text: String, click: () -> Unit): Button = button(text, panel, click)
    private fun button(text: String, color: Int, click: () -> Unit, weight: Float = 0f): Button {
        return Button(this).apply {
            this.text = text
            textSize = 12f
            setTextColor(if (color == accent) bg else white)
            background = rounded(color, 12)
            isAllCaps = false
            setOnClickListener { click() }
            if (weight > 0f) layoutParams = LinearLayout.LayoutParams(0, dp(48), weight).apply { setMargins(dp(3), 0, dp(3), 0) }
            else layoutParams = LinearLayout.LayoutParams(-1, dp(48)).apply { bottomMargin = dp(4) }
        }
    }

    private fun label(text: String, size: Int, color: Int, bold: Boolean): TextView {
        return TextView(this).apply {
            this.text = text
            textSize = size.toFloat()
            setTextColor(color)
            if (bold) setTypeface(typeface, Typeface.BOLD)
            textDirection = View.TEXT_DIRECTION_LOCALE
            setPadding(0, dp(3), 0, dp(3))
        }
    }

    private fun rounded(color: Int, radius: Int): GradientDrawable =
        GradientDrawable().apply { setColor(color); cornerRadius = dp(radius).toFloat() }

    private fun space(value: Int): View = View(this).apply { layoutParams = LinearLayout.LayoutParams(1, dp(value)) }
    private fun weight() = LinearLayout.LayoutParams(0, dp(46), 1f)
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
    private fun statusMessage(message: String) {
        android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_LONG).show()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_SPEECH && resultCode == RESULT_OK) {
            val spoken = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull().orEmpty()
            prefs.edit().putString("chat_history", prefs.getString("chat_history", "").orEmpty() + "\n\nأنت (صوت): " + spoken).apply()
            showChat()
        }
    }

    override fun onDestroy() {
        if (::tts.isInitialized) {
            tts.stop()
            tts.shutdown()
        }
        super.onDestroy()
    }

    companion object {
        private const val REQUEST_AUDIO = 4101
        private const val REQUEST_SPEECH = 4102
        private const val REQUEST_SCREENING = 4103
    }
}
