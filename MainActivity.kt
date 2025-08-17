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
    private val dragInterval = 25L
    private val movementThreshold = 4  // configurable 3–5px

    private val executor = Executors.newSingleThreadExecutor()
    private val uiHandler = Handler(Looper.getMainLooper())

    private var isDoubleTapDragging = false
    private var doubleTapDownTime = 0L
    private val dragDelay = 150L // milliseconds to decide drag vs double-click
    private var isFingerDown = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)
        val touchPad = findViewById<View>(R.id.touchPad)
        val rightClickBtn = findViewById<Button>(R.id.rightClickBtn)
        val leftClickBtn = findViewById<Button>(R.id.leftClickBtn)
        val scrollArea = findViewById<View>(R.id.scrollArea)
        var lastScrollY = 0f


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

        rightClickBtn.setOnClickListener { sendCommand("RCLICK") }
        leftClickBtn.setOnClickListener { sendCommand("LCLICK") }

        // Gesture detector
        gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                if (!isDoubleTapDragging) sendCommand("LCLICK")
                return true
            }

            override fun onDoubleTap(e: MotionEvent): Boolean {
                doubleTapDownTime = System.currentTimeMillis()
                return true
            }

            override fun onDoubleTapEvent(e: MotionEvent): Boolean {
                when (e.action) {
                    MotionEvent.ACTION_DOWN -> {
                        isFingerDown = true
                        doubleTapDownTime = System.currentTimeMillis()
                        uiHandler.postDelayed({
                            if (isFingerDown) { // still holding finger
                                if (!isDoubleTapDragging) {
                                    isDoubleTapDragging = true
                                    sendCommand("DRAG_START")
                                }
                            }
                        }, dragDelay)
                    }

                    MotionEvent.ACTION_UP -> {
                        isFingerDown = false
                        val elapsed = System.currentTimeMillis() - doubleTapDownTime
                        if (isDoubleTapDragging) {
                            sendCommand("DRAG_END")
                            isDoubleTapDragging = false
                        } else if (elapsed < dragDelay) {
                            sendCommand("DCLICK")
                        }
                    }
                }
                return true
            }

            override fun onLongPress(e: MotionEvent) {
                if (!isDoubleTapDragging) sendCommand("RCLICK")
            }
        })

        // Touchpad movement
        touchPad.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)

            if (event.historySize > 0) {
                val dx = (event.x - event.getHistoricalX(0)).toInt()
                val dy = (event.y - event.getHistoricalY(0)).toInt()

                if (abs(dx) < movementThreshold && abs(dy) < movementThreshold) return@setOnTouchListener true

                val now = System.currentTimeMillis()
                val interval = if (isDoubleTapDragging) dragInterval else normalInterval

                if (now - lastSendTime > interval) {
                    if (isDoubleTapDragging) sendCommand("DRAG_MOVE,$dx,$dy")
                    else sendCommand("M,$dx,$dy")
                    lastSendTime = now
                }
            }

            // Finger lifted
            if (event.action == MotionEvent.ACTION_UP && isDoubleTapDragging) {
                sendCommand("DRAG_END")
                isDoubleTapDragging = false
            }

            true
        }

        scrollArea.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    lastScrollY = event.y
                }
                MotionEvent.ACTION_MOVE -> {
                    val dy = event.y - lastScrollY
                    lastScrollY = event.y  // update immediately for quick direction changes

                    if (abs(dy) > 7) {
                        // send with higher scaling for responsiveness
                        val scaledDy = dy * 1.5f
                        sendCommand("SCROLL,$scaledDy")
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {}
            }
            true
        }
    }

    private fun sendCommand(cmd: String) = executor.execute {
        try {
            writer?.println(cmd)
            writer?.flush()
            Log.d("MouseClient", "Sent: $cmd")
        } catch (e: Exception) {
            Log.e("MouseClient", "Send failed", e)
            updateStatus("Send failed: ${e.message}")
        }
    }

    private fun updateStatus(msg: String) = uiHandler.post { statusText.text = msg }

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
