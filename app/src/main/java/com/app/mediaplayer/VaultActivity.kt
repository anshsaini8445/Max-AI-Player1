package com.app.mediaplayer

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.io.File
import java.security.MessageDigest

class VaultActivity : AppCompatActivity() {

    private lateinit var layoutAuth: LinearLayout
    private lateinit var layoutSecuritySetup: LinearLayout
    private lateinit var layoutVaultContent: LinearLayout
    
    private val pinDots = arrayOfNulls<View>(6)
    private var currentPinBuffer = StringBuilder()
    
    private lateinit var sharedPrefs: android.content.SharedPreferences
    private var isSettingUpPin = false
    private var isRecoveryMode = false
    
    private lateinit.let var rvVaultMedia: RecyclerView
    private lateinit.let var tvEmptyVault: TextView
    private lateinit.let var vaultAdapter: VaultMediaAdapter
    private val vaultFiles = mutableListOf<File>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_vault)

        sharedPrefs = getSharedPreferences("max_vault_prefs", Context.MODE_PRIVATE)

        layoutAuth = findViewById(R.id.layoutAuth)
        layoutSecuritySetup = findViewById(R.id.layoutSecuritySetup)
        layoutVaultContent = findViewById(R.id.layoutVaultContent)

        pinDots[0] = findViewById(R.id.pinDot1)
        pinDots[1] = findViewById(R.id.pinDot2)
        pinDots[2] = findViewById(R.id.pinDot3)
        pinDots[3] = findViewById(R.id.pinDot4)
        pinDots[4] = findViewById(R.id.pinDot5)
        pinDots[5] = findViewById(R.id.pinDot6)

        setupKeypad()
        checkInitialState()
    }

    private fun checkInitialState() {
        val storedPin = sharedPrefs.getString("vault_pin_hash", null)
        if (storedPin == null) {
            isSettingUpPin = true
            findViewById<TextView>(R.id.tvVaultTitle).text = "Set Vault PIN"
            findViewById<TextView>(R.id.tvVaultSubtitle).text = "Enter a new 6-digit PIN"
        } else {
            isSettingUpPin = false
            findViewById<TextView>(R.id.tvVaultTitle).text = "Private Vault"
            findViewById<TextView>(R.id.tvVaultSubtitle).text = "Enter 6-digit PIN"
        }
    }

    private fun setupKeypad() {
        val btnIds = intArrayOf(
            R.id.btn0, R.id.btn1, R.id.btn2, R.id.btn3, R.id.btn4,
            R.id.btn5, R.id.btn6, R.id.btn7, R.id.btn8, R.id.btn9
        )

        for (id in btnIds) {
            findViewById<Button>(id).setOnClickListener { view ->
                val digit = (view as Button).text.toString()
                appendPin(digit)
            }
        }

        findViewById<Button>(R.id.btnDel).setOnClickListener {
            if (currentPinBuffer.isNotEmpty()) {
                currentPinBuffer.deleteCharAt(currentPinBuffer.length - 1)
                updatePinDots()
            }
        }

        findViewById<Button>(R.id.btnForgot).setOnClickListener {
            showSecurityRecovery()
        }

        findViewById<Button>(R.id.btnSubmitSecurity).setOnClickListener {
            val answer = findViewById<EditText>(R.id.etSecurityAnswer).text.toString().trim()
            if (answer.isEmpty()) {
                Toast.makeText(this, "Please enter answer", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (isRecoveryMode) {
                val storedAnswerHash = sharedPrefs.getString("vault_security_answer", "")
                if (hashString(answer) == storedAnswerHash) {
                    Toast.makeText(this, "Correct! Set a new PIN", Toast.LENGTH_SHORT).show()
                    sharedPrefs.edit().remove("vault_pin_hash").apply()
                    layoutSecuritySetup.visibility = View.GONE
                    layoutAuth.visibility = View.VISIBLE
                    isRecoveryMode = false
                    isSettingUpPin = true
                    currentPinBuffer.clear()
                    updatePinDots()
                    findViewById<TextView>(R.id.tvVaultTitle).text = "Set New PIN"
                    findViewById<TextView>(R.id.tvVaultSubtitle).text = "Enter a new 6-digit PIN"
                } else {
                    Toast.makeText(this, "Incorrect answer", Toast.LENGTH_SHORT).show()
                }
            } else {
                // Initial setup security answer saving
                val pinHash = hashString(currentPinBuffer.toString())
                val answerHash = hashString(answer)
                sharedPrefs.edit()
                    .putString("vault_pin_hash", pinHash)
                    .putString("vault_security_answer", answerHash)
                    .apply()

                Toast.makeText(this, "Vault PIN setup complete!", Toast.LENGTH_SHORT).show()
                layoutSecuritySetup.visibility = View.GONE
                openVaultContent()
            }
        }
    }

    private fun appendPin(digit: String) {
        if (currentPinBuffer.length < 6) {
            currentPinBuffer.append(digit)
            updatePinDots()
            if (currentPinBuffer.length == 6) {
                handlePinComplete()
            }
        }
    }

    private fun updatePinDots() {
        for (i in pinDots.indices) {
            if (i < currentPinBuffer.length) {
                pinDots[i]?.setBackgroundResource(R.drawable.ic_app_icon)
            } else {
                pinDots[i]?.setBackgroundResource(R.drawable.bg_circle_outline)
            }
        }
    }

    private fun handlePinComplete() {
        val pinString = currentPinBuffer.toString()
        if (isSettingUpPin) {
            // Prompt for security question
            layoutAuth.visibility = View.GONE
            layoutSecuritySetup.visibility = View.VISIBLE
            findViewById<TextView>(R.id.tvSecurityPrompt).text = "Security Question: What is your birthplace?"
        } else {
            val storedHash = sharedPrefs.getString("vault_pin_hash", "")
            if (hashString(pinString) == storedHash) {
                openVaultContent()
            } else {
                Toast.makeText(this, "Incorrect PIN", Toast.LENGTH_SHORT).show()
                currentPinBuffer.clear()
                updatePinDots()
            }
        }
    }

    private fun showSecurityRecovery() {
        isRecoveryMode = true
        layoutAuth.visibility = View.GONE
        layoutSecuritySetup.visibility = View.VISIBLE
        findViewById<TextView>(R.id.tvSecurityPrompt).text = "Recovery: What is your birthplace?"
    }

    private fun openVaultContent() {
        layoutAuth.visibility = View.GONE
        layoutSecuritySetup.visibility = View.GONE
        layoutVaultContent.visibility = View.VISIBLE

        val toolbar = findViewById<Toolbar>(R.id.vaultToolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { finish() }

        rvVaultMedia = findViewById(R.id.rvVaultMedia)
        tvEmptyVault = findViewById(R.id.tvEmptyVault)

        rvVaultMedia.layoutManager = LinearLayoutManager(this)
        loadVaultFiles()
    }

    private fun loadVaultFiles() {
        vaultFiles.clear()
        val vaultDir = File(getExternalFilesDir(null), "vault_media")
        if (vaultDir.exists() && vaultDir.isDirectory) {
            val files = vaultDir.listFiles { file -> file.isFile && !file.name.equals(".nomedia", ignoreCase = true) }
            if (files != null) {
                vaultFiles.addAll(files)
            }
        }

        if (vaultFiles.isEmpty()) {
            tvEmptyVault.visibility = View.VISIBLE
            rvVaultMedia.visibility = View.GONE
        } else {
            tvEmptyVault.visibility = View.GONE
            rvVaultMedia.visibility = View.VISIBLE
            vaultAdapter = VaultMediaAdapter(vaultFiles) { file ->
                playVaultMedia(file)
            } }
            rvVaultMedia.adapter = vaultAdapter
    }

    private fun playVaultMedia(file: File) {
        // Launch PlayerActivity or AudioPlayerActivity depending on file type
        val name = file.name.lowercase()
        val intent = if (name.endsWith(".mp3.enc") || name.endsWith(".wav.enc") || name.endsWith(".aac.enc")) {
            Intent(this, AudioPlayerActivity::class.java)
        } else {
            Intent(this, PlayerActivity::class.java)
        }
        intent.putExtra("media_path", file.absolutePath)
        startActivity(intent)
    }

    private fun hashString(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    inner class VaultMediaAdapter(
        private val list: List<File>,
        private val onClick: (File) -> Unit
    ) : RecyclerView.Adapter<VaultMediaAdapter.ViewHolder>() {

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvTitle: TextView = view.findViewById(R.id.tvMediaTitle)
            val tvSize: TextView = view.findViewById(R.id.tvMediaSize)
            val ivThumb: ImageView = view.findViewById(R.id.ivThumbnail)
            val btnRestore: Button = view.findViewById(R.id.btnDelete)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_media, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val file = list[position]
            holder.tvTitle.text = file.name.removeSuffix(".enc")
            holder.tvSize.text = "Size: ${file.length() / (1024 * 1024)} MB"
            holder.ivThumb.setImageResource(R.drawable.ic_app_icon)
            
            holder.btnRestore.text = "Restore"
            holder.btnRestore.setOnClickListener {
                restoreFile(file)
            }

            holder.itemView.setOnClickListener {
                onClick(file)
            }
        }

        override fun getItemCount(): Int = list.size
    }

    private fun restoreFile(file: File) {
        val isAudio = file.name.lowercase().contains("audio") || file.name.endsWith(".mp3.enc")
        val targetDir = if (isAudio) {
            android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_MUSIC)
        } else {
            android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_MOVIES)
        }

        if (!targetDir.exists()) targetDir.mkdirs()

        val originalName = file.name.removeSuffix(".enc")
        val restoredFile = File(targetDir, originalName)

        if (file.renameTo(restoredFile)) {
            Toast.makeText(this, "Restored successfully to ${targetDir.name}", Toast.LENGTH_SHORT).show()
            loadVaultFiles()
        } else {
            Toast.makeText(this, "Failed to restore file", Toast.LENGTH_SHORT).show()
        }
    }
}
