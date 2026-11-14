package com.immichframe.immichframe

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.InputType
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import androidx.preference.SeekBarPreference
import androidx.preference.SwitchPreferenceCompat

class SettingsFragment : PreferenceFragmentCompat() {
    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        updateWifiSummary()
        launchWifiSettings()
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.settings_view, rootKey)
        val chkUseWebView = findPreference<SwitchPreferenceCompat>("useWebView")
        val chkBlurredBackground = findPreference<SwitchPreferenceCompat>("blurredBackground")
        val chkShowCurrentDate = findPreference<SwitchPreferenceCompat>("showCurrentDate")
        val chkKeepScreenOn = findPreference<SwitchPreferenceCompat>("keepScreenOn")
        val screenTimeoutPref = findPreference<EditTextPreference>("screenTimeout")
        val screenDimmingCategory = findPreference<PreferenceCategory>("screenDimmingCategory")
        val chkScreenDimming = findPreference<SwitchPreferenceCompat>("screenDimming")
        val txtDimTime = findPreference<EditTextPreference>("dimTimeRange")
        val brightnessPref = findPreference<SeekBarPreference>("manualBrightness")

        // obfuscate the authSecret
        val authPref = findPreference<EditTextPreference>("authSecret")
        authPref?.setOnBindEditTextListener { editText ->
            editText.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }

        // ensure screen timeout uses a number keypad
        screenTimeoutPref?.setOnBindEditTextListener { editText ->
            editText.inputType = InputType.TYPE_CLASS_NUMBER
        }

        // set URI input type and disable suggestions/auto-correct for the server URL
        val urlPref = findPreference<EditTextPreference>("webview_url")
        urlPref?.setOnBindEditTextListener { editText ->
            editText.inputType = InputType.TYPE_CLASS_TEXT or 
                                 InputType.TYPE_TEXT_VARIATION_URI or 
                                 InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        }

        // update visibility based on switches
        val useWebView = chkUseWebView?.isChecked ?: false
        chkBlurredBackground?.isVisible = !useWebView
        chkShowCurrentDate?.isVisible = !useWebView
        
        val keepScreenOn = chkKeepScreenOn?.isChecked ?: false
        screenDimmingCategory?.isVisible = keepScreenOn
        screenTimeoutPref?.isVisible = !keepScreenOn
        if (!keepScreenOn) {
            screenTimeoutPref?.summary = "Screen turns off after ${screenTimeoutPref?.text ?: "10"} minutes"
            chkScreenDimming?.isChecked = false
            txtDimTime?.isVisible = false
        }
        
        val screenDimming = chkScreenDimming?.isChecked ?: false
        txtDimTime?.isVisible = screenDimming
        if (screenDimming) {
            updateDimSummary()
        }

        // React to changes

        // apply brightness setting dynamically
        brightnessPref?.setOnPreferenceChangeListener { _, newValue ->
            val brightness = (newValue as Int) / 100f  // brightness setting is 0.0f - 1.0f
            val activity = activity
            if (activity != null) {
                val lp = activity.window.attributes
                lp.screenBrightness = brightness
                activity.window.attributes = lp
            }
            true
        }

        // use Webview setting
        // show blurred background setting only if WebView is not set
        // show show current date setting only if WebView is not set
        chkUseWebView?.setOnPreferenceChangeListener { _, newValue ->
            val value = newValue as Boolean
            chkBlurredBackground?.isVisible = !value
            chkShowCurrentDate?.isVisible = !value
            //add android settings button
            true
        }

        // keep screen on setting - toggles screen dimming category visibility
        // show screen dimming setting only if keep-on is set
        // show screen timeout setting only if keep-on is not set
        chkKeepScreenOn?.setOnPreferenceChangeListener { _, newValue ->
            val value = newValue as Boolean
            screenDimmingCategory?.isVisible = value
            screenTimeoutPref?.isVisible = !value
            if (!value) {
                chkScreenDimming?.isChecked = false
                txtDimTime?.isVisible = false
            }
            true
        }

        // validate screen-on timeout value and update summary line
        screenTimeoutPref?.setOnPreferenceChangeListener { _, newValue ->
            val value = newValue.toString().toIntOrNull()
            if (value != null && value in 1..1440) {
                screenTimeoutPref?.summary = "Screen turns off after $newValue minutes"
                true
            } else {
                SnackbarHelper.show(requireView(), "Please enter a value between 1 and 1440 minutes (24 hours)")
                false
            }
        }

        // screen dimming setting - dimming time becomes visible if set
        chkScreenDimming?.setOnPreferenceChangeListener { _, newValue ->
            val value = newValue as Boolean
            txtDimTime?.isVisible = value
            true
        }

        // settings lock setting: prevent further access to settings screen
        val chkSettingsLock = findPreference<SwitchPreferenceCompat>("settingsLock")
        chkSettingsLock?.setOnPreferenceChangeListener { _, newValue ->
            val enabling = newValue as Boolean
            if (enabling) {
                AlertDialog.Builder(requireContext())
                    .setTitle("Confirm Action")
                    .setMessage(
                        "This will disable access to the settings screen, the only way back is via RPC commands (or uninstall/reinstall).\n" +
                                "Are you absolutely sure?"
                    )
                    .setPositiveButton("Yes", null) // Proceed
                    .setNegativeButton("No") { dialog, _ ->
                        chkSettingsLock.isChecked = false // revert
                        dialog.dismiss()
                    }
                    .show()
            }
            true
        }

        // close settings view
        val btnClose = findPreference<Preference>("closeSettings")
        btnClose?.setOnPreferenceClickListener {
            val url = PreferenceManager.getDefaultSharedPreferences(requireContext())
                .getString("webview_url", "")?.trim()
            val urlPattern = Regex("^https?://.+")
            return@setOnPreferenceClickListener if (url.isNullOrEmpty()|| !url.matches(urlPattern)) {
                SnackbarHelper.show(requireView(), "Please enter a valid server URL.")
                false
            } else {
                activity?.setResult(Activity.RESULT_OK)
                activity?.finish()
                true
            }
        }

        // launch Wi-Fi settings panel (Android 10+) or standard Wi-Fi settings fallback
        val btnWifiSettings = findPreference<Preference>("wifiSettings")
        btnWifiSettings?.setOnPreferenceClickListener {
            val context = context ?: return@setOnPreferenceClickListener true
            val hasLocation = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED

            if (hasLocation) {
                launchWifiSettings()
            } else {
                locationPermissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    )
                )
            }
            true
        }
        updateWifiSummary()

        // launch Android settings selection
        val btnAndroidSettings = findPreference<Preference>("androidSettings")
        btnAndroidSettings?.setOnPreferenceClickListener {
            val context = requireContext()

            // Only show message + auto-return on Android 9 and below
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                SnackbarHelper.show(requireView(), "Returning to app in 2 minutes…")

                // Schedule return after 2 minutes
                Handler(Looper.getMainLooper()).postDelayed({
                    val returnIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
                    returnIntent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    context.startActivity(returnIntent)
                }, 2 * 60 * 1000)
            }

            // Launch Android settings
            val intent = Intent(Settings.ACTION_SETTINGS)
            startActivity(intent)
            true
        }

        // check for app updates
        val btnUpdate = findPreference<Preference>("checkForUpdates")
        val currentVersion = try {
            val pInfo = requireContext().packageManager.getPackageInfo(requireContext().packageName, 0)
            pInfo.versionName ?: "Unknown"
        } catch (e: Exception) {
            "Unknown"
        }
        btnUpdate?.summary = "Check for a new app version (Current: $currentVersion)"
        btnUpdate?.setOnPreferenceClickListener {
            UpdateHelper.checkForUpdate(requireView())
            true
        }

        // get dimming time range setting from input string
        txtDimTime?.setOnPreferenceChangeListener { _, newValue ->
            val timeRange = newValue.toString().trim()

            val regex = "^([01]?[0-9]|2[0-3]):([0-5][0-9])-([01]?[0-9]|2[0-3]):([0-5][0-9])$".toRegex()
            if (timeRange.matches(regex)) {
                val (start, end) = timeRange.split("-")
                val (startHour, startMinute) = start.split(":").map { it.toInt() }
                val (endHour, endMinute) = end.split(":").map { it.toInt() }

                // Save parsed time values separately
                val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(requireContext())
                sharedPreferences.edit()
                    .putInt("dimStartHour", startHour)
                    .putInt("dimStartMinute", startMinute)
                    .putInt("dimEndHour", endHour)
                    .putInt("dimEndMinute", endMinute)
                    .apply()

                updateDimSummary()
                true // Accept new value
            } else {
                SnackbarHelper.show(requireView(), "Invalid time format. Use HH:mm-HH:mm.")
                false // Reject value change
            }
        }
    }

    // update the summary line for the "Dimming Time Range" selection with the current setting
    private fun updateDimSummary() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
        val startHour = prefs.getInt("dimStartHour", 22)
        val startMinute = prefs.getInt("dimStartMinute", 0)
        val endHour = prefs.getInt("dimEndHour", 6)
        val endMinute = prefs.getInt("dimEndMinute", 0)

        val txtDimTime = findPreference<EditTextPreference>("dimTimeRange")
        txtDimTime?.summary = String.format("Current: %02d:%02d - %02d:%02d", startHour, startMinute, endHour, endMinute)
    }

    // make sure the WiFi summary is updated when user returns from WiFi settings panel
    override fun onResume() {
        super.onResume()
        updateWifiSummary()
    }

    // launch the WiFi settings panel
    private fun launchWifiSettings() {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            Intent(Settings.Panel.ACTION_WIFI)
        } else {
            Intent(Settings.ACTION_WIFI_SETTINGS)
        }
        try {
            startActivity(intent)
        } catch (_: Exception) {
            // Fallback to standard Wi-Fi settings if panel is unavailable
            startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
        }
    }

    // update the summmary line for WiFi in the settings menu to show the connected network if any
    private fun updateWifiSummary() {
        val pref = findPreference<Preference>("wifiSettings") ?: return
        val context = context ?: return

        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        if (cm == null) {
            pref.summary = "Not connected"
            return
        }

        val network = cm.activeNetwork
        if (network == null) {
            pref.summary = "Not connected"
            return
        }

        val caps = cm.getNetworkCapabilities(network)
        if (caps == null) {
            pref.summary = "Not connected"
            return
        }

        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
            val hasLocation = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED

            var ssid: String? = null
            if (hasLocation) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val wifiInfo = caps.transportInfo as? WifiInfo
                    ssid = wifiInfo?.ssid
                }

                if (ssid.isNullOrBlank() || ssid == "<unknown ssid>") {
                    @Suppress("DEPRECATION")
                    val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
                    @Suppress("DEPRECATION")
                    ssid = wm?.connectionInfo?.ssid
                }
            }

            val cleanSsid = ssid?.removeSurrounding("\"")
            if (!cleanSsid.isNullOrBlank() && cleanSsid != "<unknown ssid>") {
                pref.summary = "Currently connected to $cleanSsid"
            } else {
                pref.summary = "Connected to Wi‑Fi"
            }
        } else if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) {
            pref.summary = "Connected via Ethernet"
        } else {
            pref.summary = "Not connected"
        }
    }
}
