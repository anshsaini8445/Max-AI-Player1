package com.app.mediaplayer

import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Intent
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem as ExoMediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.android.material.bottomsheet.BottomSheetDialog
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.math.abs

class AudioPlayerActivity : AppCompatActivity() {

    private var player: Player? = null
    private var mediaController: MediaController? = null
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
    private var seekPosition: Long = 0
    private var totalDuration: Long = 0

    private val progressUpdater = object : Runnable {
        override fun run() {
            if (!isSeeking) {
                player?.let { p ->
                    val pos = p.currentPosition
                    tvCurrent?.text = formatTime(pos)
                    seekBar?.progress = pos.toInt()
                }
            }
            mainHandler.postDelayed(this, 1000)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
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

            setupClickListeners()
            setupGestures()
        } catch (e: Exception) {
            e.printStackTrace()
            finish()
        }
    }

    override fun onStart() {
        super.onStart()
        try {
            val sessionToken = SessionToken(this, ComponentName(this, PlaybackService::class.java))
            val future = MediaController.Builder(this, sessionToken).buildAsync()
            future.addListener({
                mediaController = future.get()
                player = mediaController
                setupMedia()
            }, ContextCompat.getMainExecutor(this))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun setupMedia() {
        try {
            val mediaList = MainActivity.currentMediaList
            val startIndex = intent.getIntExtra("START_INDEX", 0)

            if (mediaList.isNotEmpty()) {
                if (player?.mediaItemCount != mediaList.size) {
                    val exoItems = mediaList.map {
                        ExoMediaItem.Builder()
                            .setUri(it.path)
                            .setMediaMetadata(MediaMetadata.Builder().setTitle(it.title).build())
                            .build()
                    }
                    player?.setMediaItems(exoItems, startIndex, 0L)
                    player?.prepare()
                    player?.play()
                } else if (player?.currentMediaItemIndex != startIndex) {
                    player?.seekTo(startIndex, 0L)
                    player?.play()
                }
                loadAlbumArtAsync(mediaList[startIndex].path)
            }

            player?.addListener(object : Player.Listener {
                override fun onMediaItemTransition(mediaItem: ExoMediaItem?, reason: Int) {
                    val title = mediaItem?.mediaMetadata?.title?.toString() ?: "Song"
                    tvTitle?.text = title
                    val idx = player?.currentMediaItemIndex ?: 0
                    if (idx in mediaList.indices) {
                        loadAlbumArtAsync(mediaList[idx].path)
                    }
                    player?.let { p ->
                        totalDuration = p.duration
                        if (totalDuration > 0) {
                            seekBar?.max = totalDuration.toInt()
                            tvTotal?.text = formatTime(totalDuration)
                        }
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

            findViewById<CardView>(R.id.btnAudioPlayPause)?.setOnClickListener {
                if (player?.isPlaying == true) player?.pause() else player?.play()
            }

            findViewById<ImageButton>(R.id.btnAudioPrev)?.setOnClickListener { player?.seekToPreviousMediaItem() }
            findViewById<ImageButton>(R.id.btnAudioNext)?.setOnClickListener { player?.seekToNextMediaItem() }

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

    private fun setupClickListeners() {
        findViewById<ImageButton>(R.id.btnBackAudio)?.setOnClickListener { finish() }

        findViewById<ImageButton>(R.id.btnAudioShare)?.setOnClickListener {
            val idx = player?.currentMediaItemIndex ?: 0
            val mediaList = MainActivity.currentMediaList
            if (idx in mediaList.indices) {
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "audio/*"
                    putExtra(Intent.EXTRA_STREAM, android.net.Uri.parse("file://${mediaList[idx].path}"))
                }
                startActivity(Intent.createChooser(shareIntent, "Share Audio via"))
            }
        }

        findViewById<ImageButton>(R.id.btnFavorite)?.setOnClickListener {
            Toast.makeText(this, "Added to Favorites", Toast.LENGTH_SHORT).show()
        }

        findViewById<ImageButton>(R.id.btnEqAudio)?.setOnClickListener {
            showEqualizerDialog()
        }

        findViewById<ImageButton>(R.id.btnSleepTimer)?.setOnClickListener {
            showSleepTimerDialog()
        }

        findViewById<ImageButton>(R.id.btnMoreAudio)?.setOnClickListener {
            showMoreOptionsDialog()
        }

        findViewById<ImageButton>(R.id.btnQueueList)?.setOnClickListener {
            showQueueBottomSheet()
        }

        findViewById<ImageButton>(R.id.btnShuffle)?.setOnClickListener {
            val shuffleOn = !(player?.shuffleModeEnabled ?: false)
            player?.shuffleModeEnabled = shuffleOn
            Toast.makeText(this, if (shuffleOn) "Shuffle ON" else "Shuffle OFF", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showEqualizerDialog() {
        val presets = arrayOf("Normal", "Classical", "Dance", "Flat", "Bass Boost")
        AlertDialog.Builder(this)
            .setTitle("Equalizer Presets")
            .setItems(presets) { _, which ->
                Toast.makeText(this, "${presets[which]} Applied", Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    private fun showSleepTimerDialog() {
        val timers = arrayOf("15 minutes", "30 minutes", "45 minutes", "60 minutes", "Turn off timer")
        AlertDialog.Builder(this)
            .setTitle("Sleep Timer")
            .setItems(timers) { _, which ->
                Toast.makeText(this, "Timer: ${timers[which]}", Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    private fun showMoreOptionsDialog() {
        val options = arrayOf("Set as ringtone", "Add to playlist", "Playback Speed", "File info")
        AlertDialog.Builder(this)
            .setItems(options) { _, which ->
                when (which) {
                    2 -> {
                        val speeds = arrayOf("0.75x", "1.0x (Normal)", "1.25x", "1.5x")
                        val speedVals = floatArrayOf(0.75f, 1.0f, 1.25f, 1.5f)
                        AlertDialog.Builder(this).setItems(speeds) { _, sIdx ->
                            player?.playbackParameters = PlaybackParameters(speedVals[sIdx])
                        }.show()
                    }
                    else -> Toast.makeText(this, options[which], Toast.LENGTH_SHORT).show()
                }
            }
            .show()
    }

    private fun showQueueBottomSheet() {
        val dialog = BottomSheetDialog(this)
        val mediaList = MainActivity.currentMediaList
        val titles = mediaList.map { it.title }.toTypedArray()

        dialog.setContentView(layoutInflater.inflate(R.layout.dialog_list_menu, null))
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

    @SuppressLint("ClickableViewAccessibility")
    private fun setupGestures() {
        val detector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onScroll(e1: MotionEvent?, e2: MotionEvent, dX: Float, dY: Float): Boolean {
                if (e1 == null || totalDuration <= 0) return false
                if (abs(dX) > abs(dY)) {
                    isSeeking = true
                    val change = (dX * -80).toLong()
                    seekPosition = (player?.currentPosition ?: 0) + change
                    if (seekPosition < 0) seekPosition = 0
                    if (seekPosition > totalDuration) seekPosition = totalDuration
                    tvCurrent?.text = formatTime(seekPosition)
                    seekBar?.progress = seekPosition.toInt()
                    return true
                }
                return false
            }
        })

        cardAlbumArt?.setOnTouchListener { _, event ->
            detector.onTouchEvent(event)
            if (event.action == MotionEvent.ACTION_UP && isSeeking) {
                player?.seekTo(seekPosition)
                isSeeking = false
            }
            true
        }
    }

    private fun formatTime(ms: Long): String {
        if (ms < 0) return "00:00"
        val totalSec = ms / 1000
        val m = totalSec / 60
        val s = totalSec % 60
        return String.format(Locale.getDefault(), "%02d:%02d", m, s)
    }

    override fun onStop() {
        super.onStop()
        mainHandler.removeCallbacks(progressUpdater)
        mediaController?.release()
        mediaController = null
    }

    override fun onDestroy() {
        rotationAnimator?.cancel()
        bgExecutor.shutdown()
        super.onDestroy()
    }
}
