package com.example.ui.common

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * High-Stakes Confirmation Dialog for Strict Mode / Anti-Cheat Lock.
 *
 * Enforces 600-1000 character minimum for cognitive friction passphrase.
 */
@Composable
fun StrictModeActivationDialog(
    initialPassphrase: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var candidatePhrase by remember {
        mutableStateOf(
            if (initialPassphrase.isNotBlank() && initialPassphrase.length in 600..1000) initialPassphrase
            else ""
        )
    }

    val trimmedCandidate = candidatePhrase.trim()
    val isCandidateValid = trimmedCandidate.length in 600..1000

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

                // Primary Passphrase Input Field
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Define Your Reflective Passphrase",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Choose a meaningful statement of personal discipline (min 600 chars, max 1000).",
                        color = Color(0xFF888888),
                        fontSize = 11.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = candidatePhrase,
                        onValueChange = { input ->
                            if (input.length <= 1000) {
                                candidatePhrase = input
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("strict_mode_define_input"),
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = if (isCandidateValid) Color(0xFF10B981) else Color(0xFFE53935),
                            unfocusedBorderColor = if (isCandidateValid) Color(0xFF10B981) else Color(0xFF22262F),
                            focusedContainerColor = Color(0xFF141519),
                            unfocusedContainerColor = Color(0xFF141519),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        placeholder = {
                            Text(
                                "Enter your reflective statement of discipline (600 to 1000 characters)...",
                                color = Color(0xFF555555),
                                fontSize = 12.sp
                            )
                        },
                        minLines = 4,
                        maxLines = 8
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${candidatePhrase.length} / 1000",
                            fontSize = 11.sp,
                            color = if (isCandidateValid) Color(0xFF10B981) else if (candidatePhrase.isNotEmpty()) Color(0xFFEF5350) else Color(0xFF888888),
                            fontWeight = if (isCandidateValid) FontWeight.Bold else FontWeight.Normal
                        )
                        if (candidatePhrase.isNotEmpty() && !isCandidateValid) {
                            Text(
                                text = "Need ${600 - candidatePhrase.length} more characters",
                                fontSize = 11.sp,
                                color = Color(0xFFEF5350),
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Final Warning Box
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFF2B0E0E),
                    border = BorderStroke(1.dp, Color(0xFFE53935).copy(alpha = 0.6f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Warning",
                            tint = Color(0xFFEF5350),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Note: You must remember this exact phrase verbatim. You will not be asked to confirm it, and there is no bypass or reset.",
                            color = Color(0xFFFF8A80),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            lineHeight = 16.sp
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
                            if (isCandidateValid) {
                                onConfirm(trimmedCandidate)
                            }
                        },
                        enabled = isCandidateValid,
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
