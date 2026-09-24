package com.app.mediaplayer

import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

class PlayerActivity : AppCompatActivity() {

    private var player: ExoPlayer? = nullptr_placeholder_fix()
    private lateinit var playerView: PlayerView
    private var isFullscreen = false
    private var isLocked = false

    private fun nullptr_placeholder_fix(): ExoPlayer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player)

        // Hide system bars for immersive video experience
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val windowInsetsController = WindowInsetsControllerCompat(window, window.decorView)
        windowInsetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        playerView = findViewById(R.id.player_view)
        
        val videoUriString = intent.getStringExtra("VIDEO_URI")
        val videoUri = if (videoUriString != null) Uri.parse(videoUriString) else null

        setupCustomControls()

        player = ExoPlayer.Builder(this).build().apply {
            playerView.player = this
            if (videoUri != null) {
                setMediaItem(MediaItem.fromUri(videoUri))
            }
            prepare()
            playWhenReady = true
        }
    }

    private fun setupCustomControls() {
        val btnRotate = playerView.findViewById<ImageButton>(R.id.btn_rotate)
        val btnFullscreen = playerView.findViewById<ImageButton>(R.id.btn_fullscreen)
        val btnLock = playerView.findViewById<ImageButton>(R.id.btn_lock)
        val btnScreenshot = playerView.findViewById<ImageButton>(R.id.btn_screenshot)
        val btnEqualizer = playerView.findViewById<ImageButton>(R.id.btn_equalizer)
        val btnAudioTrack = playerView.findViewById<ImageButton>(R.id.btn_audio_track)
        val btnSubtitle = playerView.findViewById<ImageButton>(R.id.btn_subtitle)
        val btnSpeed = playerView.findViewById<ImageButton>(R.id.btn_speed)
        val tvTitle = playerView.findViewById<TextView>(R.id.tv_title)

        tvTitle?.text = intent.getStringExtra("VIDEO_TITLE") ?: "Video Player"

        btnRotate?.setOnClickListener {
            requestedOrientation = if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
                ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            } else {
                ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            }
        }

        btnFullscreen?.setOnClickListener {
            isFullscreen = !isFullscreen
            requestedOrientation = if (isFullscreen) {
                ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            } else {
                ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
        }

        btnLock?.setOnClickListener {
            isLocked = !isLocked
            val controlsGroup = playerView.findViewById<View>(R.id.controls_group)
            if (isLocked) {
                controlsGroup?.visibility = View.GONE
                btnLock.setImageResource(android.R.drawable.ic_lock_lock)
            } else {
                controlsGroup?.visibility = View.VISIBLE
                btnLock.setImageResource(android.R.drawable.ic_lock_idle_lock)
            }
        }

        btnScreenshot?.setOnClickListener {
            player?.let {
                ScreenshotHelper.takeScreenshot(this, playerView, it)
            }
        }

        btnEqualizer?.setOnClickListener {
            startActivity(Intent(this, EqualizerActivity::class.java))
        }

        // Using isControllerFullyVisible() instead of unresolved isControllerVisible reference
        playerView.setControllerVisibilityListener(PlayerView.ControllerVisibilityListener { visibility ->
            if (visibility == View.VISIBLE) {
                showSystemBars()
            } else {
                if (playerView.isControllerFullyVisible().not() && !isLocked) {
                    hideSystemBars()
                }
            }
        })
    }

    private fun hideSystemBars() {
        WindowInsetsControllerCompat(window, window.decorView).let { controller ->
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    private fun showSystemBars() {
        WindowInsetsControllerCompat(window, window.decorView).show(WindowInsetsCompat.Type.systemBars())
    }

    override fun onResume() {
        super.onResume()
        hideSystemBars()
        player?.play()
    }

    override fun onPause() {
        super.onPause()
        player?.pause()
    }

    override fun onDestroy() {
        super.onDestroy()
        player?.release()
        player = null
    }
}
