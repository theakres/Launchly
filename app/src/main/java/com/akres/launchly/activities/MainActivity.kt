package com.akres.launchly.activities

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.InstallMobile
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.pm.PackageInfoCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.akres.launchly.broadcast_receivers.PackageStatusReceiver
import com.akres.launchly.screens.ConfirmationDialog
import com.akres.launchly.screens.OnboardingScreen
import com.akres.launchly.screens.SettingsDialog
import com.akres.launchly.screens.VersionSelectorDialog
import com.akres.launchly.ui.theme.LaunchlyTheme
import com.akres.launchly.utils.ActionButton
import com.akres.launchly.utils.Architecture
import com.akres.launchly.utils.AvailableVersionsViewModel
import com.akres.launchly.utils.DownloadInfo
import com.akres.launchly.utils.DownloadTracker
import com.akres.launchly.utils.Downloader
import com.akres.launchly.utils.DropdownActionMenu
import com.akres.launchly.utils.GooglePlayClient
import com.akres.launchly.utils.GooglePlayClient.getApks
import com.akres.launchly.utils.InfoText
import com.akres.launchly.utils.MINECRAFT_PACKAGE
import com.akres.launchly.utils.SavedVersion
import com.akres.launchly.utils.SavedVersionsViewModel
import com.akres.launchly.utils.SessionResult
import com.akres.launchly.utils.Storage
import com.akres.launchly.utils.VersionDatabase
import com.akres.launchly.utils.VersionStatus
import com.akres.launchly.utils.formatBytes
import com.akres.launchly.utils.formatEta
import com.akres.launchly.utils.formatSpeed
import com.akres.launchly.utils.getInstalledPackageInfo
import com.akres.launchly.utils.installPackage
import com.akres.launchly.utils.uninstallPackage
import com.dzeio.crashhandler.CrashHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.util.UUID
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        CrashHandler
            .Builder()
            .withContext(this)
            .build()
            .setup()

        if (Storage.hasStorageAccess(this)) {
            Thread {
                Storage.migrateLegacyVersions(this)
                if (!DownloadTracker.hasActiveDownloads()) {
                    Storage.rootDir().walk().forEach {
                        if (it.isFile && it.name.endsWith(".temp")) {
                            it.delete()
                        }
                    }
                }
            }.start()
        }

        val sharedPreferences = getSharedPreferences("onboarding", MODE_PRIVATE)
        val accountPreferences = getSharedPreferences("accountData", Context.MODE_PRIVATE)
        val onboardingComplete = sharedPreferences.getBoolean("onboarding_complete", false)
        if (onboardingComplete && !Storage.hasStorageAccess(this)) {
            Storage.requestStorageAccess(this)
        }
        setContent {
            LaunchlyTheme {
                val navController = rememberNavController()
                NavHost(
                    navController = navController,
                    startDestination = if (onboardingComplete) "main" else "onboarding",
                ) {
                    composable("onboarding") {
                        if (!onboardingComplete) {
                            val removes = mutableListOf<String>()
                            if (packageManager.canRequestPackageInstalls()) {
                                removes.add("Permissions")
                            }
                            if (!accountPreferences.getString("accountEmail", null).isNullOrEmpty()) {
                                removes.add("SignIn")
                            }
                            if (!sharedPreferences.getBoolean("first_run", true)) {
                                removes.add("Start")
                            }
                            OnboardingScreen(
                                onFinish = {
                                    sharedPreferences
                                        .edit()
                                        .putBoolean("onboarding_complete", true)
                                        .apply()
                                    sharedPreferences.edit().putBoolean("first_run", false).apply()
                                    navController.navigate("main") {
                                        popUpTo("onboarding") { inclusive = true }
                                    }
                                },
                                removes = removes,
                            )
                        } else {
                            navController.navigate("main") {
                                popUpTo("onboarding") { inclusive = true }
                            }
                        }
                    }
                    composable("main") {
                        MainScreen(navController)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(navHostController: NavHostController) {
    val context = LocalContext.current
    val versionDatabase = VersionDatabase(context)
    val savedVersionsViewModel: SavedVersionsViewModel = viewModel()
    LaunchedEffect(Unit) {
        savedVersionsViewModel.reloadSavedVersions(context)
    }

    val downloadedVersions = savedVersionsViewModel.downloadedVersions
    val showVersionSelector = remember { mutableStateOf(false) }
    val showSettings = remember { mutableStateOf(false) }
    val selectedIds = remember { mutableStateListOf<String>() }
    val removingIds = remember { mutableStateListOf<String>() }
    val showDeleteConfirm = remember { mutableStateOf(false) }
    val selectionMode = selectedIds.isNotEmpty()

    BackHandler(enabled = selectionMode) { selectedIds.clear() }

    val versionsViewModels = remember {
        Build.SUPPORTED_ABIS
            .filter { Architecture.fromString(it) != Architecture.UNKNOWN }
            .map { AvailableVersionsViewModel(it) }
    }
    var userProfileInfo by remember { mutableStateOf(listOf<String?>()) }

    LaunchedEffect(Unit) {
        userProfileInfo = GooglePlayClient.getUserProfileInfo(context)
    }

    Scaffold(
        floatingActionButton = {
            AnimatedVisibility(
                visible = !selectionMode,
                enter = fadeIn(tween(200)) + scaleIn(tween(200), initialScale = 0.8f),
                exit = fadeOut(tween(150)) + scaleOut(tween(150), targetScale = 0.8f),
            ) {
                ExtendedFloatingActionButton(
                    { Text("Add Version") },
                    { Icon(Icons.Default.Add, "Add") },
                    { showVersionSelector.value = true },
                )
            }
        },
        topBar = {
            TopAppBar(
                title = {
                    Crossfade(
                        targetState = if (selectionMode) "${selectedIds.size} selected" else "Launchly",
                        animationSpec = tween(200),
                        label = "title",
                    ) { Text(it) }
                },
                navigationIcon = {
                    AnimatedVisibility(
                        visible = selectionMode,
                        enter = fadeIn(tween(200)) + expandHorizontally(tween(200)),
                        exit = fadeOut(tween(150)) + shrinkHorizontally(tween(150)),
                    ) {
                        IconButton(onClick = { selectedIds.clear() }) {
                            Icon(Icons.Default.Close, "Cancel selection")
                        }
                    }
                },
                actions = {
                    Crossfade(
                        targetState = selectionMode,
                        animationSpec = tween(200),
                        label = "actions",
                    ) { selecting ->
                        if (selecting) {
                            IconButton(onClick = { showDeleteConfirm.value = true }) {
                                Icon(Icons.Default.Delete, "Delete")
                            }
                        } else {
                            IconButton(onClick = { showSettings.value = true }) {
                                Icon(Icons.Default.Settings, "Settings")
                            }
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        var isRefreshing by remember { mutableStateOf(false) }
        val coroutineScope = rememberCoroutineScope()

        SavedVersionsList(
            items = downloadedVersions,
            isRefreshing = isRefreshing,
            onRefresh = {
                isRefreshing = true
                savedVersionsViewModel.reloadSavedVersions(context)
                coroutineScope.launch {
                    delay(500)
                    isRefreshing = false
                }
            },
            selectedIds = selectedIds,
            removingIds = removingIds,
            onToggleSelect = { id ->
                if (id in selectedIds) selectedIds.remove(id) else selectedIds.add(id)
            },
            onRemoveVersion = {
                removingIds.remove(it)
                DownloadTracker.cancel(context, it)
                versionDatabase.removeVersion(it)
                val fileToDelete = Storage.versionDir(it)
                if (fileToDelete.exists()) fileToDelete.deleteRecursively()
                downloadedVersions.remove(downloadedVersions.find { version -> version.installationId == it })
            },
            modifier = Modifier.padding(innerPadding),
        )

        VersionSelectorDialog(
            versionsViewModels = versionsViewModels,
            showDialog = showVersionSelector.value,
            addedVersions = downloadedVersions.map { "${it.versionCode}|${it.architecture.arch}" }.toSet(),
            onConfirmation = { versions ->
                showVersionSelector.value = false
                val now = System.currentTimeMillis()
                val existing = downloadedVersions.map { "${it.versionCode}|${it.architecture.arch}" }.toMutableSet()
                versions.filter { existing.add("${it.code}|${it.architecture.arch}") }.forEachIndexed { index, version ->
                    val newVersion =
                        SavedVersion(
                            UUID.randomUUID().toString(),
                            version.name,
                            version.code.toString(),
                            version.name,
                            version.architecture,
                            VersionStatus.NOT_DOWNLOADED,
                            now + index,
                        )
                    versionDatabase.insertVersion(newVersion)
                    downloadedVersions.add(newVersion)
                }
            },
        )
        ConfirmationDialog(
            title = if (selectedIds.size > 1) "Remove versions" else "Remove version",
            description =
                if (selectedIds.size > 1) {
                    "Are you sure you want to remove ${selectedIds.size} versions?"
                } else {
                    "Are you sure you want to remove this version?"
                },
            onConfirmation = {
                removingIds.addAll(selectedIds.filter { it !in removingIds })
                selectedIds.clear()
                showDeleteConfirm.value = false
            },
            showDialog = showDeleteConfirm.value,
            onDismiss = { showDeleteConfirm.value = false },
            confirmText = "Remove",
        )
        SettingsDialog(
            showDialog = showSettings.value,
            navHostController = navHostController,
            userProfileInfo = userProfileInfo,
            onDismissRequest = { showSettings.value = false },
        )
    }
}

private val client = OkHttpClient()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavedVersionsList(
    items: SnapshotStateList<SavedVersion>,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onRemoveVersion: (String) -> Unit,
    selectedIds: List<String>,
    removingIds: List<String>,
    onToggleSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val savedVersionsViewModel: SavedVersionsViewModel = viewModel()
    val context = LocalContext.current
    val isUpdating = remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        try {
            getInstalledPackageInfo(context, MINECRAFT_PACKAGE)
        } catch (_: Exception) {
            items.forEach {
                if (it.status == VersionStatus.INSTALLED) {
                    persistVersionUpdate(context, it.copy(status = VersionStatus.NOT_INSTALLED))
                    savedVersionsViewModel.reloadSavedVersions(context)
                }
            }
        }
    }

    val packageStateReceiver =
        remember {
            PackageStatusReceiver { packageName ->
                if (packageName == MINECRAFT_PACKAGE && !isUpdating.value) {
                    items.forEach {
                        if (it.status == VersionStatus.INSTALLED) {
                            persistVersionUpdate(context, it.copy(status = VersionStatus.NOT_INSTALLED))
                        }
                    }
                    savedVersionsViewModel.reloadSavedVersions(context)
                }
            }
        }

    DisposableEffect(Unit) {
        val filter =
            IntentFilter().apply {
                addAction(Intent.ACTION_PACKAGE_REMOVED)
                addDataScheme("package")
            }
        context.registerReceiver(packageStateReceiver, filter)
        onDispose {
            context.unregisterReceiver(packageStateReceiver)
        }
    }

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        modifier = modifier,
    ) {
        LazyColumn(Modifier.fillMaxSize()) {
            items(items.reversed(), key = { it.installationId }) {
                VersionCard(
                    it,
                    onRemoveVersion,
                    isUpdating = isUpdating,
                    selected = it.installationId in selectedIds,
                    selectionMode = selectedIds.isNotEmpty(),
                    isRemoving = it.installationId in removingIds,
                    onToggleSelect = { onToggleSelect(it.installationId) },
                ) { status ->
                    if (status == VersionStatus.INSTALLED) {
                        items.forEach { version ->
                            if (version.installationId != it.installationId && version.status == VersionStatus.INSTALLED) {
                                persistVersionUpdate(context, version.copy(status = VersionStatus.NOT_INSTALLED))
                            }
                        }
                    }
                    persistVersionUpdate(
                        context,
                        it.copy(status = status),
                        savedVersionsViewModel,
                    )
                }
            }
        }
    }
}

private fun persistVersionUpdate(
    context: Context,
    savedVersion: SavedVersion,
    savedVersionsViewModel: SavedVersionsViewModel = SavedVersionsViewModel(),
    downloadedVersions: SnapshotStateList<SavedVersion> = savedVersionsViewModel.downloadedVersions,
) {
    VersionDatabase(context).updateVersion(savedVersion)
    val index = savedVersionsViewModel.indexOfVersion(savedVersion.installationId)
    if (index != -1) {
        downloadedVersions[index] = savedVersion
    }
}

@Composable
fun VersionCard(
    savedVersion: SavedVersion,
    onRemoveVersion: (String) -> Unit = {},
    isUpdating: MutableState<Boolean> = mutableStateOf(false),
    selected: Boolean = false,
    selectionMode: Boolean = false,
    isRemoving: Boolean = false,
    onToggleSelect: () -> Unit = {},
    updateStatus: (VersionStatus) -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val downloadId = savedVersion.installationId
    val downloadInfo = DownloadTracker.info[downloadId]
    LaunchedEffect(downloadId) {
        if (savedVersion.status == VersionStatus.DOWNLOADING && !DownloadTracker.isDownloading(downloadId)) {
            updateStatus(VersionStatus.NOT_DOWNLOADED)
        }
    }
    val installProgress = remember { mutableIntStateOf(-1) }
    val isLoading = remember { mutableStateOf(false) }
    val animatedAlpha by animateFloatAsState(
        targetValue = if (isRemoving) 0f else 1f,
        label = "alpha",
        finishedListener = {
            if (isRemoving) onRemoveVersion(savedVersion.installationId)
        },
    )

    VersionCardContent(
        savedVersion = savedVersion,
        animatedAlpha = animatedAlpha,
        isRemoving = isRemoving,
        selected = selected,
        selectionMode = selectionMode,
        onToggleSelect = onToggleSelect,
        downloadInfo = downloadInfo,
        installProgress = installProgress.intValue,
        isLoading = isLoading.value,
        onVersionAction = { action ->
            scope.launch {
                withContext(Dispatchers.Main) {
                    handleVersionAction(
                        action,
                        savedVersion,
                        context,
                        installProgress,
                        isLoading,
                        isUpdating,
                        scope,
                        updateStatus,
                    )
                }
            }
        },
    )
}

@Composable
fun VersionCardContent(
    savedVersion: SavedVersion,
    animatedAlpha: Float,
    isRemoving: Boolean,
    selected: Boolean,
    selectionMode: Boolean,
    onToggleSelect: () -> Unit,
    downloadInfo: DownloadInfo?,
    isLoading: Boolean,
    installProgress: Int,
    onVersionAction: (VersionAction) -> Unit,
) {
    val context = LocalContext.current
    val currentSelectionMode by rememberUpdatedState(selectionMode)
    val currentToggle by rememberUpdatedState(onToggleSelect)
    val isDownloading = savedVersion.status == VersionStatus.DOWNLOADING
    val cardColor by animateColorAsState(
        targetValue =
            if (selected) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow
            },
        animationSpec = tween(220),
        label = "cardColor",
    )
    val cardScale by animateFloatAsState(
        targetValue = if (selected) 0.97f else 1f,
        animationSpec = tween(220),
        label = "cardScale",
    )

    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .layout { measurable, constraints ->
                    val placeable = measurable.measure(constraints)
                    val scale = if (isRemoving) animatedAlpha else 1f
                    layout(placeable.width, (placeable.height * scale).roundToInt()) {
                        placeable.placeRelative(0, 0)
                    }
                }.alpha(if (isRemoving) animatedAlpha else 1f)
                .padding(8.dp),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    scaleX = cardScale
                    scaleY = cardScale
                }.clip(MaterialTheme.shapes.medium)
                .background(cardColor)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { if (currentSelectionMode) currentToggle() },
                        onLongPress = { currentToggle() },
                    )
                },
        ) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier =
                        Modifier
                            .padding(start = 8.dp)
                            .fillMaxHeight()
                            .weight(1f),
                ) {
                    Text(
                        savedVersion.versionName,
                        style = MaterialTheme.typography.titleLarge,
                    )
                    InfoText("Code: ${savedVersion.versionCode}")
                    InfoText("Architecture: ${savedVersion.architecture.arch}")

                    val lastInfo = remember { mutableStateOf(DownloadInfo()) }
                    if (downloadInfo != null) lastInfo.value = downloadInfo
                    val reveal by animateFloatAsState(
                        targetValue = if (isDownloading) 1f else 0f,
                        animationSpec = tween(durationMillis = 400),
                        label = "downloadReveal",
                    )
                    val animatedFraction by animateFloatAsState(
                        targetValue = lastInfo.value.fraction,
                        animationSpec = tween(durationMillis = 900, easing = LinearEasing),
                        label = "downloadFraction",
                    )
                    if (isDownloading || reveal > 0f) {
                        val dl = lastInfo.value
                        Column(
                            Modifier
                                .clipToBounds()
                                .layout { measurable, constraints ->
                                    val placeable = measurable.measure(constraints)
                                    layout(placeable.width, (placeable.height * reveal).roundToInt()) {
                                        placeable.placeRelative(0, 0)
                                    }
                                }.alpha(reveal),
                        ) {
                            InfoText("Speed: ${formatSpeed(dl.speed)} • ETA ${formatEta(dl.etaSeconds)}")
                            InfoText("${formatBytes(dl.downloaded)} / ${formatBytes(dl.total)} (${(dl.fraction * 100).toInt()}%)")
                            LinearProgressIndicator(
                                progress = { animatedFraction },
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(top = 6.dp, end = 8.dp),
                            )
                        }
                    }
                    if (installProgress > -1) InfoText("Installing... $installProgress%")
                }

                VersionActionButton(
                    savedVersion,
                    Modifier
                        .padding(8.dp)
                        .size(48.dp)
                        .aspectRatio(1f),
                    isLoading,
                    !selectionMode,
                    context,
                    onVersionAction,
                )
            }
        }
    }
}

@Composable
private fun VersionActionButton(
    savedVersion: SavedVersion,
    buttonModifier: Modifier,
    isLoading: Boolean,
    enabled: Boolean,
    context: Context,
    onVersionAction: (VersionAction) -> Unit,
) {
    when (savedVersion.status) {
        VersionStatus.INSTALLED -> {
            ActionButton(
                icon = Icons.Default.PlayArrow,
                backgroundColor = MaterialTheme.colorScheme.primary,
                modifier = buttonModifier,
                loading = isLoading,
                enabled = enabled,
                onClick = {
                    context.startActivity(
                        context.packageManager.getLaunchIntentForPackage(MINECRAFT_PACKAGE),
                    )
                },
            )
        }

        VersionStatus.NOT_INSTALLED -> {
            ActionButton(
                icon = Icons.Default.InstallMobile,
                backgroundColor = MaterialTheme.colorScheme.primary,
                modifier = buttonModifier,
                loading = isLoading,
                enabled = enabled,
                onClick = { onVersionAction(VersionAction.Install) },
            )
        }

        VersionStatus.NOT_DOWNLOADED -> {
            ActionButton(
                icon = Icons.Default.Download,
                backgroundColor = MaterialTheme.colorScheme.primary,
                modifier = buttonModifier,
                loading = isLoading,
                enabled = enabled,
                onClick = { onVersionAction(VersionAction.StartDownload) },
            )
        }

        VersionStatus.DOWNLOADING -> {
            val expanded = remember { mutableStateOf(false) }
            ActionButton(
                icon = Icons.Default.MoreHoriz,
                backgroundColor = MaterialTheme.colorScheme.inversePrimary,
                modifier = buttonModifier,
                loading = isLoading,
                enabled = enabled,
                onClick = { expanded.value = true },
            ) {
                DropdownActionMenu(
                    menu =
                        listOf(
                            "Cancel" to { onVersionAction(VersionAction.CancelDownload) },
                        ),
                    expanded = expanded,
                )
            }
        }
    }
}

suspend fun handleVersionAction(
    action: VersionAction,
    savedVersion: SavedVersion,
    context: Context,
    installProgress: MutableIntState,
    isLoading: MutableState<Boolean>,
    isUpdating: MutableState<Boolean>,
    coroutineScope: CoroutineScope,
    updateStatus: (VersionStatus) -> Unit,
) {
    when (action) {
        is VersionAction.StartDownload -> {
            if (!Storage.hasStorageAccess(context)) {
                Toast.makeText(context, "Allow access to all files to save versions to /Launchly", Toast.LENGTH_LONG).show()
                Storage.requestStorageAccess(context)
                return
            }
            var totalSize = 0L
            try {
                val urlToFileNameMap =
                    getApks(savedVersion.versionCode.toInt(), context).associate { apk ->
                        totalSize += apk.size
                        apk.name to apk.url
                    }
                val id = savedVersion.installationId
                val appContext = context.applicationContext
                DownloadTracker.startTracking(appContext, id, savedVersion.name, totalSize)
                updateStatus(VersionStatus.DOWNLOADING)
                DownloadTracker.createDownloader(id, client).startDownload(
                    urlToFileNameMap,
                    Storage.versionDir(savedVersion.installationId),
                ) { status ->
                    when (status) {
                        is Downloader.DownloadStatus.Progress -> {
                            DownloadTracker.updateProgress(appContext, id, status.downloadedSize, status.speed)
                        }

                        is Downloader.DownloadStatus.AllFinished -> {
                            DownloadTracker.finishTracking(appContext, id)
                            updateStatus(VersionStatus.NOT_INSTALLED)
                        }

                        is Downloader.DownloadStatus.Failure -> {
                            DownloadTracker.finishTracking(appContext, id)
                            updateStatus(VersionStatus.NOT_DOWNLOADED)
                        }

                        is Downloader.DownloadStatus.Cancelled -> {
                            DownloadTracker.finishTracking(appContext, id)
                            updateStatus(VersionStatus.NOT_DOWNLOADED)
                        }

                        is Downloader.DownloadStatus.FileDownloaded -> Unit
                    }
                }
            } catch (e: Exception) {
                DownloadTracker.finishTracking(context, savedVersion.installationId)
                Toast.makeText(context, "This account didn't buy Minecraft, is this the right Account?", Toast.LENGTH_SHORT).show()
            }
        }

        is VersionAction.CancelDownload -> {
            DownloadTracker.getDownloader(savedVersion.installationId)?.cancelAll()
        }

        is VersionAction.Install -> {
            isLoading.value = true
            val apks =
                Storage.versionDir(savedVersion.installationId).listFiles { file ->
                    file.isFile &&
                        file.name.endsWith(".apk")
                }
            if (apks != null && apks.isNotEmpty()) {
                val onResult: (SessionResult) -> Unit = {
                    when (it) {
                        is SessionResult.Progress -> {
                            installProgress.intValue = it.progress
                        }

                        is SessionResult.Success -> {
                            installProgress.intValue = -1
                            isLoading.value = false
                            updateStatus(VersionStatus.INSTALLED)

                            coroutineScope.launch {
                                delay(500)
                                isUpdating.value = false
                            }
                        }

                        is SessionResult.Failure -> {
                            installProgress.intValue = -1
                            isLoading.value = false
                            isUpdating.value = false
                        }

                        is SessionResult.Cancelled -> {
                            installProgress.intValue = -1
                            isLoading.value = false
                            isUpdating.value = false
                        }
                    }
                }
                try {
                    val versionCode = getInstalledPackageInfo(context, MINECRAFT_PACKAGE).let { PackageInfoCompat.getLongVersionCode(it) }
                    if (versionCode > savedVersion.versionCode.toLong()) {
                        uninstallPackage(context, MINECRAFT_PACKAGE) { status ->
                            if (status == SessionResult.Success) {
                                installPackage(context, apks, onResult)
                            } else {
                                isLoading.value = false
                            }
                        }
                    } else {
                        isUpdating.value = true
                        installPackage(context, apks, onResult)
                    }
                } catch (e: Exception) {
                    installPackage(context, apks, onResult)
                }
            }
        }
    }
}

sealed class VersionAction {
    data object StartDownload : VersionAction()

    data object CancelDownload : VersionAction()

    data object Install : VersionAction()
}
