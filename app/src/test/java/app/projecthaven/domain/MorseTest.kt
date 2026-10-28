// SPDX-License-Identifier: GPL-3.0-or-later
package app.projecthaven.domain

import app.projecthaven.domain.morse.MorseCode
import app.projecthaven.domain.morse.MorsePulse
import app.projecthaven.domain.morse.MorseScheduler
import app.projecthaven.domain.morse.MorseTiming
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

class MorseTest {

    @Test
    fun encodesLettersAndDigitsWithDistinctCodes() {
        val enc = MorseCode.encode("SOS 112")
        assertEquals(listOf(listOf("...", "---", "..."), listOf(".----", ".----", "..---")), enc.words)
        assertEquals("... --- ... / .---- .---- ..---", enc.display())
        // Every table entry is unique, so no two characters share a pattern.
        assertEquals(MorseCode.table.size, MorseCode.table.values.toSet().size)
    }

    @Test
    fun lowercaseAndExtraSpacesAreNormalised() {
        assertEquals(MorseCode.encode("HELP ME").words, MorseCode.encode("  help   me ").words)
    }

    @Test
    fun unsupportedCharactersAreReportedNotReplaced() {
        val enc = MorseCode.encode("A#B")
        assertEquals(listOf(listOf(".-", "-...")), enc.words)
        assertEquals(setOf('#'), enc.unsupported)
    }

    @Test
    fun emptyTextHasNoWords() {
        assertTrue(MorseCode.encode("   ").isEmpty)
        assertTrue(MorseCode.encode("###").isEmpty)
    }

    @Test
    fun parisIsFiftyUnitsIncludingTrailingWordGap() {
        val timing = MorseTiming()
        val pulses = MorseScheduler.pulses(MorseCode.encode("PARIS"), timing)
        assertEquals(43, MorseScheduler.totalUnits(pulses))
        assertEquals(50, MorseScheduler.totalUnits(pulses) + timing.wordGapUnits)
    }

    @Test
    fun unitLengthFollowsParisStandard() {
        assertEquals(100L, MorseTiming(wordsPerMinute = 12).unitMillis)
        assertEquals(60L, MorseTiming(wordsPerMinute = 20).unitMillis)
        val pulses = MorseScheduler.pulses(MorseCode.encode("E"), MorseTiming(wordsPerMinute = 12))
        assertEquals(100L, MorseScheduler.durationMillis(pulses, MorseTiming(wordsPerMinute = 12)))
    }

    @Test
    fun pulseSequenceForSosLettersUsesStandardGaps() {
        val pulses = MorseScheduler.pulses(MorseCode.encode("SO"), MorseTiming())
        val expected = listOf(
            MorsePulse(true, 1), MorsePulse(false, 1), MorsePulse(true, 1), MorsePulse(false, 1), MorsePulse(true, 1),
            MorsePulse(false, 3),
            MorsePulse(true, 3), MorsePulse(false, 1), MorsePulse(true, 3), MorsePulse(false, 1), MorsePulse(true, 3),
        )
        assertEquals(expected, pulses)
    }

    @Test
    fun sosProsignHasNoLetterGaps() {
        val pulses = MorseScheduler.pulses(MorseCode.sos(), MorseTiming())
        assertFalse(pulses.any { !it.on && it.units > 1 })
        assertEquals(9, pulses.count { it.on })
        // 3 dots + 3 dashes + 3 dots = 3 + 9 + 3 on-units, plus 8 one-unit gaps
        assertEquals(23, MorseScheduler.totalUnits(pulses))
    }

    @Test
    fun customLetterAndWordGapsAreApplied() {
        val timing = MorseTiming(wordsPerMinute = 10, letterGapUnits = 5, wordGapUnits = 12)
        val pulses = MorseScheduler.pulses(MorseCode.encode("E E"), timing)
        assertEquals(listOf(MorsePulse(true, 1), MorsePulse(false, 12), MorsePulse(true, 1)), pulses)
        val two = MorseScheduler.pulses(MorseCode.encode("EE"), timing)
        assertEquals(listOf(MorsePulse(true, 1), MorsePulse(false, 5), MorsePulse(true, 1)), two)
    }

    @Test
    fun invalidTimingIsRejected() {
        assertThrows(IllegalArgumentException::class.java) { MorseTiming(wordsPerMinute = 0) }
        assertThrows(IllegalArgumentException::class.java) { MorseTiming(letterGapUnits = 2) }
        assertThrows(IllegalArgumentException::class.java) { MorseTiming(wordGapUnits = 6) }
        assertThrows(IllegalArgumentException::class.java) { MorseTiming(letterGapUnits = 8, wordGapUnits = 8) }
    }
}
