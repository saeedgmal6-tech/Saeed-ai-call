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
        status = text("جاهز • في انتظار عنوان خادمك الخاص", 11f, muted)
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
        content.addView(text("اكتب سؤالك أو استخدم الميكروفون. الذكاء هيشتغل من خلال نموذج مفتوح المصدر على خادمك الخاص، من غير مفاتيح Google أو OpenAI. لازم الخادم يكون متاحًا على الشبكة.", 14f, muted))
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
        val serverUrl = prefs.getString("server_url", "") ?: ""
        val model = prefs.getString("model", "qwen2.5:3b") ?: "qwen2.5:3b"
        if (serverUrl.isBlank()) {
            val answer = "لسه عنوان الخادم الخاص مش متحدد. افتح الإعدادات واكتب عنوان خادم SAEED AI اللي عليه النموذج المفتوح المصدر. مفيش أي مفتاح API مطلوب."
            appendHistory("model", answer)
            addBubble("model", answer)
            status.text = "الخادم غير مضبوط • أدخل عنوانه من الإعدادات"
            return
        }
        status.text = "جاري الاتصال بالخادم الخاص..."
        val loading = text("SAEED AI بيجهز الرد من خادمك الخاص...", 13f, accent)
        content.addView(loading)
        worker.execute {
            val answer = try { askPrivateServer(serverUrl, model, message) } catch (e: Exception) {
                "تعذر الاتصال بالخادم الخاص: " + (e.message ?: "خطأ غير معروف") + "\nراجع العنوان وتأكد إن الخادم شغال ويمكن الوصول له من الهاتف."
            }
            runOnUiThread {
                content.removeView(loading)
                appendHistory("model", answer)
                addBubble("model", answer)
                status.text = if (answer.startsWith("تعذر الاتصال")) "الخادم غير متصل" else "متصل بالخادم الخاص"
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

    private fun askPrivateServer(serverAddress: String, model: String, prompt: String): String {
        val base = serverAddress.trim().trimEnd('/')
        val endpoint = if (base.endsWith("/v1/chat/completions")) base else "$base/v1/chat/completions"
        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15000
            readTimeout = 120000
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
        }
        val messages = JSONArray()
        val system = "أنت SAEED AI، مساعد شخصي عملي. تحدث باللهجة المصرية الواضحة ما لم يطلب المستخدم غير ذلك. كن دقيقًا، لا تدّع تنفيذ إجراء لم تنفذه، وصرّح بحدود معرفتك. الذاكرة الشخصية المتاحة: $memory"
        messages.put(JSONObject().put("role", "system").put("content", system))
        val start = maxOf(0, history.length() - 16)
        for (i in start until history.length()) {
            val item = history.optJSONObject(i) ?: continue
            val role = if (item.optString("role") == "user") "user" else "assistant"
            messages.put(JSONObject().put("role", role).put("content", item.optString("text")))
        }
        if (history.length() == 0 || history.optJSONObject(history.length() - 1)?.optString("text") != prompt) {
            messages.put(JSONObject().put("role", "user").put("content", prompt))
        }
        val body = JSONObject()
            .put("model", model)
            .put("messages", messages)
            .put("temperature", 0.7)
            .put("max_tokens", 2048)
        connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
        val code = connection.responseCode
        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
        val response = stream.bufferedReader().use { it.readText() }
        connection.disconnect()
        if (code !in 200..299) throw IllegalStateException("HTTP $code: " + response.take(500))
        val json = JSONObject(response)
        return json.getJSONArray("choices").getJSONObject(0)
            .getJSONObject("message").getString("content")
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
        content.addView(text("اكتب المعلومات التي تريد من المساعد تذكرها. تُحفظ محليًا على الهاتف وتُضاف إلى سياق المحادثة مع نموذجك الخاص.", 13f, muted))
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
        content.addView(text("إعدادات الخادم الخاص", 21f, white, true))
        content.addView(text("التطبيق يتصل بخادم تملكه أو تستضيفه أنت ويشغّل نموذجًا مفتوح المصدر. لا توجد مفاتيح API ولا اتصال بمزود ذكاء اصطناعي خارجي. عنوان الخادم وحده مطلوب.", 13f, muted))
        content.addView(text("عنوان الخادم (Base URL)", 14f, accent, true))
        val serverField = EditText(this).apply {
            setText(prefs.getString("server_url", ""))
            hint = "مثال: http://192.168.1.10:11434"
            inputType = 17
            setTextColor(white)
            setHintTextColor(muted)
            textDirection = View.TEXT_DIRECTION_LTR
            setPadding(dp(12), dp(10), dp(12), dp(10))
            setBackgroundColor(panel)
        }
        content.addView(serverField, LinearLayout.LayoutParams(-1, dp(54)))
        content.addView(text("اسم النموذج على الخادم", 14f, accent, true))
        val modelField = EditText(this).apply {
            setText(prefs.getString("model", "qwen2.5:3b"))
            hint = "مثال: qwen2.5:3b"
            setTextColor(white)
            setHintTextColor(muted)
            textDirection = View.TEXT_DIRECTION_LTR
            setPadding(dp(12), dp(10), dp(12), dp(10))
            setBackgroundColor(panel)
        }
        content.addView(modelField, LinearLayout.LayoutParams(-1, dp(54)))
        content.addView(button("حفظ إعدادات الخادم") {
            val address = serverField.text.toString().trim().trimEnd('/')
            prefs.edit()
                .putString("server_url", address)
                .putString("model", modelField.text.toString().trim().ifBlank { "qwen2.5:3b" })
                .remove("api_key")
                .apply()
            status.text = if (address.isBlank()) "أدخل عنوان الخادم الخاص" else "تم حفظ عنوان الخادم الخاص"
            Toast.makeText(this, "تم حفظ الإعدادات على الهاتف. لا يوجد مفتاح API.", Toast.LENGTH_SHORT).show()
        })
        content.addView(button("اختبار اتصال الخادم") {
            val address = serverField.text.toString().trim()
            val model = modelField.text.toString().trim().ifBlank { "qwen2.5:3b" }
            if (address.isBlank()) {
                Toast.makeText(this, "اكتب عنوان الخادم أولًا.", Toast.LENGTH_SHORT).show()
            } else {
                status.text = "جاري اختبار الخادم الخاص..."
                worker.execute {
                    val result = try {
                        val answer = askPrivateServer(address, model, "رد بكلمة واحدة: متصل")
                        "نجح الاتصال بالنموذج: " + answer.take(100)
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
        content.addView(text("واجهة الاتصال متوافقة مع OpenAI Chat Completions API محليًا فقط؛ مثل Ollama أو llama.cpp server عند تشغيل واجهة /v1/chat/completions. لا يتم إرسال Authorization أو أي مفتاح. لا تضع عنوان خادم عام غير موثوق، ويفضل استخدام شبكة خاصة أو VPN.", 12f, muted))
        content.addView(text("الصوت: إدخال الكلام يعتمد على خدمة التعرف المثبتة في الهاتف، والإجابة الصوتية تعتمد على Android TTS. لا يوجد استماع دائم في الخلفية.", 12f, muted))
        content.addView(text("الذاكرة وسجل الدردشة محفوظان محليًا داخل التطبيق. محتوى الرسائل ينتقل فقط إلى عنوان الخادم الذي تحدده.", 12f, muted))
    }

    override fun onDestroy() {
        if (::tts.isInitialized) tts.shutdown()
        worker.shutdown()
        super.onDestroy()
    }
}
