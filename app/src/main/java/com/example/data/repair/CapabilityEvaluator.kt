package com.example.data.repair

import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import com.example.data.model.ActionType
import com.example.data.model.AutomationCapability
import com.example.data.model.DoctorAction

data class ActionEligibility(
    val actionId: String,
    val isRegistered: Boolean,
    val isSupportedOnDevice: Boolean,
    val capability: AutomationCapability,
    val permissionGranted: Boolean,
    val requiresUserConfirmation: Boolean,
    val restrictionSummary: String
)

/**
 * Evaluates whether a repair action can run via normal Android API, requires a special permission,
 * requires user action in System Settings, or is restricted by OS/OEM/Device Owner policy.
 */
object CapabilityEvaluator {

    fun evaluate(
        context: Context,
        action: DoctorAction,
        apiLevel: Int = Build.VERSION.SDK_INT,
        canWriteOverride: Boolean? = null
    ): ActionEligibility {
        val registered = SafeRepairRegistry.isRegistered(action.id)
        if (!registered) {
            return ActionEligibility(
                actionId = action.id,
                isRegistered = false,
                isSupportedOnDevice = false,
                capability = AutomationCapability.OEM_RESTRICTED,
                permissionGranted = false,
                requiresUserConfirmation = true,
                restrictionSummary = "Action rejected: not in SafeRepairRegistry."
            )
        }

        if (apiLevel < action.minSdk) {
            return ActionEligibility(
                actionId = action.id,
                isRegistered = true,
                isSupportedOnDevice = false,
                capability = AutomationCapability.OEM_RESTRICTED,
                permissionGranted = false,
                requiresUserConfirmation = true,
                restrictionSummary = "Requires Android API ${action.minSdk}+ (current API $apiLevel)."
            )
        }

        return when (action.actionType) {
            ActionType.OPTIMIZE_SCREEN_TIMEOUT_SAFE,
            ActionType.OPTIMIZE_DISPLAY_POWER_SAFE -> {
                val canWrite = canWriteOverride ?: runCatching { Settings.System.canWrite(context) }.getOrDefault(false)
                ActionEligibility(
                    actionId = action.id,
                    isRegistered = true,
                    isSupportedOnDevice = true,
                    capability = AutomationCapability.SPECIAL_PERMISSION,
                    permissionGranted = canWrite,
                    requiresUserConfirmation = true,
                    restrictionSummary = if (canWrite) {
                        "WRITE_SETTINGS special permission granted: can safely optimize sleep timeout & adaptive brightness."
                    } else {
                        "Special permission 'Modify system settings' not granted yet. Will request via System Settings or open Display Settings."
                    }
                )
            }
            ActionType.CLEAR_APP_OWN_CACHE,
            ActionType.REFRESH_NETWORK_SOCKET,
            ActionType.OPTIMIZE_APP_MEMORY_AND_STATE,
            ActionType.FIX_AUDIO_ROUTING_AND_VOLUME,
            ActionType.AUDIT_AUDIO_ROUTING_AND_VOLUME,
            ActionType.AUDIT_THERMAL_AND_POWER_STATE,
            ActionType.RUN_AUDIO_TEST,
            ActionType.RUN_VIBRATION_TEST,
            ActionType.RUN_SCREEN_TEST,
            ActionType.RUN_NETWORK_TEST -> ActionEligibility(
                actionId = action.id,
                isRegistered = true,
                isSupportedOnDevice = true,
                capability = AutomationCapability.NORMAL_API,
                permissionGranted = true,
                requiresUserConfirmation = false,
                restrictionSummary = "Supported directly via documented Android APIs."
            )
            ActionType.GUARDED_CLEAR_APP_DATA_GUIDE,
            ActionType.GUARDED_UNINSTALL_UNUSED_GUIDE,
            ActionType.GUARDED_RESET_NETWORK_GUIDE -> ActionEligibility(
                actionId = action.id,
                isRegistered = true,
                isSupportedOnDevice = true,
                capability = AutomationCapability.MANUAL_SETTINGS,
                permissionGranted = false,
                requiresUserConfirmation = true,
                restrictionSummary = "High-risk destructive category: NEVER auto-executed. Requires backup warning and explicit user confirmation."
            )
            else -> {
                val intentResolvable = isSettingsIntentResolvable(context, action.actionType)
                ActionEligibility(
                    actionId = action.id,
                    isRegistered = true,
                    isSupportedOnDevice = true,
                    capability = action.capability,
                    permissionGranted = true,
                    requiresUserConfirmation = true,
                    restrictionSummary = if (intentResolvable) {
                        action.capability.explanation
                    } else {
                        "Direct OEM screen unavailable on this firmware; will fall back safely to Android Settings."
                    }
                )
            }
        }
    }

    private fun isSettingsIntentResolvable(context: Context, type: ActionType): Boolean {
        val intent = SafeRepairActions.buildSettingsIntent(context, type) ?: return true
        return runCatching { intent.resolveActivity(context.packageManager) != null }.getOrDefault(false)
    }
}
