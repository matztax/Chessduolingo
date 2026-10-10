package com.example.schachhelfer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.view.WindowManager
import android.widget.Toast
import androidx.core.content.IntentCompat

class CaptureService : Service() {

    companion object {
        const val ACTION_START = "start"
        const val ACTION_SCAN = "scan"
        const val ACTION_STOP = "stop"
        const val EXTRA_CODE = "code"
        const val EXTRA_DATA = "data"
        private const val CHANNEL = "capture"
        private const val TAG = "Schachhelfer"
    }

    private val main = Handler(Looper.getMainLooper())
    private var projection: MediaProjection? = null
    private var reader: ImageReader? = null
    private var display: VirtualDisplay? = null
    @Volatile private var lastFrame: Bitmap? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startCapture(intent)
            ACTION_SCAN -> scan()
            ACTION_STOP -> stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun startCapture(intent: Intent) {
        // Android 14+: startForeground MUSS vor getMediaProjection() laufen
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Bildschirmaufnahme", NotificationManager.IMPORTANCE_LOW)
        )
        val notification = Notification.Builder(this, CHANNEL)
            .setContentTitle("Schachhelfer läuft")
            .setContentText("Bildschirm wird für die Brett-Erkennung gelesen")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .build()
        startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)

        val code = intent.getIntExtra(EXTRA_CODE, 0)
        val data = IntentCompat.getParcelableExtra(intent, EXTRA_DATA, Intent::class.java) ?: return
        val mpm = getSystemService(MediaProjectionManager::class.java)
        val mp = mpm.getMediaProjection(code, data)
        mp.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() { stopSelf() }
        }, main)
        projection = mp

        val bounds = getSystemService(WindowManager::class.java).maximumWindowMetrics.bounds
        val w = bounds.width()
        val h = bounds.height()
        val dpi = resources.displayMetrics.densityDpi

        val r = ImageReader.newInstance(w, h, PixelFormat.RGBA_8888, 2)
        r.setOnImageAvailableListener({ rd ->
            val image = rd.acquireLatestImage() ?: return@setOnImageAvailableListener
            try {
                val plane = image.planes[0]
                val rowPadding = plane.rowStride - plane.pixelStride * w
                val full = Bitmap.createBitmap(
                    w + rowPadding / plane.pixelStride, h, Bitmap.Config.ARGB_8888
                )
                full.copyPixelsFromBuffer(plane.buffer)
                val frame = if (rowPadding == 0) full else Bitmap.createBitmap(full, 0, 0, w, h)
                lastFrame = frame
            } finally {
                image.close()
            }
        }, main)
        reader = r
        display = mp.createVirtualDisplay(
            "schachhelfer", w, h, dpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, r.surface, null, main
        )
        toast("Aufnahme bereit")
    }

    private fun scan() {
        val frame = lastFrame
        if (frame == null) {
            toast("Noch kein Bild. Starte die Aufnahme und tippe einmal auf den Bildschirm.")
            return
        }
        Thread {
            val t0 = System.currentTimeMillis()
            val board = BoardDetector.detect(frame)
            val ms = System.currentTimeMillis() - t0
            val msg = if (board == null) {
                "Kein Brett gefunden (${ms} ms)"
            } else {
                "Brett: x=%.0f y=%.0f Feld=%.1f px, Sicherheit %.0f %% (%d ms)".format(
                    board.x, board.y, board.square, board.confidence * 100, ms
                )
            }
            Log.d(TAG, msg)
            toast(msg)
        }.start()
    }

    private fun toast(msg: String) {
        main.post { Toast.makeText(applicationContext, msg, Toast.LENGTH_LONG).show() }
    }

    override fun onDestroy() {
        display?.release()
        reader?.close()
        projection?.stop()
        super.onDestroy()
    }
}
