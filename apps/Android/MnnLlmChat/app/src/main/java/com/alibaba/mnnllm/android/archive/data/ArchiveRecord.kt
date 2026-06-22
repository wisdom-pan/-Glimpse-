package com.alibaba.mnnllm.android.archive.data

/**
 * A structured archive record. Designed for fragmented-info organization: the model first
 * classifies the [category] (scene), then fills the common fields plus scene-specific [extras].
 */
data class ArchiveRecord(
    var id: Long = 0,
    /** Auto-detected scene: chat / meeting / card / invoice / note / other. Drives display + extraction. */
    var category: String = "other",
    var contact: String? = null,
    var actionItems: List<String> = emptyList(),
    var deadline: String? = null,       // "yyyy-MM-dd HH:mm" or null
    var amount: Double? = null,
    var address: String? = null,
    var summary: String = "",
    var tags: List<String> = emptyList(),
    /** Scene-specific extra fields, e.g. card: {姓名,电话,公司,职位}; invoice: {商户,项目}. */
    var extras: Map<String, String> = emptyMap(),
    var sourceType: String = "photo",   // photo / audio (how it was imported)
    var rawText: String = "",           // OCR/ASR raw text
    var jsonRaw: String = "",           // raw model JSON output (for debugging/edit)
    var materialPath: String? = null,   // original image/audio file in private dir
    var createdAt: Long = 0,
    /** field names whose model confidence was low — UI highlights them. */
    var lowConfidenceFields: List<String> = emptyList(),
    /** true if deadline came from a fuzzy expression and needs user confirmation. */
    var deadlineFuzzy: Boolean = false
)

/** Scene metadata: label, emoji, and which extra fields to collect. */
enum class ArchiveCategory(
    val key: String,
    val label: String,
    val emoji: String,
    val extraFields: List<String>
) {
    CHAT("chat", "聊天记录", "💬", emptyList()),
    MEETING("meeting", "会议纪要", "📝", emptyList()),
    CARD("card", "名片", "💳", listOf("姓名", "电话", "公司", "职位", "邮箱")),
    INVOICE("invoice", "票据发票", "🧾", listOf("商户", "项目", "发票号")),
    NOTE("note", "手写便签", "🗒️", emptyList()),
    OTHER("other", "其他", "📋", emptyList());

    companion object {
        fun of(key: String?): ArchiveCategory = entries.firstOrNull { it.key == key } ?: OTHER
    }
}

/** A todo item derived from an archive record's action items. */
data class TodoItem(
    var id: Long = 0,
    var archiveId: Long = 0,
    var content: String = "",
    var done: Boolean = false,
    var doneAt: Long? = null,
    var deadline: String? = null
)

