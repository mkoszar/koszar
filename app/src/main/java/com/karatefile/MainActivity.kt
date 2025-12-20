package com.karatefile

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            KarateFileApp()
        }
    }
}

@Composable
private fun KarateFileApp(viewModel: FileIndexViewModel = viewModel()) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val permissionsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        val granted = result.values.any { it }
        viewModel.onPermissionResult(granted)
    }

    LaunchedEffect(Unit) {
        val granted = requiredPermissions().any { permission ->
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        }
        viewModel.onPermissionResult(granted)
        viewModel.refreshIfNeeded()
    }

    MaterialTheme {
        Scaffold(
            topBar = {
                TopAppBar(title = { Text(text = "Karate File") })
            },
        ) { padding ->
            MainContent(
                state = state,
                modifier = Modifier.padding(padding),
                onRequestPermission = {
                    permissionsLauncher.launch(requiredPermissions())
                },
                onRefresh = { viewModel.refresh(force = true) },
            )
        }
    }
}

@Composable
private fun MainContent(
    state: FileIndexState,
    modifier: Modifier = Modifier,
    onRequestPermission: () -> Unit,
    onRefresh: () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        if (!state.permissionGranted) {
            PermissionCard(onRequestPermission = onRequestPermission)
            return
        }

        var selectedTab by rememberSaveable { mutableStateOf(FileCategory.Recent) }
        val tabs = FileCategory.values().toList()
        TabRow(selectedTabIndex = tabs.indexOf(selectedTab)) {
            tabs.forEach { category ->
                Tab(
                    selected = selectedTab == category,
                    onClick = { selectedTab = category },
                    text = { Text(text = stringResource(id = category.label)) },
                )
            }
        }

        val categoryData = state.categories[selectedTab]
        var query by rememberSaveable { mutableStateOf("") }

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text(text = stringResource(id = R.string.search_placeholder)) },
            modifier = Modifier
                .padding(top = 12.dp, bottom = 16.dp)
                .fillMaxWidth(),
            singleLine = true,
        )

        if (state.isLoading) {
            Text(text = "Indeksowanie...", style = MaterialTheme.typography.bodyMedium)
            return
        }

        if (state.errorMessage != null) {
            Text(
                text = state.errorMessage ?: "",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
            Button(onClick = onRefresh, modifier = Modifier.padding(top = 8.dp)) {
                Text(text = "Spróbuj ponownie")
            }
            return
        }

        if (categoryData == null) {
            Text(text = "Brak danych", style = MaterialTheme.typography.bodyMedium)
            return
        }

        val filteredFiles = categoryData.files.filter {
            query.isBlank() || it.displayName.contains(query, ignoreCase = true) ||
                it.mimeType.contains(query, ignoreCase = true)
        }

        StatsRow(fileCount = filteredFiles.size, totalSize = filteredFiles.sumOf { it.size })
        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

        LazyColumn(
            contentPadding = PaddingValues(bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (categoryData.folders.isNotEmpty()) {
                item {
                    Text(
                        text = stringResource(id = R.string.folder_section),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                items(categoryData.folders) { folder ->
                    FolderRow(folder)
                }
                item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }
            }

            item {
                Text(
                    text = stringResource(id = R.string.files_section),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            items(filteredFiles) { file ->
                FileRow(file)
            }
        }
    }
}

@Composable
private fun StatsRow(fileCount: Int, totalSize: Long) {
    val sizeLabel = formatBytes(totalSize)
    RowWithSpacing {
        AssistChip(
            onClick = {},
            label = { Text(text = stringResource(id = R.string.stats_files, fileCount)) },
            enabled = false,
        )
        AssistChip(
            onClick = {},
            label = { Text(text = stringResource(id = R.string.stats_size, sizeLabel)) },
            enabled = false,
            colors = AssistChipDefaults.assistChipColors(
                labelColor = MaterialTheme.colorScheme.primary,
            ),
        )
    }
}

@Composable
private fun RowWithSpacing(content: @Composable () -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        content()
    }
}

@Composable
private fun FileRow(file: FileEntry) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        ListItem(
            leadingContent = {
                Icon(imageVector = Icons.Filled.InsertDriveFile, contentDescription = null)
            },
            headlineContent = {
                Text(text = file.displayName, maxLines = 1, overflow = TextOverflow.Ellipsis)
            },
            supportingContent = {
                Text(text = "${file.relativePath} • ${formatDate(file.dateAdded)}")
            },
            trailingContent = {
                Text(text = formatBytes(file.size))
            },
        )
    }
}

@Composable
private fun FolderRow(folder: FolderInfo) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        ListItem(
            leadingContent = {
                Icon(imageVector = Icons.Filled.Folder, contentDescription = null)
            },
            headlineContent = {
                Text(text = folder.displayName, maxLines = 1, overflow = TextOverflow.Ellipsis)
            },
            supportingContent = {
                Text(text = "${folder.fileCount} plików • ${formatDate(folder.latestDate)}")
            },
        )
    }
}

@Composable
private fun PermissionCard(onRequestPermission: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxSize(),
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(id = R.string.permission_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(text = stringResource(id = R.string.permission_body))
            Button(onClick = onRequestPermission) {
                Text(text = stringResource(id = R.string.permission_button))
            }
        }
    }
}

private fun requiredPermissions(): Array<String> {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        arrayOf(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VIDEO,
            Manifest.permission.READ_MEDIA_AUDIO,
        )
    } else {
        arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }
}

private fun formatDate(timestampSeconds: Long): String {
    val formatter = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
    val date = Date(timestampSeconds * 1000)
    return formatter.format(date)
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
    val value = bytes / Math.pow(1024.0, digitGroups.toDouble())
    return "${DecimalFormat("#,##0.#").format(value)} ${units[digitGroups]}"
}
