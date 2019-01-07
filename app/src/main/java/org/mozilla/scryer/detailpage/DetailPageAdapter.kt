/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.scryer.detailpage

import android.graphics.Bitmap
import android.graphics.PointF
import android.graphics.drawable.Drawable
import android.view.View
import android.view.ViewGroup
import androidx.viewpager.widget.PagerAdapter
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.SimpleTarget
import com.bumptech.glide.request.transition.Transition
import com.davemorrissey.labs.subscaleview.ImageSource
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView
import org.mozilla.scryer.persistence.ScreenshotModel

class DetailPageAdapter : PagerAdapter() {

    var screenshots = listOf<ScreenshotModel>()
    var itemCallback: ItemCallback? = null
    var imageStateCallback: ImageStateCllabck? = null

    override fun getCount(): Int {
        return screenshots.size
    }

    override fun instantiateItem(container: ViewGroup, position: Int): Any {
        val imageView = object : SubsamplingScaleImageView(container.context) {
            override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
                super.onSizeChanged(w, h, oldw, oldh)
                resetScaleAndCenter()
            }
        }

        imageView.setOnStateChangedListener(object : SubsamplingScaleImageView.OnStateChangedListener {
            override fun onCenterChanged(newCenter: PointF, origin: Int) {}

            override fun onScaleChanged(newScale: Float, origin: Int) {
                imageStateCallback?.onScaleChanged(newScale == imageView.minScale)
            }
        })

        val item = screenshots[position]
        val path = item.absolutePath
        Glide.with(container.context)
                .asBitmap()
                .load(path)
                .into(object : SimpleTarget<Bitmap>() {
                    override fun onLoadFailed(errorDrawable: Drawable?) {
                        itemCallback?.onItemLoaded(item)
                    }

                    override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
                        imageView.setImage(ImageSource.bitmap(resource))
                        itemCallback?.onItemLoaded(item)
                    }
                })

        container.addView(imageView)
        imageView.setOnClickListener {
            itemCallback?.onItemClicked(screenshots[position])
        }
        return imageView
    }

    override fun destroyItem(container: ViewGroup, position: Int, obj: Any) {
        container.removeView(obj as View)
    }

    override fun isViewFromObject(view: View, obj: Any): Boolean {
        return view == obj
    }

    interface ItemCallback {
        fun onItemClicked(item: ScreenshotModel)
        fun onItemLoaded(item: ScreenshotModel)
    }

    interface ImageStateCllabck {
        fun onScaleChanged(scale: Boolean)
    }
}
