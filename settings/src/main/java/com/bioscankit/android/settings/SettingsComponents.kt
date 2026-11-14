package com.bioscankit.android.settings

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallMade
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.bioscankit.android.design.BioScanBackButton
import com.bioscankit.android.design.BioScanDesign

internal data class SettingsPalette(
    val pageTop: Color,
    val pageBottom: Color,
    val cardBackground: Color,
    val iconTileBackground: Color,
)

/**
 * 设置类页面的调色板。
 *
 * 与 iOS `SettingsPageContainer` / `SettingsCardView` 一致：
 * 页面背景用 `systemBackground`，卡片与图标底座用 `secondarySystemBackground`，
 * 即**系统中性色**，不掺入宿主品牌色（品牌色只用于图标和强调文字）。
 */
@Composable
internal fun settingsPalette(): SettingsPalette {
    val colors = BioScanDesign.colors
    return SettingsPalette(
        pageTop = colors.systemBackground,
        pageBottom = colors.secondarySystemBackground,
        cardBackground = colors.secondarySystemBackground,
        iconTileBackground = colors.systemBackground,
    )
}

@Composable
private fun SettingsNavigationBar(color: Color) {
    val activity = LocalContext.current.findActivity()
    val usesLightIcons = BioScanDesign.isDark

    DisposableEffect(activity, color) {
        val window = activity?.window
        val previousColor = window?.navigationBarColor
        val previousContrastEnforced = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window?.isNavigationBarContrastEnforced
        } else {
            null
        }
        val controller = window?.let { WindowCompat.getInsetsController(it, it.decorView) }
        val previousLightNavigationBars = controller?.isAppearanceLightNavigationBars

        onDispose {
            if (window != null && previousColor != null) {
                window.navigationBarColor = previousColor
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && previousContrastEnforced != null) {
                window?.isNavigationBarContrastEnforced = previousContrastEnforced
            }
            if (previousLightNavigationBars != null) {
                controller?.isAppearanceLightNavigationBars = previousLightNavigationBars
            }
        }
    }

    SideEffect {
        activity?.window?.let { window ->
            window.navigationBarColor = color.toArgb()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                window.isNavigationBarContrastEnforced = false
            }
            WindowCompat.getInsetsController(window, window.decorView)
                .isAppearanceLightNavigationBars = !usesLightIcons
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}


@Composable
fun SettingsPageContainer(
    versionText: String,
    modifier: Modifier = Modifier,
    onVersionTapped: (() -> Unit)? = null,
    content: @Composable ColumnScope.(SettingsPageMetrics) -> Unit,
) {
    val colors = BioScanDesign.colors
    val palette = settingsPalette()
    SettingsNavigationBar(palette.pageBottom)
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(palette.pageTop, palette.pageBottom),
                ),
            ),
    ) {
        val widthDp = maxWidth.value.toInt()
        val twoColumns = widthDp >= 960
        val metrics = SettingsPageMetrics(
            usesTwoColumnLayout = twoColumns,
            contentMaxWidthDp = if (twoColumns) 1080 else 760,
            horizontalPaddingDp = if (twoColumns) 24 else 18,
            columnSpacingDp = if (twoColumns) 22 else 0,
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = metrics.horizontalPaddingDp.dp, vertical = 10.dp)
                // 与 iOS SettingsView 的 `.safeAreaInset(edge: .bottom) { 88 }` 一致：
                // 设置页底部有 Tab 栏悬浮，留出同样高度的空白，避免最后一行被挡住。
                .padding(bottom = 88.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = metrics.contentMaxWidthDp.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                content(metrics)
                Text(
                    text = versionText,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.secondaryText,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = onVersionTapped != null) { onVersionTapped?.invoke() }
                        .padding(top = 4.dp, bottom = 14.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
    }
}

@Composable
fun SettingsTopBar(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = BioScanDesign.colors
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium.copy(
                fontSize = 17.sp,
                lineHeight = 22.sp,
                fontWeight = FontWeight.SemiBold,
            ),
            color = colors.primaryText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        BioScanBackButton(
            onClick = onBack,
            contentDescription = null,
            modifier = Modifier.align(Alignment.CenterStart),
        )
    }
}

@Composable
fun SettingsSection(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SettingsSectionTitle(title)
        SettingsCard(content = content)
    }
}

@Composable
fun SettingsSectionTitle(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium.copy(
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
        ),
        color = BioScanDesign.colors.secondaryText,
        modifier = modifier,
    )
}

/**
 * Card-internal title with an optional subtitle.
 *
 * Distinct from [SettingsSectionTitle], which renders the small uppercase
 * section header above a card.
 */
@Composable
fun SettingsItemTitle(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
) {
    val colors = BioScanDesign.colors
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge.copy(
                fontSize = 17.sp,
                lineHeight = 23.sp,
                fontWeight = FontWeight.Black,
            ),
            color = colors.primaryText,
        )
        if (subtitle != null) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    fontWeight = FontWeight.Medium,
                ),
                color = colors.secondaryText,
            )
        }
    }
}

@Composable
fun SettingsFieldLabel(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        modifier = modifier,
        style = MaterialTheme.typography.bodyLarge.copy(
            fontSize = 14.sp,
            lineHeight = 20.sp,
            fontWeight = FontWeight.Bold,
        ),
        color = BioScanDesign.colors.primaryText,
    )
}

@Composable
fun SettingsChoiceField(
    label: String,
    options: List<SettingsSegmentOption>,
    selectedId: String,
    onSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SettingsFieldLabel(label)
        SettingsSegmentedControl(
            options = options,
            selectedId = selectedId,
            onSelected = onSelected,
        )
    }
}

@Composable
fun SettingsCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val palette = settingsPalette()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(palette.cardBackground, RoundedCornerShape(16.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}

@Composable
fun SettingsRow(
    title: String,
    subtitle: String? = null,
    detail: String? = null,
    accessory: SettingsAccessory = SettingsAccessory.None,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    leading: @Composable () -> Unit,
    trailing: (@Composable () -> Unit)? = null,
) {
    val colors = BioScanDesign.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .alpha(if (enabled) 1f else 0.5f)
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = if (subtitle == null) Alignment.CenterVertically else Alignment.Top,
    ) {
        SettingsIconTile(content = leading)
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 14.sp,
                    lineHeight = 21.sp,
                    fontWeight = FontWeight.Bold,
                ),
                color = colors.primaryText,
            )
            subtitle?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = colors.secondaryText,
                )
            }
        }
        detail?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.labelLarge.copy(
                    fontSize = 12.sp,
                    lineHeight = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                ),
                color = colors.secondaryText,
            )
        }
        if (trailing != null) {
            trailing()
        }

        when (accessory) {
            SettingsAccessory.None -> Unit
            SettingsAccessory.Navigation -> Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = colors.secondaryText,
                modifier = Modifier
                    .padding(top = if (subtitle == null) 2.dp else 6.dp)
                    // iOS: chevron.right 12 bold
                    .size(14.dp),
            )
            SettingsAccessory.External -> Icon(
                imageVector = Icons.AutoMirrored.Filled.CallMade,
                contentDescription = null,
                tint = colors.secondaryText,
                modifier = Modifier
                    .padding(top = if (subtitle == null) 2.dp else 6.dp)
                    .size(14.dp),
            )
        }
    }
}

@Composable
fun SettingsIconTile(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val palette = settingsPalette()
    Box(
        modifier = modifier
            .size(44.dp)
            .background(palette.iconTileBackground, RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

enum class SettingsIconStyle {
    Accent,
    Danger,
    Primary,
}

@Composable
fun SettingsIcon(
    imageVector: ImageVector,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    style: SettingsIconStyle = SettingsIconStyle.Accent,
    size: androidx.compose.ui.unit.Dp = 17.dp,
) {
    val colors = BioScanDesign.colors
    Icon(
        imageVector = imageVector,
        contentDescription = contentDescription,
        tint = when (style) {
            SettingsIconStyle.Accent -> colors.accent
            SettingsIconStyle.Danger -> colors.danger
            SettingsIconStyle.Primary -> colors.primaryText
        },
        modifier = modifier.size(size),
    )
}

@Composable
fun SettingsDangerRow(
    title: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    leading: @Composable () -> Unit,
) {
    val colors = BioScanDesign.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .alpha(if (enabled) 1f else 0.5f)
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading()
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = 14.sp,
                lineHeight = 21.sp,
                fontWeight = FontWeight.Bold,
            ),
            color = colors.danger,
        )
    }
}

@Composable
fun SettingsDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(
        modifier = modifier,
        thickness = 1.dp,
        color = if (BioScanDesign.isDark) {
            Color.White.copy(alpha = 0.18f)
        } else {
            Color.Black.copy(alpha = 0.07f)
        },
    )
}

@Composable
fun SettingsSegmentedControl(
    options: List<SettingsSegmentOption>,
    selectedId: String,
    onSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (options.isEmpty()) return
    val isDark = BioScanDesign.isDark
    val colors = BioScanDesign.colors
    val container = if (isDark) Color(0xFF2C2C2E) else Color(0xFFE9E9EE)
    val selected = if (isDark) Color(0xFF4A4A4E) else Color.White
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(34.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(container)
            .padding(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        options.forEach { option ->
            val isSelected = option.id == selectedId
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize()
                    .then(
                        if (isSelected) {
                            Modifier.shadow(
                                elevation = 1.5.dp,
                                shape = RoundedCornerShape(6.dp),
                            )
                        } else {
                            Modifier
                        }
                    )
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (isSelected) selected else Color.Transparent)
                    .clickable { onSelected(option.id) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = option.title,
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = colors.primaryText,
                )
            }
        }
    }
}

@Composable
fun SettingsMembershipCard(
    state: SettingsMembershipState,
    modifier: Modifier = Modifier,
    isActionDisabled: Boolean = false,
    onAction: (() -> Unit)? = null,
    /** 覆盖默认的 accent 底色/白字，供宿主对齐自家 CTA 配色。 */
    actionBackground: Color? = null,
    actionContentColor: Color? = null,
) {
    val colors = BioScanDesign.colors
    val palette = settingsPalette()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(palette.cardBackground, RoundedCornerShape(14.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        when (state) {
            is SettingsMembershipState.Lifetime,
            is SettingsMembershipState.LifetimeCopy,
            -> Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Rounded.CheckCircle,
                    contentDescription = null,
                    tint = colors.accent,
                    modifier = Modifier.size(16.dp),
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = when (state) {
                            is SettingsMembershipState.LifetimeCopy -> state.title
                            else -> "Lifetime Membership"
                        },
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 14.sp,
                            lineHeight = 21.sp,
                            fontWeight = FontWeight.Black,
                        ),
                        color = colors.primaryText,
                    )
                    Text(
                        text = when (state) {
                            is SettingsMembershipState.LifetimeCopy -> state.subtitle
                            else -> "Unlimited scans"
                        },
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                        ),
                        color = colors.secondaryText,
                    )
                }
            }

            is SettingsMembershipState.Upgrade -> {
                val remainingText = state.remainingText
                    ?: state.remainingScans.coerceAtLeast(0).takeIf { it > 0 }
                        ?.let { stringResource(R.string.bioscan_settings_membership_remaining, it) }
                remainingText?.takeIf(String::isNotBlank)?.let {
                    Text(
                        text = remainingText,
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                        color = colors.primaryText,
                    )
                }
                onAction?.let { action ->
                    Surface(
                        modifier = Modifier.alpha(if (isActionDisabled) 0.6f else 1f),
                        enabled = !isActionDisabled,
                        onClick = action,
                        shape = RoundedCornerShape(999.dp),
                        color = actionBackground ?: colors.accent,
                        contentColor = actionContentColor ?: Color.White,
                    ) {
                        Text(
                            text = state.actionTitle ?: stringResource(R.string.bioscan_settings_membership_upgrade),
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Black,
                            ),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        )
                    }
                }
                Text(
                    text = state.footnote ?: stringResource(R.string.bioscan_settings_membership_footnote),
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                    color = colors.accent,
                )
            }
        }
    }
}
