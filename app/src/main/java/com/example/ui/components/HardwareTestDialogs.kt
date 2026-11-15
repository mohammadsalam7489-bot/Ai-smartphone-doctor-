package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.diagnostics.PingResult
import com.example.ui.theme.CyberAmber
import com.example.ui.theme.CyberBackground
import com.example.ui.theme.CyberCard
import com.example.ui.theme.CyberCardBorder
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.CyberGreen
import com.example.ui.theme.CyberRed
import com.example.ui.theme.CyberSurface
import com.example.ui.theme.CyberSurfaceVariant
import com.example.ui.theme.CyberTextMuted
import com.example.ui.theme.CyberTextPrimary
import com.example.ui.theme.CyberTextSecondary

@Composable
fun AudioTestDialog(
    isHindi: Boolean = false,
    onDismiss: () -> Unit,
    onPlayAgain: () -> Unit,
    onFeedback: (Boolean) -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "audio_anim")
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "audio_pulse"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CyberCard,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                    contentDescription = if (isHindi) "स्पीकर ऑडियो परीक्षण" else "Audio Test",
                    tint = CyberCyan,
                    modifier = Modifier.size(26.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = if (isHindi) "स्पीकर ऑडियो निदान" else "SPEAKER AUDIO DIAGNOSTIC",
                    color = CyberTextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
        },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(86.dp)
                        .scale(pulse)
                        .background(CyberCyan.copy(alpha = 0.15f), CircleShape)
                        .border(1.5.dp, CyberCyan, CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = null,
                        tint = CyberCyan,
                        modifier = Modifier.size(44.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = if (isHindi) {
                        "डिवाइस स्पीकर के माध्यम से कैलिब्रेटेड ऑडियो ध्वनि (587.33 Hz) भेजी गई है।"
                    } else {
                        "Calibrated diagnostic audio tone (587.33 Hz) was transmitted through device speaker."
                    },
                    textAlign = TextAlign.Center,
                    color = CyberTextSecondary,
                    fontSize = 12.sp,
                    lineHeight = 17.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (isHindi) "क्या आपको ध्वनि साफ सुनाई दी?" else "Did you clearly hear the sound?",
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Bold,
                    color = CyberTextPrimary,
                    fontSize = 13.sp
                )

                Spacer(modifier = Modifier.height(14.dp))
                OutlinedButton(
                    onClick = onPlayAgain,
                    border = androidx.compose.foundation.BorderStroke(1.dp, CyberCyan.copy(alpha = 0.6f)),
                    modifier = Modifier.testTag("play_tone_again_button")
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, tint = CyberCyan, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (isHindi) "पुनः बजाएं" else "Replay Tone", color = CyberCyan, fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onFeedback(true) },
                colors = ButtonDefaults.buttonColors(containerColor = CyberGreen, contentColor = Color(0xFF00220F)),
                modifier = Modifier.testTag("sound_pass_button")
            ) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(if (isHindi) "साफ सुनाई दी" else "Clear & Audible", fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = { onFeedback(false) },
                border = androidx.compose.foundation.BorderStroke(1.dp, CyberRed.copy(alpha = 0.6f)),
                modifier = Modifier.testTag("sound_fail_button")
            ) {
                Text(if (isHindi) "आवाज़ खराब / नहीं" else "Distorted / No Sound", color = CyberRed, fontSize = 12.sp)
            }
        }
    )
}

@Composable
fun VibrationTestDialog(
    isHindi: Boolean = false,
    onDismiss: () -> Unit,
    onTriggerAgain: () -> Unit,
    onFeedback: (Boolean) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CyberCard,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Vibration,
                    contentDescription = if (isHindi) "वाइब्रेशन परीक्षण" else "Vibration Test",
                    tint = CyberCyan,
                    modifier = Modifier.size(26.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = if (isHindi) "हैप्टिक मोटर निदान" else "HAPTIC MOTOR DIAGNOSTIC",
                    color = CyberTextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
        },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(86.dp)
                        .background(CyberCyan.copy(alpha = 0.15f), CircleShape)
                        .border(1.5.dp, CyberCyan, CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Vibration,
                        contentDescription = null,
                        tint = CyberCyan,
                        modifier = Modifier.size(44.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = if (isHindi) {
                        "डिवाइस हैप्टिक मोटर को मल्टी-फ़्रीक्वेंसी वाइब्रेशन संकेत भेजा गया है।"
                    } else {
                        "Multi-frequency vibration pulse pattern was transmitted to the device haptic actuator."
                    },
                    textAlign = TextAlign.Center,
                    color = CyberTextSecondary,
                    fontSize = 12.sp,
                    lineHeight = 17.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (isHindi) "क्या आपका फोन वाइब्रेट हुआ?" else "Did your phone vibrate?",
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Bold,
                    color = CyberTextPrimary,
                    fontSize = 13.sp
                )

                Spacer(modifier = Modifier.height(14.dp))
                OutlinedButton(
                    onClick = onTriggerAgain,
                    border = androidx.compose.foundation.BorderStroke(1.dp, CyberCyan.copy(alpha = 0.6f)),
                    modifier = Modifier.testTag("vibrate_again_button")
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, tint = CyberCyan, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (isHindi) "पुनः कंपन करें" else "Trigger Pulse Again", color = CyberCyan, fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onFeedback(true) },
                colors = ButtonDefaults.buttonColors(containerColor = CyberGreen, contentColor = Color(0xFF00220F)),
                modifier = Modifier.testTag("vibration_pass_button")
            ) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(if (isHindi) "हाँ, वाइब्रेट हुआ" else "Yes, Vibrated", fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = { onFeedback(false) },
                border = androidx.compose.foundation.BorderStroke(1.dp, CyberRed.copy(alpha = 0.6f)),
                modifier = Modifier.testTag("vibration_fail_button")
            ) {
                Text(if (isHindi) "कंपन नहीं हुआ" else "No Vibration", color = CyberRed, fontSize = 12.sp)
            }
        }
    )
}

@Composable
fun ScreenTestDialog(
    isHindi: Boolean = false,
    onDismiss: () -> Unit,
    onFinish: (Boolean) -> Unit
) {
    val colors = listOf(
        Color.Red,
        Color.Green,
        Color.Blue,
        Color.White,
        Color.Black
    )
    val colorNames = if (isHindi) {
        listOf("शुद्ध लाल", "शुद्ध हरा", "शुद्ध नीला", "शुद्ध सफ़ेद", "शुद्ध काला")
    } else {
        listOf("Pure Red", "Pure Green", "Pure Blue", "Pure White", "Pure Black")
    }

    var currentIndex by remember { mutableIntStateOf(0) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(colors[currentIndex])
                .clickable {
                    if (currentIndex < colors.lastIndex) {
                        currentIndex++
                    } else {
                        onFinish(true)
                    }
                }
                .testTag("screen_test_canvas"),
            contentAlignment = Alignment.BottomCenter
        ) {
            Surface(
                color = CyberBackground.copy(alpha = 0.88f),
                shape = RoundedCornerShape(20.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, CyberCyan.copy(alpha = 0.6f)),
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = if (isHindi) {
                            "पिक्सेल मैट्रिक्स जाँच (${currentIndex + 1}/${colors.size}): ${colorNames[currentIndex]}"
                        } else {
                            "SUB-PIXEL MATRIX TEST (${currentIndex + 1}/${colors.size}): ${colorNames[currentIndex].uppercase()}"
                        },
                        color = CyberCyan,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (isHindi) {
                            "डेड पिक्सेल, रंग असमानता या बर्न-इन धब्बों की जाँच करें।\nअगला रंग देखने के लिए स्क्रीन पर कहीं भी टैप करें।"
                        } else {
                            "Inspect for dead pixels, tint discoloration, or burn-in artifacts.\nTap screen anywhere to advance color."
                        },
                        color = CyberTextSecondary,
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        OutlinedButton(
                            onClick = onDismiss,
                            border = androidx.compose.foundation.BorderStroke(1.dp, CyberCardBorder)
                        ) {
                            Text(if (isHindi) "परीक्षण बंद करें" else "Exit Test", color = CyberTextPrimary, fontSize = 12.sp)
                        }
                        Button(
                            onClick = {
                                if (currentIndex < colors.lastIndex) {
                                    currentIndex++
                                } else {
                                    onFinish(true)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = CyberCyan, contentColor = Color(0xFF001B2E))
                        ) {
                            Text(
                                text = if (currentIndex < colors.lastIndex) {
                                    if (isHindi) "अगला रंग >>" else "Next Matrix >>"
                                } else {
                                    if (isHindi) "पूर्ण करें" else "Finish Test"
                                },
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun NetworkPingDialog(
    pingResult: PingResult?,
    isLoading: Boolean,
    isHindi: Boolean = false,
    onDismiss: () -> Unit,
    onRetest: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CyberCard,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.NetworkCheck,
                    contentDescription = if (isHindi) "नेटवर्क पिंग परीक्षण" else "Ping Test",
                    tint = CyberCyan,
                    modifier = Modifier.size(26.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = if (isHindi) "नेटवर्क गति निदान" else "NETWORK LATENCY DIAGNOSTIC",
                    color = CyberTextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
        },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(44.dp),
                        color = CyberCyan
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = if (isHindi) {
                            "8.8.8.8 तक टीसीपी विलंबता (पिंग) मापी जा रही है..."
                        } else {
                            "Measuring TCP round-trip latency to 8.8.8.8..."
                        },
                        color = CyberTextSecondary,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        textAlign = TextAlign.Center
                    )
                } else if (pingResult != null) {
                    val isPass = pingResult.isSuccess
                    val latency = pingResult.latencyMs

                    val (color, rating) = when {
                        !isPass -> CyberRed to if (isHindi) "ऑफ़लाइन / पैकेट टाइमआउट" else "OFFLINE / PACKET TIMEOUT"
                        latency < 50 -> CyberGreen to if (isHindi) "उत्कृष्ट गति (तेज़)" else "EXCELLENT LATENCY (FAST)"
                        latency < 120 -> CyberCyan to if (isHindi) "अच्छी नेटवर्क गति" else "GOOD LATENCY"
                        else -> CyberAmber to if (isHindi) "उच्च विलंबता (धीमा)" else "HIGH LATENCY (LAGGY)"
                    }

                    Text(
                        text = if (isPass) "${latency} ms" else if (isHindi) "विफल" else "FAILED",
                        fontSize = 36.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = color,
                        fontFamily = FontFamily.Monospace
                    )

                    Spacer(modifier = Modifier.height(6.dp))
                    Surface(
                        color = color.copy(alpha = 0.15f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            text = rating,
                            color = color,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = if (isPass) {
                            if (isHindi) {
                                "${pingResult.host}:53 से सॉकेट कनेक्शन ${latency}ms में सफल रहा।"
                            } else {
                                "Real socket handshake to ${pingResult.host}:53 succeeded in ${latency}ms."
                            }
                        } else {
                            if (isHindi) {
                                "${pingResult.host} से कनेक्शन स्थापित नहीं हो सका: ${pingResult.error ?: "समय समाप्त"}"
                            } else {
                                "Could not establish handshake with ${pingResult.host}: ${pingResult.error ?: "Timeout"}"
                            }
                        },
                        fontSize = 12.sp,
                        color = CyberTextSecondary,
                        textAlign = TextAlign.Center
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onRetest,
                enabled = !isLoading,
                colors = ButtonDefaults.buttonColors(containerColor = CyberCyan, contentColor = Color(0xFF001B2E)),
                modifier = Modifier.testTag("retest_ping_button")
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(if (isHindi) "पुनः जाँचें" else "Retest Ping", fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                border = androidx.compose.foundation.BorderStroke(1.dp, CyberCardBorder)
            ) {
                Text(if (isHindi) "बंद करें" else "Close", color = CyberTextPrimary, fontSize = 12.sp)
            }
        }
    )
}
