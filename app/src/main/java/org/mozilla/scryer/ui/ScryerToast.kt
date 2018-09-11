package org.mozilla.scryer.ui

import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.TextView
import android.widget.Toast
import org.mozilla.scryer.R

class ScryerToast(private val context: Context) {
    companion object {

        /**
         * Use ScryerToast#show() if you're likely to show toast multiple times within the same page,
         * so the toast view can be reused instead of inflating a new one each time this method is called.
         */
        fun makeText(context: Context, text: String, duration: Int): Toast {
            val toast = Toast(context)
            toast.setGravity(Gravity.FILL_HORIZONTAL or Gravity.BOTTOM, 0, 0)
            toast.view = View.inflate(context, R.layout.view_custom_toast, null)
            toast.view.findViewById<TextView>(R.id.text)?.text = text
            toast.duration = duration
            return toast
        }
    }

    private var toast: Toast? = null
    private var rootView: View = View.inflate(context, R.layout.view_custom_toast, null)
    private val textView: TextView by lazy {
        rootView.findViewById<TextView>(R.id.text)
    }

    fun show(msg: String, toastDuration: Int, yOffset: Int = 0) {
        textView.text = msg
        toast?.cancel()

        toast = Toast(context).apply {
            setGravity(Gravity.FILL_HORIZONTAL or Gravity.BOTTOM, 0, yOffset)
            view = rootView
            duration = toastDuration
            show()
        }
    }
}
