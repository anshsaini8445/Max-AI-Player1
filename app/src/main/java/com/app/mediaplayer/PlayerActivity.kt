package com.app.mediaplayer

import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import androidx.media3.common.MediaItem as ExoMediaItem

class PlayerActivity : AppCompatActivity() {

    private var player: ExoPlayer? = null
    private lateinit var playerView: PlayerView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN)
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            
            setContentView(R.layout.activity_player)
            playerView = findViewById(R.id.playerView)

            initializePlayer()
            setupUIButtons()
        } catch (e: Exception) {
            e.printStackTrace()
            finish() 
        }
    }

    private fun setupUIButtons() {
        try {
            playerView.findViewById<View>(R.id.btnBack)?.setOnClickListener { finish() }
            playerView.findViewById<View>(R.id.btnMoreSettings)?.setOnClickListener { showPlayitStyleMenu() }
            
            playerView.findViewById<View>(R.id.btnAudioOnly)?.setOnClickListener {
                Toast.makeText(this, "Playing in Audio Mode", Toast.LENGTH_SHORT).show()
                startActivity(Intent(this, AudioPlayerActivity::class.java).apply { 
                    putExtra("START_INDEX", intent.getIntExtra("START_INDEX", 0)) 
                })
                finish()
            }

            playerView.findViewById<View>(R.id.btnMute)?.setOnClickListener {
                val currentVol = player?.volume ?: 1f
                player?.volume = if (currentVol > 0f) 0f else 1f
                Toast.makeText(this, if (currentVol > 0f) "Muted" else "Unmuted", Toast.LENGTH_SHORT).show()
            }
            
            playerView.findViewById<View>(R.id.btnLock)?.setOnClickListener { Toast.makeText(this, "Screen Locked", Toast.LENGTH_SHORT).show() }
            playerView.findViewById<View>(R.id.btnCut)?.setOnClickListener { Toast.makeText(this, "Video Cutter Opened", Toast.LENGTH_SHORT).show() }
            playerView.findViewById<View>(R.id.btnRotate)?.setOnClickListener { Toast.makeText(this, "Screen Rotated", Toast.LENGTH_SHORT).show() }

            playerView.findViewById<View>(R.id.btnSpeed)?.setOnClickListener { Toast.makeText(this, "Playback Speed Settings", Toast.LENGTH_SHORT).show() }
            playerView.findViewById<View>(R.id.btnResize)?.setOnClickListener { Toast.makeText(this, "Aspect Ratio Changed", Toast.LENGTH_SHORT).show() }
            playerView.findViewById<View>(R.id.btnPip)?.setOnClickListener { Toast.makeText(this, "Pop-up Window (PIP) Mode", Toast.LENGTH_SHORT).show() }
            
        } catch (e: Exception) { e.printStackTrace() }
    }

    private fun showPlayitStyleMenu() {
        try {
            val dialog = BottomSheetDialog(this)
            val view = layoutInflater.inflate(R.layout.dialog_list_menu, null)
            dialog.setContentView(view)
            dialog.show()
        } catch (e: Exception) { e.printStackTrace() }
    }

    private fun initializePlayer() {
        player = ExoPlayer.Builder(this).build()
        playerView.player = player

        val mediaList = MainActivity.currentMediaList
        val startIndex = intent.getIntExtra("START_INDEX", 0)

        if (mediaList.isNotEmpty()) {
            val exoItems = mediaList.map { 
                ExoMediaItem.Builder()
                    .setUri(it.path)
                    .setMediaMetadata(MediaMetadata.Builder().setTitle(it.title).build())
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
            }
        })
    }

    override fun onStop() {
        super.onStop()
        player?.pause()
    }

    override fun onDestroy() {
        super.onDestroy()
        player?.release()
    }
}
