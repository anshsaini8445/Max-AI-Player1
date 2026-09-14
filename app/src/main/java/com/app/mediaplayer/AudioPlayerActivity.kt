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
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.media3.common.MediaItem as ExoMediaItem
import java.util.Locale
import kotlin.math.abs

class AudioPlayerActivity : AppCompatActivity() {

    private var player: Player? = null
    private var mediaController: MediaController? = null
    private var tvTitle: TextView? = null
    private var tvCurrent: TextView? = null
    private var tvTotal: TextView? = null
    private var seekBar: SeekBar? = null
    private var cardPlayPause: CardView? = null
    private var imgPlayPauseIcon: ImageView? = null
    private var imgAlbumArt: ImageView? = null
    
    private lateinit var gestureDetector: GestureDetector
    private var isSeeking = false
    private var seekPosition: Long = 0
    private var totalDuration: Long = 0
    private var rotationAnimator: ObjectAnimator? = null

    private val handler = Handler(Looper.getMainLooper())
    private val updateProgressRunnable = object : Runnable {
        override fun run() {
            if (!isSeeking) {
                player?.let { p ->
                    tvCurrent?.text = formatTime(p.currentPosition)
                    seekBar?.progress = p.currentPosition.toInt()
                }
            }
            handler.postDelayed(this, 1000)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            setContentView(R.layout.activity_audio_player)

            tvTitle = findViewById(R.id.tvAudioTitle)
            tvCurrent = findViewById(R.id.tvAudioCurrent)
            tvTotal = findViewById(R.id.tvAudioTotal)
            seekBar = findViewById(R.id.seekAudio)
            cardPlayPause = findViewById(R.id.btnAudioPlayPause)
            imgAlbumArt = findViewById(R.id.imgAlbumArt)
            
            if (cardPlayPause != null && cardPlayPause!!.childCount > 0) {
                imgPlayPauseIcon = cardPlayPause!!.getChildAt(0) as? ImageView
            }

            findViewById<ImageButton>(R.id.btnBackAudio)?.setOnClickListener { finish() }
            
            imgAlbumArt?.let {
                rotationAnimator = ObjectAnimator.ofFloat(it, View.ROTATION, 0f, 360f).apply {
                    duration = 10000 
                    repeatCount = ObjectAnimator.INFINITE
                    interpolator = LinearInterpolator()
                }
            }

            setupSwipeGestures()
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
                setupPlayer()
            }, ContextCompat.getMainExecutor(this))
        } catch (e: Exception) { e.printStackTrace() }
    }

    private fun setupPlayer() {
        try {
            val mediaList = MainActivity.currentMediaList
            val startIndex = intent.getIntExtra("START_INDEX", 0)

            if (mediaList.isNotEmpty()) {
                if (player?.mediaItemCount != mediaList.size) {
                    val exoItems = mediaList.map { item ->
                        ExoMediaItem.Builder()
                            .setUri(item.path)
                            .setMediaMetadata(MediaMetadata.Builder().setTitle(item.title).build())
                            .build() 
                    }
                    player?.setMediaItems(exoItems, startIndex, 0L)
                    player?.prepare()
                    player?.play()
                } else if (player?.currentMediaItemIndex != startIndex) {
                    player?.seekTo(startIndex, 0L)
                    player?.play()
                }
                setAlbumArt(mediaList[startIndex].path)
            }

            player?.addListener(object : Player.Listener {
                override fun onMediaItemTransition(mediaItem: ExoMediaItem?, reason: Int) {
                    tvTitle?.text = mediaItem?.mediaMetadata?.title?.toString() ?: "Unknown Audio"
                    val currentIndex = player?.currentMediaItemIndex ?: 0
                    if (mediaList.isNotEmpty() && currentIndex >= 0 && currentIndex < mediaList.size) {
                        setAlbumArt(mediaList[currentIndex].path)
                    }
                    player?.let { p ->
                        totalDuration = p.duration
                        if(totalDuration > 0) {
                            seekBar?.max = totalDuration.toInt()
                            tvTotal?.text = formatTime(totalDuration)
                        }
                    }
                }
                
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    if (isPlaying) {
                        imgPlayPauseIcon?.setImageResource(android.R.drawable.ic_media_pause)
                        handler.post(updateProgressRunnable)
                        if (rotationAnimator?.isPaused == true) rotationAnimator?.resume() else rotationAnimator?.start()
                    } else {
                        imgPlayPauseIcon?.setImageResource(android.R.drawable.ic_media_play)
                        handler.removeCallbacks(updateProgressRunnable)
                        rotationAnimator?.pause()
                    }
                }
            })

            cardPlayPause?.setOnClickListener {
                if (player?.isPlaying == true) player?.pause() else player?.play()
            }

            findViewById<ImageButton>(R.id.btnAudioPrev)?.setOnClickListener { player?.seekToPreviousMediaItem() }
            findViewById<ImageButton>(R.id.btnAudioNext)?.setOnClickListener { player?.seekToNextMediaItem() }

            seekBar?.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser) tvCurrent?.text = formatTime(progress.toLong())
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) { isSeeking = true }
                override fun onStopTrackingTouch(seekBar: SeekBar?) {
                    isSeeking = false
                    seekBar?.let { player?.seekTo(it.progress.toLong()) }
                }
            })
        } catch (e: Exception) { e.printStackTrace() }
    }

    private fun setAlbumArt(path: String) {
        Thread {
            try {
                val retriever = MediaMetadataRetriever()
                retriever.setDataSource(path)
                val art = retriever.embeddedPicture
                retriever.release()
                
                runOnUiThread {
                    if (art != null) {
                        val bitmap = BitmapFactory.decodeByteArray(art, 0, art.size)
                        imgAlbumArt?.setImageBitmap(bitmap)
                    } else {
                        imgAlbumArt?.setImageResource(android.R.drawable.ic_media_play)
                    }
                }
            } catch (e: Exception) { e.printStackTrace() }
        }.start()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupSwipeGestures() {
        gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
                if (e1 == null || totalDuration <= 0) return false
                if (abs(distanceX) > abs(distanceY)) {
                    isSeeking = true
                    val change = (distanceX * -100).toLong() 
                    seekPosition = player?.currentPosition ?: 0
                    seekPosition += change
                    if (seekPosition < 0) seekPosition = 0
                    if (seekPosition > totalDuration) seekPosition = totalDuration
                    tvCurrent?.text = formatTime(seekPosition)
                    seekBar?.progress = seekPosition.toInt()
                    return true
                }
                return false
            }
        })
        findViewById<CardView>(R.id.cardAlbumArt)?.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
            if (event.action == MotionEvent.ACTION_UP && isSeeking) {
                player?.seekTo(seekPosition)
                isSeeking = false
            }
            true 
        }
    }

    private fun formatTime(ms: Long): String {
        if (ms < 0) return "00:00"
        val totalSecs = ms / 1000
        val mins = totalSecs / 60
        val secs = totalSecs % 60
        return String.format(Locale.getDefault(), "%02d:%02d", mins, secs)
    }

    override fun onStop() {
        super.onStop()
        handler.removeCallbacks(updateProgressRunnable)
        mediaController?.release()
    }
}
