package org.mozilla.scryer.detailpage

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.hardware.camera2.CameraCharacteristics
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import org.mozilla.scryer.BuildConfig
import java.util.*

class GraphicOverlay(context: Context, attrs: AttributeSet) : View(context, attrs) {

    companion object {
        private val COLOR_BACKGROUND = Color.parseColor("#b3000000")
    }

    private val lock = Any()
    private var previewWidth: Int = 0
    private var widthScaleFactor = 1.0f
    private var previewHeight: Int = 0
    private var heightScaleFactor = 1.0f
    private var facing = CameraCharacteristics.LENS_FACING_BACK
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

        /**
         * Returns the application context of the app.
         */
        val applicationContext: Context
            get() = overlay.context.applicationContext

        /**
         * Draw the graphic on the supplied canvas. Drawing should use the following methods to convert
         * to view coordinates for the graphics that are drawn:
         *
         *
         *
         *  1. [Graphic.scaleX] and [Graphic.scaleY] adjust the size of the
         * supplied value from the preview scale to the view scale.
         *  1. [Graphic.translateX] and [Graphic.translateY] adjust the
         * coordinate from the preview's coordinate system to the view coordinate system.
         *
         *
         * @param canvas drawing canvas
         */
        abstract fun draw(canvas: Canvas)

        /**
         * Adjusts a horizontal value of the supplied value from the preview scale to the view scale.
         */
        fun scaleX(horizontal: Float): Float {
            return horizontal * overlay.widthScaleFactor
        }

        /**
         * Adjusts a vertical value of the supplied value from the preview scale to the view scale.
         */
        fun scaleY(vertical: Float): Float {
            return vertical * overlay.heightScaleFactor
        }

        /**
         * Adjusts the x coordinate from the preview's coordinate system to the view coordinate system.
         */
        fun translateX(x: Float): Float {
            return if (overlay.facing == CameraCharacteristics.LENS_FACING_FRONT) {
                overlay.width - scaleX(x)
            } else {
                scaleX(x)
            }
        }

        /**
         * Adjusts the y coordinate from the preview's coordinate system to the view coordinate system.
         */
        fun translateY(y: Float): Float {
            return scaleY(y)
        }

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
     * Sets the camera attributes for size and facing direction, which informs how to transform image
     * coordinates later.
     */
    fun setCameraInfo(previewWidth: Int, previewHeight: Int, facing: Int) {
        synchronized(lock) {
            this.previewWidth = previewWidth
            this.previewHeight = previewHeight
            this.facing = facing
        }
        postInvalidate()
    }

    /**
     * Draws the overlay with its associated graphic objects.
     */
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        synchronized(lock) {
            if (previewWidth != 0 && previewHeight != 0) {
                widthScaleFactor = canvas.width.toFloat() / previewWidth.toFloat()
                heightScaleFactor = canvas.height.toFloat() / previewHeight.toFloat()
            }

            canvas.drawColor(COLOR_BACKGROUND)

            for (graphic in graphics) {
                graphic.draw(canvas)
            }

            debugPaint?.let {
                canvas.drawCircle(touchX.toFloat(), touchY.toFloat(), 20f, debugPaint)
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (BuildConfig.DEBUG) {
            touchX = event.x.toInt()
            touchY = event.y.toInt()
            postInvalidate()
        }
        return super.onTouchEvent(event)
    }
}

