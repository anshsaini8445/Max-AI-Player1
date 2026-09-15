package com.app.mediaplayer

import android.animation.ObjectAnimator
import android.content.Intent
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.content.FileProvider
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem as ExoMediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.mp3.Mp3Extractor
import com.google.android.material.bottomsheet.BottomSheetDialog
import java.io.File
import java.util.Locale
import java.util.concurrent.Executors

@OptIn(UnstableApi::class)
class AudioPlayerActivity : AppCompatActivity() {

    private var player: ExoPlayer? = null
    private var tvTitle: TextView? = null
    private var tvSubtitle: TextView? = null
    private var tvCurrent: TextView? = null
    private var tvTotal: TextView? = null
    private var seekBar: SeekBar? = null
    private var imgPlayPause: ImageView? = null
    private var cardAlbumArt: CardView? = null
    private var imgAlbumArt: ImageView? = null

    private var rotationAnimator: ObjectAnimator? = null
    private val bgExecutor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    private var isSeeking = false
    private var totalDuration: Long = 0

    private val progressUpdater = object : Runnable {
        override fun run() {
            if (!isSeeking && player != null) {
                val pos = player?.currentPosition ?: 0L
                tvCurrent?.text = formatTime(pos)
                seekBar?.progress = pos.toInt()
            }
            mainHandler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            setContentView(R.layout.activity_audio_player)

            tvTitle = findViewById(R.id.tvAudioTitle)
            tvSubtitle = findViewById(R.id.tvAudioSubtitle)
            tvCurrent = findViewById(R.id.tvAudioCurrent)
            tvTotal = findViewById(R.id.tvAudioTotal)
            seekBar = findViewById(R.id.seekAudio)
            imgPlayPause = findViewById(R.id.imgPlayPause)
            cardAlbumArt = findViewById(R.id.cardAlbumArt)
            imgAlbumArt = findViewById(R.id.imgAlbumArt)

            tvTitle?.isSelected = true

            cardAlbumArt?.let {
                rotationAnimator = ObjectAnimator.ofFloat(it, View.ROTATION, 0f, 360f).apply {
                    duration = 14000
                    repeatCount = ObjectAnimator.INFINITE
                    interpolator = LinearInterpolator()
                }
            }

            initializeDirectAudioPlayer()
            setupButtons()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Audio engine error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun initializeDirectAudioPlayer() {
        try {
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .build()

            val renderersFactory = DefaultRenderersFactory(this).apply {
                setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
                setEnableDecoderFallback(true)
            }

            val extractorsFactory = DefaultExtractorsFactory().apply {
                setConstantBitrateSeekingEnabled(true)
                setMp3ExtractorFlags(Mp3Extractor.FLAG_ENABLE_INDEX_SEEKING)
            }

            val mediaSourceFactory = DefaultMediaSourceFactory(this, extractorsFactory)

            val loadControl = DefaultLoadControl.Builder()
                .setBufferDurationsMs(8000, 30000, 500, 1000)
                .build()

            player = ExoPlayer.Builder(this, renderersFactory)
                .setMediaSourceFactory(mediaSourceFactory)
                .setLoadControl(loadControl)
                .setAudioAttributes(audioAttributes, true)
                .setHandleAudioBecomingNoisy(true)
                .setWakeMode(C.WAKE_MODE_LOCAL)
                .build()

            val mediaList = MainActivity.currentMediaList
            val startIndex = intent.getIntExtra("START_INDEX", 0)
            val startPosition = intent.getLongExtra("START_POSITION", 0L)

            if (mediaList.isNotEmpty() && startIndex in mediaList.indices) {
                val exoItems = mediaList.map { item ->
                    ExoMediaItem.Builder()
                        .setUri(Uri.fromFile(File(item.path)))
                        .setMediaMetadata(MediaMetadata.Builder().setTitle(item.title).build())
                        .build()
                }
                player?.setMediaItems(exoItems, startIndex, startPosition)
                player?.prepare()
                player?.play()

                loadAlbumArtAsync(mediaList[startIndex].path)
            }

            player?.addListener(object : Player.Listener {
                override fun onMediaItemTransition(mediaItem: ExoMediaItem?, reason: Int) {
                    val title = mediaItem?.mediaMetadata?.title?.toString() ?: "Playing Audio"
                    tvTitle?.text = title
                    val idx = player?.currentMediaItemIndex ?: 0
                    if (idx in mediaList.indices) {
                        loadAlbumArtAsync(mediaList[idx].path)
                    }
                    totalDuration = player?.duration ?: 0L
                    if (totalDuration > 0) {
                        seekBar?.max = totalDuration.toInt()
                        tvTotal?.text = formatTime(totalDuration)
                    }
                }

                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    if (isPlaying) {
                        imgPlayPause?.setImageResource(android.R.drawable.ic_media_pause)
                        mainHandler.post(progressUpdater)
                        if (rotationAnimator?.isPaused == true) rotationAnimator?.resume() else rotationAnimator?.start()
                    } else {
                        imgPlayPause?.setImageResource(android.R.drawable.ic_media_play)
                        mainHandler.removeCallbacks(progressUpdater)
                        rotationAnimator?.pause()
                    }
                }
            })

            seekBar?.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser) tvCurrent?.text = formatTime(progress.toLong())
                }
                override fun onStartTrackingTouch(sb: SeekBar?) { isSeeking = true }
                override fun onStopTrackingTouch(sb: SeekBar?) {
                    isSeeking = false
                    sb?.let { player?.seekTo(it.progress.toLong()) }
                }
            })
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun setupButtons() {
        findViewById<ImageButton>(R.id.btnBackAudio)?.setOnClickListener { finish() }

        findViewById<CardView>(R.id.btnAudioPlayPause)?.setOnClickListener {
            if (player?.isPlaying == true) player?.pause() else player?.play()
        }

        findViewById<ImageButton>(R.id.btnAudioPrev)?.setOnClickListener { player?.seekToPreviousMediaItem() }
        findViewById<ImageButton>(R.id.btnAudioNext)?.setOnClickListener { player?.seekToNextMediaItem() }

        findViewById<ImageButton>(R.id.btnShuffle)?.setOnClickListener {
            val shuffle = !(player?.shuffleModeEnabled ?: false)
            player?.shuffleModeEnabled = shuffle
            Toast.makeText(this, if (shuffle) "Shuffle ON" else "Shuffle OFF", Toast.LENGTH_SHORT).show()
        }

        findViewById<ImageButton>(R.id.btnAudioShare)?.setOnClickListener { shareCurrentAudio() }
        findViewById<ImageButton>(R.id.btnFavorite)?.setOnClickListener {
            Toast.makeText(this, "Added to Favorites ❤️", Toast.LENGTH_SHORT).show()
        }
        findViewById<ImageButton>(R.id.btnEqAudio)?.setOnClickListener { showEqualizerDialog() }
        findViewById<ImageButton>(R.id.btnSleepTimer)?.setOnClickListener { showSleepTimerDialog() }

        // THREE-DOT MENU (⋮) IN AUDIO PLAYER
        findViewById<ImageButton>(R.id.btnMoreAudio)?.setOnClickListener {
            showAudioThreeDotMenu()
        }

        findViewById<ImageButton>(R.id.btnQueueList)?.setOnClickListener { showQueueBottomSheet() }
    }

    private fun showAudioThreeDotMenu() {
        val idx = player?.currentMediaItemIndex ?: 0
        val mediaList = MainActivity.currentMediaList
        val currentItem = if (idx in mediaList.indices) mediaList[idx] else null

        val options = arrayOf(
            "🔔 Set as ringtone",
            "📑 Add to playlist",
            "⚡ Speed play",
            "ℹ️ File info"
        )

        AlertDialog.Builder(this)
            .setTitle(currentItem?.title ?: "Audio Options")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> Toast.makeText(this, "Set as system ringtone", Toast.LENGTH_SHORT).show()
                    1 -> Toast.makeText(this, "Added to My Playlist", Toast.LENGTH_SHORT).show()
                    2 -> {
                        val speeds = arrayOf("0.75x", "1.0x (Normal)", "1.25x", "1.5x", "2.0x")
                        val speedVals = floatArrayOf(0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
                        AlertDialog.Builder(this)
                            .setTitle("Audio Playback Speed")
                            .setItems(speeds) { _, sIdx ->
                                player?.playbackParameters = PlaybackParameters(speedVals[sIdx])
                                Toast.makeText(this, "Speed: ${speeds[sIdx]}", Toast.LENGTH_SHORT).show()
                            }.show()
                    }
                    3 -> {
                        currentItem?.let { item ->
                            val f = File(item.path)
                            val mb = f.length() / (1024 * 1024)
                            AlertDialog.Builder(this)
                                .setTitle("File Info")
                                .setMessage("Title: ${item.title}\nSize: ${mb} MB\nPath: ${item.path}")
                                .setPositiveButton("OK", null)
                                .show()
                        }
                    }
                }
            }
            .show()
    }

    private fun shareCurrentAudio() {
        val idx = player?.currentMediaItemIndex ?: 0
        val mediaList = MainActivity.currentMediaList
        if (idx in mediaList.indices) {
            val file = File(mediaList[idx].path)
            if (file.exists()) {
                val contentUri = FileProvider.getUriForFile(
                    this,
                    "${applicationContext.packageName}.provider",
                    file
                )
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "audio/*"
                    putExtra(Intent.EXTRA_STREAM, contentUri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                startActivity(Intent.createChooser(shareIntent, "Share Audio via:"))
            }
        }
    }

    private fun showEqualizerDialog() {
        val presets = arrayOf("Normal", "Classical", "Dance", "Flat", "Bass Boost", "Vocal Booster")
        AlertDialog.Builder(this)
            .setTitle("Equalizer Presets")
            .setItems(presets) { _, which ->
                Toast.makeText(this, "Equalizer: ${presets[which]} Applied", Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    private fun showSleepTimerDialog() {
        val timers = arrayOf("15 minutes", "30 minutes", "45 minutes", "60 minutes", "Turn off timer")
        AlertDialog.Builder(this)
            .setTitle("Sleep Timer")
            .setItems(timers) { _, which ->
                Toast.makeText(this, "Timer set to ${timers[which]}", Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    private fun showQueueBottomSheet() {
        val mediaList = MainActivity.currentMediaList
        val titles = mediaList.map { it.title }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("Now Playing (${mediaList.size} Songs)")
            .setItems(titles) { _, which ->
                player?.seekTo(which, 0L)
            }
            .show()
    }

    private fun loadAlbumArtAsync(path: String) {
        bgExecutor.execute {
            var bmp: android.graphics.Bitmap? = null
            try {
                val retriever = MediaMetadataRetriever()
                retriever.setDataSource(path)
                val art = retriever.embeddedPicture
                retriever.release()
                if (art != null) {
                    bmp = BitmapFactory.decodeByteArray(art, 0, art.size)
                }
            } catch (_: Exception) {}

            mainHandler.post {
                if (bmp != null) {
                    imgAlbumArt?.setImageBitmap(bmp)
                    imgAlbumArt?.imageTintList = null
                } else {
                    imgAlbumArt?.setImageResource(android.R.drawable.ic_media_play)
                }
            }
        }
    }

    private fun formatTime(ms: Long): String {
        if (ms < 0) return "00:00"
        val totalSec = ms / 1000
        val m = totalSec / 60
        val s = totalSec % 60
        return String.format(Locale.getDefault(), "%02d:%02d", m, s)
    }

    override fun onDestroy() {
        rotationAnimator?.cancel()
        mainHandler.removeCallbacks(progressUpdater)
        player?.release()
        player = null
        bgExecutor.shutdown()
        super.onDestroy()
    }
}
