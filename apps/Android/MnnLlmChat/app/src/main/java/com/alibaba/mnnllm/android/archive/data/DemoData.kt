package com.alibaba.mnnllm.android.archive.data

/**
 * Demo dataset for presentations — covers every scene category with realistic content,
 * spread across several days so the timeline grouping, todos, tags and scene-specific
 * extras all showcase well. Inserted on demand from Settings.
 */
object DemoData {

    private const val DAY = 24 * 60 * 60 * 1000L
    private const val HOUR = 60 * 60 * 1000L

    fun records(now: Long): List<ArchiveRecord> = listOf(
        // ---- 今天 ----
        ArchiveRecord(
            category = "chat",
            summary = "张总确认合同今天发出",
            contact = "张总",
            actionItems = listOf("整理合同终稿", "今天18点前发邮件给张总", "抄送财务备案"),
            deadline = fmt(now + 6 * HOUR),
            tags = listOf("工作", "合同", "紧急"),
            sourceType = "photo",
            rawText = "张总：合同改好了吗？今天下班前一定要发我邮箱，记得抄送财务。",
            createdAt = now - 1 * HOUR
        ),
        ArchiveRecord(
            category = "invoice",
            summary = "星巴克会议茶歇发票",
            amount = 268.0,
            address = "星巴克 深圳科技园店",
            tags = listOf("报销", "餐饮"),
            extras = mapOf("商户" to "星巴克(科技园店)", "项目" to "团队茶歇", "发票号" to "04420251号"),
            deadline = fmt(now + 3 * DAY),
            actionItems = listOf("月底前提交报销"),
            sourceType = "photo",
            rawText = "电子发票 星巴克 金额¥268.00 项目:餐饮服务 发票号04420251 开票日期2026-06-22",
            createdAt = now - 3 * HOUR
        ),
        // ---- 昨天 ----
        ArchiveRecord(
            category = "meeting",
            summary = "Q3产品评审会纪要",
            contact = "产品组",
            actionItems = listOf("OCR准确率提升到98%", "下周三前出v2.0设计稿", "对接算法团队评估端侧延迟"),
            deadline = fmt(now - 1 * DAY + 14 * HOUR),
            address = "A座3楼会议室",
            tags = listOf("会议", "产品", "Q3"),
            sourceType = "audio",
            rawText = "今天Q3产品评审,重点三件事:第一OCR准确率要冲到98%以上;第二v2.0设计稿下周三前给到;第三让算法团队评估一下端侧推理延迟。",
            createdAt = now - 1 * DAY - 2 * HOUR
        ),
        ArchiveRecord(
            category = "card",
            summary = "李明 名片",
            contact = "李明",
            tags = listOf("人脉", "客户"),
            extras = mapOf(
                "姓名" to "李明",
                "电话" to "13812345678",
                "公司" to "杭州智链科技有限公司",
                "职位" to "技术总监",
                "邮箱" to "liming@zhilian.com"
            ),
            sourceType = "photo",
            rawText = "李明 技术总监 杭州智链科技有限公司 Tel:138-1234-5678 Email:liming@zhilian.com",
            createdAt = now - 1 * DAY - 5 * HOUR
        ),
        // ---- 前几天 ----
        ArchiveRecord(
            category = "note",
            summary = "周末采购清单",
            actionItems = listOf("买打印纸A4两包", "续费域名", "预订周五团建餐厅"),
            tags = listOf("生活", "待办"),
            sourceType = "photo",
            rawText = "手写便签:1.A4纸2包 2.域名要续费了 3.订周五团建的餐厅",
            createdAt = now - 3 * DAY
        ),
        ArchiveRecord(
            category = "chat",
            summary = "客户王姐催进度",
            contact = "王姐",
            actionItems = listOf("周五前给王姐项目进度同步"),
            deadline = fmt(now + 2 * DAY + 10 * HOUR),
            tags = listOf("工作", "客户"),
            sourceType = "photo",
            rawText = "王姐:项目现在到哪一步啦?周五能不能给我一个进度同步?客户那边在催。",
            createdAt = now - 4 * DAY
        ),
        ArchiveRecord(
            category = "invoice",
            summary = "滴滴出行行程发票",
            amount = 56.8,
            address = "深圳",
            tags = listOf("报销", "交通"),
            extras = mapOf("商户" to "滴滴出行", "项目" to "市内交通"),
            sourceType = "photo",
            rawText = "滴滴出行 电子发票 ¥56.80 行程:科技园-会展中心 2026-06-18",
            createdAt = now - 5 * DAY
        )
    )

    private fun fmt(ts: Long): String =
        java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
            .format(java.util.Date(ts))
}
