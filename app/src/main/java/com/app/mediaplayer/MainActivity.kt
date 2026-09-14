package com.app.mediaplayer

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import java.io.File

class MainActivity : AppCompatActivity() {

    private val videoList = ArrayList<MediaItem>()
    private val audioList = ArrayList<MediaItem>()
    private lateinit var recyclerView: RecyclerView

    private var tabVideo: TextView? = null
    private var tabFolder: TextView? = null
    private var historyBlock: LinearLayout? = null
    private var tabLayout: LinearLayout? = null
    private var bottomSearchContainer: LinearLayout? = null
    private var meLayout: ScrollView? = null
    private var etSearch: EditText? = null

    // 4 Bottom Navigation Tabs
    private var navVideoIcon: ImageView? = null
    private var navVideoText: TextView? = null
    private var navMusicIcon: ImageView? = null
    private var navMusicText: TextView? = null
    private var navSearchIcon: ImageView? = null
    private var navSearchText: TextView? = null
    private var navMeIcon: ImageView? = null
    private var navMeText: TextView? = null

    private var isShowingVideos = true
    private var isFolderView = false
    private var isSearching = false

    private val activeColor = Color.parseColor("#00E5FF")
    private val inactiveColor = Color.parseColor("#8E8E9F")

    companion object {
        var currentMediaList = ArrayList<MediaItem>()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            setContentView(R.layout.activity_main)

            recyclerView = findViewById(R.id.recyclerView)
            tabVideo = findViewById(R.id.tabVideo)
            tabFolder = findViewById(R.id.tabFolder)
            historyBlock = findViewById(R.id.historyBlock)
            tabLayout = findViewById(R.id.tabLayout)
            bottomSearchContainer = findViewById(R.id.bottomSearchContainer)
            meLayout = findViewById(R.id.meLayout)
            etSearch = findViewById(R.id.etSearch)

            // Bottom Navigation Views
            navVideoIcon = findViewById(R.id.navVideoIcon)
            navVideoText = findViewById(R.id.navVideoText)
            navMusicIcon = findViewById(R.id.navMusicIcon)
            navMusicText = findViewById(R.id.navMusicText)
            navSearchIcon = findViewById(R.id.navSearchIcon)
            navSearchText = findViewById(R.id.navSearchText)
            navMeIcon = findViewById(R.id.navMeIcon)
            navMeText = findViewById(R.id.navMeText)

            recyclerView.layoutManager = LinearLayoutManager(this)

            setupTopTabs()
            setupCustomBottomTabs()
            setupSearch()
            setupMeFeatureClicks()

            checkAndRequestPermissions()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun setupCustomBottomTabs() {
        findViewById<View>(R.id.navVideoTab)?.setOnClickListener {
            highlightBottomTab(0)
            isSearching = false
            bottomSearchContainer?.visibility = View.GONE
            meLayout?.visibility = View.GONE
            recyclerView.visibility = View.VISIBLE
            historyBlock?.visibility = View.VISIBLE
            tabLayout?.visibility = View.VISIBLE
            isShowingVideos = true
            isFolderView = false
            resetTopTabs()
            updateList()
        }

        findViewById<View>(R.id.navMusicTab)?.setOnClickListener {
            highlightBottomTab(1)
            isSearching = false
            bottomSearchContainer?.visibility = View.GONE
            meLayout?.visibility = View.GONE
            recyclerView.visibility = View.VISIBLE
            historyBlock?.visibility = View.VISIBLE
            tabLayout?.visibility = View.VISIBLE
            isShowingVideos = false
            isFolderView = false
            resetTopTabs()
            updateList()
        }

        findViewById<View>(R.id.navSearchTab)?.setOnClickListener {
            highlightBottomTab(2)
            isSearching = true
            meLayout?.visibility = View.GONE
            bottomSearchContainer?.visibility = View.VISIBLE
            recyclerView.visibility = View.VISIBLE
            historyBlock?.visibility = View.GONE
            tabLayout?.visibility = View.GONE
            filterList(etSearch?.text?.toString() ?: "")
        }

        findViewById<View>(R.id.navMeTab)?.setOnClickListener {
            highlightBottomTab(3)
            isSearching = false
            bottomSearchContainer?.visibility = View.GONE
            historyBlock?.visibility = View.GONE
            tabLayout?.visibility = View.GONE
            recyclerView.visibility = View.GONE
            meLayout?.visibility = View.VISIBLE
        }
    }

    private fun highlightBottomTab(index: Int) {
        val icons = listOf(navVideoIcon, navMusicIcon, navSearchIcon, navMeIcon)
        val texts = listOf(navVideoText, navMusicText, navSearchText, navMeText)

        for (i in 0..3) {
            if (i == index) {
                icons[i]?.setColorFilter(activeColor)
                texts[i]?.setTextColor(activeColor)
            } else {
                icons[i]?.setColorFilter(inactiveColor)
                texts[i]?.setTextColor(inactiveColor)
            }
        }
    }

    private fun setupMeFeatureClicks() {
        try {
            // Top Star / Reward icon opens Monthly & Yearly VIP Subscription Screen
            findViewById<View>(R.id.btnRewardTop)?.setOnClickListener {
                startActivity(Intent(this, SubscriptionActivity::class.java))
            }

            // Settings button opens Monthly & Yearly VIP Subscription Screen
            findViewById<View>(R.id.btnMeSettings)?.setOnClickListener {
                startActivity(Intent(this, SubscriptionActivity::class.java))
            }

            findViewById<View>(R.id.btnMeTransfer)?.setOnClickListener {
                Toast.makeText(this, "File Transfer", Toast.LENGTH_SHORT).show()
            }
            findViewById<View>(R.id.btnMeVault)?.setOnClickListener {
                Toast.makeText(this, "Privacy Vault", Toast.LENGTH_SHORT).show()
            }
            findViewById<View>(R.id.btnMePlaylists)?.setOnClickListener {
                Toast.makeText(this, "My Playlists", Toast.LENGTH_SHORT).show()
            }
            findViewById<View>(R.id.btnMeHistory)?.setOnClickListener {
                Toast.makeText(this, "History", Toast.LENGTH_SHORT).show()
            }
            findViewById<View>(R.id.btnMeBin)?.setOnClickListener {
                Toast.makeText(this, "Recycle Bin", Toast.LENGTH_SHORT).show()
            }
            findViewById<View>(R.id.btnMeTheme)?.setOnClickListener {
                Toast.makeText(this, "Themes", Toast.LENGTH_SHORT).show()
            }
            findViewById<View>(R.id.btnMeHelp)?.setOnClickListener {
                Toast.makeText(this, "Help Center", Toast.LENGTH_SHORT).show()
            }
            findViewById<View>(R.id.btnMeRate)?.setOnClickListener {
                Toast.makeText(this, "Thanks for rating us 5 Stars!", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun setupSearch() {
        etSearch?.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (isSearching) filterList(s?.toString() ?: "")
            }
        })
    }

    private fun filterList(query: String) {
        try {
            val combinedList = ArrayList<MediaItem>()
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

    private fun resetTopTabs() {
        try {
            tabVideo?.setTextColor(activeColor)
            tabFolder?.setTextColor(inactiveColor)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun setupTopTabs() {
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
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
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

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (grantResults.isNotEmpty()) scanMedia()
    }

    private fun scanMedia() {
        try {
            videoList.clear()
            audioList.clear()

            val videoProjection = arrayOf(
                MediaStore.Video.Media._ID,
                MediaStore.Video.Media.TITLE,
                MediaStore.Video.Media.DATA,
                MediaStore.Video.Media.DURATION
            )
            contentResolver.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                videoProjection,
                null,
                null,
                null
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.TITLE)
                val pathCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)
                val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)

                while (cursor.moveToNext()) {
                    videoList.add(
                        MediaItem(
                            cursor.getLong(idCol),
                            cursor.getString(titleCol) ?: "Unknown Video",
                            cursor.getString(pathCol),
                            cursor.getLong(durationCol),
                            true
                        )
                    )
                }
            }

            val audioProjection = arrayOf(
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.DATA,
                MediaStore.Audio.Media.DURATION
            )
            contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                audioProjection,
                null,
                null,
                null
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val pathCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
                val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)

                while (cursor.moveToNext()) {
                    audioList.add(
                        MediaItem(
                            cursor.getLong(idCol),
                            cursor.getString(titleCol) ?: "Unknown Audio",
                            cursor.getString(pathCol),
                            cursor.getLong(durationCol),
                            false
                        )
                    )
                }
            }
            updateList()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun getFolders(items: List<MediaItem>): List<MediaFolder> {
        val grouped = items.groupBy { item ->
            try {
                File(item.path).parentFile?.name ?: "Unknown Folder"
            } catch (e: Exception) {
                "Unknown Folder"
            }
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
                    tabFolder?.setTextColor(inactiveColor)
                    tabVideo?.setTextColor(activeColor)
                    showItemsInFolder(clickedFolder.mediaItems)
                }
            } else {
                showItemsInFolder(list)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun showItemsInFolder(itemsToShow: List<MediaItem>) {
        try {
            recyclerView.layoutManager = LinearLayoutManager(this)
            recyclerView.adapter = MediaAdapter(
                itemsToShow,
                false,
                { item -> showMediaOptionsDialog(item) },
                { item ->
                    currentMediaList.clear()
                    currentMediaList.addAll(itemsToShow)
                    val targetActivity = if (item.isVideo) PlayerActivity::class.java else AudioPlayerActivity::class.java
                    startActivity(Intent(this, targetActivity).apply {
                        putExtra("START_INDEX", itemsToShow.indexOf(item))
                    })
                }
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
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
                    startActivity(Intent(this, AudioPlayerActivity::class.java).apply {
                        putExtra("START_INDEX", currentMediaList.indexOf(item))
                    })
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
                    Toast.makeText(this, "Deleted", Toast.LENGTH_SHORT).show()
                }
            }
            dialog.show()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
