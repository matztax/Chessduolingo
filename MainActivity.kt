package com.example.schachhelfer

import android.content.Intent
import android.media.projection.MediaProjectionManager
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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
            text = "2. Brett scannen"
            setOnClickListener {
                startService(Intent(this@MainActivity, CaptureService::class.java)
                    .setAction(CaptureService.ACTION_SCAN))
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
