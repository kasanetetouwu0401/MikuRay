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
 * Padanan Compose 1:1 dari 5 baris <MaterialCardView> yang dulu ada langsung
 * di activity_about.xml (di dalam <ExpandableView>): Source Code, OSS Licenses,
 * Feedback, Telegram Channel, Privacy Policy. Urutan, ikon, dan teks sama persis
 * -- hanya cara render-nya yang pindah dari View ke Compose.
 */
@Composable
fun AboutRowsSection(
    onSourceCodeClick: () -> Unit,
    onOssLicensesClick: () -> Unit,
    onFeedbackClick: () -> Unit,
    onTelegramChannelClick: () -> Unit,
    onPrivacyPolicyClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val arrow = painterResource(R.drawable.uwu_arrow)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        MikuActionRow(
            title = stringResource(R.string.title_source_code),
            icon = painterResource(R.drawable.ic_source_code_24dp),
            trailingIcon = arrow,
            position = MikuRowPosition.TOP,
            onClick = onSourceCodeClick,
        )
        MikuActionRow(
            title = stringResource(R.string.title_oss_license),
            icon = painterResource(R.drawable.license_24px),
            trailingIcon = arrow,
            position = MikuRowPosition.MIDDLE,
            onClick = onOssLicensesClick,
        )
        MikuActionRow(
            title = stringResource(R.string.title_pref_feedback),
            icon = painterResource(R.drawable.ic_feedback_24dp),
            trailingIcon = arrow,
            position = MikuRowPosition.MIDDLE,
            onClick = onFeedbackClick,
        )
        MikuActionRow(
            title = stringResource(R.string.title_tg_channel),
            icon = painterResource(R.drawable.ic_telegram_24dp),
            trailingIcon = arrow,
            position = MikuRowPosition.MIDDLE,
            onClick = onTelegramChannelClick,
        )
        MikuActionRow(
            title = stringResource(R.string.title_privacy_policy),
            icon = painterResource(R.drawable.ic_privacy_24dp),
            trailingIcon = arrow,
            position = MikuRowPosition.BOTTOM,
            onClick = onPrivacyPolicyClick,
        )
    }
}
