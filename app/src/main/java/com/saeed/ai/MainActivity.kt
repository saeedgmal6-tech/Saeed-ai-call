package com.saeed.ai

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.view.Gravity
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.content.Context
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.concurrent.Executors

class MainActivity : Activity(), TextToSpeech.OnInitListener {

    private val bg = Color.rgb(10, 16, 24)
    private val panel = Color.rgb(20, 30, 43)
    private val accent = Color.rgb(85, 214, 194)
    private val white = Color.rgb(239, 245, 250)
    private val muted = Color.rgb(151, 168, 184)
    private val prefs by lazy { getSharedPreferences("saeed_ai_private", MODE_PRIVATE) }
    private val worker = Executors.newSingleThreadExecutor()
    private lateinit var tts: TextToSpeech
    private lateinit var root: LinearLayout
    private lateinit var content: LinearLayout
    private lateinit var scroll: ScrollView
    private lateinit var input: EditText
    private lateinit var status: TextView
    private var currentTab = "chat"
    private var history = JSONArray()
    private var memory = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = bg
        window.navigationBarColor = bg
        tts = TextToSpeech(this, this)
        history = try { JSONArray(prefs.getString("history", "[]")) } catch (_: Exception) { JSONArray() }
        memory = prefs.getString("memory", "") ?: ""
        showShell()
        showChat()
    }

    override fun onInit(result: Int) {
        if (result == TextToSpeech.SUCCESS) tts.language = Locale("ar", "EG")
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun text(value: String, size: Float = 15f, color: Int = white, bold: Boolean = false): TextView {
        return TextView(this).apply {
            this.text = value
            textSize = size
            setTextColor(color)
            if (bold) setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.RIGHT
            textDirection = View.TEXT_DIRECTION_RTL
            setPadding(dp(4), dp(5), dp(4), dp(5))
        }
    }

    private fun button(label: String, action: () -> Unit): Button {
        return Button(this).apply {
            text = label
            textSize = 13f
            isAllCaps = false
            setTextColor(white)
            setBackgroundTintList(android.content.res.ColorStateList.valueOf(panel))
            setOnClickListener { action() }
        }
    }

    private fun showShell() {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bg)
            setPadding(dp(16), dp(12), dp(16), dp(8))
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(8), 0, dp(12))
        }
        header.addView(text("SAEED AI", 25f, accent, true))
        header.addView(text("مساعدك الشخصي الذكي", 13f, muted))
        status = text("جاهز • المحرك السحابي يحتاج مفتاح API", 11f, muted)
        header.addView(status)
        root.addView(header)
        val divider = View(this).apply { setBackgroundColor(panel) }
        root.addView(divider, LinearLayout.LayoutParams(-1, dp(1)))
        scroll = ScrollView(this)
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(12), 0, dp(12))
        }
        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        input = EditText(this).apply {
            hint = "اكتب رسالتك إلى SAEED AI..."
            textSize = 15f
            setTextColor(white)
            setHintTextColor(muted)
            setSingleLine(false)
            maxLines = 4
            setPadding(dp(12), dp(8), dp(12), dp(8))
            setBackgroundTintList(android.content.res.ColorStateList.valueOf(accent))
            textDirection = View.TEXT_DIRECTION_RTL
        }
        val composer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        composer.addView(button("🎙") { startVoiceInput() }, LinearLayout.LayoutParams(dp(54), dp(54)))
        composer.addView(input, LinearLayout.LayoutParams(0, dp(54), 1f))
        composer.addView(button("إرسال") { sendMessage() }, LinearLayout.LayoutParams(dp(76), dp(54)))
        root.addView(composer)
        val nav = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, dp(8), 0, 0)
        }
        nav.addView(button("المحادثة") { showChat() }, LinearLayout.LayoutParams(0, dp(48), 1f))
        nav.addView(button("الذاكرة") { showMemory() }, LinearLayout.LayoutParams(0, dp(48), 1f))
        nav.addView(button("الإعدادات") { showSettings() }, LinearLayout.LayoutParams(0, dp(48), 1f))
        root.addView(nav)
        setContentView(root)
    }

    private fun resetContent() {
        content.removeAllViews()
        input.visibility = if (currentTab == "chat") View.VISIBLE else View.GONE
        root.getChildAt(root.childCount - 2).visibility = if (currentTab == "chat") View.VISIBLE else View.GONE
    }

    private fun showChat() {
        currentTab = "chat"
        resetContent()
        content.addView(text("أهلاً بيك. أنا SAEED AI.", 20f, white, true))
        content.addView(text("اكتب سؤالك أو استخدم زر الميكروفون. أقدر حاليًا أتواصل مع نموذج Gemini بعد إضافة مفتاح API، وأنفذ مجموعة من أوامر الهاتف المدعومة.", 14f, muted))
        for (i in 0 until history.length()) {
            val item = history.optJSONObject(i) ?: continue
            addBubble(item.optString("role"), item.optString("text"))
        }
        scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
    }

    private fun addBubble(role: String, message: String) {
        val isUser = role == "user"
        val bubble = TextView(this).apply {
            text = (if (isUser) "أنت\n" else "SAEED AI\n") + message
            textSize = 15f
            setTextColor(white)
            textDirection = View.TEXT_DIRECTION_RTL
            gravity = Gravity.RIGHT
            setPadding(dp(14), dp(10), dp(14), dp(10))
            setBackgroundColor(if (isUser) Color.rgb(29, 65, 72) else panel)
        }
        val params = LinearLayout.LayoutParams(-1, -2)
        params.setMargins(0, dp(5), 0, dp(5))
        content.addView(bubble, params)
    }

    private fun sendMessage() {
        val message = input.text.toString().trim()
        if (message.isEmpty()) return
        input.setText("")
        (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager)
            .hideSoftInputFromWindow(input.windowToken, 0)
        appendHistory("user", message)
        addBubble("user", message)
        val local = handleLocalCommand(message)
        if (local != null) {
            appendHistory("model", local)
            addBubble("model", local)
            speak(local)
            return
        }
        val key = prefs.getString("api_key", "") ?: ""
        if (key.isBlank()) {
            val answer = "علشان أجاوبك بذكاء فعلي، لازم توصيل محرك AI. لسه مفيش مفتاح Gemini API محفوظ. افتح الإعدادات وأضف المفتاح. التطبيق نفسه لا يحتوي نموذجًا محليًا كبيرًا حتى الآن."
            appendHistory("model", answer)
            addBubble("model", answer)
            status.text = "المحرك غير متصل • أضف مفتاح API من الإعدادات"
            return
        }
        status.text = "جاري التفكير..."
        val loading = text("SAEED AI بيجهز الرد...", 13f, accent)
        content.addView(loading)
        worker.execute {
            val answer = try { askGemini(key, message) } catch (e: Exception) {
                "حصل خطأ في الاتصال بالمحرك: " + (e.message ?: "خطأ غير معروف")
            }
            runOnUiThread {
                content.removeView(loading)
                appendHistory("model", answer)
                addBubble("model", answer)
                status.text = "متصل عند الطلب • Gemini"
                speak(answer)
                scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
            }
        }
    }

    private fun appendHistory(role: String, value: String) {
        history.put(JSONObject().put("role", role).put("text", value))
        while (history.length() > 60) history.remove(0)
        prefs.edit().putString("history", history.toString()).apply()
    }

    private fun askGemini(key: String, prompt: String): String {
        val model = prefs.getString("model", "gemini-2.5-flash") ?: "gemini-2.5-flash"
        val url = URL("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$key")
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 20000
            readTimeout = 60000
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
        }
        val contents = JSONArray()
        val start = maxOf(0, history.length() - 16)
        var lastRole = ""
        var lastText = ""
        for (i in start until history.length()) {
            val item = history.optJSONObject(i) ?: continue
            val role = if (item.optString("role") == "user") "user" else "model"
            lastRole = role
            lastText = item.optString("text")
            contents.put(JSONObject().put("role", role).put("parts", JSONArray().put(JSONObject().put("text", lastText))))
        }
        if (lastRole != "user" || lastText != prompt) {
            contents.put(JSONObject().put("role", "user").put("parts", JSONArray().put(JSONObject().put("text", prompt))))
        }
        val system = "أنت SAEED AI، مساعد شخصي عملي. تحدث باللهجة المصرية الواضحة ما لم يطلب المستخدم غير ذلك. كن دقيقًا، لا تدّع تنفيذ إجراء لم تنفذه، وصرّح بحدود معرفتك. الذاكرة الشخصية المتاحة: $memory"
        val body = JSONObject()
            .put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", system))))
            .put("contents", contents)
            .put("generationConfig", JSONObject().put("temperature", 0.7).put("maxOutputTokens", 2048))
        connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
        val code = connection.responseCode
        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
        val response = stream.bufferedReader().use { it.readText() }
        connection.disconnect()
        if (code !in 200..299) throw IllegalStateException("HTTP $code: " + response.take(500))
        val json = JSONObject(response)
        return json.getJSONArray("candidates").getJSONObject(0)
            .getJSONObject("content").getJSONArray("parts").getJSONObject(0).getString("text")
    }

    private fun handleLocalCommand(raw: String): String? {
        val q = raw.trim().lowercase(Locale("ar", "EG"))
        if (q in listOf("افتح يوتيوب", "افتح اليوتيوب", "youtube")) {
            openApp("com.google.android.youtube", "https://youtube.com")
            return "بحاول أفتح يوتيوب."
        }
        if (q in listOf("افتح واتساب", "افتح الواتس", "whatsapp")) {
            openApp("com.whatsapp", "https://whatsapp.com")
            return "بحاول أفتح واتساب."
        }
        if (q in listOf("افتح الإعدادات", "افتح الاعدادات", "settings")) {
            startActivity(Intent(android.provider.Settings.ACTION_SETTINGS))
            return "فتحت إعدادات الهاتف."
        }
        if (q == "امسح المحادثة" || q == "امسح سجل المحادثة") {
            history = JSONArray()
            prefs.edit().putString("history", "[]").apply()
            content.removeAllViews()
            return "تم مسح سجل المحادثة المحفوظ داخل التطبيق."
        }
        return null
    }

    private fun openApp(packageName: String, fallback: String) {
        val launch = packageManager.getLaunchIntentForPackage(packageName)
        if (launch != null) startActivity(launch)
        else startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(fallback)))
    }

    private fun startVoiceInput() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 41)
            return
        }
        try {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ar-EG")
                putExtra(RecognizerIntent.EXTRA_PROMPT, "اتكلم مع SAEED AI")
            }
            startActivityForResult(intent, 42)
        } catch (_: Exception) {
            Toast.makeText(this, "خدمة التعرف على الكلام غير متاحة على الجهاز.", Toast.LENGTH_LONG).show()
        }
    }

    @Deprecated("Legacy Android activity result API remains supported")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 42 && resultCode == Activity.RESULT_OK) {
            val spoken = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!spoken.isNullOrBlank()) {
                input.setText(spoken)
                sendMessage()
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 41 && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) startVoiceInput()
    }

    private fun speak(value: String) {
        if (::tts.isInitialized) tts.speak(value, TextToSpeech.QUEUE_FLUSH, null, "saeed-ai-response")
    }

    private fun showMemory() {
        currentTab = "memory"
        resetContent()
        content.addView(text("ذاكرة SAEED AI", 21f, white, true))
        content.addView(text("اكتب المعلومات التي تريد من المساعد تذكرها. تُحفظ محليًا على الهاتف وتُضاف إلى سياق المحادثة مع Gemini.", 13f, muted))
        val editor = EditText(this).apply {
            setText(memory)
            minLines = 8
            gravity = Gravity.TOP or Gravity.RIGHT
            textDirection = View.TEXT_DIRECTION_RTL
            setTextColor(white)
            setHintTextColor(muted)
            hint = "تفضيلاتك، معلومات وملاحظات مهمة..."
            setPadding(dp(12), dp(12), dp(12), dp(12))
            setBackgroundColor(panel)
        }
        content.addView(editor, LinearLayout.LayoutParams(-1, dp(220)))
        content.addView(button("حفظ الذاكرة") {
            memory = editor.text.toString()
            prefs.edit().putString("memory", memory).apply()
            Toast.makeText(this, "تم حفظ الذاكرة على الهاتف.", Toast.LENGTH_SHORT).show()
        })
        content.addView(button("مسح الذاكرة") {
            memory = ""
            editor.setText("")
            prefs.edit().remove("memory").apply()
            Toast.makeText(this, "تم مسح الذاكرة المحلية.", Toast.LENGTH_SHORT).show()
        })
    }

    private fun showSettings() {
        currentTab = "settings"
        resetContent()
        content.addView(text("إعدادات الذكاء", 21f, white, true))
        content.addView(text("الاتصال الحالي يستخدم Gemini API. المفتاح يُحفظ في مساحة التطبيق الخاصة على هذا الجهاز، وليس مشفرًا تشفيرًا مستقلًا.", 13f, muted))
        content.addView(text("مفتاح Gemini API", 14f, accent, true))
        val keyField = EditText(this).apply {
            setText(prefs.getString("api_key", ""))
            hint = "الصق مفتاح API هنا"
            inputType = 129
            setTextColor(white)
            setHintTextColor(muted)
            textDirection = View.LAYOUT_DIRECTION_LTR
            setPadding(dp(12), dp(10), dp(12), dp(10))
            setBackgroundColor(panel)
        }
        content.addView(keyField, LinearLayout.LayoutParams(-1, dp(54)))
        content.addView(text("اسم النموذج", 14f, accent, true))
        val modelField = EditText(this).apply {
            setText(prefs.getString("model", "gemini-2.5-flash"))
            setTextColor(white)
            setHintTextColor(muted)
            textDirection = View.TEXT_DIRECTION_LTR
            setPadding(dp(12), dp(10), dp(12), dp(10))
            setBackgroundColor(panel)
        }
        content.addView(modelField, LinearLayout.LayoutParams(-1, dp(54)))
        content.addView(button("حفظ إعدادات المحرك") {
            prefs.edit()
                .putString("api_key", keyField.text.toString().trim())
                .putString("model", modelField.text.toString().trim().ifBlank { "gemini-2.5-flash" })
                .apply()
            status.text = if (keyField.text.isNullOrBlank()) "المحرك غير متصل" else "تم حفظ إعدادات المحرك"
            Toast.makeText(this, "تم حفظ الإعدادات.", Toast.LENGTH_SHORT).show()
        })
        content.addView(button("اختبار الاتصال") {
            val key = keyField.text.toString().trim()
            if (key.isBlank()) {
                Toast.makeText(this, "أضف مفتاح API أولًا.", Toast.LENGTH_SHORT).show()
            } else {
                status.text = "جاري اختبار الاتصال..."
                worker.execute {
                    val result = try {
                        val test = askGemini(key, "رد بكلمة واحدة: متصل")
                        "نجح الاتصال: " + test.take(100)
                    } catch (e: Exception) {
                        "فشل الاتصال: " + (e.message ?: "خطأ")
                    }
                    runOnUiThread {
                        status.text = result
                        Toast.makeText(this, result, Toast.LENGTH_LONG).show()
                    }
                }
            }
        })
        content.addView(text("الصوت: إدخال الكلام يعتمد على خدمة التعرف المثبتة في الهاتف، والإجابة الصوتية تعتمد على محرك Android TTS. لا يوجد استماع دائم في الخلفية.", 12f, muted))
        content.addView(text("الخصوصية: سجل الدردشة والذاكرة ومفتاح API محفوظة محليًا في مساحة التطبيق. الرسائل التي ترسلها للمحرك تنتقل إلى Google Gemini.", 12f, muted))
    }

    override fun onDestroy() {
        if (::tts.isInitialized) tts.shutdown()
        worker.shutdown()
        super.onDestroy()
    }
}
