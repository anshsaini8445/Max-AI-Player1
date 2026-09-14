package com.app.mediaplayer

import android.app.PictureInPictureParams
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.Rational
import android.view.View
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem as ExoMediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.google.android.material.bottomsheet.BottomSheetDialog

class PlayerActivity : AppCompatActivity() {

    private var player: ExoPlayer? = null
    private lateinit var playerView: PlayerView

    private var isLocked = false
    private var isMuted = false
    private var currentResizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
    private var currentSpeed = 1.0f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            window.setFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN
            )
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE

            setContentView(R.layout.activity_player)
            playerView = findViewById(R.id.playerView)

            initializePlayer()
            setupControls()
        } catch (e: Exception) {
            e.printStackTrace()
            finish()
        }
    }

    private fun initializePlayer() {
        player = ExoPlayer.Builder(this).build()
        playerView.player = player

        val mediaList = MainActivity.currentMediaList
        val startIndex = intent.getIntExtra("START_INDEX", 0)

        if (mediaList.isNotEmpty()) {
            val exoItems = mediaList.map { item ->
                ExoMediaItem.Builder()
                    .setUri(item.path)
                    .setMediaMetadata(MediaMetadata.Builder().setTitle(item.title).build())
                    .build()
            }
            player?.setMediaItems(exoItems, startIndex, 0L)
            player?.prepare()
            player?.play()
        }

        val tvTitle = playerView.findViewById<TextView>(R.id.tvVideoTitle)
        player?.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: ExoMediaItem?, reason: Int) {
                tvTitle?.text = mediaItem?.mediaMetadata?.title?.toString() ?: "Unknown Video"
                tvTitle?.isSelected = true
            }
        })
    }

    private fun setupControls() {
        // Top Bar
        playerView.findViewById<ImageButton>(R.id.btnBack)?.setOnClickListener { finish() }
        playerView.findViewById<ImageButton>(R.id.btnMoreSettings)?.setOnClickListener { showMoreMenu() }
        playerView.findViewById<ImageButton>(R.id.btnPlaylistVideo)?.setOnClickListener { showPlaylistQueue() }

        // Audio Only Mode Switch
        playerView.findViewById<ImageButton>(R.id.btnAudioOnly)?.setOnClickListener {
            Toast.makeText(this, "Playing in Audio Mode", Toast.LENGTH_SHORT).show()
            val intent = Intent(this, AudioPlayerActivity::class.java).apply {
                putExtra("START_INDEX", player?.currentMediaItemIndex ?: 0)
            }
            startActivity(intent)
            finish()
        }

        // Left Controls: Mute & Lock
        val btnMute = playerView.findViewById<TextView>(R.id.btnMute)
        btnMute?.setOnClickListener {
            isMuted = !isMuted
            player?.volume = if (isMuted) 0f else 1f
            btnMute.text = if (isMuted) "🔇" else "🔊"
            Toast.makeText(this, if (isMuted) "Muted" else "Unmuted", Toast.LENGTH_SHORT).show()
        }

        val btnLock = playerView.findViewById<TextView>(R.id.btnLock)
        btnLock?.setOnClickListener {
            toggleLock(btnLock)
        }

        // Right Controls: Cut & Rotate
        playerView.findViewById<TextView>(R.id.btnCut)?.setOnClickListener {
            Toast.makeText(this, "Video Cutter tool opened", Toast.LENGTH_SHORT).show()
        }

        playerView.findViewById<TextView>(R.id.btnRotate)?.setOnClickListener {
            requestedOrientation = if (requestedOrientation == ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE) {
                ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            } else {
                ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            }
        }

        // Bottom Controls: Speed, Aspect Ratio, PIP
        val btnSpeed = playerView.findViewById<TextView>(R.id.btnSpeed)
        btnSpeed?.setOnClickListener { showSpeedDialog(btnSpeed) }

        playerView.findViewById<ImageButton>(R.id.btnResize)?.setOnClickListener {
            currentResizeMode = when (currentResizeMode) {
                AspectRatioFrameLayout.RESIZE_MODE_FIT -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                AspectRatioFrameLayout.RESIZE_MODE_FILL -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
            }
            playerView.resizeMode = currentResizeMode
            val modeName = when (currentResizeMode) {
                AspectRatioFrameLayout.RESIZE_MODE_FIT -> "Fit to Screen"
                AspectRatioFrameLayout.RESIZE_MODE_FILL -> "Stretch / 16:9"
                else -> "Cropped / Zoom"
            }
            Toast.makeText(this, modeName, Toast.LENGTH_SHORT).show()
        }

        playerView.findViewById<ImageButton>(R.id.btnPip)?.setOnClickListener { enterPipMode() }
    }

    private fun toggleLock(btnLock: TextView) {
        isLocked = !isLocked
        val topBar = playerView.findViewById<View>(R.id.topBarVideo)
        val rightBar = playerView.findViewById<View>(R.id.rightControlsLayout)
        val bottomBar = playerView.findViewById<View>(R.id.bottomControlsLayout)
        val btnMute = playerView.findViewById<View>(R.id.btnMute)

        if (isLocked) {
            topBar?.visibility = View.GONE
            rightBar?.visibility = View.GONE
            bottomBar?.visibility = View.GONE
            btnMute?.visibility = View.GONE
            btnLock.text = "🔒"
            Toast.makeText(this, "Controls Locked", Toast.LENGTH_SHORT).show()
        } else {
            topBar?.visibility = View.VISIBLE
            rightBar?.visibility = View.VISIBLE
            bottomBar?.visibility = View.VISIBLE
            btnMute?.visibility = View.VISIBLE
            btnLock.text = "🔓"
            Toast.makeText(this, "Controls Unlocked", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showSpeedDialog(btnSpeed: TextView) {
        val speeds = arrayOf("0.5x", "0.75x", "1.0x (Normal)", "1.25x", "1.5x", "2.0x")
        val speedValues = floatArrayOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)

        AlertDialog.Builder(this)
            .setTitle("Playback Speed")
            .setItems(speeds) { _, which ->
                currentSpeed = speedValues[which]
                player?.playbackParameters = PlaybackParameters(currentSpeed)
                btnSpeed.text = "${currentSpeed}x"
            }
            .show()
    }

    private fun enterPipMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val params = PictureInPictureParams.Builder()
                .setAspectRatio(Rational(16, 9))
                .build()
            enterPictureInPictureMode(params)
        } else {
            Toast.makeText(this, "Picture-in-Picture requires Android 8.0+", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        playerView.useController = !isInPictureInPictureMode
    }

    private fun showPlaylistQueue() {
        val mediaList = MainActivity.currentMediaList
        val titles = mediaList.map { it.title }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("Now Playing Queue")
            .setItems(titles) { _, which ->
                player?.seekTo(which, 0L)
            }
            .show()
    }

    private fun showMoreMenu() {
        try {
            val dialog = BottomSheetDialog(this)
            val view = layoutInflater.inflate(R.layout.dialog_list_menu, null)
            dialog.setContentView(view)
            dialog.show()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onStop() {
        super.onStop()
        player?.pause()
    }

    override fun onDestroy() {
        super.onDestroy()
        player?.release()
        player = null
    }
}
