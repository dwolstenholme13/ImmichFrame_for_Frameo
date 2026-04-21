package com.immichframe.immichframe

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.FileProvider
import java.io.File
import retrofit2.Call
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET

// this is a helper to check for app updates on GitHub, and if the user requests, download and
// install the APK file found

object UpdateHelper {
    private const val GITHUB_OWNER = "dwolstenholme13"
    private const val GITHUB_REPO = "ImmichFrame_for_Frameo"
    private const val BASE_URL = "https://api.github.com/"

    data class GitHubRelease(
        val tag_name: String,
        val assets: List<GitHubAsset>,
        val body: String
    )

    data class GitHubAsset(
        val name: String,
        val browser_download_url: String
    )

    interface GitHubService {
        @GET("repos/$GITHUB_OWNER/$GITHUB_REPO/releases/latest")
        fun getLatestRelease(): Call<GitHubRelease>
    }

    // check server for newer version
    fun checkForUpdate(context: Context, showNoUpdateToast: Boolean = true) {
        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        val service = retrofit.create(GitHubService::class.java)
        service.getLatestRelease().enqueue(object : retrofit2.Callback<GitHubRelease> {
            override fun onResponse(call: Call<GitHubRelease>, response: retrofit2.Response<GitHubRelease>) {
                if (response.isSuccessful) {
                    val release = response.body()
                    if (release != null) {
                        if (isNewerVersion(context, release.tag_name)) {
                            showUpdateDialog(context, release)
                        } else if (showNoUpdateToast) {
                            Toast.makeText(context, "You are on the latest version", Toast.LENGTH_SHORT).show()
                        }
                    }
                } else if (showNoUpdateToast) {
                    Toast.makeText(context, "Failed to check for updates", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onFailure(call: Call<GitHubRelease>, t: Throwable) {
                if (showNoUpdateToast) {
                    Toast.makeText(context, "Error checking for updates", Toast.LENGTH_SHORT).show()
                }
            }
        })
    }

    // determine if version on server is newer than current version or not
    // version string has 4 parts (e.g. 1.0.48.1), with the last number being this project's version
    // number because it's a fork of ImmichFrame Android which has 3 parts (e.g. 1.0.48)
    private fun isNewerVersion(context: Context, latestTag: String): Boolean {
        val currentVersion = try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            pInfo.versionName ?: "0.0.0.0"
        } catch (e: Exception) {
            "0.0.0.0"
        }

        val cleanLatest = latestTag.removePrefix("v").trim()
        val cleanCurrent = currentVersion.removePrefix("v").trim()

        val latestParts = cleanLatest.split(".").mapNotNull { it.toIntOrNull() }
        val currentParts = cleanCurrent.split(".").mapNotNull { it.toIntOrNull() }

        // compare each version number part starting at most major
        // git tag usually omits first two values, so ignore those
        val relevantCurrent = currentParts.drop(currentParts.size - latestParts.size)
        val length = maxOf(latestParts.size, relevantCurrent.size)
        for (i in 0 until length) {
            val latestPart = latestParts.getOrElse(i) { 0 }
            val currentPart = relevantCurrent.getOrElse(i) { 0 }
            if (latestPart > currentPart) return true
            if (latestPart < currentPart) return false
        }
        return false
    }

    // show update dialog to user and get yes/no input
    private fun showUpdateDialog(context: Context, release: GitHubRelease) {
        AlertDialog.Builder(context)
            .setTitle("Update Available")
            .setMessage("Version ${release.tag_name} is available. Do you want to download and install it?\n\nRelease notes:\n${release.body}")
            .setPositiveButton("Download") { _, _ ->
                downloadAndInstall(context, release)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // download and install APK, showing progress bar
    private fun downloadAndInstall(context: Context, release: GitHubRelease) {
        val asset = release.assets.find { it.name.endsWith(".apk") } ?: return
        val url = asset.browser_download_url

        // clean up any previous update files
        val downloadDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
        downloadDir?.listFiles { _, name -> name.endsWith(".apk") }?.forEach {
            try { it.delete() } catch (_: Exception) {}
        }

        val request = DownloadManager.Request(Uri.parse(url))
            .setTitle("ImmichFrame for Frameo Update")
            .setDescription("Downloading version ${release.tag_name}")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, "ImmichFrame_update.apk")

        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val downloadId = manager.enqueue(request)

        // progress dialog UI
        val padding = (24 * context.resources.displayMetrics.density).toInt()
        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, padding, padding, padding)
        }
        val progressBar = ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
        }
        val progressText = TextView(context).apply {
            text = "0% (0 / 0 MB)"
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(0, (8 * context.resources.displayMetrics.density).toInt(), 0, 0)
        }
        layout.addView(progressBar)
        layout.addView(progressText)

        val progressDialog = AlertDialog.Builder(context)
            .setTitle("Downloading Update")
            .setView(layout)
            .setCancelable(false)
            .setNegativeButton("Cancel") { _, _ ->
                manager.remove(downloadId)
            }
            .show()

        val handler = Handler(Looper.getMainLooper())
        val progressRunnable = object : Runnable {
            override fun run() {
                val query = DownloadManager.Query().setFilterById(downloadId)
                val cursor = manager.query(query)
                if (cursor != null && cursor.moveToFirst()) {
                    val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                    val downloaded = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                    val total = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))

                    if (total > 0) {
                        val progress = (downloaded * 100 / total).toInt()
                        progressBar.progress = progress
                        val downloadedMB = downloaded / (1024 * 1024f)
                        val totalMB = total / (1024 * 1024f)
                        progressText.text = String.format("%.1f MB / %.1f MB (%d%%)", downloadedMB, totalMB, progress)
                    }

                    if (status == DownloadManager.STATUS_SUCCESSFUL || status == DownloadManager.STATUS_FAILED) {
                        progressDialog.dismiss()
                        cursor.close()
                        return  // stop polling
                    }
                }
                cursor?.close()
                handler.postDelayed(this, 500)
            }
        }
        handler.post(progressRunnable)

        val onComplete = object : BroadcastReceiver() {
            override fun onReceive(ctxt: Context, intent: Intent) {
                val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
                if (id == downloadId) {
                    val query = DownloadManager.Query().setFilterById(downloadId)
                    val cursor = manager.query(query)
                    if (cursor != null && cursor.moveToFirst()) {
                        val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                        if (status == DownloadManager.STATUS_SUCCESSFUL) {
                            val uriString = cursor.getString(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_LOCAL_URI))
                            if (uriString != null) {
                                val file = File(Uri.parse(uriString).path!!)
                                installApk(context, file)
                            }
                        } else {
                            Toast.makeText(context, "Download failed", Toast.LENGTH_SHORT).show()
                        }
                        cursor.close()
                    }
                    context.unregisterReceiver(this)
                }
            }
        }
        val receiverFlags = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            Context.RECEIVER_EXPORTED
        } else {
            0
        }
        context.registerReceiver(onComplete, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE), receiverFlags)
    }

    // install APK file
    private fun installApk(context: Context, file: File) {
        if (!file.exists()) {
            Toast.makeText(context, "Cannot find downloaded APK file: ${file.name}", Toast.LENGTH_SHORT).show()
            return
        }

        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
