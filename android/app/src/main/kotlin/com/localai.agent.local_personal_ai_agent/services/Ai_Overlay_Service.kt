package com.localai.agent.local_personal_ai_agent.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.core.app.NotificationCompat
import io.flutter.embedding.android.FlutterView
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.embedding.engine.dart.DartExecutor

class Ai_Overlay_Service : Service() {

    private var Window_Manager: WindowManager? = null
    private var Overlay_Container: FrameLayout? = null
    private var Flutter_Engine: FlutterEngine? = null
    private var Flutter_View_Instance: FlutterView? = null

    companion object {
        private const val Notification_Id = 1001
        private const val Channel_Id = "Ai_Overlay_Channel"
    }

    override fun onCreate() {
        super.onCreate()
        Log.d("Ai_Overlay", "Creating Overlay Service...")
        Start_Foreground_Notification()
        Setup_Overlay_View()
    }

    private fun Start_Foreground_Notification() {
        val Notification_Manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val Channel = NotificationChannel(Channel_Id, "Overlay Service", NotificationManager.IMPORTANCE_LOW)
            Notification_Manager.createNotificationChannel(Channel)
        }

        val Notification: Notification = NotificationCompat.Builder(this, Channel_Id)
            .setContentTitle("Local AI Agent")
            .setContentText("Burbuja flotante activa")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        startForeground(Notification_Id, Notification)
    }

    private fun Setup_Overlay_View() {
        Window_Manager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        Flutter_Engine = FlutterEngine(this)
        Flutter_Engine?.navigationChannel?.setInitialRoute("/overlay")
        Flutter_Engine?.dartExecutor?.executeDartEntrypoint(
            DartExecutor.DartEntrypoint.createDefault()
        )

        Flutter_View_Instance = FlutterView(this)
        Flutter_View_Instance?.attachToFlutterEngine(Flutter_Engine!!)

        Overlay_Container = FrameLayout(this)
        Overlay_Container?.addView(Flutter_View_Instance)

        val Params = WindowManager.LayoutParams(
            350,
            450,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) 
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY 
            else 
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )

        Params.gravity = Gravity.TOP or Gravity.START
        Params.x = 200
        Params.y = 300

        Overlay_Container?.setOnTouchListener(object : View.OnTouchListener {
            private var Initial_X = 0
            private var Initial_Y = 0
            private var Initial_Touch_X = 0f
            private var Initial_Touch_Y = 0f

            override fun onTouch(V: View?, Event: MotionEvent?): Boolean {
                if (Event == null) return false
                when (Event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        Initial_X = Params.x
                        Initial_Y = Params.y
                        Initial_Touch_X = Event.rawX
                        Initial_Touch_Y = Event.rawY
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        Params.x = Initial_X + (Event.rawX - Initial_Touch_X).toInt()
                        Params.y = Initial_Y + (Event.rawY - Initial_Touch_Y).toInt()
                        Window_Manager?.updateViewLayout(Overlay_Container, Params)
                        return true
                    }
                }
                return false
            }
        })

        try {
            Window_Manager?.addView(Overlay_Container, Params)
            Log.d("Ai_Overlay", "Added Overlay View to WindowManager")
        } catch (E: Exception) {
            Log.e("Ai_Overlay", "Failed to add overlay view", E)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d("Ai_Overlay", "Destroying Overlay Service...")
        if (Overlay_Container != null && Window_Manager != null) {
            try {
                Window_Manager?.removeView(Overlay_Container)
            } catch (E: Exception) {
                Log.e("Ai_Overlay", "Error removing overlay view", E)
            }
        }
        Flutter_View_Instance?.detachFromFlutterEngine()
        Flutter_Engine?.destroy()
    }

    override fun onBind(Intent: Intent?): IBinder? {
        return null
    }
}
