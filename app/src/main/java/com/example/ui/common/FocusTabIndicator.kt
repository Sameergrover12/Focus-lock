package com.example.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TabPosition
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp

/**
 * Continuous 1:1 finger-tracking tab indicator bar that glides smoothly
 * beneath the user's thumb at 120fps based on PagerState offset.
 */
@Composable
fun FocusTabIndicator(
    tabPositions: List<TabPosition>,
    pagerState: PagerState,
    modifier: Modifier = Modifier
) {
    if (tabPositions.isEmpty()) return

    val targetPageFloat = (pagerState.currentPage + pagerState.currentPageOffsetFraction)
        .coerceIn(0f, (tabPositions.size - 1).toFloat())
    val leftPageIndex = targetPageFloat.toInt().coerceIn(0, tabPositions.lastIndex)
    val rightPageIndex = (leftPageIndex + 1).coerceIn(0, tabPositions.lastIndex)
    val fraction = targetPageFloat - leftPageIndex

    val currentTab = tabPositions[leftPageIndex]
    val nextTab = tabPositions[rightPageIndex]

    val indicatorLeft = lerp(currentTab.left, nextTab.left, fraction)
    val indicatorRight = lerp(currentTab.right, nextTab.right, fraction)
    val indicatorWidth = (indicatorRight - indicatorLeft).coerceAtLeast(0.dp)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .wrapContentSize(Alignment.BottomStart)
            .offset(x = indicatorLeft)
            .width(indicatorWidth)
            .height(3.dp)
            .background(
                color = MaterialTheme.colorScheme.primary,
                shape = RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp)
            )
    )
}
