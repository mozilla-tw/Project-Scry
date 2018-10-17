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
import android.graphics.Point
import android.graphics.RectF
import android.os.Bundle
import android.support.design.widget.BottomSheetBehavior
import android.support.design.widget.FloatingActionButton
import android.support.v4.app.ActivityOptionsCompat
import android.support.v4.content.ContextCompat
import android.support.v4.graphics.drawable.DrawableCompat
import android.support.v4.view.ViewCompat
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.Toolbar
import android.view.Menu
import android.view.MenuItem
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
import org.mozilla.scryer.collectionview.OnDeleteScreenshotListener
import org.mozilla.scryer.collectionview.showDeleteScreenshotDialog
import org.mozilla.scryer.collectionview.showScreenshotInfoDialog
import org.mozilla.scryer.collectionview.showShareScreenshotDialog
import org.mozilla.scryer.persistence.CollectionModel
import org.mozilla.scryer.persistence.ScreenshotModel
import org.mozilla.scryer.sortingpanel.SortingPanelActivity
import org.mozilla.scryer.telemetry.TelemetryWrapper
import org.mozilla.scryer.ui.ScryerToast
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

    private var shareMenu: MenuItem? = null
    private var moveToMenu: MenuItem? = null
    private var screenshotInfoMenu: MenuItem? = null
    private var deleteMenu: MenuItem? = null

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

    private val screenSize: RectF by lazy {
        val size = Point().apply {
            windowManager.defaultDisplay.getRealSize(this)
        }
        RectF(0f, 0f, size.x.toFloat(), size.y.toFloat())
    }

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

        TelemetryWrapper.showDetailPage()
    }

    override fun onBackPressed() {
        when {
            isTextMode -> {
                isTextMode = false
                updateUI()
            }

            supportActionBar?.isShowing != true -> {
                toggleActionBar()
            }

            else -> {
                super.onBackPressed()
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_detail, menu)

        if (menu != null) {
            shareMenu = menu.findItem(R.id.action_share)
            shareMenu?.let {
                val wrapped = DrawableCompat.wrap(it.icon).mutate()
                DrawableCompat.setTint(wrapped, ContextCompat.getColor(this, R.color.white))
            }

            moveToMenu = menu.findItem(R.id.action_move_to)
            screenshotInfoMenu = menu.findItem(R.id.action_screenshot_info)
            deleteMenu = menu.findItem(R.id.action_delete)
        }

        return true
    }

    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        when (item?.itemId) {
            R.id.action_share -> {
                showShareScreenshotDialog(this, screenshots[view_pager.currentItem])
                TelemetryWrapper.clickShareButtonInDetailPage()
            }
            R.id.action_move_to -> {
                startActivity(SortingPanelActivity.sortOldScreenshot(this, screenshots[view_pager.currentItem]))
            }
            R.id.action_screenshot_info -> {
                showScreenshotInfoDialog(this, screenshots[view_pager.currentItem])
            }
            R.id.action_delete -> {
                showDeleteScreenshotDialog(this, screenshots[view_pager.currentItem],
                        object : OnDeleteScreenshotListener {
                            override fun onDeleteScreenshot() {
                                finish()
                            }
                        })
            }
            else -> return super.onOptionsItemSelected(item)
        }
        return super.onOptionsItemSelected(item)
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
                    TelemetryWrapper.clickTextModeButton()
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
                runTextRecognition(screenshots[view_pager.currentItem])
            }

            if (result is Result.Success) {
                if (result is Result.WeiredImageSize) {
                    ScryerToast.makeText(this@DetailPageActivity,
                            getString(R.string.detail_ocr_error_edgecase),
                            Toast.LENGTH_SHORT).show()
                    TelemetryWrapper.showTextModeResult(TelemetryWrapper.Value.WEIRD_SIZE)
                } else {
                    TelemetryWrapper.showTextModeResult(TelemetryWrapper.Value.SUCCESS)
                }

                if (isRecognizing) {
                    processTextRecognitionResult(result.value)
                    isTextMode = true
                    updateUI()
                }

            } else if (result is Result.Failed) {
                ScryerToast.makeText(this@DetailPageActivity,
                        getString(R.string.detail_ocr_error_failed),
                        Toast.LENGTH_SHORT).show()

                TelemetryWrapper.showTextModeResult(TelemetryWrapper.Value.FAIL)
            }

            isRecognizing = false
            updateUI()
        }
    }

    private suspend fun runTextRecognition(screenshot: ScreenshotModel): Result {
        val decoded = BitmapFactory.decodeFile(screenshot.absolutePath)
        return decoded?.let { bitmap ->
            runTextRecognition(bitmap)?.let { result ->
                if (isValidSize(bitmap)) {
                    Result.Success(result)
                } else {
                    Result.WeiredImageSize(result,
                            "weird image size: ${bitmap.width}x${bitmap.height}")
                }

            } ?: Result.Failed("recognize failed")

        } ?: Result.Failed("invalid bitmap")
    }

    private fun isValidSize(bitmap: Bitmap): Boolean {
        return if (bitmap.width >= bitmap.height) {
            isValidLandscapeSize(bitmap)
        } else {
            isValidPortraitSize(bitmap)
        }
    }

    private fun isValidPortraitSize(bitmap: Bitmap): Boolean {
        val isWidthValid = bitmap.width <= 1.5f * screenSize.width()
        val isHeightValid = bitmap.height <= 2 * screenSize.height()
        return isWidthValid && isHeightValid
    }

    private fun isValidLandscapeSize(bitmap: Bitmap): Boolean {
        val isWidthValid = bitmap.width <= screenSize.height()
        val isHeightValid = bitmap.height <= 2 * screenSize.width()
        return isWidthValid && isHeightValid
    }

    private fun updateUI() {
        if (isTextMode) {
            updateLoadingViewVisibility(false)
            updateFabUI(true, false)
            updateTextModePanelVisibility(true)
            enableActionMenu(false)

        } else {
            updateLoadingViewVisibility(isRecognizing)
            updateFabUI(false, isRecognizing)
            updateTextModePanelVisibility(false)
            enableActionMenu(true)
        }
        updateNavigationIcon()
    }

    private fun updateLoadingViewVisibility(visible: Boolean) {
        if (visible) {
            loadingViewController.show()
        } else {
            loadingViewController.hide()
        }
    }

    private fun updateFabUI(isTextMode: Boolean, isLoading: Boolean) {
        when {
            isTextMode -> {
                cancel_fab.visibility = View.INVISIBLE
                text_mode_fab.verticalScrollbarPosition = View.INVISIBLE
            }

            isLoading -> {
                cancel_fab.visibility = View.VISIBLE
                text_mode_fab.visibility = View.INVISIBLE
            }

            else -> {
                cancel_fab.visibility = View.INVISIBLE
                text_mode_fab.visibility = View.VISIBLE
            }
        }
    }

    private fun updateTextModePanelVisibility(visible: Boolean) {
        val visibility = if (visible) {
            BottomSheetBehavior.from(text_mode_panel_content).state = BottomSheetBehavior.STATE_COLLAPSED
            View.VISIBLE
        } else {
            View.GONE
        }
        text_mode_panel.visibility = visibility
        text_mode_background.visibility = visibility
    }

    private fun enableActionMenu(enable: Boolean) {
        shareMenu?.isVisible = enable
        moveToMenu?.isVisible = enable
        screenshotInfoMenu?.isVisible = enable
        deleteMenu?.isVisible = enable
    }

    private fun updateNavigationIcon() {
        toolbar.navigationIcon = ContextCompat.getDrawable(this, if (isTextMode) {
            R.drawable.close_large
        } else {
            R.drawable.back
        })
    }

    @Suppress("ConstantConditionIf")
    private suspend fun getScreenshots(): List<ScreenshotModel> = withContext(DefaultDispatcher) {
        if (SUPPORT_SLIDE) {
            srcCollectionId?.let {
                val list = if (it == CollectionModel.CATEGORY_NONE) {
                    listOf(it, CollectionModel.UNCATEGORIZED)
                } else {
                    listOf(it)
                }
                viewModel.getScreenshotList(list)
            } ?: viewModel.getScreenshotList()
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
            text_mode_fab.visibility = View.VISIBLE
            cancel_fab.visibility = View.INVISIBLE

        } else {
            toolbar_background.visibility = View.INVISIBLE
            supportActionBar?.hide()
            window?.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
            text_mode_fab.visibility = View.INVISIBLE
            cancel_fab.visibility = View.INVISIBLE
        }
    }

    private suspend fun runTextRecognition(selectedImage: Bitmap): FirebaseVisionText? =
            suspendCoroutine { cont ->
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
        val blocks = texts.blocks.toMutableList().apply { }
        blocks.sortBy { it.boundingBox?.centerY() }

        if (blocks.size == 0) {
            Toast.makeText(applicationContext, "No text found", Toast.LENGTH_SHORT).show()
            ScryerToast.makeText(this, getString(R.string.detail_ocr_error_notext),
                    Toast.LENGTH_SHORT).show()
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

    sealed class Result {
        open class Success(val value: FirebaseVisionText) : Result()
        class WeiredImageSize(
                value: FirebaseVisionText,
                @Suppress("unused") val msg: String
        ) : Success(value)
        class Failed(@Suppress("unused") val msg: String) : Result()
    }

}
