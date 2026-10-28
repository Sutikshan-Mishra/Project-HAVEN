// SPDX-License-Identifier: GPL-3.0-or-later
package app.projecthaven.ui.screens.guides

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.Calculate
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.projecthaven.domain.guides.ArticleSection
import app.projecthaven.domain.guides.CollectionId
import app.projecthaven.domain.guides.FuelUse
import app.projecthaven.domain.guides.GuideKeys
import app.projecthaven.domain.guides.GuideSearch
import app.projecthaven.domain.guides.Recipe
import app.projecthaven.domain.guides.RecipeCategory
import app.projecthaven.domain.guides.RecipeFilter
import app.projecthaven.domain.guides.RecipeFilters
import app.projecthaven.domain.guides.SectionStyle
import app.projecthaven.domain.ration.RationInput
import app.projecthaven.domain.ration.RationPlanner
import app.projecthaven.domain.ration.RationResult
import app.projecthaven.domain.ration.StapleAmount
import app.projecthaven.ui.LocalAppContainer
import app.projecthaven.ui.components.EmptyState
import app.projecthaven.ui.components.GroupDivider
import app.projecthaven.ui.components.HavenChip
import app.projecthaven.ui.components.HavenScreen
import app.projecthaven.ui.components.HavenTextField
import app.projecthaven.ui.components.Hint
import app.projecthaven.ui.components.ListGroup
import app.projecthaven.ui.components.ListRow
import app.projecthaven.ui.components.LocalShowMessage
import app.projecthaven.ui.components.SearchField
import app.projecthaven.ui.components.SectionLabel
import app.projecthaven.ui.navigation.Routes
import app.projecthaven.ui.theme.Dimens
import app.projecthaven.ui.theme.Haven
import kotlinx.coroutines.launch
import java.util.Locale

private fun Recipe.meta(): String = buildList {
    add(if (totalMinutes > 0) "$totalMinutes min" else "No waiting")
    add(fuel.label)
    if (vegetarian) add("Veg")
}.joinToString(" · ")

@Composable
fun CookbookScreen(back: () -> Unit, go: (String) -> Unit) {
    val lib = rememberLibrary()?.getOrNull()
    var query by rememberSaveable { mutableStateOf("") }
    var filters by remember { mutableStateOf(setOf<RecipeFilter>()) }
    var base by remember { mutableStateOf<String?>(null) }
    HavenScreen("Emergency Cookbook", onBack = back, largeTitle = true) {
        if (lib == null) return@HavenScreen
        val cookbook = lib.cookbook
        SearchField(query, { query = it }, placeholder = "Search recipes")
        if (query.isNotBlank()) {
            val hits = remember(query) { GuideSearch.searchCookbook(cookbook, query) }
            if (hits.isEmpty()) EmptyState(Icons.Rounded.Restaurant, "No results")
            else ListGroup {
                hits.forEachIndexed { i, h ->
                    if (i > 0) GroupDivider(inset = 16.dp)
                    ListRow(h.title, subtitle = h.subtitle, chevron = true, onClick = { go(routeFor(h)) })
                }
            }
            return@HavenScreen
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Dimens.gapSmall), verticalArrangement = Arrangement.spacedBy(Dimens.gapSmall)) {
            RecipeFilter.entries.forEach { f ->
                HavenChip(f.label, selected = f in filters, onClick = { filters = if (f in filters) filters - f else filters + f })
            }
        }
        val bases = remember(cookbook) { cookbook.recipes.flatMap { it.baseIngredients }.distinct().sorted() }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Dimens.gapSmall)) {
            HavenChip("Any ingredient", selected = base == null, onClick = { base = null })
            bases.forEach { b -> HavenChip(b.replaceFirstChar { it.uppercase() }, selected = base == b, onClick = { base = if (base == b) null else b }) }
        }
        val shown = RecipeFilters.apply(cookbook.recipes, filters, base)
        if (shown.isEmpty()) {
            EmptyState(Icons.Rounded.Restaurant, "No recipes match", "Remove a filter to see more.")
        }
        RecipeCategory.entries.forEach { category ->
            val inCategory = shown.filter { it.category == category }
            if (inCategory.isNotEmpty()) {
                SectionLabel(category.title)
                ListGroup {
                    inCategory.forEachIndexed { i, r ->
                        if (i > 0) GroupDivider(inset = 16.dp)
                        ListRow(r.title, subtitle = r.meta(), chevron = true, onClick = { go(Routes.recipe(r.id)) })
                    }
                }
            }
        }
        SectionLabel("Planning")
        ListGroup {
            ListRow("Ration and water planner", icon = Icons.Rounded.Calculate, chevron = true, onClick = { go(Routes.PLANNER) })
        }
        SectionLabel("Food and water safety")
        ListGroup {
            cookbook.safety.forEachIndexed { i, a ->
                if (i > 0) GroupDivider(inset = 16.dp)
                ListRow(a.title, subtitle = a.summary, chevron = true, onClick = { go(Routes.article(CollectionId.COOKBOOK.key, a.id)) })
            }
        }
    }
}

@Composable
fun RecipeScreen(id: String, back: () -> Unit) {
    val container = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    val show = LocalShowMessage.current
    val lib = rememberLibrary()?.getOrNull()
    val recipe = lib?.recipe(id)
    val bookmarks by container.settings.bookmarks.collectAsStateWithLifecycle(initialValue = emptySet())
    val key = GuideKeys.recipe(id)
    val saved = key in bookmarks
    HavenScreen(recipe?.title ?: "Recipe", onBack = back, largeTitle = true, actions = {
        if (recipe != null) IconButton(onClick = {
            scope.launch { container.settings.setBookmarked(key, !saved); show(if (saved) "Removed from saved" else "Saved") }
        }) {
            Icon(if (saved) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder, contentDescription = if (saved) "Remove from saved" else "Save", tint = Haven.colors.accent)
        }
    }) {
        if (lib == null) return@HavenScreen
        if (recipe == null) { EmptyState(Icons.Rounded.Restaurant, "Recipe not found"); return@HavenScreen }
        Text(recipe.summary, style = Haven.type.body, color = Haven.colors.textSecondary)
        ListGroup {
            ListRow("Serves", value = recipe.servings.toString())
            GroupDivider(inset = 16.dp)
            ListRow("Time", value = "${recipe.prepMinutes} min prep" + if (recipe.cookMinutes > 0) " · ${recipe.cookMinutes} min cooking" else "")
            GroupDivider(inset = 16.dp)
            ListRow("Water", value = if (recipe.waterMl == 0) "None" else "About ${recipe.waterMl} mL")
            GroupDivider(inset = 16.dp)
            ListRow("Fuel", value = recipe.fuel.label, valueColor = if (recipe.fuel == FuelUse.NONE) Haven.colors.ready else Haven.colors.textSecondary)
            GroupDivider(inset = 16.dp)
            ListRow("Equipment", subtitle = recipe.equipment.joinToString(", "))
        }
        SectionView(ArticleSection("ingredients", "Ingredients", SectionStyle.BULLETS, recipe.ingredients.map { "${it.amount} ${it.item}" }))
        SectionView(ArticleSection("steps", "Method", SectionStyle.STEPS, recipe.steps))
        if (recipe.substitutions.isNotEmpty()) SectionView(ArticleSection("subs", "Substitutions", SectionStyle.BULLETS, recipe.substitutions))
        if (recipe.safety.isNotEmpty()) SectionView(ArticleSection("safety", "Food safety", SectionStyle.WARNING, recipe.safety))
        Hint("Updated ${recipe.updated}")
    }
}

@Composable
fun PlannerScreen(back: () -> Unit) {
    var people by rememberSaveable { mutableStateOf("4") }
    var days by rememberSaveable { mutableStateOf("3") }
    var meals by rememberSaveable { mutableStateOf("2") }
    var water by rememberSaveable { mutableStateOf("3") }
    var stored by rememberSaveable { mutableStateOf("") }
    var fuelMeals by rememberSaveable { mutableStateOf("") }
    var rice by rememberSaveable { mutableStateOf("75") }
    var dal by rememberSaveable { mutableStateOf("30") }
    var atta by rememberSaveable { mutableStateOf("0") }

    fun num(s: String) = s.replace(',', '.').toDoubleOrNull()
    val input = RationInput(
        people = num(people)?.toInt() ?: 0,
        days = num(days)?.toInt() ?: 0,
        mealsPerDay = num(meals)?.toInt() ?: 0,
        waterLitresPerPersonPerDay = num(water) ?: 0.0,
        staples = listOf(
            StapleAmount("Rice", num(rice) ?: 0.0),
            StapleAmount("Dal", num(dal) ?: 0.0),
            StapleAmount("Atta", num(atta) ?: 0.0),
        ).filter { it.gramsPerPersonPerMeal > 0 },
        waterAvailableLitres = num(stored),
        cookedMealsFuelCanCover = num(fuelMeals)?.toInt(),
    )
    val result = RationPlanner.plan(input)
    val numeric = KeyboardOptions(keyboardType = KeyboardType.Decimal)

    HavenScreen("Ration planner", onBack = back, largeTitle = false) {
        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.gapSmall)) {
            HavenTextField(people, { people = it.take(4) }, "4", Modifier.weight(1f), label = "People", keyboardOptions = numeric)
            HavenTextField(days, { days = it.take(3) }, "3", Modifier.weight(1f), label = "Days", keyboardOptions = numeric)
            HavenTextField(meals, { meals = it.take(1) }, "2", Modifier.weight(1f), label = "Meals/day", keyboardOptions = numeric)
        }
        HavenTextField(water, { water = it.take(5) }, "3", label = "Drinking and cooking water per person per day (L)", keyboardOptions = numeric)
        SectionLabel("Dry staples per person per meal (g)")
        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.gapSmall)) {
            HavenTextField(rice, { rice = it.take(4) }, "0", Modifier.weight(1f), label = "Rice", keyboardOptions = numeric)
            HavenTextField(dal, { dal = it.take(4) }, "0", Modifier.weight(1f), label = "Dal", keyboardOptions = numeric)
            HavenTextField(atta, { atta = it.take(4) }, "0", Modifier.weight(1f), label = "Atta", keyboardOptions = numeric)
        }
        SectionLabel("What you have (optional)")
        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.gapSmall)) {
            HavenTextField(stored, { stored = it.take(6) }, "—", Modifier.weight(1f), label = "Water stored (L)", keyboardOptions = numeric)
            HavenTextField(fuelMeals, { fuelMeals = it.take(4) }, "—", Modifier.weight(1f), label = "Cooked meals fuel covers", keyboardOptions = numeric)
        }

        when (result) {
            is RationResult.Invalid -> Hint(result.reason, color = Haven.colors.attention)
            is RationResult.Ok -> {
                val p = result.plan
                SectionLabel("You need")
                ListGroup {
                    ListRow("Meals", value = p.totalMeals.toString())
                    GroupDivider(inset = 16.dp)
                    ListRow("Water", value = String.format(Locale.ROOT, "%.0f L", p.waterNeededLitres))
                    p.staples.forEach { s ->
                        GroupDivider(inset = 16.dp)
                        ListRow(s.name, value = if (s.totalGrams >= 1000) String.format(Locale.ROOT, "%.1f kg", s.totalGrams / 1000) else "${s.totalGrams.toInt()} g")
                    }
                }
                if (p.waterDaysCovered != null || p.fuelDaysCovered != null) {
                    SectionLabel("Your supplies last")
                    ListGroup {
                        p.waterDaysCovered?.let {
                            ListRow("Water", value = if (it == 1) "1 day" else "$it days",
                                valueColor = if (it < input.days) Haven.colors.emergency else Haven.colors.ready)
                        }
                        if (p.waterDaysCovered != null && p.fuelDaysCovered != null) GroupDivider(inset = 16.dp)
                        p.fuelDaysCovered?.let {
                            ListRow("Cooked meals", value = if (it == 1) "1 day" else "$it days",
                                valueColor = if (it < input.days) Haven.colors.attention else Haven.colors.ready)
                        }
                    }
                    p.noCookMealsNeeded?.takeIf { it > 0 }?.let { Hint("Plan $it no-cook meals for when fuel runs out.") }
                }
                Hint("Planning figures only. Needs are higher in heat, illness, pregnancy and for heavy work.")
            }
        }
    }
}
