/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.scryer.filemonitor

import android.content.Context
import android.database.ContentObserver
import android.database.Cursor
import android.net.Uri
import android.os.Handler
import android.provider.MediaStore
import kotlinx.coroutines.experimental.Dispatchers
import kotlinx.coroutines.experimental.GlobalScope
import kotlinx.coroutines.experimental.launch
import kotlinx.coroutines.experimental.withContext
import org.mozilla.scryer.capture.ScreenCaptureManager
import org.mozilla.scryer.permission.PermissionHelper
import java.io.File

class MediaProviderDelegate(private val context: Context, private val handler: Handler?) : FileMonitorDelegate {

    private var observer: ContentObserver? = null

    override fun startMonitor(listener: FileMonitor.ChangeListener) {
        observer = object : ContentObserver(handler) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                uri ?: return

                if (!PermissionHelper.hasStoragePermission(context)) {
                    return
                }

                if (!uri.toString().contains(MediaStore.Images.Media.EXTERNAL_CONTENT_URI.toString())) {
                    return
                }

                context.contentResolver.query(uri,
                        arrayOf(MediaStore.Images.Media.DISPLAY_NAME,
                                MediaStore.Images.Media.DATA,
                                MediaStore.Images.Media.DATE_ADDED),
                        null,
                        null,
                        MediaStore.Images.Media.DATE_ADDED + " DESC"
                ).use {
                    val cursor = it ?: return@use
                    if (cursor.moveToFirst()) {
                        notifyChangeAsync(cursor, listener)
                    }
                }
            }

        }.apply {
            context.contentResolver.registerContentObserver(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    true,
                    this)
        }
    }

    private fun notifyChangeAsync(cursor: Cursor, listener: FileMonitor.ChangeListener) {
        val path = cursor.getString(cursor.getColumnIndex(MediaStore.Images.Media.DATA))
        val dateAdded = cursor.getLong(cursor.getColumnIndex(MediaStore.Images.Media.DATE_ADDED))
        val currentTime = System.currentTimeMillis() / 1000

        GlobalScope.launch {
            val isExtSupported = ScreenshotFetcher.isExtSupported(path)
            val isPotentialScreenshot = path.contains("screenshot", true)
            val isCapturedByFab = File(path).parentFile.name == ScreenCaptureManager.SCREENSHOT_DIR
            val isNewlyCaptured = Math.abs(currentTime - dateAdded) <= 10

            if (isExtSupported && isPotentialScreenshot && isNewlyCaptured && !isCapturedByFab) {
                withContext(Dispatchers.Main) {
                    listener.onChangeStart(path)
                    listener.onChangeFinish(path)
                }
            }
        }
    }

    override fun stopMonitor() {
        observer?.let {
            context.contentResolver.unregisterContentObserver(it)
        }
    }
}