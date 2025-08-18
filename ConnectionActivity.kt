package com.example.ownmouse

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class ConnectActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_connection)

        val ipEditText = findViewById<EditText>(R.id.IpTextEdit)
        val portEditText = findViewById<EditText>(R.id.PortTextEdit)
        val connectBtn = findViewById<Button>(R.id.connetBtn)

        connectBtn.setOnClickListener {
            val ip = ipEditText.text.toString().trim()
            val portStr = portEditText.text.toString().trim()

            if (ip.isEmpty() || portStr.isEmpty()) {
                Toast.makeText(this, "Enter IP and Port", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val port = portStr.toIntOrNull()
            if (port == null) {
                Toast.makeText(this, "Invalid Port", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Pass IP + Port to MainActivity
            val intent = Intent(this, MainActivity::class.java)
            intent.putExtra("SERVER_IP", ip)
            intent.putExtra("SERVER_PORT", port)
            startActivity(intent)
        }
    }
}
