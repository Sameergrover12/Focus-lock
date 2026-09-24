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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.utf16CodePoint
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.preferences.UserPreferencesRepository

/**
 * High-Stakes Confirmation & Verification Dialog for Strict Mode / Cheating Protection.
 *
 * Mandated Warning Text:
 * "Strict Mode activates irreversible friction. To modify locked features, disable restrictions,
 * or access emergency breaks, you will be required to type your exact reflective passphrase verbatim.
 * If you forget it, there is no bypass or reset."
 */
@Composable
fun StrictModeActivationDialog(
    initialPassphrase: String,
    onConfirm: (String) -> Unit,
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

    val trimmedCandidate = candidatePhrase.trim()
    val isCandidateValid = trimmedCandidate.length >= 15
    val isMatched = isCandidateValid && verificationInput.trim() == trimmedCandidate

    val textToolbar = LocalTextToolbar.current
    val noPasteToolbar = remember(textToolbar) { NoPasteTextToolbar(textToolbar) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color(0xFF0D0E11),
            border = BorderStroke(1.dp, Color(0xFFE53935).copy(alpha = 0.8f)),
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .padding(16.dp)
                .testTag("strict_mode_activation_dialog")
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
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFFE53935).copy(alpha = 0.2f),
                            modifier = Modifier.size(38.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = null,
                                    tint = Color(0xFFEF5350),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Activate Strict Mode",
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Irreversible friction safeguard",
                                color = Color(0xFFAAAAAA),
                                fontSize = 12.sp
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Cancel",
                            tint = Color(0xFF888888)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // High-Stakes Contextual Warning Box
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF200B0B),
                    border = BorderStroke(1.dp, Color(0xFFE53935).copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Warning",
                            tint = Color(0xFFEF5350),
                            modifier = Modifier
                                .size(20.dp)
                                .padding(top = 2.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Strict Mode activates irreversible friction. To modify locked features, disable restrictions, or access emergency breaks, you will be required to type your exact reflective passphrase verbatim. If you forget it, there is no bypass or reset.",
                            color = Color(0xFFFFCDD2),
                            fontSize = 12.sp,
                            lineHeight = 18.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Step 1: Define Passphrase
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "1. Define Your Reflective Passphrase",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Choose a meaningful statement of personal discipline (min 15 chars).",
                        color = Color(0xFF888888),
                        fontSize = 11.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = candidatePhrase,
                        onValueChange = {
                            candidatePhrase = it
                            verificationInput = "" // reset verification if candidate changes
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("strict_mode_define_input"),
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF10B981),
                            unfocusedBorderColor = Color(0xFF22262F),
                            focusedContainerColor = Color(0xFF141519),
                            unfocusedContainerColor = Color(0xFF141519),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        placeholder = {
                            Text("Enter your reflective statement...", color = Color(0xFF555555))
                        },
                        minLines = 2,
                        maxLines = 4
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Step 2: Verbatim Verification Test
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "2. Verbatim Typing Verification",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )

                        if (isMatched) {
                            Surface(
                                shape = CircleShape,
                                color = Color(0xFF064E3B),
                                modifier = Modifier.size(20.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Matched",
                                        tint = Color(0xFF10B981),
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Type the exact phrase above manually to prove commitment. Copy-paste is disabled.",
                        color = Color(0xFF888888),
                        fontSize = 11.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    CompositionLocalProvider(LocalTextToolbar provides noPasteToolbar) {
                        OutlinedTextField(
                            value = verificationInput,
                            onValueChange = { incoming ->
                                val filtered = filterOutPaste(
                                    previousText = verificationInput,
                                    newText = incoming,
                                    context = context,
                                    onPasteAttemptBlocked = {
                                        pasteBlockedNotice = "Copy-paste disabled. Type character-by-character."
                                    }
                                )
                                if (filtered != verificationInput) {
                                    pasteBlockedNotice = null
                                }
                                verificationInput = filtered
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("strict_mode_verify_input")
                                .onPreviewKeyEvent { keyEvent ->
                                    if (keyEvent.type == KeyEventType.KeyDown) {
                                        val isCtrlOrMeta = keyEvent.isCtrlPressed || keyEvent.isMetaPressed
                                        if (isCtrlOrMeta && (keyEvent.key == Key.V ||
                                                    keyEvent.utf16CodePoint == 'v'.code ||
                                                    keyEvent.utf16CodePoint == 'V'.code)) {
                                            pasteBlockedNotice = "Copy-paste (Ctrl+V) disabled."
                                            true
                                        } else false
                                    } else false
                                },
                            shape = RoundedCornerShape(10.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = if (isMatched) Color(0xFF10B981) else Color(0xFF22262F),
                                unfocusedBorderColor = if (isMatched) Color(0xFF10B981) else Color(0xFF22262F),
                                focusedContainerColor = Color(0xFF141519),
                                unfocusedContainerColor = Color(0xFF141519),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            placeholder = {
                                Text("Type the exact passphrase to verify...", color = Color(0xFF555555))
                            },
                            minLines = 2,
                            maxLines = 4
                        )
                    }

                    if (pasteBlockedNotice != null) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = pasteBlockedNotice ?: "",
                            color = Color(0xFFEF5350),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "${verificationInput.length} / ${trimmedCandidate.length} characters",
                            fontSize = 11.sp,
                            color = Color(0xFF888888)
                        )
                        Text(
                            text = if (isMatched) "✓ Exact Match Confirmed" else "Pending Match",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isMatched) Color(0xFF10B981) else Color(0xFFEF5350)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, Color(0xFF22262F)),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = Color(0xFF141519),
                            contentColor = Color.White
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("strict_mode_cancel_button")
                    ) {
                        Text("Cancel", fontSize = 13.sp)
                    }

                    Button(
                        onClick = {
                            if (isMatched) {
                                onConfirm(trimmedCandidate)
                            }
                        },
                        enabled = isMatched,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF10B981),
                            contentColor = Color.Black,
                            disabledContainerColor = Color(0xFF22262F),
                            disabledContentColor = Color(0xFF555555)
                        ),
                        modifier = Modifier
                            .weight(1.3f)
                            .testTag("strict_mode_confirm_button")
                    ) {
                        Text(
                            text = "Lock & Enforce",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
