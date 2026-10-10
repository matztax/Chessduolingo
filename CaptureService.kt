package com.example.schachhelfer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ContentValues
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.graphics.drawable.Icon
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.MediaStore
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
        const val EXTRA_DELAY = "delay"
        private const val CHANNEL = "capture"
        private const val TAG = "Schachhelfer"
    }

    private val main = Handler(Looper.getMainLooper())
    private var projection: MediaProjection? = null
    private var reader: ImageReader? = null
    private var display: VirtualDisplay? = null
    private var screenW = 0
    private var screenH = 0

    /** Das jeweils neueste Bild bleibt geöffnet, auch wenn der Bildschirm danach still steht. */
    private var heldImage: Image? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startCapture(intent)
            ACTION_SCAN -> {
                val delay = intent.getLongExtra(EXTRA_DELAY, 0L)
                main.postDelayed({ scan() }, delay)
            }
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
        val scanIntent = PendingIntent.getService(
            this, 0,
            Intent(this, CaptureService::class.java)
                .setAction(ACTION_SCAN)
                .putExtra(EXTRA_DELAY, 1500L),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val scanAction = Notification.Action.Builder(
            Icon.createWithResource(this, android.R.drawable.ic_menu_search),
            "Brett scannen", scanIntent
        ).build()
        val notification = Notification.Builder(this, CHANNEL)
            .setContentTitle("Schachhelfer läuft")
            .setContentText("Zum Scannen unten auf \"Brett scannen\" tippen")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .addAction(scanAction)
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
        screenW = bounds.width()
        screenH = bounds.height()
        val dpi = resources.displayMetrics.densityDpi

        val r = ImageReader.newInstance(screenW, screenH, PixelFormat.RGBA_8888, 3)
        r.setOnImageAvailableListener({ rd ->
            val image = try { rd.acquireLatestImage() } catch (e: Exception) { null }
                ?: return@setOnImageAvailableListener
            heldImage?.close()
            heldImage = image
        }, main)
        reader = r
        display = mp.createVirtualDisplay(
            "schachhelfer", screenW, screenH, dpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, r.surface, null, main
        )
        toast("Aufnahme bereit")
    }

    private fun imageToBitmap(image: Image): Bitmap {
        val plane = image.planes[0]
        val rowPadding = plane.rowStride - plane.pixelStride * screenW
        val full = Bitmap.createBitmap(
            screenW + rowPadding / plane.pixelStride, screenH, Bitmap.Config.ARGB_8888
        )
        full.copyPixelsFromBuffer(plane.buffer)
        return if (rowPadding == 0) full else Bitmap.createBitmap(full, 0, 0, screenW, screenH)
    }

    private fun scan() {
        val image = heldImage
        if (image == null) {
            toast("Noch kein Bild. Tippe einmal auf den Bildschirm und versuche es erneut.")
            return
        }
        val frame = imageToBitmap(image)
        Thread {
            val t0 = System.currentTimeMillis()
            val board = BoardDetector.detect(frame)
            val ms = System.currentTimeMillis() - t0
            val msg = if (board == null) {
                saveDebug(frame)
                "Kein Brett gefunden. ${BoardDetector.lastDebug}. Bild in Galerie unter Schachhelfer gespeichert."
            } else {
                "Brett: x=%.0f y=%.0f Feld=%.1f px, Sicherheit %.0f %% (%d ms)".format(
                    board.x, board.y, board.square, board.confidence * 100, ms
                )
            }
            Log.d(TAG, msg)
            toast(msg)
        }.start()
    }

    /** Speichert das gescannte Bild in der Galerie (Pictures/Schachhelfer), damit man es ansehen kann. */
    private fun saveDebug(bmp: Bitmap) {
        try {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "schachhelfer_${System.currentTimeMillis()}.png")
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Schachhelfer")
            }
            val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return
            contentResolver.openOutputStream(uri)?.use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        } catch (e: Exception) {
            Log.e(TAG, "Debug-Bild konnte nicht gespeichert werden", e)
        }
    }

    private fun toast(msg: String) {
        main.post { Toast.makeText(applicationContext, msg, Toast.LENGTH_LONG).show() }
    }

    override fun onDestroy() {
        heldImage?.close()
        display?.release()
        reader?.close()
        projection?.stop()
        super.onDestroy()
    }
}
