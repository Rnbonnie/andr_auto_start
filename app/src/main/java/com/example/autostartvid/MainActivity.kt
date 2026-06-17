package com.example.autostartvid

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import android.widget.Toast
import android.widget.VideoView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private lateinit var videoView: VideoView
    private val VIDEO_PATH = "/autostart/auto.mp4"
    private val handler = Handler(Looper.getMainLooper())
    private var retryCount = 0
    private val MAX_RETRIES = 5
    
    private var startTimeMillis: Long = 0
    private val WAIT_DURATION_MILLIS = 3000L
    private var isPlaybackStarted = false
    
    private val CHANNEL_ID = "autostart_status"
    private val NOTIFICATION_ID = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)
        videoView = findViewById(R.id.videoView)

        createNotificationChannel()

        if (startTimeMillis == 0L) {
            startTimeMillis = SystemClock.elapsedRealtime()
        }

        if (checkAllPermissions()) {
            startCountdown()
        } else {
            requestAllPermissions()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(CHANNEL_ID, "Autostart", NotificationManager.IMPORTANCE_LOW)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun updateNotification(message: String) {
        try {
            val builder = NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setContentTitle("Autostart Video")
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)

            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.notify(NOTIFICATION_ID, builder.build())
        } catch (e: Exception) {
            Log.e("MainActivity", "Notification error", e)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (checkAllPermissions()) {
            startCountdown()
        }
    }

    private fun startCountdown() {
        handler.removeCallbacksAndMessages(null)
        
        val elapsed = SystemClock.elapsedRealtime() - startTimeMillis
        val remaining = WAIT_DURATION_MILLIS - elapsed

        if (remaining > 0) {
            val secRemaining = remaining / 1000
            statusText.visibility = View.VISIBLE
            statusText.text = "Launching video in $secRemaining sec..."
            updateNotification("Launching in $secRemaining s...")
            handler.postDelayed({
                startCountdown()
            }, 1000)
        } else {
            if (!isPlaybackStarted) {
                isPlaybackStarted = true
                statusText.visibility = View.VISIBLE
                statusText.text = "Starting playback..."
                updateNotification("Video starting")
                playVideoInternally()
            }
        }
    }

    private fun checkAllPermissions(): Boolean {
        val storageOk = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }
        
        val notificationOk = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

        // On Android 9 and below, we don't strictly require overlay permission for autostart
        val overlayOk = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            checkOverlayPermission()
        } else {
            true
        }

        return storageOk && notificationOk && overlayOk
    }

    private fun requestAllPermissions() {
        val permissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_VIDEO) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.READ_MEDIA_VIDEO)
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }

        if (permissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permissions.toTypedArray(), 100)
        } else {
            // If storage is OK but overlay isn't (on Android 10+), try to request it
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && !checkOverlayPermission()) {
                requestOverlayPermission()
            } else {
                // On Android 9 or if everything is fine, just start
                startCountdown()
            }
        }
    }

    private fun checkOverlayPermission(): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Settings.canDrawOverlays(this)
            } else {
                true
            }
        } catch (e: Exception) {
            true // Assume OK if check fails
        }
    }

    private fun requestOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
                startActivityForResult(intent, 101)
            } catch (e: Exception) {
                Log.e("MainActivity", "Settings screen not found", e)
                // If screen not found, just proceed to countdown
                startCountdown()
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (checkAllPermissions()) {
            startCountdown()
        }
    }

    private fun getRandomVideoFromDir(dirPath: String): File? {
        val dir = File(dirPath)
        if (dir.exists() && dir.isDirectory) {
            val videoExtensions = listOf(".mp4", ".mkv", ".avi", ".webm", ".3gp")
            val files = dir.listFiles()
            if (files != null) {
                val videoFiles = files.filter { file ->
                    file.isFile && videoExtensions.any { ext -> file.name.lowercase().endsWith(ext) }
                }
                if (videoFiles.isNotEmpty()) {
                    return videoFiles.random()
                }
            }
        }
        return null
    }

    private fun playVideoInternally() {
        var videoFile: File? = null

        videoFile = getRandomVideoFromDir(Environment.getExternalStorageDirectory().absolutePath + "/autostart")

        if (videoFile == null) {
            val moviesFile = File(Environment.getExternalStorageDirectory().absolutePath + "/Movies/auto.mp4")
            if (moviesFile.exists()) {
                videoFile = moviesFile
            }
        }

        if (videoFile == null) {
            val downloadFile = File(Environment.getExternalStorageDirectory().absolutePath + "/Download/auto.mp4")
            if (downloadFile.exists()) {
                videoFile = downloadFile
            }
        }

        if (videoFile == null) {
            videoFile = getRandomVideoFromDir("/sdcard/autostart")
        }

        if (videoFile == null) {
            videoFile = getRandomVideoFromDir("/storage/emulated/0/autostart")
        }

        if (videoFile != null) {
            try {
                statusText.visibility = View.GONE
                
                videoView.setVideoPath(videoFile.absolutePath)
                videoView.setOnPreparedListener { mp ->
                    mp.isLooping = true
                    videoView.start()
                    updateNotification("Video playing")
                }
                
                videoView.setOnErrorListener { _, what, extra ->
                    Log.e("MainActivity", "Video error: $what, $extra")
                    statusText.visibility = View.VISIBLE
                    statusText.text = "Video error: $what"
                    handleRetry()
                    true
                }

                videoView.setOnCompletionListener {
                    videoView.start() // Ensure looping
                }

            } catch (e: Exception) {
                Log.e("MainActivity", "Error playing video", e)
                statusText.visibility = View.VISIBLE
                statusText.text = "Error: ${e.message}"
                handleRetry()
            }
        } else {
            if (retryCount < MAX_RETRIES) {
                retryCount++
                statusText.text = "Video not found, retrying ($retryCount/$MAX_RETRIES)..."
                handler.postDelayed({ playVideoInternally() }, 3000)
            } else {
                statusText.visibility = View.VISIBLE
                statusText.text = "Error: Video file not found!"
            }
        }
    }

    private fun handleRetry() {
        if (retryCount < MAX_RETRIES) {
            retryCount++
            handler.postDelayed({ playVideoInternally() }, 5000)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
    }
}
