package com.immichframe.immichframe

import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.google.android.material.snackbar.Snackbar

// show information message with Snackbar, with ImmichFrame logo on left
object SnackbarHelper {
    private var durationMs = 4000  // use 4s delay for all snackbars

    fun show(view: View, message: String) {
        view?.let { 
            val snackbar = Snackbar.make(it, message, durationMs) 
            val textView = snackbar.view.findViewById<TextView>(com.google.android.material.R.id.snackbar_text)

            // center the text in the snackbar and increase font size to 18sp
            textView.textAlignment = View.TEXT_ALIGNMENT_CENTER
            textView.gravity = Gravity.CENTER_HORIZONTAL or Gravity.CENTER_VERTICAL
            textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)

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
