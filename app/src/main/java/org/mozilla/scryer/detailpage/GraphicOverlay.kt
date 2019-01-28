package org.mozilla.scryer.detailpage

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import org.mozilla.scryer.BuildConfig
import java.util.*

class GraphicOverlay(context: Context, attrs: AttributeSet) : View(context, attrs) {

    companion object {
        private val COLOR_OVERLAY = Color.parseColor("#b3000000")

        const val MODE_HIGHLIGHT = 0
        const val MODE_OVERLAY_HIGHLIGHT = 1
        const val DEFAULT_MODE = MODE_HIGHLIGHT
    }

    private val lock = Any()
    private val graphics = HashSet<Graphic>()

    private val debugPaint = if (BuildConfig.DEBUG) {
        Paint().apply {
            color = Color.RED
            style = Paint.Style.FILL_AND_STROKE
        }
    } else {
        null
    }
    private var touchX: Int = 0
    private var touchY: Int = 0

    var overlayColor: Int? = null
        set(value) {
            field = value
            postInvalidate()
        }

    var overlayMode: Int = DEFAULT_MODE
        set(value) {
            field = value
            invalidate()
        }

    private val isTouchable: Boolean
        get() {
            return overlayMode == MODE_OVERLAY_HIGHLIGHT
        }

    init {
        setLayerType(View.LAYER_TYPE_SOFTWARE, null)
        isClickable = true
    }

    /**
     * Base class for a custom graphics object to be rendered within the graphic overlay. Subclass
     * this and implement the [Graphic.draw] method to define the graphics element. Add
     * instances to the overlay using [GraphicOverlay.add].
     */
    abstract class Graphic(private val overlay: GraphicOverlay) {
        var scale = 1f
        var translationX = 0f
        var translationY = 0f

        /**
         * Draw the graphic on the supplied canvas. Drawing should use the following methods to convert
         * to view coordinates for the graphics that are drawn:
         *
         * @param canvas drawing canvas
         */
        abstract fun draw(canvas: Canvas)

        fun postInvalidate() {
            overlay.postInvalidate()
        }
    }

    /**
     * Removes all graphics from the overlay.
     */
    fun clear() {
        synchronized(lock) {
            graphics.clear()
        }
        postInvalidate()
    }

    /**
     * Adds a graphic to the overlay.
     */
    fun add(graphic: Graphic) {
        synchronized(lock) {
            graphics.add(graphic)
        }
        postInvalidate()
    }

    /**
     * Removes a graphic from the overlay.
     */
    fun remove(graphic: Graphic) {
        synchronized(lock) {
            graphics.remove(graphic)
        }
        postInvalidate()
    }

    /**
     * Draws the overlay with its associated graphic objects.
     */
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        synchronized(lock) {
            overlayColor?.let {
                canvas.drawColor(it)
            } ?: run {
                canvas.drawColor(if (overlayMode == MODE_OVERLAY_HIGHLIGHT) {
                    COLOR_OVERLAY
                } else {
                    Color.TRANSPARENT
                })
            }

            for (graphic in graphics) {
                graphic.draw(canvas)
            }

            debugPaint?.takeIf { isTouchable }?.let {
                canvas.drawCircle(touchX.toFloat(), touchY.toFloat(), 20f, debugPaint)
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isTouchable) {
            return false
        }

        if (BuildConfig.DEBUG) {
            touchX = event.x.toInt()
            touchY = event.y.toInt()
            postInvalidate()
        }
        return super.onTouchEvent(event)
    }

    override fun dispatchTouchEvent(event: MotionEvent?): Boolean {
        if (!isTouchable) {
            return false
        }
        return super.dispatchTouchEvent(event)
    }

    fun setBlocks(blocks: List<Graphic>, overlayMode: Int = DEFAULT_MODE) {
        this.overlayMode = overlayMode
        clear()
        blocks.forEach { add(it) }
    }
}

