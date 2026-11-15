package com.example.util

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

class NaturalVoiceManager(context: Context) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var isInitialized = false

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private val _currentlySpeakingMessageId = MutableStateFlow<String?>(null)
    val currentlySpeakingMessageId: StateFlow<String?> = _currentlySpeakingMessageId.asStateFlow()

    init {
        try {
            // Prefer Google TTS engine for highest quality neural female voices
            tts = TextToSpeech(context.applicationContext, this, "com.google.android.tts")
        } catch (_: Exception) {
            // Fallback to default engine
            tts = TextToSpeech(context.applicationContext, this)
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            isInitialized = true
            setupVoiceForLocale(Locale.ENGLISH)

            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    _isSpeaking.value = true
                }

                override fun onDone(utteranceId: String?) {
                    _isSpeaking.value = false
                    _currentlySpeakingMessageId.value = null
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    _isSpeaking.value = false
                    _currentlySpeakingMessageId.value = null
                }

                override fun onError(utteranceId: String?, errorCode: Int) {
                    _isSpeaking.value = false
                    _currentlySpeakingMessageId.value = null
                }
            })
        } else {
            Log.w("NaturalVoiceManager", "TextToSpeech init failed with status: $status")
        }
    }

    /**
     * Intelligently selects a natural, warm, human-like female voice
     * similar to Gemini / Google Assistant tone, avoiding monotone robotic voices.
     */
    private fun setupVoiceForLocale(locale: Locale) {
        val engine = tts ?: return
        try {
            engine.language = locale

            // Find best neural/high-quality female voice
            val voices = engine.voices
            if (!voices.isNullOrEmpty()) {
                val bestVoice = findBestFemaleVoice(voices, locale)
                if (bestVoice != null) {
                    engine.voice = bestVoice
                }
            }

            // Natural warm cadence (1.08 pitch slightly brighter/feminine, 0.98 rate is conversational)
            engine.setPitch(1.08f)
            engine.setSpeechRate(0.98f)
        } catch (e: Exception) {
            Log.e("NaturalVoiceManager", "Error setting up voice: ${e.message}")
        }
    }

    private fun findBestFemaleVoice(voices: Set<Voice>, locale: Locale): Voice? {
        val matchingLocaleVoices = voices.filter {
            it.locale.language.equals(locale.language, ignoreCase = true)
        }

        // 1. Look for known high quality neural female voices (Google TTS network/high quality female tags)
        val femaleKeywords = listOf("female", "woman", "sfg", "hie", "hif", "end", "en-us-x-sfg", "hi-in-x-hie")
        for (kw in femaleKeywords) {
            matchingLocaleVoices.firstOrNull { voice ->
                voice.name.contains(kw, ignoreCase = true) || voice.features?.any { it.contains(kw, ignoreCase = true) } == true
            }?.let { return it }
        }

        // 2. Look for high quality / low latency voice without robotic traits
        return matchingLocaleVoices.firstOrNull { !it.isNetworkConnectionRequired }
            ?: matchingLocaleVoices.firstOrNull()
    }

    /**
     * Speaks the given text cleanly.
     * Strips technical markdown characters (*, #, _, [ACTION:...]) before synthesis
     * to keep speech natural and fluent.
     */
    fun speak(text: String, messageId: String? = null) {
        if (!isInitialized || tts == null) return

        stop()

        val cleanSpokenText = sanitizeTextForSpeech(text)
        if (cleanSpokenText.isBlank()) return

        // Auto-detect language
        val isHindi = isTextHindi(cleanSpokenText)
        val targetLocale = if (isHindi) Locale("hi", "IN") else Locale.US
        setupVoiceForLocale(targetLocale)

        _currentlySpeakingMessageId.value = messageId
        _isSpeaking.value = true

        val params = Bundle().apply {
            putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, messageId ?: "tts_id_${System.currentTimeMillis()}")
        }

        tts?.speak(cleanSpokenText, TextToSpeech.QUEUE_FLUSH, params, params.getString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID))
    }

    fun stop() {
        try {
            tts?.stop()
        } catch (_: Exception) {}
        _isSpeaking.value = false
        _currentlySpeakingMessageId.value = null
    }

    fun shutdown() {
        stop()
        tts?.shutdown()
        tts = null
    }

    private fun isTextHindi(text: String): Boolean {
        // Detect Devanagari unicode range (0900-097F)
        for (char in text) {
            if (char in '\u0900'..'\u097F') return true
        }

        // Common Hinglish marker tokens
        val hinglishTokens = listOf("karo", "kripya", "samasya", "bhai", "hai", "mera", "phone", "garam", "chal", "nahi", "batayein", "maine")
        val lower = text.lowercase()
        return hinglishTokens.any { lower.contains(it) }
    }

    private fun sanitizeTextForSpeech(raw: String): String {
        return raw
            // Remove ACTION tags
            .replace(Regex("\\[ACTION:[A-Z_]+\\]"), "")
            .replace(Regex("\\[TEST:[A-Z_]+\\]"), "")
            // Remove markdown headers and bullets
            .replace(Regex("^#+\\s*", RegexOption.MULTILINE), "")
            .replace(Regex("^[•\\-*]\\s*", RegexOption.MULTILINE), "")
            // Remove asterisks & bold/italic markers
            .replace("*", "")
            .replace("_", "")
            .replace("`", "")
            // Remove excessive newlines
            .replace(Regex("\\n+"), ". ")
            .trim()
    }
}
