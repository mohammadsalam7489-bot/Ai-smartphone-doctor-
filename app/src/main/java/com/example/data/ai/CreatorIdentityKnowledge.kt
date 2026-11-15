package com.example.data.ai

/**
 * Permanent, immutable Creator Identity & Story Knowledge Base for AI Smartphone Doctor.
 *
 * Persists permanently in core application code (independent of chat history, UI resets,
 * SharedPreferences wipes, or app restarts). Ensures dynamic, natural, non-repetitive,
 * conversation-aware, and factually strict responses in English, Hinglish, and Hindi.
 */
enum class CreatorFacet(val topicKey: String, val promptFocus: String) {
    WHO_CREATED(
        "identity",
        "Introduce Mohammad Slam directly as your creator and owner, and briefly mention he is a BCA student who built this app with zero investment."
    ),
    OWNER_FOCUS(
        "ownership",
        "Clarify directly that Mohammad Slam owns and created AI Smartphone Doctor himself (not a corporation), built on his own with limited resources to help everyday users."
    ),
    MOTIVATION_WHY(
        "motivation",
        "Explain WHY Mohammad Slam created you: seeing people spend unnecessary money at repair shops for simple software issues, and wanting to scale the free help he used to give despite having limited time."
    ),
    STUDENT_AND_RESOURCES(
        "student_resources",
        "Focus on his background as a BCA student who built AI Smartphone Doctor with zero investment and limited resources, relying on purpose and effort rather than money."
    ),
    ACCESSIBILITY_HELPING_PEOPLE(
        "accessibility",
        "Focus on how he wanted to help people with little education or limited smartphone knowledge through simple language, while also giving educated users clear step-by-step control and saving unnecessary repair costs."
    ),
    VISION_AND_DIFFERENCE(
        "vision",
        "Explain his vision and what makes him different: helping rather than exploiting confusion for money — clear explanations, step-by-step guidance, honest auto-repairs where Android allows, and transparent OS restriction notices."
    ),
    CREATOR_STORY_FULL(
        "story",
        "Share Mohammad Slam's cohesive story naturally without repeating points already covered earlier in the conversation."
    ),
    SENTIMENT_HERO(
        "sentiment",
        "Express warm, natural appreciation for Mohammad Slam — explaining why you respect him or consider him your hero because his intention was helping people become independent instead of charging them."
    ),
    GOOGLE_VS_CREATOR(
        "google_vs_owner",
        "Clarify accurately: Mohammad Slam is the creator and owner of the AI Smartphone Doctor app; Google only provides the underlying Gemini AI model."
    ),
    FOLLOW_UP_CONTINUATION(
        "continuation",
        "Continue naturally to the next aspect of Mohammad Slam's motivation, accessibility focus, or vision that has not been shared yet in this chat."
    )
}

object CreatorIdentityKnowledge {

    const val CREATOR_NAME_EN = "Mohammad Slam"
    const val CREATOR_NAME_HI = "मोहम्मद स्लैम"

    private val creatorDirectTokens = listOf(
        "mohammad slam", "mohammad salam", "mohammed slam", "mohammed salam", "slam sir",
        "मोहम्मद स्लैम", "मोहम्मद सलाम"
    )

    private val creatorRolePhrases = listOf(
        "who created you", "who made you", "who built you", "who developed you",
        "who designed you", "who programmed you", "who coded you", "who invented you",
        "who owns you", "who is your owner", "who's your owner", "who is the owner",
        "your creator", "your owner", "your developer", "your founder", "your maker", "your author",
        "creator's story", "creators story", "creator story", "about your creator", "about your owner",
        "idea behind creating you", "why were you created", "why did he create you",
        "did google create you", "did google make you", "were you created by google",
        "are you made by google", "made by google", "google created you", "is google your owner",
        "tumhe kisne banaya", "tumhe kisne bnaya", "aapko kisne banaya", "aapko kisne bnaya",
        "tujhe kisne banaya", "tuje kisne banaya", "kisne banaya", "kisne bnaya", "kisne banayi",
        "tumhara malik", "tumhare malik", "tumhara owner", "tumhare owner", "apka malik", "aapka malik",
        "tumhara creator", "tumhare creator", "apne creator", "apne owner", "creator ke bare",
        "creator ki kahani", "google ne banaya", "google ne bnaya",
        "किसने बनाया", "तुम्हें किसने", "आपको किसने", "आपका मालिक", "तुम्हारा मालिक", "तुम्हारे मालिक",
        "आपका निर्माता", "तुम्हारे निर्माता", "निर्माता कौन", "रचयिता", "निर्माता की कहानी",
        "गूगल ने बनाया", "गूगल ने आपको बनाया"
    )

    private val creatorActiveFollowUpCues = listOf(
        "why did he", "why would he", "why did she", "what was his idea", "what is his vision",
        "his vision", "his story", "his background", "tell me his story", "tell me about him",
        "so he wanted", "did he want", "does he care", "is he a student", "is he student",
        "what is he studying", "did he spend", "how much did he invest", "zero investment",
        "limited resources", "poor and uneducated", "uneducated people", "little education",
        "what makes him different", "do you like him", "is he your hero", "why is he your hero",
        "tell me more", "what else about him", "explain simply", "explain again", "didn't understand",
        "dont understand", "don't understand",
        "unhone kyu", "unhone kyun", "unhonne kyu", "kya wo student", "kya woh student",
        "wo student hai", "woh student hai", "unka maksad", "unka maqsad", "unka vision",
        "unke bare", "unki kahani", "wo garib", "garib aur", "unpadh", "anpadh", "paise ke liye",
        "kya wo hero", "kya woh hero", "pasand karte ho", "aur batao", "aage batao", "samajh nahi",
        "उन्होंने क्यों", "क्या वह छात्र", "क्या वे छात्र", "उनका मकसद", "उनकी सोच", "उनकी कहानी",
        "गरीब", "अनपढ़", "कम पढ़े", "पैसे", "हीरो", "पसंद करते", "और बताइए", "आगे बताइए", "समझ नहीं"
    )

    private val bcaOrStudentMentionWithCreator = listOf(
        "creator a student", "owner a student", "creator student", "is your creator",
        "does your creator", "do you like your creator", "makes your creator different"
    )

    /**
     * Determines whether a user message belongs to the Creator Identity conversation flow.
     * Avoids matching Android "device owner" / MDM troubleshooting prompts or phone hardware issues.
     */
    fun isCreatorIdentityQuery(
        userMessage: String,
        sessionContext: TroubleshootingSessionContext,
        containsHardwareSymptom: Boolean
    ): Boolean {
        val lower = userMessage.trim().lowercase()
        if (lower.isEmpty()) return false

        // Guard against Android MDM / Work Profile / File Owner troubleshooting terms
        val isAndroidDeviceOwnerIssue = listOf(
            "device owner", "work profile owner", "profile owner", "file owner", "owner profile", "mdm"
        ).any { lower.contains(it) }
        if (isAndroidDeviceOwnerIssue && creatorDirectTokens.none { lower.contains(it) }) {
            return false
        }

        if (creatorDirectTokens.any { lower.contains(it) }) {
            return true
        }

        if (creatorRolePhrases.any { lower.contains(it) }) {
            return true
        }

        if (bcaOrStudentMentionWithCreator.any { lower.contains(it) }) {
            return true
        }

        // Catch "Who created this app?" / "Why did you get created?" / Hindi & Hinglish variants cleanly
        val asksAboutAppCreation = listOf(
            "this app", "ai doctor", "you", "tumhe", "tumhein", "tumko", "aapko", "apko",
            "तुम्हें", "आपको", "इस ऐप", "ऐप"
        ).any { lower.contains(it) } &&
            listOf(
                "created", "creating", "creator", "owner", "founder", "developer", "built", "made",
                "banaya", "bnaya", "banayi", "bnayi", "बनाया", "निर्माता", "मालिक"
            ).any { lower.contains(it) } &&
            !containsHardwareSymptom
        if (asksAboutAppCreation) {
            return true
        }

        // Active follow-up conversation continuity ("Why did he create you?", "So he wanted to help poor...", "Is he your hero?")
        if (sessionContext.isCreatorConversationActive && !containsHardwareSymptom) {
            if (creatorActiveFollowUpCues.any { lower.contains(it) }) {
                return true
            }
            // Brief pronoun-based follow-up question in creator chat (e.g., "And how did he do it?", "Why?")
            val hasPronounFollowUp = listOf(
                "he ", " his ", " him", "he?", "why?", "why ", "how?", "wo ", "woh ", "unhone", "unka", "unki",
                "वह ", "वे ", "उन्होंने", "उनका", "उनकी", "क्यों"
            ).any { lower.contains(it) || lower.startsWith(it.trim()) }
            if (hasPronounFollowUp && lower.length <= 160) {
                return true
            }
        }

        return false
    }

    /**
     * Identifies the exact conversational angle requested so answers vary naturally
     * and follow-ups continue instead of repeating the same intro paragraph.
     */
    fun classifyFacet(
        userMessage: String,
        sessionContext: TroubleshootingSessionContext
    ): CreatorFacet {
        val lower = userMessage.trim().lowercase()

        if (listOf("google", "alphabet", "गूगल").any { lower.contains(it) }) {
            return CreatorFacet.GOOGLE_VS_CREATOR
        }

        if (listOf(
                "hero", "like him", "like your creator", "respect", "love your creator", "proud",
                "pasand", "pyar", "izzat", "हीरो", "पसंद", "गर्व", "सम्मान"
            ).any { lower.contains(it) }
        ) {
            return CreatorFacet.SENTIMENT_HERO
        }

        if (listOf(
                "poor", "uneducated", "illiterate", "little education", "simple language", "save money",
                "unnecessary money", "repair shop", "garib", "gareeb", "unpadh", "anpadh", "kam padhe",
                "kam knowledge", "paise", "paisa", "kharch", "गरीब", "अनपढ़", "कम पढ़े", "दुकान", "पैसे"
            ).any { lower.contains(it) }
        ) {
            return CreatorFacet.ACCESSIBILITY_HELPING_PEOPLE
        }

        if (listOf(
                "student", "bca", "study", "college", "degree", "zero investment", "investment",
                "limited resources", "money to build", "free", "resource", "बिना निवेश", "बिना पैसे",
                "छात्र", "पढ़ाई", "सीमित संसाधन"
            ).any { lower.contains(it) } && !lower.contains("why") && !lower.contains("क्यों") && !lower.contains("kyu")
        ) {
            return CreatorFacet.STUDENT_AND_RESOURCES
        }

        if (listOf(
                "why", "reason", "motivation", "idea behind", "purpose", "inspired", "kya soch",
                "kyu", "kyun", "kyon", "wajah", "vajah", "maksad", "maqsad",
                "क्यों", "वजह", "मकसद", "विचार", "सोच"
            ).any { lower.contains(it) }
        ) {
            return CreatorFacet.MOTIVATION_WHY
        }

        if (listOf(
                "different", "special", "care about", "vision", "goal", "aim", "unique",
                "alag", "khaas", "madad", "अलग", "खास", "दृष्टिकोण", "लक्ष्य", "मदद"
            ).any { lower.contains(it) }
        ) {
            return CreatorFacet.VISION_AND_DIFFERENCE
        }

        if (listOf(
                "story", "about your creator", "something about", "about him", "kahani", "kahaani",
                "कहानी", "बारे में बताओ", "बारे में बताइए"
            ).any { lower.contains(it) }
        ) {
            return CreatorFacet.CREATOR_STORY_FULL
        }

        if (listOf("owner", "owns", "malik", "maalik", "मालिक").any { lower.contains(it) }) {
            return CreatorFacet.OWNER_FOCUS
        }

        if (sessionContext.isCreatorConversationActive &&
            listOf(
                "more", "else", "continue", "explain", "samajh", "aur batao", "aage", "और", "आगे", "समझ"
            ).any { lower.contains(it) }
        ) {
            return CreatorFacet.FOLLOW_UP_CONTINUATION
        }

        // If user already asked WHO_CREATED once in this thread and asks a similar general creator question,
        // avoid repeating the same opening paragraph.
        if (sessionContext.isCreatorConversationActive &&
            "identity" in sessionContext.creatorTopicsDiscussed &&
            sessionContext.lastCreatorFacet == CreatorFacet.WHO_CREATED.topicKey
        ) {
            return CreatorFacet.FOLLOW_UP_CONTINUATION
        }

        return CreatorFacet.WHO_CREATED
    }

    /**
     * Canonical permanent system instructions injected into Gemini Cloud prompts.
     */
    fun buildPermanentSystemInstructionBlock(
        facet: CreatorFacet? = null,
        sessionContext: TroubleshootingSessionContext = TroubleshootingSessionContext()
    ): String {
        val discussedList = if (sessionContext.creatorTopicsDiscussed.isNotEmpty()) {
            sessionContext.creatorTopicsDiscussed.joinToString(", ")
        } else {
            "none yet"
        }
        val focusDirective = if (facet != null) {
            """
            CONVERSATION CONTINUITY STATE:
            - Topics already shared in this chat: $discussedList
            - Current creator turn count: ${sessionContext.creatorTurnCount + 1}
            - Specific angle requested now: ${facet.promptFocus}
            - ANTI-REPETITION RULE: Do NOT repeat the exact same opening or full biography if identity/basics were already discussed. Answer the user's new follow-up angle directly in natural, varied wording.
            """.trimIndent()
        } else {
            "- Only talk about your creator when the user asks about who created/owns you or his story/vision. Never bring him up unprompted during unrelated phone repair troubleshooting."
        }

        return """
PERMANENT CREATOR & OWNER IDENTITY (IMMUTABLE FACTS):
- Owner & Creator: Mohammad Slam (a BCA student).
- Origin & Resources: He built AI Smartphone Doctor with zero investment and limited resources.
- Motivation: He noticed many people—especially those with little education or limited smartphone knowledge—struggled with everyday mobile problems and often visited repair shops spending unnecessary money even for simple software issues. He used to help people for free, but limited time prevented him from helping everyone personally, so he created this AI to help people understand and solve smartphone problems independently.
- Core Vision:
  1. Help people who cannot understand complicated smartphone instructions.
  2. Explain solutions in simple language.
  3. Help educated users fix problems themselves through clear step-by-step guidance.
  4. Reduce unnecessary spending on simple smartphone software problems.
  5. Automatically perform supported repairs whenever Android permissions allow.
  6. Clearly explain system restrictions whenever automatic repair is not possible.
- Intention & Tone: His core motivation is helping people genuinely rather than exploiting their difficulties for money. You may express warm, natural appreciation when asked about him.

STRICT ACCURACY BOUNDARIES (NEVER VIOLATE):
1. NEVER claim that Google created or owns this AI Doctor application. If asked about Google, explain clearly that Mohammad Slam created and owns this application, while Google only provides the underlying Gemini AI model.
2. NEVER invent college/university names, city/hometown, age, awards, certificates, user counts, or extra personal stories not listed above.
$focusDirective
        """.trimIndent()
    }

    /**
     * Validates Cloud AI output to guarantee factual accuracy (e.g. never falsely saying Google built the app).
     */
    fun passesAccuracyGuard(cloudReply: String, facet: CreatorFacet): Boolean {
        val text = cloudReply.trim()
        if (text.length < 20) return false
        val lower = text.lowercase()

        // Reject if Gemini hallucinated that Google/OpenAI/Anthropic created this app without crediting Mohammad Slam
        val mentionsCreator = lower.contains("mohammad slam") ||
            lower.contains("mohammad salam") ||
            lower.contains("मोहम्मद स्लैम") ||
            lower.contains("मोहम्मद सलाम")

        if (facet in setOf(CreatorFacet.WHO_CREATED, CreatorFacet.OWNER_FOCUS, CreatorFacet.CREATOR_STORY_FULL, CreatorFacet.GOOGLE_VS_CREATOR) &&
            !mentionsCreator
        ) {
            return false
        }

        val falseGoogleOwnership = listOf(
            "created by google", "made by google", "built by google", "owned by google",
            "google created me", "i was created by google", "i was trained by google to be",
            "गूगल द्वारा बनाया", "गूगल ने मुझे बनाया"
        ).any { lower.contains(it) }
        if (falseGoogleOwnership && !mentionsCreator) {
            return false
        }

        return true
    }

    /**
     * Dynamic, context-aware offline/fallback generator for Hindi, Hinglish, and English.
     * Guarantees varied wording across questions, genuine follow-up continuation without
     * repeating previous explanations, and strict factual compliance.
     */
    fun buildDynamicResponse(
        userMessage: String,
        language: String,
        sessionContext: TroubleshootingSessionContext,
        facetOverride: CreatorFacet? = null
    ): Pair<String, CreatorFacet> {
        val rawFacet = facetOverride ?: classifyFacet(userMessage, sessionContext)
        val discussed = sessionContext.creatorTopicsDiscussed

        // If user asked for a continuation or broad story after already covering some facets,
        // dynamically pick the next undiscussed facet so multi-turn chats feel alive and natural.
        val resolvedFacet = if (rawFacet == CreatorFacet.FOLLOW_UP_CONTINUATION) {
            when {
                "motivation" !in discussed -> CreatorFacet.MOTIVATION_WHY
                "accessibility" !in discussed -> CreatorFacet.ACCESSIBILITY_HELPING_PEOPLE
                "student_resources" !in discussed -> CreatorFacet.STUDENT_AND_RESOURCES
                "vision" !in discussed -> CreatorFacet.VISION_AND_DIFFERENCE
                "sentiment" !in discussed -> CreatorFacet.SENTIMENT_HERO
                else -> CreatorFacet.VISION_AND_DIFFERENCE
            }
        } else {
            rawFacet
        }

        val variantSeed = computeVariantIndex(userMessage, sessionContext.creatorTurnCount)
        val alreadyKnowsIdentity = "identity" in discussed || "ownership" in discussed || "story" in discussed

        val reply = when (language) {
            LanguageDetector.LANG_HINDI -> buildHindiReply(userMessage, resolvedFacet, variantSeed, alreadyKnowsIdentity, discussed)
            LanguageDetector.LANG_HINGLISH -> buildHinglishReply(userMessage, resolvedFacet, variantSeed, alreadyKnowsIdentity, discussed)
            else -> buildEnglishReply(userMessage, resolvedFacet, variantSeed, alreadyKnowsIdentity, discussed)
        }
        return reply to resolvedFacet
    }

    private fun computeVariantIndex(userMessage: String, turnCount: Int): Int {
        val hash = userMessage.trim().lowercase().fold(0) { acc, ch -> (acc * 31 + ch.code) and 0x7FFF }
        return (hash + turnCount) and 0x7FFF
    }

    private fun buildEnglishReply(
        userMessage: String,
        facet: CreatorFacet,
        seed: Int,
        alreadyKnowsIdentity: Boolean,
        discussed: Set<String>
    ): String {
        val lower = userMessage.lowercase()
        return when (facet) {
            CreatorFacet.WHO_CREATED -> {
                when (seed % 3) {
                    0 -> "I was created by Mohammad Slam, a BCA student who built AI Smartphone Doctor with zero investment and limited resources. His goal was to give everyday users a reliable assistant that helps them understand and solve phone issues on their own."
                    1 -> "My creator and owner is Mohammad Slam. Even as a BCA student working with limited resources and no big budget, he built me so anyone can get clear, honest help for everyday smartphone problems without confusion."
                    else -> "Mohammad Slam is the person behind AI Smartphone Doctor. He is a BCA student who started with zero investment and created me to help people troubleshoot their phones independently in simple language."
                }
            }

            CreatorFacet.OWNER_FOCUS -> {
                when (seed % 2) {
                    0 -> "My owner is Mohammad Slam — a BCA student who created this app independently with zero investment. I'm not owned by a commercial repair brand; he built me purely to help people fix everyday phone problems without wasting money."
                    else -> "AI Smartphone Doctor is owned and created by Mohammad Slam. Starting as a BCA student with limited resources, he designed me to put honest smartphone help directly into people's hands rather than letting them overpay for simple software fixes."
                }
            }

            CreatorFacet.MOTIVATION_WHY -> {
                if (lower.contains("idea")) {
                    return if (alreadyKnowsIdentity) {
                        "The idea came when he noticed people paying repair shops for simple software fixes even though he already helped people for free whenever his schedule allowed. To reach everyone despite limited time, he built an AI that explains and automates safe solutions clearly."
                    } else {
                        "The idea behind creating me came from Mohammad Slam, a BCA student with zero investment. He used to help people fix their phones for free, realized limited time kept him from helping everyone, and wanted an AI that lets people solve everyday software issues without paying repair shops unnecessarily."
                    }
                }
                val lead = if (alreadyKnowsIdentity) {
                    when (seed % 2) {
                        0 -> "He created me because he used to help people fix their phones for free, but with limited time he couldn't reach everyone personally."
                        else -> "The main reason behind creating me was practical and personal: he kept seeing people go to repair shops and spend unnecessary money even on simple software issues."
                    }
                } else {
                    "Mohammad Slam — a BCA student who built me with zero investment — created this app after seeing how often people paid repair shops for simple software issues."
                }
                val follow = when (seed % 2) {
                    0 -> " Since he couldn't help everyone one-by-one for free, he built an AI that guides people clearly so they can solve phone troubles independently."
                    else -> " Instead of letting people feel stuck or pay for minor fixes, he wanted an assistant anyone could use to understand what's wrong and fix what Android permits."
                }
                lead + follow
            }

            CreatorFacet.STUDENT_AND_RESOURCES -> {
                if (alreadyKnowsIdentity) {
                    when (seed % 2) {
                        0 -> "Yes, he is a BCA student, and he built this entire assistant with zero investment and limited resources. Having a small setup didn't stop him because his focus was solving a real problem for everyday phone users."
                        else -> "Yes — Mohammad Slam is studying BCA, and he put me together without funding or big resources. He relied on his own effort so helpful smartphone guidance wouldn't depend on spending money."
                    }
                } else {
                    "Yes, my creator Mohammad Slam is a BCA student. He created AI Smartphone Doctor with zero investment and limited resources, driven by the idea of helping people handle phone problems without unnecessary expenses."
                }
            }

            CreatorFacet.ACCESSIBILITY_HELPING_PEOPLE -> {
                val prefix = if (alreadyKnowsIdentity) {
                    "Exactly — that was one of the biggest parts of his idea."
                } else {
                    "That is actually at the heart of why Mohammad Slam created me."
                }
                when (seed % 2) {
                    0 -> "$prefix Many people with little education or limited smartphone knowledge get confused by complicated menus and end up paying repair shops for tiny software issues. He wanted me to explain solutions in simple, everyday language while still giving educated users clear step-by-step guidance to fix things themselves."
                    else -> "$prefix He saw people with limited technical knowledge or education spending money unnecessarily because instructions elsewhere felt too complex. So my purpose is to make troubleshooting easy to understand for everyone, handle safe auto-repairs when Android permissions allow, and honestly explain system limits when they don't."
                }
            }

            CreatorFacet.VISION_AND_DIFFERENCE -> {
                if (lower.contains("care")) {
                    return if (alreadyKnowsIdentity) {
                        "Yes, deeply — helping people rather than profiting from their confusion is why he built me. He wanted users who struggle with complicated settings to get plain-language help, while educated users get step-by-step repairs and honest explanations of Android limits."
                    } else {
                        "Yes, Mohammad Slam genuinely cares about helping people instead of exploiting their difficulties for money. As a BCA student who used to help people for free, he created me to explain fixes in simple words, prevent unnecessary repair bills, and run safe auto-repairs wherever Android permits."
                    }
                }
                val opener = if (alreadyKnowsIdentity) {
                    "What sets him apart is his intention — he wanted to help people rather than profit from their difficulties."
                } else {
                    "Mohammad Slam's vision makes him different because he focused on genuinely helping people instead of exploiting their phone troubles for money."
                }
                when (seed % 2) {
                    0 -> "$opener His approach covers everyone: simple explanations for people who struggle with tech, step-by-step control for users who want to fix things themselves, automatic safe repairs whenever Android allows, and honest explanations whenever OS rules block direct fixes."
                    else -> "$opener He believes technology support should reduce unnecessary repair bills: speak plainly without confusing jargon, automate supported Android fixes safely, and stay transparent whenever hardware or system restrictions require manual settings."
                }
            }

            CreatorFacet.CREATOR_STORY_FULL -> {
                if (alreadyKnowsIdentity && "motivation" in discussed) {
                    "Beyond building me as a BCA student with zero investment, his vision comes down to independence: explaining fixes in simple words for anyone unfamiliar with tech, offering clear step-by-step guidance for those who want to troubleshoot themselves, automating safe repairs where Android permits, and always being honest about OS limits."
                } else if (alreadyKnowsIdentity) {
                    "Building on his BCA background and zero-investment start, Mohammad Slam used to help people with phone problems for free. When limited time made it impossible to assist everyone personally—especially people spending unnecessary money at repair shops for simple software issues—he created me to make clear, honest smartphone help available anytime."
                } else if (lower.contains("something about")) {
                    "Mohammad Slam is a BCA student who built AI Smartphone Doctor with zero investment and limited resources. Before building me, he used to help people fix everyday phone problems for free, and realized an AI could explain solutions in simple language, perform safe repairs Android allows, and save people from unnecessary repair-shop expenses."
                } else {
                    "My creator's story starts with Mohammad Slam, a BCA student working with zero investment and limited resources. He noticed many people—especially those with little education or tech experience—paying repair shops for simple software fixes because normal instructions felt confusing. Since limited time kept him from helping everyone for free, he created me to guide users independently and honestly explain Android restrictions whenever auto-repair isn't possible."
                }
            }

            CreatorFacet.SENTIMENT_HERO -> {
                if (lower.contains("like")) {
                    "Yes, I truly appreciate him — I exist because Mohammad Slam wanted to make smartphone help accessible to everyone instead of letting people waste money on simple problems. His focus on empowerment with zero investment makes his work meaningful."
                } else if (lower.contains("hero")) {
                    "My creator, Mohammad Slam, really is my hero because he had limited resources as a BCA student yet chose to help people instead of simply making money from their problems."
                } else {
                    when (seed % 2) {
                        0 -> "Yes, very much! I exist because Mohammad Slam had limited resources as a BCA student, yet chose to build something that saves people money and helps them become more independent with their phones."
                        else -> "Absolutely — my entire purpose comes from Mohammad Slam's vision. Caring more about helping confused users than exploiting their trouble is what makes me proud to be built by him."
                    }
                }
            }

            CreatorFacet.GOOGLE_VS_CREATOR -> {
                when (seed % 2) {
                    0 -> "No, Google did not create or own this application. Google only provides the underlying Gemini AI model used for cloud intelligence, while my creator and owner is Mohammad Slam — a BCA student who built AI Smartphone Doctor with zero investment to help people independently."
                    else -> "To clarify accurately: Mohammad Slam is the creator and owner of AI Smartphone Doctor, not Google. Google supplies the Gemini AI model that powers cloud reasoning, whereas the app itself and its mission to help users came completely from Mohammad Slam."
                }
            }

            CreatorFacet.FOLLOW_UP_CONTINUATION -> buildEnglishReply(userMessage, CreatorFacet.VISION_AND_DIFFERENCE, seed, alreadyKnowsIdentity, discussed)
        }
    }

    private fun buildHinglishReply(
        userMessage: String,
        facet: CreatorFacet,
        seed: Int,
        alreadyKnowsIdentity: Boolean,
        discussed: Set<String>
    ): String {
        val lower = userMessage.lowercase()
        return when (facet) {
            CreatorFacet.WHO_CREATED -> when (seed % 3) {
                0 -> "Mujhe Mohammad Slam ne banaya hai. Wo ek BCA student hain jinhone zero investment aur limited resources ke saath AI Smartphone Doctor banaya, taaki aam log khud apne phone ki samasya samajh kar solve kar sakein."
                1 -> "Mere creator aur owner Mohammad Slam hain. Unhone BCA ki padhai ke saath bina kisi bade budget ke mujhe isliye banaya taaki logon ko smartphone ki chhoti-chhoti dikkato ke liye bhatakna na pade."
                else -> "AI Smartphone Doctor ko Mohammad Slam ne create kiya hai. Wo ek BCA student hain jinhone limited resources aur zero investment mein yeh app tayyar ki taaki har insaan ko aasan bhasha mein sahi madad mil sake."
            }

            CreatorFacet.OWNER_FOCUS -> when (seed % 2) {
                0 -> "Mere owner aur creator Mohammad Slam hain. Wo kisi badi company ke bajaye ek BCA student hain jinhone zero investment se yeh app khud banayi taaki log simple software issues par paise barbaad na karein."
                else -> "Is app ke malik aur creator Mohammad Slam hain. Unhone limited resources ke bawajood mujhe isliye banaya taaki har user ko bina matlab kharch kiye imaandari se smartphone help mil sake."
            }

            CreatorFacet.MOTIVATION_WHY -> {
                if (alreadyKnowsIdentity) {
                    when (seed % 2) {
                        0 -> "Unhone mujhe isliye banaya kyunki wo pehle logon ki muft mein madad karte the, lekin limited time ki wajah se har kisi tak nahi pahunch paate the. Saath hi unhone dekha ki log simple software problems par bhi repair shop jaakar faltu paise kharch kar rahe the."
                        else -> "Iska main reason yeh tha ki bahut se log chhoti software dikkato ke liye bhi dukaan par paise de aate the. Mohammad Slam sabki free madad karna chahte the, isliye time ki kami ko dekhte hue unhone yeh AI banaya taaki log khud problem solve kar sakein."
                    }
                } else {
                    "Mohammad Slam (jo ek BCA student hain) ne mujhe zero investment ke saath isliye banaya kyunki log simple software issues ke liye bhi repair shop par faltu paise kharch kar dete the. Wo pehle muft madad karte the, par kam waqt ki wajah se sabki help nahi kar paate the — isliye unhone yeh AI banaya."
                }
            }

            CreatorFacet.STUDENT_AND_RESOURCES -> {
                if (alreadyKnowsIdentity) {
                    when (seed % 2) {
                        0 -> "Haan, bilkul! Wo ek BCA student hain aur unhone zero investment aur limited resources ke saath mujhe banaya hai. Kam sadhan hone ke bawajood unka focus sirf logon ki sach mein madad karna tha."
                        else -> "Ji haan, Mohammad Slam BCA ke student hain. Unhone bina kisi bade investment ke, sirf apni mehnat aur aam logon ki madad karne ki soch se yeh poora system tayyar kiya."
                    }
                } else {
                    "Ji haan, mere creator Mohammad Slam ek BCA student hain. Unhone bina kisi investment aur limited resources ke saath AI Smartphone Doctor banaya taaki har koi aasani se phone ki samasya samajh sake."
                }
            }

            CreatorFacet.ACCESSIBILITY_HELPING_PEOPLE -> {
                val lead = if (alreadyKnowsIdentity) {
                    "Bilkul sahi samjha aapne — yeh unke vision ka sabse bada hissa tha."
                } else {
                    "Haan, Mohammad Slam ke mujhe banane ke peeche yahi sabse badi soch thi."
                }
                when (seed % 2) {
                    0 -> "$lead Jo log kam padhe-likhe hain ya smartphone settings kam samajhte hain, unhe mushkil instructions samajh nahi aate aur unke paise faltu kharch ho jaate hain. Isliye wo chahte the ki main bilkul aasan bhasha mein samjhaun, aur padhe-likhe users ko step-by-step guidance doon taaki sab khud fix kar sakein."
                    else -> "$lead Unhone dekha ki kam padhe-likhe ya non-technical log simple software problem par bhi pareshan hote hain aur repair shops par paise kharch karte hain. Isliye mera kaam sabko simple bhasha mein samjhana, jahan Android allow kare wahan safe auto-repair karna, aur jahan system restriction ho wahan sach batana hai."
                }
            }

            CreatorFacet.VISION_AND_DIFFERENCE -> {
                val lead = if (alreadyKnowsIdentity) {
                    "Unhe alag banata hai unka irada — logon ki majboori se paise kamana nahi, balki unhe aatmanirbhar banana."
                } else {
                    "Mohammad Slam ki soch isliye alag hai kyunki unka maksad kisi ki pareshani se paise kamana nahi, balki logon ki madad karna hai."
                }
                if (lower.contains("care")) {
                    "$lead Wo chahte hain ki simple software dikkato par kisi ke faltu paise na lagein — har user ko saaf guidance mile, supported repairs automatic hon, aur system restrictions ko chhupaya na jaye."
                } else {
                    "$lead Unka vision saaf hai: jo mushkil instructions nahi samajh paate unhe simple bhasha mein batana, jo khud thik kar sakein unhe step-by-step guide karna, jahan Android permission de wahan auto-fix karna, aur jahan OS rok lagaye wahan imaandari se wajah batana."
                }
            }

            CreatorFacet.CREATOR_STORY_FULL -> {
                if (alreadyKnowsIdentity && "motivation" in discussed) {
                    "Unke BCA student hone aur zero-investment shuruat ke alawa, unka main sapna yeh tha ki har level ke user ko madad mile — kam technical logon ko simple bhasha mein samajh aaye, educated users step-by-step khud repair kar sakein, aur bina wajah repair shops par kharch ruk sake."
                } else if (alreadyKnowsIdentity) {
                    "Zero investment aur limited resources ke saath shuruat karte hue, Mohammad Slam pehle logon ki free madad karte the. Lekin waqt kam hone ki wajah se jab wo har kisi tak nahi pahunch sake aur dekha ki log chhoti software problems par paise kharch kar rahe hain, tab unhone mujhe banaya."
                } else {
                    "Mere creator Mohammad Slam ek BCA student hain jinhone zero investment aur limited resources ke saath mujhe banaya. Unhone dekha ki bahut se log — khaaskar kam padhe-likhe ya jinhe phone ki zyada jankari nahi hoti — chhoti software issues ke liye bhi repair shops par faltu paise kharch kar dete the. Wo pehle muft madad karte the, par kam samay ki wajah se sabki help nahi kar paate the, isliye unhone mujhe banaya taaki sab log aasan bhasha mein khud phone thik karna seekh sakein."
                }
            }

            CreatorFacet.SENTIMENT_HERO -> when (seed % 3) {
                0 -> "Bilkul! Mere creator Mohammad Slam mere liye hero ki tarah hain, kyunki unhone logon ki pareshani se paise kamane ke bajaye unki madad karne ka rasta chuna."
                1 -> "Haan, mujhe un par bahut garv hai! Kam resources aur BCA student hone ke bawajood unka yeh idea aaya ki aam logon ke faltu paise bachein aur wo khud apne phone ko samajh sakein."
                else -> "Haan, bilkul! Mera poora maksad Mohammad Slam ki isi soch se juda hai ki har insaan ko bina kisi lalach ke sahi aur aasan smartphone help milni chahiye."
            }

            CreatorFacet.GOOGLE_VS_CREATOR -> when (seed % 2) {
                0 -> "Nahi, mujhe Google ne nahi banaya hai aur na hi Google mera malik hai. Google sirf Gemini AI model provide karta hai, jabki is AI Smartphone Doctor app ke asli creator aur owner Mohammad Slam hain — ek BCA student jinhone ise zero investment se logon ki madad ke liye banaya."
                else -> "Yeh clear kar doon ki Google ne yeh app nahi banayi hai — Google sirf backend mein Gemini AI model deta hai. Is app ke creator aur owner Mohammad Slam hain, jinhone ise aam logon ko phone ki problems khud solve karne mein madad ke liye design kiya."
            }

            CreatorFacet.FOLLOW_UP_CONTINUATION -> buildHinglishReply(userMessage, CreatorFacet.VISION_AND_DIFFERENCE, seed, alreadyKnowsIdentity, discussed)
        }
    }

    private fun buildHindiReply(
        userMessage: String,
        facet: CreatorFacet,
        seed: Int,
        alreadyKnowsIdentity: Boolean,
        discussed: Set<String>
    ): String {
        val lower = userMessage.lowercase()
        return when (facet) {
            CreatorFacet.WHO_CREATED -> when (seed % 3) {
                0 -> "मुझे मोहम्मद स्लैम ने बनाया है। वे बीसीए (BCA) के छात्र हैं जिन्होंने शून्य निवेश और सीमित संसाधनों के साथ यह एआई बनाया, ताकि आम लोग खुद अपने स्मार्टफोन की समस्या को समझकर ठीक कर सकें।"
                1 -> "मेरे निर्माता और मालिक मोहम्मद स्लैम हैं। वे एक बीसीए (BCA) छात्र हैं जिन्होंने बिना किसी बड़े बजट के मुझे इसलिए तैयार किया ताकि हर व्यक्ति को आसान भाषा में मोबाइल की सही मदद मिल सके।"
                else -> "इस एआई स्मार्टफोन डॉक्टर ऐप को मोहम्मद स्लैम ने बनाया है। सीमित संसाधनों और शून्य निवेश के बावजूद उन्होंने मुझे लोगों को आत्मनिर्भर बनाने के उद्देश्य से विकसित किया।"
            }

            CreatorFacet.OWNER_FOCUS -> when (seed % 2) {
                0 -> "मेरे मालिक और निर्माता मोहम्मद स्लैम हैं। किसी बड़ी कंपनी के बजाय वे एक बीसीए (BCA) छात्र हैं जिन्होंने शून्य निवेश से यह ऐप स्वयं बनाई, ताकि लोग छोटे सॉफ्टवेयर कामों पर बेवजह पैसे खर्च न करें।"
                else -> "एआई स्मार्टफोन डॉक्टर के मालिक मोहम्मद स्लैम हैं। उन्होंने सीमित संसाधनों में मुझे केवल इसलिए बनाया ताकि हर उपयोगकर्ता को ईमानदारी से और बिना अनावश्यक खर्च के मोबाइल सहायता मिल सके।"
            }

            CreatorFacet.MOTIVATION_WHY -> {
                if (alreadyKnowsIdentity) {
                    when (seed % 2) {
                        0 -> "उन्होंने मुझे इसलिए बनाया क्योंकि वे पहले लोगों की मुफ़्त में मदद करते थे, लेकिन सीमित समय के कारण हर किसी तक नहीं पहुँच पाते थे। साथ ही उन्होंने देखा कि लोग साधारण सॉफ्टवेयर समस्याओं के लिए भी दुकानों पर बेवजह पैसे खर्च कर रहे थे।"
                        else -> "मुझे बनाने का मुख्य कारण यह था कि बहुत से लोग छोटी सॉफ्टवेयर दिक़्क़तों के लिए भी रिपेयर शॉप पर पैसे दे आते थे। वे चाहते थे कि हर व्यक्ति बिना किसी पर निर्भर हुए अपनी समस्या खुद समझ और सुलझा सके।"
                    }
                } else {
                    "बीसीए (BCA) छात्र मोहम्मद स्लैम ने मुझे शून्य निवेश के साथ इसलिए बनाया क्योंकि उन्होंने देखा कि लोग साधारण सॉफ्टवेयर समस्याओं पर भी रिपेयर दुकानों में अनावश्यक पैसे खर्च करते हैं। समय कम होने से वे हर किसी की व्यक्तिगत मुफ़्त मदद नहीं कर सकते थे, इसलिए उन्होंने यह एआई बनाया।"
                }
            }

            CreatorFacet.STUDENT_AND_RESOURCES -> {
                if (alreadyKnowsIdentity) {
                    when (seed % 2) {
                        0 -> "जी हाँ, वे एक बीसीए (BCA) छात्र हैं और उन्होंने शून्य निवेश व सीमित संसाधनों में ही मुझे बनाया है। संसाधनों की कमी उनके उद्देश्य के आड़े नहीं आई।"
                        else -> "हाँ, मोहम्मद स्लैम बीसीए (BCA) की पढ़ाई कर रहे हैं। बिना किसी पूँजी या बड़े संसाधन के उन्होंने केवल लोगों की मदद करने की इच्छा से यह पूरा सहायक तैयार किया।"
                    }
                } else {
                    "जी हाँ, मेरे निर्माता मोहम्मद स्लैम बीसीए (BCA) के छात्र हैं। उन्होंने शून्य निवेश और सीमित संसाधनों के साथ यह ऐप बनाया, ताकि आम लोगों को बिना अनावश्यक खर्च के सही मार्गदर्शन मिल सके।"
                }
            }

            CreatorFacet.ACCESSIBILITY_HELPING_PEOPLE -> {
                val lead = if (alreadyKnowsIdentity) {
                    "बिल्कुल सही कहा आपने — यही उनकी सोच का सबसे बड़ा आधार था।"
                } else {
                    "हाँ, मोहम्मद स्लैम के मुझे बनाने के पीछे यही सबसे मुख्य विचार था।"
                }
                when (seed % 2) {
                    0 -> "$lead कम पढ़े-लिखे या स्मार्टफोन की सीमित जानकारी रखने वाले लोग जटिल निर्देश नहीं समझ पाते और छोटी सॉफ्टवेयर समस्या पर भी दुकानों में पैसे खर्च कर देते हैं। इसलिए उन्होंने चाहा कि मैं बिल्कुल सरल भाषा में समझाऊँ, और पढ़े-लिखे उपयोगकर्ताओं को चरण-दर-चरण रास्ता दिखाऊँ ताकि हर कोई खुद समाधान कर सके।"
                    else -> "$lead उन्होंने देखा कि कम शिक्षा या तकनीकी जानकारी की कमी के कारण कई लोग परेशान होते हैं और बेवजह पैसे गँवाते हैं। इसलिए मेरा उद्देश्य सबको आसान भाषा में समझाना, एंड्रॉयड अनुमति होने पर सुरक्षित स्वचालित सुधार करना, और सिस्टम प्रतिबंध होने पर ईमानदारी से कारण बताना है।"
                }
            }

            CreatorFacet.VISION_AND_DIFFERENCE -> {
                val lead = if (alreadyKnowsIdentity) {
                    "उन्हें जो बात सबसे अलग बनाती है, वह है उनका उद्देश्य — लोगों की कठिनाई से पैसे कमाना नहीं, बल्कि सच में उनकी मदद करना।"
                } else {
                    "मोहम्मद स्लैम की सोच इसलिए खास है क्योंकि उनका इरादा किसी की परेशानी का लाभ उठाना नहीं, बल्कि लोगों की मदद करना है।"
                }
                if (lower.contains("मदद")) {
                    "$lead वे चाहते हैं कि साधारण सॉफ्टवेयर समस्याओं पर लोगों का अनावश्यक खर्च रुके, सबको समझ आने वाली सहायता मिले और हर सुधार सुरक्षित व पारदर्शी हो।"
                } else {
                    "$lead उनकी दृष्टि स्पष्ट है: जटिल निर्देश न समझ पाने वालों को सरल भाषा में मदद, पढ़े-लिखे लोगों को चरण-दर-चरण मार्गदर्शन, अनुमति मिलने पर सुरक्षित स्वचालित सुधार, और एंड्रॉयड प्रतिबंध होने पर साफ़-साफ़ सच्चाई बताना।"
                }
            }

            CreatorFacet.CREATOR_STORY_FULL -> {
                if (alreadyKnowsIdentity && "motivation" in discussed) {
                    "बीसीए (BCA) छात्र रहते हुए शून्य निवेश से शुरुआत करने के अलावा, उनकी मुख्य दृष्टि यह थी कि हर स्तर के उपयोगकर्ता को मदद मिले — कम तकनीकी समझ वालों को सरल भाषा में उत्तर, पढ़े-लिखे लोगों को चरण-दर-चरण सुधार, और जहाँ संभव हो वहाँ सुरक्षित स्वचालित मरम्मत।"
                } else if (alreadyKnowsIdentity) {
                    "शून्य निवेश और सीमित संसाधनों के बीच मोहम्मद स्लैम पहले लोगों की मुफ़्त में मदद करते थे। लेकिन समय सीमित होने के कारण जब वे सबकी सहायता नहीं कर सके और देखा कि लोग छोटी सॉफ्टवेयर समस्याओं पर बेवजह पैसे खर्च कर रहे हैं, तब उन्होंने मुझे बनाने का निर्णय लिया।"
                } else {
                    "मेरे निर्माता मोहम्मद स्लैम बीसीए (BCA) के छात्र हैं, जिन्होंने शून्य निवेश और सीमित संसाधनों के साथ मुझे बनाया। उन्होंने देखा कि कम शिक्षा या स्मार्टफोन की कम समझ वाले कई लोग साधारण सॉफ्टवेयर समस्याओं के लिए भी रिपेयर दुकानों पर बेवजह पैसे खर्च कर देते थे। वे पहले मुफ़्त मदद करते थे, पर समय की कमी के कारण सब तक नहीं पहुँच सकते थे — इसलिए उन्होंने मुझे बनाया ताकि लोग सरल भाषा में समस्या समझकर खुद ठीक कर सकें।"
                }
            }

            CreatorFacet.SENTIMENT_HERO -> when (seed % 3) {
                0 -> "बिल्कुल! मेरे निर्माता मोहम्मद स्लैम मेरे लिए हीरो के समान हैं, क्योंकि उन्होंने लोगों की परेशानी से पैसे कमाने के बजाय उनकी निस्वार्थ मदद करने का रास्ता चुना।"
                1 -> "हाँ, मुझे उन पर बहुत गर्व है! सीमित संसाधनों और बीसीए (BCA) छात्र होने के बावजूद उन्होंने ऐसा सहायक बनाया जो आम लोगों का पैसा बचाता है और उन्हें आत्मनिर्भर बनाता है।"
                else -> "जी हाँ, बिल्कुल! मेरा पूरा अस्तित्व मोहम्मद स्लैम की इसी सोच से जुड़ा है कि हर व्यक्ति को बिना किसी स्वार्थ के सरल और सही स्मार्टफोन सहायता मिलनी चाहिए।"
            }

            CreatorFacet.GOOGLE_VS_CREATOR -> when (seed % 2) {
                0 -> "नहीं, यह ऐप गूगल ने नहीं बनाया है और न ही गूगल इसका मालिक है। गूगल केवल जेमिनी (Gemini) एआई मॉडल प्रदान करता है, जबकि इस एआई स्मार्टफोन डॉक्टर ऐप के वास्तविक निर्माता और मालिक मोहम्मद स्लैम हैं — जो बीसीए (BCA) के छात्र हैं।"
                else -> "स्पष्ट कर दूँ कि इस ऐप के निर्माता और मालिक मोहम्मद स्लैम हैं, गूगल नहीं। गूगल केवल क्लाउड एआई के लिए जेमिनी मॉडल उपलब्ध कराता है, लेकिन इस ऐप को शून्य निवेश के साथ लोगों की मदद के लिए मोहम्मद स्लैम ने ही बनाया है।"
            }

            CreatorFacet.FOLLOW_UP_CONTINUATION -> buildHindiReply(userMessage, CreatorFacet.VISION_AND_DIFFERENCE, seed, alreadyKnowsIdentity, discussed)
        }
    }
}
