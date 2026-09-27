package com.saeed.aicall

import android.app.Activity
import android.app.role.RoleManager
import android.os.Bundle
import android.text.InputType
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {

    private lateinit var numberInput: EditText
    private lateinit var statusText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences("saeed_ai_call", MODE_PRIVATE)

        numberInput = EditText(this).apply {
            hint = "رقم المتصل الذي تريد أن يتعامل معه AI"
            inputType = InputType.TYPE_CLASS_PHONE
            setText(prefs.getString("target_caller", ""))
        }

        statusText = TextView(this).apply {
            textSize = 16f
            text = "الوضع الحالي: التطبيق ليس تطبيق الهاتف الافتراضي."
        }

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 60, 40, 40)
        }

        layout.addView(TextView(this).apply {
            text = "Saeed AI Call"
            textSize = 28f
        })

        layout.addView(TextView(this).apply {
            text = "النسخة الجديدة لا تطلب منك جعل التطبيق تطبيق الهاتف الافتراضي.\n" +
                    "حدد رقم المتصل الذي تريد مراقبته، ثم فعّل صلاحية فحص المكالمات."
            textSize = 16f
        })

        layout.addView(numberInput)

        layout.addView(Button(this).apply {
            text = "حفظ الرقم"
            setOnClickListener {
                prefs.edit()
                    .putString("target_caller", numberInput.text.toString().trim())
                    .apply()
                statusText.text = "تم حفظ الرقم: " + numberInput.text
            }
        })

        layout.addView(Button(this).apply {
            text = "تفعيل فحص المكالمات"
            setOnClickListener {
                val rm = getSystemService(RoleManager::class.java)
                if (rm != null && rm.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING)) {
                    startActivityForResult(
                        rm.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING),
                        REQUEST_SCREENING_ROLE
                    )
                } else {
                    statusText.text = "الجهاز لا يوفر دور فحص المكالمات."
                }
            }
        })

        layout.addView(statusText)
        setContentView(layout)
    }

    override fun onResume() {
        super.onResume()
        val rm = getSystemService(RoleManager::class.java)
        if (rm != null && rm.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)) {
            statusText.text = "الحالة: فحص المكالمات مفعّل. التطبيق ليس Dialer افتراضيًا."
        }
    }

    companion object {
        private const val REQUEST_SCREENING_ROLE = 2001
    }
}
