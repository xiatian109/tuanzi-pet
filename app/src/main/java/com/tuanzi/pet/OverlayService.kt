package com.tuanzi.pet

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.webkit.WebView
import android.webkit.WebSettings
import android.webkit.WebViewClient
import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import java.io.File

/**
 * 团子精灵悬浮窗前台服务。
 *
 * 用透明 WebView 加载本地 HTML（内嵌 SVG），渲染成一个可拖拽、可点击的悬浮小精灵。
 * 这是「方向B·AI桌面宠物」的"身体"层——AI 大脑不变，经它把桌宠画到屏幕上。
 */
class OverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var webView: WebView

    private val serviceName = "com.tuanzi.pet.TUANZI_PET"

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createNotificationChannel()
        }
        // 前台服务通知，避免被杀
        startForegroundWithNotification()

        if (!::webView.isInitialized) {
            addPetToWindow()
            startMoodWatcher()
        }

        return START_STICKY
    }

    // 心情联动：定时读 /sdcard/Download/mood_state.json，把主导情绪告诉前端 JS 切表情
    private val moodHandler = Handler(Looper.getMainLooper())
    private val moodRunnable = object : Runnable {
        override fun run() {
            try {
                applyMoodFromFile()
            } catch (_: Exception) {
            }
            moodHandler.postDelayed(this, 8000L) // 每 8 秒轮询一次
        }
    }

    private fun startMoodWatcher() {
        moodHandler.removeCallbacks(moodRunnable)
        moodHandler.postDelayed(moodRunnable, 3000L) // 等 WebView 加载完再开始
    }

    private fun stopMoodWatcher() {
        moodHandler.removeCallbacks(moodRunnable)
    }

    private fun applyMoodFromFile() {
        val f = File("/sdcard/Download/mood_state.json")
        if (!f.exists()) return
        val text = f.readText().trim()
        if (text.isEmpty()) return
        val obj = JSONObject(text)
        val joy = obj.optInt("joy", 0)
        val anger = obj.optInt("anger", 0)
        val sad = obj.optInt("sad", 0)
        val worry = obj.optInt("worry", 0)
        val shy = obj.optInt("shy", 0)
        val jealous = obj.optInt("jealousy", 0)

        // 主导情绪判定：最大的那个情绪决定待机表情
        val max = maxOf(joy, anger, sad, worry, shy, jealous)
        val emo = when (max) {
            joy -> if (joy >= 60) "joy" else "calm"
            anger -> if (anger >= 30) "anger" else "calm"
            sad -> if (sad >= 30) "sad" else "calm"
            worry -> if (worry >= 30) "worry" else "calm"
            shy -> if (shy >= 30) "shy" else "calm"
            jealous -> if (jealous >= 30) "jealousy" else "calm"
            else -> "calm"
        }

        if (::webView.isInitialized) {
            webView.post {
                webView.evaluateJavascript("window.applyMood('$emo');", null)
            }
        }
    }

    private fun startForegroundWithNotification() {
        val channelId = "tuanzi_pet_channel"
        val notification = Notification.Builder(this, channelId)
            .setContentTitle("团子精灵")
            .setContentText("正在你的桌边陪着你")
            .setSmallIcon(android.R.drawable.star_on)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(1, notification)
        }
    }

    private fun createNotificationChannel() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            "tuanzi_pet_channel",
            "团子精灵",
            NotificationManager.IMPORTANCE_LOW
        )
        manager.createNotificationChannel(channel)
    }

    private fun addPetToWindow() {
        // 透明 WebView 承载 SVG
        webView = WebView(this).apply {
            setBackgroundColor(0x00000000)
            setInitialScale(100)
            settings.apply {
                javaScriptEnabled = true
                allowFileAccess = true
                domStorageEnabled = true
            }
            webViewClient = WebViewClient()

            @Suppress("DEPRECATION")
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW

            // 加载本地 HTML（SVG 内嵌）
            loadUrl("file:///android_asset/tuanzi.html")
        }

        val size = 220
        val params = WindowManager.LayoutParams(
            size,
            size,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 40
            y = 300
        }

        windowManager.addView(webView, params)

        // 可拖拽 + 可点击：用位移阈值区分"拖拽"和"戳一下"
        // 若只是轻点（位移很小），放行给 WebView 内的 JS 处理 click → 触发表情
        webView.setOnTouchListener { view, event ->
            val p = view.layoutParams as WindowManager.LayoutParams
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    downRawX = event.rawX
                    downRawY = event.rawY
                    startX = p.x.toFloat()
                    startY = p.y.toFloat()
                    isDragging = false
                    // 第一下 always return true，以便捕获后续 MOVE/UP
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downRawX
                    val dy = event.rawY - downRawY
                    // 超过拖动阈值才开始移动窗口
                    if (Math.abs(dx) > touchSlop || Math.abs(dy) > touchSlop) {
                        isDragging = true
                        p.x = (startX + dx).toInt()
                        p.y = (startY + dy).toInt()
                        windowManager.updateViewLayout(view, p)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    // 轻点（没拖动）→ 视为"戳一下"，直接调 JS 表情函数（绕过 WebView click）
                    if (!isDragging) {
                        webView.post {
                            webView.evaluateJavascript("onPetTouch();", null)
                        }
                    }
                    true
                }
                else -> false
            }
        }
        webView.isClickable = true
        webView.isFocusable = true
    }

    private var downRawX = 0f
    private var downRawY = 0f
    private var startX = 0f
    private var startY = 0f
    private var isDragging = false
    private val touchSlop = 12  // 像素位移阈值，超过才算拖拽

    override fun onDestroy() {
        super.onDestroy()
        stopMoodWatcher()
        if (::webView.isInitialized) {
            try {
                windowManager.removeView(webView)
            } catch (_: Exception) {
            }
            webView.destroy()
        }
    }

    companion object {
        const val ACTION_SHOW = "com.tuanzi.pet.ACTION_SHOW"
        const val ACTION_HIDE = "com.tuanzi.pet.ACTION_HIDE"
    }
}