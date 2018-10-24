package org.mozilla.scryer.filemonitor

import android.content.Context
import android.database.Cursor
import android.provider.MediaStore
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

        context.contentResolver.query(uri,
                columns,
                selection,
                null,
                null
        ).use {
            val cursor = it ?: return@use
            while (cursor.moveToNext()) {
                val path = cursor.getString(cursor.getColumnIndex(MediaStore.Images.ImageColumns.DATA)).trim()
                if (path.contains("screenshot", true)) {
                    val folder = File(path).parent?.trimEnd(File.separatorChar) ?: continue
                    results.add(folder)
                }
            }
        }

        return results
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