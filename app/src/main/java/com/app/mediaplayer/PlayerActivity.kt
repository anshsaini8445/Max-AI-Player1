package com.app.mediaplayer

import android.net.Uri
import android.os.Bundle
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

class PlayerActivity : AppCompatActivity() {

    private var player: ExoPlayer? = null
    private var playerView: PlayerView? = null
    private lateinit var gestureDetector: GestureDetector

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layoutResId = resources.getIdentifier("activity_player", "layout", packageName)
        if (layoutResId != 0) {
            setContentView(layoutResId)
        }

        playerView = locatePlayerView()
        if (playerView == null) {
            playerView = PlayerView(this).also {
                setContentView(it)
            }
        }

        initializePlayer()
        setupDoubleTapSeek()
    }

    private fun locatePlayerView(): PlayerView? {
        val candidateIds = listOf("player_view", "playerView", "exo_player_view", "video_view")
        for (idName in candidateIds) {
            val resId = resources.getIdentifier(idName, "id", packageName)
            if (resId != 0) {
                val found = findViewById<PlayerView>(resId)
                if (found != null) return found
            }
        }
        val rootView = findViewById<View>(android.R.id.content)
        return findViewByType(rootView)
    }

    private fun findViewByType(view: View?): PlayerView? {
        if (view is PlayerView) return view
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                val child = findViewByType(view.getChildAt(i))
                if (child != null) return child
            }
        }
        return null
    }

    private fun initializePlayer() {
        player = ExoPlayer.Builder(this).build()
        playerView?.player = player

        val videoUri = intent.data ?: intent.getStringExtra("video_uri")?.let { Uri.parse(it) }
        if (videoUri != null) {
            val mediaItem = MediaItem.fromUri(videoUri)
            player?.setMediaItem(mediaItem)
            player?.prepare()
            player?.playWhenReady = true
        }
    }

    private fun setupDoubleTapSeek() {
        gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDoubleTap(e: MotionEvent): Boolean {
                val p = player ?: return false
                val screenWidth = resources.displayMetrics.widthPixels
                val current = p.currentPosition
                val totalDuration = p.duration

                if (e.x < screenWidth / 2) {
                    val target = (current - 10000).coerceAtLeast(0)
                    p.seekTo(target)
                    Toast.makeText(this@PlayerActivity, "-10s", Toast.LENGTH_SHORT).show()
                } else {
                    val maxLimit = if (totalDuration > 0) totalDuration else Long.MAX_VALUE
                    val target = (current + 10000).coerceAtMost(maxLimit)
                    p.seekTo(target)
                    Toast.makeText(this@PlayerActivity, "+10s", Toast.LENGTH_SHORT).show()
                }
                return true
            }
        })

        playerView?.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
            false
        }
    }

    override fun onResume() {
        super.onResume()
        player?.playWhenReady = true
    }

    override fun onPause() {
        super.onPause()
        player?.playWhenReady = false
    }

    override fun onDestroy() {
        super.onDestroy()
        player?.release()
        player = null
    }
}