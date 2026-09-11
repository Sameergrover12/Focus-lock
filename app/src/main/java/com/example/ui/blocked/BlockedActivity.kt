package com.example.ui.blocked

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

class BlockedActivity : ComponentActivity() {

    companion object {
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_REASON = "extra_reason"
        const val EXTRA_TYPE = "extra_type"
        const val EXTRA_PACKAGE = "extra_package"
        const val EXTRA_NEXT_WINDOW = "extra_next_window"

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

        // Unskippable and not dismissible by back gesture during the 3 seconds
        onBackPressedDispatcher.addCallback(this) {
            // Cannot dismiss interstitial
        }

        setContent {
            BlockedScreen(
                onTimeout = {
                    val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                        addCategory(Intent.CATEGORY_HOME)
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    startActivity(homeIntent)
                    finish()
                }
            )
        }
    }
}

@Composable
fun BlockedScreen(
    title: String = "Focus Lock",
    reason: String = "",
    type: String = BlockedActivity.TYPE_APP,
    nextWindow: String? = null,
    onGoHome: () -> Unit = {},
    onTimeout: () -> Unit = {}
) {
    LaunchedEffect(Unit) {
        delay(3000L)
        onTimeout()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .testTag("blocked_black_interstitial"),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "KAAM KARLE BSDK",
            color = Color.White,
            fontSize = 28.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 2.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .padding(24.dp)
                .testTag("blocked_message_text")
        )
    }
}
