// SPDX-License-Identifier: GPL-3.0-or-later
package app.projecthaven.domain.guides

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Bundled guide collections. Ids match the asset file names under assets/guides/. */
enum class CollectionId(val key: String, val title: String) {
    FIRST_AID("first_aid", "First Aid"),
    SURVIVAL("survival", "Survival Handbook"),
    COOKBOOK("cookbook", "Emergency Cookbook");

    companion object {
        fun fromKey(key: String): CollectionId? = entries.firstOrNull { it.key == key }
    }
}

@Serializable
data class GuideCollection(
    val id: String,
    val title: String,
    val formatVersion: Int,
    val updated: String,
    val categories: List<GuideCategory>,
)

@Serializable
data class GuideCategory(
    val id: String,
    val title: String,
    val articles: List<Article>,
)

@Serializable
data class Article(
    val id: String,
    val title: String,
    val summary: String,
    val keywords: List<String> = emptyList(),
    val sections: List<ArticleSection>,
    val references: List<Reference> = emptyList(),
    /** ISO date (yyyy-MM-dd) the text was last changed. */
    val updated: String,
    val review: Review = Review(),
)

@Serializable
data class ArticleSection(
    val id: String,
    val title: String,
    val style: SectionStyle,
    val items: List<String>,
)

@Serializable
enum class SectionStyle {
    /** Ordered steps. */
    @SerialName("steps") STEPS,
    @SerialName("bullets") BULLETS,
    /** Items the user can tick off; progress is saved locally. */
    @SerialName("checklist") CHECKLIST,
    /** "Do not" items. */
    @SerialName("avoid") AVOID,
    /** Warning signs that need urgent help. */
    @SerialName("warning") WARNING,
    @SerialName("text") TEXT,
}

@Serializable
data class Reference(
    val title: String,
    val publisher: String,
    val url: String? = null,
)

/**
 * Content is only "reviewed" once a named reviewer has checked it against the references.
 * See docs/CONTENT_REVIEW.md.
 */
@Serializable
data class Review(
    val status: ReviewStatus = ReviewStatus.PENDING,
    val reviewedBy: String? = null,
    val reviewedOn: String? = null,
)

@Serializable
enum class ReviewStatus {
    @SerialName("pending") PENDING,
    @SerialName("reviewed") REVIEWED,
}

// ---- Cookbook --------------------------------------------------------------------------

@Serializable
data class Cookbook(
    val formatVersion: Int,
    val updated: String,
    val recipes: List<Recipe>,
    /** Food- and water-safety articles. */
    val safety: List<Article>,
)

@Serializable
enum class RecipeCategory(val title: String) {
    @SerialName("no_cook") NO_COOK("No-cook meals"),
    @SerialName("minimal") MINIMAL_EQUIPMENT("Minimal-equipment meals"),
}

@Serializable
enum class FuelUse(val label: String, val rank: Int) {
    @SerialName("none") NONE("No fuel", 0),
    @SerialName("low") LOW("Low fuel", 1),
    @SerialName("medium") MEDIUM("Medium fuel", 2),
}

@Serializable
data class Ingredient(val item: String, val amount: String)

@Serializable
data class Recipe(
    val id: String,
    val title: String,
    val category: RecipeCategory,
    val summary: String,
    val servings: Int,
    val prepMinutes: Int,
    val cookMinutes: Int,
    val equipment: List<String>,
    /** Approximate water needed for the whole recipe, including soaking and cooking. */
    val waterMl: Int,
    val fuel: FuelUse,
    val vegetarian: Boolean,
    /** Base pantry items used for "recipes by ingredient", e.g. rice, lentils, atta. */
    val baseIngredients: List<String>,
    val ingredients: List<Ingredient>,
    val steps: List<String>,
    val substitutions: List<String> = emptyList(),
    val safety: List<String> = emptyList(),
    val keywords: List<String> = emptyList(),
    val updated: String,
) {
    val totalMinutes: Int get() = prepMinutes + cookMinutes
}

enum class RecipeFilter(val label: String) {
    NO_COOKING("No cooking"),
    LIMITED_WATER("Limited water"),
    LIMITED_FUEL("Limited fuel"),
    VEGETARIAN("Vegetarian"),
}

object RecipeFilters {
    /** A recipe counts as "limited water" when the whole recipe needs at most this much. */
    const val LIMITED_WATER_ML = 500

    fun matches(recipe: Recipe, filters: Set<RecipeFilter>): Boolean = filters.all { filter ->
        when (filter) {
            RecipeFilter.NO_COOKING -> recipe.fuel == FuelUse.NONE
            RecipeFilter.LIMITED_WATER -> recipe.waterMl <= LIMITED_WATER_ML
            RecipeFilter.LIMITED_FUEL -> recipe.fuel.rank <= FuelUse.LOW.rank
            RecipeFilter.VEGETARIAN -> recipe.vegetarian
        }
    }

    fun apply(recipes: List<Recipe>, filters: Set<RecipeFilter>, baseIngredient: String? = null): List<Recipe> =
        recipes.filter { r ->
            matches(r, filters) &&
                (baseIngredient == null || r.baseIngredients.any { it.equals(baseIngredient, ignoreCase = true) })
        }
}

/** Stable key used for bookmarks: "first_aid/severe-bleeding", "recipe/sattu-drink". */
object GuideKeys {
    fun article(collection: CollectionId, articleId: String) = "${collection.key}/$articleId"
    fun recipe(recipeId: String) = "recipe/$recipeId"
    fun checklistItem(collection: CollectionId, articleId: String, sectionId: String, index: Int) =
        "${collection.key}/$articleId/$sectionId/$index"
}
