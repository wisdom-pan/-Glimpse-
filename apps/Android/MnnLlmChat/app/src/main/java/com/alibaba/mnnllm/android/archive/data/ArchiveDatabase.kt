package com.alibaba.mnnllm.android.archive.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * Real on-device SQLite store for archive records and todos (PRD: Room/SQLite ledger).
 * Implemented with SQLiteOpenHelper to avoid an annotation processor in the existing build.
 */
class ArchiveDatabase private constructor(context: Context) :
    SQLiteOpenHelper(context.applicationContext, DB_NAME, null, DB_VERSION) {

    private val gson = Gson()
    private val strListType = object : TypeToken<List<String>>() {}.type
    private val strMapType = object : TypeToken<Map<String, String>>() {}.type

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE archive(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                category TEXT,
                contact TEXT,
                action_items TEXT,
                deadline TEXT,
                amount REAL,
                address TEXT,
                summary TEXT,
                tags TEXT,
                extras TEXT,
                source_type TEXT,
                raw_text TEXT,
                json_raw TEXT,
                material_path TEXT,
                created_at INTEGER
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE todo(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                archive_id INTEGER,
                content TEXT,
                done INTEGER,
                done_at INTEGER,
                deadline TEXT
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX idx_todo_archive ON todo(archive_id)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS archive")
        db.execSQL("DROP TABLE IF EXISTS todo")
        onCreate(db)
    }

    // ---- Archive CRUD ----

    fun insertArchive(r: ArchiveRecord): Long {
        val cv = ContentValues().apply {
            put("category", r.category)
            put("contact", r.contact)
            put("action_items", gson.toJson(r.actionItems))
            put("deadline", r.deadline)
            r.amount?.let { put("amount", it) }
            put("address", r.address)
            put("summary", r.summary)
            put("tags", gson.toJson(r.tags))
            put("extras", gson.toJson(r.extras))
            put("source_type", r.sourceType)
            put("raw_text", r.rawText)
            put("json_raw", r.jsonRaw)
            put("material_path", r.materialPath)
            put("created_at", if (r.createdAt > 0) r.createdAt else System.currentTimeMillis())
        }
        val id = writableDatabase.insert("archive", null, cv)
        // auto-generate todos from action items
        for (item in r.actionItems) {
            if (item.isBlank()) continue
            val tcv = ContentValues().apply {
                put("archive_id", id)
                put("content", item)
                put("done", 0)
                put("deadline", r.deadline)
            }
            writableDatabase.insert("todo", null, tcv)
        }
        return id
    }

    fun updateArchive(r: ArchiveRecord) {
        val cv = ContentValues().apply {
            put("category", r.category)
            put("contact", r.contact)
            put("action_items", gson.toJson(r.actionItems))
            put("deadline", r.deadline)
            r.amount?.let { put("amount", it) } ?: putNull("amount")
            put("address", r.address)
            put("summary", r.summary)
            put("tags", gson.toJson(r.tags))
            put("extras", gson.toJson(r.extras))
            put("source_type", r.sourceType)
            put("raw_text", r.rawText)
            put("json_raw", r.jsonRaw)
            put("material_path", r.materialPath)
        }
        writableDatabase.update("archive", cv, "id=?", arrayOf(r.id.toString()))
    }

    fun deleteArchive(id: Long): String? {
        // returns material path so caller can delete the file
        var path: String? = null
        readableDatabase.rawQuery("SELECT material_path FROM archive WHERE id=?", arrayOf(id.toString())).use {
            if (it.moveToFirst()) path = it.getString(0)
        }
        writableDatabase.delete("todo", "archive_id=?", arrayOf(id.toString()))
        writableDatabase.delete("archive", "id=?", arrayOf(id.toString()))
        return path
    }

    fun getArchive(id: Long): ArchiveRecord? {
        readableDatabase.rawQuery("SELECT * FROM archive WHERE id=?", arrayOf(id.toString())).use {
            return if (it.moveToFirst()) cursorToArchive(it) else null
        }
    }

    /** Newest first. */
    fun listArchives(): List<ArchiveRecord> {
        val list = ArrayList<ArchiveRecord>()
        readableDatabase.rawQuery("SELECT * FROM archive ORDER BY created_at DESC", null).use {
            while (it.moveToNext()) list.add(cursorToArchive(it))
        }
        return list
    }

    /** Global search by contact / summary / action item content. */
    fun search(query: String): List<ArchiveRecord> {
        val like = "%$query%"
        val ids = LinkedHashSet<Long>()
        readableDatabase.rawQuery(
            "SELECT id FROM archive WHERE contact LIKE ? OR summary LIKE ? OR raw_text LIKE ? ORDER BY created_at DESC",
            arrayOf(like, like, like)
        ).use { while (it.moveToNext()) ids.add(it.getLong(0)) }
        readableDatabase.rawQuery(
            "SELECT DISTINCT archive_id FROM todo WHERE content LIKE ?", arrayOf(like)
        ).use { while (it.moveToNext()) ids.add(it.getLong(0)) }
        return ids.mapNotNull { getArchive(it) }
    }

    private fun cursorToArchive(c: Cursor): ArchiveRecord {
        fun str(name: String) = c.getString(c.getColumnIndexOrThrow(name))
        return ArchiveRecord(
            id = c.getLong(c.getColumnIndexOrThrow("id")),
            category = str("category") ?: "other",
            contact = str("contact"),
            actionItems = parseList(str("action_items")),
            deadline = str("deadline"),
            amount = c.getColumnIndexOrThrow("amount").let { idx -> if (c.isNull(idx)) null else c.getDouble(idx) },
            address = str("address"),
            summary = str("summary") ?: "",
            tags = parseList(str("tags")),
            extras = parseMap(str("extras")),
            sourceType = str("source_type") ?: "photo",
            rawText = str("raw_text") ?: "",
            jsonRaw = str("json_raw") ?: "",
            materialPath = str("material_path"),
            createdAt = c.getLong(c.getColumnIndexOrThrow("created_at"))
        )
    }

    private fun parseList(json: String?): List<String> {
        if (json.isNullOrBlank()) return emptyList()
        return try { gson.fromJson(json, strListType) ?: emptyList() } catch (e: Exception) { emptyList() }
    }

    private fun parseMap(json: String?): Map<String, String> {
        if (json.isNullOrBlank()) return emptyMap()
        return try { gson.fromJson(json, strMapType) ?: emptyMap() } catch (e: Exception) { emptyMap() }
    }

    // ---- Todo ----

    fun listTodos(done: Boolean): List<TodoItem> {
        val list = ArrayList<TodoItem>()
        readableDatabase.rawQuery(
            "SELECT * FROM todo WHERE done=? ORDER BY id DESC",
            arrayOf(if (done) "1" else "0")
        ).use {
            while (it.moveToNext()) list.add(cursorToTodo(it))
        }
        return list
    }

    fun setTodoDone(id: Long, done: Boolean) {
        val cv = ContentValues().apply {
            put("done", if (done) 1 else 0)
            if (done) put("done_at", System.currentTimeMillis()) else putNull("done_at")
        }
        writableDatabase.update("todo", cv, "id=?", arrayOf(id.toString()))
    }

    fun setTodoDeadline(id: Long, deadline: String?) {
        val cv = ContentValues().apply {
            if (deadline.isNullOrBlank()) putNull("deadline") else put("deadline", deadline)
        }
        writableDatabase.update("todo", cv, "id=?", arrayOf(id.toString()))
    }

    fun getTodo(id: Long): TodoItem? {
        readableDatabase.rawQuery("SELECT * FROM todo WHERE id=?", arrayOf(id.toString())).use {
            return if (it.moveToFirst()) cursorToTodo(it) else null
        }
    }

    private fun cursorToTodo(c: Cursor): TodoItem {
        return TodoItem(
            id = c.getLong(c.getColumnIndexOrThrow("id")),
            archiveId = c.getLong(c.getColumnIndexOrThrow("archive_id")),
            content = c.getString(c.getColumnIndexOrThrow("content")) ?: "",
            done = c.getInt(c.getColumnIndexOrThrow("done")) == 1,
            doneAt = c.getColumnIndexOrThrow("done_at").let { idx -> if (c.isNull(idx)) null else c.getLong(idx) },
            deadline = c.getString(c.getColumnIndexOrThrow("deadline"))
        )
    }

    fun wipeAll() {
        writableDatabase.delete("todo", null, null)
        writableDatabase.delete("archive", null, null)
    }

    companion object {
        private const val DB_NAME = "archive.db"
        private const val DB_VERSION = 2

        @Volatile private var instance: ArchiveDatabase? = null

        fun get(context: Context): ArchiveDatabase =
            instance ?: synchronized(this) {
                instance ?: ArchiveDatabase(context).also { instance = it }
            }
    }
}
