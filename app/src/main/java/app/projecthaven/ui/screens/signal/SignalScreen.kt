// SPDX-License-Identifier: GPL-3.0-or-later
package app.projecthaven.ui.screens.signal

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.FlashOff
import androidx.compose.material.icons.rounded.FlashOn
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.projecthaven.data.settings.MorsePrefs
import app.projecthaven.data.signal.MorseOutput
import app.projecthaven.data.signal.SignalMode
import app.projecthaven.domain.morse.MorseCode
import app.projecthaven.domain.morse.MorseTiming
import app.projecthaven.ui.LocalAppContainer
import app.projecthaven.ui.components.EmergencyButton
import app.projecthaven.ui.components.HavenCard
import app.projecthaven.ui.components.HavenChip
import app.projecthaven.ui.components.HavenScreen
import app.projecthaven.ui.components.HavenSwitch
import app.projecthaven.ui.components.HavenTextField
import app.projecthaven.ui.components.Hint
import app.projecthaven.ui.components.KeepScreenOn
import app.projecthaven.ui.components.ListRow
import app.projecthaven.ui.components.LocalShowMessage
import app.projecthaven.ui.components.PrimaryButton
import app.projecthaven.ui.components.SecondaryButton
import app.projecthaven.ui.components.SectionLabel
import app.projecthaven.ui.theme.ControlShape
import app.projecthaven.ui.theme.Dimens
import app.projecthaven.ui.theme.Haven
import kotlinx.coroutines.launch

@Composable
fun SignalScreen(back: () -> Unit) {
    val container = LocalAppContainer.current
    val signal = container.signal
    val show = LocalShowMessage.current
    val scope = rememberCoroutineScope()
    val state by signal.state.collectAsStateWithLifecycle()
    val torchOn by signal.torchOn.collectAsStateWithLifecycle()
    val morsePrefs by container.settings.morse.collectAsStateWithLifecycle(initialValue = MorsePrefs(10, 3, 7))
    var text by remember { mutableStateOf("SOS") }
    var outputs by remember { mutableStateOf(setOf(MorseOutput.FLASH)) }
    var repeat by remember { mutableStateOf(true) }
    var showTiming by remember { mutableStateOf(false) }
    val encoding = remember(text) { MorseCode.encode(text) }
    val active = state.mode !is SignalMode.Idle

    // Signals stop when the user leaves this screen, so nothing keeps flashing unnoticed.
    DisposableEffect(Unit) { onDispose { signal.stop() } }
    if (active) KeepScreenOn()
    LaunchedEffect(state.message) { state.message?.let { show(it); signal.clearMessage() } }

    HavenScreen("Signal & Light", onBack = back, largeTitle = false) {
        AnimatedVisibility(visible = active) {
            EmergencyButton("Stop", onClick = { signal.stop() }, icon = Icons.Rounded.Stop, modifier = Modifier.fillMaxWidth())
        }

        SectionLabel("Light")
        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.gap)) {
            val torchActive = state.mode == SignalMode.Torch && torchOn
            HavenCard(
                modifier = Modifier.weight(1f).height(110.dp).semantics { contentDescription = if (torchActive) "Flashlight on. Tap to turn off" else "Flashlight off. Tap to turn on" },
                onClick = { if (signal.torchAvailable) signal.toggleTorch() else show("This device has no camera flash") },
                color = if (torchActive) Haven.colors.accent else Haven.colors.surface,
            ) {
                androidx.compose.material3.Icon(
                    if (torchActive) Icons.Rounded.FlashOn else Icons.Rounded.FlashOff, contentDescription = null,
                    tint = if (torchActive) Haven.colors.onAccent else Haven.colors.accent, modifier = Modifier.size(28.dp),
                )
                Spacer(Modifier.weight(1f))
                Text("Flashlight", style = Haven.type.headline, color = if (torchActive) Haven.colors.onAccent else Haven.colors.text)
                Text(if (!signal.torchAvailable) "No flash" else if (torchActive) "On" else "Off", style = Haven.type.footnote,
                    color = if (torchActive) Haven.colors.onAccent else Haven.colors.textSecondary)
            }
            HavenCard(
                modifier = Modifier.weight(1f).height(110.dp),
                onClick = { signal.startScreenLight() },
            ) {
                androidx.compose.material3.Icon(Icons.Rounded.Lightbulb, contentDescription = null, tint = Haven.colors.accent, modifier = Modifier.size(28.dp))
                Spacer(Modifier.weight(1f))
                Text("Screen light", style = Haven.type.headline, color = Haven.colors.text)
                Text("Full white screen", style = Haven.type.footnote, color = Haven.colors.textSecondary)
            }
        }

        SectionLabel("Morse code")
        HavenTextField(text, { text = it.take(120) }, placeholder = "Message", singleLine = true)
        HavenCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Lamp(lit = state.lit && state.mode is SignalMode.Morse)
                Spacer(Modifier.width(12.dp))
                Text(
                    if (encoding.isEmpty) "Nothing to send" else encoding.display(),
                    style = Haven.type.callout, color = Haven.colors.text, modifier = Modifier.weight(1f),
                )
            }
            if (encoding.unsupported.isNotEmpty()) {
                Hint("Skipped: ${encoding.unsupported.joinToString(" ")}", color = Haven.colors.attention)
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Dimens.gapSmall), verticalArrangement = Arrangement.spacedBy(Dimens.gapSmall)) {
            MorseOutput.entries.forEach { o ->
                HavenChip(o.label, selected = o in outputs, onClick = {
                    outputs = if (o in outputs) outputs - o else outputs + o
                })
            }
        }
        ListRow("Repeat", trailing = { HavenSwitch(repeat) { repeat = it } })
        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.gapSmall), modifier = Modifier.fillMaxWidth()) {
            PrimaryButton("Send", modifier = Modifier.weight(1f), enabled = !encoding.isEmpty && outputs.isNotEmpty(), onClick = {
                if (MorseOutput.FLASH in outputs && !signal.torchAvailable) show("No camera flash. Using the screen instead.")
                signal.startMorse(text, encoding, morsePrefs.timing(), outputs, repeat)
            })
            SecondaryButton("Preview", modifier = Modifier.weight(1f), enabled = !encoding.isEmpty, onClick = {
                signal.startMorse(text, encoding, morsePrefs.timing(), outputs, repeat = false, preview = true)
            })
        }
        EmergencyButton("Send SOS", icon = Icons.Rounded.FlashOn, modifier = Modifier.fillMaxWidth(), onClick = {
            signal.startMorse("SOS", MorseCode.sos(), morsePrefs.timing(), outputs.ifEmpty { setOf(MorseOutput.FLASH) }, repeat = true)
        })
        TextButton(onClick = { showTiming = !showTiming }) {
            Text(if (showTiming) "Hide timing" else "Timing · ${morsePrefs.wpm} WPM", style = Haven.type.callout, color = Haven.colors.accent)
        }
        AnimatedVisibility(visible = showTiming) {
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.gapSmall)) {
                TimingSlider("Speed", "${morsePrefs.wpm} WPM", morsePrefs.wpm, 5..25) { v ->
                    scope.launch { container.settings.setMorse(morsePrefs.copy(wpm = v)) }
                }
                TimingSlider("Letter gap", "${morsePrefs.letterGap} units", morsePrefs.letterGap, 3..9) { v ->
                    val word = maxOf(morsePrefs.wordGap, v + 1, MorseTiming.STANDARD_WORD_GAP)
                    scope.launch { container.settings.setMorse(morsePrefs.copy(letterGap = v, wordGap = word)) }
                }
                TimingSlider("Word gap", "${morsePrefs.wordGap} units", morsePrefs.wordGap, 7..21) { v ->
                    if (v > morsePrefs.letterGap) scope.launch { container.settings.setMorse(morsePrefs.copy(wordGap = v)) }
                }
                Hint("Longer gaps are easier to read by eye. Standard is 3 and 7.")
            }
        }

        SectionLabel("Alarm")
        val volume = remember(state.mode) { signal.alarmVolumePercent() }
        if (state.mode == SignalMode.Siren) {
            EmergencyButton("Stop siren", icon = Icons.Rounded.Stop, modifier = Modifier.fillMaxWidth(), onClick = { signal.stop() })
        } else {
            PrimaryButton("Sound siren", icon = Icons.Rounded.Campaign, modifier = Modifier.fillMaxWidth(), onClick = { signal.startSiren() })
        }
        Hint(
            "Loudness depends on alarm volume" + (volume?.let { " (now $it%)" } ?: "") + " and your speaker.",
            color = if ((volume ?: 100) < 50) Haven.colors.attention else Haven.colors.textSecondary,
        )
    }
}

@Composable
private fun Lamp(lit: Boolean) {
    Box(
        Modifier
            .size(18.dp)
            .clip(CircleShape)
            .background(if (lit) Haven.colors.accent else Haven.colors.surfaceRaised),
    )
}

@Composable
private fun TimingSlider(label: String, value: String, current: Int, range: IntRange, onChange: (Int) -> Unit) {
    Column(Modifier.fillMaxWidth().clip(ControlShape).background(Haven.colors.surface)) {
        Row(Modifier.fillMaxWidth()) {
            ListRow(label, value = value)
        }
        Slider(
            value = current.toFloat(),
            onValueChange = { onChange(it.toInt()) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            steps = (range.last - range.first - 1).coerceAtLeast(0),
            colors = SliderDefaults.colors(
                thumbColor = Haven.colors.accent,
                activeTrackColor = Haven.colors.accent,
                inactiveTrackColor = Haven.colors.surfaceRaised,
            ),
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = "$label, $value" },
        )
    }
}
