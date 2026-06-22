package com.alibaba.mnnllm.android.archive.export

import com.alibaba.mnnllm.android.archive.data.ArchiveRecord

/**
 * Export utilities (PRD module 8, V1.2): single-record Markdown, batch CSV, with optional
 * desensitization (hide the middle 4 digits/characters of sensitive fields).
 */
object ArchiveExporter {

    fun toMarkdown(r: ArchiveRecord, desensitize: Boolean = false): String {
        fun s(v: String?) = if (desensitize) Desensitizer.mask(v) else (v ?: "")
        val sb = StringBuilder()
        sb.appendLine("# ${r.summary}")
        sb.appendLine()
        if (!r.contact.isNullOrBlank()) sb.appendLine("- 联系人：${s(r.contact)}")
        if (!r.deadline.isNullOrBlank()) sb.appendLine("- 截止时间：${r.deadline}")
        if (r.amount != null) sb.appendLine("- 金额：${if (desensitize) "***" else r.amount}")
        if (!r.address.isNullOrBlank()) sb.appendLine("- 地点：${s(r.address)}")
        if (r.actionItems.isNotEmpty()) {
            sb.appendLine("- 待办：")
            r.actionItems.forEach { sb.appendLine("  - [ ] $it") }
        }
        if (r.tags.isNotEmpty()) sb.appendLine("- 标签：${r.tags.joinToString("，")}")
        sb.appendLine("- 来源：${r.sourceType}")
        return sb.toString()
    }

    fun toCsv(records: List<ArchiveRecord>, desensitize: Boolean = false): String {
        fun esc(v: String?): String {
            val s = (v ?: "").replace("\"", "\"\"")
            return "\"$s\""
        }
        fun maybe(v: String?) = if (desensitize) Desensitizer.mask(v) else v
        val sb = StringBuilder()
        sb.appendLine("summary,contact,deadline,amount,address,action_items,tags,source_type")
        for (r in records) {
            sb.append(esc(r.summary)).append(",")
            sb.append(esc(maybe(r.contact))).append(",")
            sb.append(esc(r.deadline)).append(",")
            sb.append(esc(if (desensitize && r.amount != null) "***" else r.amount?.toString())).append(",")
            sb.append(esc(maybe(r.address))).append(",")
            sb.append(esc(r.actionItems.joinToString("；"))).append(",")
            sb.append(esc(r.tags.joinToString("；"))).append(",")
            sb.append(esc(r.sourceType)).appendLine()
        }
        return sb.toString()
    }
}

/** Masks the middle of a sensitive string, keeping head/tail visible (PRD 脱敏). */
object Desensitizer {

    private val PHONE = Regex("(\\d{3})\\d{4}(\\d{4})")

    fun mask(value: String?): String {
        if (value.isNullOrBlank()) return value ?: ""
        // phone numbers -> keep first 3 + last 4
        val phoneMasked = PHONE.replace(value) { m -> "${m.groupValues[1]}****${m.groupValues[2]}" }
        if (phoneMasked != value) return phoneMasked
        // generic: hide the middle 4 chars
        if (value.length <= 4) return "*".repeat(value.length)
        val keep = (value.length - 4) / 2
        val head = value.substring(0, keep.coerceAtLeast(1))
        val tail = value.substring(value.length - keep.coerceAtLeast(1))
        return "$head****$tail"
    }
}
