// SPDX-License-Identifier: GPL-3.0-or-later
package app.projecthaven.domain

import app.projecthaven.domain.calc.CalcResult
import app.projecthaven.domain.calc.Calculator
import app.projecthaven.domain.units.UnitCategory
import app.projecthaven.domain.units.UnitConverter
import app.projecthaven.domain.units.Units
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class UnitConverterTest {
    private fun c(v: Double, from: String, to: String) = UnitConverter.convert(v, Units.byId(from), Units.byId(to))

    @Test fun length() {
        assertEquals(2.54, c(1.0, "in", "cm"), 1e-12)
        assertEquals(1.0, c(0.3048, "m", "ft"), 1e-12)
        assertEquals(36.0, c(1.0, "yd", "in"), 1e-9)
    }

    @Test fun distance() {
        assertEquals(1.609344, c(1.0, "mi", "km"), 1e-12)
        assertEquals(1.852, c(1.0, "nmi", "km"), 1e-12)
    }

    @Test fun mass() {
        assertEquals(2.20462262, c(1.0, "kg", "lb"), 1e-8)
        assertEquals(16.0, c(1.0, "lb", "oz"), 1e-9)
    }

    @Test fun volume() {
        assertEquals(3.785411784, c(1.0, "gal_us", "l"), 1e-12)
        assertEquals(1000.0, c(1.0, "l", "ml"), 1e-9)
        assertEquals(8.0, c(1.0, "cup_us", "floz_us"), 1e-9)
    }

    @Test fun temperature() {
        assertEquals(212.0, c(100.0, "c", "f"), 1e-9)
        assertEquals(-40.0, c(-40.0, "f", "c"), 1e-9)
        assertEquals(273.15, c(0.0, "c", "k"), 1e-9)
        assertEquals(98.6, c(37.0, "c", "f"), 1e-9)
    }

    @Test fun speed() {
        assertEquals(100.0, c(27.7777777778, "mps", "kmh"), 1e-6)
        assertEquals(1.609344, c(1.0, "mph", "kmh"), 1e-9)
        assertEquals(1.852, c(1.0, "kn", "kmh"), 1e-9)
    }

    @Test fun roundTripEveryUnit() {
        for (category in UnitCategory.entries) {
            val units = Units.inCategory(category)
            assertTrue(units.size >= 3)
            for (a in units) for (b in units) {
                val there = UnitConverter.convert(12.5, a, b)
                assertEquals("$a->$b", 12.5, UnitConverter.convert(there, b, a), 1e-9)
            }
        }
    }

    @Test fun crossCategoryIsRejected() {
        assertThrows(IllegalArgumentException::class.java) { c(1.0, "kg", "m") }
    }

    @Test fun physicalValidity() {
        assertFalse(UnitConverter.isPhysicallyValid(-300.0, Units.byId("c")))
        assertTrue(UnitConverter.isPhysicallyValid(-273.15, Units.byId("c")))
        assertFalse(UnitConverter.isPhysicallyValid(-1.0, Units.byId("kg")))
    }

    @Test fun formatting() {
        assertEquals("2.54", UnitConverter.format(2.54))
        assertEquals("0.3333333", UnitConverter.format(1.0 / 3.0))
        assertEquals("100", UnitConverter.format(100.0))
        assertEquals("—", UnitConverter.format(Double.NaN))
    }
}

class CalculatorTest {
    private fun eval(s: String): String = when (val r = Calculator.evaluate(s)) {
        is CalcResult.Value -> Calculator.format(r.value)
        is CalcResult.Error -> "ERR:" + r.message
    }

    @Test fun precedenceAndParentheses() {
        assertEquals("14", eval("2+3×4"))
        assertEquals("20", eval("(2+3)×4"))
        assertEquals("2", eval("10-4-4"))
        assertEquals("1", eval("8÷4÷2"))
    }

    @Test fun decimalsAreExact() {
        assertEquals("0.3", eval("0.1+0.2"))
        assertEquals("0.333333333333", eval("1÷3"))
    }

    @Test fun unaryMinusAndPercent() {
        assertEquals("-5", eval("-5"))
        assertEquals("3", eval("-(-3)"))
        assertEquals("15", eval("50%×30"))
        assertEquals("-6", eval("2×-3"))
    }

    @Test fun errors() {
        assertEquals("ERR:" + Calculator.ERROR_DIVIDE_BY_ZERO, eval("5÷0"))
        assertEquals("ERR:" + Calculator.ERROR_DIVIDE_BY_ZERO, eval("5÷(2-2)"))
        assertEquals("ERR:" + Calculator.ERROR_INVALID, eval("2+"))
        assertEquals("ERR:" + Calculator.ERROR_INVALID, eval("(2+3"))
        assertEquals("ERR:" + Calculator.ERROR_INVALID, eval("1.2.3"))
        assertEquals("ERR:" + Calculator.ERROR_INVALID, eval("2)"))
        assertEquals("ERR:" + Calculator.ERROR_EMPTY, eval(""))
    }

    @Test fun largeAndSmallNumbersUseExponent() {
        assertEquals("1E20", Calculator.format(BigDecimal("100000000000000000000")))
        assertEquals("1.5E-10", Calculator.format(BigDecimal("0.00000000015")))
        assertEquals("123456789", eval("123456789"))
    }
}
