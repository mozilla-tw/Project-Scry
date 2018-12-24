/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.scryer.detailpage

import android.graphics.drawable.Drawable
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import org.mozilla.scryer.persistence.ScreenshotModel

class DetailPageAdapter : androidx.viewpager.widget.PagerAdapter() {

    var screenshots = listOf<ScreenshotModel>()
    var itemCallback: ItemCallback? = null

    override fun getCount(): Int {
        return screenshots.size
    }

    override fun instantiateItem(container: ViewGroup, position: Int): Any {
        val imageView = ImageView(container.context)
        val item = screenshots[position]
        val path = item.absolutePath
        Glide.with(container.context)
                .load(path)
                .listener(object : RequestListener<Drawable> {
                    override fun onLoadFailed(e: GlideException?, model: Any, target: Target<Drawable>,
                                              isFirstResource: Boolean): Boolean {
                        itemCallback?.onItemLoaded(item)
                        return false
                    }

                    override fun onResourceReady(resource: Drawable, model: Any, target: Target<Drawable>,
                                                 dataSource: DataSource,
                                                 isFirstResource: Boolean): Boolean {
                        itemCallback?.onItemLoaded(item)
                        return false
                    }
                })
                .into(imageView)
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
}
