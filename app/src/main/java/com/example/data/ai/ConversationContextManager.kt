package com.example.data.ai

import org.json.JSONArray
import org.json.JSONObject

enum class ConversationIntent {
    SMALL_TALK,
    CLARIFY_EXPLANATION,
    FOLLOW_UP_NOT_FIXED,
    PROBLEM_REPORT
}

data class TroubleshootingSessionContext(
    val activeProblemId: String? = null,
    val activeProblemTitle: String? = null,
    val activeCategory: String? = null,
    val lastSymptomQuery: String? = null,
    val attemptedActionIds: Set<String> = emptySet(),
    val failedActionIds: Set<String> = emptySet(),
    val succeededActionIds: Set<String> = emptySet(),
    val lastAssistantSummary: String? = null,
    val followUpStepOffset: Int = 0
) {
    val hasActiveProblem: Boolean
        get() = !activeProblemId.isNullOrBlank() || !lastSymptomQuery.isNullOrBlank()

    val excludedActionIds: Set<String>
        get() = attemptedActionIds + failedActionIds

    fun toJsonString(): String = JSONObject().apply {
        put("activeProblemId", activeProblemId ?: "")
        put("activeProblemTitle", activeProblemTitle ?: "")
        put("activeCategory", activeCategory ?: "")
        put("lastSymptomQuery", lastSymptomQuery ?: "")
        put("attemptedActionIds", JSONArray(attemptedActionIds.toList()))
        put("failedActionIds", JSONArray(failedActionIds.toList()))
        put("succeededActionIds", JSONArray(succeededActionIds.toList()))
        put("lastAssistantSummary", (lastAssistantSummary ?: "").take(600))
        put("followUpStepOffset", followUpStepOffset)
    }.toString()

    companion object {
        fun fromJsonString(raw: String?): TroubleshootingSessionContext {
            if (raw.isNullOrBlank()) return TroubleshootingSessionContext()
            return runCatching {
                val obj = JSONObject(raw)
                TroubleshootingSessionContext(
                    activeProblemId = obj.optString("activeProblemId").takeIf { it.isNotBlank() },
                    activeProblemTitle = obj.optString("activeProblemTitle").takeIf { it.isNotBlank() },
                    activeCategory = obj.optString("activeCategory").takeIf { it.isNotBlank() },
                    lastSymptomQuery = obj.optString("lastSymptomQuery").takeIf { it.isNotBlank() },
                    attemptedActionIds = jsonArrayToSet(obj.optJSONArray("attemptedActionIds")),
                    failedActionIds = jsonArrayToSet(obj.optJSONArray("failedActionIds")),
                    succeededActionIds = jsonArrayToSet(obj.optJSONArray("succeededActionIds")),
                    lastAssistantSummary = obj.optString("lastAssistantSummary").takeIf { it.isNotBlank() },
                    followUpStepOffset = obj.optInt("followUpStepOffset", 0)
                )
            }.getOrDefault(TroubleshootingSessionContext())
        }

        private fun jsonArrayToSet(arr: JSONArray?): Set<String> {
            if (arr == null) return emptySet()
            return buildSet {
                for (i in 0 until arr.length()) {
                    arr.optString(i).takeIf { it.isNotBlank() }?.let { add(it) }
                }
            }
        }
    }
}

object ConversationContextManager {

    private val symptomKeywords = setOf(
        "battery", "charge", "charging", "drain", "heat", "heating", "overheat", "hot", "garam",
        "hang", "slow", "lag", "freeze", "stuck", "crash", "restart", "reboot",
        "wifi", "wi-fi", "internet", "network", "data", "sim", "signal", "bluetooth", "hotspot", "dns",
        "speaker", "sound", "audio", "mic", "microphone", "aawaz", "awaz", "awaaz", "volume", "earpiece",
        "screen", "display", "touch", "brightness", "flicker", "pixel", "black",
        "storage", "space", "memory", "ram", "full", "cache", "update", "app", "camera",
        "gps", "location", "sensor", "fingerprint", "face", "notification", "call",
        "बैटरी", "चार्ज", "गर्म", "हीटिंग", "हैंंग", "धीमा", "लैग", "वाई-फाई", "इंटरनेट", "नेटवर्क",
        "स्पीकर", "आवाज़", "स्क्रीन", "टच", "स्टोरेज", "मेमोरी", "रैम", "ब्लूटूथ", "कैमरा", "ऐप"
    )

    private val clarifyPhrases = listOf(
        "don't understand", "dont understand", "do not understand", "did not understand", "didn't understand",
        "explain again", "explain that again", "can you explain", "simpler", "simple words", "easier way to understand",
        "what do you mean", "confused", "samajh nahi", "samajh nhi", "samajh me nahi", "samajh mein nahi",
        "samajh na aaya", "samjhao", "dobara samjhao", "phir se batao", "aasan bhasha", "simple mein",
        "समझ नहीं", "समझ में नहीं", "फिर से बताएं", "फिर से समझाएं", "दोबारा समझाएं", "आसान भाषा", "सरल शब्दों"
    )

    private val followUpNotFixedPhrases = listOf(
        "not fixed", "still not", "didn't fix", "did not fix", "didn't work", "did not work", "does not work",
        "doesn't work", "still happening", "still having", "same issue", "same problem", "still slow",
        "another solution", "other solution", "next solution", "any other way", "easier solution", "easy solution",
        "tried it", "i tried", "maine try kiya", "maine try kia", "try kiya", "try kia", "kiya lekin", "kiya par",
        "fix nahi", "fix nhi", "nahi hua", "nhi hua", "theek nahi", "thik nahi", "theek nhi",
        "abhi bhi", "ab bhi", "kaam nahi kiya", "kam nahi kiya", "kaam nhi", "koi aur", "aur easy",
        "नहीं हुआ", "ठीक नहीं हुआ", "अभी भी", "कोई और", "आसान तरीका", "आसान उपाय", "काम नहीं", "कोशिश की"
    )

    private val smallTalkTokens = setOf(
        "hello", "hi", "hey", "namaste", "hii", "hlo", "hola",
        "thanks", "thank", "shukriya", "dhanyawad", "dhanyavad",
        "bye", "goodbye", "ok", "okay", "cool", "great", "awesome", "nice", "alright"
    )

    private val smallTalkPhrases = listOf(
        "how are you", "how r u", "what are you doing", "what r u doing", "who are you",
        "what can you do", "good morning", "good afternoon", "good evening", "good night",
        "thank you", "thanks a lot", "thx", "kaise ho", "kya haal", "kya kar rahe", "kya kar rhe",
        "aap kaun", "kaun ho", "आप कैसे", "कैसे हैं", "क्या कर रहे", "धन्यवाद", "शुक्रिया", "नमस्ते"
    )

    fun classifyIntent(
        userMessage: String,
        sessionContext: TroubleshootingSessionContext = TroubleshootingSessionContext()
    ): ConversationIntent {
        val text = userMessage.trim()
        if (text.isEmpty()) return ConversationIntent.SMALL_TALK

        val lower = text.lowercase()
        val tokens = lower
            .replace(Regex("[^a-z0-9\\u0900-\\u097F\\s'-]"), " ")
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }

        val containsHardwareSymptom = tokens.any { it in symptomKeywords } ||
            symptomKeywords.any { kw -> kw.length >= 4 && lower.contains(kw) }

        if (clarifyPhrases.any { lower.contains(it) }) {
            return ConversationIntent.CLARIFY_EXPLANATION
        }

        if (followUpNotFixedPhrases.any { lower.contains(it) }) {
            return ConversationIntent.FOLLOW_UP_NOT_FIXED
        }

        if (!containsHardwareSymptom) {
            val isSmallTalkPhrase = smallTalkPhrases.any { lower.contains(it) }
            val isShortGreetingOrThanks = tokens.size <= 5 && tokens.any { it in smallTalkTokens }
            if (isSmallTalkPhrase || isShortGreetingOrThanks) {
                return ConversationIntent.SMALL_TALK
            }
        }

        // If user replies concisely ("yes", "no", "it happens on wifi") while active issue exists, treat as problem follow-up
        if (!containsHardwareSymptom && sessionContext.hasActiveProblem &&
            (lower == "yes" || lower == "no" || lower == "haan" || lower == "nahi" || lower == "हाँ" || lower == "नहीं")
        ) {
            return ConversationIntent.FOLLOW_UP_NOT_FIXED
        }

        return ConversationIntent.PROBLEM_REPORT
    }

    fun buildSmallTalkFallback(
        userMessage: String,
        language: String,
        deviceLabel: String
    ): String {
        val lower = userMessage.lowercase()
        val isThanks = listOf("thank", "shukriya", "dhanyawad", "dhanyavad", "धन्यवाद", "शुक्रिया").any { lower.contains(it) }
        val isWhatDoing = listOf("what are you doing", "what r u doing", "kya kar rahe", "kya kar rhe", "क्या कर रहे", "who are you", "kaun ho").any { lower.contains(it) }
        val isHowAreYou = listOf("how are you", "how r u", "kaise ho", "kya haal", "कैसे हैं", "कैसे हो").any { lower.contains(it) }

        return when (language) {
            LanguageDetector.LANG_HINDI -> when {
                isThanks -> "आपका स्वागत है! मुझे खुशी है कि मैं मदद कर सका। अगर आपके $deviceLabel में कभी भी कोई समस्या आए या कोई सवाल हो, तो बेझिझक पूछें।"
                isWhatDoing -> "मैं आपके $deviceLabel की सेहत और लाइव सिस्टम टेलीमेट्री पर नज़र रख रहा हूँ ताकि बैटरी, स्टोरेज या नेटवर्क की किसी भी दिक्कत में तुरंत आपकी मदद कर सकूँ। बताइए, क्या कोई समस्या जांचनी है?"
                isHowAreYou -> "मैं बिल्कुल ठीक हूँ, पूछने के लिए धन्यवाद! मैं आपके $deviceLabel की सहायता के लिए तैयार हूँ। बताइए, आज मैं आपकी क्या मदद कर सकता हूँ?"
                else -> "नमस्ते! मैं आपका एआई स्मार्टफोन सहायक हूँ। बताइए, आज हम किस विषय पर बात करें या आपके $deviceLabel में क्या देखना है?"
            }
            LanguageDetector.LANG_HINGLISH -> when {
                isThanks -> "Aapka welcome hai! Mujhe khushi hai ki main help kar saka. Agar $deviceLabel mein kabhi bhi koi aur dikkat aaye toh zaroor batayein."
                isWhatDoing -> "Main abhi aapke $deviceLabel ke system vitals aur device health check karne ke liye ready hoon. Batayein, kya phone mein koi issue check karna hai ya sirf haal-chaal?"
                isHowAreYou -> "Main bilkul badhiya hoon, poochhne ke liye shukriya! Aap kaise hain? Batayein, aaj $deviceLabel ke liye main kya help kar sakta hoon?"
                else -> "Hello! Main aapka AI Smartphone Assistant hoon. Batayein, aaj kya haal hain aur main aapki kya help kar sakta hoon?"
            }
            else -> when {
                isThanks -> "You're very welcome! I'm always here if your $deviceLabel ever acts up or if you have another question."
                isWhatDoing -> "Right now I'm keeping an eye on your $deviceLabel's device health and ready to help whenever you need troubleshooting, a quick hardware check, or a friendly chat. What's on your mind?"
                isHowAreYou -> "I'm doing great, thanks for asking! How are you doing today? Let me know if you'd like to chat or check anything on your $deviceLabel."
                else -> "Hello! I'm your AI Smartphone Assistant. How can I help you with your $deviceLabel today, or how is your day going?"
            }
        }
    }
}
