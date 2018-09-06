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
import android.os.Bundle
import android.support.design.widget.BottomSheetBehavior
import android.support.design.widget.FloatingActionButton
import android.support.v4.app.ActivityOptionsCompat
import android.support.v4.view.ViewCompat
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.Toolbar
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import com.google.firebase.ml.vision.FirebaseVision
import com.google.firebase.ml.vision.common.FirebaseVisionImage
import com.google.firebase.ml.vision.text.FirebaseVisionText
import kotlinx.android.synthetic.main.activity_detail_page.*
import kotlinx.coroutines.experimental.CommonPool
import kotlinx.coroutines.experimental.DefaultDispatcher
import kotlinx.coroutines.experimental.android.UI
import kotlinx.coroutines.experimental.launch
import kotlinx.coroutines.experimental.withContext
import org.mozilla.scryer.BuildConfig
import org.mozilla.scryer.R
import org.mozilla.scryer.persistence.ScreenshotModel
import org.mozilla.scryer.viewmodel.ScreenshotViewModel
import kotlin.coroutines.experimental.suspendCoroutine

class DetailPageActivity : AppCompatActivity() {

    companion object Launcher {
        private const val EXTRA_SCREENSHOT_ID = "screenshot_id"
        private const val EXTRA_COLLECTION_ID = "collection_id"

        private const val SUPPORT_SLIDE = true

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

    private lateinit var screenshots: List<ScreenshotModel>
    private val loadingViewController: LoadingViewGroup by lazy {
        LoadingViewGroup(this)
    }

    private var isRecognizing = false
    private var isTextMode = false
    private var isEnterTransitionPostponed = true

    private val itemCallback = object : DetailPageAdapter.ItemCallback {
        override fun onItemClicked(item: ScreenshotModel) {
            toggleActionBar()
        }

        override fun onItemLoaded(item: ScreenshotModel) {
            if (isEnterTransitionPostponed && item.id == screenshotId) {
                isEnterTransitionPostponed = false
                supportStartPostponedEnterTransition()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_detail_page)
        supportPostponeEnterTransition()

        initActionBar()
        initViewPager()
        initFab()
        initPanel()

        updateUI()
    }

    private fun initActionBar() {
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)

        toolbar.setNavigationOnClickListener {
            onBackPressed()
        }

        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowTitleEnabled(false)
        }
    }

    private fun initViewPager() {
        launch(UI) {
            screenshots = getScreenshots().sortedByDescending { it.lastModified }
            view_pager.adapter = DetailPageAdapter().apply {
                this.screenshots = this@DetailPageActivity.screenshots
                this.itemCallback = this@DetailPageActivity.itemCallback
            }
            view_pager.currentItem = screenshots.indexOfFirst { it.id == screenshotId }
        }
    }

    private fun initFab() {
        val fab = findViewById<FloatingActionButton>(R.id.text_mode_fab)

        val fabListener = View.OnClickListener {
            when (it.id) {
                R.id.text_mode_fab -> {
                    isRecognizing = true
                    startRecognition()
                }

                R.id.cancel_fab -> {
                    isRecognizing = false
                    updateUI()
                }
            }
        }

        fab.setOnClickListener(fabListener)
        cancel_fab.setOnClickListener(fabListener)
    }

    private fun initPanel() {
        BottomSheetBehavior.from(text_mode_panel_content)
                .setBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
                    override fun onSlide(bottomSheet: View, slideOffset: Float) {

                    }

                    override fun onStateChanged(bottomSheet: View, newState: Int) {
                        if (newState == BottomSheetBehavior.STATE_HIDDEN) {
                            isTextMode = false
                            updateUI()
                        }
                    }
                })
    }

    private fun startRecognition() {
        launch(UI) {
            updateUI()

            val result = withContext(CommonPool) {
                val path = (screenshots[view_pager.currentItem]).absolutePath
                val bitmap = BitmapFactory.decodeFile(path)
                bitmap?.let {
                    runTextRecognition(it)
                }
            }

            result?.let {
                if (isRecognizing) {
                    processTextRecognitionResult(it)
                    isTextMode = true
                    updateUI()
                }
            }

            isRecognizing = false
            updateUI()
        }
    }

    private fun updateUI() {
        if (isTextMode) {
            text_mode_panel.visibility = View.VISIBLE
            text_mode_background.visibility = View.VISIBLE
            BottomSheetBehavior.from(text_mode_panel_content).state = BottomSheetBehavior.STATE_COLLAPSED
            cancel_fab.visibility = View.INVISIBLE
            text_mode_fab.verticalScrollbarPosition = View.INVISIBLE
            loadingViewController.hide()

        } else {
            text_mode_panel.visibility = View.GONE
            text_mode_background.visibility = View.GONE
            if (isRecognizing) {
                loadingViewController.show()
                cancel_fab.visibility = View.VISIBLE
                text_mode_fab.visibility = View.INVISIBLE

            } else {
                loadingViewController.hide()
                cancel_fab.visibility = View.INVISIBLE
                text_mode_fab.visibility = View.VISIBLE
            }
        }
    }

    @Suppress("ConstantConditionIf")
    private suspend fun getScreenshots(): List<ScreenshotModel> = withContext(DefaultDispatcher) {
        if (SUPPORT_SLIDE) {
            srcCollectionId?.let { viewModel.getScreenshotList(listOf(it)) }
                    ?: viewModel.getScreenshotList()
        } else {
            viewModel.getScreenshot(screenshotId)?.let { listOf(it) } ?: emptyList()
        }
    }

    private fun toggleActionBar() {
        var isShowing = supportActionBar?.isShowing ?: false
        isShowing = !isShowing

        if (isShowing) {
            toolbar_background.visibility = View.VISIBLE
            supportActionBar?.show()
            window?.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)

        } else {
            toolbar_background.visibility = View.INVISIBLE
            supportActionBar?.hide()
            window?.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        }
    }

    private suspend fun runTextRecognition(selectedImage: Bitmap): FirebaseVisionText? = suspendCoroutine { cont ->
        val image = FirebaseVisionImage.fromBitmap(selectedImage)
        val detector = FirebaseVision.getInstance().visionTextDetector
        detector.detectInImage(image)
                .addOnSuccessListener { texts ->
                    cont.resume(texts)
                }
                .addOnFailureListener { _ ->
                    cont.resume(null)
                }
    }

    private fun processTextRecognitionResult(texts: FirebaseVisionText) {
        val blocks = texts.blocks.toMutableList().apply {  }
        blocks.sortBy { it.boundingBox?.centerY() }

        if (blocks.size == 0) {
            Toast.makeText(applicationContext, "No text found", Toast.LENGTH_SHORT).show()
            return
        }

        val builder = StringBuilder()

        mGraphicOverlay.clear()

        for (block in blocks) {
            val lines = block.lines.toMutableList()
            lines.sortBy { it.boundingBox?.centerY() }

            for (line in lines) {
                builder.append(line.text).append("\n")
            }

            builder.append("\n")

            if (BuildConfig.DEBUG) {
                val textGraphic = TextGraphic(mGraphicOverlay, block)
                mGraphicOverlay.add(textGraphic)
            }
        }

        text_mode_text.text = builder.toString()
    }
//
//    private fun showSystemUI() {
//        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
//                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
//                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
//    }
//
//    private fun hideSystemUI() {
//        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
//                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
//                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
//                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
//                View.SYSTEM_UI_FLAG_LOW_PROFILE or
//                View.SYSTEM_UI_FLAG_FULLSCREEN or
//                View.SYSTEM_UI_FLAG_IMMERSIVE
//    }

    private class LoadingViewGroup(private val activity: DetailPageActivity) {
        fun show() {
            activity.apply {
                loading_overlay.visibility = View.VISIBLE
                loading_progress.visibility = View.VISIBLE
                loading_text.visibility = View.VISIBLE
            }
        }

        fun hide() {
            activity.apply {
                loading_overlay.visibility = View.INVISIBLE
                loading_progress.visibility = View.INVISIBLE
                loading_text.visibility = View.INVISIBLE
            }
        }
    }
}
