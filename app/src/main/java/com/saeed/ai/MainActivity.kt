package com.saeed.ai

import android.Manifest
import android.app.Activity
import android.app.SearchManager
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
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
import kotlinx.coroutines.Job
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
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

class MainActivity : Activity() {

    private val backgroundColor = Color.rgb(8, 13, 22)
    private val surface = Color.rgb(18, 27, 42)
    private val surfaceLight = Color.rgb(26, 39, 57)
    private val accent = Color.rgb(70, 226, 197)
    private val gold = Color.rgb(224, 184, 105)
    private val foregroundColor = Color.rgb(240, 245, 250)
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
    private var modelDownloading = false
    private var downloadJob: Job? = null

    private val modelName = "qwen2.5-1.5b-instruct-q4_k_m.gguf"
    private val modelUrl = "https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF/resolve/main/qwen2.5-1.5b-instruct-q4_k_m.gguf?download=true"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = backgroundColor
        window.navigationBarColor = backgroundColor
        window.decorView.systemUiVisibility = 0
        history = try {
            JSONArray(preferences.getString("history", "[]"))
        } catch (_: Exception) {
            JSONArray()
        }
        memory = preferences.getString("memory", "") ?: ""
        buildShell()
        showHome()
        inspectModel()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun rounded(color: Int, radius: Int = 18): GradientDrawable {
        return GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radius).toFloat()
        }
    }

    private fun label(value: String, size: Float = 14f, color: Int = foregroundColor, bold: Boolean = false): TextView {
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
            setTextColor(if (value.contains("تحميل")) backgroundColor else foregroundColor)
            gravity = Gravity.CENTER
            setTypeface(typeface, Typeface.BOLD)
            background = rounded(if (value.contains("تحميل")) accent else surfaceLight, 14)
            setPadding(dp(14), dp(12), dp(14), dp(12))
            setOnClickListener { onClick() }
        }
    }


    private fun sectionTitle(value: String, subtitle: String = ""): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(2), dp(12), dp(2), dp(8))
            addView(label(value, 19f, foregroundColor, true))
            if (subtitle.isNotBlank()) addView(label(subtitle, 12f, secondary))
        }
    }

    private fun panel(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(surface, 22)
            setPadding(dp(16), dp(15), dp(16), dp(15))
        }
    }

    private fun smallTag(value: String, color: Int = accent): TextView {
        return TextView(this).apply {
            text = value
            textSize = 10f
            setTextColor(color)
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(dp(10), dp(5), dp(10), dp(5))
            background = rounded(Color.argb(30, Color.red(color), Color.green(color), Color.blue(color)), 20)
        }
    }

    private fun addGap(parent: LinearLayout, height: Int = 10) {
        parent.addView(View(this), LinearLayout.LayoutParams(1, dp(height)))
    }

    private fun navItem(title: String, icon: String, selected: Boolean, onClick: () -> Unit): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = rounded(if (selected) Color.rgb(25, 55, 60) else Color.TRANSPARENT, 16)
            setPadding(dp(8), dp(7), dp(8), dp(7))
            addView(TextView(this@MainActivity).apply {
                text = icon
                textSize = 18f
                gravity = Gravity.CENTER
                setTextColor(if (selected) accent else secondary)
            })
            addView(TextView(this@MainActivity).apply {
                text = title
                textSize = 10f
                gravity = Gravity.CENTER
                setTextColor(if (selected) accent else secondary)
                setTypeface(typeface, if (selected) Typeface.BOLD else Typeface.NORMAL)
            })
            setOnClickListener { onClick() }
        }
    }


    private fun buildShell() {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(backgroundColor)
            setPadding(dp(18), dp(8), dp(18), dp(8))
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(6), 0, dp(12))
        }
        val brand = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        brand.addView(label("SAEED AI", 23f, accent, true))
        brand.addView(label("PERSONAL INTELLIGENCE", 9f, secondary, true))
        header.addView(brand, LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(TextView(this).apply {
            text = "S"
            textSize = 20f
            gravity = Gravity.CENTER
            setTextColor(backgroundColor)
            setTypeface(typeface, Typeface.BOLD)
            background = rounded(accent, 18)
        }, LinearLayout.LayoutParams(dp(42), dp(42)))
        root.addView(header)

        val statusRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = rounded(Color.rgb(16, 34, 43), 14)
            setPadding(dp(12), dp(8), dp(12), dp(8))
        }
        statusText = label("المحرك المحلي غير محمّل", 11f, gold, true)
        statusRow.addView(statusText, LinearLayout.LayoutParams(0, -2, 1f))
        statusRow.addView(smallTag("PRIVATE", accent))
        root.addView(statusRow)

        progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progress = 0
            progressTintList = android.content.res.ColorStateList.valueOf(accent)
            progressBackgroundTintList = android.content.res.ColorStateList.valueOf(surfaceLight)
            visibility = View.GONE
        }
        root.addView(progressBar, LinearLayout.LayoutParams(-1, dp(3)))
        scroll = ScrollView(this).apply {
            isFillViewport = true
            clipToPadding = false
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(12), 0, dp(18))
        }
        scroll.addView(body)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        composer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(8), 0, dp(8))
        }
        composer.addView(actionButton("🎙") { startVoiceInput() }, LinearLayout.LayoutParams(dp(48), dp(50)))
        input = EditText(this).apply {
            hint = "اكتب لـ SAEED AI..."
            textSize = 14f
            setTextColor(foregroundColor)
            setHintTextColor(secondary)
            maxLines = 4
            minLines = 1
            gravity = Gravity.RIGHT or Gravity.CENTER_VERTICAL
            textDirection = View.TEXT_DIRECTION_RTL
            setPadding(dp(15), dp(8), dp(15), dp(8))
            background = rounded(surface, 18)
        }
        val inputParams = LinearLayout.LayoutParams(0, dp(50), 1f)
        inputParams.setMargins(dp(8), 0, dp(8), 0)
        composer.addView(input, inputParams)
        composer.addView(actionButton("➤") { sendMessage() }, LinearLayout.LayoutParams(dp(50), dp(50)))
        root.addView(composer)

        val navigation = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            background = rounded(Color.rgb(13, 21, 34), 20)
            setPadding(dp(5), dp(5), dp(5), dp(5))
        }
        navigation.addView(navItem("الرئيسية", "⌂", currentTab == "home") { showHome() }, LinearLayout.LayoutParams(0, dp(54), 1f))
        navigation.addView(navItem("المحادثة", "✦", currentTab == "chat") { showChat() }, LinearLayout.LayoutParams(0, dp(54), 1f))
        navigation.addView(navItem("الذاكرة", "◈", currentTab == "memory") { showMemory() }, LinearLayout.LayoutParams(0, dp(54), 1f))
        navigation.addView(navItem("المحرك", "⚙", currentTab == "engine") { showEngine() }, LinearLayout.LayoutParams(0, dp(54), 1f))
        root.addView(navigation)
        setContentView(root)
    }


    private fun showHome() {
        currentTab = "home"
        body.removeAllViews()
        composer.visibility = View.GONE
        body.addView(sectionTitle("مساحة SAEED AI", "مساعدك الشخصي • خصوصية أولًا"))
        val hero = panel()
        hero.addView(smallTag("PERSONAL INTELLIGENCE SYSTEM", accent))
        addGap(hero, 12)
        hero.addView(label("كل أفكارك تبدأ\nمن هنا.", 27f, foregroundColor, true))
        hero.addView(label("محادثة، ذاكرة، ومحركات قابلة للتوسّع في مكان واحد.", 13f, secondary))
        addGap(hero, 12)
        hero.addView(actionButton(if (modelReady) "افتح المحادثة" else "إعداد المحرك المحلي") {
            if (modelReady) showChat() else showEngine()
        })
        body.addView(hero)
        addGap(body, 14)
        body.addView(sectionTitle("الوصول السريع", "المكونات الأساسية المتاحة حاليًا"))
        val cards = listOf(
            Triple("المحادثة الذكية", "اسأل واكتب بالنموذج المحلي", "✦"),
            Triple("الذاكرة الشخصية", "تحكم في المعلومات المحفوظة", "◈"),
            Triple("إدارة المحرك", "تنزيل النموذج وتشغيله", "⚙")
        )
        for ((title, description, symbol) in cards) {
            val card = panel()
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            row.addView(label(symbol, 22f, accent, true), LinearLayout.LayoutParams(dp(42), -2))
            val texts = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            texts.addView(label(title, 15f, foregroundColor, true))
            texts.addView(label(description, 11f, secondary))
            row.addView(texts, LinearLayout.LayoutParams(0, -2, 1f))
            card.addView(row)
            card.setOnClickListener {
                when (title) {
                    "المحادثة الذكية" -> showChat()
                    "الذاكرة الشخصية" -> showMemory()
                    else -> showEngine()
                }
            }
            body.addView(card)
            addGap(body, 8)
        }
        body.addView(sectionTitle("أدوات سريعة", "وظائف حقيقية متاحة من أندرويد"))
        val toolsCard = panel()
        toolsCard.addView(label("بحث على الويب", 15f, foregroundColor, true))
        toolsCard.addView(label("يفتح تطبيق البحث أو المتصفح بنتائج مباشرة. النتائج لا تُدمج تلقائيًا في المحادثة المحلية حتى الآن.", 11f, secondary))
        addGap(toolsCard, 8)
        toolsCard.addView(actionButton("فتح البحث على الويب") { openWebSearch() })
        addGap(toolsCard, 12)
        toolsCard.addView(label("استيراد ملف نصي", 15f, foregroundColor, true))
        toolsCard.addView(label("استيراد TXT أو Markdown وإضافته إلى ذاكرتك المحلية كسياق للمحادثات.", 11f, secondary))
        addGap(toolsCard, 8)
        toolsCard.addView(actionButton("اختيار ملف نصي") { openTextDocument() })
        body.addView(toolsCard)
        addGap(body, 10)
        body.addView(label("تحليل PDF وWord والصور، والبحث المدمج في ردود المساعد، والتحكم الآلي في الهاتف ما زالت تحتاج وحدات مستقلة.", 11f, gold))
    }


    private fun openWebSearch() {
        val query = input.text.toString().trim()
        if (query.isBlank()) {
            Toast.makeText(this, "اكتب موضوع البحث في خانة المحادثة أولًا.", Toast.LENGTH_LONG).show()
            showChat()
            input.requestFocus()
            return
        }
        try {
            val intent = Intent(Intent.ACTION_WEB_SEARCH).apply {
                putExtra(SearchManager.QUERY, query)
            }
            startActivity(intent)
        } catch (_: Exception) {
            val encoded = android.net.Uri.encode(query)
            startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://www.google.com/search?q=$encoded")))
        }
    }

    private fun openTextDocument() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "text/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("text/plain", "text/markdown", "text/x-markdown"))
        }
        try {
            startActivityForResult(intent, 43)
        } catch (_: Exception) {
            Toast.makeText(this, "متعذر فتح مدير الملفات على الجهاز.", Toast.LENGTH_LONG).show()
        }
    }

    private fun importTextDocument(data: Intent?) {
        val uri = data?.data ?: return
        scope.launch(Dispatchers.IO) {
            try {
                val imported = contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use {
                    val buffer = CharArray(200_001)
                    val count = it.read(buffer, 0, buffer.size)
                    String(buffer, 0, if (count < 0) 0 else count)
                }.orEmpty()
                if (imported.isBlank()) throw IllegalStateException("الملف فارغ أو لا يحتوي على نص قابل للقراءة.")
                val name = uri.lastPathSegment?.substringAfterLast('/')?.take(80) ?: "مستند مستورد"
                val addition = "\n\n[محتوى ملف: $name]\n$imported"
                withContext(Dispatchers.Main) {
                    memory = (memory + addition).takeLast(100_000)
                    preferences.edit().putString("memory", memory).apply()
                    Toast.makeText(this@MainActivity, "تم استيراد النص إلى الذاكرة المحلية.", Toast.LENGTH_LONG).show()
                    showMemory()
                }
            } catch (error: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "تعذر استيراد الملف: ${error.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun showSettings() {
        showEngine()
    }

    private fun inspectModel() {
        val file = modelFile()
        if (file.exists() && file.length() > 900_000_000L) {
            loadLocalModel()
            return
        }
        statusText.text = if (modelPartFile().exists() && modelPartFile().length() > 0L) {
            "يوجد تنزيل سابق غير مكتمل • يمكن استكماله"
        } else {
            "النموذج جاهز للتنزيل • حوالي 1 جيجابايت • Wi-Fi"
        }
        if (currentTab == "chat") showModelWelcome()
    }

    private fun modelFile(): File {
        val directory = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: filesDir
        return File(directory, modelName)
    }

    private fun modelPartFile(): File {
        return File(modelFile().parentFile, "$modelName.part")
    }


    private fun showModelWelcome() {
        body.removeAllViews()
        val hero = panel()
        hero.addView(smallTag("YOUR PRIVATE AI", accent))
        addGap(hero, 12)
        hero.addView(label("ذكاء اصطناعي\nعلى مقاسك.", 29f, foregroundColor, true))
        hero.addView(label("مساحة واحدة لأفكارك وأسئلتك ومهامك. ابدأ بالمحرك المحلي، ثم نوسّع قدرات SAEED AI بوحدات مستقلة.", 14f, secondary))
        addGap(hero, 14)
        val modelLine = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        modelLine.addView(label("Qwen 1.5B • محلي", 12f, accent, true), LinearLayout.LayoutParams(0, -2, 1f))
        modelLine.addView(smallTag("1.12 GB", gold))
        hero.addView(modelLine)
        addGap(hero, 14)
        hero.addView(actionButton("تحميل المحرك المحلي") { startModelDownload() })
        body.addView(hero)
        addGap(body, 14)
        body.addView(sectionTitle("مساحة العمل", "الأدوات المتاحة في هذه النسخة"))
        val features = listOf(
            "محادثة محلية" to "توليد النصوص على الجهاز بعد تحميل النموذج",
            "ذاكرة شخصية" to "حفظ تفضيلاتك ومعلوماتك محليًا",
            "إدخال صوتي" to "إملاء السؤال باللغة العربية"
        )
        for ((title, description) in features) {
            val item = panel()
            item.addView(label(title, 15f, foregroundColor, true))
            item.addView(label(description, 12f, secondary))
            body.addView(item)
            addGap(body, 8)
        }
        body.addView(label("البحث المباشر، تحليل الصور والملفات، وتنفيذ أوامر الهاتف وحدات غير مفعلة بعد؛ لن نعرضها كأنها تعمل قبل تنفيذها واختبارها.", 11f, gold))
    }

    private fun startModelDownload() {
        if (downloadJob?.isActive == true) return
        val target = modelFile()
        val partial = modelPartFile()
        modelDownloading = true
        progressBar.visibility = View.VISIBLE
        progressBar.isIndeterminate = false
        progressBar.progress = 0
        statusText.text = "جاري الاتصال بمصدر النموذج..."
        showEngine()

        downloadJob = scope.launch(Dispatchers.IO) {
            var connection: HttpURLConnection? = null
            try {
                val existingBytes = if (partial.exists()) partial.length() else 0L
                connection = (URL(modelUrl).openConnection() as HttpURLConnection).apply {
                    instanceFollowRedirects = true
                    connectTimeout = 30000
                    readTimeout = 90000
                    requestMethod = "GET"
                    setRequestProperty("User-Agent", "SAEED-AI-Android/2.0")
                    setRequestProperty("Accept", "application/octet-stream")
                    setRequestProperty("Accept-Encoding", "identity")
                    if (existingBytes > 0L) setRequestProperty("Range", "bytes=$existingBytes-")
                }

                val responseCode = connection.responseCode
                val append = existingBytes > 0L && responseCode == HttpURLConnection.HTTP_PARTIAL
                if (responseCode != HttpURLConnection.HTTP_OK && !append) {
                    throw IllegalStateException("خادم النموذج رد برمز HTTP $responseCode")
                }
                val contentType = connection.contentType ?: ""
                if (contentType.contains("text/html", ignoreCase = true)) {
                    throw IllegalStateException("مصدر النموذج أعاد صفحة ويب بدل ملف النموذج")
                }

                val responseLength = connection.contentLengthLong
                val totalLength = if (append && responseLength > 0L) existingBytes + responseLength else responseLength
                if (!append && partial.exists()) partial.delete()

                var lastPercent = -1
                connection.inputStream.buffered().use { inputStream ->
                    FileOutputStream(partial, append).buffered().use { outputStream ->
                        val buffer = ByteArray(256 * 1024)
                        var downloaded = if (append) existingBytes else 0L
                        while (true) {
                            val count = inputStream.read(buffer)
                            if (count == -1) break
                            outputStream.write(buffer, 0, count)
                            downloaded += count
                            if (totalLength > 0L) {
                                val percent = ((downloaded * 100L) / totalLength).toInt().coerceIn(0, 100)
                                if (percent != lastPercent) {
                                    lastPercent = percent
                                    withContext(Dispatchers.Main) {
                                        progressBar.isIndeterminate = false
                                        progressBar.progress = percent
                                        statusText.text = "تنزيل النموذج المحلي: $percent%"
                                    }
                                }
                            }
                        }
                        outputStream.flush()
                    }
                }

                if (!partial.exists() || partial.length() < 900_000_000L) {
                    throw IllegalStateException("الملف وصل ناقصًا (${partial.length()} بايت). اضغط استكمال التنزيل.")
                }
                if (target.exists()) target.delete()
                if (!partial.renameTo(target)) {
                    partial.copyTo(target, overwrite = true)
                    partial.delete()
                }
                withContext(Dispatchers.Main) {
                    modelDownloading = false
                    downloadJob = null
                    progressBar.isIndeterminate = false
                    progressBar.progress = 100
                    statusText.text = "اكتمل تنزيل النموذج • جاري تشغيل المحرك"
                    loadLocalModel()
                }
            } catch (error: Exception) {
                withContext(Dispatchers.Main) {
                    modelDownloading = false
                    downloadJob = null
                    progressBar.isIndeterminate = false
                    progressBar.visibility = View.GONE
                    val detail = error.message ?: "تحقق من اتصال الإنترنت والمساحة المتاحة"
                    statusText.text = "تعذر التنزيل: $detail"
                    Toast.makeText(this@MainActivity, statusText.text, Toast.LENGTH_LONG).show()
                    showEngine()
                }
            } finally {
                connection?.disconnect()
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
            setTextColor(foregroundColor)
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
        body.addView(sectionTitle("المحادثة", "جلسة خاصة • معالجة محلية"))
        if (history.length() == 0) {
            val welcome = panel()
            welcome.addView(smallTag("SAEED AI", accent))
            addGap(welcome, 8)
            welcome.addView(label("أهلًا بيك.\nتحب نبدأ بإيه؟", 24f, foregroundColor, true))
            welcome.addView(label("اسأل سؤال، اطلب شرح، أو ابدأ في كتابة فكرة. هكون واضح معاك في حدود قدراتي.", 13f, secondary))
            body.addView(welcome)
            addGap(body, 12)
            for (suggestion in listOf("اشرحلي موضوع معقد ببساطة", "ساعدني أكتب خطة عمل", "راجعلي كود برمجي")) {
                body.addView(actionButton(suggestion) {
                    input.setText(suggestion)
                    input.setSelection(input.text.length)
                })
                addGap(body, 6)
            }
        } else {
            for (i in 0 until history.length()) {
                val item = history.optJSONObject(i) ?: continue
                addBubble(item.optString("role"), item.optString("text"))
            }
        }
    }


    private fun showMemory() {
        currentTab = "memory"
        body.removeAllViews()
        composer.visibility = View.GONE
        body.addView(sectionTitle("الذاكرة الشخصية", "بياناتك محفوظة محليًا على هذا الجهاز"))
        val info = panel()
        info.addView(smallTag("ON-DEVICE STORAGE", accent))
        info.addView(label("SAEED AI بيتعلم تفضيلاتك من المعلومات اللي تختار حفظها هنا.", 14f, foregroundColor, true))
        info.addView(label("ما تكتبش كلمات مرور أو بيانات شديدة الحساسية. الذاكرة الحالية محفوظة في مساحة التطبيق الخاصة.", 12f, secondary))
        body.addView(info)
        addGap(body, 12)
        val editor = EditText(this).apply {
            setText(memory)
            minLines = 9
            gravity = Gravity.TOP or Gravity.RIGHT
            textDirection = View.TEXT_DIRECTION_RTL
            setTextColor(foregroundColor)
            setHintTextColor(secondary)
            hint = "اكتب تفضيلاتك وطريقة الرد المفضلة ومعلومات تساعد المساعد..."
            textSize = 14f
            setPadding(dp(16), dp(16), dp(16), dp(16))
            background = rounded(surface, 18)
        }
        body.addView(editor, LinearLayout.LayoutParams(-1, dp(250)))
        addGap(body, 10)
        body.addView(actionButton("حفظ التغييرات") {
            memory = editor.text.toString()
            preferences.edit().putString("memory", memory).apply()
            Toast.makeText(this, "تم حفظ الذاكرة على الجهاز.", Toast.LENGTH_LONG).show()
        })
    }


    private fun showEngine() {
        currentTab = "engine"
        body.removeAllViews()
        composer.visibility = View.GONE
        body.addView(sectionTitle("مركز الذكاء", "إدارة النموذج المحلي وحالة التشغيل"))
        val modelCard = panel()
        modelCard.addView(smallTag(if (modelReady) "ENGINE ONLINE" else "ENGINE OFFLINE", if (modelReady) accent else gold))
        addGap(modelCard, 10)
        modelCard.addView(label("Qwen2.5 1.5B Instruct", 19f, foregroundColor, true))
        modelCard.addView(label("GGUF • Q4_K_M • نموذج لغوي محلي", 12f, secondary))
        addGap(modelCard, 8)
        modelCard.addView(label("حجم الملف: 1.12 جيجابايت تقريبًا", 12f, gold))
        modelCard.addView(label("الحالة: ${if (modelReady) "المحرك يعمل وجاهز للمحادثة" else if (modelDownloading) "جاري تنزيل النموذج" else "النموذج غير محمّل"}", 13f, if (modelReady) accent else gold, true))
        if (modelDownloading) {
            addGap(modelCard, 8)
            modelCard.addView(label("التنزيل مستمر. لا تغلق التطبيق حتى يكتمل.", 12f, secondary))
        } else if (!modelFile().exists()) {
            addGap(modelCard, 10)
            val buttonText = if (modelPartFile().exists() && modelPartFile().length() > 0L) "استكمال تنزيل النموذج" else "تنزيل النموذج"
            modelCard.addView(actionButton(buttonText) { startModelDownload() })
        } else if (!modelReady) {
            addGap(modelCard, 10)
            modelCard.addView(actionButton("تشغيل المحرك") { loadLocalModel() })
        }
        body.addView(modelCard)
        addGap(body, 14)
        body.addView(sectionTitle("قدرات المنصة", "خطة التطوير — ليست وظائف مفعلة"))
        val roadmap = listOf(
            "البحث على الإنترنت" to "يتطلب موصل بحث وخدمة اتصال",
            "تحليل الصور والملفات" to "يتطلب نماذج رؤية واستخراج مستندات",
            "تنفيذ مهام الهاتف" to "يتطلب صلاحيات Android وموافقة المستخدم",
            "الصوت الكامل" to "تحويل كلام ونطق صوتي مع اختيار المحرك"
        )
        for ((title, description) in roadmap) {
            val item = panel()
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            row.addView(label(title, 14f, foregroundColor, true), LinearLayout.LayoutParams(0, -2, 1f))
            row.addView(smallTag("قيد التطوير", gold))
            item.addView(row)
            item.addView(label(description, 11f, secondary))
            body.addView(item)
            addGap(body, 8)
        }
        body.addView(label("لن يتم تفعيل أي قدرة في الواجهة قبل ربطها بتنفيذ حقيقي واختبارها.", 11f, secondary))
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
        if (requestCode == 43 && resultCode == Activity.RESULT_OK) {
            importTextDocument(data)
            return
        }
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
        downloadJob?.cancel()
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
