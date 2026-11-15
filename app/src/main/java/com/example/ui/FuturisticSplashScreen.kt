package com.example.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.theme.CyberAmber
import com.example.ui.theme.CyberBackground
import com.example.ui.theme.CyberBlue
import com.example.ui.theme.CyberCard
import com.example.ui.theme.CyberCardBorder
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.CyberGreen
import com.example.ui.theme.CyberSurface
import com.example.ui.theme.CyberTeal
import com.example.ui.theme.CyberTextMuted
import com.example.ui.theme.CyberTextPrimary
import com.example.ui.theme.CyberTextSecondary
import kotlinx.coroutines.delay

@Composable
fun FuturisticSplashScreen(
    onSplashFinished: () -> Unit,
    durationMs: Long = 5000L
) {
    var progress by remember { mutableFloatStateOf(0f) }
    var currentLogText by remember { mutableStateOf("INITIALIZING NEURAL CORE...") }

    // Infinite transitions for cyber HUD animations
    val infiniteTransition = rememberInfiniteTransition(label = "hud_spin")
    val ringRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(6000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )
    val counterRotation by infiniteTransition.animateFloat(
        initialValue = 360f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(9000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "counter_rotation"
    )
    val glowPulse by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )
    val scanLineY by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "scanline"
    )

    // 5-second timing sequence with real-time telemetry state
    LaunchedEffect(Unit) {
        val startTime = System.currentTimeMillis()
        while (true) {
            val elapsed = System.currentTimeMillis() - startTime
            val fraction = (elapsed.toFloat() / durationMs).coerceIn(0f, 1f)
            progress = fraction

            currentLogText = when {
                fraction < 0.20f -> ">> [INIT] LOADING NEURAL KERNEL v2.5..."
                fraction < 0.40f -> ">> [SCAN] READING HARDWARE SENSORS & SOC THERMALS..."
                fraction < 0.65f -> ">> [CHECK] VERIFYING BATTERY HEALTH & PARTITIONS..."
                fraction < 0.85f -> ">> [SYSTEM] INITIALIZING ZERO-TRUST REPAIR ENGINE..."
                fraction < 0.98f -> ">> [READY] DIAGNOSTIC HUD SYNCHRONIZED..."
                else -> ">> SYSTEM ONLINE • LAUNCHING DOCTOR..."
            }

            if (fraction >= 1f) {
                delay(150)
                onSplashFinished()
                break
            }
            delay(40)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF0F223D),
                        Color(0xFF091424),
                        CyberBackground
                    ),
                    center = Offset(540f, 960f),
                    radius = 1200f
                )
            )
            .testTag("futuristic_splash_screen")
    ) {
        // Background cyber grid lines
        Canvas(modifier = Modifier.fillMaxSize()) {
            val step = 60.dp.toPx()
            for (x in 0..(size.width / step).toInt()) {
                drawLine(
                    color = Color(0x0C00E5FF),
                    start = Offset(x * step, 0f),
                    end = Offset(x * step, size.height),
                    strokeWidth = 1f
                )
            }
            for (y in 0..(size.height / step).toInt()) {
                drawLine(
                    color = Color(0x0C00E5FF),
                    start = Offset(0f, y * step),
                    end = Offset(size.width, y * step),
                    strokeWidth = 1f
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top Header with Skip Button & Encrypted Badge
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, start = 20.dp, end = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = CyberSurface.copy(alpha = 0.85f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, CyberCardBorder)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(CyberGreen, CircleShape)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "SECURE HUD v2.5",
                            color = CyberCyan,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                // Quick skip button with 48dp accessible tap box
                Box(
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .clickable { onSplashFinished() }
                        .testTag("skip_splash_btn"),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = Color(0x2200E5FF),
                        border = androidx.compose.foundation.BorderStroke(1.dp, CyberCyan.copy(alpha = 0.5f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "SKIP",
                                color = CyberCyan,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                Icons.Default.FastForward,
                                contentDescription = "Skip",
                                tint = CyberCyan,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }

            // Center Animated Badge & Holographic Rings
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
            Box(
                modifier = Modifier.size(240.dp),
                contentAlignment = Alignment.Center
            ) {
                // Outer rotating cyber dashed ring
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .rotate(ringRotation)
                ) {
                    drawCircle(
                        color = CyberCyan.copy(alpha = 0.35f),
                        radius = size.minDimension / 2 - 4.dp.toPx(),
                        style = Stroke(
                            width = 2.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                    )
                    // Concentric accent arcs
                    drawArc(
                        color = CyberCyan,
                        startAngle = 0f,
                        sweepAngle = 70f,
                        useCenter = false,
                        style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                    )
                    drawArc(
                        color = CyberBlue,
                        startAngle = 180f,
                        sweepAngle = 70f,
                        useCenter = false,
                        style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                    )
                }

                // Inner counter-rotating ring
                Canvas(
                    modifier = Modifier
                        .size(210.dp)
                        .rotate(counterRotation)
                ) {
                    drawArc(
                        color = CyberTeal,
                        startAngle = 45f,
                        sweepAngle = 40f,
                        useCenter = false,
                        style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
                    )
                    drawArc(
                        color = CyberAmber,
                        startAngle = 225f,
                        sweepAngle = 40f,
                        useCenter = false,
                        style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
                    )
                }

                // Pulsing glow aura
                Box(
                    modifier = Modifier
                        .size(175.dp)
                        .scale(glowPulse)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    CyberCyan.copy(alpha = 0.28f),
                                    CyberBlue.copy(alpha = 0.12f),
                                    Color.Transparent
                                )
                            ),
                            CircleShape
                        )
                )

                // The Hindi AI Smartphone Doctor Badge Image
                Image(
                    painter = painterResource(id = R.drawable.doctor_badge_transparent),
                    contentDescription = "एआई स्मार्टफोन डॉक्टर Logo",
                    modifier = Modifier
                        .size(165.dp)
                        .clip(CircleShape)
                        .border(2.dp, CyberCyan.copy(alpha = 0.7f), CircleShape)
                        .testTag("splash_doctor_logo")
                )

                // Cyber laser scan beam overlay across the logo
                Canvas(modifier = Modifier.size(165.dp)) {
                    val yPos = size.height * scanLineY
                    drawLine(
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                Color.Transparent,
                                CyberCyan.copy(alpha = 0.8f),
                                Color.White,
                                CyberCyan.copy(alpha = 0.8f),
                                Color.Transparent
                            )
                        ),
                        start = Offset(0f, yPos),
                        end = Offset(size.width, yPos),
                        strokeWidth = 2.5.dp.toPx()
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Hindi App Title from Badge
            Text(
                text = "एआई स्मार्टफोन डॉक्टर",
                color = CyberCyan,
                fontSize = 26.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.sp,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(4.dp))

            // English Sub-Brand
            Text(
                text = "AI SMARTPHONE DOCTOR",
                color = CyberTextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.5.sp,
                fontFamily = FontFamily.Monospace,
                textAlign = TextAlign.Center
            )

            Text(
                text = "NEURAL HARDWARE DIAGNOSTICS & SYSTEM REPAIR",
                color = CyberTextSecondary,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 1.5.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 2.dp)
            )

            Spacer(modifier = Modifier.height(28.dp))

            // Cyber Progress Card
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                shape = RoundedCornerShape(14.dp),
                color = CyberCard.copy(alpha = 0.9f),
                border = androidx.compose.foundation.BorderStroke(1.dp, CyberCardBorder)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    // Header row: Status & countdown
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Bolt,
                                contentDescription = null,
                                tint = CyberCyan,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "SYSTEM INITIALIZATION",
                                color = CyberCyan,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }

                        val remainingSec = ((durationMs * (1f - progress)) / 1000f).toInt() + 1
                        Text(
                            text = "${(progress * 100).toInt()}% • ${remainingSec}s",
                            color = CyberTextPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Progress Bar
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .background(Color(0xFF091424), RoundedCornerShape(4.dp))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(progress)
                                .height(8.dp)
                                .background(
                                    Brush.horizontalGradient(
                                        colors = listOf(CyberBlue, CyberCyan, CyberTeal)
                                    ),
                                    RoundedCornerShape(4.dp)
                                )
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Live terminal typewriter text
                    Text(
                        text = currentLogText,
                        color = CyberTextSecondary,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

            // Bottom Telemetry Chips
            Row(
                modifier = Modifier
                    .padding(bottom = 16.dp, start = 16.dp, end = 16.dp)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                TelemetryChip(icon = Icons.Default.Sensors, label = "SENSORS", status = "ONLINE", color = CyberGreen)
                TelemetryChip(icon = Icons.Default.Security, label = "SECURITY", status = "ZERO-TRUST", color = CyberCyan)
                TelemetryChip(icon = Icons.Default.Bolt, label = "NEURAL AI", status = "READY", color = CyberTeal)
            }
        }
    }
}

@Composable
private fun TelemetryChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    status: String,
    color: Color
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = CyberSurface.copy(alpha = 0.7f),
        border = androidx.compose.foundation.BorderStroke(1.dp, CyberCardBorder.copy(alpha = 0.6f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .background(color, CircleShape)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Column {
                Text(
                    text = label,
                    color = CyberTextMuted,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = status,
                    color = color,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}
