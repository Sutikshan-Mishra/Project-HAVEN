// SPDX-License-Identifier: GPL-3.0-or-later
package app.projecthaven.domain.units

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

enum class UnitCategory(val label: String) {
    LENGTH("Length"),
    DISTANCE("Distance"),
    MASS("Mass"),
    VOLUME("Volume"),
    TEMPERATURE("Temperature"),
    SPEED("Speed"),
}

/**
 * A unit expressed through conversion to and from its category's base unit
 * (metre, kilogram, litre, degree Celsius, metre per second).
 */
class MeasureUnit internal constructor(
    val id: String,
    val label: String,
    val symbol: String,
    val category: UnitCategory,
    val toBase: (Double) -> Double,
    val fromBase: (Double) -> Double,
) {
    override fun toString(): String = id
}

object Units {
    private fun linear(id: String, label: String, symbol: String, category: UnitCategory, factor: Double) =
        MeasureUnit(id, label, symbol, category, { it * factor }, { it / factor })

    // Exact definitions: inch = 25.4 mm, foot = 0.3048 m, yard = 0.9144 m,
    // international mile = 1609.344 m, nautical mile = 1852 m, pound = 0.45359237 kg,
    // US gallon = 3.785411784 L, imperial gallon = 4.54609 L.
    val all: List<MeasureUnit> = listOf(
        linear("mm", "Millimetre", "mm", UnitCategory.LENGTH, 0.001),
        linear("cm", "Centimetre", "cm", UnitCategory.LENGTH, 0.01),
        linear("m", "Metre", "m", UnitCategory.LENGTH, 1.0),
        linear("in", "Inch", "in", UnitCategory.LENGTH, 0.0254),
        linear("ft", "Foot", "ft", UnitCategory.LENGTH, 0.3048),
        linear("yd", "Yard", "yd", UnitCategory.LENGTH, 0.9144),

        linear("dist_m", "Metre", "m", UnitCategory.DISTANCE, 1.0),
        linear("km", "Kilometre", "km", UnitCategory.DISTANCE, 1000.0),
        linear("mi", "Mile", "mi", UnitCategory.DISTANCE, 1609.344),
        linear("nmi", "Nautical mile", "nmi", UnitCategory.DISTANCE, 1852.0),

        linear("g", "Gram", "g", UnitCategory.MASS, 0.001),
        linear("kg", "Kilogram", "kg", UnitCategory.MASS, 1.0),
        linear("t", "Tonne", "t", UnitCategory.MASS, 1000.0),
        linear("oz", "Ounce", "oz", UnitCategory.MASS, 0.028349523125),
        linear("lb", "Pound", "lb", UnitCategory.MASS, 0.45359237),

        linear("ml", "Millilitre", "mL", UnitCategory.VOLUME, 0.001),
        linear("l", "Litre", "L", UnitCategory.VOLUME, 1.0),
        linear("m3", "Cubic metre", "m³", UnitCategory.VOLUME, 1000.0),
        linear("cup_us", "Cup (US)", "cup", UnitCategory.VOLUME, 0.2365882365),
        linear("floz_us", "Fluid ounce (US)", "fl oz", UnitCategory.VOLUME, 0.0295735295625),
        linear("gal_us", "Gallon (US)", "gal", UnitCategory.VOLUME, 3.785411784),
        linear("gal_uk", "Gallon (UK)", "gal", UnitCategory.VOLUME, 4.54609),

        MeasureUnit("c", "Celsius", "°C", UnitCategory.TEMPERATURE, { it }, { it }),
        MeasureUnit("f", "Fahrenheit", "°F", UnitCategory.TEMPERATURE,
            { (it - 32.0) * 5.0 / 9.0 }, { it * 9.0 / 5.0 + 32.0 }),
        MeasureUnit("k", "Kelvin", "K", UnitCategory.TEMPERATURE,
            { it - 273.15 }, { it + 273.15 }),

        linear("mps", "Metres per second", "m/s", UnitCategory.SPEED, 1.0),
        linear("kmh", "Kilometres per hour", "km/h", UnitCategory.SPEED, 1.0 / 3.6),
        linear("mph", "Miles per hour", "mph", UnitCategory.SPEED, 0.44704),
        linear("kn", "Knot", "kn", UnitCategory.SPEED, 1852.0 / 3600.0),
    )

    private val byId = all.associateBy { it.id }

    fun byId(id: String): MeasureUnit = byId[id] ?: throw IllegalArgumentException("Unknown unit $id")

    fun inCategory(category: UnitCategory): List<MeasureUnit> = all.filter { it.category == category }
}

object UnitConverter {

    fun convert(value: Double, from: MeasureUnit, to: MeasureUnit): Double {
        require(from.category == to.category) { "Cannot convert ${from.category} to ${to.category}" }
        if (from === to) return value
        return to.fromBase(from.toBase(value))
    }

    /** Absolute zero checks: temperatures below 0 K are physically impossible. */
    fun isPhysicallyValid(value: Double, unit: MeasureUnit): Boolean = when (unit.category) {
        UnitCategory.TEMPERATURE -> unit.toBase(value) >= -273.15 - 1e-9
        UnitCategory.LENGTH, UnitCategory.DISTANCE, UnitCategory.MASS,
        UnitCategory.VOLUME -> value >= 0.0
        UnitCategory.SPEED -> true
    }

    /** Formats to [significant] significant digits without trailing zeros or exponent noise. */
    fun format(value: Double, significant: Int = 7): String {
        if (value.isNaN() || value.isInfinite()) return "—"
        if (value == 0.0) return "0"
        val rounded = BigDecimal(value).round(MathContext(significant, RoundingMode.HALF_EVEN))
        val abs = rounded.abs()
        return if (abs >= BigDecimal("1e12") || abs < BigDecimal("1e-6")) {
            String.format(java.util.Locale.ROOT, "%.${significant - 1}e", value)
        } else {
            rounded.stripTrailingZeros().toPlainString()
        }
    }
}
