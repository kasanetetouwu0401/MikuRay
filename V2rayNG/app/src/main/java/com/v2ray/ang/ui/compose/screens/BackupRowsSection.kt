package com.v2ray.ang.ui.compose.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.v2ray.ang.R
import com.v2ray.ang.ui.compose.components.MikuActionRow
import com.v2ray.ang.ui.compose.components.MikuRowPosition

/**
 * Padanan Compose 1:1 dari 4 baris <MaterialCardView> di activity_backup.xml:
 * Backup, Share, Restore, WebDAV Config. Urutan, ikon, teks sama persis --
 * hanya cara render-nya yang pindah dari View ke Compose.
 */
@Composable
fun BackupRowsSection(
    onBackupClick: () -> Unit,
    onShareClick: () -> Unit,
    onRestoreClick: () -> Unit,
    onWebDavConfigClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val arrow = painterResource(R.drawable.uwu_arrow)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 4.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        MikuActionRow(
            title = stringResource(R.string.title_configuration_backup),
            icon = painterResource(R.drawable.ic_backup_24dp),
            trailingIcon = arrow,
            position = MikuRowPosition.TOP,
            onClick = onBackupClick,
        )
        MikuActionRow(
            title = stringResource(R.string.title_configuration_share),
            icon = painterResource(R.drawable.ic_share_24dp),
            trailingIcon = arrow,
            position = MikuRowPosition.MIDDLE,
            onClick = onShareClick,
        )
        MikuActionRow(
            title = stringResource(R.string.title_configuration_restore),
            icon = painterResource(R.drawable.ic_restore_24dp),
            trailingIcon = arrow,
            position = MikuRowPosition.MIDDLE,
            onClick = onRestoreClick,
        )
        MikuActionRow(
            title = stringResource(R.string.title_webdav_config_setting),
            icon = painterResource(R.drawable.ic_settings_24dp),
            trailingIcon = arrow,
            position = MikuRowPosition.BOTTOM,
            onClick = onWebDavConfigClick,
        )
    }
}
