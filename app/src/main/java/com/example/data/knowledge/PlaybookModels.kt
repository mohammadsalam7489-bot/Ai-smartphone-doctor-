package com.example.data.knowledge

import com.example.data.model.DeviceVitals
import com.example.data.model.OemCompatibilityProfile

/**
 * Structured Knowledge Record extracted from the AI Smartphone Doctor Blueprint
 * and verified Android / OEM troubleshooting procedures.
 */
data class DiagnosticPlaybook(
    val id: String,
    val problemTitle: String,
    val category: String,
    val problemIdentification: String,
    val symptomsAndKeywords: List<String>,
    val possibleCauses: List<String>,
    val diagnosticQuestions: List<String>,
    val requiredDeviceInfo: List<String>,
    val androidVersionCompatibility: String,
    val minSdk: Int = 24,
    val oemSettingsPathResolver: (OemCompatibilityProfile) -> String,
    val stepByStepSolution: List<String>,
    val automaticActionIds: List<String>,
    val requiredPermissions: List<String>,
    val androidApiRestrictions: String,
    val alternativeSolutions: List<String>,
    val verificationMethod: String,
    val hardwareFaultIndicators: List<String>,
    val escalationInstructions: String,
    val vitalsBoostTrigger: (DeviceVitals) -> Int = { 0 }
)

data class RagMatchResult(
    val playbook: DiagnosticPlaybook,
    val score: Int,
    val matchReasons: List<String>
)
