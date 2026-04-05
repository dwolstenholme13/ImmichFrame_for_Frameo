package com.immichframe.immichframe

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.InputType
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import androidx.preference.SwitchPreferenceCompat

class SettingsFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.settings_view, rootKey)
        val chkUseWebView = findPreference<SwitchPreferenceCompat>("useWebView")
        val chkBlurredBackground = findPreference<SwitchPreferenceCompat>("blurredBackground")
        val chkShowCurrentDate = findPreference<SwitchPreferenceCompat>("showCurrentDate")
        val chkKeepScreenOn = findPreference<SwitchPreferenceCompat>("keepScreenOn")
        val txtScreenTimeout = findPreference<EditTextPreference>("screenTimeout")
        val screenDimmingCategory = findPreference<PreferenceCategory>("screenDimmingCategory")
        val chkScreenDim = findPreference<SwitchPreferenceCompat>("screenDim")
        val txtDimTime = findPreference<EditTextPreference>("dim_time_range")

        // obfuscate the authSecret
        val authPref = findPreference<EditTextPreference>("authSecret")
        authPref?.setOnBindEditTextListener { editText ->
            editText.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }

        // Update visibility based on switches
        val useWebView = chkUseWebView?.isChecked ?: false
        chkBlurredBackground?.isVisible = !useWebView
        chkShowCurrentDate?.isVisible = !useWebView
        
        val keepScreenOn = chkKeepScreenOn?.isChecked ?: false
        screenDimmingCategory?.isVisible = keepScreenOn
        txtScreenTimeout?.isVisible = !keepScreenOn
        if (!keepScreenOn) {
            chkScreenDim?.isChecked = false
            txtDimTime?.isVisible = false
        }
        
        val screenDim = chkScreenDim?.isChecked ?: false
        txtDimTime?.isVisible = screenDim

        // React to changes

        // use Webview setting
        chkUseWebView?.setOnPreferenceChangeListener { _, newValue ->
            val value = newValue as Boolean
            chkBlurredBackground?.isVisible = !value
            chkShowCurrentDate?.isVisible = !value
            //add android settings button
            true
        }

        // keep screen on setting - toggles screen dimming category visibility
        // show screen dimming only if keep-on is set
        // show screen timeout setting only if keep-on is not set
        chkKeepScreenOn?.setOnPreferenceChangeListener { _, newValue ->
            val value = newValue as Boolean
            screenDimmingCategory?.isVisible = value
            txtScreenTimeout?.isVisible = !value
            if (!value) {
                chkScreenDim?.isChecked = false
                txtDimTime?.isVisible = false
            }
            true
        }

        // validate screen on timeout value
        txtScreenTimeout?.setOnPreferenceChangeListener { _, newValue ->
            val value = newValue.toString().toIntOrNull()
            if (value != null && value in 1..1440) {
                true
            } else {
                Toast.makeText(requireContext(), "Please enter a value between 1 and 1440 minutes (24 hours)", Toast.LENGTH_SHORT).show()
                false
            }
        }

        // screen dimming setting - dimming time becomes visible if set
        chkScreenDim?.setOnPreferenceChangeListener { _, newValue ->
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
                Toast.makeText(requireContext(), "Please enter a valid server URL.", Toast.LENGTH_LONG).show()
                false
            } else {
                activity?.setResult(Activity.RESULT_OK)
                activity?.finish()
                true
            }
        }

        // launch Android settings selection
        val btnAndroidSettings = findPreference<Preference>("androidSettings")
        btnAndroidSettings?.setOnPreferenceClickListener {
            val context = requireContext()

            // Only show Toast + auto-return on Android 9 and below
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                Toast.makeText(context, "Returning to app in 2 minutes…", Toast.LENGTH_LONG).show()

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

                true // Accept new value
            } else {
                Toast.makeText(requireContext(), "Invalid time format. Use HH:mm-HH:mm.", Toast.LENGTH_LONG).show()
                false // Reject value change
            }
        }
    }
}
