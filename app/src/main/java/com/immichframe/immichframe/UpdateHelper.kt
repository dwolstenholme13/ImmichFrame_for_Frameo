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
import android.view.View
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.google.android.material.snackbar.Snackbar
import java.io.File
import retrofit2.Call
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path

// this is a helper to check for app updates on GitHub, and if the user requests, download and
// install the APK file found

object UpdateHelper {
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
        @GET("repos/{owner}/{repo}/releases/latest")
        fun getLatestRelease(
            @Path("owner") owner: String,
            @Path("repo") repo: String
        ): Call<GitHubRelease>
    }

    // check server for newer version
    fun checkForUpdate(anchorView: View, showNoUpdateMsg: Boolean = true) {
        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        val service = retrofit.create(GitHubService::class.java)
        val context = anchorView.context
        val owner = context.getString(R.string.github_owner)
        val repo = context.getString(R.string.github_repo)
        service.getLatestRelease(owner, repo).enqueue(object : retrofit2.Callback<GitHubRelease> {
            override fun onResponse(call: Call<GitHubRelease>, response: retrofit2.Response<GitHubRelease>) {
                if (response.isSuccessful) {
                    val release = response.body()
                    if (release != null) {
                        if (isNewerVersion(context, release.tag_name)) {
                            showUpdateDialog(anchorView, release)
                        } else if (showNoUpdateMsg) {
                            showSnackbar(anchorView, "You are on the latest version")
                        }
                    }
                } else if (showNoUpdateMsg) {
                    showSnackbar(anchorView, "Failed to check for updates")
                }
            }

            override fun onFailure(call: Call<GitHubRelease>, t: Throwable) {
                if (showNoUpdateMsg) {
                    showSnackbar(anchorView, "Error checking for updates")
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
    private fun showUpdateDialog(anchorView: View, release: GitHubRelease) {
        AlertDialog.Builder(anchorView.context)
            .setTitle("Update Available")
            .setMessage("Version ${release.tag_name} is available. Do you want to download and install it?\n\nRelease notes:\n${release.body}")
            .setPositiveButton("Download") { _, _ ->
                downloadAndInstall(anchorView, release)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // download and install APK, showing progress bar
    private fun downloadAndInstall(anchorView: View, release: GitHubRelease) {
        val asset = release.assets.find { it.name.endsWith(".apk") } ?: return
        val url = asset.browser_download_url

        // clean up any previous update files
        val context = anchorView.context
        val downloadDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
        downloadDir?.listFiles { _, name -> name.endsWith(".apk") }?.forEach {
            try { it.delete() } catch (_: Exception) {}
        }

        val request = DownloadManager.Request(Uri.parse(url))
            .setTitle("${context.getString(R.string.app_name)} Update")
            .setDescription("Downloading version ${release.tag_name}")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, "${context.getString(R.string.github_repo)}.${release.tag_name}.apk")

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
                                installApk(anchorView, file)
                            }
                        } else {
                            showSnackbar(anchorView, "Download failed")
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
    private fun installApk(anchorView: View, file: File) {
        if (!file.exists()) {
            showSnackbar(anchorView, "Cannot find downloaded APK file: ${file.name}")
            return
        }

        val context = anchorView.context
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    // show information message with Snackbar, with ImmichFrame logo
    private fun showSnackbar(view: View, message: String, isLong: Boolean = false) {
        val duration = if (isLong) Snackbar.LENGTH_LONG else Snackbar.LENGTH_SHORT
        view?.let { 
            val snackbar = Snackbar.make(it, message, duration) 
            val textView = snackbar.view.findViewById<TextView>(com.google.android.material.R.id.snackbar_text)

            // center the text in the snackbar
            textView.textAlignment = View.TEXT_ALIGNMENT_CENTER
            textView.gravity = Gravity.CENTER_HORIZONTAL or Gravity.CENTER_VERTICAL

            // add the ImmichFrame logo to the left of the text
            //val logo = ContextCompat.getDrawable(it.context, R.drawable.immich_frame_foreground)
            val logo = ContextCompat.getDrawable(it.context, R.mipmap.immich_frame_round)
            val density = it.resources.displayMetrics.density.toInt()
            val iconSize = 56 * density
            logo?.setBounds(0, 0, iconSize, iconSize)
            textView.setCompoundDrawables(logo, null, null, null)

            // fix padding
            textView.compoundDrawablePadding = 12 * density
            val vPadding = 8 * density
            val hPadding = 12 * density
            snackbar.view.setPadding(hPadding, vPadding, hPadding, vPadding)

            snackbar.show()
        }
    }
}
