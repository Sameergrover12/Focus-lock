package com.example.ui.webwords

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.common.CheatProtectionAuthDialog
import com.example.ui.common.PendingLooseningAction
import com.example.ui.viewmodel.FocusViewModel

@Composable
fun WebKeywordsScreen(
    viewModel: FocusViewModel
) {
    var selectedTab by remember { mutableIntStateOf(0) } // 0: Websites, 1: Keywords

    val websites by viewModel.blockedWebsites.collectAsStateWithLifecycle()
    val keywords by viewModel.blockedKeywords.collectAsStateWithLifecycle()
    val isCheatProtectionEnabled by viewModel.isCheatProtectionEnabled.collectAsStateWithLifecycle()

    var newWebsiteInput by remember { mutableStateOf("") }
    var newKeywordInput by remember { mutableStateOf("") }
    var pendingCheatAction by remember { mutableStateOf<PendingLooseningAction?>(null) }

    pendingCheatAction?.let { action ->
        CheatProtectionAuthDialog(
            action = action,
            onVerify = { viewModel.verifyCheatPassphrase(it) },
            onDismiss = { pendingCheatAction = null }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("web_keywords_screen")
    ) {
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text("Websites (${websites.size})") },
                icon = { Icon(Icons.Default.Language, contentDescription = null, modifier = Modifier.size(18.dp)) }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text("Keywords (${keywords.size})") },
                icon = { Icon(Icons.Default.TextFields, contentDescription = null, modifier = Modifier.size(18.dp)) }
            )
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Explanatory & Limitation Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp).padding(top = 2.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = if (selectedTab == 0) {
                                    "Browser URL Protection: Reads the address bar in Chrome, Firefox, Samsung Internet, Edge, Brave, and scans visible domains."
                                } else {
                                    "Screen Content Protection: Scans visible UI and web page text for restricted words. Note: text baked into static images or video frames cannot be detected without OCR."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 18.sp
                            )
                        }
                    }
                }
            }

            // Input Form Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        if (selectedTab == 0) {
                            Text(
                                text = "Add Blocked Website or Domain",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = newWebsiteInput,
                                    onValueChange = { newWebsiteInput = it },
                                    placeholder = { Text("e.g. youtube.com, reddit.com") },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("website_input_field"),
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Button(
                                    onClick = {
                                        if (newWebsiteInput.isNotBlank()) {
                                            viewModel.addBlockedWebsite(newWebsiteInput)
                                            newWebsiteInput = ""
                                        }
                                    },
                                    enabled = newWebsiteInput.isNotBlank(),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.testTag("add_website_button")
                                ) {
                                    Icon(imageVector = Icons.Default.Add, contentDescription = "Add")
                                }
                            }
                        } else {
                            Text(
                                text = "Add Blocked Keyword or Phrase",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = newKeywordInput,
                                    onValueChange = { newKeywordInput = it },
                                    placeholder = { Text("e.g. gambling, distraction") },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("keyword_input_field"),
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Button(
                                    onClick = {
                                        if (newKeywordInput.isNotBlank()) {
                                            viewModel.addBlockedKeyword(newKeywordInput)
                                            newKeywordInput = ""
                                        }
                                    },
                                    enabled = newKeywordInput.isNotBlank(),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.testTag("add_keyword_button")
                                ) {
                                    Icon(imageVector = Icons.Default.Add, contentDescription = "Add")
                                }
                            }
                        }
                    }
                }
            }

            // List Header
            item {
                Text(
                    text = if (selectedTab == 0) "Blocked Domains (${websites.size})" else "Blocked Keywords (${keywords.size})",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            if (selectedTab == 0) {
                if (websites.isEmpty()) {
                    item {
                        EmptyStateCard("No websites blocked yet. Add domains like 'youtube.com' or 'tiktok.com' above.")
                    }
                }
                items(websites, key = { it.id }) { site ->
                    RuleItemCard(
                        icon = Icons.Default.Language,
                        primaryText = site.domainOrUrl,
                        secondaryText = "Any matching URL in browser triggers exit",
                        onDelete = {
                            if (isCheatProtectionEnabled) {
                                pendingCheatAction = PendingLooseningAction(
                                    title = "Unblock Website '${site.domainOrUrl}'",
                                    description = "Deleting this rule will allow uninhibited browsing to '${site.domainOrUrl}'.",
                                    onAuthorized = { viewModel.removeBlockedWebsite(site.id) }
                                )
                            } else {
                                viewModel.removeBlockedWebsite(site.id)
                            }
                        },
                        testTag = "website_item_${site.id}"
                    )
                }
            } else {
                if (keywords.isEmpty()) {
                    item {
                        EmptyStateCard("No keywords blocked yet. Add keywords or phrases above to prevent viewing them.")
                    }
                }
                items(keywords, key = { it.id }) { word ->
                    RuleItemCard(
                        icon = Icons.Default.TextFields,
                        primaryText = word.keyword,
                        secondaryText = "Blocks matching screen & browser text",
                        onDelete = {
                            if (isCheatProtectionEnabled) {
                                pendingCheatAction = PendingLooseningAction(
                                    title = "Remove Blocked Keyword '${word.keyword}'",
                                    description = "Deleting this rule removes keyword shielding for '${word.keyword}'.",
                                    onAuthorized = { viewModel.removeBlockedKeyword(word.id) }
                                )
                            } else {
                                viewModel.removeBlockedKeyword(word.id)
                            }
                        },
                        testTag = "keyword_item_${word.id}"
                    )
                }
            }
        }
    }
}

@Composable
private fun RuleItemCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    primaryText: String,
    secondaryText: String,
    onDelete: () -> Unit,
    testTag: String
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = primaryText,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = secondaryText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Remove rule",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun EmptyStateCard(message: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
