package com.example.ui.common

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
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
import androidx.compose.ui.platform.TextToolbar
import androidx.compose.ui.platform.TextToolbarStatus
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch

data class PendingLooseningAction(
    val title: String,
    val description: String,
    val onAuthorized: () -> Unit
)

/**
 * Custom TextToolbar that suppresses the "Paste" menu item
 * to prevent pasting via selection context popups.
 */
class NoPasteTextToolbar(private val delegate: TextToolbar) : TextToolbar {
    override val status: TextToolbarStatus
        get() = delegate.status

    override fun hide() {
        delegate.hide()
    }

    override fun showMenu(
        rect: Rect,
        onCopyRequested: (() -> Unit)?,
        onPasteRequested: (() -> Unit)?,
        onCutRequested: (() -> Unit)?,
        onSelectAllRequested: (() -> Unit)?
    ) {
        // Disallow paste action in popup context toolbar
        delegate.showMenu(
            rect = rect,
            onCopyRequested = onCopyRequested,
            onPasteRequested = null,
            onCutRequested = onCutRequested,
            onSelectAllRequested = onSelectAllRequested
        )
    }
}

/**
 * Filters out pasted, drag-and-dropped, or clipboard-inserted text chunks.
 * Ensures the passphrase can only be typed character-by-character directly from the keyboard.
 */
fun filterOutPaste(
    previousText: String,
    newText: String,
    context: Context,
    onPasteAttemptBlocked: () -> Unit
): String {
    // Single-character deletions or bulk deletions are always allowed
    if (newText.length <= previousText.length) {
        return newText
    }

    val addedCount = newText.length - previousText.length

    // Find the exact inserted substring between previousText and newText
    var prefixLen = 0
    while (prefixLen < previousText.length && prefixLen < newText.length && previousText[prefixLen] == newText[prefixLen]) {
        prefixLen++
    }
    var suffixLen = 0
    while (suffixLen < (previousText.length - prefixLen) &&
        suffixLen < (newText.length - prefixLen) &&
        previousText[previousText.length - 1 - suffixLen] == newText[newText.length - 1 - suffixLen]
    ) {
        suffixLen++
    }
    val insertedSubstring = newText.substring(prefixLen, newText.length - suffixLen)

    // Check against system clipboard
    val clipboard = try {
        context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    } catch (e: Exception) {
        null
    }
    val clipText = clipboard?.primaryClip?.let { clip ->
        if (clip.itemCount > 0) clip.getItemAt(0)?.text?.toString() else null
    }

    val isClipboardMatch = if (!clipText.isNullOrEmpty() && insertedSubstring.isNotEmpty()) {
        if (clipText == insertedSubstring || clipText == newText) {
            true
        } else if (insertedSubstring.length >= 2 && (clipText.contains(insertedSubstring) || insertedSubstring.contains(clipText))) {
            true
        } else {
            false
        }
    } else false

    // Bulk insertion (>3 characters at once) represents paste, drag-and-drop, or autocomplete dump
    val isBulkInsertion = addedCount > 3

    if (isClipboardMatch || isBulkInsertion) {
        onPasteAttemptBlocked()
        return previousText // Discard paste
    }

    return newText
}

@Composable
fun CheatProtectionAuthDialog(
    action: PendingLooseningAction,
    onVerify: suspend (String) -> Boolean,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var inputPassphrase by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var pasteBlockedNotice by remember { mutableStateOf<String?>(null) }
    var isVerifying by remember { mutableStateOf(false) }

    val textToolbar = LocalTextToolbar.current
    val noPasteToolbar = remember(textToolbar) { NoPasteTextToolbar(textToolbar) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .padding(16.dp)
                .testTag("cheat_protection_dialog"),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BoxIcon()
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Cheat Protection Lock",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Passphrase required to loosen restrictions",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = action.title,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = action.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "Type your 600–1,000 character passphrase (copy-paste is disabled):",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(8.dp))

                CompositionLocalProvider(LocalTextToolbar provides noPasteToolbar) {
                    OutlinedTextField(
                        value = inputPassphrase,
                        onValueChange = { incoming ->
                            val filtered = filterOutPaste(
                                previousText = inputPassphrase,
                                newText = incoming,
                                context = context,
                                onPasteAttemptBlocked = {
                                    pasteBlockedNotice = "Copy-paste is disabled. Passphrase must be typed manually."
                                }
                            )
                            if (filtered != inputPassphrase) {
                                pasteBlockedNotice = null
                            }
                            inputPassphrase = filtered
                            errorMessage = null
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 120.dp, max = 180.dp)
                            .testTag("cheat_protection_input")
                            .onPreviewKeyEvent { keyEvent ->
                                if (keyEvent.type == KeyEventType.KeyDown) {
                                    val isCtrlOrMeta = keyEvent.isCtrlPressed || keyEvent.isMetaPressed
                                    if (isCtrlOrMeta && (keyEvent.key == Key.V || keyEvent.utf16CodePoint == 'v'.code || keyEvent.utf16CodePoint == 'V'.code)) {
                                        pasteBlockedNotice = "Copy-paste (Ctrl+V) is disabled. Passphrase must be typed manually."
                                        true
                                    } else false
                                } else false
                            },
                        placeholder = { Text("Type full passphrase manually...") },
                        shape = RoundedCornerShape(12.dp),
                        isError = errorMessage != null
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${inputPassphrase.length} characters entered",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (pasteBlockedNotice != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = pasteBlockedNotice ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Medium
                    )
                }

                if (errorMessage != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = errorMessage ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Medium
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("cheat_protection_cancel_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Cancel")
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Button(
                        onClick = {
                            if (inputPassphrase.isBlank()) {
                                errorMessage = "Passphrase cannot be empty."
                                return@Button
                            }
                            isVerifying = true
                            errorMessage = null
                            coroutineScope.launch {
                                val matches = onVerify(inputPassphrase)
                                isVerifying = false
                                if (matches) {
                                    action.onAuthorized()
                                    onDismiss()
                                } else {
                                    errorMessage = "Incorrect passphrase. Action not authorized."
                                }
                            }
                        },
                        enabled = !isVerifying && inputPassphrase.isNotBlank(),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("cheat_protection_confirm_button"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text(if (isVerifying) "Verifying..." else "Authorize")
                    }
                }
            }
        }
    }
}

@Composable
private fun BoxIcon() {
    Surface(
        modifier = Modifier.size(40.dp),
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.errorContainer
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
fun CheatProtectionSetupDialog(
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var passphrase by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var pasteBlockedNotice by remember { mutableStateOf<String?>(null) }

    val length = passphrase.length
    val isLengthValid = length in 600..1000

    val textToolbar = LocalTextToolbar.current
    val noPasteToolbar = remember(textToolbar) { NoPasteTextToolbar(textToolbar) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .padding(16.dp)
                .testTag("cheat_protection_setup_dialog"),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        modifier = Modifier.size(40.dp),
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Set Cheat Protection",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "600 to 1,000 character passphrase",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "Any loosening of rules will require typing this exact passphrase. It can contain letters, numbers, punctuation, spaces, or paragraphs. Copy-paste is disabled.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(12.dp))

                CompositionLocalProvider(LocalTextToolbar provides noPasteToolbar) {
                    OutlinedTextField(
                        value = passphrase,
                        onValueChange = { incoming ->
                            val filtered = filterOutPaste(
                                previousText = passphrase,
                                newText = incoming,
                                context = context,
                                onPasteAttemptBlocked = {
                                    pasteBlockedNotice = "Copy-paste is disabled. Passphrase must be typed manually."
                                }
                            )
                            if (filtered != passphrase) {
                                pasteBlockedNotice = null
                            }
                            passphrase = filtered
                            errorMessage = null
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 140.dp, max = 200.dp)
                            .testTag("cheat_setup_passphrase_input")
                            .onPreviewKeyEvent { keyEvent ->
                                if (keyEvent.type == KeyEventType.KeyDown) {
                                    val isCtrlOrMeta = keyEvent.isCtrlPressed || keyEvent.isMetaPressed
                                    if (isCtrlOrMeta && (keyEvent.key == Key.V || keyEvent.utf16CodePoint == 'v'.code || keyEvent.utf16CodePoint == 'V'.code)) {
                                        pasteBlockedNotice = "Copy-paste (Ctrl+V) is disabled. Passphrase must be typed manually."
                                        true
                                    } else false
                                } else false
                            },
                        placeholder = { Text("Type passphrase manually (min 600 chars)...") },
                        shape = RoundedCornerShape(12.dp),
                        isError = length > 0 && !isLengthValid
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "$length / 1000 characters",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = when {
                            length == 0 -> MaterialTheme.colorScheme.onSurfaceVariant
                            isLengthValid -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.error
                        }
                    )
                    Text(
                        text = if (length < 600) "${600 - length} more needed" else if (length > 1000) "${length - 1000} too many" else "Length valid",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isLengthValid) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    )
                }

                if (pasteBlockedNotice != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = pasteBlockedNotice ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Medium
                    )
                }

                if (errorMessage != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = errorMessage ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("cheat_setup_cancel_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Cancel")
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Button(
                        onClick = {
                            if (!isLengthValid) {
                                errorMessage = "Passphrase must be between 600 and 1,000 characters."
                                return@Button
                            }
                            onConfirm(passphrase)
                        },
                        enabled = isLengthValid,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("cheat_setup_save_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Enable Lock")
                    }
                }
            }
        }
    }
}
