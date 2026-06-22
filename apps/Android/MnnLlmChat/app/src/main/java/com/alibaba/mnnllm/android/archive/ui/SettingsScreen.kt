package com.alibaba.mnnllm.android.archive.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Slideshow
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alibaba.mnnllm.android.archive.ArchiveSettings
import com.alibaba.mnnllm.android.archive.ArchiveViewModel
import com.alibaba.mnnllm.android.archive.cloud.CloudKeyStore
import com.alibaba.mls.api.download.DownloadInfo
import com.alibaba.mls.api.download.DownloadListener
import com.alibaba.mls.api.download.ModelDownloadManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: ArchiveViewModel, onBack: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val context = LocalContext.current
    val structModel = remember { ArchiveSettings.structuringModelId(context) }
    val audioModel = remember { ArchiveSettings.audioModelId(context) }

    var structProgress by remember { mutableStateOf(modelProgress(context, structModel)) }
    var audioProgress by remember { mutableStateOf(modelProgress(context, audioModel)) }

    var cloudEnabled by remember { mutableStateOf(ArchiveSettings.cloudEnabled(context)) }
    var cloudEndpoint by remember { mutableStateOf(ArchiveSettings.cloudEndpoint(context)) }
    var cloudKey by remember { mutableStateOf("") }
    var showWipeDialog by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = cs.background,
        topBar = {
            TopAppBar(
                title = { Text("设置", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = cs.background)
            )
        }
    ) { pad ->
        Column(
            Modifier.padding(pad).padding(16.dp).fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            SettingsCard(title = "模型管理", icon = Icons.Default.Download) {
                ModelRow("结构化推理 · Qwen3.5-2B", structModel, structProgress) {
                    downloadModel(context, structModel) { structProgress = it }
                }
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                ModelRow("音频转写 · Gemma 4 E2B", audioModel, audioProgress) {
                    downloadModel(context, audioModel) { audioProgress = it }
                }
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                Text("OCR 模型已内置于 App，开箱即用。", fontSize = 12.sp, color = cs.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = {
                        context.startActivity(
                            android.content.Intent(context, com.alibaba.mnnllm.android.main.MainActivity::class.java)
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("打开完整模型市场") }
            }

            SettingsCard(title = "云端增强（默认关闭）", icon = Icons.Default.CloudOff) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("关闭时无任何网络请求", Modifier.weight(1f), fontSize = 13.sp, color = cs.onSurfaceVariant)
                    Switch(checked = cloudEnabled, onCheckedChange = {
                        cloudEnabled = it; ArchiveSettings.setCloudEnabled(context, it)
                    })
                }
                if (cloudEnabled) {
                    OutlinedTextField(cloudEndpoint, { cloudEndpoint = it; ArchiveSettings.setCloudEndpoint(context, it) },
                        label = { Text("云端 API 地址") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                    OutlinedTextField(cloudKey, { cloudKey = it },
                        label = { Text("API Key（Keystore 加密）") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                    Button(onClick = { CloudKeyStore.saveApiKey(context, cloudKey); cloudKey = "" },
                        modifier = Modifier.padding(top = 8.dp)) { Text("保存密钥") }
                    Text("强制脱敏：手机号/地址/金额占位；原图/录音禁止上传。",
                        fontSize = 12.sp, color = cs.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
                }
            }

            SettingsCard(title = "演示", icon = Icons.Default.Slideshow) {
                Text("一键载入覆盖各场景的示例记录，用于功能演示。", fontSize = 12.sp, color = cs.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { viewModel.loadDemoData { android.widget.Toast.makeText(context, "已载入演示数据", android.widget.Toast.LENGTH_SHORT).show() } },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("载入演示数据") }
            }

            SettingsCard(title = "数据管理", icon = Icons.Default.Storage) {
                OutlinedButton(onClick = { viewModel.wipeAll(deleteMaterials = true) }, modifier = Modifier.fillMaxWidth()) {
                    Text("清理原图（保留文字台账）")
                }
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { showWipeDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = cs.error),
                    modifier = Modifier.fillMaxWidth()
                ) { Text("一键清空全部数据") }
            }
        }
    }

    if (showWipeDialog) {
        AlertDialog(
            onDismissRequest = { showWipeDialog = false },
            title = { Text("确认清空") },
            text = { Text("将删除全部归档记录、待办与素材文件，不可恢复。") },
            confirmButton = { TextButton(onClick = { viewModel.wipeAll(true); showWipeDialog = false }) { Text("清空") } },
            dismissButton = { TextButton(onClick = { showWipeDialog = false }) { Text("取消") } }
        )
    }
}

@Composable
private fun SettingsCard(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, content: @Composable ColumnScope.() -> Unit) {
    val cs = MaterialTheme.colorScheme
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = cs.surface)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = cs.primary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun ModelRow(label: String, modelId: String, progress: Double, onDownload: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Column {
        Text(label, fontWeight = FontWeight.Medium)
        Text(modelId, fontSize = 11.sp, color = cs.onSurfaceVariant)
        Spacer(Modifier.height(6.dp))
        when {
            progress >= 100.0 -> Text("✅ 已下载", color = cs.secondary, fontSize = 13.sp)
            progress in 0.0..99.9 -> {
                LinearProgressIndicator(progress = { (progress / 100.0).toFloat() }, modifier = Modifier.fillMaxWidth(), color = cs.primary)
                Text("下载中 ${progress.toInt()}%", fontSize = 12.sp, color = cs.onSurfaceVariant)
            }
            else -> Button(onClick = onDownload) { Text("下载") }
        }
    }
}

private fun modelProgress(context: android.content.Context, modelId: String): Double {
    val info = ModelDownloadManager.getInstance(context).getDownloadInfo(modelId)
    return if (info.isComplete()) 100.0 else info.progress
}

private fun downloadModel(context: android.content.Context, modelId: String, onProgress: (Double) -> Unit) {
    val mgr = ModelDownloadManager.getInstance(context)
    mgr.addListener(object : DownloadListener {
        override fun onDownloadStart(id: String) {}
        override fun onDownloadProgress(id: String, info: DownloadInfo) { if (id == modelId) onProgress(info.progress) }
        override fun onDownloadFinished(id: String, path: String) { if (id == modelId) onProgress(100.0) }
        override fun onDownloadFailed(id: String, e: Exception) {}
    })
    mgr.startDownload(modelId)
    onProgress(0.0)
}
