package com.example.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Firebase
import com.google.firebase.ai.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

data class ChatMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val text: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)

sealed interface AiState {
    data object Idle : AiState
    data object Loading : AiState
    data class Success(val responseText: String) : AiState
    data class Error(val message: String) : AiState
}

data class DiagnosticResult(
    val batteryHealth: String = "Unavailable",
    val batteryTemp: Float = -1f,
    val batteryLevel: Int = -1,
    val isCharging: Boolean = false,
    val ramUsage: String = "Unavailable",
    val storageUsage: String = "Unavailable",
    val sensorsStatus: String = "Pending Hardware Check",
    val touchTestPassed: Boolean = false,
    val audioTestPassed: Boolean = false,
    val overallScore: Int = 0
)

data class RepairEstimate(
    val issueName: String,
    val estimatedCost: String,
    val difficulty: String,
    val diyGuide: String,
    val professionalAdvice: String
)

class DoctorViewModel(application: Application) : AndroidViewModel(application) {

    private val tag = "DoctorViewModel"

    private val primaryModel by lazy {
        Firebase.ai.generativeModel("gemini-3.5-flash-lite")
    }
    private val fallbackModel by lazy {
        Firebase.ai.generativeModel("gemini-3-flash-preview")
    }

    private val _currentLanguage = MutableStateFlow("English")
    val currentLanguage: StateFlow<String> = _currentLanguage.asStateFlow()

    fun setLanguage(lang: String) {
        _currentLanguage.value = lang
    }

    private val _chatMessages = MutableStateFlow<List<ChatMessage>>(
        listOf(
            ChatMessage(text = "Hello! I am your AI Smartphone Doctor. How can I help you troubleshoot or optimize your device today?", isUser = false)
        )
    )
    val chatMessages: StateFlow<List<ChatMessage>> = _chatMessages.asStateFlow()

    private val _aiState = MutableStateFlow<AiState>(AiState.Idle)
    val aiState: StateFlow<AiState> = _aiState.asStateFlow()

    private val _diagnosticResult = MutableStateFlow(DiagnosticResult())
    val diagnosticResult: StateFlow<DiagnosticResult> = _diagnosticResult.asStateFlow()

    private val _repairEstimate = MutableStateFlow<RepairEstimate?>(null)
    val repairEstimate: StateFlow<RepairEstimate?> = _repairEstimate.asStateFlow()

    init {
        refreshBatteryStats()
    }

    fun refreshBatteryStats() {
        try {
            val context = getApplication<Application>().applicationContext
            val vitals = com.example.data.diagnostics.RealDeviceDiagnostics.getDeviceVitals(context)
            val isCharging = vitals.batteryStatus == "Charging" || vitals.batteryStatus == "Full"

            val storageStr = if (vitals.storageVerified) {
                val usedGB = (vitals.storageTotalBytes - vitals.storageFreeBytes) / (1024 * 1024 * 1024)
                val totalGB = vitals.storageTotalBytes / (1024 * 1024 * 1024)
                "$usedGB GB / $totalGB GB (${vitals.storageUsedPercent}%)"
            } else {
                "Unavailable"
            }
            val ramStr = if (vitals.ramVerified) {
                val usedGB = (vitals.ramTotalBytes - vitals.ramFreeBytes) / (1024.0 * 1024 * 1024)
                val totalGB = vitals.ramTotalBytes / (1024.0 * 1024 * 1024)
                String.format(java.util.Locale.US, "%.1f GB / %.1f GB (%d%%)", usedGB, totalGB, vitals.ramUsedPercent)
            } else {
                "Unavailable"
            }

            _diagnosticResult.value = _diagnosticResult.value.copy(
                batteryHealth = vitals.batteryHealth,
                batteryLevel = vitals.batteryLevel,
                batteryTemp = vitals.batteryTempC,
                isCharging = isCharging,
                ramUsage = ramStr,
                storageUsage = storageStr,
                overallScore = vitals.healthScore
            )
        } catch (e: Exception) {
            Log.e(tag, "Error reading battery/storage", e)
        }
    }

    fun sendChatMessage(prompt: String) {
        if (prompt.isBlank()) return
        val userMsg = ChatMessage(text = prompt, isUser = true)
        _chatMessages.value = _chatMessages.value + userMsg

        viewModelScope.launch {
            _aiState.value = AiState.Loading
            try {
                val targetLang = com.example.data.ai.LanguageDetector.detectResponseLanguage(prompt, _currentLanguage.value)
                val systemContext = when (targetLang) {
                    com.example.data.ai.LanguageDetector.LANG_HINDI ->
                        "आप एक विशेषज्ञ एआई स्मार्टफोन डॉक्टर और हार्डवेयर तकनीशियन हैं। उपयोगकर्ता की समस्या का उत्तर **100% शुद्ध देवनागरी हिंदी** में दें। कोई हिंग्लिश या मिश्रित अंग्रेजी वाक्य न लिखें।"
                    com.example.data.ai.LanguageDetector.LANG_HINGLISH ->
                        "You are an expert AI Smartphone Doctor. Reply strictly in natural conversational **Hinglish** (Roman script Hindi-English mix) consistently across all headings and steps."
                    else ->
                        "You are an expert AI Smartphone Doctor and Hardware Technician. Provide precise, practical, friendly troubleshooting advice for smartphone issues in **100% Pure Professional English**. Keep responses concise and structured."
                }
                val fullPrompt = "$systemContext\n\nUser Question: $prompt"

                val response = try {
                    withTimeout(20_000L) { primaryModel.generateContent(fullPrompt) }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Throwable) {
                    withTimeout(25_000L) { fallbackModel.generateContent(fullPrompt) }
                }
                val answer = response.text ?: "I couldn't generate a response. Please try again."
                _chatMessages.value = _chatMessages.value + ChatMessage(text = answer, isUser = false)
                _aiState.value = AiState.Success(answer)
            } catch (e: TimeoutCancellationException) {
                Log.e(tag, "Chat timed out", e)
                val err = "Request timed out. Please check your network connection and retry."
                _chatMessages.value = _chatMessages.value + ChatMessage(text = err, isUser = false)
                _aiState.value = AiState.Error(err)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(tag, "AI chat error", e)
                val err = "Cloud AI unavailable: ${e.localizedMessage ?: "Unknown error occurred."}"
                _chatMessages.value = _chatMessages.value + ChatMessage(text = err, isUser = false)
                _aiState.value = AiState.Error(err)
            }
        }
    }

    fun estimateRepair(issue: String, deviceBrand: String) {
        viewModelScope.launch {
            _aiState.value = AiState.Loading
            try {
                val prompt = "Provide a structured assessment for repairing a '$issue' on a '$deviceBrand' smartphone. Include estimated cost range in USD, repair difficulty (Easy, Moderate, Hard, Professional Required), and 3 clear DIY troubleshooting or fix steps."
                val response = withTimeout(25_000L) {
                    primaryModel.generateContent(prompt)
                }
                val text = response.text ?: "No estimate generated."

                val estimate = RepairEstimate(
                    issueName = issue,
                    estimatedCost = extractCost(text),
                    difficulty = extractDifficulty(text),
                    diyGuide = text,
                    professionalAdvice = "Visit an authorized service center if hardware replacement requires micro-soldering or specialized screen disassembly tools."
                )
                _repairEstimate.value = estimate
                _aiState.value = AiState.Success(text)
            } catch (e: TimeoutCancellationException) {
                _aiState.value = AiState.Error("Repair estimation timed out.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(tag, "Repair estimate error", e)
                _aiState.value = AiState.Error(e.localizedMessage ?: "Failed to estimate repair.")
            }
        }
    }

    private fun extractCost(text: String): String {
        val regex = Regex("\\$[0-9]+(-[0-9]+)?")
        val match = regex.find(text)
        return match?.value ?: "$50 - $150"
    }

    private fun extractDifficulty(text: String): String {
        val lower = text.lowercase()
        return when {
            lower.contains("easy") -> "Easy (DIY)"
            lower.contains("moderate") -> "Moderate"
            lower.contains("hard") -> "Advanced"
            else -> "Professional Required"
        }
    }
}
