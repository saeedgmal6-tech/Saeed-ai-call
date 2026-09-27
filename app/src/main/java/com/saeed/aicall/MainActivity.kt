package com.saeed.aicall

import android.app.Activity
import android.app.role.RoleManager
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val layout=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(40,60,40,40)}
        layout.addView(TextView(this).apply{text="Saeed AI Call";textSize=28f})
        layout.addView(TextView(this).apply{text="النسخة الأولى تختبر استقبال المكالمات والرد التلقائي.\nبعد نجاحها نضيف طبقة الصوت والـAI.";textSize=16f})
        layout.addView(Button(this).apply{
            text="تعيين كتطبيق الهاتف الافتراضي"
            setOnClickListener{
                val rm=getSystemService(RoleManager::class.java)
                if(rm!=null && rm.isRoleAvailable(RoleManager.ROLE_DIALER))
                    startActivityForResult(rm.createRequestRoleIntent(RoleManager.ROLE_DIALER),1001)
            }
        })
        setContentView(layout)
    }
}
