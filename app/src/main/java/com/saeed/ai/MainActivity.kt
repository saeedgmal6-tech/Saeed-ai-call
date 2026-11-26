package com.saeed.ai

import android.Manifest
import android.app.Activity
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.speech.RecognizerIntent
import android.view.Gravity
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.arm.aichat.AiChat
import com.arm.aichat.InferenceEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Locale

class MainActivity : Activity() {

    private val background = Color.rgb(8, 13, 22)
    private val surface = Color.rgb(18, 27, 42)
    private val surfaceLight = Color.rgb(26, 39, 57)
    private val accent = Color.rgb(70, 226, 197)
    private val gold = Color.rgb(224, 184, 105)
    private val foreground = Color.rgb(240, 245, 250)
    private val secondary = Color.rgb(151, 168, 186)
    private val preferences by lazy { getSharedPreferences("saeed_ai_local", MODE_PRIVATE) }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val handler = Handler(Looper.getMainLooper())

    private lateinit var root: LinearLayout
    private lateinit var body: LinearLayout
    private lateinit var scroll: ScrollView
    private lateinit var composer: LinearLayout
    private lateinit var input: EditText
    private lateinit var statusText: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var engine: InferenceEngine

    private var history = JSONArray()
    private var memory = ""
    private var modelReady = false
    private var engineCreated = false
    private var currentTab = "chat"
    private var downloadId = -1L

    private val modelName = "Qwen2.5-1.5B-Instruct-Q4_K_M.gguf"
    private val modelUrl = "https://huggingface.co/tensorblock/Qwen2.5-1.5B-Instruct-GGUF/resolve/main/Qwen2.5-1.5B-Instruct-Q4_K_M.gguf?download=true"

    private val downloadMonitor = object : Runnable {
        override fun run() {
            monitorDownload()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = background
        window.navigationBarColor = background
        window.decorView.systemUiVisibility = 0
        history = try {
            JSONArray(preferences.getString("history", "[]"))
        } catch (_: Exception) {
            JSONArray()
        }
        memory = preferences.getString("memory", "") ?: ""
        downloadId = preferences.getLong("download_id", -1L)
        buildShell()
        showChat()
        inspectModel()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun rounded(color: Int, radius: Int = 18): GradientDrawable {
        return GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radius).toFloat()
        }
    }

    private fun label(value: String, size: Float = 14f, color: Int = foreground, bold: Boolean = false): TextView {
        return TextView(this).apply {
            text = value
            textSize = size
            setTextColor(color)
            if (bold) setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.RIGHT
            textDirection = View.TEXT_DIRECTION_RTL
            setPadding(dp(4), dp(5), dp(4), dp(5))
        }
    }

    private fun actionButton(value: String, onClick: () -> Unit): TextView {
        return TextView(this).apply {
            text = value
            textSize = 14f
            setTextColor(if (value.contains("تحميل")) background else foreground)
            gravity = Gravity.CENTER
            setTypeface(typeface, Typeface.BOLD)
            background = rounded(if (value.contains("تحميل")) accent else surfaceLight, 14)
            setPadding(dp(14), dp(12), dp(14), dp(12))
            setOnClickListener { onClick() }
        }
    }

    private fun buildShell() {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(background)
            setPadding(dp(16), dp(10), dp(16), dp(8))
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(5), 0, dp(12))
        }
        val brand = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        brand.addView(label("SAEED AI", 25f, accent, true))
        brand.addView(label("PRIVATE INTELLIGENCE • ON-DEVICE", 9f, secondary, true))
        header.addView(brand, LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(actionButton("⚙") { showSettings() }, LinearLayout.LayoutParams(dp(48), dp(46)))
        root.addView(header)

        statusText = label("المحرك المحلي غير محمّل", 12f, gold)
        root.addView(statusText)

        progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progress = 0
            progressTintList = android.content.res.ColorStateList.valueOf(accent)
            progressBackgroundTintList = android.content.res.ColorStateList.valueOf(surfaceLight)
            visibility = View.GONE
        }
        root.addView(progressBar, LinearLayout.LayoutParams(-1, dp(4)))

        scroll = ScrollView(this).apply {
            isFillViewport = true
            clipToPadding = false
        }
        body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(12), 0, dp(12))
        }
        scroll.addView(body)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        composer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(6), 0, dp(6))
        }
        val voice = actionButton("🎙") { startVoiceInput() }
        composer.addView(voice, LinearLayout.LayoutParams(dp(48), dp(52)))
        input = EditText(this).apply {
            hint = "اكتب رسالتك إلى SAEED AI..."
            textSize = 15f
            setTextColor(foreground)
            setHintTextColor(secondary)
            maxLines = 4
            minLines = 1
            gravity = Gravity.RIGHT or Gravity.CENTER_VERTICAL
            textDirection = View.TEXT_DIRECTION_RTL
            setPadding(dp(14), dp(8), dp(14), dp(8))
            background = rounded(surface, 16)
        }
        val inputParams = LinearLayout.LayoutParams(0, dp(52), 1f)
        inputParams.setMargins(dp(8), 0, dp(8), 0)
        composer.addView(input, inputParams)
        composer.addView(actionButton("➤") { sendMessage() }, LinearLayout.LayoutParams(dp(52), dp(52)))
        root.addView(composer)

        val navigation = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, dp(6), 0, 0)
        }
        navigation.addView(actionButton("المحادثة") { showChat() }, LinearLayout.LayoutParams(0, dp(44), 1f))
        navigation.addView(actionButton("الذاكرة") { showMemory() }, LinearLayout.LayoutParams(0, dp(44), 1f))
        navigation.addView(actionButton("المحرك") { showEngine() }, LinearLayout.LayoutParams(0, dp(44), 1f))
        root.addView(navigation)
        setContentView(root)
    }

    private fun inspectModel() {
        val file = modelFile()
        if (file.exists() && file.length() > 900_000_000L) {
            loadLocalModel()
            return
        }
        if (downloadId > 0L) {
            handler.post(downloadMonitor)
        } else {
            statusText.text = "النموذج جاهز للتنزيل • حوالي 1 جيجابايت • Wi-Fi"
            if (currentTab == "chat") showModelWelcome()
        }
    }

    private fun modelFile(): File {
        val directory = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: filesDir
        return File(directory, modelName)
    }

    private fun showModelWelcome() {
        body.removeAllViews()
        val hero = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(surface, 24)
            setPadding(dp(18), dp(20), dp(18), dp(20))
        }
        hero.addView(label("ذكاء اصطناعي خاص بيك.", 23f, foreground, true))
        hero.addView(label("مش مجرد واجهة شات. المحرك هيشتغل محليًا على موبايلك، من غير مفاتيح API أو إرسال محادثاتك لمزود ذكاء اصطناعي.", 14f, secondary))
        hero.addView(label("الموديل: Qwen 1.5B Instruct • Q4_K_M", 12f, accent, true))
        hero.addView(label("حجم التنزيل قرابة 986 ميجابايت. التنزيل مضبوط على Wi-Fi لتجنب استهلاك باقة الموبايل.", 12f, gold))
        hero.addView(actionButton("تحميل النموذج الآن") { startModelDownload() })
        body.addView(hero)
        body.addView(label("بعد اكتمال التنزيل، التطبيق هيحمّل النموذج ويجهز المحادثة تلقائيًا.", 13f, secondary))
    }

    private fun startModelDownload() {
        try {
            val request = DownloadManager.Request(Uri.parse(modelUrl)).apply {
                setTitle("SAEED AI • تحميل نموذج الذكاء")
                setDescription("تنزيل نموذج Qwen المحلي — قرابة 1 جيجابايت")
                setMimeType("application/octet-stream")
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI)
                setAllowedOverRoaming(false)
                setDestinationInExternalFilesDir(this@MainActivity, Environment.DIRECTORY_DOWNLOADS, modelName)
            }
            val manager = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            downloadId = manager.enqueue(request)
            preferences.edit().putLong("download_id", downloadId).apply()
            statusText.text = "بدأ تنزيل النموذج على Wi-Fi"
            progressBar.visibility = View.VISIBLE
            handler.removeCallbacks(downloadMonitor)
            handler.post(downloadMonitor)
            showEngine()
        } catch (error: Exception) {
            statusText.text = "تعذر بدء التنزيل: ${error.message}"
        }
    }

    private fun monitorDownload() {
        if (downloadId <= 0L) return
        val manager = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val query = DownloadManager.Query().setFilterById(downloadId)
        val cursor = manager.query(query)
        if (cursor == null) return
        cursor.use {
            if (!it.moveToFirst()) {
                handler.postDelayed(downloadMonitor, 1500)
                return
            }
            val status = it.getInt(it.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
            val downloaded = it.getLong(it.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
            val total = it.getLong(it.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
            if (total > 0L) {
                val percent = ((downloaded * 100L) / total).toInt().coerceIn(0, 100)
                progressBar.progress = percent
                progressBar.visibility = View.VISIBLE
                statusText.text = "تنزيل النموذج المحلي: $percent%"
            }
            when (status) {
                DownloadManager.STATUS_SUCCESSFUL -> {
                    handler.removeCallbacks(downloadMonitor)
                    downloadId = -1L
                    preferences.edit().remove("download_id").apply()
                    progressBar.progress = 100
                    statusText.text = "اكتمل التنزيل • جاري تشغيل المحرك"
                    loadLocalModel()
                }
                DownloadManager.STATUS_FAILED -> {
                    val reason = it.getInt(it.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
                    handler.removeCallbacks(downloadMonitor)
                    downloadId = -1L
                    preferences.edit().remove("download_id").apply()
                    statusText.text = "فشل التنزيل (رمز $reason). جرّب Wi-Fi آخر."
                }
                else -> handler.postDelayed(downloadMonitor, 1200)
            }
        }
    }

    private fun loadLocalModel() {
        if (modelReady) return
        statusText.text = "جاري تهيئة محرك الذكاء المحلي..."
        progressBar.visibility = View.VISIBLE
        scope.launch(Dispatchers.IO) {
            try {
                if (!engineCreated) {
                    engine = AiChat.getInferenceEngine(applicationContext)
                    engineCreated = true
                }
                withTimeout(120_000L) {
                    engine.state.first {
                        it is InferenceEngine.State.Initialized || it is InferenceEngine.State.Error
                    }
                }
                if (engine.state.value is InferenceEngine.State.Error) {
                    throw IllegalStateException("فشل تحميل مكتبة تشغيل النموذج")
                }
                val file = modelFile()
                if (!file.exists() || file.length() < 900_000_000L) {
                    throw IllegalStateException("ملف النموذج غير مكتمل")
                }
                withContext(Dispatchers.Main) {
                    statusText.text = "جاري تحميل النموذج إلى الذاكرة..."
                }
                engine.loadModel(file.absolutePath)
                engine.setSystemPrompt(
                    "أنت SAEED AI، مساعد شخصي ذكي باللغة العربية. تحدث باللهجة المصرية الطبيعية عندما يناسب السياق. كن دقيقًا وصريحًا ولا تدّع تنفيذ أفعال لم تنفذها. لا تخترع معلومات أو مصادر. إذا لم تعرف فقل ذلك. اهتم بالشرح المنظم والتحليل والكتابة والبرمجة. ذاكرة المستخدم التي يجب مراعاتها: $memory"
                )
                modelReady = true
                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    statusText.text = "المحرك المحلي يعمل • Qwen 1.5B"
                    showChat()
                    addBubble("assistant", "أنا جاهز. المحرك شغال على موبايلك، ومحادثاتك بتتجهز محليًا.")
                }
            } catch (error: Exception) {
                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    statusText.text = "تعذر تشغيل النموذج: ${error.message ?: "خطأ غير معروف"}"
                    Toast.makeText(this@MainActivity, statusText.text, Toast.LENGTH_LONG).show()
                    showEngine()
                }
            }
        }
    }

    private fun sendMessage() {
        val prompt = input.text.toString().trim()
        if (prompt.isEmpty()) return
        if (!modelReady) {
            Toast.makeText(this, "حمّل النموذج المحلي أولًا من تبويب المحرك.", Toast.LENGTH_LONG).show()
            showEngine()
            return
        }
        input.setText("")
        (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager)
            .hideSoftInputFromWindow(input.windowToken, 0)
        appendHistory("user", prompt)
        addBubble("user", prompt)
        val responseBubble = addBubble("assistant", "")
        statusText.text = "SAEED AI بيفكر محليًا..."
        scope.launch(Dispatchers.IO) {
            try {
                val response = StringBuilder()
                engine.sendUserPrompt(prompt, 512).collect { token ->
                    response.append(token)
                    withContext(Dispatchers.Main) {
                        responseBubble.text = "SAEED AI\n" + response.toString()
                        scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
                    }
                }
                val answer = response.toString().ifBlank { "النموذج لم يُرجع نصًا. جرّب صياغة السؤال مرة أخرى." }
                appendHistory("assistant", answer)
                withContext(Dispatchers.Main) {
                    responseBubble.text = "SAEED AI\n$answer"
                    statusText.text = "المحرك المحلي جاهز"
                }
            } catch (error: Exception) {
                withContext(Dispatchers.Main) {
                    responseBubble.text = "تعذر توليد الرد: ${error.message ?: "خطأ في المحرك"}"
                    statusText.text = "حدث خطأ أثناء الاستدلال المحلي"
                }
            }
        }
    }

    private fun appendHistory(role: String, value: String) {
        history.put(JSONObject().put("role", role).put("text", value))
        while (history.length() > 60) history.remove(0)
        preferences.edit().putString("history", history.toString()).apply()
    }

    private fun addBubble(role: String, value: String): TextView {
        val user = role == "user"
        val bubble = TextView(this).apply {
            text = (if (user) "أنت" else "SAEED AI") + if (value.isBlank()) "" else "\n$value"
            textSize = 15f
            setTextColor(foreground)
            textDirection = View.TEXT_DIRECTION_RTL
            gravity = Gravity.RIGHT
            setPadding(dp(15), dp(12), dp(15), dp(12))
            background = rounded(if (user) Color.rgb(22, 65, 70) else surface, 18)
        }
        val params = LinearLayout.LayoutParams(-1, -2)
        params.setMargins(0, dp(5), 0, dp(5))
        body.addView(bubble, params)
        scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
        return bubble
    }

    private fun showChat() {
        currentTab = "chat"
        body.removeAllViews()
        composer.visibility = View.VISIBLE
        if (!modelReady) {
            showModelWelcome()
            return
        }
        body.addView(label("مساحة المحادثة الخاصة", 20f, foreground, true))
        for (i in 0 until history.length()) {
            val item = history.optJSONObject(i) ?: continue
            addBubble(item.optString("role"), item.optString("text"))
        }
        if (history.length() == 0) {
            body.addView(label("اسألني، اكتب فكرة، اطلب تحليلًا أو مساعدة في البرمجة. الاستدلال يتم على الجهاز بعد تحميل النموذج.", 14f, secondary))
        }
    }

    private fun showMemory() {
        currentTab = "memory"
        body.removeAllViews()
        composer.visibility = View.GONE
        body.addView(label("الذاكرة الشخصية", 22f, foreground, true))
        body.addView(label("المعلومات التي تكتبها هنا تُحفظ على الهاتف وتدخل في تعليمات المحرك عند تحميله.", 13f, secondary))
        val editor = EditText(this).apply {
            setText(memory)
            minLines = 8
            gravity = Gravity.TOP or Gravity.RIGHT
            textDirection = View.TEXT_DIRECTION_RTL
            setTextColor(foreground)
            setHintTextColor(secondary)
            hint = "تفضيلاتك ومعلوماتك المهمة..."
            setPadding(dp(14), dp(14), dp(14), dp(14))
            background = rounded(surface, 16)
        }
        body.addView(editor, LinearLayout.LayoutParams(-1, dp(230)))
        body.addView(actionButton("حفظ الذاكرة") {
            memory = editor.text.toString()
            preferences.edit().putString("memory", memory).apply()
            Toast.makeText(this, "اتحفظت الذاكرة على الجهاز. أعد تحميل النموذج لتطبيق التغييرات.", Toast.LENGTH_LONG).show()
        })
    }

    private fun showEngine() {
        currentTab = "engine"
        body.removeAllViews()
        composer.visibility = View.GONE
        body.addView(label("مركز المحرك المحلي", 22f, foreground, true))
        body.addView(label("كل عمليات توليد النص تتم على الهاتف بعد تنزيل ملف النموذج. لا يوجد عنوان خادم ولا مفتاح API.", 14f, secondary))
        body.addView(label("النموذج الحالي", 14f, accent, true))
        body.addView(label("Qwen2.5 1.5B Instruct • GGUF • Q4_K_M", 16f, foreground, true))
        body.addView(label("الحجم: حوالي 986 ميجابايت. الحد الأدنى المقترح للمساحة الفارغة: 2 جيجابايت.", 13f, gold))
        body.addView(label("الحالة: ${if (modelReady) "يعمل" else "غير محمّل"}", 14f, if (modelReady) accent else gold, true))
        if (!modelFile().exists() && downloadId <= 0L) {
            body.addView(actionButton("تحميل النموذج الآن") { startModelDownload() })
        } else if (downloadId > 0L) {
            body.addView(actionButton("متابعة حالة التنزيل") { monitorDownload() })
        } else if (modelFile().exists() && !modelReady) {
            body.addView(actionButton("تشغيل النموذج") { loadLocalModel() })
        }
        body.addView(actionButton("العودة للمحادثة") { showChat() })
        body.addView(label("تنبيه: هذا إصدار أولي بمحرك محلي واحد. ليس دمجًا لكل نماذج ChatGPT ولا يملك تلقائيًا البحث المباشر أو تحليل الصور والملفات أو التحكم الكامل بالنظام. هذه القدرات تحتاج وحدات منفصلة سنضيفها ونختبرها.", 12f, secondary))
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
            Toast.makeText(this, "خدمة تحويل الكلام إلى نص غير متاحة.", Toast.LENGTH_LONG).show()
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

    override fun onDestroy() {
        handler.removeCallbacks(downloadMonitor)
        if (isFinishing && engineCreated) {
            scope.launch(Dispatchers.IO) {
                try {
                    engine.destroy()
                } catch (_: Exception) {
                }
            }
        }
        scope.cancel()
        super.onDestroy()
    }
}
