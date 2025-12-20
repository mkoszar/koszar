package com.karatefile

data class FileIndexState(
    val permissionGranted: Boolean = false,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val categories: Map<FileCategory, CategoryData> = emptyMap(),
)
