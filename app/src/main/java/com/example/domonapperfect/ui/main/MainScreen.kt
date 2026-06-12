package com.example.domonapperfect.ui.main

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.domonapperfect.data.network.KeyResponse
import kotlinx.coroutines.launch

fun Context.getActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.getActivity()
    else -> null
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: IntercomViewModel,
    onLogout: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val autoOpenEnabled by viewModel.autoOpenEnabled.collectAsState()
    val isCallNotificationOnly by viewModel.isCallNotificationOnly.collectAsState()
    val isOpenButtonOnLeft by viewModel.isOpenButtonOnLeft.collectAsState()
    val isRingtoneEnabled by viewModel.isRingtoneEnabled.collectAsState()
    val isDoNotDisturbEnabled by viewModel.isDoNotDisturbEnabled.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.loadKeys()
    }

    var playingCamera by remember { mutableStateOf<KeyResponse?>(null) }
    var selectedTab by remember { mutableStateOf(0) }
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var newFolderName by remember { mutableStateOf("") }
    
    var editingDoor by remember { mutableStateOf<KeyResponse?>(null) }
    var editedDoorName by remember { mutableStateOf("") }
    var selectedFolderId by remember { mutableStateOf<String?>("") }

    LaunchedEffect(uiState.actionMessage) {
        uiState.actionMessage?.let { message ->
            coroutineScope.launch {
                snackbarHostState.showSnackbar(message)
                viewModel.clearActionMessage()
            }
        }
    }

    if (showCreateFolderDialog) {
        AlertDialog(
            onDismissRequest = { showCreateFolderDialog = false },
            title = { Text("Новая папка") },
            text = {
                OutlinedTextField(
                    value = newFolderName,
                    onValueChange = { newFolderName = it },
                    label = { Text("Название папки") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (newFolderName.isNotBlank()) {
                        viewModel.createFolder(newFolderName)
                    }
                    showCreateFolderDialog = false
                    newFolderName = ""
                }) { Text("Создать") }
            },
            dismissButton = {
                TextButton(onClick = { showCreateFolderDialog = false }) { Text("Отмена") }
            }
        )
    }

    editingDoor?.let { door ->
        AlertDialog(
            onDismissRequest = { editingDoor = null },
            title = { Text("Настроить дверь") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = editedDoorName,
                        onValueChange = { editedDoorName = it },
                        label = { Text("Свое название") },
                        singleLine = true
                    )
                    Text("Папка", style = MaterialTheme.typography.labelLarge)
                    // Simple drop down simulation with a radio button list or just buttons
                    LazyColumn(modifier = Modifier.heightIn(max = 200.dp)) {
                        item {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(selected = selectedFolderId == null, onClick = { selectedFolderId = null })
                                Text("Без папки")
                            }
                        }
                        items(items = uiState.folders, key = { it.id }) { folder ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(selected = selectedFolderId == folder.id, onClick = { selectedFolderId = folder.id })
                                Text(folder.name)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.updateDoorCustomization(door.id, editedDoorName.takeIf { it.isNotBlank() }, selectedFolderId)
                    editingDoor = null
                }) { Text("Сохранить") }
            },
            dismissButton = {
                TextButton(onClick = { editingDoor = null }) { Text("Отмена") }
            }
        )
    }

    if (playingCamera != null) {
        Dialog(
            onDismissRequest = { playingCamera = null },
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                dismissOnBackPress = true,
                dismissOnClickOutside = true
            )
        ) {
            val context = androidx.compose.ui.platform.LocalContext.current

            var isMicEnabled by remember { mutableStateOf(false) }

            Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
                VideoPlayer(
                    camera = playingCamera!!,
                    token = viewModel.token,
                    isMicrophoneEnabled = isMicEnabled,
                    modifier = Modifier.fillMaxSize().padding(bottom = 100.dp)
                )
                
                Row(
                    modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter).padding(bottom = 32.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    val isOpening = uiState.openingKeys.contains(playingCamera!!.id)
                    Button(
                        onClick = { viewModel.openDoor(playingCamera!!.id) },
                        enabled = !isOpening,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        modifier = Modifier.height(56.dp)
                    ) {
                        if (isOpening) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
                        } else {
                            Text("Открыть эту дверь", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    
                    if (!playingCamera!!.webrtcVideoUrl.isNullOrEmpty()) {
                        Button(
                            onClick = { isMicEnabled = !isMicEnabled },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isMicEnabled) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary
                            ),
                            modifier = Modifier.height(56.dp)
                        ) {
                            Icon(
                                imageVector = if (isMicEnabled) androidx.compose.material.icons.Icons.Default.Mic else androidx.compose.material.icons.Icons.Default.MicOff,
                                contentDescription = if (isMicEnabled) "Выключить микрофон" else "Включить микрофон",
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                    
                    Button(
                        onClick = { playingCamera = null },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.height(56.dp)
                    ) {
                        Text("Закрыть", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    Scaffold(
        topBar = {
            if (selectedTab == 0) {
                TopAppBar(
                    title = { Text("Domofon App Perfect") },
                    actions = {
                        IconButton(onClick = { showCreateFolderDialog = true }) {
                            Icon(Icons.Default.Add, contentDescription = "Создать папку")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        titleContentColor = MaterialTheme.colorScheme.primary
                    )
                )
            }
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Default.Home, contentDescription = "Главная") },
                    label = { Text("Главная") }
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Default.History, contentDescription = "История") },
                    label = { Text("История") }
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Icon(Icons.Default.Settings, contentDescription = "Настройки") },
                    label = { Text("Настройки") }
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (selectedTab == 2) {
                SettingsScreen(
                    isAutoOpen = autoOpenEnabled,
                    onAutoOpenChange = { viewModel.setAutoOpen(it) },
                    isCallNotificationOnly = isCallNotificationOnly,
                    onCallNotificationOnlyChange = { viewModel.setCallNotificationOnly(it) },
                    isOpenButtonOnLeft = isOpenButtonOnLeft,
                    onOpenButtonOnLeftChange = { viewModel.setOpenButtonOnLeft(it) },
                    isRingtoneEnabled = isRingtoneEnabled,
                    onRingtoneEnabledChange = { viewModel.setRingtoneEnabled(it) },
                    isDoNotDisturbEnabled = isDoNotDisturbEnabled,
                    onDoNotDisturbEnabledChange = { viewModel.setDoNotDisturbEnabled(it) },
                    callWindowDelaySeconds = viewModel.callWindowDelaySeconds.collectAsState().value,
                    onCallWindowDelayChange = { viewModel.setCallWindowDelaySeconds(it) },
                    onLogout = onLogout
                )
            } else if (selectedTab == 1) {
                HistoryScreen(viewModel)
            } else {
                if (uiState.isLoading && uiState.keys.isEmpty()) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                } else if (uiState.error != null && uiState.keys.isEmpty()) {
                    Text(
                        text = uiState.error!!,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.align(Alignment.Center)
                    )
                } else {
                    val uniqueCameras = uiState.keys
                        .filter { !it.httpVideoUrl.isNullOrEmpty() || !it.webrtcVideoUrl.isNullOrEmpty() }
                        .distinctBy { it.webrtcVideoUrl ?: it.httpVideoUrl }

                    val keysByFolder = uiState.keys.groupBy { 
                        uiState.customizations[it.id]?.folderId 
                    }

                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        if (uniqueCameras.isNotEmpty()) {
                            item {
                                Text("Камеры", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                    items(items = uniqueCameras, key = { it.id }) { cam ->
                                        val customName = uiState.customizations[cam.id]?.customName
                                        val displayName = if (customName.isNullOrBlank()) "Камера" else customName
                                        Card(
                                            shape = RoundedCornerShape(16.dp),
                                            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                                            modifier = Modifier.width(280.dp)
                                        ) {
                                            Column {
                                                if (!cam.videoPreview.isNullOrEmpty()) {
                                                    AsyncImage(
                                                        model = cam.videoPreview,
                                                        contentDescription = "Camera Preview",
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .height(160.dp)
                                                            .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                                                            .clickable { playingCamera = cam },
                                                        contentScale = ContentScale.Crop
                                                    )
                                                }
                                                Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                                    Text(text = displayName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                                    Button(
                                                        onClick = { playingCamera = cam },
                                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                                                    ) {
                                                        Text("Смотреть")
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Display Folders
                        val sortedFolders = uiState.folders.sortedBy { it.orderIndex }
                        sortedFolders.forEach { folder ->
                            val folderKeys = (keysByFolder[folder.id] ?: emptyList()).sortedBy { uiState.customizations[it.id]?.orderIndex ?: 0 }
                            if (folderKeys.isNotEmpty() || true) { // Show empty folders too? Yes, for now.
                                item {
                                    Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                        Text("📁 ${folder.name}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                        IconButton(onClick = { viewModel.deleteFolder(folder.id) }) {
                                            Icon(androidx.compose.material.icons.Icons.Default.Add, contentDescription = "Удалить папку", tint = MaterialTheme.colorScheme.error) // using add icon temporarily, better use Delete but we don't have it imported, let's just use Text
                                        }
                                    }
                                }
                                items(items = folderKeys, key = { it.id }) { key ->
                                    DoorCard(
                                        key = key,
                                        customName = uiState.customizations[key.id]?.customName,
                                        isOpening = uiState.openingKeys.contains(key.id),
                                        isOpenButtonOnLeft = isOpenButtonOnLeft,
                                        onOpen = { viewModel.openDoor(key.id) },
                                        onEdit = {
                                            editedDoorName = uiState.customizations[key.id]?.customName ?: key.name
                                            selectedFolderId = uiState.customizations[key.id]?.folderId
                                            editingDoor = key
                                        },
                                        onMoveUp = { viewModel.moveDoorUp(key.id) },
                                        onMoveDown = { viewModel.moveDoorDown(key.id) }
                                    )
                                }
                            }
                        }

                        // Display Uncategorized
                        val uncategorizedKeys = (keysByFolder[null] ?: emptyList()).sortedBy { uiState.customizations[it.id]?.orderIndex ?: 0 }
                        if (uncategorizedKeys.isNotEmpty()) {
                            item {
                                Text(if (uiState.folders.isEmpty()) "Двери" else "Остальные двери", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp, bottom = 8.dp))
                            }
                            items(items = uncategorizedKeys, key = { it.id }) { key ->
                                DoorCard(
                                    key = key,
                                    customName = uiState.customizations[key.id]?.customName,
                                    isOpening = uiState.openingKeys.contains(key.id),
                                    isOpenButtonOnLeft = isOpenButtonOnLeft,
                                    onOpen = { viewModel.openDoor(key.id) },
                                    onEdit = {
                                        editedDoorName = uiState.customizations[key.id]?.customName ?: key.name
                                        selectedFolderId = uiState.customizations[key.id]?.folderId
                                        editingDoor = key
                                    },
                                    onMoveUp = { viewModel.moveDoorUp(key.id) },
                                    onMoveDown = { viewModel.moveDoorDown(key.id) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DoorCard(
    key: KeyResponse,
    customName: String?,
    isOpening: Boolean,
    isOpenButtonOnLeft: Boolean,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val openButton = @Composable {
                Button(
                    onClick = onOpen,
                    enabled = !isOpening,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    if (isOpening) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
                    } else {
                        Text("Открыть")
                    }
                }
            }

            if (isOpenButtonOnLeft) {
                openButton()
                Spacer(modifier = Modifier.width(16.dp))
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (customName.isNullOrBlank()) key.name else customName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.outline)
                    }
                }
                if (!customName.isNullOrBlank()) {
                    Text(text = "Оригинал: ${key.name}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 4.dp)) {
                IconButton(onClick = onMoveUp, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Вверх", tint = MaterialTheme.colorScheme.outline)
                }
                IconButton(onClick = onMoveDown, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Вниз", tint = MaterialTheme.colorScheme.outline)
                }
            }

            if (!isOpenButtonOnLeft) {
                Spacer(modifier = Modifier.width(16.dp))
                openButton()
            }
        }
    }
}
