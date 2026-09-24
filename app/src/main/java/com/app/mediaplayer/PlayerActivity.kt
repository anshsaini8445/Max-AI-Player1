package com.app.mediaplayer

import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import kotlin.math.abs

class PlayerActivity : AppCompatActivity() {

    private var player: ExoPlayer? = null
    private lateinit var playerView: PlayerView
    private lateinit var layoutRewindOverlay: View
    private lateinit var layoutForwardOverlay: View

    private val handler = Handler(Looper.getMainLooper())
    private val hideOverlayRunnable = Runnable {
        layoutRewindOverlay.visibility = View.GONE
        layoutForwardOverlay.visibility = View.GONE
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player)

        playerView = findViewById(R.id.playerView)
        layoutRewindOverlay = findViewById(R.id.layoutRewindOverlay)
        layoutForwardOverlay = findViewById(R.id.layoutForwardOverlay)

        val videoUriString = intent.getStringExtra("video_uri")
        val videoUri = if (videoUriString != null) Uri.parse(videoUriString) else null

        player = ExoPlayer.Builder(this).build().apply {
            playerView.player = this
            if (videoUri != null) {
                setMediaItem(MediaItem.fromUri(videoUri))
            }
            prepare()
            play()
        }

        setupDoubleTapGesture()
    }

    private fun setupDoubleTapGesture() {
        val gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDoubleTap(e: MotionEvent): Boolean {
                val screenWidth = playerView.width
                if (screenWidth > 0) {
                    if (e.x < screenWidth / 2) {
                        // Rewind 10 seconds
                        player?.let {
                            val targetPosition = maxOf(0L, it.currentPosition - 10000L)
                            it.seekTo(targetPosition)
                        }
                        showRewindOverlay()
                    } else {
                        // Forward 10 seconds
                        player?.let {
                            val targetPosition = minOf(it.duration, it.currentPosition + 10000L)
                            it.seekTo(targetPosition)
                        }
                        showForwardOverlay()
                    }
                }
                return true
            }

            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                if (playerView.isControllerVisible) {
                    playerView.hideController()
                } else {
                    playerView.showController()
                }
                return true
            }
        })

        playerView.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
            false
        }
    }

    private fun showRewindOverlay() {
        handler.removeCallbacks(hideOverlayRunnable)
        layoutForwardOverlay.visibility = View.GONE
        layoutRewindOverlay.visibility = View.VISIBLE
        handler.postDelayed(hideOverlayRunnable, 800)
    }

    private fun showForwardOverlay() {
        handler.removeCallbacks(hideOverlayRunnable)
        layoutRewindOverlay.visibility = View.GONE
        layoutForwardOverlay.visibility = View.VISIBLE
        handler.postDelayed(hideOverlayRunnable, 800)
    }

    override fun onStop() {
        super.onStop()
        player?.pause()
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        player?.release()
        player = null
    }
}
