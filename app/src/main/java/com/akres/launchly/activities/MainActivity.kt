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
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.activity.compose.BackHandler
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.animateColorAsState
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
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
import androidx.compose.material.icons.filled.Edit
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
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.layout
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.core.content.pm.PackageInfoCompat
import androidx.core.net.toUri
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.akres.launchly.broadcast_receivers.PackageStatusReceiver
import com.akres.launchly.screens.ConfirmationDialog
import com.akres.launchly.screens.Onboarding
import com.akres.launchly.screens.SettingsDialog
import com.akres.launchly.screens.VersionSelector
import com.akres.launchly.ui.theme.LaunchlyTheme
import com.akres.launchly.utils.ActionButton
import com.akres.launchly.utils.Architecture
import com.akres.launchly.utils.Category
import com.akres.launchly.utils.DownloadInfo
import com.akres.launchly.utils.DownloadTracker
import com.akres.launchly.utils.Downloader
import com.akres.launchly.utils.formatBytes
import com.akres.launchly.utils.formatEta
import com.akres.launchly.utils.formatSpeed
import com.akres.launchly.utils.DropDown
import com.akres.launchly.utils.GPlayAPI
import com.akres.launchly.utils.GPlayAPI.getApks
import com.akres.launchly.utils.Info
import com.akres.launchly.utils.SavedVersionData
import com.akres.launchly.utils.SessionResult
import com.akres.launchly.utils.Status
import com.akres.launchly.utils.VersionDatabaseHelper
import com.akres.launchly.utils.VersionDatabaseViewModel
import com.akres.launchly.utils.VersionsViewModel
import com.akres.launchly.utils.getPackageArch
import com.akres.launchly.utils.getPackageInfo
import com.akres.launchly.utils.getPackageInstallSource
import com.akres.launchly.utils.installPackage
import com.akres.launchly.utils.uninstallApp
import com.dzeio.crashhandler.CrashHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.io.File
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

        if (!DownloadTracker.hasActive()) {
            filesDir.walk().forEach {
                if (it.isFile && it.name.endsWith(".temp")) {
                    it.delete()
                }
            }
        }

        val sharedPreferences = getSharedPreferences("onboarding", MODE_PRIVATE)
        val accountPreferences = getSharedPreferences("accountData", Context.MODE_PRIVATE)
        val onboardingComplete = sharedPreferences.getBoolean("onboarding_complete", false)
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
                            Onboarding(
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
                        Main(navController)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Main(navHostController: NavHostController) {
    val context = LocalContext.current
    val versionDbHelper = VersionDatabaseHelper(context)
    val versionDbViewModel: VersionDatabaseViewModel = viewModel()
    LaunchedEffect(Unit) {
        versionDbViewModel.loadSavedVersions(context)
    }

    val downloadedVersions = versionDbViewModel.downloadedVersions
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
            .map { VersionsViewModel(it) }
    }
    var userProfileInfo by remember { mutableStateOf(listOf<String?>()) }

    LaunchedEffect(Unit) {
        userProfileInfo = GPlayAPI.getUserProfileInfo(context)
    }

    Scaffold(
        floatingActionButton = {
            if (!selectionMode) {
                ExtendedFloatingActionButton(
                    { Text("Add Version") },
                    { Icon(Icons.Default.Add, "Add") },
                    { showVersionSelector.value = true },
                )
            }
        },
        topBar = {
            TopAppBar(
                title = { Text(if (selectionMode) "${selectedIds.size} selected" else "Launchly") },
                navigationIcon = {
                    if (selectionMode) {
                        IconButton(onClick = { selectedIds.clear() }) {
                            Icon(Icons.Default.Close, "Cancel selection")
                        }
                    }
                },
                actions = {
                    if (selectionMode) {
                        IconButton(onClick = { showDeleteConfirm.value = true }) {
                            Icon(Icons.Default.Delete, "Delete")
                        }
                    } else {
                        IconButton(onClick = {
                            showSettings.value = true
                        }) { Icon(Icons.Default.Settings, "Settings") }
                    }
                },
            )
        },
    ) { innerPadding ->
        var isRefreshing by remember { mutableStateOf(false) }
        val coroutineScope = rememberCoroutineScope()

        DownloadedVersions(
            items = downloadedVersions,
            isRefreshing = isRefreshing,
            onRefresh = {
                isRefreshing = true
                versionDbViewModel.loadSavedVersions(context)
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
                versionDbHelper.removeVersion(it)
                val fileToDelete = File(context.filesDir, "versions/$it/")
                if (fileToDelete.exists()) fileToDelete.deleteRecursively()
                downloadedVersions.remove(downloadedVersions.find { version -> version.installationId == it })
            },
            modifier = Modifier.padding(innerPadding),
        )

        VersionSelector(
            versionsViewModels = versionsViewModels,
            showDialog = showVersionSelector.value,
            onConfirmation = { versions ->
                showVersionSelector.value = false
                val now = System.currentTimeMillis()
                versions.forEachIndexed { index, version ->
                    val newVersion =
                        SavedVersionData(
                            UUID.randomUUID().toString(),
                            version.name,
                            version.code.toString(),
                            version.name,
                            version.architecture,
                            Status.NOT_DOWNLOADED,
                            listOf(),
                            now + index,
                        )
                    versionDbHelper.addVersion(newVersion)
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
fun DownloadedVersions(
    items: SnapshotStateList<SavedVersionData>,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onRemoveVersion: (String) -> Unit,
    selectedIds: List<String>,
    removingIds: List<String>,
    onToggleSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val versionDbViewModel: VersionDatabaseViewModel = viewModel()
    val context = LocalContext.current
    val isUpdating = remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        try {
            getPackageInfo(context, "com.mojang.minecraftpe")
        } catch (_: Exception) {
            items.forEach {
                if (it.status == Status.INSTALLED) {
                    updateInfo(context, it.copy(status = Status.NOT_INSTALLED))
                    versionDbViewModel.loadSavedVersions(context)
                }
            }
        }
    }

    val packageStateReceiver =
        remember {
            PackageStatusReceiver { packageName, status ->
                if (packageName == "com.mojang.minecraftpe" && status == "removed" && !isUpdating.value) {
                    items.forEach {
                        if (it.status == Status.INSTALLED) {
                            updateInfo(context, it.copy(status = Status.NOT_INSTALLED))
                        }
                    }
                    versionDbViewModel.loadSavedVersions(context)
                }
            }
        }

    DisposableEffect(Unit) {
        val filter =
            IntentFilter().apply {
                addAction(Intent.ACTION_PACKAGE_REMOVED)
                addAction(Intent.ACTION_PACKAGE_ADDED)
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
                VersionItem(
                    it,
                    onRemoveVersion,
                    isUpdating = isUpdating,
                    selected = it.installationId in selectedIds,
                    selectionMode = selectedIds.isNotEmpty(),
                    isRemoving = it.installationId in removingIds,
                    onToggleSelect = { onToggleSelect(it.installationId) },
                ) { status ->
                    if (status == Status.INSTALLED) {
                        items.forEach { version ->
                            if (version.installationId != it.installationId && version.status == Status.INSTALLED) {
                                updateInfo(context, version.copy(status = Status.NOT_INSTALLED))
                            }
                        }
                    }
                    updateInfo(
                        context,
                        it.copy(status = status),
                        versionDbViewModel,
                    )
                }
            }
        }
    }
}

private fun updateInfo(
    context: Context,
    savedVersionData: SavedVersionData,
    versionDbViewModel: VersionDatabaseViewModel = VersionDatabaseViewModel(),
    downloadedVersions: SnapshotStateList<SavedVersionData> = versionDbViewModel.downloadedVersions,
) {
    VersionDatabaseHelper(context).editVersion(savedVersionData)
    val index = versionDbViewModel.getIndexById(savedVersionData.installationId)
    if (index != -1) {
        downloadedVersions[index] = savedVersionData
    }
}

@Composable
fun VersionItem(
    savedVersionData: SavedVersionData,
    onRemoveVersion: (String) -> Unit = {},
    isLaunchly: Boolean = true,
    packageName: String = "com.mojang.minecraftpe",
    isUpdating: MutableState<Boolean> = mutableStateOf(false),
    selected: Boolean = false,
    selectionMode: Boolean = false,
    isRemoving: Boolean = false,
    onToggleSelect: () -> Unit = {},
    updateStatus: (Status) -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val downloadId = savedVersionData.installationId
    val downloadInfo = DownloadTracker.info[downloadId]
    LaunchedEffect(downloadId) {

        if (isLaunchly && savedVersionData.status == Status.DOWNLOADING && !DownloadTracker.isActive(downloadId)) {
            updateStatus(Status.NOT_DOWNLOADED)
        }
    }
    val installProgress = remember { mutableIntStateOf(-1) }
    val isLoading = remember { mutableStateOf(false) }
    val animatedAlpha by animateFloatAsState(
        targetValue = if (isRemoving) 0f else 1f,
        label = "alpha",
        finishedListener = {
            if (isRemoving) onRemoveVersion(savedVersionData.installationId)
        },
    )

    VersionItemLayout(
        savedVersionData = savedVersionData,
        isLaunchly = isLaunchly,
        animatedAlpha = animatedAlpha,
        isRemoving = isRemoving,
        selected = selected,
        selectionMode = selectionMode,
        onToggleSelect = onToggleSelect,
        downloadInfo = downloadInfo,
        installProgress = installProgress.intValue,
        isLoading = isLoading.value,
        packageName = packageName,
        onVersionAction = { action ->
            scope.launch {
                withContext(Dispatchers.Main) {
                    handleVersionAction(
                        action,
                        savedVersionData,
                        context,
                        installProgress,
                        isLoading,
                        packageName,
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
fun VersionItemLayout(
    savedVersionData: SavedVersionData,
    isLaunchly: Boolean,
    animatedAlpha: Float,
    isRemoving: Boolean,
    selected: Boolean,
    selectionMode: Boolean,
    onToggleSelect: () -> Unit,
    downloadInfo: DownloadInfo?,
    isLoading: Boolean,
    installProgress: Int,
    packageName: String,
    onVersionAction: (VersionAction) -> Unit,
) {
    val context = LocalContext.current
    val currentSelectionMode by rememberUpdatedState(selectionMode)
    val currentToggle by rememberUpdatedState(onToggleSelect)
    val isDownloading = savedVersionData.status == Status.DOWNLOADING
    val cardColor by animateColorAsState(
        targetValue =
            if (selected) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow
            },
        label = "cardColor",
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
                .clip(MaterialTheme.shapes.medium)
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
                        savedVersionData.versionName,
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Info("Code: ${savedVersionData.versionCode}")
                    Info("Architecture: ${savedVersionData.architecture.arch}")
                    if (savedVersionData.patches.isNotEmpty()) Info("Patches: ${savedVersionData.patches.joinToString()}")

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
                            Info("Speed: ${formatSpeed(dl.speed)} • ETA ${formatEta(dl.etaSeconds)}")
                            Info("${formatBytes(dl.downloaded)} / ${formatBytes(dl.total)} (${(dl.fraction * 100).toInt()}%)")
                            LinearProgressIndicator(
                                progress = { animatedFraction },
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(top = 6.dp, end = 8.dp),
                            )
                        }
                    }
                    if (installProgress > -1) Info("Installing... $installProgress%")
                }

                GetActionButton(
                    savedVersionData,
                    Modifier
                        .padding(8.dp)
                        .size(48.dp)
                        .aspectRatio(1f),
                    isLoading,
                    !selectionMode,
                    context,
                    packageName,
                    onVersionAction,
                )
            }
            if (selected) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = "Selected",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier =
                        Modifier
                            .align(Alignment.TopEnd)
                            .padding(10.dp)
                            .size(20.dp),
                )
            }
        }
    }
}

@Composable
private fun GetActionButton(
    savedVersionData: SavedVersionData,
    buttonModifier: Modifier,
    isLoading: Boolean,
    enabled: Boolean,
    context: Context,
    packageName: String,
    onVersionAction: (VersionAction) -> Unit,
) {
    when (savedVersionData.status) {
        Status.INSTALLED -> {
            ActionButton(
                icon = Icons.Default.PlayArrow,
                backgroundColor = MaterialTheme.colorScheme.primary,
                modifier = buttonModifier,
                loading = isLoading,
                enabled = enabled,
                onClick = {
                    context.startActivity(
                        context.packageManager.getLaunchIntentForPackage(
                            packageName,
                        ),
                    )
                },
            )
        }

        Status.NOT_INSTALLED -> {
            ActionButton(
                icon = Icons.Default.InstallMobile,
                backgroundColor = MaterialTheme.colorScheme.primary,
                modifier = buttonModifier,
                loading = isLoading,
                enabled = enabled,
                onClick = { onVersionAction(VersionAction.Install) },
            )
        }

        Status.NOT_DOWNLOADED -> {
            ActionButton(
                icon = Icons.Default.Download,
                backgroundColor = MaterialTheme.colorScheme.primary,
                modifier = buttonModifier,
                loading = isLoading,
                enabled = enabled,
                onClick = { onVersionAction(VersionAction.StartDownload) },
            )
        }

        Status.DOWNLOADING -> {
            val expanded = remember { mutableStateOf(false) }
            ActionButton(
                icon = Icons.Default.MoreHoriz,
                backgroundColor = MaterialTheme.colorScheme.inversePrimary,
                modifier = buttonModifier,
                loading = isLoading,
                enabled = enabled,
                onClick = { expanded.value = true },
            ) {
                DropDown(
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
    savedVersionData: SavedVersionData,
    context: Context,
    installProgress: MutableIntState,
    isLoading: MutableState<Boolean>,
    packageName: String,
    isUpdating: MutableState<Boolean>,
    coroutineScope: CoroutineScope,
    updateStatus: (Status) -> Unit,
) {
    when (action) {
        is VersionAction.StartDownload -> {
            var totalSize = 0L
            try {
                val urlToFileNameMap =
                    getApks(savedVersionData.versionCode.toInt(), context).associate { apk ->
                        totalSize += apk.size
                        apk.name to apk.url
                    }
                val id = savedVersionData.installationId
                val appContext = context.applicationContext
                DownloadTracker.begin(appContext, id, savedVersionData.name, totalSize)
                updateStatus(Status.DOWNLOADING)
                DownloadTracker.newDownloader(id, client).startDownload(
                    urlToFileNameMap,
                    File("${context.filesDir}/versions/${savedVersionData.installationId}"),
                ) { status ->
                    when (status) {
                        is Downloader.DownloadStatus.OnProgress -> {
                            DownloadTracker.update(appContext, id, status.downloadedSize, status.speed)
                        }

                        is Downloader.DownloadStatus.OnFinish -> {
                            println("Download completed. Files: ${status.files}")
                            DownloadTracker.end(appContext, id)
                            updateStatus(Status.NOT_INSTALLED)
                        }

                        is Downloader.DownloadStatus.OnFailure -> {
                            println("Failed to download ${status.fileName}: ${status.failureReason}")
                            DownloadTracker.end(appContext, id)
                            updateStatus(Status.NOT_DOWNLOADED)
                        }

                        is Downloader.DownloadStatus.OnCancel -> {
                            println("Download cancelled for file: ${status.fileName}")
                            DownloadTracker.end(appContext, id)
                            updateStatus(Status.NOT_DOWNLOADED)
                        }

                        is Downloader.DownloadStatus.OnDownloadSuccess -> {
                            println("Successfully downloaded: ${status.fileName}")
                        }
                    }
                }
            } catch (e: Exception) {
                DownloadTracker.end(context, savedVersionData.installationId)
                Toast.makeText(context, "This account didn't buy Minecraft, is this the right Account?", Toast.LENGTH_SHORT).show()
            }
        }

        is VersionAction.CancelDownload -> {
            DownloadTracker.downloader(savedVersionData.installationId)?.cancelAll()
        }

        is VersionAction.Install -> {
            println("Installing ${savedVersionData.name}")
            isLoading.value = true
            val apks =
                File("${context.filesDir}/versions/${savedVersionData.installationId}/").listFiles { file ->
                    file.isFile &&
                        file.name.endsWith(".apk")
                }
            if (apks != null && apks.isNotEmpty()) {
                val onResult: (SessionResult) -> Unit = {
                    when (it) {
                        is SessionResult.OnProgress -> {
                            installProgress.intValue = it.progress
                        }

                        is SessionResult.OnSuccess -> {
                            installProgress.intValue = -1
                            isLoading.value = false
                            updateStatus(Status.INSTALLED)

                            coroutineScope.launch {
                                delay(500)
                                isUpdating.value = false
                            }
                        }

                        is SessionResult.OnFailure -> {
                            installProgress.intValue = -1
                            isLoading.value = false
                            isUpdating.value = false
                            println("Failed to install: ${it.message}")
                        }

                        is SessionResult.OnCancel -> {
                            installProgress.intValue = -1
                            isLoading.value = false
                            isUpdating.value = false
                            println("Installation cancelled")
                        }
                    }
                }
                try {
                    val versionCode = getPackageInfo(context, packageName).let { PackageInfoCompat.getLongVersionCode(it) }
                    if (versionCode > savedVersionData.versionCode.toLong()) {
                        uninstallApp(context, packageName) { status ->
                            if (status == SessionResult.OnSuccess) {
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
