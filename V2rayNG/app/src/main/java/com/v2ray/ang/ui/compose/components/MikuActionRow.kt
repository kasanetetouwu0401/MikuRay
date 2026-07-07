package com.v2ray.ang.ui.compose.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.v2ray.ang.ui.compose.theme.MikuTheme

/**
 * Posisi baris di dalam grup card (menentukan sudut mana yang membulat),
 * setara dengan ShapeAppearance.App.CardView.Top / Middle / Bottom di XML.
 */
enum class MikuRowPosition { SINGLE, TOP, MIDDLE, BOTTOM }

private fun MikuRowPosition.toShape(bigRadius: androidx.compose.ui.unit.Dp = 28.dp, smallRadius: androidx.compose.ui.unit.Dp = 4.dp) =
    when (this) {
        MikuRowPosition.SINGLE -> RoundedCornerShape(bigRadius)
        MikuRowPosition.TOP -> RoundedCornerShape(topStart = bigRadius, topEnd = bigRadius, bottomStart = smallRadius, bottomEnd = smallRadius)
        MikuRowPosition.MIDDLE -> RoundedCornerShape(smallRadius)
        MikuRowPosition.BOTTOM -> RoundedCornerShape(topStart = smallRadius, topEnd = smallRadius, bottomStart = bigRadius, bottomEnd = bigRadius)
    }

/**
 * Satu baris aksi ala MikuRay: lingkaran ikon (bg colorPrimary) + judul + chip panah di kanan.
 * Padanan Compose dari pola berulang di activity_about.xml (layout_soure_ccode, layout_oss_licenses, dst).
 *
 * Contoh pemakaian di dalam MikuComposeTheme { }:
 * MikuActionRow(
 *     title = stringResource(R.string.title_source_code),
 *     icon = painterResource(R.drawable.ic_source_code_24dp),
 *     trailingIcon = painterResource(R.drawable.uwu_arrow),
 *     position = MikuRowPosition.TOP,
 *     onClick = { Utils.openUri(context, AppConfig.APP_URL) },
 * )
 */
@Composable
fun MikuActionRow(
    title: String,
    icon: Painter,
    trailingIcon: Painter,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    position: MikuRowPosition = MikuRowPosition.SINGLE,
    iconBackground: Color = MaterialTheme.colorScheme.primary,
    iconTint: Color = MaterialTheme.colorScheme.onPrimary,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clickable(onClick = onClick),
        color = MikuTheme.extraColors.colorCard,
        shape = position.toShape(),
        tonalElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(45.dp)
                    .background(iconBackground, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(25.dp),
                )
            }

            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp),
            )

            TrailingArrowChip(trailingIcon)
        }
    }
}

@Composable
private fun RowScope.TrailingArrowChip(icon: Painter) {
    Box(
        modifier = Modifier
            .size(28.dp)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(25.dp),
        )
    }
}
