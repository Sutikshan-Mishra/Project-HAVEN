// SPDX-License-Identifier: GPL-3.0-or-later
package app.projecthaven.domain.calc

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

sealed interface CalcResult {
    data class Value(val value: BigDecimal) : CalcResult
    data class Error(val message: String) : CalcResult
}

/**
 * Decimal calculator for + − × ÷, parentheses, unary minus and postfix percent (x% = x/100).
 * Uses [BigDecimal] so that e.g. 0.1 + 0.2 = 0.3 exactly.
 */
object Calculator {

    private val mc = MathContext(34, RoundingMode.HALF_EVEN)

    const val ERROR_DIVIDE_BY_ZERO = "Can't divide by zero"
    const val ERROR_INVALID = "Invalid expression"
    const val ERROR_EMPTY = "Enter a calculation"

    fun evaluate(expression: String): CalcResult {
        val normalized = expression
            .replace('×', '*').replace('÷', '/').replace('−', '-')
            .replace(" ", "")
        if (normalized.isEmpty()) return CalcResult.Error(ERROR_EMPTY)
        return try {
            val parser = Parser(normalized)
            val value = parser.parseExpression()
            if (!parser.atEnd()) CalcResult.Error(ERROR_INVALID) else CalcResult.Value(value)
        } catch (e: DivideByZero) {
            CalcResult.Error(ERROR_DIVIDE_BY_ZERO)
        } catch (e: ParseError) {
            CalcResult.Error(ERROR_INVALID)
        } catch (e: ArithmeticException) {
            CalcResult.Error(ERROR_INVALID)
        }
    }

    /** Display form: up to 12 significant digits, plain notation for ordinary magnitudes. */
    fun format(value: BigDecimal): String {
        if (value.signum() == 0) return "0"
        val rounded = value.round(MathContext(12, RoundingMode.HALF_EVEN)).stripTrailingZeros()
        val abs = rounded.abs()
        return if (abs >= BigDecimal("1e15") || abs < BigDecimal("1e-9")) {
            val unscaled = rounded.unscaledValue().abs().toString()
            val exponent = unscaled.length - 1 - rounded.scale()
            val mantissa = if (unscaled.length > 1) unscaled[0] + "." + unscaled.substring(1) else unscaled
            (if (rounded.signum() < 0) "-" else "") + mantissa.trimEnd('0').trimEnd('.') + "E" + exponent
        } else {
            rounded.toPlainString()
        }
    }

    private class ParseError : Exception()
    private class DivideByZero : Exception()

    private class Parser(private val s: String) {
        private var pos = 0

        fun atEnd() = pos >= s.length

        fun parseExpression(): BigDecimal {
            var left = parseTerm()
            while (!atEnd()) {
                left = when (s[pos]) {
                    '+' -> { pos++; left.add(parseTerm(), mc) }
                    '-' -> { pos++; left.subtract(parseTerm(), mc) }
                    else -> return left
                }
            }
            return left
        }

        private fun parseTerm(): BigDecimal {
            var left = parseUnary()
            while (!atEnd()) {
                left = when (s[pos]) {
                    '*' -> { pos++; left.multiply(parseUnary(), mc) }
                    '/' -> {
                        pos++
                        val right = parseUnary()
                        if (right.signum() == 0) throw DivideByZero()
                        left.divide(right, mc)
                    }
                    else -> return left
                }
            }
            return left
        }

        private fun parseUnary(): BigDecimal {
            if (!atEnd() && s[pos] == '-') { pos++; return parseUnary().negate() }
            if (!atEnd() && s[pos] == '+') { pos++; return parseUnary() }
            return parsePostfix()
        }

        private fun parsePostfix(): BigDecimal {
            var value = parsePrimary()
            while (!atEnd() && s[pos] == '%') {
                pos++
                value = value.divide(BigDecimal(100), mc)
            }
            return value
        }

        private fun parsePrimary(): BigDecimal {
            if (atEnd()) throw ParseError()
            if (s[pos] == '(') {
                pos++
                val inner = parseExpression()
                if (atEnd() || s[pos] != ')') throw ParseError()
                pos++
                return inner
            }
            val start = pos
            var dots = 0
            while (!atEnd() && (s[pos].isDigit() || s[pos] == '.')) {
                if (s[pos] == '.') dots++
                pos++
            }
            val token = s.substring(start, pos)
            if (token.isEmpty() || token == "." || dots > 1) throw ParseError()
            return BigDecimal(token)
        }
    }
}
