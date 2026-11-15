package com.example.data.ai

import com.example.data.diagnostics.RealDeviceDiagnostics
import com.example.data.knowledge.KnowledgeIssueRecord
import com.example.data.model.AutomationCapability
import com.example.data.model.DeviceVitals
import com.example.data.model.DoctorAction
import com.example.data.model.OemCompatibilityProfile
import com.example.data.model.RiskClassification
import com.example.data.model.VerificationOutcome
import java.util.Locale

data class LocalizedActionText(
    val title: String,
    val description: String,
    val whyRequired: String,
    val whatWillChange: String,
    val capabilityBadge: String,
    val riskLabel: String
)

/**
 * Ensures zero cross-language leakage across Hindi, Hinglish, and English in
 * structured telemetry evidence, fallback KB plans, action cards, and verification UI.
 */
object LocalizedDiagnosticFormatter {

    fun buildVerifiedTelemetryEvidence(
        vitals: DeviceVitals,
        oemProfile: OemCompatibilityProfile,
        language: String
    ): List<String> = when (language) {
        LanguageDetector.LANG_HINDI -> buildList {
            add("डिवाइस पहचान (सत्यापित): ${vitals.brand} ${vitals.model} (${oemProfile.oemName} • ${oemProfile.osFlavor}, ${oemProfile.androidCompatibility.versionLabel})")
            if (vitals.batteryVerified) {
                add("बैटरी (BatteryManager सत्यापित): ${vitals.batteryLevel}% • स्थिति: ${translateBatteryStatusHi(vitals.batteryStatus)} (${translateBatteryHealthHi(vitals.batteryHealth)})")
            } else {
                add("बैटरी सेंसर: एकत्र नहीं हो सका (रीडिंग उपलब्ध नहीं)")
            }
            if (vitals.temperatureVerified) {
                add("तापमान व थर्मल (सत्यापित): ${vitals.batteryTempC}°C • थर्मल स्तर: ${translateThermalHi(vitals.thermalStatus)} • बैटरी सेवर: ${if (vitals.isPowerSaveMode) "चालू" else "बंद"}")
            } else {
                add("तापमान सेंसर: हार्डवेयर थर्मिस्टर रीडिंग उपलब्ध नहीं — बिना प्रमाण ओवरहीटिंग घोषित नहीं की गई")
            }
            if (vitals.ramVerified) {
                add("रैम मेमोरी (ActivityManager सत्यापित): ${RealDeviceDiagnostics.formatBytes(vitals.ramFreeBytes)} खाली / ${RealDeviceDiagnostics.formatBytes(vitals.ramTotalBytes)} (${vitals.ramUsedPercent}% उपयोग, कम मेमोरी: ${if (vitals.isLowMemory) "हाँ" else "नहीं"})")
            } else {
                add("रैम मेमोरी: सिस्टम रीडिंग उपलब्ध नहीं")
            }
            if (vitals.storageVerified) {
                add("स्टोरेज (StatFs सत्यापित): ${RealDeviceDiagnostics.formatBytes(vitals.storageFreeBytes)} खाली / ${RealDeviceDiagnostics.formatBytes(vitals.storageTotalBytes)} (${vitals.storageUsedPercent}% उपयोग)")
            } else {
                add("स्टोरेज: विभाजन आँकड़े उपलब्ध नहीं")
            }
            if (vitals.networkVerified) {
                add("नेटवर्क (ConnectivityManager सत्यापित): ${translateNetworkHi(vitals.networkType)} (${if (vitals.isInternetAvailable) "इंटरनेट सक्रिय" else "ऑफ़लाइन"})")
            } else {
                add("नेटवर्क स्थिति: सत्यापित नहीं हो सकी")
            }
            add("स्वास्थ्य स्कोर (सत्यापित सेंसर आधारित): ${vitals.healthScore}/100")
        }

        LanguageDetector.LANG_HINGLISH -> buildList {
            add("Device (Verified): ${vitals.brand} ${vitals.model} (${oemProfile.oemName} • ${oemProfile.osFlavor}, ${oemProfile.androidCompatibility.versionLabel})")
            if (vitals.batteryVerified) {
                add("Battery (Verified via BatteryManager): ${vitals.batteryLevel}% • ${vitals.batteryStatus} (Health: ${vitals.batteryHealth})")
            } else {
                add("Battery Sensor: Collect nahi ho paya (Unavailable)")
            }
            if (vitals.temperatureVerified) {
                add("Temp & Thermal (Verified): ${vitals.batteryTempC}°C • ${vitals.thermalStatus} • PowerSaver=${if (vitals.isPowerSaveMode) "ON" else "OFF"}")
            } else {
                add("Temperature Sensor: Reading available nahi hai (Overheating assume nahi kiya gaya)")
            }
            if (vitals.ramVerified) {
                add("RAM (Verified via ActivityManager): ${RealDeviceDiagnostics.formatBytes(vitals.ramFreeBytes)} free (${vitals.ramUsedPercent}% used, LowMem=${vitals.isLowMemory})")
            } else {
                add("RAM: System telemetry available nahi hai")
            }
            if (vitals.storageVerified) {
                add("Storage (Verified via StatFs): ${RealDeviceDiagnostics.formatBytes(vitals.storageFreeBytes)} free (${vitals.storageUsedPercent}% used)")
            } else {
                add("Storage: Partition reading available nahi hai")
            }
            if (vitals.networkVerified) {
                add("Network (Verified via ConnectivityManager): ${vitals.networkType} (${if (vitals.isInternetAvailable) "Online" else "Offline"})")
            }
            add("Health Score (Verified telemetry only): ${vitals.healthScore}/100")
        }

        else -> buildList {
            add("Device (Verified Build): ${vitals.brand} ${vitals.model} (${oemProfile.oemName} • ${oemProfile.osFlavor}, ${oemProfile.androidCompatibility.versionLabel})")
            if (vitals.batteryVerified) {
                add("Battery (Verified BatteryManager): ${vitals.batteryLevel}% • ${vitals.batteryStatus} (Health: ${vitals.batteryHealth})")
            } else {
                add("Battery Telemetry: Not collected / unavailable")
            }
            if (vitals.temperatureVerified) {
                add("Thermal (Verified): ${vitals.batteryTempC}°C • ${vitals.thermalStatus} • PowerSaver=${if (vitals.isPowerSaveMode) "ON" else "OFF"}")
            } else {
                add("Temperature Telemetry: Battery thermistor reading unavailable (no unverified overheating claim)")
            }
            if (vitals.ramVerified) {
                add("RAM (Verified ActivityManager): ${RealDeviceDiagnostics.formatBytes(vitals.ramFreeBytes)} free (${vitals.ramUsedPercent}% used, LowMem=${vitals.isLowMemory})")
            } else {
                add("RAM Telemetry: Not collected / unavailable")
            }
            if (vitals.storageVerified) {
                add("Storage (Verified StatFs): ${RealDeviceDiagnostics.formatBytes(vitals.storageFreeBytes)} free (${vitals.storageUsedPercent}% used)")
            } else {
                add("Storage Telemetry: Not collected / unavailable")
            }
            if (vitals.networkVerified) {
                add("Network (Verified ConnectivityManager): ${vitals.networkType} (${if (vitals.isInternetAvailable) "Online" else "Offline"})")
            } else {
                add("Network Telemetry: Not collected / unavailable")
            }
            add("Health Score (Calculated strictly from verified collectors): ${vitals.healthScore}/100")
        }
    }

    fun buildUnverifiedAssumptions(
        vitals: DeviceVitals,
        oemProfile: OemCompatibilityProfile,
        language: String
    ): List<String> = when (language) {
        LanguageDetector.LANG_HINDI -> buildList {
            add("एंड्रॉइड सुरक्षा सैंडबॉक्स (${oemProfile.androidCompatibility.versionLabel}) के कारण अन्य तृतीय-पक्ष ऐप्स का निजी कैश और पृष्ठभूमि वेक-लॉक सीधे मापे नहीं जा सकते।")
            vitals.unverifiedMetrics.forEach { metric ->
                add("एकत्र नहीं हो सका: ${translateMetricNameHi(metric)} (डिवाइस/सेंसर उपलब्ध नहीं)")
            }
        }
        LanguageDetector.LANG_HINGLISH -> buildList {
            add("Android ${oemProfile.androidCompatibility.versionLabel} sandbox ki wajah se doosre apps ka private cache aur background wakelocks directly read nahi ho sakte.")
            vitals.unverifiedMetrics.forEach { metric ->
                add("Collect nahi hua: $metric")
            }
        }
        else -> buildList {
            add("Third-party app private caches & per-app background wakelocks are sandboxed by ${oemProfile.androidCompatibility.versionLabel}.")
            vitals.unverifiedMetrics.forEach { metric ->
                add("Not collected / OS-unavailable: $metric")
            }
        }
    }

    fun localizeCategoryName(categoryNumber: Int, rawEnglish: String, language: String): String {
        if (language != LanguageDetector.LANG_HINDI) return rawEnglish
        return when (categoryNumber) {
            1 -> "बूट, रीस्टार्ट और सिस्टम क्रैश"
            2 -> "फोन धीमा चलना और हैंग होना"
            3 -> "पॉकेट टच और अचानक स्क्रीन जागना"
            4 -> "ऐप क्रैश और बार-बार बंद होना"
            5 -> "बैटरी खपत और चार्जिंग समस्याएं"
            6 -> "स्टोरेज भरना और फ़ाइल प्रबंधन"
            7 -> "वाई-फाई और इंटरनेट कनेक्शन"
            8 -> "टचस्क्रीन और डिस्प्ले समस्याएं"
            9 -> "स्पीकर, माइक और ऑडियो खराबी"
            10 -> "नोटिफ़िकेशन और अलर्ट देरी"
            11 -> "गेमिंग एफपीएस गिरावट और लैग"
            12 -> "ब्लूटूथ और वायरलेस पेयरिंग"
            13 -> "ऐप अनुमतियाँ और प्रतिबंधित सेटिंग्स"
            14 -> "सिस्टम अपडेट (ओटीए) त्रुटियाँ"
            15 -> "गूगल खाता लॉगिन और सिंक"
            16 -> "कीबोर्ड और टाइपिंग समस्याएं"
            17 -> "टॉकबैक और एक्सेसिबिलिटी नियंत्रण"
            18 -> "कैमरा और फ़ोटो गुणवत्ता"
            19 -> "जीपीएस और लोकेशन सटीकता"
            20 -> "कॉल ड्रॉप, नेटवर्क व एसएमएस"
            21 -> "प्ले स्टोर डाउनलोड लंबित त्रुटि"
            22 -> "होम स्क्रीन और लॉन्चर क्रैश"
            23 -> "क्लाउड बैकअप और रिस्टोर"
            24 -> "सिस्टम यूआई और स्टेटस बार"
            25 -> "डेवलपर विकल्प और यूएसबी सेटिंग्स"
            26 -> "सिम कार्ड, 5G नेटवर्क और कॉल सिग्नल"
            27 -> "अधिक गर्म होना और थर्मल नियंत्रण"
            28 -> "यूएसबी फ़ाइल ट्रांसफ़र और ओटीजी"
            29 -> "वर्क प्रोफ़ाइल और डिवाइस प्रबंधन"
            else -> rawEnglish
        }
    }

    fun buildHindiIssueSummaryTitle(issue: KnowledgeIssueRecord): String {
        val catHi = localizeCategoryName(issue.categoryNumber, issue.categoryName, LanguageDetector.LANG_HINDI)
        return "संभावित श्रेणी: $catHi (${issue.problemId})"
    }

    fun buildHindiFallbackSteps(issue: KnowledgeIssueRecord, oemPath: String): List<String> =
        when (issue.categoryNumber) {
            5 -> listOf(
                "बैटरी उपयोग सेटिंग्स में देखें कि कौन-सा ऐप या पृष्ठभूमि सेवा सबसे अधिक बैटरी खर्च कर रही है और अनावश्यक ऐप्स पर बैकग्राउंड सीमा लगाएं।",
                "मूल चार्जर व केबल से चार्जिंग जांचें, पोर्ट में धूल न होने की पुष्टि करें और चार्जिंग के समय भारी गेमिंग न करें।",
                "अधिक बैटरी बचाने के लिए बैटरी सेवर चालू करें और स्क्रीन समय समाप्ति 30 सेकंड पर सेट करें।",
                "डिवाइस सेटिंग्स मार्ग: `$oemPath`"
            )
            27 -> listOf(
                "चार्जिंग या भारी उपयोग के दौरान फोन का मोटा कवर हटा दें और सीधी धूप या गर्म जगह से दूर रखें।",
                "पृष्ठभूमि में चल रहे भारी ऐप्स बंद करें और उच्च ब्राइटनेस या 5G हॉटस्पॉट लगातार चालू न रखें।",
                "थर्मल व बैटरी सेटिंग्स की जांच के लिए नीचे दिए गए सुरक्षित निदान बटन चलाएं।",
                "डिवाइस सेटिंग्स मार्ग: `$oemPath`"
            )
            7 -> listOf(
                "वाई-फाई बंद करके 10 सेकंड बाद पुनः जोड़ें या राउटर को रीस्टार्ट करें।",
                "सुनिश्चित करें कि फोन की तारीख और समय 'स्वचालित (Automatic)' पर सेट हैं, क्योंकि गलत समय से इंटरनेट और सुरक्षा प्रमाणपत्र विफल होते हैं।",
                "वाई-फाई सेटिंग्स में जाकर नेटवर्क को 'Forget' करके दोबारा पासवर्ड से जोड़ें या नीचे दिया डीएनएस परीक्षण चलाएं।",
                "डिवाइस सेटिंग्स मार्ग: `$oemPath`"
            )
            12 -> listOf(
                "ब्लूटूथ बंद करके 10 सेकंड बाद फिर चालू करें और सुनिश्चित करें कि ईयरबड्स दूसरे फोन से जुड़े न हों।",
                "ब्लूटूथ सेटिंग्स में पुराने पेयर्ड डिवाइस को 'Unpair/Forget' करें और नए सिरे से पेयरिंग मोड में जोड़ें।",
                "यदि फिर भी न जुड़े, तो केवल नेटवर्क/ब्लूटूथ सेटिंग्स रीसेट मार्गदर्शन देखें।",
                "डिवाइस सेटिंग्स मार्ग: `$oemPath`"
            )
            2, 4, 11 -> listOf(
                "नीचे दिए सुरक्षित 'रैम व ऐप कैश जाँच' बटन से अस्थायी मेमोरी और अपने ऐप का कैश साफ करें।",
                "जांचें कि आंतरिक स्टोरेज में कम से कम 15% से अधिक जगह खाली है; भरा हुआ स्टोरेज फोन को बहुत धीमा करता है।",
                "जो ऐप बार-बार हैंग या बंद हो रहा है, ऐप मैनेजर में उसका केवल कैश साफ करें (डेटा न हटाएं) और प्ले स्टोर से अपडेट करें।",
                "डिवाइस सेटिंग्स मार्ग: `$oemPath`"
            )
            8 -> listOf(
                "स्क्रीन पर लगा खराब या ढीला टेम्पर्ड ग्लास या नमी हटाकर साफ सूखे कपड़े से स्क्रीन पोंछें।",
                "नीचे दिए 'स्क्रीन पिक्सेल और टच परीक्षण' से देखें कि स्क्रीन के किस हिस्से में टच प्रतिक्रिया रुक रही है।",
                "यदि चार्जिंग के दौरान नकली टच होते हैं, तो तुरंत दूसरा मूल चार्जर उपयोग करके जांचें।",
                "डिवाइस सेटिंग्स मार्ग: `$oemPath`"
            )
            9 -> listOf(
                "स्पीकर जाली में धूल न जमी हो इसकी जांच करें और सुनिश्चित करें कि फोन किसी ब्लूटूथ डिवाइस से जुड़ा न हो।",
                "नीचे दिए 'ऑडियो और स्पीकर जाँच' कार्य से मीडिया वॉल्यूम स्तर व रिंगर स्थिति की पुष्टि करें।",
                "यदि 1000Hz परीक्षण ध्वनि साफ न सुनाई दे तो अधिकृत सेवा केंद्र से स्पीकर हार्डवेयर की जांच कराएं।",
                "डिवाइस सेटिंग्स मार्ग: `$oemPath`"
            )
            10 -> listOf(
                "अधिसूचना सेटिंग्स में जाकर सुनिश्चित करें कि संबंधित ऐप की सूचनाएं और 'Do Not Disturb' प्रतिबंध बंद नहीं हैं।",
                "बैटरी अनुकूलन (Battery Optimization) में महत्वपूर्ण चैट ऐप्स को 'Unrestricted' या अनुमत रखें ताकि अलर्ट समय पर आएं।",
                "डिवाइस सेटिंग्स मार्ग: `$oemPath`"
            )
            else -> listOf(
                "सबसे पहले नीचे दिए गए सुरक्षित स्वचालित निदान परीक्षण चलाकर वास्तविक सेंसर स्थिति जांचें।",
                "डिवाइस सेटिंग्स मार्ग `$oemPath` खोलें और प्रभावित ऐप या सुविधा के विकल्प जांचें।",
                "समस्या बनी रहने पर फोन को एक बार रीस्टार्ट करें और सिस्टम अपडेट जांचें।"
            )
        }

    fun localizeHindiPossibleCauses(issue: KnowledgeIssueRecord): List<String> {
        val catHi = localizeCategoryName(issue.categoryNumber, issue.categoryName, LanguageDetector.LANG_HINDI)
        return when (issue.categoryNumber) {
            5 -> listOf(
                "पृष्ठभूमि में अत्यधिक बैटरी उपयोग करने वाले ऐप्स या लगातार सिंक प्रक्रियाएं",
                "स्क्रीन ब्राइटनेस, अधिक रिफ्रेश रेट या लंबे समय तक सक्रिय नेटवर्क/लोकेशन सेवा",
                "चार्जिंग केबल, एडॉप्टर या उम्रदराज़ बैटरी सेल की क्षमता में गिरावट"
            )
            27 -> listOf(
                "लगातार भारी प्रोसेसर कार्य, गेमिंग, या चार्जिंग के दौरान पृष्ठभूमि डाउनलोड",
                "कमज़ोर मोबाइल नेटवर्क सिग्नल में अधिक ट्रांसमिट पावर लगना",
                "अत्यधिक बाहरी गर्मी या मोटे कवर के कारण ऊष्मा न निकल पाना"
            )
            7, 26 -> listOf(
                "राउटर/डीएनएस अस्थायी त्रुटि या आईपी आवंटन में रुकावट",
                "गलत सिस्टम दिनांक/समय या सिम/नेटवर्क मोड असंगति",
                "पृष्ठभूमि वीपीएन, प्राइवेट डीएनएस या नेटवर्क कॉन्फ़िगरेशन त्रुटि"
            )
            12 -> listOf(
                "पुरानी या दूषित ब्लूटूथ पेयरिंग प्रोफ़ाइल",
                "ऑडियो डिवाइस का किसी अन्य नज़दीकी फोन से पहले से जुड़ा होना",
                "कम बैटरी या ब्लूटूथ कैश/कनेक्शन अस्थायी रुकावट"
            )
            2, 4, 11 -> listOf(
                "रैम पर अत्यधिक पृष्ठभूमि ऐप्स का दबाव या कम खाली स्टोरेज (<15%)",
                "किसी ऐप का पुराना संस्करण या अस्थायी कैश भ्रष्टाचार",
                "उच्च तापमान के कारण प्रोसेसर थ्रॉटलिंग (थर्मल धीमापन)"
            )
            else -> listOf(
                "$catHi से संबंधित अस्थायी सिस्टम या ऐप कॉन्फ़िगरेशन त्रुटि",
                "पृष्ठभूमि संसाधन दबाव या लंबित सिस्टम/ऐप अपडेट",
                "यदि सेफ मोड में भी यही समस्या रहे, तो संभावित हार्डवेयर दोष"
            )
        }
    }

    fun localizeHindiQuestions(issue: KnowledgeIssueRecord): List<String> = when (issue.categoryNumber) {
        5 -> listOf(
            "क्या बैटरी तब भी जल्दी गिरती है जब स्क्रीन बंद हो या केवल किसी खास ऐप के उपयोग पर?",
            "क्या आप फोन के साथ आए मूल चार्जर और केबल का उपयोग कर रहे हैं?"
        )
        27 -> listOf(
            "क्या फोन मुख्य रूप से चार्जिंग, गेमिंग, या 5G मोबाइल डेटा उपयोग करते समय गर्म होता है?",
            "क्या कवर हटाने पर भी फोन के ऊपरी या बैटरी हिस्से में असामान्य गर्मी महसूस होती है?"
        )
        7, 26 -> listOf(
            "क्या यह समस्या केवल एक वाई-फाई नेटवर्क पर है या मोबाइल डेटा पर भी इंटरनेट नहीं चल रहा?",
            "क्या अन्य डिवाइस उसी वाई-फाई नेटवर्क पर इंटरनेट चला पा रहे हैं?"
        )
        12 -> listOf(
            "क्या यह ब्लूटूथ डिवाइस पहले इसी फोन से सही जुड़ता था या नई पेयरिंग में विफल हो रहा है?",
            "क्या किसी दूसरे फोन से ये ईयरबड्स/स्पीकर जुड़ रहे हैं?"
        )
        else -> listOf(
            "क्या यह समस्या किसी नए सिस्टम अपडेट या नया ऐप इंस्टॉल करने के बाद शुरू हुई?",
            "क्या फोन रीस्टार्ट करने के बाद कुछ देर के लिए यह समस्या ठीक होती है?"
        )
    }

    fun localizeHindiHardwareIndicators(issue: KnowledgeIssueRecord): List<String> = when (issue.categoryNumber) {
        5, 27 -> listOf(
            "बैटरी का फूलना, चार्जिंग पोर्ट का अत्यधिक गर्म होना, या 50°C से अधिक तापमान",
            "बिना उपयोग के अचानक फोन बंद होना या केवल चार्जर लगे रहने पर ही चालू रहना"
        )
        8 -> listOf(
            "टूटा हुआ ग्लास, स्क्रीन में स्थायी काली/रंगीन लाइनें, या हर जगह टच बंद होना"
        )
        9 -> listOf(
            "1000Hz ऑडियो परीक्षण में बिल्कुल ध्वनि न आना या फटी हुई आवाज़ आना (ब्लूटूथ बंद होने पर)"
        )
        else -> listOf(
            "फोन को सेफ मोड में चलाने पर भी समस्या लगातार बने रहना या भौतिक क्षति/नमी के संकेत"
        )
    }

    fun localizeAction(action: DoctorAction, language: String): LocalizedActionText {
        val riskLabel = localizeRiskLabel(action.riskClass, language)
        val badgeLabel = localizeCapabilityBadge(action.capability, language)

        if (language == LanguageDetector.LANG_ENGLISH) {
            return LocalizedActionText(
                title = action.title,
                description = action.description,
                whyRequired = action.whyRequired,
                whatWillChange = action.whatWillChange,
                capabilityBadge = badgeLabel,
                riskLabel = riskLabel
            )
        }

        if (language == LanguageDetector.LANG_HINGLISH) {
            val (t, d) = when (action.id) {
                "auto_clear_own_cache" -> "🧹 App Diagnostic Cache Clear Karein" to "Is app ka temporary cache safely delete karke exact bytes batata hai"
                "auto_refresh_network_socket" -> "🌐 DNS & Socket Ping Check Karein" to "Network link aur DNS latency (8.8.8.8) live test karta hai"
                "auto_optimize_memory" -> "🧠 RAM & App Buffers Check Karein" to "Live system RAM inspect karke app buffers release karta hai"
                "auto_fix_audio_output" -> "🔊 Audio Routing & Volume Fix Karein" to "Stuck audio mode reset karke muted media volume raise karta hai aur tone test chalata hai"
                "auto_audit_audio" -> "🔊 Speaker & Audio Volume Check Karein" to "Media volume, mute aur Bluetooth routing check karke tone bajata hai"
                "auto_audit_thermal_power" -> "🌡️ Thermal & Battery Power Audit" to "Live temperature, thermal throttling aur Battery Saver status check karta hai"
                "auto_optimize_display_power" -> "🔆 Screen Timeout & Auto-Brightness Fix Karein" to "WRITE_SETTINGS permission hone par 30s sleep aur Adaptive Brightness lagata hai"
                "auto_screen_timeout_safe" -> "⏱️ Screen Timeout 30s Optimize Karein" to "WRITE_SETTINGS permission hone par screen sleep time 30s set karta hai"
                "test_audio" -> "🔊 Speaker Audio Test Chalayein" to "587Hz audio tone play karke speaker check karta hai"
                "test_vibration" -> "📳 Vibration Motor Test Chalayein" to "Phone ka vibration pulse test karta hai"
                "test_screen" -> "🎨 Screen Pixel & Touch Test" to "RGB color screen se dead pixel aur touch check karta hai"
                "test_network" -> "🌐 Network Ping Latency Test" to "Live internet response time ms mein measure karta hai"
                "repair_battery_saver" -> "⚡ Battery Settings Kholein" to "Battery usage aur Battery Saver kholne ke liye Android Settings open karta hai"
                "repair_storage_cleaner" -> "💾 Storage Manager Kholein" to "Unused files aur storage clean karne ke liye System Storage open karta hai"
                "repair_app_cache" -> "📱 App Manager Kholein (Cache Clear)" to "Slow ya crash ho rahe apps ka cache clear karne ke liye App Info open karta hai"
                "repair_network_reset" -> "📶 Wi-Fi Settings Kholein" to "Wi-Fi reconnect ya Private DNS check karne ke liye Settings open karta hai"
                "repair_mobile_data" -> "📡 Mobile Data & SIM Settings" to "5G/4G mode aur APN check karne ke liye Network Settings open karta hai"
                "repair_bluetooth" -> "🎧 Bluetooth Settings Kholein" to "Bluetooth devices unpair/re-pair karne ke liye Settings open karta hai"
                "repair_display_timeout" -> "☀️ Display Settings Kholein" to "Brightness, refresh rate aur screen timeout set karne ke liye Display kholta hai"
                "repair_sound_levels" -> "🔊 Sound & Vibration Settings" to "Volume, DND aur audio settings check karne ke liye kholta hai"
                "repair_notifications" -> "🔔 Notification Settings Kholein" to "App notifications aur DND exceptions check karne ke liye kholta hai"
                "repair_system_update" -> "🔄 System Update Check Karein" to "Official Android/OEM firmware update screen kholta hai"
                "repair_settings_search" -> "🔍 Android Settings Kholein" to "Exact setting dhoondhne ke liye Android Settings open karta hai"
                else -> action.title to action.description
            }
            return LocalizedActionText(
                title = t,
                description = d,
                whyRequired = action.whyRequired,
                whatWillChange = action.whatWillChange,
                capabilityBadge = badgeLabel,
                riskLabel = riskLabel
            )
        }

        // Pure Devanagari Hindi
        val (tHi, dHi, whyHi, changeHi) = when (action.id) {
            "auto_clear_own_cache" -> Quad(
                "🧹 ऐप डायग्नोस्टिक कैश साफ़ करें",
                "इस ऐप का अस्थायी कैश सुरक्षित रूप से हटाता है और साफ़ की गई बाइट्स दिखाता है",
                "अन्य ऐप्स या व्यक्तिगत डेटा को छुए बिना इस ऐप के अस्थायी बफ़र खाली करता है",
                "केवल ऐप के अंदर के कैश फ़ोल्डर साफ़ होंगे"
            )
            "auto_refresh_network_socket" -> Quad(
                "🌐 नेटवर्क डीएनएस और पिंग जाँच",
                "इंटरनेट लिंक, डीएनएस और सर्वर प्रतिक्रिया समय (ms) की लाइव जाँच करता है",
                "नेटवर्क धीमा होने या वेबसाइट न खुलने का सटीक कारण जांचने के लिए",
                "सुरक्षित डीएनएस/टीसीपी कनेक्शन परीक्षण चलाता है"
            )
            "auto_optimize_memory" -> Quad(
                "🧠 रैम मेमोरी जाँच और बफ़र मुक्त करें",
                "सिस्टम मेमोरी दबाव मापता है और अनावश्यक इन-ऐप बफ़र खाली करता है",
                "एंड्रॉइड सुरक्षा नीति के कारण अन्य ऐप्स को ज़बरदस्ती बंद किए बिना असली खाली रैम जांचता है",
                "ऐप की अनावश्यक मेमोरी मुक्त करके लाइव रैम स्थिति मापता है"
            )
            "auto_fix_audio_output" -> Quad(
                "🔊 ऑडियो रूटिंग व वॉल्यूम स्वतः सुधारें",
                "अटकी ऑडियो रूटिंग को सामान्य करता है, म्यूट मीडिया वॉल्यूम बढ़ाता है और परीक्षण ध्वनि बजाता है",
                "कई बार 'आवाज़ न आने' की समस्या केवल म्यूट वॉल्यूम या गलत कॉल रूटिंग से होती है",
                "AudioManager मोड NORMAL करता है, मीडिया वॉल्यूम 45% तक बढ़ाता है और 587Hz ध्वनि जांचता है"
            )
            "auto_audit_audio" -> Quad(
                "🔊 ऑडियो वॉल्यूम और स्पीकर जाँच",
                "मीडिया वॉल्यूम, साइलेंट मोड व ब्लूटूथ रूटिंग जांचकर परीक्षण ध्वनि चलाता है",
                "हार्डवेयर खराबी मानने से पहले म्यूट, डीएनडी या ब्लूटूथ कनेक्शन जांचने के लिए",
                "ऑडियो सेटिंग पढ़ता है और 587Hz परीक्षण ध्वनि बजाता है"
            )
            "auto_audit_thermal_power" -> Quad(
                "🌡️ थर्मल और बैटरी पावर जाँच",
                "बैटरी तापमान, थर्मल थ्रॉटलिंग और बैटरी सेवर स्थिति की वास्तविक रीडिंग लेता है",
                "यह जांचने के लिए कि फोन गर्मी के कारण धीमा हो रहा है या नहीं",
                "केवल-पठन (Read-only) सिस्टम सेंसर जांच करता है"
            )
            "auto_optimize_display_power" -> Quad(
                "🔆 स्क्रीन टाइमआउट (30s) व ऑटो-ब्राइटनेस",
                "अनुमति होने पर 30 सेकंड स्क्रीन समय व स्वचालित ब्राइटनेस स्वतः लागू करता है",
                "लंबे स्क्रीन समय और स्थिर तेज चमक से बैटरी खपत व गर्मी बढ़ती है",
                "SCREEN_OFF_TIMEOUT = 30s और SCREEN_BRIGHTNESS_MODE = AUTOMATIC लागू करके सत्यापन करता है"
            )
            "auto_screen_timeout_safe" -> Quad(
                "⏱️ स्क्रीन समय समाप्ति 30 सेकंड सेट करें",
                "अनुमति होने पर स्क्रीन स्लीप समय को स्वतः 30 सेकंड पर अनुकूलित करता है",
                "लंबे स्क्रीन समय से बैटरी खपत और गर्मी बढ़ती है",
                "आपकी अनुमति के बाद स्क्रीन टाइमआउट 30 सेकंड करता है"
            )
            "test_audio" -> Quad(
                "🔊 स्पीकर ध्वनि परीक्षण चलाएं",
                "स्पीकर की स्पष्टता जांचने के लिए 587.33 Hz परीक्षण ध्वनि बजाता है",
                "स्पीकर हार्डवेयर आउटपुट की पुष्टि के लिए",
                "थोड़ी देर के लिए परीक्षण ध्वनि बजाएगा"
            )
            "test_vibration" -> Quad(
                "📳 वाइब्रेशन मोटर परीक्षण चलाएं",
                "फोन की वाइब्रेशन मोटर की जांच के लिए पल्स सक्रिय करता है",
                "हैप्टिक मोटर सही काम कर रही है या नहीं यह देखने के लिए",
                "फोन संक्षिप्त रूप से वाइब्रेट होगा"
            )
            "test_screen" -> Quad(
                "🎨 स्क्रीन पिक्सेल और टच परीक्षण",
                "मृत पिक्सेल और रंग दोष देखने के लिए पूर्ण-स्क्रीन रंग परीक्षण दिखाता है",
                "डिस्प्ले पर दाग, बर्न-इन या टच रुकने की जांच के लिए",
                "पूर्ण स्क्रीन पर परीक्षण रंग दिखाएगा"
            )
            "test_network" -> Quad(
                "🌐 नेटवर्क पिंग विलंबता परीक्षण",
                "8.8.8.8 सर्वर पर लाइव इंटरनेट प्रतिक्रिया समय (ms) मापता है",
                "इंटरनेट गति व कनेक्शन स्थिरता की पुष्टि के लिए",
                "सुरक्षित सॉकेट पिंग परीक्षण करता है"
            )
            "repair_battery_saver" -> Quad(
                "⚡ बैटरी और पावर सेटिंग्स खोलें",
                "बैटरी उपयोग देखने और बैटरी सेवर चालू करने के लिए सिस्टम सेटिंग्स खोलता है",
                "एंड्रॉइड सिस्टम सेटिंग्स में उपयोगकर्ता की सहमति से बैटरी सेवर बदला जाता है",
                "फोन की आधिकारिक बैटरी सेटिंग्स स्क्रीन खोलेगा"
            )
            "repair_storage_cleaner" -> Quad(
                "💾 स्टोरेज प्रबंधक खोलें",
                "अनावश्यक फ़ाइलें और भरी जगह देखने के लिए सिस्टम स्टोरेज खोलता है",
                "कम खाली स्थान (<15%) फोन हैंग और अपडेट विफलता का बड़ा कारण है",
                "एंड्रॉइड स्टोरेज प्रबंधन स्क्रीन खोलेगा"
            )
            "repair_app_cache" -> Quad(
                "📱 ऐप प्रबंधक खोलें (केवल कैश साफ़ करें)",
                "हैंग या क्रैश हो रहे ऐप का केवल कैश साफ़ करने के लिए ऐप सेटिंग्स खोलता है",
                "एंड्रॉइड सैंडबॉक्स के कारण किसी अन्य ऐप का कैश केवल सेटिंग्स से ही साफ़ हो सकता है",
                "ऐप सूची खोलेगा (चैट और लॉगिन सुरक्षित रहेंगे)"
            )
            "repair_network_reset" -> Quad(
                "📶 वाई-फाई और नेटवर्क सेटिंग्स खोलें",
                "वाई-फाई दोबारा जोड़ने या प्राइवेट डीएनएस जांचने के लिए सेटिंग्स खोलता है",
                "एंड्रॉइड 10+ में वाई-फाई टॉगल व नेटवर्क सुधार उपयोगकर्ता अनुमति से होते हैं",
                "सिस्टम वाई-फाई सेटिंग्स खोलेगा"
            )
            "repair_mobile_data" -> Quad(
                "📡 मोबाइल डेटा और सिम सेटिंग्स खोलें",
                "5G/4G नेटवर्क मोड, एपीएन और रोमिंग जांचने के लिए सेटिंग्स खोलता है",
                "सिम और कैरियर विकल्प सुरक्षित सिस्टम सेटिंग्स के अंतर्गत आते हैं",
                "मोबाइल नेटवर्क सेटिंग्स स्क्रीन खोलेगा"
            )
            "repair_bluetooth" -> Quad(
                "🎧 ब्लूटूथ सेटिंग्स खोलें",
                "खराब पेयरिंग हटाकर ब्लूटूथ डिवाइस पुनः जोड़ने के लिए सेटिंग्स खोलता है",
                "दूषित ब्लूटूथ प्रोफ़ाइल ठीक करने के लिए",
                "ब्लूटूथ डिवाइस सूची खोलेगा"
            )
            "repair_display_timeout" -> Quad(
                "☀️ डिस्प्ले और रिफ्रेश रेट सेटिंग्स खोलें",
                "ब्राइटनेस, रिफ्रेश रेट और स्क्रीन टाइमआउट बदलने के लिए डिस्प्ले सेटिंग्स खोलता है",
                "स्क्रीन की बैटरी खपत और गर्मी कम करने के लिए",
                "एंड्रॉइड डिस्प्ले सेटिंग्स खोलेगा"
            )
            "repair_sound_levels" -> Quad(
                "🔊 ध्वनि और वाइब्रेशन सेटिंग्स खोलें",
                "मीडिया, कॉल वॉल्यूम और डीएनडी मोड जांचने के लिए सेटिंग्स खोलता है",
                "सिस्टम वॉल्यूम और डीएनडी नियंत्रण सिस्टम सेटिंग्स में होते हैं",
                "एंड्रॉइड ध्वनि सेटिंग्स खोलेगा"
            )
            "repair_notifications" -> Quad(
                "🔔 ऐप सूचना सेटिंग्स खोलें",
                "म्यूट अधिसूचना चैनल और अनुमतियाँ जांचने के लिए सेटिंग्स खोलता है",
                "ऐप्स के रुके हुए अलर्ट दोबारा चालू करने के लिए",
                "सिस्टम सूचना प्राथमिकताएं खोलेगा"
            )
            "repair_accessibility" -> Quad(
                "♿ एक्सेसिबिलिटी और टच सेटिंग्स खोलें",
                "टॉकबैक या टच देरी विकल्प जांचने के लिए सेटिंग्स खोलता है",
                "एक्सेसिबिलिटी सेवाओं पर केवल उपयोगकर्ता नियंत्रण संभव है",
                "एंड्रॉइड एक्सेसिबिलिटी सेटिंग्स खोलेगा"
            )
            "repair_system_update" -> Quad(
                "🔄 आधिकारिक सिस्टम अपडेट जांचें",
                "निर्माता के नवीनतम एंड्रॉइड व सुरक्षा पैच की जांच के लिए अपडेट स्क्रीन खोलता है",
                "ओएस अपडेट थर्मल, मॉडेम और मेमोरी बग ठीक करते हैं",
                "आधिकारिक सिस्टम अपडेट पृष्ठ खोलेगा"
            )
            "repair_settings_search" -> Quad(
                "🔍 एंड्रॉइड सेटिंग्स (खोज) खोलें",
                "अपने डिवाइस के मॉडल के अनुसार सही विकल्प खोजने के लिए सेटिंग्स खोलता है",
                "अलग-अलग फोन मॉडल के मेनू नाम भिन्न होने पर सुरक्षित तरीका",
                "मुख्य एंड्रॉइड सेटिंग्स खोलेगा"
            )
            else -> Quad(action.title, action.description, action.whyRequired, action.whatWillChange)
        }
        return LocalizedActionText(
            title = tHi,
            description = dHi,
            whyRequired = whyHi,
            whatWillChange = changeHi,
            capabilityBadge = badgeLabel,
            riskLabel = riskLabel
        )
    }

    fun localizeCapabilityBadge(capability: AutomationCapability, language: String): String {
        if (language == LanguageDetector.LANG_ENGLISH) return capability.badgeLabel
        if (language == LanguageDetector.LANG_HINGLISH) {
            return when (capability) {
                AutomationCapability.NORMAL_API -> "AUTO IN-APP"
                AutomationCapability.SPECIAL_PERMISSION -> "PERMISSION CHAHIYE"
                AutomationCapability.MANUAL_SETTINGS -> "SYSTEM SETTINGS"
                AutomationCapability.ACCESSIBILITY_SERVICE -> "MANUAL ACCESSIBILITY"
                AutomationCapability.DEVICE_OWNER -> "DEVICE OWNER"
                AutomationCapability.OEM_RESTRICTED -> "OEM SETTINGS"
            }
        }
        return when (capability) {
            AutomationCapability.NORMAL_API -> "स्वतः कार्य"
            AutomationCapability.SPECIAL_PERMISSION -> "अनुमति आवश्यक"
            AutomationCapability.MANUAL_SETTINGS -> "सिस्टम सेटिंग्स"
            AutomationCapability.ACCESSIBILITY_SERVICE -> "एक्सेसिबिलिटी अनुमति"
            AutomationCapability.DEVICE_OWNER -> "डिवाइस प्रबंधन"
            AutomationCapability.OEM_RESTRICTED -> "डिवाइस सेटिंग्स"
        }
    }

    fun localizeRiskLabel(riskClass: RiskClassification, language: String): String {
        if (language == LanguageDetector.LANG_ENGLISH) return riskClass.label.uppercase(Locale.US)
        if (language == LanguageDetector.LANG_HINGLISH) {
            return when (riskClass) {
                RiskClassification.READ_ONLY -> "READ-ONLY"
                RiskClassification.SAFE_APP_LEVEL -> "SAFE AUTO"
                RiskClassification.SYSTEM_SETTINGS_INTENT -> "USER SETTINGS"
                RiskClassification.SPECIAL_PERMISSION_WRITE -> "SPECIAL PERMISSION"
                RiskClassification.DESTRUCTIVE_GUARDED -> "HIGH-RISK GUARDED"
            }
        }
        return when (riskClass) {
            RiskClassification.READ_ONLY -> "केवल जाँच"
            RiskClassification.SAFE_APP_LEVEL -> "सुरक्षित स्वतः"
            RiskClassification.SYSTEM_SETTINGS_INTENT -> "उपयोगकर्ता सेटिंग्स"
            RiskClassification.SPECIAL_PERMISSION_WRITE -> "विशेष अनुमति"
            RiskClassification.DESTRUCTIVE_GUARDED -> "उच्च जोखिम सुरक्षित"
        }
    }

    fun localizeOutcomeLabel(outcome: VerificationOutcome, language: String): String {
        if (language == LanguageDetector.LANG_ENGLISH) return outcome.label.uppercase(Locale.US)
        if (language == LanguageDetector.LANG_HINGLISH) {
            return when (outcome) {
                VerificationOutcome.VERIFIED_IMPROVED -> "VERIFIED IMPROVED"
                VerificationOutcome.NEEDS_USER_CONFIRMATION -> "CONFIRMATION ZAROORI"
                VerificationOutcome.NO_MEASURABLE_CHANGE -> "KOI BADLAV NAHI"
                VerificationOutcome.EXECUTION_FAILED -> "FAILED"
            }
        }
        return when (outcome) {
            VerificationOutcome.VERIFIED_IMPROVED -> "सुधार सत्यापित"
            VerificationOutcome.NEEDS_USER_CONFIRMATION -> "उपयोगकर्ता पुष्टि आवश्यक"
            VerificationOutcome.NO_MEASURABLE_CHANGE -> "कोई बदलाव नहीं दिखा"
            VerificationOutcome.EXECUTION_FAILED -> "निष्पादन विफल"
        }
    }

    private fun translateBatteryStatusHi(status: String): String = when (status.lowercase()) {
        "charging" -> "चार्ज हो रही है"
        "discharging" -> "बैटरी पर चल रहा है"
        "full" -> "पूर्ण चार्ज"
        "not charging" -> "चार्ज नहीं हो रहा"
        "unavailable" -> "उपलब्ध नहीं"
        else -> status
    }

    private fun translateBatteryHealthHi(health: String): String = when (health.lowercase()) {
        "good" -> "अच्छा"
        "overheating" -> "अधिक गर्म"
        "dead" -> "खराब"
        "over voltage" -> "उच्च वोल्टेज"
        "cold" -> "कम तापमान"
        "unavailable" -> "उपलब्ध नहीं"
        else -> health
    }

    private fun translateThermalHi(thermal: String): String = when {
        thermal.contains("Nominal", true) -> "सामान्य"
        thermal.contains("Light", true) -> "हल्की गर्माहट"
        thermal.contains("Moderate", true) -> "मध्यम थ्रॉटलिंग"
        thermal.contains("Severe", true) -> "गंभीर थर्मल थ्रॉटलिंग"
        thermal.contains("Critical", true) -> "अत्यधिक ओवरहीट"
        thermal.contains("Emergency", true) || thermal.contains("Shutdown", true) -> "आपातकालीन तापमान चेतावनी"
        thermal.contains("Unavailable", true) -> "सेंसर उपलब्ध नहीं"
        else -> thermal
    }

    private fun translateNetworkHi(net: String): String = when (net.lowercase()) {
        "wi-fi" -> "वाई-फाई"
        "mobile data" -> "मोबाइल डेटा"
        "ethernet" -> "ईथरनेट"
        "disconnected" -> "डिस्कनेक्टेड"
        else -> net
    }

    private fun translateMetricNameHi(metric: String): String = when {
        metric.contains("Battery", true) -> "बैटरी स्तर"
        metric.contains("Temperature", true) -> "बैटरी तापमान सेंसर"
        metric.contains("Thermal", true) -> "थर्मल स्टेटस"
        metric.contains("RAM", true) -> "रैम उपयोग"
        metric.contains("Storage", true) -> "आंतरिक स्टोरेज"
        metric.contains("Display", true) -> "डिस्प्ले रिज़ॉल्यूशन/रिफ्रेश रेट"
        metric.contains("Audio", true) -> "ऑडियो वॉल्यूम स्तर"
        metric.contains("Screen Timeout", true) -> "स्क्रीन टाइमआउट"
        else -> metric
    }

    private data class Quad(val first: String, val second: String, val third: String, val fourth: String)
}
