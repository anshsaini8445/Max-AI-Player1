package com.app.mediaplayer

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        findViewById<ImageButton>(R.id.btnBackSettings)?.setOnClickListener { finish() }

        // 1. General Settings
        findViewById<View>(R.id.itemGeneral)?.setOnClickListener {
            val items = arrayOf("Language: English / Hindi", "Clear Watch History", "Clear Search History")
            AlertDialog.Builder(this)
                .setTitle("General Settings")
                .setItems(items) { _, which ->
                    Toast.makeText(this, "${items[which]} selected", Toast.LENGTH_SHORT).show()
                }.show()
        }

        // 2. Video Settings Dialog (Pop-up, gestures, orientation, resume)
        findViewById<View>(R.id.itemVideo)?.setOnClickListener {
            val videoOptions = arrayOf(
                "✓ Show video headlines",
                "✓ Hardware Acceleration (HW)",
                "✓ Double-tap to seek (10s)",
                "✓ Resume from last position",
                "✓ Swipe gestures for volume/brightness",
                "✓ Background / Pop-up PiP mode"
            )
            AlertDialog.Builder(this)
                .setTitle("Video Playback Settings")
                .setItems(videoOptions) { _, which ->
                    Toast.makeText(this, "${videoOptions[which]} toggled", Toast.LENGTH_SHORT).show()
                }.show()
        }

        // 3. Audio Settings
        findViewById<View>(R.id.itemAudio)?.setOnClickListener {
            val audioOptions = arrayOf("Filter short audios (< 30s)", "Auto Scan .nomedia folders", "Audio Bitrate: Auto")
            AlertDialog.Builder(this)
                .setTitle("Audio Settings")
                .setItems(audioOptions) { _, which ->
                    Toast.makeText(this, "${audioOptions[which]} updated", Toast.LENGTH_SHORT).show()
                }.show()
        }

        // 4. Downloads
        findViewById<View>(R.id.itemDownloads)?.setOnClickListener {
            Toast.makeText(this, "Download Location: /storage/emulated/0/Download", Toast.LENGTH_LONG).show()
        }

        // 5. Subscription (Opens VIP Pass Activity)
        findViewById<View>(R.id.itemSubscription)?.setOnClickListener {
            startActivity(Intent(this, SubscriptionActivity::class.java))
        }

        // 6. Game Center Toggle
        findViewById<SwitchCompat>(R.id.switchGame)?.setOnCheckedChangeListener { _, isChecked ->
            Toast.makeText(this, if (isChecked) "Game content blocked" else "Game content enabled", Toast.LENGTH_SHORT).show()
        }

        // 7. About Us
        findViewById<View>(R.id.itemAbout)?.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("About MX Player")
                .setMessage("MX Player Pro v2.5\nSmooth 8K/4K Video, Music & Audio Engine.\nBuilt with high-performance playback architecture.")
                .setPositiveButton("OK", null)
                .show()
        }
    }
}
