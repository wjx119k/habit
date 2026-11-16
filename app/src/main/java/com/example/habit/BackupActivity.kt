package com.example.habit

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.button.MaterialButton
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 数据与备份。
 *
 * 导出：把所有数据写成 JSON 文件，用系统分享面板发出去（微信、邮件、网盘都行）
 * 导入：从文件里读回来，覆盖当前数据
 */
class BackupActivity : AppCompatActivity() {

    /** 系统文件选择器：选一个 json 文件 */
    private val pickFile = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@registerForActivityResult
        importFrom(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_backup)

        val root = findViewById<View>(R.id.backupRoot)
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        findViewById<ImageButton>(R.id.btnBackupBack).setOnClickListener {
            finish()
            @Suppress("DEPRECATION")
            overridePendingTransition(android.R.anim.fade_in, R.anim.slide_out_right)
        }

        findViewById<MaterialButton>(R.id.btnExportShare).setOnClickListener { exportAndShare() }

        findViewById<MaterialButton>(R.id.btnImportFile).setOnClickListener {
            runCatching {
                pickFile.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
            }.onFailure {
                Toast.makeText(this, R.string.backup_import_failed, Toast.LENGTH_SHORT).show()
            }
        }

        renderSummary()
    }

    override fun onResume() {
        super.onResume()
        renderSummary()
    }

    private fun renderSummary() {
        val s = Backup.summary(this)
        findViewById<TextView>(R.id.tvBackupSummary).text = getString(
            R.string.backup_summary,
            s.plans, s.items, s.redemptions, s.history, s.achievements
        )

        val lastView = findViewById<TextView>(R.id.tvBackupLast)
        val last = Backup.lastExportAt(this)
        if (last > 0) {
            val format = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
            lastView.text = getString(R.string.backup_last_export, format.format(Date(last)))
            lastView.visibility = View.VISIBLE
        } else {
            lastView.visibility = View.GONE
        }
    }

    // ============================================================
    //  导出 + 分享
    // ============================================================

    private fun exportAndShare() {
        runCatching {
            val file = Backup.writeToCache(this)

            // FileProvider 把私有目录里的文件"借"给别的 App 读
            val uri: Uri = FileProvider.getUriForFile(
                this,
                "$packageName.fileprovider",
                file
            )

            val send = Intent(Intent.ACTION_SEND).apply {
                type = "application/json"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, getString(R.string.backup_share_subject))
                putExtra(Intent.EXTRA_TEXT, getString(R.string.backup_share_text))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            startActivity(Intent.createChooser(send, getString(R.string.backup_share_title)))
            renderSummary()
        }.onFailure {
            Toast.makeText(this, getString(R.string.backup_export_failed, it.message.orEmpty()), Toast.LENGTH_LONG).show()
        }
    }

    // ============================================================
    //  导入
    // ============================================================

    private fun importFrom(uri: Uri) {
        val raw = runCatching {
            contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
        }.getOrNull()

        if (raw.isNullOrBlank()) {
            Toast.makeText(this, R.string.backup_import_failed, Toast.LENGTH_SHORT).show()
            return
        }

        val error = Backup.restore(this, raw)

        if (error != null) {
            Toast.makeText(this, error, Toast.LENGTH_LONG).show()
            return
        }

        Toast.makeText(this, R.string.backup_import_done, Toast.LENGTH_LONG).show()

        // 数据整份换了，回到启动页重新加载最干净
        val restart = Intent(this, SplashActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        startActivity(restart)
    }
}
