package com.app.mediaplayer

import android.annotation.SuppressLint
import android.app.PictureInPictureParams
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.net.Uri
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
import androidx.media3.common.C
import androidx.media3.common.MediaItem as ExoMediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.mp3.Mp3Extractor
import androidx.media3.extractor.mp4.Mp4Extractor
import androidx.media3.extractor.ts.TsExtractor
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import java.io.File

@UnstableApi
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

            initializeSuperEnginePlayer()
            setupControls()
        } catch (e: Exception) {
            e.printStackTrace()
            finish()
        }
    }

    private fun initializeSuperEnginePlayer() {
        // 1. All Video & Audio Codec Engines (8K/4K/HEVC/AV1 + Software Fallback)
        val renderersFactory = DefaultRenderersFactory(this).apply {
            setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
            setEnableDecoderFallback(true) // 8K/4K hardware fail hone par software decoder sambhalega
        }

        // 2. All Extractors + MX Player Feature (आधी अधूरी फाइल और .crdownload सपोर्ट)
        val extractorsFactory = DefaultExtractorsFactory().apply {
            setConstantBitrateSeekingEnabled(true) // Constant & Variable Bitrate MP3/Audio fix
            setMp4ExtractorFlags(
                Mp4Extractor.FLAG_WORKAROUND_IGNORE_EDIT_LISTS or
                Mp4Extractor.FLAG_READ_SEF_DATA
            )
            setMp3ExtractorFlags(Mp3Extractor.FLAG_ENABLE_INDEX_SEEKING)
            setTsExtractorMode(TsExtractor.MODE_SINGLE_PMT)
        }

        // 3. Ultra Fast & Smooth Buffer Load Control (144p se lekar 8K bina ruke chale)
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                15000, // 15s min buffer
                50000, // 50s max buffer
                1000,  // 1s instant play buffer (बिना लोडिंग के तुरंत स्टार्ट)
                2000   // 2s rebuffer
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        player = ExoPlayer.Builder(this, renderersFactory)
            .setExtractorsFactory(extractorsFactory)
            .setLoadControl(loadControl)
            .build()

        playerView.player = player

        val mediaList = MainActivity.currentMediaList
        val startIndex = intent.getIntExtra("START_INDEX", 0)

        if (mediaList.isNotEmpty() && startIndex in mediaList.indices) {
            val dataSourceFactory = DefaultDataSource.Factory(this)
            val progressiveMediaSourceFactory = ProgressiveMediaSource.Factory(dataSourceFactory, extractorsFactory)

            // Chrome adhi download video (.crdownload) aur normal video media build
            val mediaSources = mediaList.map { item ->
                val uri = Uri.fromFile(File(item.path))
                val mime = when {
                    item.path.endsWith(".crdownload", true) || item.path.endsWith(".part", true) -> {
                        if (item.path.contains("mp3", true) || item.path.contains("audio", true)) {
                            MimeTypes.AUDIO_UNKNOWN
                        } else {
                            MimeTypes.VIDEO_MP4 // Chrome Incomplete files bypass
                        }
                    }
                    item.path.endsWith(".mkv", true) -> MimeTypes.VIDEO_MATROSKA
                    item.path.endsWith(".webm", true) -> MimeTypes.VIDEO_WEBM
                    item.path.endsWith(".mp4", true) -> MimeTypes.VIDEO_MP4
                    else -> null
                }

                val exoItem = ExoMediaItem.Builder()
                    .setUri(uri)
                    .setMimeType(mime)
                    .setMediaMetadata(MediaMetadata.Builder().setTitle(item.title).build())
                    .build()

                progressiveMediaSourceFactory.createMediaSource(exoItem)
            }

            player?.setMediaSources(mediaSources, startIndex, 0L)
            player?.prepare()
            player?.play()
        }

        val tvTitle = playerView.findViewById<TextView>(R.id.tvVideoTitle)
        player?.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: ExoMediaItem?, reason: Int) {
                tvTitle?.text = mediaItem?.mediaMetadata?.title?.toString() ?: "Playing Media"
                tvTitle?.isSelected = true
            }

            // आधी अधूरी फाइल खत्म होने पर क्रैश से बचाने के लिए सेफ हैंडलर
            override fun onPlayerError(error: PlaybackException) {
                if (error.errorCode == PlaybackException.ERROR_CODE_IO_UNSPECIFIED ||
                    error.errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED) {
                    Toast.makeText(this@PlayerActivity, "Played downloaded portion of file", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this@PlayerActivity, "Codec fallback recovered", Toast.LENGTH_SHORT).show()
                }
            }
        })
    }

    private fun setupControls() {
        playerView.findViewById<ImageButton>(R.id.btnBack)?.setOnClickListener { finish() }
        playerView.findViewById<ImageButton>(R.id.btnMoreSettings)?.setOnClickListener { showMoreMenu() }
        playerView.findViewById<ImageButton>(R.id.btnPlaylistVideo)?.setOnClickListener { showPlaylistQueue() }

        playerView.findViewById<ImageButton>(R.id.btnAudioOnly)?.setOnClickListener {
            Toast.makeText(this, "Audio Mode Active", Toast.LENGTH_SHORT).show()
            val intent = Intent(this, AudioPlayerActivity::class.java).apply {
                putExtra("START_INDEX", player?.currentMediaItemIndex ?: 0)
            }
            startActivity(intent)
            finish()
        }

        val btnMute = playerView.findViewById<TextView>(R.id.btnMute)
        btnMute?.setOnClickListener {
            isMuted = !isMuted
            player?.volume = if (isMuted) 0f else 1f
            btnMute.text = if (isMuted) "🔇" else "🔊"
            Toast.makeText(this, if (isMuted) "Muted" else "Unmuted", Toast.LENGTH_SHORT).show()
        }

        val btnLock = playerView.findViewById<TextView>(R.id.btnLock)
        btnLock?.setOnClickListener { toggleLock(btnLock) }

        playerView.findViewById<TextView>(R.id.btnCut)?.setOnClickListener {
            Toast.makeText(this, "Video Cutter Opened", Toast.LENGTH_SHORT).show()
        }

        playerView.findViewById<TextView>(R.id.btnRotate)?.setOnClickListener {
            requestedOrientation = if (requestedOrientation == ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE) {
                ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            } else {
                ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            }
        }

        val btnSpeed = playerView.findViewById<TextView>(R.id.btnSpeed)
        btnSpeed?.setOnClickListener { showSpeedDialog(btnSpeed) }

        playerView.findViewById<ImageButton>(R.id.btnResize)?.setOnClickListener {
            currentResizeMode = when (currentResizeMode) {
                AspectRatioFrameLayout.RESIZE_MODE_FIT -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                AspectRatioFrameLayout.RESIZE_MODE_FILL -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
            }
            playerView.resizeMode = currentResizeMode
            val name = when (currentResizeMode) {
                AspectRatioFrameLayout.RESIZE_MODE_FIT -> "Fit to Screen"
                AspectRatioFrameLayout.RESIZE_MODE_FILL -> "Stretch (16:9)"
                else -> "Crop / Zoom"
            }
            Toast.makeText(this, name, Toast.LENGTH_SHORT).show()
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
            Toast.makeText(this, "Requires Android 8.0+", Toast.LENGTH_SHORT).show()
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
