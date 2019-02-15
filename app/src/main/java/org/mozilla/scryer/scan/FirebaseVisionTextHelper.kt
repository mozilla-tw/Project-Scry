/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.scryer.scan

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.google.firebase.ml.vision.FirebaseVision
import com.google.firebase.ml.vision.common.FirebaseVisionImage
import com.google.firebase.ml.vision.text.FirebaseVisionText
import kotlinx.coroutines.experimental.Dispatchers
import kotlinx.coroutines.experimental.isActive
import kotlinx.coroutines.experimental.withContext
import mozilla.components.support.base.log.Log
import org.mozilla.scryer.ScryerApplication
import org.mozilla.scryer.persistence.ScreenshotContentModel
import org.mozilla.scryer.persistence.ScreenshotModel
import kotlin.coroutines.experimental.suspendCoroutine

class FirebaseVisionTextHelper {
    companion object {
        private const val TAG = "FirebaseVisionTextHelper"

        /** Cancellable scan **/
        suspend fun scan(
                updateListener: suspend (((model: ScreenshotModel, index: Int, total: Int) -> Unit))
        ) = withContext(Dispatchers.IO) {

            val list = ScryerApplication.getScreenshotRepository().getScreenshotList().sortedByDescending {
                it.lastModified
            }

            val remains = list.filter {
                ScryerApplication.getScreenshotRepository().getContentText(it) == null
            }

            if (remains.isEmpty()) {
                return@withContext
            }

            remains.forEachIndexed { index, model ->
                if (!isActive) {
                    Log.log(tag = TAG, message = "scan interrupted")
                    return@withContext
                }

                updateListener.invoke(model, index, remains.size)
                Log.log(tag = TAG, message = "progress: ${index + 1}/${remains.size}")
            }
            Log.log(tag = TAG, message = "scan finished")
        }

        suspend fun scanAndSave(updateListener: ((index: Int, total: Int) -> Unit)? = null) {
            scan { model, index, total ->
                writeContentTextToDb(model, extractText((model)))
                updateListener?.invoke(index, total)
            }
        }

        suspend fun extractText(screenshot: ScreenshotModel): String {
            val decoded = try {
                BitmapFactory.decodeFile(screenshot.absolutePath)
            } catch (e: Error) {
                return ""
            }

            return extractText(decoded).text
        }

        suspend fun extractText(selectedImage: Bitmap): FirebaseVisionText = suspendCoroutine { cont ->
            val image = FirebaseVisionImage.fromBitmap(selectedImage)
            val detector = FirebaseVision.getInstance().onDeviceTextRecognizer
            detector.processImage(image)
                    .addOnSuccessListener { texts ->
                        cont.resume(texts)
                    }
                    .addOnFailureListener { exception ->
                        cont.resumeWithException(exception)
                    }
        }

        suspend fun writeContentTextToDb(
                screenshot: ScreenshotModel,
                contentText: String
        ) = withContext(Dispatchers.IO) {
            val model = ScreenshotContentModel(screenshot.id, contentText)
            ScryerApplication.getScreenshotRepository().updateScreenshotContent(model)
        }
    }
}
