// SPDX-License-Identifier: GPL-3.0-or-later
package app.projecthaven.domain.ration

import kotlin.math.floor

/**
 * Simple arithmetic for emergency supply planning. All per-person amounts are user-editable
 * planning inputs, not dietary prescriptions.
 */
data class RationInput(
    val people: Int,
    val days: Int,
    val mealsPerDay: Int = 2,
    /** Drinking and cooking water per person per day, in litres. */
    val waterLitresPerPersonPerDay: Double = DEFAULT_WATER_L,
    /** Dry quantity per person per meal, in grams, for each staple being planned. */
    val staples: List<StapleAmount> = emptyList(),
    /** Water already stored, in litres (optional). */
    val waterAvailableLitres: Double? = null,
    /** How many cooked meals the available fuel can cover (optional, user's estimate). */
    val cookedMealsFuelCanCover: Int? = null,
) {
    companion object {
        /** Sphere Handbook survival needs for drinking and food: roughly 2.5–3 L per person per day. */
        const val DEFAULT_WATER_L = 3.0
        const val MAX_PEOPLE = 500
        const val MAX_DAYS = 365
    }
}

data class StapleAmount(val name: String, val gramsPerPersonPerMeal: Double)

data class StapleTotal(val name: String, val totalGrams: Double)

data class RationPlan(
    val totalMeals: Int,
    val waterNeededLitres: Double,
    val staples: List<StapleTotal>,
    /** Whole days the stored water covers, when water available is given. */
    val waterDaysCovered: Int?,
    /** Days of cooked meals the fuel covers, when given. */
    val fuelDaysCovered: Int?,
    /** Meals that must be no-cook because fuel runs out first. */
    val noCookMealsNeeded: Int?,
)

sealed interface RationResult {
    data class Ok(val plan: RationPlan) : RationResult
    data class Invalid(val reason: String) : RationResult
}

object RationPlanner {

    fun plan(input: RationInput): RationResult {
        if (input.people !in 1..RationInput.MAX_PEOPLE) return RationResult.Invalid("People must be 1–${RationInput.MAX_PEOPLE}")
        if (input.days !in 1..RationInput.MAX_DAYS) return RationResult.Invalid("Days must be 1–${RationInput.MAX_DAYS}")
        if (input.mealsPerDay !in 1..6) return RationResult.Invalid("Meals per day must be 1–6")
        if (input.waterLitresPerPersonPerDay <= 0.0 || input.waterLitresPerPersonPerDay > 50.0)
            return RationResult.Invalid("Water per person must be more than 0 and at most 50 L")
        if (input.staples.any { it.gramsPerPersonPerMeal < 0.0 || it.gramsPerPersonPerMeal > 2000.0 })
            return RationResult.Invalid("Staple amounts must be 0–2000 g per meal")
        if ((input.waterAvailableLitres ?: 0.0) < 0.0) return RationResult.Invalid("Stored water cannot be negative")
        if ((input.cookedMealsFuelCanCover ?: 0) < 0) return RationResult.Invalid("Fuel estimate cannot be negative")

        val totalMeals = input.people * input.days * input.mealsPerDay
        val dailyWater = input.people * input.waterLitresPerPersonPerDay
        val waterNeeded = dailyWater * input.days
        val staples = input.staples.map { StapleTotal(it.name, it.gramsPerPersonPerMeal * totalMeals) }

        val waterDays = input.waterAvailableLitres?.let { floor(it / dailyWater).toInt() }

        val cookedMealsPerDay = input.mealsPerDay // each meal for the whole group cooked together
        val fuelDays = input.cookedMealsFuelCanCover?.let { it / cookedMealsPerDay }
        val noCookMeals = input.cookedMealsFuelCanCover?.let {
            val groupMeals = input.days * input.mealsPerDay
            (groupMeals - it).coerceAtLeast(0) * input.people
        }

        return RationResult.Ok(
            RationPlan(
                totalMeals = totalMeals,
                waterNeededLitres = waterNeeded,
                staples = staples,
                waterDaysCovered = waterDays,
                fuelDaysCovered = fuelDays,
                noCookMealsNeeded = noCookMeals,
            ),
        )
    }

    /** Scales a recipe's servings to a different number of people. */
    fun scaleFactor(recipeServings: Int, people: Int): Double {
        require(recipeServings > 0 && people > 0)
        return people.toDouble() / recipeServings
    }
}
