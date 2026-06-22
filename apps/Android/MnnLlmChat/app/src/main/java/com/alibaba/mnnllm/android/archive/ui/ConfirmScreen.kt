package com.alibaba.mnnllm.android.archive.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alibaba.mnnllm.android.archive.ArchiveSettings
import com.alibaba.mnnllm.android.archive.ArchiveViewModel
import com.alibaba.mnnllm.android.archive.ProcessingState
import com.alibaba.mnnllm.android.archive.calendar.CalendarRepository
import com.alibaba.mnnllm.android.archive.data.ArchiveRecord
import com.alibaba.mnnllm.android.archive.reminder.ReminderScheduler
import com.alibaba.mls.api.download.ModelDownloadManager
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfirmScreen(
    viewModel: ArchiveViewModel,
    initial: ArchiveRecord,
    processing: ProcessingState,
    sourceType: String,
    onArchived: () -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current

    // Key on `initial` so fields re-populate when the structured record arrives.
    var contact by remember(initial) { mutableStateOf(initial.contact ?: "") }
    var actionItems by remember(initial) { mutableStateOf(initial.actionItems.joinToString("\n")) }
    var deadline by remember(initial) { mutableStateOf(initial.deadline ?: "") }
    var amount by remember(initial) { mutableStateOf(initial.amount?.toString() ?: "") }
    var address by remember(initial) { mutableStateOf(initial.address ?: "") }
    var summary by remember(initial) { mutableStateOf(initial.summary) }
    var tags by remember(initial) { mutableStateOf(initial.tags.joinToString(", ")) }
    val low = initial.lowConfidenceFields.toSet()

    val needModel = processing is ProcessingState.NeedModel

    fun snapshot() = collect(initial, contact, actionItems, deadline, amount, address, summary, tags, sourceType)

    // writes the record + calendar event + reminder, with user feedback
    fun archiveWithCalendar() {
        val rec = snapshot()
        viewModel.saveRecord(rec) { id ->
            if (deadline.isNotBlank()) {
                val eventId = CalendarRepository.insertEvent(context, id, summary, deadline, address, 30)
                ReminderScheduler.schedule(context, id, summary, deadline, 30)
                Toast.makeText(
                    context,
                    if (eventId != null) "已写入日历并设置提醒" else "已归档，但日历写入失败（请检查日历权限）",
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                Toast.makeText(context, "已归档（无截止时间，未写日历）", Toast.LENGTH_SHORT).show()
            }
            onArchived()
        }
    }

    val calendarPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { granted ->
        if (granted.values.all { it }) archiveWithCalendar()
        else {
            Toast.makeText(context, "未授予日历权限，已仅归档", Toast.LENGTH_SHORT).show()
            viewModel.saveRecord(snapshot()) { onArchived() }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("核对信息") },
                navigationIcon = { IconButton(onClick = onCancel) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } }
            )
        },
        bottomBar = {
            if (!needModel) {
                Column(Modifier.padding(12.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                if (hasCalendarPermission(context)) archiveWithCalendar()
                                else calendarPermLauncher.launch(
                                    arrayOf(
                                        android.Manifest.permission.WRITE_CALENDAR,
                                        android.Manifest.permission.READ_CALENDAR
                                    )
                                )
                            },
                            modifier = Modifier.weight(1f)
                        ) { Text("写入日历") }
                        OutlinedButton(
                            onClick = {
                                viewModel.saveRecord(snapshot()) {
                                    writeToNotes(context, summary, snapshot())
                                    onArchived()
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) { Text("存入备忘录") }
                        OutlinedButton(
                            onClick = {
                                viewModel.saveRecord(snapshot()) {
                                    Toast.makeText(context, "已归档", Toast.LENGTH_SHORT).show(); onArchived()
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) { Text("仅归档") }
                    }
                }
            }
        }
    ) { pad ->
        Column(Modifier.padding(pad).padding(16.dp).verticalScroll(rememberScrollState())) {
            if (needModel) {
                val modelId = (processing as ProcessingState.NeedModel).modelId
                ModelNeededCard(modelId, sourceType, viewModel)
                Spacer(Modifier.height(12.dp))
                Text("已识别文字（OCR）：", style = MaterialTheme.typography.labelLarge)
                if (initial.rawText.isBlank()) Text("(空)", color = Color.Gray)
                else Text(
                    initial.rawText.lines().map { it.trim() }.filter { it.isNotBlank() }.joinToString("  "),
                    color = Color.Gray, fontSize = 14.sp, lineHeight = 22.sp
                )
                HorizontalDivider(Modifier.padding(vertical = 12.dp))
            }

            // detected scene chip
            val cat = com.alibaba.mnnllm.android.archive.data.ArchiveCategory.of(initial.category)
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) {
                AssistChip(onClick = {}, label = { Text("${cat.emoji} ${cat.label}") })
                Spacer(Modifier.width(8.dp))
                Text("已自动识别场景", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            FieldCard("摘要", summary, isLow = low.contains("summary")) { summary = it }
            FieldCard("联系人/商户", contact, isLow = low.contains("contact")) { contact = it }
            FieldCard("待办事项（每行一条）", actionItems, isLow = low.contains("action_items"), singleLine = false) { actionItems = it }

            // deadline: fuzzy -> force picker
            DeadlineField(deadline, isLow = low.contains("deadline") || initial.deadlineFuzzy, fuzzy = initial.deadlineFuzzy) {
                deadline = it
            }
            FieldCard("金额", amount, keyboard = KeyboardType.Number, isLow = low.contains("amount")) { amount = it }
            FieldCard("地点", address, isLow = low.contains("address")) { address = it }
            FieldCard("标签（逗号分隔）", tags, isLow = false) { tags = it }

            // scene-specific extra fields (read-only display; editable not required for MVP)
            if (initial.extras.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text("${cat.label}专属信息", fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp, bottom = 4.dp))
                initial.extras.forEach { (k, v) ->
                    OutlinedTextField(
                        value = v, onValueChange = {}, readOnly = true,
                        label = { Text(k) },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ModelNeededCard(modelId: String, sourceType: String, viewModel: ArchiveViewModel) {
    val context = LocalContext.current
    var progress by remember { mutableStateOf(-1.0) }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
        Column(Modifier.padding(16.dp)) {
            Text("结构化模型未下载", style = MaterialTheme.typography.titleMedium)
            Text("需要下载 $modelId 才能进行结构化分析。OCR 文字已识别完成。", color = Color.DarkGray)
            Spacer(Modifier.height(8.dp))
            if (progress in 0.0..99.9) {
                LinearProgressIndicator(progress = { (progress / 100.0).toFloat() }, modifier = Modifier.fillMaxWidth())
                Text("下载中 ${progress.toInt()}%")
            }
            Button(onClick = {
                val mgr = ModelDownloadManager.getInstance(context)
                mgr.addListener(object : com.alibaba.mls.api.download.DownloadListener {
                    override fun onDownloadStart(id: String) {}
                    override fun onDownloadProgress(id: String, info: com.alibaba.mls.api.download.DownloadInfo) {
                        if (id == modelId) progress = info.progress
                    }
                    override fun onDownloadFinished(id: String, path: String) {
                        if (id == modelId) viewModel.continueStructuring(sourceType)
                    }
                    override fun onDownloadFailed(id: String, e: Exception) {}
                })
                mgr.startDownload(modelId)
                progress = 0.0
            }) { Text("下载模型") }
        }
    }
}

@Composable
private fun FieldCard(
    label: String,
    value: String,
    keyboard: KeyboardType = KeyboardType.Text,
    isLow: Boolean = false,
    singleLine: Boolean = true,
    onChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label + if (isLow) "  ⚠️请核对" else "") },
        singleLine = singleLine,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        isError = isLow,
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
    )
}

@Composable
private fun DeadlineField(value: String, isLow: Boolean, fuzzy: Boolean, onChange: (String) -> Unit) {
    val context = LocalContext.current
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text("截止时间" + if (fuzzy) "  ⚠️模糊时间，请补全" else if (isLow) "  ⚠️请核对" else "") },
        readOnly = false,
        isError = isLow || (fuzzy && value.isBlank()),
        trailingIcon = {
            TextButton(onClick = {
                val cal = Calendar.getInstance()
                DatePickerDialog(context, { _, y, m, d ->
                    TimePickerDialog(context, { _, h, min ->
                        onChange(String.format("%04d-%02d-%02d %02d:%02d", y, m + 1, d, h, min))
                    }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), true).show()
                }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
            }) { Text("选择") }
        },
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
    )
}

private fun collect(
    base: ArchiveRecord, contact: String, actionItems: String, deadline: String,
    amount: String, address: String, summary: String, tags: String, sourceType: String
): ArchiveRecord {
    return base.copy(
        id = 0,
        contact = contact.ifBlank { null },
        actionItems = actionItems.split("\n").map { it.trim() }.filter { it.isNotBlank() },
        deadline = deadline.ifBlank { null },
        amount = amount.toDoubleOrNull(),
        address = address.ifBlank { null },
        summary = summary,
        tags = tags.split(",", "，").map { it.trim() }.filter { it.isNotBlank() },
        sourceType = sourceType,
        lowConfidenceFields = emptyList(),
        deadlineFuzzy = false,
        createdAt = System.currentTimeMillis()
    )
}

private fun hasCalendarPermission(context: Context): Boolean {
    return ContextCompat.checkSelfPermission(context, android.Manifest.permission.WRITE_CALENDAR) ==
            PackageManager.PERMISSION_GRANTED
}

/** Writes the record to the system notes app: ACTION_CREATE_NOTE (Android 14+) → share fallback. */
private fun writeToNotes(context: Context, title: String, r: ArchiveRecord) {
    val body = buildString {
        appendLine(title)
        if (!r.contact.isNullOrBlank()) appendLine("联系人：${r.contact}")
        if (!r.deadline.isNullOrBlank()) appendLine("时间：${r.deadline}")
        if (r.amount != null) appendLine("金额：${r.amount}")
        if (!r.address.isNullOrBlank()) appendLine("地点：${r.address}")
        if (r.actionItems.isNotEmpty()) {
            appendLine("待办：")
            r.actionItems.forEach { appendLine("· $it") }
        }
        if (r.tags.isNotEmpty()) appendLine("标签：${r.tags.joinToString("，")}")
    }.trim()

    // Try the standard "create note" action first (supported by some OEM notes apps on API 33+).
    if (android.os.Build.VERSION.SDK_INT >= 34) {
        try {
            val note = android.content.Intent("android.intent.action.CREATE_NOTE").apply {
                putExtra(android.content.Intent.EXTRA_TITLE, title)
                putExtra(android.content.Intent.EXTRA_TEXT, body)
            }
            if (note.resolveActivity(context.packageManager) != null) {
                context.startActivity(note)
                Toast.makeText(context, "已发送到备忘录", Toast.LENGTH_SHORT).show()
                return
            }
        } catch (e: Exception) { /* fall through */ }
    }
    // Fallback: share as plain text to any notes / memo app the user picks.
    try {
        val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(android.content.Intent.EXTRA_SUBJECT, title)
            putExtra(android.content.Intent.EXTRA_TEXT, body)
        }
        context.startActivity(android.content.Intent.createChooser(send, "存入备忘录"))
    } catch (e: Exception) {
        Toast.makeText(context, "已归档（未找到备忘录应用）", Toast.LENGTH_SHORT).show()
    }
}
