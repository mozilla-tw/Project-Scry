package org.mozilla.scryer.filemonitor

import android.content.Context
import android.database.Cursor
import android.provider.MediaStore
import com.crashlytics.android.Crashlytics
import org.mozilla.scryer.persistence.CollectionModel
import org.mozilla.scryer.persistence.ScreenshotModel
import java.io.File

class ScreenshotFetcher {
    private val supportExt = listOf("jpg", "png")

    fun fetchScreenshots(context: Context): List<ScreenshotModel> {
        val folders = getFolders(context)
        val screenshots = mutableListOf<ScreenshotModel>()
        folders.forEach { folderPath ->
            screenshots.addAll(fetchScreenshots(folderPath))
        }
        return screenshots
    }

    private fun getFolders(context: Context): List<String> {
        val uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val columns = arrayOf(MediaStore.Images.Media.DATA, MediaStore.Images.ImageColumns.BUCKET_DISPLAY_NAME)
        val selection = "${MediaStore.Images.ImageColumns.BUCKET_ID} IS NOT NULL) GROUP BY (${MediaStore.Images.ImageColumns.BUCKET_ID}"
        val results = mutableListOf<String>()

        try {
            context.contentResolver.query(uri,
                    columns,
                    selection,
                    null,
                    null
            ).use {
                val cursor = it ?: return@use
                while (cursor.moveToNext()) {
                    val index = cursor.getColumnIndex(MediaStore.Images.Media.DATA)
                    if (index < 0) {
                        continue
                    }

                    // whether getString() throws exception is not defined and depends on implementation,
                    // so here simply catch the exception and ignore fail cases
                    val path = getCursorStringOrEmpty(cursor, index)
                    if (path.contains("screenshot", true)) {
                        val folder = File(path).parent?.trimEnd(File.separatorChar) ?: continue
                        results.add(folder)
                    }
                }
            }
        } catch (e: SecurityException) {
            // TODO: It's unclear what king of permission is needed from the log on Crashlytics,
            // maybe there're some others permissions that are needed on some devices (e.g. Redmi 5 Plus)
            // Permission Denial: reading com.android.providers.media.MediaProvider uri
            // content://media/external/images/media from pid=4453, uid=1410264 requires null,
            // or grantUriPermission()
            Crashlytics.logException(e)
        }

        return results
    }

    private fun getCursorStringOrEmpty(cursor: Cursor, idx: Int): String {
        return try {
            cursor.getString(idx).trim()
        } catch (e: Exception) {
            ""
        }
    }

    private fun fetchScreenshots(dirPath: String): List<ScreenshotModel> {
        val results = mutableListOf<ScreenshotModel>()

        File(dirPath).listFiles()?.filter { file ->
            val fileName = file.name.toLowerCase()
            supportExt.any { fileName.endsWith(it) }

        }?.forEach {
            val model = ScreenshotModel(it.absolutePath, it.lastModified(),
                    CollectionModel.UNCATEGORIZED)
            results.add(model)
        }

        return results
    }
}