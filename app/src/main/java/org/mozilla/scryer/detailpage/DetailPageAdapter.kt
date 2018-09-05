/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.scryer.detailpage

import android.support.v4.view.PagerAdapter
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import com.bumptech.glide.Glide
import org.mozilla.scryer.persistence.ScreenshotModel

class DetailPageAdapter : PagerAdapter() {

    var screenshots = listOf<ScreenshotModel>()
    var onItemClickListener: ((item: ScreenshotModel) -> Unit)? = null

    override fun getCount(): Int {
        return screenshots.size
    }

    override fun instantiateItem(container: ViewGroup, position: Int): Any {
        val imageView = ImageView(container.context)
        val path = screenshots[position].absolutePath
        Glide.with(container.context).load(path).into(imageView)
        container.addView(imageView)
        imageView.setOnClickListener {
            onItemClickListener?.invoke(screenshots[position])
        }
        return imageView
    }

    override fun destroyItem(container: ViewGroup, position: Int, obj: Any) {
        container.removeView(obj as View)
    }

    override fun isViewFromObject(view: View, obj: Any): Boolean {
        return view == obj
    }
}
