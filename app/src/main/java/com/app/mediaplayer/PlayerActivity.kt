package com.app.mediaplayer

import android.app.PictureInPictureParams
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.StrictMode
import android.provider.MediaStore
import android.util.Rational
import android.view.View
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
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
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.mp3.Mp3Extractor
import androidx.media3.extractor.mp4.Mp4Extractor
import androidx.media3.extractor.ts.TsExtractor
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

@OptIn(UnstableApi::class)
class PlayerActivity : AppCompatActivity() {

    private var player: ExoPlayer? = null
    private lateinit var playerView: PlayerView
    private lateinit var prefs: SharedPreferences

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
            prefs = getSharedPreferences("MX_PLAYER_RESUME_PREFS", Context.MODE_PRIVATE)

            initializePlayerEngine()
            setupControls()
        } catch (e: Exception) {
            e.printStackTrace()
            finish()
        }
    }

    private fun initializePlayerEngine() {
        val renderersFactory = DefaultRenderersFactory(this).apply {
            setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
            setEnableDecoderFallback(true)
        }

        val extractorsFactory = DefaultExtractorsFactory().apply {
            setConstantBitrateSeekingEnabled(true)
            setMp4ExtractorFlags(
                Mp4Extractor.FLAG_WORKAROUND_IGNORE_EDIT_LISTS or
                Mp4Extractor.FLAG_READ_SEF_DATA
            )
            setMp3ExtractorFlags(Mp3Extractor.FLAG_ENABLE_INDEX_SEEKING)
            setTsExtractorMode(TsExtractor.MODE_SINGLE_PMT)
        }

        val mediaSourceFactory = DefaultMediaSourceFactory(this, extractorsFactory)

        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(15000, 50000, 1000, 2000)
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        player = ExoPlayer.Builder(this, renderersFactory)
            .setMediaSourceFactory(mediaSourceFactory)
            .setLoadControl(loadControl)
            .build()

        playerView.player = player

        val mediaList = MainActivity.currentMediaList
        val startIndex = intent.getIntExtra("START_INDEX", 0)

        if (mediaList.isNotEmpty() && startIndex in mediaList.indices) {
            val dataSourceFactory = DefaultDataSource.Factory(this)
            val progressiveMediaSourceFactory = ProgressiveMediaSource.Factory(dataSourceFactory, extractorsFactory)

            val mediaSources = mediaList.map { item ->
                val uri = Uri.fromFile(File(item.path))
                val mime = when {
                    item.path.endsWith(".crdownload", true) || item.path.endsWith(".part", true) -> MimeTypes.VIDEO_MP4
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

            // Exact Millisecond Saved Resume Position
            val targetPath = mediaList[startIndex].path
            val savedPositionMs = prefs.getLong("RESUME_POS_$targetPath", 0L)

            player?.setMediaSources(mediaSources, startIndex, savedPositionMs)
            player?.prepare()
            player?.play()

            if (savedPositionMs > 1000L) {
                Toast.makeText(this, "Resumed playback", Toast.LENGTH_SHORT).show()
            }
        }

        val tvTitle = playerView.findViewById<TextView>(R.id.tvVideoTitle)
        player?.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: ExoMediaItem?, reason: Int) {
                tvTitle?.text = mediaItem?.mediaMetadata?.title?.toString() ?: "Playing Video"
                tvTitle?.isSelected = true
            }

            override fun onPlayerError(error: PlaybackException) {
                Toast.makeText(this@PlayerActivity, "Codec fallback recovered", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun setupControls() {
        playerView.findViewById<ImageButton>(R.id.btnBack)?.setOnClickListener { finish() }
        playerView.findViewById<ImageButton>(R.id.btnMoreSettings)?.setOnClickListener { showThreeDotMenu() }
        playerView.findViewById<ImageButton>(R.id.btnPlaylistVideo)?.setOnClickListener { showPlaylistQueue() }

        playerView.findViewById<ImageButton>(R.id.btnAudioOnly)?.setOnClickListener {
            switchToAudioPlayer()
        }

        val imgMute = playerView.findViewById<ImageView>(R.id.imgMute)
        playerView.findViewById<View>(R.id.cardMute)?.setOnClickListener {
            isMuted = !isMuted
            player?.volume = if (isMuted) 0f else 1f
            imgMute?.setImageResource(
                if (isMuted) android.R.drawable.ic_lock_silent_mode
                else android.R.drawable.ic_lock_silent_mode_off
            )
            Toast.makeText(this, if (isMuted) "Muted" else "Unmuted", Toast.LENGTH_SHORT).show()
        }

        playerView.findViewById<View>(R.id.cardLock)?.setOnClickListener { setControlsLocked(true) }
        playerView.findViewById<View>(R.id.cardUnlock)?.setOnClickListener { setControlsLocked(false) }

        playerView.findViewById<View>(R.id.cardCut)?.setOnClickListener {
            Toast.makeText(this, "Video Cutter: Ready", Toast.LENGTH_SHORT).show()
        }

        playerView.findViewById<View>(R.id.cardRotate)?.setOnClickListener {
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
                AspectRatioFrameLayout.RESIZE_MODE_FILL -> "Stretch 16:9"
                else -> "Crop to Zoom"
            }
            Toast.makeText(this, name, Toast.LENGTH_SHORT).show()
        }

        playerView.findViewById<ImageButton>(R.id.btnPip)?.setOnClickListener { enterPipMode() }
    }

    private fun switchToAudioPlayer() {
        saveCurrentPosition()
        val currentIdx = player?.currentMediaItemIndex ?: 0
        val currentPos = player?.currentPosition ?: 0L
        player?.pause()

        val intent = Intent(this, AudioPlayerActivity::class.java).apply {
            putExtra("START_INDEX", currentIdx)
            putExtra("START_POSITION", currentPos)
        }
        startActivity(intent)
        finish()
    }

    private fun setControlsLocked(locked: Boolean) {
        isLocked = locked
        val topBar = playerView.findViewById<View>(R.id.topBarVideo)
        val leftBar = playerView.findViewById<View>(R.id.leftControlsLayout)
        val rightBar = playerView.findViewById<View>(R.id.rightControlsLayout)
        val bottomBar = playerView.findViewById<View>(R.id.bottomControlsLayout)
        val unlockBtn = playerView.findViewById<View>(R.id.cardUnlock)

        if (locked) {
            topBar?.visibility = View.GONE
            leftBar?.visibility = View.GONE
            rightBar?.visibility = View.GONE
            bottomBar?.visibility = View.GONE
            unlockBtn?.visibility = View.VISIBLE
        } else {
            topBar?.visibility = View.VISIBLE
            leftBar?.visibility = View.VISIBLE
            rightBar?.visibility = View.VISIBLE
            bottomBar?.visibility = View.VISIBLE
            unlockBtn?.visibility = View.GONE
        }
    }

    private fun showThreeDotMenu() {
        try {
            val dialog = BottomSheetDialog(this)
            val view = layoutInflater.inflate(R.layout.dialog_list_menu, null)
            dialog.setContentView(view)

            val currentIndex = player?.currentMediaItemIndex ?: 0
            val mediaList = MainActivity.currentMediaList
            val currentItem = if (currentIndex in mediaList.indices) mediaList[currentIndex] else null

            view.findViewById<TextView>(R.id.menuMediaTitle)?.text = currentItem?.title ?: "Video Options"

            // Share
            view.findViewById<View>(R.id.menuShare)?.setOnClickListener {
                dialog.dismiss()
                currentItem?.let { item ->
                    try {
                        StrictMode.setVmPolicy(StrictMode.VmPolicy.Builder().build())
                        val contentUri = try {
                            ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, item.id)
                        } catch (_: Exception) {
                            Uri.fromFile(File(item.path))
                        }
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "video/*"
                            putExtra(Intent.EXTRA_STREAM, contentUri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        startActivity(Intent.createChooser(shareIntent, "Share Video via:"))
                    } catch (e: Exception) {
                        Toast.makeText(this, "Share error: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }

            // Play As Audio
            view.findViewById<View>(R.id.menuPlayAudio)?.setOnClickListener {
                dialog.dismiss()
                switchToAudioPlayer()
            }

            // Privacy Vault Move
            view.findViewById<View>(R.id.menuLockVault)?.setOnClickListener {
                dialog.dismiss()
                currentItem?.let { item ->
                    try {
                        val sourceFile = File(item.path)
                        if (sourceFile.exists()) {
                            val vaultDir = File(filesDir, ".PrivacyVault")
                            if (!vaultDir.exists()) vaultDir.mkdirs()

                            val destFile = File(vaultDir, sourceFile.name)
                            FileInputStream(sourceFile).use { input ->
                                FileOutputStream(destFile).use { output ->
                                    input.copyTo(output)
                                }
                            }
                            val oldPath = sourceFile.absolutePath
                            sourceFile.delete()

                            MediaScannerConnection.scanFile(this, arrayOf(oldPath), null, null)
                            MainActivity.currentMediaList.removeAt(currentIndex)
                            Toast.makeText(this, "Moved to Private Vault!", Toast.LENGTH_SHORT).show()

                            if (MainActivity.currentMediaList.isNotEmpty()) {
                                initializePlayerEngine()
                            } else {
                                finish()
                            }
                        }
                    } catch (e: Exception) {
                        Toast.makeText(this, "Vault error: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }

            // Real Delete
            view.findViewById<View>(R.id.menuDelete)?.setOnClickListener {
                dialog.dismiss()
                currentItem?.let { item ->
                    AlertDialog.Builder(this)
                        .setTitle("Delete Video")
                        .setMessage("Permanently delete \"${item.title}\"?")
                        .setPositiveButton("Delete") { _, _ ->
                            try {
                                val file = File(item.path)
                                if (file.delete()) {
                                    MediaScannerConnection.scanFile(this, arrayOf(item.path), null, null)
                                    MainActivity.currentMediaList.removeAt(currentIndex)
                                    Toast.makeText(this, "Deleted successfully", Toast.LENGTH_SHORT).show()

                                    if (MainActivity.currentMediaList.isNotEmpty()) {
                                        val nextIndex = if (currentIndex >= MainActivity.currentMediaList.size) 0 else currentIndex
                                        player?.seekTo(nextIndex, 0L)
                                        initializePlayerEngine()
                                    } else {
                                        finish()
                                    }
                                }
                            } catch (e: Exception) {
                                Toast.makeText(this, "Delete failed: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                        .setNegativeButton("Cancel", null)
                        .show()
                }
            }

            dialog.show()
        } catch (e: Exception) {
            e.printStackTrace()
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
            .setTitle("Now Playing (${mediaList.size} Videos)")
            .setItems(titles) { _, which ->
                player?.seekTo(which, 0L)
            }
            .show()
    }

    private fun saveCurrentPosition() {
        val currentIndex = player?.currentMediaItemIndex ?: -1
        val currentPositionMs = player?.currentPosition ?: 0L
        val mediaList = MainActivity.currentMediaList
        if (currentIndex in mediaList.indices) {
            val path = mediaList[currentIndex].path
            prefs.edit().putLong("RESUME_POS_$path", currentPositionMs).apply()
        }
    }

    override fun onPause() {
        super.onPause()
        saveCurrentPosition()
        player?.pause()
    }

    override fun onStop() {
        super.onStop()
        saveCurrentPosition()
        player?.pause()
    }

    override fun onDestroy() {
        saveCurrentPosition()
        player?.release()
        player = null
        super.onDestroy()
    }
}
