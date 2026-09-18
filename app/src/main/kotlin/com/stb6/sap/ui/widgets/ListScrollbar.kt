package com.stb6.sap.ui.widgets

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun Modifier.listScrollbar(state: LazyListState, color: Color): Modifier {
    val alpha by animateFloatAsState(if (state.isScrollInProgress) 0.9f else 0.45f, label = "list-scrollbar")
    return drawWithContent {
        drawContent()
        val info = state.layoutInfo
        val rowHeight = info.visibleItemsInfo.firstOrNull()?.size ?: return@drawWithContent
        val spacing = info.mainAxisItemSpacing
        val total = info.totalItemsCount.toFloat() * rowHeight +
            (info.totalItemsCount - 1).coerceAtLeast(0) * spacing + info.beforeContentPadding + info.afterContentPadding
        val viewport = size.height
        if (total <= viewport || viewport <= 0f) return@drawWithContent
        // Offset estimation requires equal-height rows.
        val scrolled = state.firstVisibleItemIndex.toFloat() * (rowHeight + spacing) + state.firstVisibleItemScrollOffset
        val height = (viewport * viewport / total).coerceIn(minOf(24.dp.toPx(), viewport), viewport)
        val top = (scrolled / (total - viewport) * (viewport - height)).coerceIn(0f, viewport - height)
        val width = 3.dp.toPx()
        drawRoundRect(color.copy(alpha = alpha), Offset(size.width - width - 2.dp.toPx(), top),
            Size(width, height), CornerRadius(width / 2))
    }
}
