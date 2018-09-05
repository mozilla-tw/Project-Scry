/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.scryer.detailpage

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.support.constraint.Group
import android.support.v4.app.ActivityOptionsCompat
import android.support.v4.view.ViewCompat
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.Toolbar
import android.view.MenuItem
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.Toast
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import com.google.firebase.ml.vision.FirebaseVision
import com.google.firebase.ml.vision.common.FirebaseVisionImage
import com.google.firebase.ml.vision.text.FirebaseVisionText
import kotlinx.coroutines.experimental.DefaultDispatcher
import kotlinx.coroutines.experimental.android.UI
import kotlinx.coroutines.experimental.launch
import kotlinx.coroutines.experimental.withContext
import org.mozilla.scryer.R
import org.mozilla.scryer.persistence.ScreenshotModel
import org.mozilla.scryer.viewmodel.ScreenshotViewModel
import java.io.File

class DetailPageActivity : AppCompatActivity() {

    companion object Launcher {
        private const val EXTRA_SCREENSHOT_ID = "screenshot_id"
        private const val EXTRA_COLLECTION_ID = "collection_id"

        fun showDetailPage(context: Context, screenshot: ScreenshotModel, srcView: View?,
                           collectionId: String? = null) {
            val intent = Intent(context, DetailPageActivity::class.java)
            val bundle = srcView?.let {
                val option = ActivityOptionsCompat.makeSceneTransitionAnimation(
                        context as Activity, srcView, ViewCompat.getTransitionName(it))
                option.toBundle()
            }
            intent.putExtra(EXTRA_SCREENSHOT_ID, screenshot.id)
            collectionId?.let {
                intent.putExtra(EXTRA_COLLECTION_ID, collectionId)
            }
            (context as AppCompatActivity).startActivity(intent, bundle)
        }
    }

    private val mGraphicOverlay: GraphicOverlay by lazy { findViewById<GraphicOverlay>(R.id.graphic_overlay) }
    private val toolbarGroup: Group by lazy { findViewById<Group>(R.id.toolbar_group) }

    /** Where did the user came from to this page **/
    private val srcCollectionId: String? by lazy {
        intent?.getStringExtra(EXTRA_COLLECTION_ID)
    }

    private val screenshotId: String by lazy {
        val id = intent?.getStringExtra(EXTRA_SCREENSHOT_ID)
        id ?: throw IllegalArgumentException("invalid screenshot id")
    }

    private val viewModel: ScreenshotViewModel by lazy {
        ScreenshotViewModel.get(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_detail_page)

        initActionBar()
        initViewPager()

        val imageView = findViewById<ImageView>(R.id.image_view)
        imageView.setOnClickListener {
            toggleActionBar()
        }

        val path = intent.getStringExtra("path")
        val bitmap = BitmapFactory.decodeFile(path)
        bitmap?.let {
            runTextRecognition(it)
        }

        supportPostponeEnterTransition()
        Glide.with(this).load(File(path).absolutePath).listener(object : RequestListener<Drawable> {
            override fun onResourceReady(resource: Drawable?, model: Any?, target: Target<Drawable>?, dataSource: DataSource?, isFirstResource: Boolean): Boolean {
                supportStartPostponedEnterTransition()
                return false
            }

            override fun onLoadFailed(e: GlideException?, model: Any?, target: Target<Drawable>?, isFirstResource: Boolean): Boolean {
                supportStartPostponedEnterTransition()
                return false
            }
        }).into(imageView)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun initActionBar() {
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        toolbar.setNavigationOnClickListener {
            onBackPressed()
        }

        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    private fun initViewPager() {
        launch(UI) {
            val screenshots = withContext(DefaultDispatcher) {
                srcCollectionId?.let {
                    viewModel.getScreenshotList(listOf(it))
                } ?: run {
                    viewModel.getScreenshotList()
                }
            }


        }
    }

    private fun toggleActionBar() {
        var isShowing = supportActionBar?.isShowing ?: false
        isShowing = !isShowing

        if (isShowing) {
            toolbarGroup.visibility = View.VISIBLE
            supportActionBar?.show()
            window?.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        } else {
            toolbarGroup.visibility = View.INVISIBLE
            supportActionBar?.hide()
            window?.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        }
    }

    private fun runTextRecognition(selectedImage: Bitmap) {
        val image = FirebaseVisionImage.fromBitmap(selectedImage)
        val detector = FirebaseVision.getInstance().visionTextDetector
        detector.detectInImage(image)
                .addOnSuccessListener { texts ->
                    processTextRecognitionResult(texts)
                }
                .addOnFailureListener { e ->
                    // Task failed with an exception
                    e.printStackTrace()
                }
    }

    private fun processTextRecognitionResult(texts: FirebaseVisionText) {
        val blocks = texts.blocks
        if (blocks.size == 0) {
            Toast.makeText(applicationContext, "No text found", Toast.LENGTH_SHORT).show()
            return
        }
        mGraphicOverlay.clear()
        for (i in blocks.indices) {
            val lines = blocks[i].lines
            for (j in lines.indices) {
                val elements = lines[j].elements
                for (k in elements.indices) {
                    val textGraphic = TextGraphic(mGraphicOverlay, elements[k])
                    mGraphicOverlay.add(textGraphic)

                }
            }
        }
    }
}
