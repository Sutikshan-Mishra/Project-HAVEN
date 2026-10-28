// SPDX-License-Identifier: GPL-3.0-or-later
package app.projecthaven.ui.screens.tools

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.projecthaven.data.device.BatteryInfo
import app.projecthaven.domain.calc.CalcResult
import app.projecthaven.domain.calc.Calculator
import app.projecthaven.domain.units.MeasureUnit
import app.projecthaven.domain.units.UnitCategory
import app.projecthaven.domain.units.UnitConverter
import app.projecthaven.domain.units.Units
import app.projecthaven.ui.LocalAppContainer
import app.projecthaven.ui.components.BigValue
import app.projecthaven.ui.components.GroupDivider
import app.projecthaven.ui.components.HavenCard
import app.projecthaven.ui.components.HavenChip
import app.projecthaven.ui.components.HavenHeader
import app.projecthaven.ui.components.HavenScreen
import app.projecthaven.ui.components.HavenTextField
import app.projecthaven.ui.components.Hint
import app.projecthaven.ui.components.ListGroup
import app.projecthaven.ui.components.ListRow
import app.projecthaven.ui.theme.ControlShape
import app.projecthaven.ui.theme.Dimens
import app.projecthaven.ui.theme.Haven

// ---- Unit converter ------------------------------------------------------------------------

@Composable
fun ConverterScreen(back: () -> Unit) {
    var category by remember { mutableStateOf(UnitCategory.LENGTH) }
    val units = remember(category) { Units.inCategory(category) }
    var from by remember(category) { mutableStateOf(units[0]) }
    var to by remember(category) { mutableStateOf(units[1]) }
    var input by remember { mutableStateOf("1") }
    val value = input.replace(',', '.').toDoubleOrNull()

    HavenScreen("Unit converter", onBack = back, largeTitle = false) {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Dimens.gapSmall)) {
            UnitCategory.entries.forEach { c -> HavenChip(c.label, selected = c == category, onClick = { category = c }) }
        }
        HavenTextField(
            input, { input = it.take(24) }, placeholder = "Value", label = "Value",
            keyboardOptions = KeyboardOptions(keyboardType = if (category == UnitCategory.TEMPERATURE) KeyboardType.Text else KeyboardType.Decimal),
            isError = input.isNotBlank() && value == null,
            supportingText = if (input.isNotBlank() && value == null) "Enter a number" else null,
        )
        ListGroup {
            UnitPicker("From", from, units) { from = it }
            GroupDivider(inset = 16.dp)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                IconButton(onClick = { val t = from; from = to; to = t }) {
                    Icon(Icons.Rounded.SwapVert, contentDescription = "Swap units", tint = Haven.colors.accent)
                }
            }
            GroupDivider(inset = 16.dp)
            UnitPicker("To", to, units) { to = it }
        }
        HavenCard(Modifier.fillMaxWidth()) {
            val result = when {
                value == null -> "—"
                !UnitConverter.isPhysicallyValid(value, from) -> "Not possible"
                else -> UnitConverter.format(UnitConverter.convert(value, from, to))
            }
            BigValue(result, to.symbol, "${input.ifBlank { "—" }} ${from.symbol}", Modifier.align(Alignment.CenterHorizontally))
        }
    }
}

@Composable
private fun UnitPicker(label: String, selected: MeasureUnit, units: List<MeasureUnit>, onSelect: (MeasureUnit) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        ListRow(label, value = "${selected.label} (${selected.symbol})", valueColor = Haven.colors.accent, onClick = { open = true })
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, modifier = Modifier.background(Haven.colors.surface)) {
            units.forEach { u ->
                DropdownMenuItem(
                    text = { Text("${u.label} (${u.symbol})", style = Haven.type.body, color = Haven.colors.text) },
                    onClick = { onSelect(u); open = false },
                )
            }
        }
    }
}

// ---- Calculator ----------------------------------------------------------------------------

@Composable
fun CalculatorScreen(back: () -> Unit) {
    var expr by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    fun press(key: String) {
        error = null
        when (key) {
            "C" -> { expr = ""; result = null }
            "⌫" -> expr = expr.dropLast(1)
            "=" -> when (val r = Calculator.evaluate(expr)) {
                is CalcResult.Value -> { result = Calculator.format(r.value); expr = result!! }
                is CalcResult.Error -> error = r.message
            }
            else -> {
                if (result != null && expr == result && key.first().isDigit()) expr = ""
                result = null
                if (expr.length < 64) expr += key
            }
        }
    }

    Column(Modifier.fillMaxSize().background(Haven.colors.background)) {
        HavenHeader("Calculator", onBack = back, largeTitle = false)
        Column(
            Modifier.weight(1f).fillMaxWidth().padding(Dimens.gutter),
            verticalArrangement = Arrangement.Bottom,
            horizontalAlignment = Alignment.End,
        ) {
            Text(
                expr.ifEmpty { "0" }, style = Haven.type.numeral, color = Haven.colors.text, maxLines = 2,
                overflow = TextOverflow.Ellipsis, textAlign = TextAlign.End,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
            if (error != null) Text(error!!, style = Haven.type.callout, color = Haven.colors.emergency)
        }
        val rows = listOf(
            listOf("C", "(", ")", "÷"),
            listOf("7", "8", "9", "×"),
            listOf("4", "5", "6", "−"),
            listOf("1", "2", "3", "+"),
            listOf("%", "0", ".", "⌫"),
        )
        Column(Modifier.padding(Dimens.gutter), verticalArrangement = Arrangement.spacedBy(Dimens.gapSmall)) {
            rows.forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(Dimens.gapSmall)) {
                    row.forEach { k -> Key(k, Modifier.weight(1f), operator = k in setOf("÷", "×", "−", "+")) { press(k) } }
                }
            }
            Key("=", Modifier.fillMaxWidth(), operator = true, wide = true) { press("=") }
        }
    }
}

@Composable
private fun Key(label: String, modifier: Modifier, operator: Boolean, wide: Boolean = false, onClick: () -> Unit) {
    val description = when (label) {
        "⌫" -> "Delete"; "C" -> "Clear"; "÷" -> "Divide"; "×" -> "Multiply"; "−" -> "Minus"; "+" -> "Plus"; "=" -> "Equals"; "%" -> "Percent"
        else -> label
    }
    Box(
        modifier
            .let { if (wide) it.heightIn(min = 56.dp) else it.aspectRatio(1.4f) }
            .clip(ControlShape)
            .background(if (operator) Haven.colors.accent else Haven.colors.surface)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = Haven.type.title, color = if (operator) Haven.colors.onAccent else Haven.colors.text)
    }
}

// ---- Battery -------------------------------------------------------------------------------

@Composable
fun BatteryScreen(back: () -> Unit) {
    val container = LocalAppContainer.current
    val info by remember(container) { container.battery.updates() }.collectAsStateWithLifecycle(initialValue = null as BatteryInfo?)
    HavenScreen("Battery", onBack = back, largeTitle = false) {
        val b = info
        if (b == null) {
            Hint("Reading battery…")
            return@HavenScreen
        }
        HavenCard(Modifier.fillMaxWidth()) {
            BigValue(b.percent?.toString() ?: "—", if (b.percent != null) "%" else null,
                if (b.charging) "Charging" + (b.plugged?.let { " · $it" } ?: "") else "On battery",
                Modifier.align(Alignment.CenterHorizontally),
                color = when { b.percent == null -> Haven.colors.text; b.percent <= 15 && !b.charging -> Haven.colors.emergency; else -> Haven.colors.text })
        }
        val rows = buildList {
            add("Battery saver" to if (b.powerSaveMode) "On" else "Off")
            b.temperatureC?.let { add("Temperature" to "%.1f °C".format(it)) }
            b.voltageMv?.let { add("Voltage" to "%.2f V".format(it / 1000f)) }
            b.health?.let { add("Health" to it) }
            b.technology?.let { add("Technology" to it) }
        }
        ListGroup {
            rows.forEachIndexed { i, (k, v) ->
                if (i > 0) GroupDivider(inset = 16.dp)
                ListRow(k, value = v)
            }
        }
        Hint("Only values your device reports are shown.")
    }
}
