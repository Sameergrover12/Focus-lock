package com.example.ui.webwords

import android.widget.Toast
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.common.CheatProtectionAuthDialog
import com.example.ui.common.FocusTabIndicator
import com.example.ui.common.PendingLooseningAction
import com.example.ui.viewmodel.FocusViewModel
import com.example.util.FocusHaptics
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

@Composable
fun WebKeywordsScreen(
    viewModel: FocusViewModel
) {
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { 2 })
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val view = LocalView.current
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    val websites by viewModel.blockedWebsites.collectAsStateWithLifecycle()
    val keywords by viewModel.blockedKeywords.collectAsStateWithLifecycle()
    val isCheatProtectionEnabled by viewModel.isCheatProtectionEnabled.collectAsStateWithLifecycle()

    var newWebsiteInput by remember { mutableStateOf("") }
    var newKeywordInput by remember { mutableStateOf("") }
    var pendingCheatAction by remember { mutableStateOf<PendingLooseningAction?>(null) }

    val websitesListState = rememberLazyListState()
    val keywordsListState = rememberLazyListState()

    // Keyboard dismiss on settled page change
    LaunchedEffect(pagerState.settledPage) {
        focusManager.clearFocus()
        keyboardController?.hide()
    }

    // Mid-page safety recovery guard
    LaunchedEffect(pagerState.isScrollInProgress) {
        if (!pagerState.isScrollInProgress && kotlin.math.abs(pagerState.currentPageOffsetFraction) > 0.001f) {
            pagerState.animateScrollToPage(pagerState.currentPage)
        }
    }

    fun submitWebsites() {
        val input = newWebsiteInput.trim()
        if (input.isNotBlank()) {
            newWebsiteInput = "" // Clear text field immediately on Main thread
            viewModel.addBlockedWebsites(input) { result ->
                when (result) {
                    is FocusViewModel.AddRuleResult.AllDuplicates -> {
                        FocusHaptics.rejectOrDelete(view)
                        Toast.makeText(context, "Website is already in your blocked list", Toast.LENGTH_SHORT).show()
                    }
                    is FocusViewModel.AddRuleResult.Added -> {
                        FocusHaptics.confirm(view)
                    }
                    is FocusViewModel.AddRuleResult.EmptyInput -> {
                        FocusHaptics.rejectOrDelete(view)
                    }
                }
            }
        } else {
            FocusHaptics.rejectOrDelete(view)
        }
    }

    fun submitKeywords() {
        val input = newKeywordInput.trim()
        if (input.isNotBlank()) {
            newKeywordInput = "" // Clear text field immediately on Main thread
            viewModel.addBlockedKeywords(input) { result ->
                when (result) {
                    is FocusViewModel.AddRuleResult.AllDuplicates -> {
                        FocusHaptics.rejectOrDelete(view)
                        Toast.makeText(context, "Keyword is already in your blocked list", Toast.LENGTH_SHORT).show()
                    }
                    is FocusViewModel.AddRuleResult.Added -> {
                        FocusHaptics.confirm(view)
                    }
                    is FocusViewModel.AddRuleResult.EmptyInput -> {
                        FocusHaptics.rejectOrDelete(view)
                    }
                }
            }
        } else {
            FocusHaptics.rejectOrDelete(view)
        }
    }

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
            selectedTabIndex = pagerState.currentPage,
            containerColor = MaterialTheme.colorScheme.surface,
            indicator = { tabPositions ->
                FocusTabIndicator(tabPositions = tabPositions, pagerState = pagerState)
            }
        ) {
            Tab(
                selected = pagerState.currentPage == 0,
                onClick = {
                    FocusHaptics.tick(view)
                    coroutineScope.launch {
                        pagerState.animateScrollToPage(
                            0,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioNoBouncy,
                                stiffness = Spring.StiffnessMedium
                            )
                        )
                    }
                },
                modifier = Modifier.testTag("tab_websites"),
                selectedContentColor = MaterialTheme.colorScheme.primary,
                unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Language,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Websites (${websites.size})",
                        maxLines = 1,
                        softWrap = false,
                        fontSize = 13.sp,
                        letterSpacing = (-0.2).sp,
                        fontWeight = if (pagerState.currentPage == 0) FontWeight.SemiBold else FontWeight.Normal
                    )
                }
            }
            Tab(
                selected = pagerState.currentPage == 1,
                onClick = {
                    FocusHaptics.tick(view)
                    coroutineScope.launch {
                        pagerState.animateScrollToPage(
                            1,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioNoBouncy,
                                stiffness = Spring.StiffnessMedium
                            )
                        )
                    }
                },
                modifier = Modifier.testTag("tab_keywords"),
                selectedContentColor = MaterialTheme.colorScheme.primary,
                unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.TextFields,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Keywords (${keywords.size})",
                        maxLines = 1,
                        softWrap = false,
                        fontSize = 13.sp,
                        letterSpacing = (-0.2).sp,
                        fontWeight = if (pagerState.currentPage == 1) FontWeight.SemiBold else FontWeight.Normal
                    )
                }
            }
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxSize()
                .weight(1f),
            beyondViewportPageCount = 1
        ) { page ->
            when (page) {
                0 -> {
                    // Page 0: Websites
                    LazyColumn(
                        state = websitesListState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        item(key = "add_website_card") {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
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
                                            shape = RoundedCornerShape(12.dp),
                                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                            keyboardActions = KeyboardActions(onDone = { submitWebsites() })
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Button(
                                            onClick = { submitWebsites() },
                                            enabled = newWebsiteInput.isNotBlank(),
                                            shape = RoundedCornerShape(12.dp),
                                            modifier = Modifier.testTag("add_website_button")
                                        ) {
                                            Icon(imageVector = Icons.Default.Add, contentDescription = "Add")
                                        }
                                    }
                                }
                            }
                        }

                        item(key = "website_list_header") {
                            Text(
                                text = "Blocked Domains (${websites.size})",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                        }

                        if (websites.isEmpty()) {
                            item(key = "empty_websites_state") {
                                EmptyStateCard("No websites blocked yet. Add domains like 'youtube.com' or 'tiktok.com' above.")
                            }
                        }

                        items(
                            items = websites,
                            key = { it.id },
                            contentType = { "website_rule_row" }
                        ) { site ->
                            RuleItemCard(
                                modifier = Modifier.animateItem(
                                    fadeInSpec = tween(180),
                                    fadeOutSpec = tween(150),
                                    placementSpec = spring(
                                        dampingRatio = Spring.DampingRatioNoBouncy,
                                        stiffness = Spring.StiffnessMediumLow
                                    )
                                ),
                                icon = Icons.Default.Language,
                                primaryText = site.domainOrUrl,
                                secondaryText = "Any matching URL in browser triggers exit",
                                onDelete = {
                                    FocusHaptics.rejectOrDelete(view)
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
                    }
                }
                1 -> {
                    // Page 1: Keywords
                    LazyColumn(
                        state = keywordsListState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        item(key = "add_keyword_card") {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
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
                                            shape = RoundedCornerShape(12.dp),
                                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                            keyboardActions = KeyboardActions(onDone = { submitKeywords() })
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Button(
                                            onClick = { submitKeywords() },
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

                        item(key = "keyword_list_header") {
                            Text(
                                text = "Blocked Keywords (${keywords.size})",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                        }

                        if (keywords.isEmpty()) {
                            item(key = "empty_keywords_state") {
                                EmptyStateCard("No keywords blocked yet. Add keywords or phrases above to prevent viewing them.")
                            }
                        }

                        items(
                            items = keywords,
                            key = { it.id },
                            contentType = { "keyword_rule_row" }
                        ) { word ->
                            RuleItemCard(
                                modifier = Modifier.animateItem(
                                    fadeInSpec = tween(180),
                                    fadeOutSpec = tween(150),
                                    placementSpec = spring(
                                        dampingRatio = Spring.DampingRatioNoBouncy,
                                        stiffness = Spring.StiffnessMediumLow
                                    )
                                ),
                                icon = Icons.Default.TextFields,
                                primaryText = word.keyword,
                                secondaryText = "Blocks matching screen & browser text",
                                onDelete = {
                                    FocusHaptics.rejectOrDelete(view)
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
    }
}

@Composable
private fun RuleItemCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    primaryText: String,
    secondaryText: String,
    onDelete: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
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
