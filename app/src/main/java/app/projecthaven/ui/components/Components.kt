// SPDX-License-Identifier: GPL-3.0-or-later
package app.projecthaven.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.projecthaven.ui.theme.CardShape
import app.projecthaven.ui.theme.ControlShape
import app.projecthaven.ui.theme.Dimens
import app.projecthaven.ui.theme.Haven

// ---- Screens and headers -----------------------------------------------------------------

/** Standard scrolling screen with a header. Content is laid out with the 16 dp gutter. */
@Composable
fun HavenScreen(
    title: String,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    largeTitle: Boolean = onBack == null,
    scrollable: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(Haven.colors.background),
    ) {
        HavenHeader(title, onBack, largeTitle, actions)
        val scroll = rememberScrollState()
        val body = Modifier
            .fillMaxWidth()
            .weight(1f)
            .let { if (scrollable) it.verticalScroll(scroll) else it }
            .padding(horizontal = Dimens.gutter)
        Column(body, verticalArrangement = Arrangement.spacedBy(Dimens.gap)) {
            content()
            Spacer(Modifier.height(Dimens.gapLarge))
        }
    }
}

@Composable
fun HavenHeader(
    title: String,
    onBack: (() -> Unit)? = null,
    largeTitle: Boolean = onBack == null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(Haven.colors.background)
            .windowInsetsPadding(WindowInsets.statusBars),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = Haven.colors.accent)
                }
                if (!largeTitle) {
                    Text(
                        title, style = Haven.type.headline, color = Haven.colors.text, maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).semantics { heading() },
                    )
                } else Spacer(Modifier.weight(1f))
            } else {
                Spacer(Modifier.weight(1f))
            }
            Row(verticalAlignment = Alignment.CenterVertically, content = actions)
        }
        if (largeTitle) {
            Text(
                title,
                style = Haven.type.largeTitle,
                color = Haven.colors.text,
                modifier = Modifier
                    .padding(start = Dimens.gutter, end = Dimens.gutter, bottom = Dimens.gap)
                    .semantics { heading() },
            )
        }
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        style = Haven.type.caption,
        color = Haven.colors.textSecondary,
        modifier = modifier.padding(start = 4.dp, top = Dimens.gapSmall).semantics { heading() },
    )
}

// ---- Cards and lists -------------------------------------------------------------------------

@Composable
fun HavenCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
    color: Color = Haven.colors.surface,
    contentPadding: Dp = Dimens.gutter,
    content: @Composable ColumnScope.() -> Unit,
) {
    val base = modifier
        .clip(CardShape)
        .background(color)
        .let { if (onClick != null) it.clickable(onClickLabel = onClickLabel, role = Role.Button, onClick = onClick) else it }
        .padding(contentPadding)
    Column(base, content = content)
}

/** Rounded group of rows separated by hairlines. */
@Composable
fun ListGroup(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(Haven.colors.surface),
        content = content,
    )
}

@Composable
fun GroupDivider(inset: Dp = 56.dp) {
    HorizontalDivider(Modifier.padding(start = inset), thickness = 0.5.dp, color = Haven.colors.divider)
}

@Composable
fun ListRow(
    title: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    iconTint: Color = Haven.colors.accent,
    subtitle: String? = null,
    value: String? = null,
    valueColor: Color = Haven.colors.textSecondary,
    chevron: Boolean = false,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = Dimens.rowHeight)
            .let { if (onClick != null) it.clickable(role = Role.Button, onClick = onClick) else it }
            .padding(horizontal = Dimens.gutter, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(Dimens.gutter))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = Haven.type.body, color = Haven.colors.text)
            if (subtitle != null) {
                Text(subtitle, style = Haven.type.footnote, color = Haven.colors.textSecondary)
            }
        }
        if (value != null) {
            Spacer(Modifier.width(Dimens.gapSmall))
            Text(value, style = Haven.type.callout, color = valueColor, maxLines = 1)
        }
        if (trailing != null) {
            Spacer(Modifier.width(Dimens.gapSmall))
            trailing()
        }
        if (chevron) {
            Icon(
                Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null,
                tint = Haven.colors.textSecondary, modifier = Modifier.size(22.dp),
            )
        }
    }
}

enum class StatusLevel { READY, ATTENTION, ERROR, NEUTRAL }

@Composable
fun StatusLevel.color(): Color = when (this) {
    StatusLevel.READY -> Haven.colors.ready
    StatusLevel.ATTENTION -> Haven.colors.attention
    StatusLevel.ERROR -> Haven.colors.emergency
    StatusLevel.NEUTRAL -> Haven.colors.textSecondary
}

@Composable
fun StatusRow(label: String, value: String, level: StatusLevel, onClick: (() -> Unit)? = null) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .let { if (onClick != null) it.clickable(role = Role.Button, onClick = onClick) else it }
            .padding(horizontal = Dimens.gutter, vertical = 8.dp)
            .semantics(mergeDescendants = true) { contentDescription = "$label: $value" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(level.color()))
        Spacer(Modifier.width(12.dp))
        Text(label, style = Haven.type.callout, color = Haven.colors.text, modifier = Modifier.weight(1f))
        Text(value, style = Haven.type.callout, color = Haven.colors.textSecondary, maxLines = 1)
    }
}

// ---- Buttons and controls ------------------------------------------------------------------

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    color: Color = Haven.colors.accent,
    contentColor: Color = Haven.colors.onAccent,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = ControlShape,
        color = if (enabled) color else Haven.colors.surfaceRaised,
        contentColor = if (enabled) contentColor else Haven.colors.textSecondary,
        modifier = modifier.heightIn(min = 52.dp),
    ) {
        Row(
            Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
            }
            Text(text, style = Haven.type.headline)
        }
    }
}

@Composable
fun EmergencyButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null) =
    PrimaryButton(text, onClick, modifier, icon, color = Haven.colors.emergency, contentColor = Haven.colors.onEmergency)

@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    tint: Color = Haven.colors.accent,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = ControlShape,
        color = Haven.colors.surfaceRaised,
        contentColor = if (enabled) tint else Haven.colors.textSecondary,
        modifier = modifier.heightIn(min = Dimens.minTouch),
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
            }
            Text(text, style = Haven.type.callout)
        }
    }
}

@Composable
fun HavenChip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        shape = ControlShape,
        color = if (selected) Haven.colors.accent else Haven.colors.surface,
        contentColor = if (selected) Haven.colors.onAccent else Haven.colors.text,
        modifier = modifier
            .heightIn(min = 36.dp)
            .semantics { contentDescription = text + if (selected) ", selected" else "" },
    ) {
        Box(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
            Text(text, style = Haven.type.callout)
        }
    }
}

/** iOS-style segmented control. */
@Composable
fun <T> Segmented(options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(ControlShape)
            .background(Haven.colors.surfaceRaised)
            .padding(3.dp),
    ) {
        options.forEach { option ->
            val isSelected = option == selected
            Box(
                Modifier
                    .weight(1f)
                    .heightIn(min = 40.dp)
                    .clip(ControlShape)
                    .background(if (isSelected) Haven.colors.accent else Color.Transparent)
                    .clickable(role = Role.RadioButton) { onSelect(option) }
                    .semantics { contentDescription = label(option) + if (isSelected) ", selected" else "" },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label(option), style = Haven.type.callout,
                    color = if (isSelected) Haven.colors.onAccent else Haven.colors.text,
                )
            }
        }
    }
}

@Composable
fun HavenSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        colors = SwitchDefaults.colors(
            checkedThumbColor = Haven.colors.onAccent,
            checkedTrackColor = Haven.colors.accent,
            checkedBorderColor = Haven.colors.accent,
            uncheckedThumbColor = Haven.colors.textSecondary,
            uncheckedTrackColor = Haven.colors.surfaceRaised,
            uncheckedBorderColor = Haven.colors.divider,
        ),
    )
}

@Composable
fun HavenTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    label: String? = null,
    singleLine: Boolean = true,
    minLines: Int = 1,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    isError: Boolean = false,
    supportingText: String? = null,
    leadingIcon: ImageVector? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Column(modifier) {
        if (label != null) {
            Text(label, style = Haven.type.footnote, color = Haven.colors.textSecondary, modifier = Modifier.padding(start = 4.dp, bottom = 4.dp))
        }
        TextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = { Text(placeholder, style = Haven.type.body, color = Haven.colors.textSecondary) },
            singleLine = singleLine,
            minLines = minLines,
            textStyle = Haven.type.body,
            keyboardOptions = keyboardOptions,
            isError = isError,
            leadingIcon = leadingIcon?.let { icon -> @Composable { Icon(icon, contentDescription = null, tint = Haven.colors.textSecondary) } },
            trailingIcon = trailing,
            shape = ControlShape,
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Haven.colors.surface,
                unfocusedContainerColor = Haven.colors.surface,
                errorContainerColor = Haven.colors.surface,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                errorIndicatorColor = Color.Transparent,
                cursorColor = Haven.colors.accent,
                focusedTextColor = Haven.colors.text,
                unfocusedTextColor = Haven.colors.text,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        if (supportingText != null) {
            Text(
                supportingText, style = Haven.type.footnote,
                color = if (isError) Haven.colors.emergency else Haven.colors.textSecondary,
                modifier = Modifier.padding(start = 4.dp, top = 4.dp),
            )
        }
    }
}

@Composable
fun SearchField(value: String, onValueChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier) {
    HavenTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = placeholder,
        modifier = modifier,
        leadingIcon = Icons.Rounded.Search,
        trailing = if (value.isNotEmpty()) {
            @Composable { IconButton(onClick = { onValueChange("") }) { Icon(Icons.Rounded.Close, contentDescription = "Clear search", tint = Haven.colors.textSecondary) } }
        } else null,
    )
}

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    message: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.fillMaxWidth().padding(vertical = 40.dp, horizontal = Dimens.gutter),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Dimens.gapSmall),
    ) {
        Icon(icon, contentDescription = null, tint = Haven.colors.textSecondary, modifier = Modifier.size(40.dp))
        Text(title, style = Haven.type.headline, color = Haven.colors.text)
        if (message != null) Text(message, style = Haven.type.footnote, color = Haven.colors.textSecondary)
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(Dimens.gapSmall))
            PrimaryButton(actionLabel, onAction)
        }
    }
}

/** One short line of context. Never a paragraph. */
@Composable
fun Hint(text: String, modifier: Modifier = Modifier, color: Color = Haven.colors.textSecondary) {
    Text(text, style = Haven.type.footnote, color = color, modifier = modifier.padding(horizontal = 4.dp))
}

@Composable
fun ConfirmDialog(
    title: String,
    text: String?,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = false,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Haven.colors.surface,
        title = { Text(title, style = Haven.type.headline, color = Haven.colors.text) },
        text = text?.let { body -> @Composable { Text(body, style = Haven.type.body, color = Haven.colors.textSecondary) } },
        confirmButton = {
            TextButton(onClick = { onConfirm(); onDismiss() }) {
                Text(confirmLabel, style = Haven.type.headline, color = if (destructive) Haven.colors.emergency else Haven.colors.accent)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", style = Haven.type.callout, color = Haven.colors.accent) }
        },
    )
}

@Composable
fun BigValue(value: String, unit: String?, caption: String?, modifier: Modifier = Modifier, color: Color = Haven.colors.text) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, style = Haven.type.numeral, color = color)
            if (unit != null) {
                Spacer(Modifier.width(6.dp))
                Text(unit, style = Haven.type.title, color = Haven.colors.textSecondary, modifier = Modifier.padding(bottom = 8.dp))
            }
        }
        if (caption != null) Text(caption, style = Haven.type.footnote, color = Haven.colors.textSecondary)
    }
}
