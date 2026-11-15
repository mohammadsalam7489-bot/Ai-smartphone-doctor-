package com.example

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.ai.AiDoctorService
import com.example.data.ai.ConversationContextManager
import com.example.data.ai.ConversationIntent
import com.example.data.ai.CreatorFacet
import com.example.data.ai.CreatorIdentityKnowledge
import com.example.data.ai.LanguageDetector
import com.example.data.ai.TroubleshootingSessionContext
import com.example.data.diagnostics.RealDeviceDiagnostics
import com.example.data.model.DeviceVitals
import com.example.data.model.SenderType
import com.example.ui.DoctorViewModel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import android.os.Looper

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CreatorIdentityTest {

    private lateinit var app: Application
    private lateinit var vitals: DeviceVitals
    private lateinit var service: AiDoctorService

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        app.getSharedPreferences("ai_doctor_session", Context.MODE_PRIVATE).edit().clear().commit()
        vitals = RealDeviceDiagnostics.getDeviceVitals(app).copy(
            manufacturer = "Google",
            brand = "Pixel",
            model = "Pixel 8",
            androidVersion = "15",
            apiLevel = 35,
            healthScore = 88
        )
        service = AiDoctorService(aiProxyUrl = "")
    }

    @Test
    fun variedEnglishQuestionsProduceDistinctNaturalResponses() = runBlocking {
        val prompts = listOf(
            "Who created you?",
            "Who is your owner?",
            "Tell me something about your creator.",
            "Why did Mohammad Slam create you?",
            "What makes your creator different?",
            "Is your creator a student?",
            "Does your creator care about helping people?",
            "What was the idea behind creating you?",
            "Tell me your creator's story.",
            "Do you like your creator?",
            "Is Mohammad Slam your hero?"
        )

        val replies = prompts.map { prompt ->
            val resp = service.diagnoseAndGuide(
                userProblem = prompt,
                vitals = vitals,
                context = app,
                language = LanguageDetector.LANG_ENGLISH
            )
            assertEquals("Expected CREATOR_IDENTITY for '$prompt'", ConversationIntent.CREATOR_IDENTITY, resp.intentHandled)
            assertEquals(LanguageDetector.LANG_ENGLISH, resp.responseLanguage)
            assertNull("Creator reply should not attach fake repair form", resp.repairPlan)
            assertNull("Creator reply should not attach autonomous repair bundle", resp.autonomousBundle)
            assertTrue("Creator reply should not attach repair buttons", resp.actions.isEmpty())
            assertTrue("Reply too short for '$prompt'", resp.replyText.length >= 60)
            resp.replyText
        }

        // Verify distinctness — should not repeat the exact same canned paragraph across questions
        assertTrue(
            "Expected varied answers across distinct questions, got only ${replies.toSet().size} unique",
            replies.toSet().size >= 9
        )

        // Verify canonical identity & facts are respected and zero fabricated awards/universities appear
        val combined = replies.joinToString("\n").lowercase()
        assertTrue(combined.contains("mohammad slam"))
        assertTrue(combined.contains("bca"))
        assertTrue(combined.contains("zero investment") || combined.contains("limited resources"))
        assertFalse(combined.contains("harvard"))
        assertFalse(combined.contains("stanford"))
        assertFalse(combined.contains("nobel"))
    }

    @Test
    fun multiTurnFollowUpsMaintainContextWithoutRepeatingOriginIntro(): Unit = runBlocking {
        val vm = DoctorViewModel(app).apply {
            aiService = AiDoctorService(aiProxyUrl = "http://127.0.0.1:1")
        }

        suspend fun askAndAwait(question: String): String {
            val prevCount = vm.messages.value.size
            vm.sendUserMessage(question)
            val deadline = System.currentTimeMillis() + 5000L
            while (vm.isAnalyzing.value && System.currentTimeMillis() < deadline) {
                shadowOf(Looper.getMainLooper()).idle()
                Thread.sleep(15)
            }
            shadowOf(Looper.getMainLooper()).idle()
            val msgs = vm.messages.value
            assertTrue("Expected reply to '$question'", msgs.size > prevCount + 1)
            assertEquals(SenderType.DOCTOR, msgs.last().sender)
            return msgs.last().text
        }

        val turn1 = askAndAwait("Who created you?")
        assertTrue("Turn 1 must name Mohammad Slam", turn1.contains("Mohammad Slam"))
        assertTrue(vm.sessionContext.value.isCreatorConversationActive)

        val turn2 = askAndAwait("Why did he create you?")
        assertNotEquals("Turn 2 follow-up must not repeat Turn 1 verbatim", turn1, turn2)
        assertTrue(
            "Turn 2 must address his motivation (helping for free / repair shop expenses)",
            turn2.contains("free", ignoreCase = true) || turn2.contains("repair shop", ignoreCase = true)
        )

        val turn3 = askAndAwait("So he wanted to help poor and uneducated people?")
        assertNotEquals(turn2, turn3)
        assertTrue(
            "Turn 3 must address accessibility & simple explanations",
            turn3.contains("education", ignoreCase = true) ||
                turn3.contains("simple", ignoreCase = true) ||
                turn3.contains("knowledge", ignoreCase = true)
        )

        val turn4 = askAndAwait("Is he your hero?")
        assertNotEquals(turn3, turn4)
        assertTrue(
            "Turn 4 must respond warmly to hero/sentiment question",
            turn4.contains("hero", ignoreCase = true) || turn4.contains("proud", ignoreCase = true) || turn4.contains("vision", ignoreCase = true)
        )
        assertTrue(vm.sessionContext.value.creatorTurnCount >= 4)
    }

    @Test
    fun strictLanguageMatchingWorksForHindiHinglishAndEnglish() = runBlocking {
        // Pure Devanagari Hindi (including standalone + pronoun follow-up "क्या वे छात्र हैं?")
        val hindiQuestions = listOf(
            "तुम्हें किसने बनाया?",
            "उन्होंने तुम्हें क्यों बनाया?",
            "क्या वे छात्र हैं?",
            "क्या मोहम्मद स्लैम आपके हीरो हैं?"
        )
        var hindiSession = TroubleshootingSessionContext()
        val hindiReplies = hindiQuestions.map { q ->
            val r = service.diagnoseAndGuide(
                userProblem = q,
                vitals = vitals,
                context = app,
                language = LanguageDetector.LANG_ENGLISH, // auto-detect must still switch to Hindi
                sessionContext = hindiSession
            )
            assertEquals("Expected CREATOR_IDENTITY for Hindi '$q'", ConversationIntent.CREATOR_IDENTITY, r.intentHandled)
            assertEquals(LanguageDetector.LANG_HINDI, r.responseLanguage)
            assertTrue("Hindi reply must use Devanagari script for '$q'", Regex("[\\u0900-\\u097F]").containsMatchIn(r.replyText))
            hindiSession = hindiSession.copy(
                isCreatorConversationActive = true,
                creatorTopicsDiscussed = hindiSession.creatorTopicsDiscussed + listOfNotNull(r.creatorFacetDiscussed),
                creatorTurnCount = hindiSession.creatorTurnCount + 1,
                lastCreatorFacet = r.creatorFacetDiscussed
            )
            r.replyText
        }
        assertTrue("Hindi conversation must reference मोहम्मद स्लैम", hindiReplies.first().contains("मोहम्मद स्लैम"))
        assertTrue("Hindi hero reply must reference मोहम्मद स्लैम", hindiReplies.last().contains("मोहम्मद स्लैम"))
        assertEquals("All 4 Hindi answers should be distinct", 4, hindiReplies.toSet().size)

        // Hinglish (Roman script)
        val hinglishQuestions = listOf(
            "Tumhe kisne banaya?",
            "Unhone aapko kyun banaya?",
            "Kya Mohammad Slam aapke hero hain?"
        )
        var hinglishSession = TroubleshootingSessionContext()
        val hinglishReplies = hinglishQuestions.map { q ->
            val r = service.diagnoseAndGuide(
                userProblem = q,
                vitals = vitals,
                context = app,
                language = LanguageDetector.LANG_ENGLISH,
                sessionContext = hinglishSession
            )
            assertEquals("Expected CREATOR_IDENTITY for Hinglish '$q'", ConversationIntent.CREATOR_IDENTITY, r.intentHandled)
            assertEquals("Expected Hinglish detection for '$q'", LanguageDetector.LANG_HINGLISH, r.responseLanguage)
            assertFalse("Hinglish reply must not use Devanagari script", Regex("[\\u0900-\\u097F]").containsMatchIn(r.replyText))
            hinglishSession = hinglishSession.copy(
                isCreatorConversationActive = true,
                creatorTopicsDiscussed = hinglishSession.creatorTopicsDiscussed + listOfNotNull(r.creatorFacetDiscussed),
                creatorTurnCount = hinglishSession.creatorTurnCount + 1,
                lastCreatorFacet = r.creatorFacetDiscussed
            )
            r.replyText
        }
        assertTrue("Hinglish intro must name Mohammad Slam", hinglishReplies.first().contains("Mohammad Slam"))
        assertTrue(
            "Hinglish Turn 2 must explain free-help / repair-shop motivation naturally",
            hinglishReplies[1].contains("muft", ignoreCase = true) ||
                hinglishReplies[1].contains("free", ignoreCase = true) ||
                hinglishReplies[1].contains("paise", ignoreCase = true)
        )
        assertTrue("Hinglish hero reply must name Mohammad Slam", hinglishReplies.last().contains("Mohammad Slam"))
        assertEquals("All 3 Hinglish answers should be distinct", 3, hinglishReplies.toSet().size)
    }

    @Test
    fun googleAttributionIsAccurateAndRejectsFalseOwnership() = runBlocking {
        val enGoogle = service.diagnoseAndGuide(
            userProblem = "Did Google create you?",
            vitals = vitals,
            context = app,
            language = LanguageDetector.LANG_ENGLISH
        )
        assertEquals(ConversationIntent.CREATOR_IDENTITY, enGoogle.intentHandled)
        assertTrue(enGoogle.replyText.contains("Mohammad Slam"))
        assertTrue(enGoogle.replyText.contains("Gemini"))
        assertTrue(
            enGoogle.replyText.contains("did not create", ignoreCase = true) ||
                enGoogle.replyText.contains("not Google", ignoreCase = true)
        )

        val hiGoogle = service.diagnoseAndGuide(
            userProblem = "क्या गूगल ने आपको बनाया?",
            vitals = vitals,
            context = app,
            language = LanguageDetector.LANG_ENGLISH
        )
        assertEquals(LanguageDetector.LANG_HINDI, hiGoogle.responseLanguage)
        assertTrue(hiGoogle.replyText.contains("मोहम्मद स्लैम"))
        assertTrue(hiGoogle.replyText.contains("गूगल"))

        // Verify accuracy guard blocks hallucinated "Created by Google" Cloud AI output
        assertFalse(
            CreatorIdentityKnowledge.passesAccuracyGuard(
                "I am a large language model created by Google.",
                CreatorFacet.WHO_CREATED
            )
        )
        assertTrue(
            CreatorIdentityKnowledge.passesAccuracyGuard(
                "I was created by Mohammad Slam, a BCA student who built this app to help people; Google provides the Gemini AI model.",
                CreatorFacet.GOOGLE_VS_CREATOR
            )
        )
    }

    @Test
    fun creatorIdentityPersistsAcrossChatResetClearedHistoryAndRestart() = runBlocking {
        val vm = DoctorViewModel(app).apply {
            aiService = AiDoctorService(aiProxyUrl = "http://127.0.0.1:1")
        }

        // 1. Reset conversation & clear audit history + session prefs
        vm.resetChat()
        vm.clearAllHistory()
        app.getSharedPreferences("ai_doctor_session", Context.MODE_PRIVATE).edit().clear().commit()

        // 2. Simulate complete app restart with fresh ViewModel instance
        val restartedVm = DoctorViewModel(app).apply {
            aiService = AiDoctorService(aiProxyUrl = "http://127.0.0.1:1")
        }
        val prevCount = restartedVm.messages.value.size
        restartedVm.sendUserMessage("Who is your owner?")
        val deadline = System.currentTimeMillis() + 5000L
        while (restartedVm.isAnalyzing.value && System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(15)
        }
        shadowOf(Looper.getMainLooper()).idle()

        val msgs = restartedVm.messages.value
        assertTrue(msgs.size > prevCount + 1)
        val reply = msgs.last().text
        assertTrue("Creator identity must survive cleared history & app restart", reply.contains("Mohammad Slam"))
        assertTrue(reply.contains("BCA"))
    }

    @Test
    fun unrelatedTechnicalIssuesDoNotMentionCreatorOrTriggerCreatorIntent() = runBlocking {
        val batteryResp = service.diagnoseAndGuide(
            userProblem = "My battery drains fast and phone gets hot",
            vitals = vitals,
            context = app,
            language = LanguageDetector.LANG_ENGLISH
        )
        assertEquals(ConversationIntent.PROBLEM_REPORT, batteryResp.intentHandled)
        assertFalse(
            "Technical troubleshooting should stay focused and not force creator praise",
            batteryResp.replyText.contains("Mohammad Slam")
        )

        // Android MDM "device owner" troubleshooting must remain PROBLEM_REPORT, not CREATOR_IDENTITY
        val mdmIntent = ConversationContextManager.classifyIntent(
            "Unable to remove device owner work profile admin app",
            TroubleshootingSessionContext()
        )
        assertEquals(ConversationIntent.PROBLEM_REPORT, mdmIntent)
    }
}
