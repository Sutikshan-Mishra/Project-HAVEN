// SPDX-License-Identifier: GPL-3.0-or-later
package app.projecthaven.data.signal

import app.projecthaven.data.device.TonePlayer
import app.projecthaven.data.device.TorchController
import app.projecthaven.domain.morse.MorseEncoding
import app.projecthaven.domain.morse.MorseScheduler
import app.projecthaven.domain.morse.MorseTiming
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class MorseOutput(val label: String) { FLASH("Flash"), SOUND("Sound"), SCREEN("Screen") }

sealed interface SignalMode {
    data object Idle : SignalMode
    data object Torch : SignalMode
    data class Morse(val text: String, val outputs: Set<MorseOutput>, val repeat: Boolean, val preview: Boolean) : SignalMode
    data object Siren : SignalMode
    /** Steady full-brightness white screen. */
    data object ScreenLight : SignalMode
}

data class SignalState(
    val mode: SignalMode = SignalMode.Idle,
    /** Whether the current Morse element is "on" (drives screen flash and preview lamp). */
    val lit: Boolean = false,
    val message: String? = null,
)

/**
 * Single owner of the torch, tone and screen-flash outputs. Starting any signal stops the
 * previous one first, so two features can never fight over the flash. [stop] takes effect
 * immediately and always leaves the torch off and the speaker silent.
 */
class SignalController(
    private val torch: TorchController,
    private val tones: TonePlayer,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var job: Job? = null

    private val _state = MutableStateFlow(SignalState())
    val state: StateFlow<SignalState> = _state.asStateFlow()

    val torchAvailable: Boolean get() = torch.available
    val torchOn: StateFlow<Boolean> get() = torch.isOn

    fun alarmVolumePercent(): Int? = tones.alarmVolumePercent()

    fun toggleTorch() {
        if (_state.value.mode == SignalMode.Torch) { stop(); return }
        stop()
        if (!torch.set(true)) {
            _state.value = SignalState(message = "Flashlight is unavailable. Another app may be using the camera.")
            return
        }
        _state.value = SignalState(mode = SignalMode.Torch)
    }

    fun startScreenLight() {
        stop()
        _state.value = SignalState(mode = SignalMode.ScreenLight, lit = true)
    }

    fun startSiren() {
        stop()
        if (!tones.prepareSiren()) {
            _state.value = SignalState(message = "Audio output is unavailable.")
            return
        }
        tones.play(onInterrupted = { scope.launch { stop("Alarm stopped by another audio source, such as a call.") } })
        _state.value = SignalState(mode = SignalMode.Siren)
    }

    fun startMorse(text: String, encoding: MorseEncoding, timing: MorseTiming, outputs: Set<MorseOutput>, repeat: Boolean, preview: Boolean = false) {
        stop()
        if (encoding.isEmpty) return
        val useFlash = !preview && MorseOutput.FLASH in outputs && torch.available
        val useSound = !preview && MorseOutput.SOUND in outputs && tones.prepareMorseTone()
        val effectiveOutputs = buildSet {
            if (useFlash) add(MorseOutput.FLASH)
            if (useSound) add(MorseOutput.SOUND)
            if (!preview && (MorseOutput.SCREEN in outputs || (!useFlash && !useSound))) add(MorseOutput.SCREEN)
        }
        val pulses = MorseScheduler.pulses(encoding, timing)
        _state.value = SignalState(mode = SignalMode.Morse(text, effectiveOutputs, repeat, preview))
        job = scope.launch {
            try {
                do {
                    for (p in pulses) {
                        if (!isActive) break
                        setElement(p.on, useFlash, useSound)
                        delay(p.units * timing.unitMillis)
                    }
                    setElement(false, useFlash, useSound)
                    if (repeat && isActive) delay(timing.wordGapUnits * timing.unitMillis)
                } while (repeat && isActive)
            } finally {
                withContext(NonCancellable) {
                    if (useFlash) torch.set(false)
                    if (useSound) tones.release()
                }
            }
            _state.value = SignalState()
        }
    }

    private fun setElement(on: Boolean, flash: Boolean, sound: Boolean) {
        if (flash && !torch.set(on)) {
            // Camera taken by another app mid-transmission: stop cleanly and say why.
            stop("Flashlight became unavailable.")
            return
        }
        if (sound) { if (on) tones.play() else tones.pause() }
        _state.update { it.copy(lit = on) }
    }

    fun clearMessage() { _state.update { it.copy(message = null) } }

    fun stop(message: String? = null) {
        job?.cancel()
        job = null
        if (torch.isOn.value || _state.value.mode == SignalMode.Torch) torch.set(false)
        tones.release()
        _state.value = SignalState(message = message)
    }
}
