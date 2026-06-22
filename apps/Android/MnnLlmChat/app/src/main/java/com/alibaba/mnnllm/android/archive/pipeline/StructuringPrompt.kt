package com.alibaba.mnnllm.android.archive.pipeline

import com.alibaba.mnnllm.android.archive.data.ArchiveRecord
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.util.regex.Pattern

/**
 * Builds the structuring prompt for the LLM and parses its JSON output into an ArchiveRecord.
 * Field schema follows PRD 2.2; scene emphasis follows PRD 2.3.
 */
object StructuringPrompt {

    private val gson = Gson()

    // Fuzzy time expressions that require user confirmation via date picker (PRD module 3).
    private val FUZZY_TIME = listOf(
        "下周", "下星期", "月底", "月初", "下个月", "周末", "近期", "过几天",
        "改天", "回头", "晚点", "稍后", "下下周", "年底", "明年"
    )

    fun buildPrompt(rawText: String, sourceType: String): String {
        return """你是一个碎片化信息整理助手。请先判断文本属于哪种场景，再按场景提取结构化信息。只输出一个 JSON 对象，不要输出解释、前缀或 markdown 代码块。

第一步 判断 category（场景），从以下选一个：
- chat（聊天记录/微信对话/工作指令）
- meeting（会议纪要/通知/日程）
- card（名片，含姓名电话公司职位）
- invoice（发票/账单/收据/转账记录）
- note（手写便签/备忘）
- other（其他）

第二步 按场景填写 JSON 字段：
- category: string，上面六选一
- summary: string，15字以内中文摘要（必填）
- contact: string，人名/对话对象/商户名，无则 null
- action_items: string数组，待办事项，无则 []
- deadline: string，"yyyy-MM-dd HH:mm"，无法确定则 null（不要编造）
- amount: number，金额数字，无则 null
- address: string，地点，无则 null
- tags: string数组，2-4个分类标签（如 ["工作","合同"]）
- extras: object，场景专属字段的键值对：
  · card 名片 → {"姓名":..,"电话":..,"公司":..,"职位":..,"邮箱":..}
  · invoice 票据 → {"商户":..,"项目":..,"发票号":..}
  · 其他场景 → {}

各场景提取重点：聊天→action_items/deadline/contact；会议→summary/action_items/deadline；名片→extras里的联系信息；票据→amount/extras里的商户项目；便签→action_items/deadline。

待整理文本：
\"\"\"
$rawText
\"\"\"

只输出 JSON："""
    }

    /** Extracts the JSON object from model output and parses it into a categorized record. */
    fun parse(modelOutput: String, rawText: String, sourceType: String): ArchiveRecord {
        val json = extractJsonObject(modelOutput)
        val record = ArchiveRecord(
            sourceType = sourceType,
            rawText = rawText,
            jsonRaw = modelOutput,
            createdAt = System.currentTimeMillis()
        )
        if (json == null) {
            record.summary = rawText.take(15)
            record.lowConfidenceFields = listOf("summary", "action_items")
            return record
        }
        try {
            val obj = JsonParser.parseString(json).asJsonObject
            record.category = (obj.optString("category") ?: "other").let { c ->
                if (c in listOf("chat", "meeting", "card", "invoice", "note", "other")) c else "other"
            }
            record.contact = obj.optString("contact")
            record.actionItems = obj.optStringList("action_items")
            record.deadline = obj.optString("deadline")
            record.amount = obj.optDouble("amount")
            record.address = obj.optString("address")
            record.summary = obj.optString("summary") ?: rawText.take(15)
            record.tags = obj.optStringList("tags")
            record.extras = obj.optStringMap("extras")
        } catch (e: Exception) {
            record.summary = rawText.take(15)
            record.lowConfidenceFields = listOf("summary")
        }

        if (record.deadline.isNullOrBlank()) {
            if (FUZZY_TIME.any { rawText.contains(it) }) {
                record.deadlineFuzzy = true
                record.lowConfidenceFields = record.lowConfidenceFields + "deadline"
            }
        }
        if (record.summary.isBlank()) {
            record.lowConfidenceFields = record.lowConfidenceFields + "summary"
        }
        return record
    }

    private fun extractJsonObject(raw: String): String? {
        // Strip <think>…</think> reasoning blocks some Qwen3 builds emit even with /no_think.
        var text = raw.replace(Regex("(?s)<think>.*?</think>"), "")
        text = text.replace(Regex("(?s)<think>.*"), "")  // unclosed think block
        // Find the LAST balanced {...} object (final answer comes after any reasoning).
        var result: String? = null
        var i = 0
        while (i < text.length) {
            if (text[i] == '{') {
                val obj = scanBalanced(text, i)
                if (obj != null) { result = obj; i += obj.length; continue }
            }
            i++
        }
        return result
    }

    private fun scanBalanced(text: String, start: Int): String? {
        var depth = 0
        var inStr = false
        var esc = false
        for (i in start until text.length) {
            val c = text[i]
            if (inStr) {
                if (esc) esc = false
                else if (c == '\\') esc = true
                else if (c == '"') inStr = false
            } else {
                when (c) {
                    '"' -> inStr = true
                    '{' -> depth++
                    '}' -> { depth--; if (depth == 0) return text.substring(start, i + 1) }
                }
            }
        }
        return null
    }

    private fun JsonObject.optString(key: String): String? {
        if (!has(key) || get(key).isJsonNull) return null
        val s = get(key).asString
        return if (s.isBlank() || s == "null") null else s
    }

    private fun JsonObject.optDouble(key: String): Double? {
        if (!has(key) || get(key).isJsonNull) return null
        return try { get(key).asDouble } catch (e: Exception) { null }
    }

    private fun JsonObject.optStringList(key: String): List<String> {
        if (!has(key) || get(key).isJsonNull) return emptyList()
        return try {
            val arr = get(key).asJsonArray
            arr.mapNotNull { if (it.isJsonNull) null else it.asString }.filter { it.isNotBlank() }
        } catch (e: Exception) { emptyList() }
    }

    private fun JsonObject.optStringMap(key: String): Map<String, String> {
        if (!has(key) || get(key).isJsonNull) return emptyMap()
        return try {
            val o = get(key).asJsonObject
            val m = LinkedHashMap<String, String>()
            for ((k, v) in o.entrySet()) {
                if (v.isJsonNull) continue
                val s = try { v.asString } catch (e: Exception) { v.toString() }
                if (s.isNotBlank() && s != "null") m[k] = s
            }
            m
        } catch (e: Exception) { emptyMap() }
    }
}
