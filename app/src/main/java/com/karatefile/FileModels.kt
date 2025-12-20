package com.karatefile

import androidx.annotation.StringRes

enum class FileCategory(@StringRes val label: Int) {
    Recent(R.string.tab_recent),
    Photos(R.string.tab_photos),
    Documents(R.string.tab_documents),
    Videos(R.string.tab_videos),
    Music(R.string.tab_music),
}

data class FileEntry(
    val id: Long,
    val displayName: String,
    val mimeType: String,
    val relativePath: String,
    val size: Long,
    val dateAdded: Long,
)

data class FolderInfo(
    val displayName: String,
    val fileCount: Int,
    val latestDate: Long,
)

data class CategoryData(
    val files: List<FileEntry>,
    val folders: List<FolderInfo>,
)
