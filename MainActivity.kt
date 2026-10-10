package com.example.schachhelfer

import android.Manifest
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts

class MainActivity : ComponentActivity() {

    private val projectionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
            val data = r.data
            if (r.resultCode == RESULT_OK && data != null) {
                startForegroundService(
                    Intent(this, CaptureService::class.java)
                        .setAction(CaptureService.ACTION_START)
                        .putExtra(CaptureService.EXTRA_CODE, r.resultCode)
                        .putExtra(CaptureService.EXTRA_DATA, data)
                )
            }
        }

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Ohne diese Erlaubnis zeigt Android 13+ die Benachrichtigung (mit Scan-Button) nicht an
        if (Build.VERSION.SDK_INT >= 33) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 96, 48, 48)
        }
        layout.addView(Button(this).apply {
            text = "1. Aufnahme starten"
            setOnClickListener {
                val mpm = getSystemService(MediaProjectionManager::class.java)
                projectionLauncher.launch(mpm.createScreenCaptureIntent())
            }
        })
        layout.addView(Button(this).apply {
            text = "2. Scannen (nach 4 Sekunden)"
            setOnClickListener {
                startService(
                    Intent(this@MainActivity, CaptureService::class.java)
                        .setAction(CaptureService.ACTION_SCAN)
                        .putExtra(CaptureService.EXTRA_DELAY, 4000L)
                )
                // App in den Hintergrund schieben, damit die Schach-App wieder sichtbar ist
                moveTaskToBack(true)
            }
        })
        layout.addView(Button(this).apply {
            text = "Aufnahme beenden"
            setOnClickListener {
                startService(Intent(this@MainActivity, CaptureService::class.java)
                    .setAction(CaptureService.ACTION_STOP))
            }
        })
        setContentView(layout)
    }
}
