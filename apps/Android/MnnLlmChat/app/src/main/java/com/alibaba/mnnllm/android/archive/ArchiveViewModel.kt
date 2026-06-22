package com.alibaba.mnnllm.android.archive

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.alibaba.mls.api.download.ModelDownloadManager
import com.alibaba.mnnllm.android.archive.data.ArchiveDatabase
import com.alibaba.mnnllm.android.archive.data.ArchiveRecord
import com.alibaba.mnnllm.android.archive.data.TodoItem
import com.alibaba.mnnllm.android.archive.pipeline.ArchivePipeline
import com.alibaba.mnnllm.android.archive.pipeline.AudioTranscriber
import com.alibaba.mnnllm.android.archive.pipeline.StructuringEngine
import com.alibaba.mnnllm.android.modelsettings.ModelConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

sealed class ProcessingState {
    object Idle : ProcessingState()
    /** step: current 0-based step index; steps: full label list for the active flow. */
    data class Loading(val step: Int, val steps: List<String>) : ProcessingState() {
        val total: Int get() = steps.size
        val message: String get() = steps.getOrElse(step) { "" }
    }
    data class NeedModel(val modelId: String) : ProcessingState()
    data class Done(val record: ArchiveRecord) : ProcessingState()
    data class Error(val message: String) : ProcessingState()
}

/** Stage labels shown in the progress stepper. */
object ProcessSteps {
    val PHOTO = listOf("保存素材", "文字识别 (OCR)", "结构化分析", "完成")
    val AUDIO = listOf("保存录音", "语音转写", "结构化分析", "完成")
}

class ArchiveViewModel(app: Application) : AndroidViewModel(app) {

    private val db = ArchiveDatabase.get(app)
    private val pipeline = ArchivePipeline(app)
    private var structuringEngine: StructuringEngine? = null
    private var audioTranscriber: AudioTranscriber? = null

    private val _processing = MutableStateFlow<ProcessingState>(ProcessingState.Idle)
    val processing: StateFlow<ProcessingState> = _processing

    private val _archives = MutableStateFlow<List<ArchiveRecord>>(emptyList())
    val archives: StateFlow<List<ArchiveRecord>> = _archives

    private val _todos = MutableStateFlow<List<TodoItem>>(emptyList())
    val todos: StateFlow<List<TodoItem>> = _todos

    fun refreshArchives(query: String = "") {
        viewModelScope.launch(Dispatchers.IO) {
            _archives.value = if (query.isBlank()) db.listArchives() else db.search(query)
        }
    }

    fun refreshTodos(done: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            _todos.value = db.listTodos(done)
        }
    }

    fun toggleTodo(id: Long, done: Boolean, currentDone: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            db.setTodoDone(id, done)
            _todos.value = db.listTodos(currentDone)
        }
    }

    /** Sets a todo's deadline and refreshes the list. */
    fun setTodoDeadline(id: Long, deadline: String, currentDone: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            db.setTodoDeadline(id, deadline)
            _todos.value = db.listTodos(currentDone)
        }
    }

    /** Resolve the structuring model config path; null if not downloaded yet. */
    private fun resolveStructConfig(): String? {
        val modelId = ArchiveSettings.structuringModelId(getApplication())
        return ModelConfig.getDefaultConfigFile(modelId)
    }

    private fun ensureStructuringEngine(): StructuringEngine? {
        structuringEngine?.let { return it }
        val cfg = resolveStructConfig() ?: return null
        val modelId = ArchiveSettings.structuringModelId(getApplication())
        val e = StructuringEngine(modelId, cfg)
        structuringEngine = e
        return e
    }

    /** Process a single picked image through OCR + structuring. */
    fun processImage(uri: Uri, sourceType: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val steps = ProcessSteps.PHOTO
            try {
                _processing.value = ProcessingState.Loading(0, steps)
                val path = pipeline.persistMaterial(uri, "jpg")

                // Step 1: OCR (always runs, model is bundled)
                _processing.value = ProcessingState.Loading(1, steps)
                val text = pipeline.runOcr(path)

                val engine = ensureStructuringEngine()
                if (engine == null) {
                    val record = ArchiveRecord(
                        sourceType = sourceType, rawText = text,
                        summary = text.take(15),
                        lowConfidenceFields = listOf("summary", "action_items"),
                        materialPath = path, createdAt = System.currentTimeMillis()
                    )
                    _pendingRecord = record
                    _processing.value = ProcessingState.NeedModel(
                        ArchiveSettings.structuringModelId(getApplication())
                    )
                    return@launch
                }

                // Step 2: structuring
                _processing.value = ProcessingState.Loading(2, steps)
                val record = pipeline.processText(text, sourceType, engine, path)
                _pendingRecord = record
                _processing.value = ProcessingState.Done(record)
            } catch (e: Exception) {
                _processing.value = ProcessingState.Error(e.message ?: "处理失败")
            }
        }
    }

    /** Continue structuring after the user downloaded the model. */
    fun continueStructuring(sourceType: String) {
        val record = _pendingRecord ?: return
        val steps = ProcessSteps.PHOTO
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val engine = ensureStructuringEngine()
                    ?: run { _processing.value = ProcessingState.Error("模型仍不可用"); return@launch }
                _processing.value = ProcessingState.Loading(2, steps)
                val structured = pipeline.processText(record.rawText, sourceType, engine, record.materialPath)
                _pendingRecord = structured
                _processing.value = ProcessingState.Done(structured)
            } catch (e: Exception) {
                _processing.value = ProcessingState.Error(e.message ?: "处理失败")
            }
        }
    }

    private var _pendingRecord: ArchiveRecord? = null
    fun pendingRecord(): ArchiveRecord? = _pendingRecord
    fun updatePending(r: ArchiveRecord) { _pendingRecord = r }

    /** Process an audio file: Gemma transcription -> Qwen structuring (PRD V1.1). */
    fun processAudio(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val steps = ProcessSteps.AUDIO
            try {
                _processing.value = ProcessingState.Loading(0, steps)
                val path = pipeline.persistMaterial(uri, "m4a")

                val audioCfg = ModelConfig.getDefaultConfigFile(ArchiveSettings.audioModelId(getApplication()))
                if (audioCfg == null) {
                    val record = ArchiveRecord(
                        sourceType = "audio", rawText = "",
                        summary = "需要下载音频模型", lowConfidenceFields = listOf("summary"),
                        materialPath = path, createdAt = System.currentTimeMillis()
                    )
                    _pendingRecord = record
                    _processing.value = ProcessingState.NeedModel(ArchiveSettings.audioModelId(getApplication()))
                    return@launch
                }
                _processing.value = ProcessingState.Loading(1, steps)
                val transcriber = audioTranscriber ?: AudioTranscriber(
                    ArchiveSettings.audioModelId(getApplication()), audioCfg
                ).also { audioTranscriber = it }
                val text = transcriber.transcribe(path)

                val engine = ensureStructuringEngine()
                val record = if (engine != null) {
                    _processing.value = ProcessingState.Loading(2, steps)
                    pipeline.processText(text, "audio", engine, path)
                } else {
                    ArchiveRecord(sourceType = "audio", rawText = text, summary = text.take(15),
                        lowConfidenceFields = listOf("summary", "action_items"),
                        materialPath = path, createdAt = System.currentTimeMillis())
                }
                _pendingRecord = record
                _processing.value = ProcessingState.Done(record)
            } catch (e: Exception) {
                _processing.value = ProcessingState.Error(e.message ?: "音频处理失败")
            }
        }
    }


    /** Persist the confirmed record. Returns its new id. */
    fun saveRecord(record: ArchiveRecord, onSaved: (Long) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val id = db.insertArchive(record)
            refreshArchives()
            withContext(Dispatchers.Main) { onSaved(id) }
        }
    }

    fun getArchive(id: Long, cb: (ArchiveRecord?) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val r = db.getArchive(id)
            withContext(Dispatchers.Main) { cb(r) }
        }
    }

    fun updateRecord(record: ArchiveRecord) {
        viewModelScope.launch(Dispatchers.IO) {
            db.updateArchive(record)
            refreshArchives()
        }
    }

    fun deleteRecord(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            val path = db.deleteArchive(id)
            path?.let { runCatching { File(it).delete() } }
            refreshArchives()
        }
    }

    /** Loads a curated demo dataset for presentations (real DB inserts, real todos). */
    fun loadDemoData(onDone: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            com.alibaba.mnnllm.android.archive.data.DemoData.records(now).forEach { db.insertArchive(it) }
            refreshArchives()
            refreshTodos(false)
            withContext(Dispatchers.Main) { onDone() }
        }
    }

    fun wipeAll(deleteMaterials: Boolean) {        viewModelScope.launch(Dispatchers.IO) {
            if (deleteMaterials) {
                val records = db.listArchives()
                records.forEach { r -> r.materialPath?.let { runCatching { File(it).delete() } } }
            }
            db.wipeAll()
            refreshArchives()
            refreshTodos(false)
        }
    }

    fun resetProcessing() { _processing.value = ProcessingState.Idle }

    override fun onCleared() {
        super.onCleared()
        structuringEngine?.release()
        audioTranscriber?.release()
        pipeline.release()
    }
}
