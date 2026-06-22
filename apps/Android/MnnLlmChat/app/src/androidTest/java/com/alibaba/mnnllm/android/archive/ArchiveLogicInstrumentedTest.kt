package com.alibaba.mnnllm.android.archive

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.alibaba.mnnllm.android.archive.data.ArchiveDatabase
import com.alibaba.mnnllm.android.archive.data.ArchiveRecord
import com.alibaba.mnnllm.android.archive.export.ArchiveExporter
import com.alibaba.mnnllm.android.archive.export.Desensitizer
import com.alibaba.mnnllm.android.archive.pipeline.StructuringPrompt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * End-to-end logic tests for the archive feature (no mock): real SQLite DB, real JSON parsing,
 * real export/desensitize. Runs on device alongside the OCR native test.
 */
@RunWith(AndroidJUnit4::class)
class ArchiveLogicInstrumentedTest {

    private val ctx get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun structuringParsesModelJson() {
        val output = """好的，结果如下：{"contact":"张总","action_items":["准备合同","发邮件"],
            "deadline":"2026-06-25 18:00","amount":299.0,"address":"A座3楼","summary":"与客户确认合同",
            "tags":["工作","合同"],"source_type":"chat"} 以上。"""
        val r = StructuringPrompt.parse(output, "原始文本", "chat")
        assertEquals("张总", r.contact)
        assertEquals(2, r.actionItems.size)
        assertEquals("2026-06-25 18:00", r.deadline)
        assertEquals(299.0, r.amount!!, 0.001)
        assertEquals("与客户确认合同", r.summary)
        assertTrue(r.tags.contains("合同"))
    }

    @Test
    fun structuringFallsBackOnGarbage() {
        val r = StructuringPrompt.parse("模型没有返回 JSON", "一些原始文字内容", "photo")
        // human-in-the-loop fallback: keep raw text, flag low confidence
        assertTrue(r.summary.isNotEmpty())
        assertTrue(r.lowConfidenceFields.isNotEmpty())
    }

    @Test
    fun fuzzyDeadlineDetected() {
        val output = """{"contact":null,"action_items":["开会"],"deadline":null,"summary":"下周开会","tags":[],"source_type":"chat"}"""
        val r = StructuringPrompt.parse(output, "我们下周再开个会", "chat")
        assertTrue("fuzzy deadline should be flagged", r.deadlineFuzzy)
    }

    @Test
    fun databaseCrudAndTodoGeneration() {
        val db = ArchiveDatabase.get(ctx)
        db.wipeAll()
        val id = db.insertArchive(
            ArchiveRecord(
                contact = "李总", actionItems = listOf("写方案", "约会议"),
                deadline = "2026-07-01 10:00", summary = "项目启动", tags = listOf("工作"),
                sourceType = "chat", rawText = "李总让我写方案", createdAt = System.currentTimeMillis()
            )
        )
        assertTrue(id > 0)
        val got = db.getArchive(id)
        assertNotNull(got)
        assertEquals("李总", got!!.contact)
        assertEquals(2, got.actionItems.size)

        // todos auto-generated from action items
        val todos = db.listTodos(done = false)
        assertEquals(2, todos.size)
        db.setTodoDone(todos[0].id, true)
        assertEquals(1, db.listTodos(done = false).size)
        assertEquals(1, db.listTodos(done = true).size)

        // search by contact / summary / todo content
        assertTrue(db.search("李总").any { it.id == id })
        assertTrue(db.search("项目").any { it.id == id })
        assertTrue(db.search("写方案").any { it.id == id })

        // delete removes archive + todos
        db.deleteArchive(id)
        assertTrue(db.getArchive(id) == null)
        assertTrue(db.listTodos(false).isEmpty() && db.listTodos(true).isEmpty())
    }

    @Test
    fun exportAndDesensitize() {
        val rec = ArchiveRecord(
            contact = "13812345678", actionItems = listOf("付款"),
            amount = 299.0, address = "深圳市南山区科技园", summary = "缴费", tags = listOf("财务"),
            sourceType = "invoice"
        )
        val md = ArchiveExporter.toMarkdown(rec)
        assertTrue(md.contains("缴费"))
        assertTrue(md.contains("13812345678"))

        val mdSafe = ArchiveExporter.toMarkdown(rec, desensitize = true)
        assertFalse("phone should be masked", mdSafe.contains("13812345678"))
        assertTrue(mdSafe.contains("138") && mdSafe.contains("5678"))

        val csv = ArchiveExporter.toCsv(listOf(rec))
        assertTrue(csv.lines().first().contains("summary"))
        assertTrue(csv.contains("缴费"))

        // phone masking keeps head/tail
        assertEquals("138****5678", Desensitizer.mask("13812345678"))
    }
}
