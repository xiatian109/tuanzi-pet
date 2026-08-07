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
        }

        return START_STICKY
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

        // 可拖拽：使用 rawX/rawY 避免瞬移
        webView.setOnTouchListener { _, event ->
            val p = webView.layoutParams as WindowManager.LayoutParams
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    downRawX = event.rawX
                    downRawY = event.rawY
                    startX = p.x.toFloat()
                    startY = p.y.toFloat()
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    p.x = (startX + (event.rawX - downRawX)).toInt()
                    p.y = (startY + (event.rawY - downRawY)).toInt()
                    windowManager.updateViewLayout(webView, p)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    // 简单点击识别（未移动则视为"戳一下"）
                    true
                }
                else -> false
            }
        }
    }

    private var downRawX = 0f
    private var downRawY = 0f
    private var startX = 0f
    private var startY = 0f

    override fun onDestroy() {
        super.onDestroy()
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