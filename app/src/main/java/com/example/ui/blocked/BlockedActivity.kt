package com.example.ui.blocked

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.FocusApplication
import com.example.data.preferences.UserPreferencesRepository
import com.example.focusapp.util.MotivationLibrary
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class BlockedActivity : ComponentActivity() {

    companion object {
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_REASON = "extra_reason"
        const val EXTRA_TYPE = "extra_type"
        const val EXTRA_PACKAGE = "extra_package"
        const val EXTRA_NEXT_WINDOW = "extra_next_window"
        const val EXTRA_QUOTE = "extra_quote"

        const val TYPE_APP = "type_app"
        const val TYPE_WEBSITE = "type_website"
        const val TYPE_KEYWORD = "type_keyword"
        const val TYPE_SCREEN_TIME = "type_screen_time"
        const val TYPE_GROUP_SCHEDULE = "type_group_schedule"
        const val TYPE_GROUP_BUDGET = "type_group_budget"
        const val TYPE_INVINCIBLE_TAMPER = "type_invincible_tamper"
    }

    private var allowManualDismiss = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val title = intent.getStringExtra(EXTRA_TITLE) ?: "Restricted"
        val reason = intent.getStringExtra(EXTRA_REASON) ?: ""
        val type = intent.getStringExtra(EXTRA_TYPE) ?: TYPE_APP
        val quote = intent.getStringExtra(EXTRA_QUOTE) ?: MotivationLibrary.getRandomFullScreenQuote()

        val app = application as? FocusApplication
        val prefRepo = app?.preferencesRepository

        val returnToHome = {
            val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            startActivity(homeIntent)
            finish()
        }

        // Tapping back during the 4-second pattern interrupt is disabled
        onBackPressedDispatcher.addCallback(this) {
            if (allowManualDismiss) {
                returnToHome()
            }
        }

        setContent {
            val breaksRemaining by (prefRepo?.emergencyBreaksRemaining?.collectAsStateWithLifecycle(
                initialValue = UserPreferencesRepository.MAX_WEEKLY_EMERGENCY_BREAKS
            ) ?: remember { mutableStateOf(UserPreferencesRepository.MAX_WEEKLY_EMERGENCY_BREAKS) })

            val cognitivePassphrase by (prefRepo?.cognitivePassphrase?.collectAsStateWithLifecycle(
                initialValue = UserPreferencesRepository.DEFAULT_COGNITIVE_PASSPHRASE
            ) ?: remember { mutableStateOf(UserPreferencesRepository.DEFAULT_COGNITIVE_PASSPHRASE) })

            BlockedScreen(
                title = title,
                reason = reason,
                type = type,
                quote = quote,
                breaksRemaining = breaksRemaining,
                cognitivePassphrase = cognitivePassphrase,
                onGoHome = returnToHome,
                onPatternInterruptPassed = {
                    allowManualDismiss = true
                },
                onActivateEmergencyBreak = { phrase ->
                    prefRepo?.triggerEmergencyBreak(phrase) ?: false
                },
                onEmergencyBreakActivated = {
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
    quote: String = MotivationLibrary.getRandomFullScreenQuote(),
    breaksRemaining: Int = 3,
    cognitivePassphrase: String = UserPreferencesRepository.DEFAULT_COGNITIVE_PASSPHRASE,
    nextWindow: String? = null,
    onGoHome: () -> Unit = {},
    onPatternInterruptPassed: () -> Unit = {},
    onActivateEmergencyBreak: suspend (String) -> Boolean = { false },
    onEmergencyBreakActivated: () -> Unit = {}
) {
    var showEmergencyDialog by remember { mutableStateOf(false) }
    var secondsRemaining by remember { mutableIntStateOf(4) }
    var canDismissManually by remember { mutableStateOf(false) }

    // 4-Second Pattern Interrupt: Forces user to read the quote for exactly 4 seconds
    LaunchedEffect(Unit) {
        for (i in 4 downTo 1) {
            secondsRemaining = i
            delay(1000)
        }
        secondsRemaining = 0
        canDismissManually = true
        onPatternInterruptPassed()
        // If user hasn't opened emergency dialog to type passphrase, auto-eject to Home
        if (!showEmergencyDialog) {
            onGoHome()
        }
    }

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
        BlockedActivity.TYPE_KEYWORD -> "RESTRICTED KEYWORD DETECTED"
        BlockedActivity.TYPE_SCREEN_TIME -> "SCREEN TIME LIMIT EXCEEDED"
        BlockedActivity.TYPE_GROUP_SCHEDULE -> "FOCUS SCHEDULE ACTIVE"
        BlockedActivity.TYPE_GROUP_BUDGET -> "GROUP BUDGET EXHAUSTED"
        else -> "FOCUS RESTRICTION ACTIVE"
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF000000))
            .systemBarsPadding()
            .padding(horizontal = 28.dp, vertical = 24.dp)
            .testTag("blocked_black_interstitial")
    ) {
        // Top context badge
        Surface(
            color = Color(0xFF0D0E11),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, Color(0xFF22262F)),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 16.dp)
        ) {
            Text(
                text = badgeLabel,
                color = Color(0xFF10B981),
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
                color = Color(0xFF10B981).copy(alpha = 0.45f),
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
                    color = Color(0xFFAAAAAA),
                    fontSize = 14.sp,
                    fontStyle = FontStyle.Italic,
                    fontWeight = FontWeight.Normal,
                    letterSpacing = 0.5.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // Bottom Controls: Pattern Interrupt friction countdown / Acknowledge + Emergency Failsafe
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (!canDismissManually) {
                // 4-Second friction state: manual bypass disabled
                Surface(
                    color = Color(0xFF0D0E11),
                    shape = RoundedCornerShape(27.dp),
                    border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.4f)),
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .heightIn(min = 52.dp)
                        .testTag("friction_countdown_badge")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 14.dp, horizontal = 20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "Absorbing pattern interrupt (${secondsRemaining}s)...",
                            color = Color(0xFF10B981),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 0.4.sp
                        )
                    }
                }
            } else {
                // Enabled after 4 seconds
                OutlinedButton(
                    onClick = onGoHome,
                    shape = RoundedCornerShape(27.dp),
                    border = BorderStroke(1.dp, Color(0xFF10B981)),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = Color(0xFF141519),
                        contentColor = Color.White
                    ),
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .heightIn(min = 52.dp)
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

            Spacer(modifier = Modifier.height(14.dp))

            // Emergency Failsafe Trigger
            Text(
                text = if (breaksRemaining > 0) "Emergency Break ($breaksRemaining/3 left this week)" else "Emergency Breaks Depleted (0/3)",
                color = if (breaksRemaining > 0) Color(0xFF888888) else Color(0xFF664444),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .clickable { showEmergencyDialog = true }
                    .padding(8.dp)
                    .testTag("emergency_break_button")
            )
        }
    }

    if (showEmergencyDialog) {
        EmergencyFailsafeDialog(
            breaksRemaining = breaksRemaining,
            expectedPassphrase = cognitivePassphrase,
            onDismiss = { showEmergencyDialog = false },
            onActivate = { phrase ->
                onActivateEmergencyBreak(phrase)
            },
            onSuccess = {
                showEmergencyDialog = false
                onEmergencyBreakActivated()
            }
        )
    }
}

/**
 * The Emergency Failsafe System
 * - 10-Minute Hard Cap
 * - 3 emergency bypasses per week
 * - Cognitive friction: manual verbatim typing of reflective passphrase
 * - Phantom Cutoff: no visual countdown timer
 * - Abrupt Snapback: access terminates immediately at 10:00
 */
@Composable
fun EmergencyFailsafeDialog(
    breaksRemaining: Int,
    expectedPassphrase: String,
    onDismiss: () -> Unit,
    onActivate: suspend (String) -> Boolean,
    onSuccess: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var typedInput by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }
    var isProcessing by remember { mutableStateOf(false) }

    val isMatch = typedInput.trim() == expectedPassphrase.trim()

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color(0xFF0F0F0F),
            border = BorderStroke(1.dp, Color(0xFF262626)),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("emergency_failsafe_dialog")
        ) {
            Column(
                modifier = Modifier
                    .padding(22.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.HourglassBottom,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Emergency Break Failsafe",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color(0xFF888888),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Weekly allowance banner
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (breaksRemaining > 0) Color(0xFF191919) else Color(0xFF2B1313),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Weekly Allowance:",
                            color = Color(0xFFAAAAAA),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        Text(
                            text = "$breaksRemaining of 3 breaks remaining",
                            color = if (breaksRemaining > 0) Color.White else Color(0xFFEF9A9A),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                if (breaksRemaining <= 0) {
                    Text(
                        text = "All 3 emergency breaks for this 7-day cycle have been depleted. Active blocks cannot be bypassed under any circumstances until the week resets.",
                        color = Color(0xFFE57373),
                        fontSize = 13.sp,
                        lineHeight = 20.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(18.dp))
                    Button(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(20.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF262626),
                            contentColor = Color.White
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Acknowledge & Close")
                    }
                } else {
                    Text(
                        text = "Grants exactly 10 minutes of access. During this break, there is no countdown timer on your screen. At exactly 10 minutes and 0 seconds, the block will abruptly snap back with no warning.",
                        color = Color(0xFFCCCCCC),
                        fontSize = 12.sp,
                        lineHeight = 18.sp
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Type this exact sentence to confirm your intention:",
                        color = Color(0xFFAAAAAA),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFF141414),
                        border = BorderStroke(1.dp, Color(0xFF333333)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "\"$expectedPassphrase\"",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(12.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    OutlinedTextField(
                        value = typedInput,
                        onValueChange = {
                            typedInput = it
                            isError = false
                        },
                        placeholder = {
                            Text(
                                "Type exact reflective sentence here...",
                                color = Color(0xFF666666),
                                fontSize = 13.sp
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("emergency_passphrase_input"),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Color(0xFF111111),
                            unfocusedContainerColor = Color(0xFF0D0D0D),
                            focusedBorderColor = if (isMatch) Color(0xFF81C784) else Color.White,
                            unfocusedBorderColor = if (isMatch) Color(0xFF4CAF50) else Color(0xFF262626),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        singleLine = false,
                        maxLines = 3
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    if (isMatch) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = Color(0xFF81C784),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Sentence matched exactly",
                                color = Color(0xFF81C784),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    } else if (typedInput.isNotEmpty()) {
                        Text(
                            text = "Character match: ${typedInput.length} / ${expectedPassphrase.length}",
                            color = Color(0xFF888888),
                            fontSize = 11.sp,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    if (isError) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Sentence did not match verbatim. Check punctuation and casing.",
                            color = Color(0xFFE57373),
                            fontSize = 12.sp,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Button(
                        onClick = {
                            scope.launch {
                                isProcessing = true
                                val success = onActivate(typedInput)
                                isProcessing = false
                                if (success) {
                                    onSuccess()
                                } else {
                                    isError = true
                                }
                            }
                        },
                        enabled = isMatch && !isProcessing,
                        shape = RoundedCornerShape(24.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White,
                            contentColor = Color.Black,
                            disabledContainerColor = Color(0xFF222222),
                            disabledContentColor = Color(0xFF555555)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("activate_emergency_break_button")
                    ) {
                        Text(
                            text = if (isProcessing) "Activating..." else "Activate 10-Minute Break",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }
    }
}
