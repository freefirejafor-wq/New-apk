package com.example.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.model.AppConfig
import com.example.ui.theme.*
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(
    config: AppConfig,
    onSplashFinished: () -> Unit
) {
    // Pulse animation for the logo
    val infiniteTransition = rememberInfiniteTransition(label = "splash_anim")
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "logo_scale"
    )

    LaunchedEffect(Unit) {
        // Display splash for 1.8 seconds then proceed to main tabs
        delay(1800)
        onSplashFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        NavyDark,
                        Color(0xFF060B1E),
                        NavyDark
                    )
                )
            )
            .clickable { onSplashFinished() } // Allow quick tap to skip
            .testTag("splash_screen_root"),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(24.dp)
        ) {
            // App Logo Box with subtle elevation & border
            Box(
                modifier = Modifier
                    .scale(scale)
                    .size(116.dp)
                    .clip(RoundedCornerShape(26.dp))
                    .background(Color(0xFF070B19))
                    .border(2.dp, CyanAccent.copy(alpha = 0.7f), RoundedCornerShape(26.dp))
                    .testTag("splash_app_logo"),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_app_logo),
                    contentDescription = "SportzX Live TV Logo",
                    modifier = Modifier.fillMaxSize()
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // App Title
            Text(
                text = config.appTitle,
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.ExtraBold,
                    color = TextPrimary,
                    fontSize = 28.sp,
                    letterSpacing = 0.5.sp
                ),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Subtitle
            Text(
                text = "Live HD Sports & Cricket Streaming",
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = GoldYellow,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp
                ),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(36.dp))

            // Circular progress indicator
            CircularProgressIndicator(
                modifier = Modifier.size(34.dp),
                color = CyanAccent,
                strokeWidth = 3.dp
            )

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "Loading stream servers...",
                style = MaterialTheme.typography.labelSmall.copy(
                    color = TextSecondary,
                    fontSize = 12.sp
                )
            )
        }

        // Bottom version watermark
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Version ${config.version}",
                style = MaterialTheme.typography.bodySmall.copy(
                    color = TextSecondary.copy(alpha = 0.7f),
                    fontSize = 12.sp
                )
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "High Quality Ultra HD HLS Player",
                style = MaterialTheme.typography.labelSmall.copy(
                    color = TextSecondary.copy(alpha = 0.4f),
                    fontSize = 10.sp
                )
            )
        }
    }
}
