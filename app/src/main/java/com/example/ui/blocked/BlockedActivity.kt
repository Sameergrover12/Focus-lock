package com.example.ui.blocked

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.FocusLockTheme

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

        val title = intent.getStringExtra(EXTRA_TITLE) ?: "Focus Lock"
        val reason = intent.getStringExtra(EXTRA_REASON) ?: "This activity is paused to protect your focus."
        val type = intent.getStringExtra(EXTRA_TYPE) ?: TYPE_APP
        val nextWindow = intent.getStringExtra(EXTRA_NEXT_WINDOW)

        setContent {
            FocusLockTheme {
                BlockedScreen(
                    title = title,
                    reason = reason,
                    type = type,
                    nextWindow = nextWindow,
                    onGoHome = {
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
}

@Composable
fun BlockedScreen(
    title: String,
    reason: String,
    type: String,
    nextWindow: String?,
    onGoHome: () -> Unit
) {
    val icon = when (type) {
        BlockedActivity.TYPE_SCREEN_TIME, BlockedActivity.TYPE_GROUP_BUDGET -> Icons.Default.HourglassBottom
        BlockedActivity.TYPE_GROUP_SCHEDULE -> Icons.Default.Schedule
        BlockedActivity.TYPE_WEBSITE -> Icons.Default.Language
        BlockedActivity.TYPE_KEYWORD -> Icons.Default.TextFields
        else -> Icons.Default.Lock
    }

    val typeLabel = when (type) {
        BlockedActivity.TYPE_SCREEN_TIME -> "Daily Limit Reached"
        BlockedActivity.TYPE_GROUP_SCHEDULE -> "Focus Schedule Active"
        BlockedActivity.TYPE_GROUP_BUDGET -> "Group Budget Exhausted"
        BlockedActivity.TYPE_WEBSITE -> "Website Blocked"
        BlockedActivity.TYPE_KEYWORD -> "Keyword Restricted"
        else -> "App Blocked"
    }

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .testTag("blocked_screen_surface"),
        color = MaterialTheme.colorScheme.background
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Calm accent circular emblem
                Box(
                    modifier = Modifier
                        .size(84.dp)
                        .background(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = "Focus Protection Icon",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(42.dp)
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = typeLabel,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.sp
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = reason,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    lineHeight = 24.sp
                )

                if (!nextWindow.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                    ) {
                        Text(
                            text = "Unlocks at $nextWindow",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(36.dp))

                Button(
                    onClick = onGoHome,
                    modifier = Modifier
                        .fillMaxWidth(0.75f)
                        .height(50.dp)
                        .testTag("go_home_button"),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text(
                        text = "Return to Home Screen",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}
