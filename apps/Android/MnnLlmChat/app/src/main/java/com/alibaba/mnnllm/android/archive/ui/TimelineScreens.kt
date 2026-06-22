package com.alibaba.mnnllm.android.archive.ui

import android.graphics.BitmapFactory
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddAlert
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alibaba.mnnllm.android.archive.ArchiveViewModel
import com.alibaba.mnnllm.android.archive.data.ArchiveRecord
import java.io.File

private fun srcEmoji(s: String) = when (s) { "audio" -> "🎙️"; "invoice" -> "🧾"; "card" -> "💳"; "chat" -> "💬"; else -> "📸" }

/**
 * Cleans up multi-line OCR output for display: trims each fragment, drops blanks,
 * and joins them with spaces into flowing text (OCR splits one sentence across boxes).
 */
private fun prettyRawText(raw: String): String =
    raw.lines().map { it.trim() }.filter { it.isNotBlank() }.joinToString("  ")

@Composable
private fun EmptyState(text: String) {
    Column(
        Modifier.fillMaxWidth().padding(top = 80.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Outlined.Inbox, null, modifier = Modifier.size(56.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
        Spacer(Modifier.height(12.dp))
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimelineScreen(viewModel: ArchiveViewModel, onBack: () -> Unit, onOpenDetail: (Long) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val archives by viewModel.archives.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { viewModel.refreshArchives() }

    Scaffold(
        containerColor = cs.background,
        topBar = {
            TopAppBar(
                title = { Text("时间轴", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = cs.background)
            )
        }
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            // search bar
            OutlinedTextField(
                value = query,
                onValueChange = { query = it; viewModel.refreshArchives(it) },
                placeholder = { Text("搜索联系人 / 摘要 / 待办") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                singleLine = true,
                shape = RoundedCornerShape(28.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = cs.primary,
                    unfocusedContainerColor = cs.surface,
                    focusedContainerColor = cs.surface
                ),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
            )
            if (archives.isEmpty()) {
                EmptyState(if (query.isBlank()) "还没有归档记录" else "没有匹配的记录")
            } else {
                // group by day for a true time-ordered timeline
                val groups = remember(archives) {
                    archives.sortedByDescending { it.createdAt }.groupBy { dayLabel(it.createdAt) }
                }
                LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp)) {
                    groups.forEach { (day, items) ->
                        item(key = "h_$day") {
                            Text(day, fontWeight = FontWeight.Bold, fontSize = 13.sp,
                                color = cs.primary, modifier = Modifier.padding(top = 16.dp, bottom = 4.dp))
                        }
                        items(items, key = { it.id }) { r ->
                            TimelineRow(r, onClick = { onOpenDetail(r.id) }, onDelete = { viewModel.deleteRecord(r.id) })
                        }
                    }
                }
            }
        }
    }
}

/** Returns 今天/昨天/yyyy-MM-dd for grouping. */
private fun dayLabel(ts: Long): String {
    val cal = java.util.Calendar.getInstance()
    val today = cal.clone() as java.util.Calendar
    today.set(java.util.Calendar.HOUR_OF_DAY, 0); today.set(java.util.Calendar.MINUTE, 0)
    today.set(java.util.Calendar.SECOND, 0); today.set(java.util.Calendar.MILLISECOND, 0)
    val todayStart = today.timeInMillis
    val dayMs = 24 * 60 * 60 * 1000L
    return when {
        ts >= todayStart -> "今天"
        ts >= todayStart - dayMs -> "昨天"
        else -> java.text.SimpleDateFormat("yyyy年MM月dd日", java.util.Locale.getDefault()).format(java.util.Date(ts))
    }
}

private fun timeOfDay(ts: Long): String =
    java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date(ts))

@Composable
private fun TimelineRow(r: ArchiveRecord, onClick: () -> Unit, onDelete: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val cat = com.alibaba.mnnllm.android.archive.data.ArchiveCategory.of(r.category)
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        // timeline rail: dot + connecting line
        Column(
            Modifier.width(36.dp).fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(Modifier.padding(top = 18.dp).size(12.dp).clip(RoundedCornerShape(6.dp)).background(cs.primary))
            Box(Modifier.width(2.dp).weight(1f).background(cs.primary.copy(alpha = 0.2f)))
        }
        Card(
            Modifier.weight(1f).padding(vertical = 5.dp).clickable(onClick = onClick),
            colors = CardDefaults.cardColors(containerColor = cs.surface),
            shape = RoundedCornerShape(16.dp)
        ) {
            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(cs.primaryContainer),
                    contentAlignment = Alignment.Center
                ) { Text(cat.emoji, fontSize = 20.sp) }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.clip(RoundedCornerShape(6.dp)).background(cs.primary.copy(alpha = 0.12f))
                            .padding(horizontal = 6.dp, vertical = 1.dp)) {
                            Text(cat.label, fontSize = 10.sp, color = cs.primary)
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(timeOfDay(r.createdAt), fontSize = 11.sp, color = cs.onSurfaceVariant)
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(r.summary.ifBlank { "(无摘要)" }, fontWeight = FontWeight.SemiBold, maxLines = 2)
                    val sub = listOfNotNull(
                        r.deadline?.takeIf { it.isNotBlank() }?.let { "⏰ $it" },
                        r.contact?.takeIf { it.isNotBlank() },
                        r.amount?.let { "¥$it" }
                    ).joinToString("  ")
                    if (sub.isNotEmpty()) Text(sub, fontSize = 12.sp, color = cs.onSurfaceVariant, maxLines = 1, modifier = Modifier.padding(top = 2.dp))
                    if (r.actionItems.isNotEmpty()) {
                        Text("待办 ${r.actionItems.size} 项", fontSize = 11.sp, color = cs.secondary, modifier = Modifier.padding(top = 2.dp))
                    }
                    if (r.tags.isNotEmpty()) {
                        Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            r.tags.take(3).forEach { tag ->
                                Box(Modifier.clip(RoundedCornerShape(8.dp)).background(cs.primary.copy(alpha = 0.10f))
                                    .padding(horizontal = 8.dp, vertical = 2.dp)) {
                                    Text(tag, fontSize = 11.sp, color = cs.primary)
                                }
                            }
                        }
                    }
                }
                IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, null, tint = cs.onSurfaceVariant.copy(alpha = 0.5f)) }
            }
        }
    }
}

@Composable
private fun TimelineCardUnused(r: ArchiveRecord, onClick: () -> Unit, onDelete: () -> Unit) {
    // removed; superseded by TimelineRow
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(viewModel: ArchiveViewModel, archiveId: Long, onBack: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    var record by remember { mutableStateOf<ArchiveRecord?>(null) }
    LaunchedEffect(archiveId) { viewModel.getArchive(archiveId) { record = it } }
    val context = androidx.compose.ui.platform.LocalContext.current
    var editing by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = cs.background,
        topBar = {
            TopAppBar(
                title = { Text("详情", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } },
                actions = {
                    record?.let { rec ->
                        IconButton(onClick = { editing = true }) { Icon(Icons.Default.Edit, "编辑") }
                        IconButton(onClick = { shareRecord(context, rec) }) { Icon(Icons.Default.Share, "分享") }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = cs.background)
            )
        }
    ) { pad ->
        val r = record
        if (r == null) {
            Box(Modifier.padding(pad).fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = cs.primary) }
            return@Scaffold
        }
        if (editing) {
            EditRecordDialog(r, onDismiss = { editing = false }, onSave = { updated ->
                viewModel.updateRecord(updated)
                record = updated
                editing = false
            })
        }
        LazyColumn(Modifier.padding(pad).fillMaxSize(), contentPadding = PaddingValues(16.dp)) {
            // header card with summary
            item {
                Card(
                    Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors()
                ) {
                    Column(
                        Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(cs.primary, cs.secondary))).padding(18.dp)
                    ) {
                        Text(srcEmoji(r.sourceType) + "  " + r.summary.ifBlank { "(无摘要)" },
                            color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        r.deadline?.takeIf { it.isNotBlank() }?.let {
                            Spacer(Modifier.height(6.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.CalendarMonth, null, tint = Color.White, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(it, color = Color.White.copy(alpha = 0.9f), fontSize = 13.sp)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
            }
            // image preview
            r.materialPath?.let { path ->
                if (File(path).exists() && r.sourceType != "audio") {
                    item {
                        val bmp = remember(path) { runCatching { BitmapFactory.decodeFile(path) }.getOrNull() }
                        bmp?.let {
                            Image(it.asImageBitmap(), null,
                                modifier = Modifier.fillMaxWidth().heightIn(max = 280.dp).clip(RoundedCornerShape(14.dp)),
                                contentScale = ContentScale.Fit)
                            Spacer(Modifier.height(14.dp))
                        }
                    }
                }
            }
            // fields card
            item {
                val cat = com.alibaba.mnnllm.android.archive.data.ArchiveCategory.of(r.category)
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = cs.surface)) {
                    Column(Modifier.padding(16.dp)) {
                        DetailRow("类型", cat.emoji + " " + cat.label)
                        if (r.contact != null) DetailRow("联系人", r.contact!!)
                        if (r.deadline != null) DetailRow("时间", r.deadline!!)
                        if (r.amount != null) DetailRow("金额", "¥${r.amount}")
                        if (r.address != null) DetailRow("地点", r.address!!)
                        if (r.tags.isNotEmpty()) DetailRow("标签", r.tags.joinToString("，"))
                        // scene-specific extra fields
                        r.extras.forEach { (k, v) -> DetailRow(k, v) }
                    }
                }
                Spacer(Modifier.height(14.dp))
            }
            // action items
            if (r.actionItems.isNotEmpty()) {
                item {
                    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = cs.surface)) {
                        Column(Modifier.padding(16.dp)) {
                            Text("待办事项", fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(8.dp))
                            r.actionItems.forEach {
                                Row(Modifier.padding(vertical = 3.dp)) {
                                    Text("•  ", color = cs.primary); Text(it)
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                }
            }
            // raw text
            item {
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = cs.surfaceVariant)) {
                    Column(Modifier.padding(16.dp)) {
                        Text("原始识别文字", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Spacer(Modifier.height(6.dp))
                        if (r.rawText.isBlank()) {
                            Text("(空)", fontSize = 13.sp, color = cs.onSurfaceVariant)
                        } else {
                            Text(
                                prettyRawText(r.rawText),
                                fontSize = 14.sp,
                                color = cs.onSurfaceVariant,
                                lineHeight = 22.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(label, modifier = Modifier.width(72.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
        Text(value, fontWeight = FontWeight.Medium)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodoScreen(viewModel: ArchiveViewModel, onBack: () -> Unit, onOpenDetail: (Long) -> Unit) {
    val cs = MaterialTheme.colorScheme
    var tabDone by remember { mutableStateOf(false) }
    val todos by viewModel.todos.collectAsStateWithLifecycle()
    LaunchedEffect(tabDone) { viewModel.refreshTodos(tabDone) }

    Scaffold(
        containerColor = cs.background,
        topBar = {
            TopAppBar(
                title = { Text("待办工作台", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = cs.background)
            )
        }
    ) { pad ->
        Column(Modifier.padding(pad)) {
            // segmented tabs
            Row(
                Modifier.fillMaxWidth().padding(16.dp).clip(RoundedCornerShape(12.dp)).background(cs.surfaceVariant).padding(4.dp)
            ) {
                SegTab("未完成", !tabDone, Modifier.weight(1f)) { tabDone = false }
                SegTab("已完成", tabDone, Modifier.weight(1f)) { tabDone = true }
            }
            if (todos.isEmpty()) {
                EmptyState(if (tabDone) "还没有完成的待办" else "暂无待办")
            } else {
                LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp)) {
                    items(todos, key = { it.id }) { t ->
                        TodoCard(t, tabDone, viewModel, onOpenDetail)
                    }
                }
            }
        }
    }
}

@Composable
private fun TodoCard(
    t: com.alibaba.mnnllm.android.archive.data.TodoItem,
    tabDone: Boolean,
    viewModel: ArchiveViewModel,
    onOpenDetail: (Long) -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val context = androidx.compose.ui.platform.LocalContext.current

    fun pickDeadlineThenCalendar() {
        val cal = java.util.Calendar.getInstance()
        android.app.DatePickerDialog(context, { _, y, m, d ->
            android.app.TimePickerDialog(context, { _, h, min ->
                val dl = String.format("%04d-%02d-%02d %02d:%02d", y, m + 1, d, h, min)
                viewModel.setTodoDeadline(t.id, dl, tabDone)
                // write to calendar + schedule reminder
                if (com.alibaba.mnnllm.android.archive.calendar.CalendarRepository
                        .insertEvent(context, t.archiveId, t.content, dl, null, 30) != null) {
                    com.alibaba.mnnllm.android.archive.reminder.ReminderScheduler
                        .schedule(context, t.archiveId, t.content, dl, 30)
                    android.widget.Toast.makeText(context, "已设时间并写入日历提醒", android.widget.Toast.LENGTH_SHORT).show()
                } else {
                    android.widget.Toast.makeText(context, "已设时间（日历写入失败，检查权限）", android.widget.Toast.LENGTH_SHORT).show()
                }
            }, cal.get(java.util.Calendar.HOUR_OF_DAY), cal.get(java.util.Calendar.MINUTE), true).show()
        }, cal.get(java.util.Calendar.YEAR), cal.get(java.util.Calendar.MONTH), cal.get(java.util.Calendar.DAY_OF_MONTH)).show()
    }

    Card(
        Modifier.fillMaxWidth().padding(vertical = 5.dp),
        colors = CardDefaults.cardColors(containerColor = cs.surface),
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = t.done, onCheckedChange = { c -> viewModel.toggleTodo(t.id, c, tabDone) })
            Column(Modifier.weight(1f).clickable { onOpenDetail(t.archiveId) }) {
                Text(t.content, fontWeight = FontWeight.Medium,
                    textDecoration = if (t.done) androidx.compose.ui.text.style.TextDecoration.LineThrough else null)
                if (!t.deadline.isNullOrBlank()) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                        Icon(Icons.Default.CalendarMonth, null, tint = cs.secondary, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(t.deadline!!, fontSize = 12.sp, color = cs.secondary)
                    }
                }
            }
            if (!t.done) {
                TextButton(onClick = { pickDeadlineThenCalendar() }) {
                    Icon(Icons.Default.AddAlert, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(if (t.deadline.isNullOrBlank()) "设提醒" else "改时间", fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
private fun SegTab(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Box(
        modifier.clip(RoundedCornerShape(10.dp))
            .background(if (selected) cs.surface else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) cs.primary else cs.onSurfaceVariant)
    }
}

/** Shares the record as formatted text to any channel (WeChat/notes/email/...). */
private fun shareRecord(context: android.content.Context, r: ArchiveRecord) {
    val cat = com.alibaba.mnnllm.android.archive.data.ArchiveCategory.of(r.category)
    val text = buildString {
        appendLine("【${cat.label}】${r.summary}")
        if (!r.contact.isNullOrBlank()) appendLine("联系人：${r.contact}")
        if (!r.deadline.isNullOrBlank()) appendLine("时间：${r.deadline}")
        if (r.amount != null) appendLine("金额：¥${r.amount}")
        if (!r.address.isNullOrBlank()) appendLine("地点：${r.address}")
        if (r.actionItems.isNotEmpty()) { appendLine("待办："); r.actionItems.forEach { appendLine("· $it") } }
        r.extras.forEach { (k, v) -> appendLine("$k：$v") }
        if (r.tags.isNotEmpty()) appendLine("标签：${r.tags.joinToString("，")}")
        append("—— 本地归档小助手")
    }.trim()
    val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(android.content.Intent.EXTRA_SUBJECT, r.summary)
        putExtra(android.content.Intent.EXTRA_TEXT, text)
    }
    context.startActivity(android.content.Intent.createChooser(send, "分享到"))
}

/** In-place editor for a saved record (secondary edit). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditRecordDialog(r: ArchiveRecord, onDismiss: () -> Unit, onSave: (ArchiveRecord) -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var summary by remember { mutableStateOf(r.summary) }
    var contact by remember { mutableStateOf(r.contact ?: "") }
    var deadline by remember { mutableStateOf(r.deadline ?: "") }
    var amount by remember { mutableStateOf(r.amount?.toString() ?: "") }
    var address by remember { mutableStateOf(r.address ?: "") }
    var actionItems by remember { mutableStateOf(r.actionItems.joinToString("\n")) }
    var tags by remember { mutableStateOf(r.tags.joinToString("，")) }

    fun pickDateTime() {
        val cal = java.util.Calendar.getInstance()
        android.app.DatePickerDialog(context, { _, y, m, d ->
            android.app.TimePickerDialog(context, { _, h, min ->
                deadline = String.format("%04d-%02d-%02d %02d:%02d", y, m + 1, d, h, min)
            }, cal.get(java.util.Calendar.HOUR_OF_DAY), cal.get(java.util.Calendar.MINUTE), true).show()
        }, cal.get(java.util.Calendar.YEAR), cal.get(java.util.Calendar.MONTH), cal.get(java.util.Calendar.DAY_OF_MONTH)).show()
    }

    fun build() = r.copy(
        summary = summary,
        contact = contact.ifBlank { null },
        deadline = deadline.ifBlank { null },
        amount = amount.toDoubleOrNull(),
        address = address.ifBlank { null },
        actionItems = actionItems.split("\n").map { it.trim() }.filter { it.isNotBlank() },
        tags = tags.split(",", "，").map { it.trim() }.filter { it.isNotBlank() }
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("编辑记录") },
        text = {
            androidx.compose.foundation.layout.Column(
                Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState())
            ) {
                OutlinedTextField(summary, { summary = it }, label = { Text("摘要") }, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp))
                OutlinedTextField(contact, { contact = it }, label = { Text("联系人") }, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp))
                OutlinedTextField(
                    deadline, {}, readOnly = true, label = { Text("时间") },
                    trailingIcon = { TextButton(onClick = { pickDateTime() }) { Text("选择") } },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                )
                OutlinedTextField(amount, { amount = it }, label = { Text("金额") }, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp))
                OutlinedTextField(address, { address = it }, label = { Text("地点") }, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp))
                OutlinedTextField(actionItems, { actionItems = it }, label = { Text("待办（每行一条）") }, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp))
                OutlinedTextField(tags, { tags = it }, label = { Text("标签（逗号分隔）") }, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp))
                // write to calendar / notes after editing
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        val rec = build()
                        if (rec.deadline.isNullOrBlank()) {
                            android.widget.Toast.makeText(context, "请先选择时间", android.widget.Toast.LENGTH_SHORT).show()
                        } else {
                            val ok = com.alibaba.mnnllm.android.archive.calendar.CalendarRepository
                                .insertEvent(context, r.id, rec.summary, rec.deadline, rec.address, 30) != null
                            if (ok) com.alibaba.mnnllm.android.archive.reminder.ReminderScheduler
                                .schedule(context, r.id, rec.summary, rec.deadline, 30)
                            android.widget.Toast.makeText(context, if (ok) "已写入日历" else "日历写入失败(检查权限)", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }, modifier = Modifier.weight(1f)) { Text("写日历", fontSize = 13.sp) }
                    OutlinedButton(onClick = { shareRecord(context, build()) }, modifier = Modifier.weight(1f)) { Text("存备忘录", fontSize = 13.sp) }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(build()) }) { Text("保存") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

