package org.mozilla.scryer.ui

import android.content.Context
import android.text.Spannable
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.util.Patterns
import android.view.GestureDetector
import android.view.MotionEvent
import android.widget.TextView

class TextViewClickMovement(context: Context, private val mListener: OnTextViewClickMovementListener?) : LinkMovementMethod() {
    private val mGestureDetector: GestureDetector
    private var mWidget: TextView? = null
    private var mBuffer: Spannable? = null

    enum class LinkType {

        /**
         * Indicates that phone link was clicked
         */
        PHONE,

        /**
         * Identifies that URL was clicked
         */
        WEB_URL,

        /**
         * Identifies that Email Address was clicked
         */
        EMAIL_ADDRESS,

        /**
         * Indicates that none of above mentioned were clicked
         */
        NONE
    }

    /**
     * Interface used to handle Long clicks on the [TextView] and taps
     * on the phone, web, mail links inside of [TextView].
     */
    interface OnTextViewClickMovementListener {

        /**
         * This method will be invoked when user press and hold
         * finger on the [TextView]
         *
         * @param linkText Text which contains link on which user presses.
         * @param linkType Type of the link can be one of [LinkType] enumeration
         */
        fun onLinkClicked(linkText: String, linkType: LinkType)
    }


    init {
        mGestureDetector = GestureDetector(context, SimpleOnGestureListener())
    }

    override fun onTouchEvent(widget: TextView, buffer: Spannable, event: MotionEvent): Boolean {

        mWidget = widget
        mBuffer = buffer
        mGestureDetector.onTouchEvent(event)

        return super.onTouchEvent(widget, buffer, event)
    }

    /**
     * Detects various gestures and events.
     * Notify users when a particular motion event has occurred.
     */
    internal inner class SimpleOnGestureListener : GestureDetector.SimpleOnGestureListener() {

        override fun onSingleTapConfirmed(event: MotionEvent): Boolean {
            // Notified when tap occurs.
            val linkText = getLinkText(mWidget!!, mBuffer!!, event)

            var linkType = LinkType.NONE

            if (Patterns.PHONE.matcher(linkText).matches()) {
                linkType = LinkType.PHONE
            } else if (Patterns.WEB_URL.matcher(linkText).matches()) {
                linkType = LinkType.WEB_URL
            } else if (Patterns.EMAIL_ADDRESS.matcher(linkText).matches()) {
                linkType = LinkType.EMAIL_ADDRESS
            }

            if (mListener != null && linkType != LinkType.NONE) {
                mListener.onLinkClicked(linkText, linkType)
            }

            return false
        }

        private fun getLinkText(widget: TextView, buffer: Spannable, event: MotionEvent): String {

            var x = event.x.toInt()
            var y = event.y.toInt()

            x -= widget.totalPaddingLeft
            y -= widget.totalPaddingTop

            x += widget.scrollX
            y += widget.scrollY

            val layout = widget.layout
            val line = layout.getLineForVertical(y)
            val off = layout.getOffsetForHorizontal(line, x.toFloat())

            val link = buffer.getSpans(off, off, ClickableSpan::class.java)

            return if (link.isNotEmpty()) {
                buffer.subSequence(buffer.getSpanStart(link[0]),
                        buffer.getSpanEnd(link[0])).toString()
            } else ""

        }
    }
}
