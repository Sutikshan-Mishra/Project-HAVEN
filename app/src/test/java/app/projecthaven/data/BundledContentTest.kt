// SPDX-License-Identifier: GPL-3.0-or-later
package app.projecthaven.data

import app.projecthaven.data.guides.GuideRepository
import app.projecthaven.data.guides.GuideSource
import app.projecthaven.domain.guides.CollectionId
import app.projecthaven.domain.guides.RecipeFilter
import app.projecthaven.domain.guides.RecipeFilters
import app.projecthaven.domain.guides.ReviewStatus
import app.projecthaven.domain.guides.SectionStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Parses the real guide files shipped in the APK, from disk, with no network. This is the
 * offline guarantee for bundled content: if these files parse, the app can show them.
 */
class BundledContentTest {

    private val assets = File("src/main/assets")
    private val library = GuideRepository(GuideSource { name -> File(assets, name).readText() }).parse()

    @Test fun allCollectionsParseAndHaveContent() {
        assertTrue(library.firstAid.categories.sumOf { it.articles.size } >= 25)
        assertTrue(library.survival.categories.sumOf { it.articles.size } >= 25)
        assertTrue(library.cookbook.recipes.size >= 15)
        assertTrue(library.cookbook.safety.isNotEmpty())
    }

    @Test fun firstAidArticlesFollowTheRequiredStructure() {
        val required = listOf("recognise", "now", "avoid", "warning", "help", "limits")
        library.firstAid.categories.flatMap { it.articles }.forEach { a ->
            val ids = a.sections.map { it.id }
            assertTrue("${a.id} is missing sections", ids.containsAll(required))
            assertEquals("${a.id}: immediate actions must be numbered steps", SectionStyle.STEPS, a.sections.first { it.id == "now" }.style)
            assertTrue("${a.id} needs references", a.references.isNotEmpty())
            assertTrue("${a.id} must say when to call 112", a.sections.any { s -> s.items.any { "112" in it } })
        }
    }

    @Test fun nothingClaimsReviewWithoutAReviewer() {
        val all = library.firstAid.categories.flatMap { it.articles } +
            library.survival.categories.flatMap { it.articles } + library.cookbook.safety
        all.forEach { a ->
            if (a.review.status == ReviewStatus.REVIEWED) {
                assertFalse("${a.id} marked reviewed without reviewer", a.review.reviewedBy.isNullOrBlank())
            }
        }
    }

    @Test fun articlesAreFindableBySearch() {
        assertEquals("burns", library.searchAll("scald").first().id)
        assertEquals("cpr", library.searchAll("chest compressions").first().id)
        assertTrue(library.searchAll("boil water").any { it.id == "water-treatment" })
        assertTrue(library.searchAll("khichdi").any { it.isRecipe && it.id == "khichdi" })
        assertTrue(library.searchAll("zzzz-not-a-word").isEmpty())
    }

    @Test fun everyCookbookFilterHasRecipes() {
        RecipeFilter.entries.forEach { f ->
            assertTrue("no recipes for ${f.label}", RecipeFilters.apply(library.cookbook.recipes, setOf(f)).isNotEmpty())
        }
    }

    @Test fun articleLookupByCollection() {
        assertEquals("Thermal burns", library.article(CollectionId.FIRST_AID, "burns")?.title)
        assertEquals("Spoilage warnings", library.article(CollectionId.COOKBOOK, "spoilage")?.title)
        assertTrue(library.article(CollectionId.SURVIVAL, "burns") == null)
    }
}
