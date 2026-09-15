package com.app.mediaplayer

import android.Manifest
import android.content.ContentUris
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.StrictMode
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
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

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
            findViewById<View>(R.id.btnRewardTop)?.setOnClickListener {
                startActivity(Intent(this, SubscriptionActivity::class.java))
            }
            findViewById<View>(R.id.btnMeSettings)?.setOnClickListener {
                startActivity(Intent(this, SettingsActivity::class.java))
            }
            findViewById<View>(R.id.btnMeTransfer)?.setOnClickListener {
                Toast.makeText(this, "File Transfer Engine Active", Toast.LENGTH_SHORT).show()
            }
            findViewById<View>(R.id.btnMeVault)?.setOnClickListener {
                val vaultDir = File(filesDir, ".PrivacyVault")
                val count = vaultDir.listFiles()?.size ?: 0
                Toast.makeText(this, "Privacy Vault: $count hidden files secured", Toast.LENGTH_LONG).show()
            }
            findViewById<View>(R.id.btnMePlaylists)?.setOnClickListener {
                Toast.makeText(this, "My Playlists", Toast.LENGTH_SHORT).show()
            }
            findViewById<View>(R.id.btnMeHistory)?.setOnClickListener {
                Toast.makeText(this, "Watch History", Toast.LENGTH_SHORT).show()
            }
            findViewById<View>(R.id.btnMeBin)?.setOnClickListener {
                Toast.makeText(this, "Recycle Bin (Empty)", Toast.LENGTH_SHORT).show()
            }
            findViewById<View>(R.id.btnMeTheme)?.setOnClickListener {
                Toast.makeText(this, "Dark Neon Theme Active", Toast.LENGTH_SHORT).show()
            }
            findViewById<View>(R.id.btnMeHelp)?.setOnClickListener {
                Toast.makeText(this, "Help Center", Toast.LENGTH_SHORT).show()
            }
            findViewById<View>(R.id.btnMeRate)?.setOnClickListener {
                Toast.makeText(this, "Thanks for 5 Stars!", Toast.LENGTH_SHORT).show()
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

            try {
                val downloadFolder = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                if (downloadFolder.exists() && downloadFolder.isDirectory) {
                    val partialFiles = downloadFolder.listFiles { file ->
                        file.isFile && (file.name.endsWith(".crdownload", true) || file.name.endsWith(".part", true))
                    }
                    partialFiles?.forEachIndexed { index, file ->
                        videoList.add(
                            0,
                            MediaItem(
                                (999999 + index).toLong(),
                                "⚡ [Chrome Downloading] " + file.name.removeSuffix(".crdownload").removeSuffix(".part"),
                                file.absolutePath,
                                0L,
                                true
                            )
                        )
                    }
                }
            } catch (_: Exception) {}

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

            view.findViewById<TextView>(R.id.menuMediaTitle)?.text = item.title

            // Real Sharing
            view.findViewById<View>(R.id.menuShare)?.setOnClickListener {
                dialog.dismiss()
                try {
                    StrictMode.setVmPolicy(StrictMode.VmPolicy.Builder().build())
                    val contentUri = try {
                        if (item.isVideo) {
                            ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, item.id)
                        } else {
                            ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, item.id)
                        }
                    } catch (_: Exception) {
                        Uri.fromFile(File(item.path))
                    }
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = if (item.isVideo) "video/*" else "audio/*"
                        putExtra(Intent.EXTRA_STREAM, contentUri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    startActivity(Intent.createChooser(shareIntent, "Share Media Via:"))
                } catch (e: Exception) {
                    Toast.makeText(this, "Share error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }

            // Play As Audio
            view.findViewById<View>(R.id.menuPlayAudio)?.setOnClickListener {
                dialog.dismiss()
                currentMediaList.clear()
                currentMediaList.addAll(if (isShowingVideos) videoList else audioList)
                startActivity(Intent(this, AudioPlayerActivity::class.java).apply {
                    putExtra("START_INDEX", currentMediaList.indexOf(item))
                })
            }

            // Real Privacy Vault
            view.findViewById<View>(R.id.menuLockVault)?.setOnClickListener {
                dialog.dismiss()
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

                        Toast.makeText(this, "Moved to Private Vault!", Toast.LENGTH_SHORT).show()
                        scanMedia()
                    }
                } catch (e: Exception) {
                    Toast.makeText(this, "Vault move failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }

            // Real Delete
            view.findViewById<View>(R.id.menuDelete)?.setOnClickListener {
                dialog.dismiss()
                AlertDialog.Builder(this)
                    .setTitle("Delete Media")
                    .setMessage("Permanently delete \"${item.title}\"?")
                    .setPositiveButton("Delete") { _, _ ->
                        try {
                            val file = File(item.path)
                            if (file.delete()) {
                                MediaScannerConnection.scanFile(this, arrayOf(item.path), null, null)
                                Toast.makeText(this, "Deleted successfully", Toast.LENGTH_SHORT).show()
                                scanMedia()
                            } else {
                                Toast.makeText(this, "Could not delete file", Toast.LENGTH_SHORT).show()
                            }
                        } catch (e: Exception) {
                            Toast.makeText(this, "Error deleting: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }

            dialog.show()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
