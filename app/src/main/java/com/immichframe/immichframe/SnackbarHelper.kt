package com.immichframe.immichframe

import android.view.Gravity
import android.view.View
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.google.android.material.snackbar.Snackbar

// show information message with Snackbar, with ImmichFrame logo on left
object SnackbarHelper {
    fun show(view: View, message: String, isLong: Boolean = false) {
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
