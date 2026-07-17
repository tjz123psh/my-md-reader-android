package com.pang.mdreader.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pang.mdreader.model.OutlineItem

/**
 * Table of contents panel showing document headings.
 */
@Composable
fun OutlinePanel(
    outline: List<OutlineItem>,
    activeHeadingId: String?,
    onHeadingClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (outline.isEmpty()) {
        Text(
            text = "没有标题",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(16.dp),
        )
        return
    }

    LazyColumn(modifier = modifier) {
        items(outline, key = { it.id }) { item ->
            val isActive = item.id == activeHeadingId

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onHeadingClick(item.id) }
                    .padding(
                        start = (12 + (item.level - 1) * 16).dp,
                        end = 12.dp,
                        top = 6.dp,
                        bottom = 6.dp,
                    ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Level indicator
                val indicatorColor = when (item.level) {
                    1 -> MaterialTheme.colorScheme.primary
                    2 -> MaterialTheme.colorScheme.secondary
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }

                Text(
                    text = "H${item.level}",
                    style = MaterialTheme.typography.labelSmall,
                    color = indicatorColor,
                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                )

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = item.title,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = if (isActive) FontWeight.W600 else FontWeight.Normal,
                    color = if (isActive) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface,
                )
            }

            if (isActive) {
                HorizontalDivider(
                    modifier = Modifier.padding(start = 12.dp, end = 12.dp),
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}
