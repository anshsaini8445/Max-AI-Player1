package com.app.mediaplayer

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.bottomsheet.BottomSheetDialog
import java.io.File

class MainActivity : AppCompatActivity() {

    private val videoList = mutableListOf<MediaItem>()
    private val audioList = mutableListOf<MediaItem>()
    private lateinit var recyclerView: RecyclerView
    private var bottomNav: BottomNavigationView? = null
    
    private var tabVideo: TextView? = null
    private var tabFolder: TextView? = null
    private var historyBlock: LinearLayout? = null
    private var tabLayout: LinearLayout? = null
    private var searchLayout: LinearLayout? = null
    private var etSearch: EditText? = null

    private var isShowingVideos = true 
    private var isFolderView = false 
    private var isSearching = false

    companion object {
        var currentMediaList: List<MediaItem> = emptyList()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        try {
            setContentView(R.layout.activity_main)

            recyclerView = findViewById(R.id.recyclerView)
            bottomNav = findViewById(R.id.bottomNav)
            tabVideo = findViewById(R.id.tabVideo)
            tabFolder = findViewById(R.id.tabFolder)
            historyBlock = findViewById(R.id.historyBlock)
            tabLayout = findViewById(R.id.tabLayout)
            searchLayout = findViewById(R.id.searchLayout)
            etSearch = findViewById(R.id.etSearch)

            recyclerView.layoutManager = LinearLayoutManager(this)

            setupTopTabs()
            setupSearch()

            bottomNav?.setOnItemSelectedListener { item ->
                try {
                    when (item.itemId) {
                        R.id.nav_video -> {
                            isSearching = false
                            searchLayout?.visibility = View.GONE
                            historyBlock?.visibility = View.VISIBLE
                            tabLayout?.visibility = View.VISIBLE
                            isShowingVideos = true
                            isFolderView = false
                            resetTabsToDefault()
                            updateList()
                            true
                        }
                        R.id.nav_music -> {
                            isSearching = false
                            searchLayout?.visibility = View.GONE
                            historyBlock?.visibility = View.VISIBLE
                            tabLayout?.visibility = View.VISIBLE
                            isShowingVideos = false
                            isFolderView = false
                            resetTabsToDefault()
                            updateList()
                            true
                        }
                        R.id.nav_search -> {
                            isSearching = true
                            searchLayout?.visibility = View.VISIBLE
                            historyBlock?.visibility = View.GONE
                            tabLayout?.visibility = View.GONE
                            filterList(etSearch?.text?.toString() ?: "")
                            true
                        }
                        R.id.nav_settings -> {
                            Toast.makeText(this, "Opening Me Settings...", Toast.LENGTH_SHORT).show()
                            true
                        }
                        else -> false
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    false
                }
            }

            checkAndRequestPermissions()

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun setupSearch() {
        etSearch?.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (isSearching) {
                    filterList(s?.toString() ?: "")
                }
            }
        })
    }

    private fun filterList(query: String) {
        try {
            val combinedList = mutableListOf<MediaItem>()
            combinedList.addAll(videoList)
            combinedList.addAll(audioList)
            
            val filtered = if (query.trim().isEmpty()) {
                combinedList
            } else {
                combinedList.filter { it.title.contains(query.trim(), ignoreCase = true) }
            }
            showItemsInFolder(filtered)
        } catch (e: Exception) { 
            e.printStackTrace() 
        }
    }

    private fun resetTabsToDefault() {
        try {
            val activeColor = android.graphics.Color.parseColor("#00E5FF")
            val inactiveColor = android.graphics.Color.parseColor("#AAAAAA")
            if (isShowingVideos) {
                tabVideo?.setTextColor(activeColor)
                tabFolder?.setTextColor(inactiveColor)
            } else {
                tabVideo?.setTextColor(inactiveColor)
                tabFolder?.setTextColor(activeColor)
            }
        } catch (e: Exception) { e.printStackTrace() }
    }

    private fun setupTopTabs() {
        val activeColor = android.graphics.Color.parseColor("#00E5FF")
        val inactiveColor = android.graphics.Color.parseColor("#AAAAAA")

        tabVideo?.setOnClickListener {
            if (isSearching) return@setOnClickListener
            isFolderView = false
            tabVideo?.setTextColor(activeColor)
            tabFolder?.setTextColor(inactiveColor)
            updateList()
        }

        tabFolder?.setOnClickListener {
            if (isSearching) return@setOnClickListener
            isFolderView = true
            tabFolder?.setTextColor(activeColor)
            tabVideo?.setTextColor(inactiveColor)
            updateList()
        }
    }

    private fun checkAndRequestPermissions() {
        val permissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.READ_MEDIA_VIDEO)
            permissions.add(Manifest.permission.READ_MEDIA_AUDIO)
        } else {
            permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        val missing = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missing.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missing.toTypedArray(), 101)
        } else {
            scanMedia()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (grantResults.isNotEmpty()) scanMedia()
    }

    private fun scanMedia() {
        try {
            videoList.clear()
            audioList.clear()

            val videoProjection = arrayOf(MediaStore.Video.Media._ID, MediaStore.Video.Media.TITLE, MediaStore.Video.Media.DATA, MediaStore.Video.Media.DURATION)
            contentResolver.query(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, videoProjection, null, null, null)?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.TITLE)
                val pathCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)
                val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)

                while (cursor.moveToNext()) {
                    videoList.add(MediaItem(cursor.getLong(idCol), cursor.getString(titleCol) ?: "Unknown", cursor.getString(pathCol), cursor.getLong(durationCol), true))
                }
            }

            val audioProjection = arrayOf(MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.DATA, MediaStore.Audio.Media.DURATION)
            contentResolver.query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, audioProjection, null, null, null)?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val pathCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
                val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)

                while (cursor.moveToNext()) {
                    audioList.add(MediaItem(cursor.getLong(idCol), cursor.getString(titleCol) ?: "Unknown", cursor.getString(pathCol), cursor.getLong(durationCol), false))
                }
            }
            bottomNav?.selectedItemId = R.id.nav_video
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun getFolders(items: List<MediaItem>): List<MediaFolder> {
        val grouped = items.groupBy { item ->
            try { File(item.path).parentFile?.name ?: "Unknown Folder" } catch (e: Exception) { "Unknown Folder" }
        }
        return grouped.map { MediaFolder(it.key, it.value) }.sortedBy { it.name }
    }

    private fun updateList() {
        if (isSearching) return
        try {
            val list = if (isShowingVideos) videoList else audioList
            if (isFolderView) {
                val folders = getFolders(list)
                recyclerView.layoutManager = LinearLayoutManager(this)
                recyclerView.adapter = FolderAdapter(folders) { clickedFolder ->
                    isFolderView = false
                    tabFolder?.setTextColor(android.graphics.Color.parseColor("#AAAAAA"))
                    tabVideo?.setTextColor(android.graphics.Color.parseColor("#00E5FF"))
                    showItemsInFolder(clickedFolder.mediaItems)
                }
            } else {
                showItemsInFolder(list)
            }
        } catch (e: Exception) { e.printStackTrace() }
    }

    private fun showItemsInFolder(itemsToShow: List<MediaItem>) {
        try {
            recyclerView.layoutManager = LinearLayoutManager(this)
            recyclerView.adapter = MediaAdapter(
                itemsToShow, 
                false,
                { item -> showMediaOptionsDialog(item) }, 
                { item -> 
                    currentMediaList = itemsToShow
                    val targetActivity = if (item.isVideo) PlayerActivity::class.java else AudioPlayerActivity::class.java
                    val intent = Intent(this, targetActivity).apply { putExtra("START_INDEX", itemsToShow.indexOf(item)) }
                    startActivity(intent)
                }
            )
        } catch (e: Exception) { e.printStackTrace() }
    }

    private fun showMediaOptionsDialog(item: MediaItem) {
        try {
            val dialog = BottomSheetDialog(this)
            val view = layoutInflater.inflate(R.layout.dialog_list_menu, null)
            dialog.setContentView(view)

            val titleId = resources.getIdentifier("menuMediaTitle", "id", packageName)
            if (titleId != 0) view.findViewById<TextView>(titleId)?.text = item.title

            val shareId = resources.getIdentifier("menuShare", "id", packageName)
            if (shareId != 0) {
                view.findViewById<View>(shareId)?.setOnClickListener {
                    dialog.dismiss()
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = if (item.isVideo) "video/*" else "audio/*"
                        putExtra(Intent.EXTRA_STREAM, android.net.Uri.parse("file://${item.path}"))
                    }
                    startActivity(Intent.createChooser(shareIntent, "Share Media Via:"))
                }
            }

            val playAudioId = resources.getIdentifier("menuPlayAudio", "id", packageName)
            if (playAudioId != 0) {
                view.findViewById<View>(playAudioId)?.setOnClickListener {
                    dialog.dismiss()
                    val intent = Intent(this, AudioPlayerActivity::class.java).apply { putExtra("START_INDEX", currentMediaList.indexOf(item)) }
                    startActivity(intent)
                }
            }

            val vaultId = resources.getIdentifier("menuLockVault", "id", packageName)
            if (vaultId != 0) {
                view.findViewById<View>(vaultId)?.setOnClickListener {
                    dialog.dismiss()
                    Toast.makeText(this, "Moved to Privacy Folder", Toast.LENGTH_SHORT).show()
                }
            }

            val deleteId = resources.getIdentifier("menuDelete", "id", packageName)
            if (deleteId != 0) {
                view.findViewById<View>(deleteId)?.setOnClickListener {
                    dialog.dismiss()
                    Toast.makeText(this, "Deleted Successfully", Toast.LENGTH_SHORT).show()
                }
            }

            dialog.show()
        } catch (e: Exception) { e.printStackTrace() }
    }
}
