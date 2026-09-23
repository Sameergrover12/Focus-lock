package com.example.ui.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.preferences.UserPreferencesRepository

/**
 * Psychological Friction & Passphrase Setup Dialog
 *
 * Setup Warning: "You must remember this exact phrase, including all punctuation and capitalization.
 * There is no 'Forgot Password' button. If you forget it, you will be entirely locked out of emergency breaks and settings."
 *
 * Verification Test: Force the user to manually type the phrase perfectly during this setup phase
 * to verify they have memorized it before allowing them to proceed.
 */
@Composable
fun CognitivePassphraseSetupDialog(
    initialPassphrase: String,
    onSavePassphrase: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var candidatePhrase by remember {
        mutableStateOf(
            if (initialPassphrase.isNotBlank()) initialPassphrase
            else UserPreferencesRepository.DEFAULT_COGNITIVE_PASSPHRASE
        )
    }
    var verificationInput by remember { mutableStateOf("") }
    var pasteBlockedNotice by remember { mutableStateOf<String?>(null) }

    val isCandidateValid = candidatePhrase.trim().length >= 10
    val isVerificationMatched = candidatePhrase.trim().isNotEmpty() &&
            verificationInput.trim() == candidatePhrase.trim()

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color(0xFF0F0F0F),
            border = BorderStroke(1.dp, Color(0xFF262626)),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("cognitive_passphrase_setup_dialog")
        ) {
            Column(
                modifier = Modifier
                    .padding(22.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Psychology,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Reflective Passphrase Setup",
                            color = Color.White,
                            fontSize = 17.sp,
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

                // Severe Warning Box (MANDATORY REQUIREMENT)
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF2A1010),
                    border = BorderStroke(1.dp, Color(0xFF802020)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("passphrase_severe_warning")
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = Color(0xFFEF5350),
                            modifier = Modifier
                                .size(22.dp)
                                .padding(top = 2.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "CRITICAL WARNING",
                                color = Color(0xFFEF5350),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "You must remember this exact phrase, including all punctuation and capitalization. There is no 'Forgot Password' button. If you forget it, you will be entirely locked out of emergency breaks and settings.",
                                color = Color(0xFFFFCDD2),
                                fontSize = 12.sp,
                                lineHeight = 18.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Step 1: Candidate Phrase Definition
                Text(
                    text = "Step 1: Choose Your Reflective Sentence",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "A sobering statement to confront when dopamine cravings hit:",
                    color = Color(0xFF888888),
                    fontSize = 12.sp,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = candidatePhrase,
                    onValueChange = { candidatePhrase = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("candidate_passphrase_input"),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color(0xFF141414),
                        unfocusedContainerColor = Color(0xFF111111),
                        focusedBorderColor = Color.White,
                        unfocusedBorderColor = Color(0xFF333333),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    maxLines = 3
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Step 2: Verification Test (MANDATORY REQUIREMENT)
                Text(
                    text = "Step 2: Verification Test (Prove Memorization)",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Manually type the exact sentence above to prove you have committed it to memory. Copy-pasting is not permitted.",
                    color = Color(0xFF888888),
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = verificationInput,
                    onValueChange = { incoming ->
                        val filtered = filterOutPaste(
                            previousText = verificationInput,
                            newText = incoming,
                            context = context,
                            onPasteAttemptBlocked = {
                                pasteBlockedNotice = "Copy-paste blocked. Type manually to prove memorization."
                            }
                        )
                        if (filtered != verificationInput) {
                            pasteBlockedNotice = null
                        }
                        verificationInput = filtered
                    },
                    placeholder = {
                        Text(
                            "Type phrase from memory verbatim...",
                            color = Color(0xFF555555),
                            fontSize = 12.sp
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("verification_test_input"),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color(0xFF141414),
                        unfocusedContainerColor = Color(0xFF111111),
                        focusedBorderColor = if (isVerificationMatched) Color(0xFF81C784) else Color.White,
                        unfocusedBorderColor = if (isVerificationMatched) Color(0xFF4CAF50) else Color(0xFF333333),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    maxLines = 3
                )

                if (pasteBlockedNotice != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = pasteBlockedNotice ?: "",
                        color = Color(0xFFEF5350),
                        fontSize = 11.sp,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                if (isVerificationMatched) {
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
                            text = "Verification test passed. Memorization confirmed.",
                            color = Color(0xFF81C784),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                } else if (verificationInput.isNotEmpty()) {
                    Text(
                        text = "Match progress: ${verificationInput.length} / ${candidatePhrase.trim().length} chars",
                        color = Color(0xFF888888),
                        fontSize = 11.sp,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("cancel_passphrase_button"),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Color(0xFF333333)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFCCCCCC))
                    ) {
                        Text("Cancel")
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Button(
                        onClick = {
                            if (isVerificationMatched) {
                                onSavePassphrase(candidatePhrase.trim())
                            }
                        },
                        enabled = isVerificationMatched,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("confirm_passphrase_button"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White,
                            contentColor = Color.Black,
                            disabledContainerColor = Color(0xFF222222),
                            disabledContentColor = Color(0xFF555555)
                        )
                    ) {
                        Text("Confirm & Save", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
