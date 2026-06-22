package com.alibaba.mnnllm.android.archive.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.squti.androidwaverecorder.WaveRecorder
import java.io.File

/**
 * Live microphone recording screen (PRD: 语音录入). Records to a WAV file, then hands the
 * file path back for the Gemma transcription + structuring pipeline.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordScreen(onRecorded: (android.net.Uri) -> Unit, onBack: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val context = LocalContext.current
    var recording by remember { mutableStateOf(false) }
    var seconds by remember { mutableStateOf(0) }
    val recorder = remember { mutableStateOf<WaveRecorder?>(null) }
    val filePath = remember {
        File(context.cacheDir, "record_${System.currentTimeMillis()}.wav").absolutePath
    }

    // tick timer while recording
    LaunchedEffect(recording) {
        if (recording) {
            seconds = 0
            while (recording) {
                kotlinx.coroutines.delay(1000)
                seconds++
                if (seconds >= 30 * 60) break // 30-min cap per PRD
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose { runCatching { recorder.value?.stopRecording() } }
    }

    val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 1f, targetValue = 1.25f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "scale"
    )

    Scaffold(
        containerColor = cs.background,
        topBar = {
            TopAppBar(
                title = { Text("语音录入", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = {
                    runCatching { recorder.value?.stopRecording() }; onBack()
                }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = cs.background)
            )
        }
    ) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                String.format("%02d:%02d", seconds / 60, seconds % 60),
                fontSize = 48.sp, fontWeight = FontWeight.Bold, color = cs.onSurface
            )
            Spacer(Modifier.height(8.dp))
            Text(if (recording) "正在录音… 最长 30 分钟" else "点击下方按钮开始录音",
                color = cs.onSurfaceVariant)
            Spacer(Modifier.height(48.dp))
            Box(
                Modifier.size(96.dp).scale(if (recording) pulse else 1f)
                    .clip(CircleShape)
                    .background(if (recording) cs.error else cs.primary)
                    .let { it },
                contentAlignment = Alignment.Center
            ) {
                IconButton(
                    onClick = {
                        if (!recording) {
                            val rec = WaveRecorder(filePath)
                            recorder.value = rec
                            runCatching { rec.startRecording() }
                            recording = true
                        } else {
                            runCatching { recorder.value?.stopRecording() }
                            recording = false
                            val f = File(filePath)
                            if (f.exists() && f.length() > 0) onRecorded(android.net.Uri.fromFile(f))
                        }
                    },
                    modifier = Modifier.size(96.dp)
                ) {
                    Icon(
                        if (recording) Icons.Filled.Stop else Icons.Filled.Mic,
                        contentDescription = if (recording) "停止" else "录音",
                        tint = Color.White, modifier = Modifier.size(40.dp)
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
            Text(if (recording) "点击停止并开始转写" else "", color = cs.onSurfaceVariant, fontSize = 13.sp)
        }
    }
}
