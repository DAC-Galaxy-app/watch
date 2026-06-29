package com.dacgalaxy.heartproject

import android.os.Bundle
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.dacgalaxy.heartproject.bridge.SignalQueueStore

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
    }

    private fun updateStatus() {
        val store = SignalQueueStore(this)
        findViewById<TextView>(R.id.statusText).text = buildString {
            appendLine("Received ${store.receivedCount()}")
            appendLine("Uploaded ${store.uploadedCount()}")
            appendLine("Queued ${store.queuedCount()}")
            append("Latest ${store.lastTracker()}")
        }

        val serverBaseUrl = getString(R.string.server_base_url).trim()
        findViewById<TextView>(R.id.serverText).text =
            if (serverBaseUrl.isBlank()) {
                "Server: not configured, samples stay queued"
            } else {
                "Server: $serverBaseUrl/api/v1/signals"
            }
    }
}
