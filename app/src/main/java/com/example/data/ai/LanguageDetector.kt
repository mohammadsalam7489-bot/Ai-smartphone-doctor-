package com.example.data.ai

/**
 * Determines the exact response language ("Hindi", "Hinglish", or "English")
 * for each user message while honoring Devanagari Hindi, natural Hinglish, and English inputs.
 */
object LanguageDetector {

    const val LANG_ENGLISH = "English"
    const val LANG_HINDI = "Hindi"
    const val LANG_HINGLISH = "Hinglish"

    private val devanagariRegex = Regex("[\\u0900-\\u097F]")

    private val strongHinglishMarkers = setOf(
        "mera", "meri", "mere", "tumhara", "hamara", "apne", "aap", "aapka", "aapko",
        "nahi", "nahin", "nhi", "naa", "mat", "bilkul",
        "bahut", "bohot", "zyada", "jyada", "thoda", "kam",
        "garam", "thanda", "jaldi", "khatam", "khtm", "kharab",
        "band", "chalu", "chal", "chalta", "chalti", "chalte",
        "hota", "hoti", "hote", "ho", "hai", "hain", "tha", "thi", "hu", "hoon",
        "raha", "rahi", "rahe", "rha", "rhi", "rhe", "gaya", "gayi", "gaye",
        "kaise", "kya", "kyu", "kyun", "kyon", "kab", "kahan", "kaun", "konsa",
        "dikkat", "samasya", "pareshani", "wajah", "bina", "batao", "bataiye",
        "karo", "kariye", "karen", "kerna", "karna", "krna", "dekh", "dekho", "kiya", "kia",
        "mein", "lekin", "magar", "par", "aur", "ya", "bhi", "sab", "kuch", "kuchh",
        "aawaz", "awaaz", "awaz", "aati", "aata", "aate", "aa", "jaati", "jaata", "aaya", "aayaa",
        "atak", "ruk", "rukh", "kaam", "khul", "khulta", "lagta", "dikh", "dikhta",
        "janda", "hunda", "sahi", "theek", "thik", "abhi", "pehle", "baad",
        "bhai", "maine", "mujhe", "samajh", "samjhao", "samjha", "koi", "aasan", "asan",
        "tareeka", "tarika", "shukriya", "dhanyawad", "dhanyavad", "namaste", "baat", "is", "baar", "ek"
    )

    fun detectResponseLanguage(userProblem: String, uiToggleLanguage: String): String {
        val text = userProblem.trim()
        if (text.isEmpty()) {
            return normalizeUiLanguage(uiToggleLanguage)
        }

        // 1. Any Devanagari script -> Strict Pure Devanagari Hindi
        if (devanagariRegex.containsMatchIn(text)) {
            return LANG_HINDI
        }

        // 2. Check Latin script tokens for Hinglish conversation patterns
        val tokens = text.lowercase()
            .replace(Regex("[^a-z0-9\\s']"), " ")
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }

        val markerHits = tokens.count { it in strongHinglishMarkers }
        val lower = text.lowercase()
        val hasTwoWordHinglishPhrase = listOf(
            "ho raha", "ho rahi", "ho rha", "ho rhi", "ho gaya", "ho gayi",
            "nahi chal", "nhi chal", "nahi ho", "nhi ho", "aa rahi", "aa rha",
            "apne aap", "bahut hang", "bahut garam", "jaldi drain", "jldi drain",
            "fix nahi", "fix nhi", "nhi hua", "nahi hua", "samajh nahi", "samajh nhi",
            "try kiya", "try kia", "abhi bhi", "koi aur", "aur easy",
            "kaise ho", "kya kar", "theek hai", "thik hai", "koi baat"
        ).any { lower.contains(it) }

        val singleWordHinglishGreetings = setOf("namaste", "shukriya", "dhanyawad", "dhanyavad")
        if (hasTwoWordHinglishPhrase || markerHits >= 2 || (tokens.size == 1 && tokens.first() in singleWordHinglishGreetings)) {
            return LANG_HINGLISH
        }

        // 3. Respect explicit UI language switch (e.g. Hindi UI toggle with brief technical query)
        return normalizeUiLanguage(uiToggleLanguage)
    }

    fun normalizeUiLanguage(uiLanguage: String): String {
        return when {
            uiLanguage.equals(LANG_HINDI, ignoreCase = true) -> LANG_HINDI
            uiLanguage.equals(LANG_HINGLISH, ignoreCase = true) -> LANG_HINGLISH
            else -> LANG_ENGLISH
        }
    }
}
