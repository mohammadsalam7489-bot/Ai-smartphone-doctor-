package com.example.util

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.util.Log
import com.example.data.ai.LanguageDetector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

data class SpeechChunk(
    val text: String,
    val pauseAfterMs: Long
)

class NaturalVoiceManager(context: Context) : TextToSpeech.OnInitListener {

    private val appContext = context.applicationContext
    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    private var tts: TextToSpeech? = null
    private var isInitialized = false
    private var lastFinalUtteranceId: String? = null
    private var focusRequest: AudioFocusRequest? = null

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private val _currentlySpeakingMessageId = MutableStateFlow<String?>(null)
    val currentlySpeakingMessageId: StateFlow<String?> = _currentlySpeakingMessageId.asStateFlow()

    private val _activeVoiceEngineInfo = MutableStateFlow("Android TTS Initializing")
    val activeVoiceEngineInfo: StateFlow<String> = _activeVoiceEngineInfo.asStateFlow()

    private val audioFocusListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        if (focusChange == AudioManager.AUDIOFOCUS_LOSS ||
            focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT
        ) {
            stop()
        }
    }

    init {
        try {
            // Prefer Google TTS engine for higher-quality neural voices (no human claims)
            tts = TextToSpeech(appContext, this, "com.google.android.tts")
        } catch (_: Exception) {
            tts = runCatching { TextToSpeech(appContext, this) }.getOrNull()
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            isInitialized = true
            setupVoiceForLocale(Locale.US, LanguageDetector.LANG_ENGLISH)

            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    _isSpeaking.value = true
                }

                override fun onDone(utteranceId: String?) {
                    if (utteranceId == null || utteranceId == lastFinalUtteranceId) {
                        _isSpeaking.value = false
                        _currentlySpeakingMessageId.value = null
                        abandonAudioFocus()
                    }
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    _isSpeaking.value = false
                    _currentlySpeakingMessageId.value = null
                    abandonAudioFocus()
                }

                override fun onError(utteranceId: String?, errorCode: Int) {
                    _isSpeaking.value = false
                    _currentlySpeakingMessageId.value = null
                    abandonAudioFocus()
                }
            })
        } else {
            _activeVoiceEngineInfo.value = "TTS Engine Unavailable (Code $status)"
            Log.w("NaturalVoiceManager", "TextToSpeech init failed with status: $status")
        }
    }

    private fun setupVoiceForLocale(locale: Locale, targetLanguage: String) {
        val engine = tts ?: return
        try {
            engine.language = locale
            val voices = runCatching { engine.voices }.getOrNull()
            if (!voices.isNullOrEmpty()) {
                val bestVoice = selectBestNaturalVoice(voices, locale)
                if (bestVoice != null) {
                    engine.voice = bestVoice
                    val qualityLabel = if (bestVoice.quality >= Voice.QUALITY_HIGH) "High-Quality Neural" else "Standard"
                    val netLabel = if (bestVoice.isNetworkConnectionRequired) "Network" else "Offline"
                    _activeVoiceEngineInfo.value = "Google/Device TTS ($qualityLabel • $netLabel • ${bestVoice.locale.toLanguageTag()})"
                } else {
                    _activeVoiceEngineInfo.value = "Device TTS (${locale.toLanguageTag()})"
                }
            } else {
                _activeVoiceEngineInfo.value = "Device TTS (${locale.toLanguageTag()})"
            }

            // Conversational warm pacing tuned per language (natural pauses handled by chunk queue)
            when (targetLanguage) {
                LanguageDetector.LANG_HINDI -> {
                    engine.setPitch(1.02f)
                    engine.setSpeechRate(0.93f)
                }
                LanguageDetector.LANG_HINGLISH -> {
                    engine.setPitch(1.03f)
                    engine.setSpeechRate(0.95f)
                }
                else -> {
                    engine.setPitch(1.03f)
                    engine.setSpeechRate(0.96f)
                }
            }
        } catch (e: Exception) {
            Log.e("NaturalVoiceManager", "Error setting up voice: ${e.message}")
        }
    }

    internal fun selectBestNaturalVoice(voices: Set<Voice>, locale: Locale): Voice? {
        val matchingLocaleVoices = voices.filter {
            it.locale.language.equals(locale.language, ignoreCase = true)
        }
        if (matchingLocaleVoices.isEmpty()) return null

        val preferredHints = listOf("neural", "wavenet", "journey", "studio", "sfg", "hie", "hif", "sfb", "iob", "female")
        return matchingLocaleVoices.maxByOrNull { v ->
            var score = v.quality
            if (v.locale.country.equals(locale.country, ignoreCase = true)) score += 120
            val nameLower = v.name.lowercase()
            if (preferredHints.any { nameLower.contains(it) }) score += 250
            if (v.features?.any { f -> preferredHints.any { f.contains(it, ignoreCase = true) } } == true) score += 200
            // Slight preference for offline/instant-ready when quality ties so response starts without lag
            if (!v.isNetworkConnectionRequired) score += 40
            score
        }
    }

    /**
     * Speaks a message cleanly with natural sentence pauses and matching Hindi/Hinglish/English voice.
     * Supports immediate barge-in interruption via stop().
     */
    fun speak(
        text: String,
        messageId: String? = null,
        explicitLanguage: String? = null
    ) {
        val targetLang = detectSpeechLanguage(text, explicitLanguage)
        val cleanSpokenText = sanitizeTextForSpeech(text, targetLang)
        if (cleanSpokenText.isBlank()) return

        stop()

        if (!isInitialized || tts == null) {
            _activeVoiceEngineInfo.value = "TTS Unavailable on this runtime"
            return
        }

        requestAudioFocus()

        val targetLocale = when (targetLang) {
            LanguageDetector.LANG_HINDI -> Locale("hi", "IN")
            LanguageDetector.LANG_HINGLISH -> Locale("hi", "IN")
            else -> Locale.US
        }
        setupVoiceForLocale(targetLocale, targetLang)

        val chunks = buildSpeechChunks(cleanSpokenText)
        if (chunks.isEmpty()) return

        val baseId = messageId ?: "tts_${System.currentTimeMillis()}"
        _currentlySpeakingMessageId.value = messageId
        _isSpeaking.value = true

        chunks.forEachIndexed { idx, chunk ->
            val chunkUtteranceId = "${baseId}_chunk_$idx"
            val isLast = idx == chunks.lastIndex
            if (isLast) {
                lastFinalUtteranceId = chunkUtteranceId
            }
            val queueMode = if (idx == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
            val params = Bundle().apply {
                putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, chunkUtteranceId)
            }
            tts?.speak(chunk.text, queueMode, params, chunkUtteranceId)

            if (!isLast && chunk.pauseAfterMs > 0L) {
                val silenceId = "${baseId}_pause_$idx"
                tts?.playSilentUtterance(chunk.pauseAfterMs, TextToSpeech.QUEUE_ADD, silenceId)
            }
        }
    }

    fun stop() {
        lastFinalUtteranceId = null
        try {
            tts?.stop()
        } catch (_: Exception) {}
        _isSpeaking.value = false
        _currentlySpeakingMessageId.value = null
        abandonAudioFocus()
    }

    fun shutdown() {
        stop()
        runCatching { tts?.shutdown() }
        tts = null
    }

    internal fun detectSpeechLanguage(text: String, explicitLanguage: String?): String {
        val normalizedExplicit = explicitLanguage?.let { LanguageDetector.normalizeUiLanguage(it) }
        return LanguageDetector.detectResponseLanguage(
            userProblem = text,
            uiToggleLanguage = normalizedExplicit ?: LanguageDetector.LANG_ENGLISH
        )
    }

    internal fun sanitizeTextForSpeech(raw: String, language: String): String {
        val stripped = raw
            .replace(Regex("\\[ACTION:[A-Z_]+\\]"), "")
            .replace(Regex("\\[TEST:[A-Z_]+\\]"), "")
            // Remove markdown/emoji clutter that TTS reads awkwardly
            .replace(Regex("[⚠️✅🔒🔋🐌📶🔥◦•]"), " ")
            .replace(Regex("^#+\\s*", RegexOption.MULTILINE), "")
            .replace(Regex("^[-*]\\s*", RegexOption.MULTILINE), "")
            .replace("*", "")
            .replace("_", " ")
            .replace("`", "")
            // Spoken transitions for navigation arrows and math/unit notation
            .replace("->", if (language == LanguageDetector.LANG_HINDI) " से " else " to ")
            .replace("➜", if (language == LanguageDetector.LANG_HINDI) " से " else " to ")
            .replace(" > ", if (language == LanguageDetector.LANG_HINDI) ", फिर " else ", then ")
            .replace("°C", if (language == LanguageDetector.LANG_HINDI) " डिग्री सेल्सियस" else " degrees Celsius")
            .replace("%", if (language == LanguageDetector.LANG_HINDI) " प्रतिशत" else " percent")
            .replace(Regex("\\n+"), ". ")
            .replace(Regex("\\s{2,}"), " ")
            .trim()
        return stripped
    }

    internal fun buildSpeechChunks(cleanText: String): List<SpeechChunk> {
        if (cleanText.isBlank()) return emptyList()
        val rawSegments = cleanText
            .split(Regex("(?<=[.?!।])\\s+"))
            .map { it.trim() }
            .filter { it.isNotBlank() }

        if (rawSegments.isEmpty()) return listOf(SpeechChunk(cleanText, 0L))

        val merged = mutableListOf<SpeechChunk>()
        val buffer = StringBuilder()

        fun flush(pauseMs: Long) {
            val s = buffer.toString().trim()
            if (s.isNotEmpty()) {
                merged.add(SpeechChunk(s, pauseMs))
                buffer.clear()
            }
        }

        for (seg in rawSegments) {
            val isStepStart = Regex("^(\\d+\\.|Step\\s+\\d+|चरण\\s+\\d+)").containsMatchIn(seg)
            if (isStepStart && buffer.isNotEmpty()) {
                flush(260L)
            }
            if (buffer.isNotEmpty()) buffer.append(" ")
            buffer.append(seg)
            if (buffer.length >= 90 || seg.endsWith("?") || seg.endsWith("!") || isStepStart) {
                flush(if (isStepStart || seg.endsWith("?")) 230L else 160L)
            }
        }
        flush(0L)
        return merged
    }

    private fun requestAudioFocus() {
        val am = audioManager ?: return
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val attrs = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
                val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                    .setAudioAttributes(attrs)
                    .setOnAudioFocusChangeListener(audioFocusListener)
                    .build()
                focusRequest = req
                am.requestAudioFocus(req)
            } else {
                @Suppress("DEPRECATION")
                am.requestAudioFocus(audioFocusListener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            }
        }
    }

    private fun abandonAudioFocus() {
        val am = audioManager ?: return
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                focusRequest?.let { am.abandonAudioFocusRequest(it) }
                focusRequest = null
            } else {
                @Suppress("DEPRECATION")
                am.abandonAudioFocus(audioFocusListener)
            }
        }
    }
}
