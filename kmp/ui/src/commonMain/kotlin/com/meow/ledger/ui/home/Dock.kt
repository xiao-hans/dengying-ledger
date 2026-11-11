package com.meow.ledger.ui.home

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meow.ledger.ui.theme.LocalPalette

private val EASE = CubicBezierEasing(0.2f, 0.8f, 0.2f, 1f)

private data class DockItem(val label: String, val icon: ImageVector)

private val ITEMS = listOf(
    DockItem("账单", Icons.Outlined.AccountBalanceWallet),
    DockItem("统计", Icons.Outlined.BarChart),
    DockItem("自动记账", Icons.Outlined.Bolt),
    DockItem("我的", Icons.Outlined.Person),
)

/** 底部玻璃导航。高光条滑到选中项背后，只有选中项显示文字，对齐 src/components/Dock.tsx。 */
@Composable
fun Dock(selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val palette = LocalPalette.current
    val shape = RoundedCornerShape(28.dp)
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .height(56.dp)
            .shadow(6.dp, shape, ambientColor = palette.glassShadow, spotColor = palette.glassShadow)
            .clip(shape)
            .background(palette.glassFill)
            .border(1.dp, palette.glassBorder, shape),
    ) {
        val itemWidth = maxWidth / ITEMS.size
        val offset by animateDpAsState(
            targetValue = itemWidth * selected,
            animationSpec = tween(durationMillis = 320, easing = EASE),
            label = "dock-highlight",
        )
        Box(
            Modifier
                .offset(x = offset)
                .padding(vertical = 4.dp)
                .width(itemWidth)
                .fillMaxHeight()
                .clip(RoundedCornerShape(24.dp))
                .background(palette.dockHighlight)
                .border(1.dp, palette.glassBorder, RoundedCornerShape(24.dp)),
        )
        Row(Modifier.fillMaxSize()) {
            ITEMS.forEachIndexed { index, item ->
                val active = index == selected
                Row(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { onSelect(index) },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                ) {
                    Icon(
                        item.icon,
                        contentDescription = item.label,
                        tint = if (active) palette.textPrimary else palette.textSecondary,
                        modifier = Modifier.size(22.dp),
                    )
                    if (active) {
                        Text(
                            item.label,
                            color = palette.textPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(start = 6.dp),
                        )
                    }
                }
            }
        }
    }
}

