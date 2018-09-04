package org.mozilla.scryer

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Bundle
import android.support.v4.content.ContextCompat
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.GridLayoutManager
import android.support.v7.widget.RecyclerView
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView

class SvgViewerActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_svg_viewer)

        val recyclerView = findViewById<RecyclerView>(R.id.recycler_view)
        recyclerView.layoutManager = GridLayoutManager(this, 3, GridLayoutManager.VERTICAL, false)
        recyclerView.adapter = IconAdapter()

        recyclerView.addItemDecoration(object : RecyclerView.ItemDecoration() {
            private val paint = Paint()

            init {
                paint.color = Color.BLACK
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 1f
            }

            override fun onDrawOver(c: Canvas, parent: RecyclerView, state: RecyclerView.State) {
                (0 until parent.childCount).forEach {
                    val child = parent.getChildAt(it)
                    c.drawRect(child.left.toFloat(),
                            child.top.toFloat(),
                            child.right.toFloat(),
                            child.bottom.toFloat(), paint)
                }
            }
        })


    }

    private class IconAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        val iconIds = listOf(
                R.drawable.debug_broken_svg,
                R.drawable.add,
                R.drawable.back,
                R.drawable.capture,
                R.drawable.check,
                R.drawable.close_large,
                R.drawable.close_small,
                R.drawable.error,
                R.drawable.fab_ocr,
                R.drawable.more,
                R.drawable.move,
                R.drawable.search,
                R.drawable.share,
                R.drawable.viewmore,
                R.drawable.image_emptyfolder,
                R.drawable.image_noaccess
        )

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val frameLayout = FrameLayout(parent.context)
            val view = ImageView(parent.context)
            view.scaleType = ImageView.ScaleType.CENTER_INSIDE
            frameLayout.addView(view, ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT))

            var color = Color.WHITE
            val holder = object : RecyclerView.ViewHolder(frameLayout) {}
            frameLayout.setOnClickListener {
                holder.itemView.setBackgroundColor(if (color == Color.WHITE) {
                    color = Color.BLACK
                    color
                } else {
                    color = Color.WHITE
                    color
                })
            }
//            frameLayout.viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
//                override fun onPreDraw(): Boolean {
//                    frameLayout.viewTreeObserver.removeOnPreDrawListener(this)
//                    val params = frameLayout.layoutParams
//                    params.height = frameLayout.measuredWidth
//                    frameLayout.layoutParams = params
//                    return false
//                }
//            })
            return object : RecyclerView.ViewHolder(frameLayout) {}
        }

        override fun getItemCount(): Int {
            return iconIds.size
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val iconId = iconIds[position]
            holder.itemView.setBackgroundColor(if (iconId == R.drawable.debug_broken_svg) Color.RED else Color.TRANSPARENT)
            ((holder.itemView as ViewGroup).getChildAt(0) as ImageView)
                    .setImageDrawable(ContextCompat.getDrawable(holder.itemView.context, iconIds[position]))
        }
    }
}
