package com.alibaba.mnnllm.android.archive.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alibaba.mnnllm.android.archive.ArchiveViewModel
import com.alibaba.mnnllm.android.archive.ProcessingState
import com.alibaba.mnnllm.android.archive.data.ArchiveRecord

private enum class Screen { Home, Processing, Confirm, Timeline, Detail, Todo, Settings, Camera, Record }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArchiveApp(viewModel: ArchiveViewModel, initialArchiveId: Long?) {
    ArchiveTheme {
        var screen by remember { mutableStateOf(if (initialArchiveId != null) Screen.Detail else Screen.Home) }
        var detailId by remember { mutableStateOf(initialArchiveId ?: -1L) }
        var sourceType by remember { mutableStateOf("photo") }

        val processing by viewModel.processing.collectAsStateWithLifecycle()

        LaunchedEffect(processing) {
            when (processing) {
                is ProcessingState.Done, is ProcessingState.NeedModel -> screen = Screen.Confirm
                else -> {}
            }
        }

        when (screen) {
            Screen.Home -> HomeScreen(
                viewModel = viewModel,
                onPickImage = { uri -> sourceType = "photo"; viewModel.processImage(uri, "photo"); screen = Screen.Processing },
                onPickAudio = { uri -> sourceType = "audio"; viewModel.processAudio(uri); screen = Screen.Processing },
                onOpenCamera = { screen = Screen.Camera },
                onOpenRecord = { screen = Screen.Record },
                onOpenTimeline = { viewModel.refreshArchives(); screen = Screen.Timeline },
                onOpenTodo = { viewModel.refreshTodos(false); screen = Screen.Todo },
                onOpenSettings = { screen = Screen.Settings },
                onOpenDetail = { id -> detailId = id; screen = Screen.Detail }
            )
            Screen.Record -> RecordScreen(
                onRecorded = { uri -> sourceType = "audio"; viewModel.processAudio(uri); screen = Screen.Processing },
                onBack = { screen = Screen.Home }
            )
            Screen.Camera -> CameraScreen(
                onCaptured = { uri -> sourceType = "photo"; viewModel.processImage(uri, "photo"); screen = Screen.Processing },
                onBack = { screen = Screen.Home }
            )
            Screen.Processing -> ProcessingScreen(processing) { viewModel.resetProcessing(); screen = Screen.Home }
            Screen.Confirm -> {
                val record = viewModel.pendingRecord()
                if (record == null) screen = Screen.Home
                else ConfirmScreen(
                    viewModel = viewModel, initial = record, processing = processing, sourceType = sourceType,
                    onArchived = { viewModel.resetProcessing(); viewModel.refreshArchives(); screen = Screen.Home },
                    onCancel = { viewModel.resetProcessing(); screen = Screen.Home }
                )
            }
            Screen.Timeline -> TimelineScreen(viewModel, onBack = { screen = Screen.Home }, onOpenDetail = { id -> detailId = id; screen = Screen.Detail })
            Screen.Detail -> DetailScreen(viewModel, detailId, onBack = { screen = Screen.Home })
            Screen.Todo -> TodoScreen(viewModel, onBack = { screen = Screen.Home }, onOpenDetail = { id -> detailId = id; screen = Screen.Detail })
            Screen.Settings -> SettingsScreen(viewModel, onBack = { screen = Screen.Home })
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeScreen(
    viewModel: ArchiveViewModel,
    onPickImage: (Uri) -> Unit,
    onPickAudio: (Uri) -> Unit,
    onOpenCamera: () -> Unit,
    onOpenRecord: () -> Unit,
    onOpenTimeline: () -> Unit,
    onOpenTodo: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenDetail: (Long) -> Unit
) {
    val context = LocalContext.current
    val archives by viewModel.archives.collectAsStateWithLifecycle()
    val todos by viewModel.todos.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.refreshArchives(); viewModel.refreshTodos(false) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let(onPickImage) }
    val audioPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { onPickAudio(it) } }
    val audioMimes = arrayOf("audio/*", "application/ogg", "application/octet-stream")
    var showAudioSheet by remember { mutableStateOf(false) }

    if (showAudioSheet) {
        AudioSourceSheet(
            onDismiss = { showAudioSheet = false },
            onPickFile = { showAudioSheet = false; audioPicker.launch(audioMimes) },
            onRecord = { showAudioSheet = false; onOpenRecord() }
        )
    }

    val cs = MaterialTheme.colorScheme
    Scaffold(
        containerColor = cs.background,
        bottomBar = { HomeBottomBar(selected = 0, onTimeline = onOpenTimeline, onTodo = onOpenTodo, onSettings = onOpenSettings) }
    ) { pad ->
        LazyColumn(
            Modifier.padding(pad).fillMaxSize(),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            item { GradientHeader() }
            item {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp).offset(y = (-28).dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ImportTile("相册", "导入截图", Icons.Outlined.PhotoLibrary, cs.primary, Modifier.weight(1f)) {
                        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }
                    ImportTile("录音", "语音转写", Icons.Outlined.Mic, cs.secondary, Modifier.weight(1f)) { showAudioSheet = true }
                    ImportTile("拍照", "实时识别", Icons.Outlined.PhotoCamera, cs.tertiary, Modifier.weight(1f)) { onOpenCamera() }
                }
            }

            item {
                SectionHeader("最近待办", Icons.Outlined.CheckCircle) { onOpenTodo() }
            }
            if (todos.isEmpty()) {
                item { EmptyHint("暂无待办，导入素材后自动生成") }
            } else {
                items(todos.take(3), key = { "todo_${it.id}" }) { t ->
                    Card(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).clickable { onOpenDetail(t.archiveId) },
                        colors = CardDefaults.cardColors(containerColor = cs.surface),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.RadioButtonUnchecked, null, tint = cs.primary, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(t.content, maxLines = 1, fontWeight = FontWeight.Medium)
                                t.deadline?.let { Text(it, fontSize = 12.sp, color = cs.onSurfaceVariant) }
                            }
                        }
                    }
                }
            }

            item { SectionHeader("最近归档", Icons.AutoMirrored.Filled.ListAlt) { onOpenTimeline() } }
            if (archives.isEmpty()) {
                item { EmptyHint("还没有归档，点击上方按钮开始") }
            } else {
                items(archives.take(8), key = { "arc_${it.id}" }) { r -> ArchiveCard(r) { onOpenDetail(r.id) } }
            }
        }
    }
}

@Composable
private fun GradientHeader() {
    val cs = MaterialTheme.colorScheme
    Box(
        Modifier.fillMaxWidth().height(170.dp)
            .background(Brush.linearGradient(listOf(cs.primary, cs.secondary)))
    ) {
        Column(Modifier.padding(20.dp).padding(top = 28.dp)) {
            Text("📋 本地归档小助手", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text("聊完即存 · 到点提醒 · 数据不出本机", color = Color.White.copy(alpha = 0.85f), fontSize = 13.sp)
        }
    }
}

@Composable
private fun ImportTile(title: String, subtitle: String, icon: ImageVector, accent: Color, modifier: Modifier, onClick: () -> Unit) {
    Card(
        modifier = modifier.height(118.dp).clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(Modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Box(
                Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(accent.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) { Icon(icon, null, tint = accent, modifier = Modifier.size(22.dp)) }
            Column {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text(subtitle, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AudioSourceSheet(onDismiss: () -> Unit, onPickFile: () -> Unit, onRecord: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = cs.surface) {
        Column(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
            Text("语音录入", fontWeight = FontWeight.Bold, fontSize = 16.sp,
                modifier = Modifier.padding(start = 20.dp, top = 4.dp, bottom = 8.dp))
            ListItem(
                headlineContent = { Text("现场录音") },
                supportingContent = { Text("用麦克风实时录音，最长 30 分钟") },
                leadingContent = { Icon(Icons.Outlined.Mic, null, tint = cs.primary) },
                modifier = Modifier.clickable(onClick = onRecord)
            )
            ListItem(
                headlineContent = { Text("选择音频文件") },
                supportingContent = { Text("从手机里选已有的录音 (m4a/mp3/wav…)") },
                leadingContent = { Icon(Icons.Outlined.FolderOpen, null, tint = cs.secondary) },
                modifier = Modifier.clickable(onClick = onPickFile)
            )
        }
    }
}

@Composable
private fun SectionHeader(title: String, icon: ImageVector, onMore: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 14.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.weight(1f))
        TextButton(onClick = onMore) { Text("查看全部") }
    }
}

@Composable
private fun EmptyHint(text: String) {
    Text(
        text, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

@Composable
private fun ArchiveCard(r: ArchiveRecord, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Card(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = cs.surface),
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(42.dp).clip(RoundedCornerShape(12.dp)).background(cs.primaryContainer),
                contentAlignment = Alignment.Center
            ) { Text(com.alibaba.mnnllm.android.archive.data.ArchiveCategory.of(r.category).emoji, fontSize = 20.sp) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(r.summary.ifBlank { "(无摘要)" }, fontWeight = FontWeight.Medium, maxLines = 1)
                val sub = buildString {
                    if (!r.deadline.isNullOrBlank()) append(r.deadline)
                    if (!r.contact.isNullOrBlank()) { if (isNotEmpty()) append(" · "); append(r.contact) }
                }
                if (sub.isNotEmpty()) Text(sub, fontSize = 12.sp, color = cs.onSurfaceVariant, maxLines = 1)
            }
            if (r.tags.isNotEmpty()) {
                AssistChip(onClick = onClick, label = { Text(r.tags.first(), fontSize = 11.sp) })
            }
        }
    }
}

private fun sourceEmoji(s: String) = when (s) {
    "audio" -> "🎙️"; "invoice" -> "🧾"; "card" -> "💳"; "chat" -> "💬"; else -> "📸"
}

@Composable
private fun HomeBottomBar(selected: Int, onTimeline: () -> Unit, onTodo: () -> Unit, onSettings: () -> Unit) {
    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
        NavigationBarItem(selected = selected == 0, onClick = {}, icon = { Icon(Icons.Filled.Home, null) }, label = { Text("首页") })
        NavigationBarItem(selected = false, onClick = onTimeline, icon = { Icon(Icons.AutoMirrored.Filled.ListAlt, null) }, label = { Text("时间轴") })
        NavigationBarItem(selected = false, onClick = onTodo, icon = { Icon(Icons.Filled.CheckCircle, null) }, label = { Text("待办") })
        NavigationBarItem(selected = false, onClick = onSettings, icon = { Icon(Icons.Filled.Settings, null) }, label = { Text("设置") })
    }
}

@Composable
private fun ProcessingScreen(state: ProcessingState, onCancel: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Box(Modifier.fillMaxSize().background(cs.background), contentAlignment = Alignment.Center) {
        when (state) {
            is ProcessingState.Loading -> {
                Card(
                    Modifier.padding(32.dp).fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = cs.surface),
                    shape = RoundedCornerShape(20.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
                ) {
                    Column(Modifier.padding(24.dp)) {
                        Text("正在处理…", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Spacer(Modifier.height(4.dp))
                        Text("全程本地运行，数据不出本机", fontSize = 12.sp, color = cs.onSurfaceVariant)
                        Spacer(Modifier.height(20.dp))
                        // step labels come straight from the active flow (photo vs audio)
                        state.steps.forEachIndexed { i, label ->
                            StepRow(
                                label = label,
                                status = when {
                                    i < state.step -> StepStatus.DONE
                                    i == state.step -> StepStatus.ACTIVE
                                    else -> StepStatus.PENDING
                                }
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { (state.step + 0.5f) / state.total },
                            modifier = Modifier.fillMaxWidth(),
                            color = cs.primary
                        )
                    }
                }
            }
            is ProcessingState.Error -> {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Filled.ErrorOutline, null, tint = cs.error, modifier = Modifier.size(48.dp))
                    Spacer(Modifier.height(12.dp))
                    Text("处理失败", fontWeight = FontWeight.Bold)
                    Text(state.message, color = cs.error, modifier = Modifier.padding(16.dp))
                    Button(onClick = onCancel) { Text("返回") }
                }
            }
            else -> CircularProgressIndicator(color = cs.primary)
        }
    }
}

private enum class StepStatus { DONE, ACTIVE, PENDING }

@Composable
private fun StepRow(label: String, status: StepStatus) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        when (status) {
            StepStatus.DONE -> Icon(Icons.Filled.CheckCircle, null, tint = cs.secondary, modifier = Modifier.size(22.dp))
            StepStatus.ACTIVE -> CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = cs.primary)
            StepStatus.PENDING -> Icon(Icons.Outlined.RadioButtonUnchecked, null, tint = cs.onSurfaceVariant.copy(alpha = 0.4f), modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(12.dp))
        Text(
            label,
            color = if (status == StepStatus.PENDING) cs.onSurfaceVariant.copy(alpha = 0.5f) else cs.onSurface,
            fontWeight = if (status == StepStatus.ACTIVE) FontWeight.Bold else FontWeight.Normal
        )
    }
}

