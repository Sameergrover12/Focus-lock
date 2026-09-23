package com.example.ui.blocked

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.focusapp.util.MotivationLibrary

class BlockedActivity : ComponentActivity() {

    companion object {
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_REASON = "extra_reason"
        const val EXTRA_TYPE = "extra_type"
        const val EXTRA_PACKAGE = "extra_package"
        const val EXTRA_NEXT_WINDOW = "extra_next_window"
        const val EXTRA_QUOTE = "extra_quote"

        const val TYPE_APP = "type_app"
        const val TYPE_SCREEN_TIME = "type_screen_time"
        const val TYPE_GROUP_SCHEDULE = "type_group_schedule"
        const val TYPE_GROUP_BUDGET = "type_group_budget"
        const val TYPE_WEBSITE = "type_website"
        const val TYPE_KEYWORD = "type_keyword"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val title = intent.getStringExtra(EXTRA_TITLE) ?: "Restricted"
        val reason = intent.getStringExtra(EXTRA_REASON) ?: ""
        val type = intent.getStringExtra(EXTRA_TYPE) ?: TYPE_APP
        val quote = intent.getStringExtra(EXTRA_QUOTE) ?: MotivationLibrary.getRandomFullScreenQuote()

        val returnToHome = {
            val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            startActivity(homeIntent)
            finish()
        }

        // Tapping back also acknowledges and returns to home screen
        onBackPressedDispatcher.addCallback(this) {
            returnToHome()
        }

        setContent {
            BlockedScreen(
                title = title,
                reason = reason,
                type = type,
                quote = quote,
                onGoHome = returnToHome
            )
        }
    }
}

@Composable
fun BlockedScreen(
    title: String = "Focus Lock",
    reason: String = "",
    type: String = BlockedActivity.TYPE_APP,
    quote: String = MotivationLibrary.getRandomFullScreenQuote(),
    nextWindow: String? = null,
    onGoHome: () -> Unit = {},
    onTimeout: () -> Unit = {}
) {
    // Parse quote body and attribution if separated by newline
    val parts = remember(quote) {
        val split = quote.split("\n", limit = 2)
        if (split.size > 1) {
            Pair(split[0].trim(), split[1].trim())
        } else {
            Pair(quote.trim(), "")
        }
    }
    val quoteBody = parts.first
    val quoteAuthor = parts.second

    val badgeLabel = when (type) {
        BlockedActivity.TYPE_APP -> if (title.isNotBlank()) "RESTRICTED APP • $title" else "RESTRICTED APP"
        BlockedActivity.TYPE_WEBSITE -> if (title.isNotBlank()) "RESTRICTED WEBSITE • $title" else "RESTRICTED WEBSITE"
        BlockedActivity.TYPE_SCREEN_TIME -> "SCREEN TIME LIMIT EXCEEDED"
        BlockedActivity.TYPE_GROUP_SCHEDULE -> "FOCUS SCHEDULE ACTIVE"
        BlockedActivity.TYPE_GROUP_BUDGET -> "GROUP BUDGET EXHAUSTED"
        else -> "FOCUS RESTRICTION ACTIVE"
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F0F0F))
            .systemBarsPadding()
            .padding(horizontal = 28.dp, vertical = 24.dp)
            .testTag("blocked_black_interstitial")
    ) {
        // Top context badge
        Surface(
            color = Color(0xFF1E1E1E),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, Color(0xFF333333)),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 16.dp)
        ) {
            Text(
                text = badgeLabel,
                color = Color(0xFFAAAAAA),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
            )
        }

        // Center card with prominent quote mark and clean typography
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "“",
                color = Color.White.copy(alpha = 0.35f),
                fontSize = 68.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = 48.sp,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = quoteBody,
                color = Color(0xFFF5F5F5),
                fontSize = 21.sp,
                fontWeight = FontWeight.Medium,
                lineHeight = 31.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("blocked_message_text")
            )

            if (quoteAuthor.isNotBlank()) {
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = quoteAuthor,
                    color = Color(0xFF9E9E9E),
                    fontSize = 14.sp,
                    fontStyle = FontStyle.Italic,
                    fontWeight = FontWeight.Normal,
                    letterSpacing = 0.5.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // Bottom subtle "Acknowledge & Return" button
        OutlinedButton(
            onClick = onGoHome,
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.3f)),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = Color(0xFF1A1A1A),
                contentColor = Color.White
            ),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(0.85f)
                .heightIn(min = 48.dp)
                .testTag("acknowledge_return_button")
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = "Acknowledge & Return",
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.5.sp
            )
        }
    }
}

