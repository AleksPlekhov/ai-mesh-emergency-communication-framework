package com.bitchat.android.features.voice

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import java.io.File
import java.net.URL
import java.util.zip.ZipFile

private const val TAG = "VoskManager"
private const val MODEL_NAME = "vosk-model-small-en-us-0.15"
private const val MODEL_URL = "https://alphacephei.com/vosk/models/$MODEL_NAME.zip"
private const val SAMPLE_RATE = 16000f

sealed class VoskState {
    object Idle : VoskState()
    data class Downloading(val progress: Int) : VoskState() // 0-100
    object Initializing : VoskState()
    object Ready : VoskState()
    object Listening : VoskState()
    data class Error(val message: String) : VoskState()
}

class VoskManager(private val context: Context) {

    val modelDir: File get() = File(context.filesDir, MODEL_NAME)

    private val _state = MutableStateFlow<VoskState>(VoskState.Idle)
    val state: StateFlow<VoskState> = _state.asStateFlow()

    private var model: Model? = null
    private var speechService: SpeechService? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun isModelAvailable(): Boolean =
        modelDir.exists() && modelDir.isDirectory && modelDir.list()?.isNotEmpty() == true

    /**
     * Ensures the model is downloaded and loaded, then starts listening.
     * Safe to call from any thread.
     */
    fun startTranscription(
        onPartial: (String) -> Unit,
        onResult: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        val current = _state.value
        if (current is VoskState.Downloading || current is VoskState.Initializing) return

        scope.launch {
            // 1. Download model if needed
            if (!isModelAvailable()) {
                downloadModel()
            }
            if (_state.value is VoskState.Error) return@launch

            // 2. Load model if not already in memory
            if (model == null) {
                loadModel(onError) ?: return@launch
            }

            // 3. Start listening (must run on main thread for SpeechService)
            withContext(Dispatchers.Main) {
                startListeningInternal(onPartial, onResult, onError)
            }
        }
    }

    fun stopListening() {
        speechService?.stop()
        speechService = null
        if (_state.value is VoskState.Listening) {
            _state.value = VoskState.Ready
        }
    }

    fun release() {
        stopListening()
        model?.close()
        model = null
        scope.cancel()
        _state.value = VoskState.Idle
    }

    // ---- private ----

    private suspend fun downloadModel() {
        _state.value = VoskState.Downloading(0)
        val zipFile = File(context.cacheDir, "$MODEL_NAME.zip")
        try {
            val connection = URL(MODEL_URL).openConnection()
            connection.connect()
            val total = connection.contentLength.toLong()
            connection.getInputStream().use { input ->
                zipFile.outputStream().use { output ->
                    val buf = ByteArray(8192)
                    var downloaded = 0L
                    var n: Int
                    while (input.read(buf).also { n = it } != -1) {
                        output.write(buf, 0, n)
                        downloaded += n
                        val pct = if (total > 0) ((downloaded * 100) / total).toInt().coerceIn(0, 99) else 0
                        _state.value = VoskState.Downloading(pct)
                    }
                }
            }
            _state.value = VoskState.Downloading(100)
            unzip(zipFile, context.filesDir)
        } catch (e: Exception) {
            Log.e(TAG, "Download failed", e)
            _state.value = VoskState.Error("Download failed: ${e.message}")
        } finally {
            zipFile.delete()
        }
    }

    private fun unzip(zipFile: File, targetDir: File) {
        ZipFile(zipFile).use { zip ->
            zip.entries().asSequence().forEach { entry ->
                val outFile = File(targetDir, entry.name)
                if (entry.isDirectory) {
                    outFile.mkdirs()
                } else {
                    outFile.parentFile?.mkdirs()
                    zip.getInputStream(entry).use { input ->
                        outFile.outputStream().use { input.copyTo(it) }
                    }
                }
            }
        }
    }

    private suspend fun loadModel(onError: (String) -> Unit): Model? {
        _state.value = VoskState.Initializing
        return try {
            val m = withContext(Dispatchers.IO) { Model(modelDir.absolutePath) }
            model = m
            _state.value = VoskState.Ready
            m
        } catch (e: Exception) {
            Log.e(TAG, "Model load failed", e)
            val msg = "Failed to load model: ${e.message}"
            _state.value = VoskState.Error(msg)
            withContext(Dispatchers.Main) { onError(msg) }
            null
        }
    }

    private fun startListeningInternal(
        onPartial: (String) -> Unit,
        onResult: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        val m = model ?: return
        try {
            val rec = Recognizer(m, SAMPLE_RATE)
            val service = SpeechService(rec, SAMPLE_RATE)
            service.startListening(object : RecognitionListener {
                override fun onPartialResult(hypothesis: String) {
                    parseText(hypothesis, "partial")?.let { onPartial(it) }
                }
                override fun onResult(hypothesis: String) {
                    parseText(hypothesis, "text")?.let { onResult(it) }
                }
                override fun onFinalResult(hypothesis: String) {
                    parseText(hypothesis, "text")?.let { onResult(it) }
                    _state.value = VoskState.Ready
                }
                override fun onError(e: Exception) {
                    Log.e(TAG, "Recognition error", e)
                    _state.value = VoskState.Ready
                    onError(e.message ?: "Recognition error")
                }
                override fun onTimeout() {
                    _state.value = VoskState.Ready
                }
            })
            speechService = service
            _state.value = VoskState.Listening
        } catch (e: Exception) {
            Log.e(TAG, "startListening failed", e)
            _state.value = VoskState.Ready
            onError(e.message ?: "Failed to start recognition")
        }
    }

    private fun parseText(json: String, key: String): String? = try {
        JSONObject(json).optString(key, "").takeIf { it.isNotBlank() }
    } catch (e: Exception) {
        null
    }
}
