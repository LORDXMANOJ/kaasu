package com.manoj.kaasu.ui.home.styles

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StylePickerSheet(current: HomeStyle, onPick: (HomeStyle) -> Unit, onDismiss: () -> Unit) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet) {
        Column(Modifier.navigationBarsPadding()) {
            Text("Home style", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 20.dp))
            Text(
                "Try each one. Only the Home screen changes for now.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 12.dp),
            )
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                items(HomeStyle.entries, key = { it.name }) { style ->
                    StylePreview(style, selected = style == current) { onPick(style) }
                }
            }
        }
    }
}

@Composable
private fun StylePreview(style: HomeStyle, selected: Boolean, onClick: () -> Unit) {
    val look = remember(style) { lookFor(style) }
    val outline = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    Column(
        Modifier
            .clip(RoundedCornerShape(18.dp))
            .clickable(role = Role.RadioButton, onClick = onClick)
            .semantics { this.selected = selected; contentDescription = "${style.label}, ${style.tagline}" },
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(0.78f)
                .clip(RoundedCornerShape(18.dp))
                .border(if (selected) 2.5.dp else 1.dp, outline, RoundedCornerShape(18.dp))
                .styledBackground(look)
                .padding(10.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Box(Modifier.size(width = 24.dp, height = 4.dp).styledSurface(SurfaceLook(fill = androidx.compose.ui.graphics.SolidColor(look.muted)), Corners(2.dp)))
                Box(Modifier.size(width = 52.dp, height = 12.dp).styledSurface(SurfaceLook(fill = androidx.compose.ui.graphics.SolidColor(look.text)), Corners(3.dp)))
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Box(Modifier.weight(1f).height(18.dp).styledSurface(look.primaryAction, Corners(6.dp)))
                    Box(Modifier.weight(1f).height(18.dp).styledSurface(look.action, Corners(6.dp)))
                    Box(Modifier.weight(1f).height(18.dp).styledSurface(look.action, Corners(6.dp)))
                }
                Box(Modifier.fillMaxWidth().height(1.dp).styledSurface(SurfaceLook(fill = androidx.compose.ui.graphics.SolidColor(look.line)), Corners(0.dp)))
                repeat(2) {
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        Box(Modifier.size(10.dp).styledSurface(SurfaceLook(fill = androidx.compose.ui.graphics.SolidColor(look.accent.copy(alpha = 0.7f))), Corners(5.dp)))
                        Box(Modifier.weight(1f).height(6.dp).padding(top = 2.dp).styledSurface(SurfaceLook(fill = androidx.compose.ui.graphics.SolidColor(look.muted)), Corners(2.dp)))
                    }
                }
            }
            if (selected) {
                Icon(
                    Icons.Rounded.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.align(Alignment.BottomEnd).size(22.dp),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(style.label, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 4.dp))
        Text(
            style.tagline,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        Spacer(Modifier.width(1.dp))
    }
}
