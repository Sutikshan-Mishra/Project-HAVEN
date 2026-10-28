// SPDX-License-Identifier: GPL-3.0-or-later
package app.projecthaven.domain.morse

/**
 * International Morse code (ITU-R M.1677-1) encoder.
 *
 * Text is normalised to upper case. Characters without an ITU code are reported in
 * [MorseEncoding.unsupported] and skipped rather than being silently replaced.
 */
object MorseCode {

    val table: Map<Char, String> = mapOf(
        'A' to ".-", 'B' to "-...", 'C' to "-.-.", 'D' to "-..", 'E' to ".",
        'F' to "..-.", 'G' to "--.", 'H' to "....", 'I' to "..", 'J' to ".---",
        'K' to "-.-", 'L' to ".-..", 'M' to "--", 'N' to "-.", 'O' to "---",
        'P' to ".--.", 'Q' to "--.-", 'R' to ".-.", 'S' to "...", 'T' to "-",
        'U' to "..-", 'V' to "...-", 'W' to ".--", 'X' to "-..-", 'Y' to "-.--",
        'Z' to "--..",
        '0' to "-----", '1' to ".----", '2' to "..---", '3' to "...--", '4' to "....-",
        '5' to ".....", '6' to "-....", '7' to "--...", '8' to "---..", '9' to "----.",
        '.' to ".-.-.-", ',' to "--..--", '?' to "..--..", '\'' to ".----.",
        '!' to "-.-.--", '/' to "-..-.", '(' to "-.--.", ')' to "-.--.-",
        '&' to ".-...", ':' to "---...", ';' to "-.-.-.", '=' to "-...-",
        '+' to ".-.-.", '-' to "-....-", '_' to "..--.-", '"' to ".-..-.",
        '@' to ".--.-.",
    )

    /** The distress signal is sent as one continuous prosign, without letter gaps. */
    const val SOS_PROSIGN: String = "...---..."

    fun encode(text: String): MorseEncoding {
        val unsupported = linkedSetOf<Char>()
        val words = text.trim()
            .split(Regex("\\s+"))
            .filter { it.isNotEmpty() }
            .map { word ->
                word.uppercase().mapNotNull { ch ->
                    table[ch] ?: run {
                        unsupported += ch
                        null
                    }
                }
            }
            .filter { it.isNotEmpty() }
        return MorseEncoding(words, unsupported)
    }

    /** SOS as a single prosign word, suitable for repeating with word gaps between repeats. */
    fun sos(): MorseEncoding = MorseEncoding(listOf(listOf(SOS_PROSIGN)), emptySet())
}

data class MorseEncoding(
    /** Words, each a list of letter codes made of '.' and '-'. */
    val words: List<List<String>>,
    val unsupported: Set<Char>,
) {
    val isEmpty: Boolean get() = words.isEmpty()

    /** Human-readable form: letters separated by spaces, words by " / ". */
    fun display(): String = words.joinToString(" / ") { it.joinToString(" ") }
}

/**
 * Timing in Morse "units". The standard is dot = 1, dash = 3, gap inside a letter = 1,
 * gap between letters = 3 and gap between words = 7. Letter and word gaps may be stretched
 * (Farnsworth-style spacing) to make signals easier to read by eye.
 */
data class MorseTiming(
    val wordsPerMinute: Int = 12,
    val letterGapUnits: Int = STANDARD_LETTER_GAP,
    val wordGapUnits: Int = STANDARD_WORD_GAP,
) {
    init {
        require(wordsPerMinute in MIN_WPM..MAX_WPM) { "Speed must be $MIN_WPM-$MAX_WPM WPM" }
        require(letterGapUnits >= STANDARD_LETTER_GAP) { "Letter gap must be at least 3 units" }
        require(wordGapUnits >= STANDARD_WORD_GAP) { "Word gap must be at least 7 units" }
        require(wordGapUnits > letterGapUnits) { "Word gap must be longer than letter gap" }
    }

    /** PARIS standard: a word of 50 units sent [wordsPerMinute] times a minute. */
    val unitMillis: Long get() = 1200L / wordsPerMinute

    companion object {
        const val MIN_WPM = 3
        const val MAX_WPM = 30
        const val STANDARD_LETTER_GAP = 3
        const val STANDARD_WORD_GAP = 7
    }
}

/** One step of a transmission: light/tone [on] or off for [units] Morse units. */
data class MorsePulse(val on: Boolean, val units: Int)

object MorseScheduler {

    fun pulses(encoding: MorseEncoding, timing: MorseTiming): List<MorsePulse> {
        val out = ArrayList<MorsePulse>()
        encoding.words.forEachIndexed { wordIndex, word ->
            if (wordIndex > 0) out += MorsePulse(false, timing.wordGapUnits)
            word.forEachIndexed { letterIndex, code ->
                if (letterIndex > 0) out += MorsePulse(false, timing.letterGapUnits)
                code.forEachIndexed { symbolIndex, symbol ->
                    if (symbolIndex > 0) out += MorsePulse(false, 1)
                    out += when (symbol) {
                        '.' -> MorsePulse(true, 1)
                        '-' -> MorsePulse(true, 3)
                        else -> throw IllegalArgumentException("Invalid Morse symbol '$symbol'")
                    }
                }
            }
        }
        return out
    }

    fun totalUnits(pulses: List<MorsePulse>): Int = pulses.sumOf { it.units }

    fun durationMillis(pulses: List<MorsePulse>, timing: MorseTiming): Long =
        totalUnits(pulses) * timing.unitMillis
}
