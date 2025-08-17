package com.example.ownmouse

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.io.PrintWriter
import java.net.Socket
import java.util.concurrent.Executors
import kotlin.math.abs

class MainActivity : AppCompatActivity() {

    private val serverIp = "192.168.1.7"   // change to your server IP
    private val serverPort = 5007

    private var socket: Socket? = null
    private var writer: PrintWriter? = null

    private lateinit var gestureDetector: GestureDetector
    private lateinit var statusText: TextView

    private var lastSendTime = 0L
    private val normalInterval = 35L
    private val dragInterval = 15L

    private val executor = Executors.newSingleThreadExecutor()
    private val uiHandler = Handler(Looper.getMainLooper())

    private var isDragging = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)
        val touchPad = findViewById<View>(R.id.touchPad)
        val rightClickBtn = findViewById<Button>(R.id.rightClickBtn)
        val leftClickBtn = findViewById<Button>(R.id.leftClickBtn)

        // Connect in background
        executor.execute {
            try {
                socket = Socket(serverIp, serverPort)
                writer = PrintWriter(socket!!.getOutputStream(), true)
                updateStatus("Connected to $serverIp:$serverPort")
            } catch (e: Exception) {
                updateStatus("Connection failed: ${e.message}")
                Log.e("MouseClient", "Connection error", e)
            }
        }

        rightClickBtn.setOnClickListener {
            sendCommand("RCLICK")
        }

        leftClickBtn.setOnClickListener {
            sendCommand("LCLICK")
        }

        // Setup gesture detector
        gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                sendCommand("LCLICK")
                return true
            }

            override fun onDoubleTap(e: MotionEvent): Boolean {
                // Normal double click if not dragging
                if (!isDragging) {
                    sendCommand("DCLICK")
                }
                return true
            }

            override fun onDoubleTapEvent(e: MotionEvent): Boolean {
                when (e.action) {
                    MotionEvent.ACTION_DOWN -> {
                        // Start drag immediately on second tap hold
                        if (!isDragging) {
                            isDragging = true
                            sendCommand("DRAG_START")
                        }
                    }
                    MotionEvent.ACTION_UP -> {
                        if (isDragging) {
                            sendCommand("DRAG_END")
                            isDragging = false
                        }
                    }
                }
                return true
            }

            override fun onLongPress(e: MotionEvent) {
                // Normal long press = Right click
                if (!isDragging) {
                    sendCommand("RCLICK")
                }
            }
        })

        // Touchpad movement
        touchPad.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)

            when (event.action) {
                MotionEvent.ACTION_MOVE -> {
                    if (event.historySize > 0) {
                        val dx = (event.x - event.getHistoricalX(0)).toInt()
                        val dy = (event.y - event.getHistoricalY(0)).toInt()

                        if (abs(dx) < 3 && abs(dy) < 3) return@setOnTouchListener true

                        val now = System.currentTimeMillis()
                        val interval = if (isDragging) dragInterval else normalInterval
                        if (now - lastSendTime > interval) {
                            if (isDragging) {
                                sendCommand("DRAG_MOVE,$dx,$dy")
                            } else {
                                sendCommand("M,$dx,$dy")
                            }
                            lastSendTime = now
                        }
                    }
                }

                MotionEvent.ACTION_UP -> {
                    if (isDragging) {
                        sendCommand("DRAG_END")
                        isDragging = false
                    }
                }
            }
            true
        }
    }

    private fun sendCommand(cmd: String) {
        executor.execute {
            try {
                writer?.println(cmd)
                writer?.flush()
                Log.d("MouseClient", "Sent: $cmd")
            } catch (e: Exception) {
                Log.e("MouseClient", "Send failed", e)
                updateStatus("Send failed: ${e.message}")
            }
        }
    }

    private fun updateStatus(msg: String) {
        uiHandler.post {
            statusText.text = msg
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        executor.execute {
            try {
                writer?.close()
                socket?.close()
            } catch (e: Exception) {
                Log.e("MouseClient", "Error closing socket", e)
            }
        }
    }
}
