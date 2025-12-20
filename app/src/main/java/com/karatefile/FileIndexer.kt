package com.karatefile

import android.content.ContentResolver
import android.os.Build
import android.provider.MediaStore

class FileIndexer(private val contentResolver: ContentResolver) {
    fun loadCategory(category: FileCategory, limit: Int = 100): CategoryData {
        val selectionData = when (category) {
            FileCategory.Recent -> SelectionData(null, emptyArray())
            FileCategory.Photos -> SelectionData("mime_type LIKE ?", arrayOf("image/%"))
            FileCategory.Documents -> SelectionData(
                "(mime_type IS NULL OR (mime_type NOT LIKE ? AND mime_type NOT LIKE ? AND mime_type NOT LIKE ?))",
                arrayOf("image/%", "video/%", "audio/%"),
            )
            FileCategory.Videos -> SelectionData("mime_type LIKE ?", arrayOf("video/%"))
            FileCategory.Music -> SelectionData("mime_type LIKE ?", arrayOf("audio/%"))
        }

        val sortOrder = when (category) {
            FileCategory.Recent -> "date_added DESC"
            else -> "date_added DESC"
        }

        val files = queryFiles(selectionData, sortOrder, limit)
        val folders = summarizeFolders(files)
        return CategoryData(files = files, folders = folders)
    }

    private fun queryFiles(
        selectionData: SelectionData,
        sortOrder: String,
        limit: Int,
    ): List<FileEntry> {
        val projection = mutableListOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.MIME_TYPE,
            MediaStore.Files.FileColumns.SIZE,
            MediaStore.Files.FileColumns.DATE_ADDED,
            MediaStore.Files.FileColumns.RELATIVE_PATH,
        )

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            projection.add(MediaStore.Files.FileColumns.DATA)
        }

        val uri = MediaStore.Files.getContentUri("external")
        val selectionArgs = selectionData.args
        val selection = selectionData.selection
        val files = mutableListOf<FileEntry>()

        val finalSort = "$sortOrder LIMIT $limit"
        contentResolver.query(uri, projection.toTypedArray(), selection, selectionArgs, finalSort)
            ?.use { cursor ->
                val idIndex = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
                val nameIndex = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
                val mimeIndex = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MIME_TYPE)
                val sizeIndex = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)
                val dateIndex = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_ADDED)
                val pathIndex = cursor.getColumnIndex(MediaStore.Files.FileColumns.RELATIVE_PATH)
                val legacyPathIndex = cursor.getColumnIndex(MediaStore.Files.FileColumns.DATA)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idIndex)
                    val name = cursor.getString(nameIndex) ?: ""
                    val mime = cursor.getString(mimeIndex) ?: "unknown"
                    val size = cursor.getLong(sizeIndex)
                    val date = cursor.getLong(dateIndex)
                    val relative = if (pathIndex != -1) cursor.getString(pathIndex) else null
                    val legacyPath = if (legacyPathIndex != -1) cursor.getString(legacyPathIndex) else null
                    val displayPath = relative ?: legacyPath?.substringBeforeLast('/') ?: ""

                    files.add(
                        FileEntry(
                            id = id,
                            displayName = name,
                            mimeType = mime,
                            relativePath = displayPath,
                            size = size,
                            dateAdded = date,
                        ),
                    )
                }
            }

        return files
    }

    private fun summarizeFolders(files: List<FileEntry>): List<FolderInfo> {
        val folders = mutableMapOf<String, MutableList<FileEntry>>()
        for (file in files) {
            val key = file.relativePath.ifBlank { "Inne" }
            folders.getOrPut(key) { mutableListOf() }.add(file)
        }

        return folders.map { (path, entries) ->
            val latest = entries.maxOfOrNull { it.dateAdded } ?: 0L
            FolderInfo(
                displayName = path.trimEnd('/'),
                fileCount = entries.size,
                latestDate = latest,
            )
        }.sortedByDescending { it.latestDate }
    }

    private data class SelectionData(val selection: String?, val args: Array<String>)
}
