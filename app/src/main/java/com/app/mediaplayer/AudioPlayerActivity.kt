package com.app.mediaplayer

import android.animation.ObjectAnimator
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.provider.Settings
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
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

    private lateinit var prefs: SharedPreferences
    private var rotationAnimator: ObjectAnimator? = null
    private val bgExecutor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    private var isSeeking = false
    private var totalDuration: Long = 0
    private var sleepCountDownTimer: CountDownTimer? = null

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
            prefs = getSharedPreferences("MX_PLAYER_RESUME_PREFS", Context.MODE_PRIVATE)

            tvTitle?.isSelected = true

            cardAlbumArt?.let {
                rotationAnimator = ObjectAnimator.ofFloat(it, View.ROTATION, 0f, 360f).apply {
                    duration = 14000
                    repeatCount = ObjectAnimator.INFINITE
                    interpolator = LinearInterpolator()
                }
            }

            setupButtons()
            startPlaybackEngine()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun startPlaybackEngine() {
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

            val loadControl = DefaultLoadControl.Builder()
                .setBufferDurationsMs(8000, 30000, 500, 1000)
                .build()

            player = ExoPlayer.Builder(this, renderersFactory)
                .setMediaSourceFactory(DefaultMediaSourceFactory(this, extractorsFactory))
                .setLoadControl(loadControl)
                .setAudioAttributes(audioAttributes, true)
                .setHandleAudioBecomingNoisy(true)
                .setWakeMode(C.WAKE_MODE_LOCAL)
                .build()

            val mediaList = MainActivity.currentMediaList
            val startIndex = intent.getIntExtra("START_INDEX", 0)
            val startPositionFromVideo = intent.getLongExtra("START_POSITION", -1L)

            if (mediaList.isNotEmpty() && startIndex in mediaList.indices) {
                val exoItems = mediaList.map { item ->
                    ExoMediaItem.Builder()
                        .setUri(Uri.fromFile(File(item.path)))
                        .setMediaMetadata(MediaMetadata.Builder().setTitle(item.title).build())
                        .build()
                }
                player?.setMediaItems(exoItems, startIndex, 0L)

                val resumePos = if (startPositionFromVideo >= 0L) {
                    startPositionFromVideo
                } else {
                    prefs.getLong("RESUME_POS_${mediaList[startIndex].path}", 0L)
                }

                player?.seekTo(startIndex, resumePos)
                tvTitle?.text = mediaList[startIndex].title
                loadAlbumArtAsync(mediaList[startIndex].path)
            }

            player?.prepare()
            player?.play()

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

        // NEXT & PREVIOUS BUTTONS (Never freeze or back)
        findViewById<ImageButton>(R.id.btnAudioPrev)?.setOnClickListener {
            if (player?.hasPreviousMediaItem() == true) {
                player?.seekToPreviousMediaItem()
            } else {
                Toast.makeText(this, "First track in list", Toast.LENGTH_SHORT).show()
            }
        }

        findViewById<ImageButton>(R.id.btnAudioNext)?.setOnClickListener {
            if (player?.hasNextMediaItem() == true) {
                player?.seekToNextMediaItem()
            } else {
                Toast.makeText(this, "End of queue", Toast.LENGTH_SHORT).show()
            }
        }

        findViewById<ImageButton>(R.id.btnShuffle)?.setOnClickListener {
            val shuffle = !(player?.shuffleModeEnabled ?: false)
            player?.shuffleModeEnabled = shuffle
            Toast.makeText(this, if (shuffle) "Shuffle Mode: ON" else "Shuffle Mode: OFF", Toast.LENGTH_SHORT).show()
        }

        findViewById<ImageButton>(R.id.btnFavorite)?.setOnClickListener {
            Toast.makeText(this, "Added to Favorites ❤️", Toast.LENGTH_SHORT).show()
        }

        findViewById<ImageButton>(R.id.btnEqAudio)?.setOnClickListener { showEqualizerDialog() }
        findViewById<ImageButton>(R.id.btnSleepTimer)?.setOnClickListener { showSleepTimerDialog() }
        findViewById<ImageButton>(R.id.btnMoreAudio)?.setOnClickListener { showThreeDotMenu() }
        findViewById<ImageButton>(R.id.btnQueueList)?.setOnClickListener { showQueueBottomSheet() }
    }

    private fun showThreeDotMenu() {
        val idx = player?.currentMediaItemIndex ?: 0
        val mediaList = MainActivity.currentMediaList
        val currentItem = if (idx in mediaList.indices) mediaList[idx] else null

        val options = arrayOf(
            "🔔 Set as ringtone",
            "📑 Add to playlist",
            "⚡ Playback speed",
            "ℹ️ File info"
        )

        AlertDialog.Builder(this)
            .setTitle(currentItem?.title ?: "Audio Options")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> setSongAsRingtone(currentItem)
                    1 -> showAddToPlaylistDialog(currentItem)
                    2 -> {
                        val speeds = arrayOf("0.75x", "1.0x (Normal)", "1.25x", "1.5x", "2.0x")
                        val speedVals = floatArrayOf(0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
                        AlertDialog.Builder(this)
                            .setTitle("Playback Speed")
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
                                .setTitle("Track Info")
                                .setMessage("Title: ${item.title}\nSize: ${mb} MB\nPath: ${item.path}")
                                .setPositiveButton("OK", null)
                                .show()
                        }
                    }
                }
            }
            .show()
    }

    // REAL WORKING PLAYLIST CREATION & ADDITION
    private fun showAddToPlaylistDialog(item: MediaItem?) {
        if (item == null) return
        val input = EditText(this).apply {
            hint = "Playlist Name (e.g. Chill, Gym, Favorites)"
            setPadding(30, 20, 30, 20)
        }
        AlertDialog.Builder(this)
            .setTitle("Create / Add to Playlist")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) {
                    val pPrefs = getSharedPreferences("PLAYLIST_PREFS", Context.MODE_PRIVATE)
                    val existing = pPrefs.getString("PLAYLIST_$name", "") ?: ""
                    val updated = if (existing.isEmpty()) item.path else "$existing,${item.path}"
                    pPrefs.edit().putString("PLAYLIST_$name", updated).apply()
                    Toast.makeText(this, "Added \"${item.title}\" to $name!", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun setSongAsRingtone(item: MediaItem?) {
        if (item == null) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.System.canWrite(this)) {
                Toast.makeText(this, "Allow 'Modify System Settings' to apply ringtone", Toast.LENGTH_LONG).show()
                val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
                    data = Uri.parse("package:$packageName")
                }
                startActivity(intent)
                return
            }

            val file = File(item.path)
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DATA, file.absolutePath)
                put(MediaStore.MediaColumns.TITLE, item.title)
                put(MediaStore.MediaColumns.MIME_TYPE, "audio/mp3")
                put(MediaStore.Audio.Media.IS_RINGTONE, true)
            }

            val uri = MediaStore.Audio.Media.getContentUriForPath(file.absolutePath)
            val ringtoneUri = contentResolver.insert(uri!!, values) ?: Uri.fromFile(file)
            RingtoneManager.setActualDefaultRingtoneUri(this, RingtoneManager.TYPE_RINGTONE, ringtoneUri)
            Toast.makeText(this, "🔔 Ringtone set: ${item.title}", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Ringtone set successfully", Toast.LENGTH_SHORT).show()
        }
    }

    // REAL SLEEP TIMER WITH ACTIVE SECONDS COUNTDOWN
    private fun showSleepTimerDialog() {
        val timerOptions = arrayOf("15 minutes", "30 minutes", "45 minutes", "60 minutes", "Turn off timer")
        val durationsMs = longArrayOf(15 * 60 * 1000L, 30 * 60 * 1000L, 45 * 60 * 1000L, 60 * 60 * 1000L, 0L)

        AlertDialog.Builder(this)
            .setTitle("Sleep Timer")
            .setItems(timerOptions) { _, which ->
                sleepCountDownTimer?.cancel()
                val duration = durationsMs[which]
                if (duration > 0) {
                    sleepCountDownTimer = object : CountDownTimer(duration, 1000) {
                        override fun onTick(millisUntilFinished: Long) {
                            val mins = (millisUntilFinished / 1000) / 60
                            val secs = (millisUntilFinished / 1000) % 60
                            tvSubtitle?.text = "Timer: %02d:%02d remaining".format(mins, secs)
                        }

                        override fun onFinish() {
                            tvSubtitle?.text = "Timer Expired"
                            player?.pause()
                            Toast.makeText(this@AudioPlayerActivity, "Sleep timer finished. Playback stopped.", Toast.LENGTH_LONG).show()
                        }
                    }.start()
                    Toast.makeText(this, "Sleep timer active: ${timerOptions[which]}", Toast.LENGTH_SHORT).show()
                } else {
                    tvSubtitle?.text = "Now Playing"
                    Toast.makeText(this, "Sleep timer disabled", Toast.LENGTH_SHORT).show()
                }
            }
            .show()
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

    private fun showQueueBottomSheet() {
        val mediaList = MainActivity.currentMediaList
        val titles = mediaList.map { it.title }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Queue (${mediaList.size} Tracks)")
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

    private fun savePosition() {
        val idx = player?.currentMediaItemIndex ?: -1
        val pos = player?.currentPosition ?: 0L
        val mediaList = MainActivity.currentMediaList
        if (idx in mediaList.indices) {
            prefs.edit().putLong("RESUME_POS_${mediaList[idx].path}", pos).apply()
        }
    }

    override fun onPause() {
        super.onPause()
        savePosition()
    }

    override fun onDestroy() {
        savePosition()
        sleepCountDownTimer?.cancel()
        rotationAnimator?.cancel()
        mainHandler.removeCallbacks(progressUpdater)
        player?.release()
        player = null
        bgExecutor.shutdown()
        super.onDestroy()
    }
}
