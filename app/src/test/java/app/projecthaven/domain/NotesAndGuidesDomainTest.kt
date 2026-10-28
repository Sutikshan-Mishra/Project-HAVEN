// SPDX-License-Identifier: GPL-3.0-or-later
package app.projecthaven.domain

import app.projecthaven.domain.geo.Coordinate
import app.projecthaven.domain.guides.Article
import app.projecthaven.domain.guides.ArticleSection
import app.projecthaven.domain.guides.CollectionId
import app.projecthaven.domain.guides.Cookbook
import app.projecthaven.domain.guides.FuelUse
import app.projecthaven.domain.guides.GuideCategory
import app.projecthaven.domain.guides.GuideCollection
import app.projecthaven.domain.guides.GuideSearch
import app.projecthaven.domain.guides.Ingredient
import app.projecthaven.domain.guides.Recipe
import app.projecthaven.domain.guides.RecipeCategory
import app.projecthaven.domain.guides.RecipeFilter
import app.projecthaven.domain.guides.RecipeFilters
import app.projecthaven.domain.guides.SectionStyle
import app.projecthaven.domain.notes.ImportCheck
import app.projecthaven.domain.notes.Note
import app.projecthaven.domain.notes.NoteDto
import app.projecthaven.domain.notes.NoteEditing
import app.projecthaven.domain.notes.NoteFilter
import app.projecthaven.domain.notes.NoteQuery
import app.projecthaven.domain.notes.NoteSort
import app.projecthaven.domain.notes.NoteTag
import app.projecthaven.domain.notes.NotesBackup
import app.projecthaven.domain.notes.NotesBackupRules
import app.projecthaven.domain.notes.toDto
import app.projecthaven.domain.ration.RationInput
import app.projecthaven.domain.ration.RationPlanner
import app.projecthaven.domain.ration.RationResult
import app.projecthaven.domain.ration.StapleAmount
import app.projecthaven.domain.sos.CheckIn
import app.projecthaven.domain.sos.CheckInStatus
import app.projecthaven.domain.sos.EmergencyMessage
import app.projecthaven.domain.sos.LocationForMessage
import app.projecthaven.domain.sos.PhoneNumbers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class NoteQueryTest {
    private fun note(id: Long, title: String, body: String = "", created: Long = id, updated: Long = id,
                     pinned: Boolean = false, fav: Boolean = false, tag: NoteTag? = null) =
        Note(id, "00000000-0000-0000-0000-00000000000$id", title, body, tag, pinned, fav, created, updated)

    private val notes = listOf(
        note(1, "Water points", "Hand pump near temple", created = 1, updated = 50, tag = NoteTag.LOCATION),
        note(2, "Supplies", "rice 5 kg, dal 2 kg", created = 2, updated = 10, fav = true, tag = NoteTag.SUPPLIES),
        note(3, "Route home", "via NH27", created = 3, updated = 20, pinned = true, tag = NoteTag.ROUTE),
    )

    @Test fun pinnedFirstThenSortKey() {
        assertEquals(listOf(3L, 1L, 2L), NoteQuery.apply(notes, NoteFilter(sort = NoteSort.MODIFIED)).map { it.id })
        assertEquals(listOf(3L, 2L, 1L), NoteQuery.apply(notes, NoteFilter(sort = NoteSort.CREATED)).map { it.id })
    }

    @Test fun searchTitleAndBodyCaseInsensitive() {
        assertEquals(listOf(1L), NoteQuery.apply(notes, NoteFilter(query = "PUMP")).map { it.id })
        assertEquals(listOf(2L), NoteQuery.apply(notes, NoteFilter(query = "rice dal")).map { it.id })
        assertTrue(NoteQuery.apply(notes, NoteFilter(query = "rice nh27")).isEmpty())
    }

    @Test fun tagAndFavouriteFilters() {
        assertEquals(listOf(3L), NoteQuery.apply(notes, NoteFilter(tag = NoteTag.ROUTE)).map { it.id })
        assertEquals(listOf(2L), NoteQuery.apply(notes, NoteFilter(favouritesOnly = true)).map { it.id })
    }

    @Test fun displayTitleFallsBackToBody() {
        assertEquals("first line", note(9, "", "first line\nsecond").displayTitle)
        assertEquals("Untitled note", note(9, " ", " ").displayTitle)
    }
}

class NoteEditingTest {
    private val base = Note(1, "00000000-0000-0000-0000-000000000001", "t", "b", createdAt = 1, updatedAt = 1)

    @Test fun emptyNewNoteIsNotSaved() {
        assertEquals(NoteEditing.SaveAction.Nothing, NoteEditing.decide(true, null, base.copy(title = "", body = "  ")))
        assertEquals(NoteEditing.SaveAction.Insert, NoteEditing.decide(true, null, base))
    }

    @Test fun unchangedExistingNoteIsNotRewritten() {
        assertEquals(NoteEditing.SaveAction.Nothing, NoteEditing.decide(false, base, base.copy(updatedAt = 99)))
        assertEquals(NoteEditing.SaveAction.Update, NoteEditing.decide(false, base, base.copy(body = "changed")))
        assertEquals(NoteEditing.SaveAction.Update, NoteEditing.decide(false, base, base.copy(pinned = true)))
        assertEquals(NoteEditing.SaveAction.UpdateEmpty, NoteEditing.decide(false, base, base.copy(title = "", body = "")))
    }
}

class NotesBackupRulesTest {
    private val u1 = "11111111-1111-4111-8111-111111111111"
    private val u2 = "22222222-2222-4222-8222-222222222222"
    private fun dto(uuid: String, title: String = "Note") = NoteDto(uuid, title, "body", "SUPPLIES", createdAt = 10, updatedAt = 20)
    private fun backup(vararg n: NoteDto) = NotesBackup(exportedAt = 1, notes = n.toList())

    @Test fun duplicatesAndInvalidNotesAreSkipped() {
        val b = backup(dto(u1), dto(u1), dto(u2), dto("not-a-uuid"), NoteDto(u2.replace('2', '3'), "", "", createdAt = 1, updatedAt = 1))
        val plan = (NotesBackupRules.plan(b, existingUuids = emptySet()) as ImportCheck.Ok).plan
        assertEquals(listOf(u1, u2), plan.toInsert.map { it.uuid })
        assertEquals(1, plan.duplicates)
        assertEquals(2, plan.invalid)
        assertEquals(NoteTag.SUPPLIES, plan.toInsert.first().tag)
    }

    @Test fun existingNotesAreNeverOverwritten() {
        val plan = (NotesBackupRules.plan(backup(dto(u1), dto(u2)), setOf(u1.uppercase())) as ImportCheck.Ok).plan
        assertEquals(listOf(u2), plan.toInsert.map { it.uuid })
        assertEquals(1, plan.duplicates)
    }

    @Test fun wrongFormatOrNewerVersionIsRejected() {
        assertTrue(NotesBackupRules.plan(backup(dto(u1)).copy(format = "other"), emptySet()) is ImportCheck.Rejected)
        assertTrue(NotesBackupRules.plan(backup(dto(u1)).copy(formatVersion = 2), emptySet()) is ImportCheck.Rejected)
    }

    @Test fun locationMustBePairedAndInRange() {
        assertFalse(NotesBackupRules.isValid(dto(u1).copy(latitude = 10.0)))
        assertFalse(NotesBackupRules.isValid(dto(u1).copy(latitude = 100.0, longitude = 10.0)))
        assertTrue(NotesBackupRules.isValid(dto(u1).copy(latitude = 26.8, longitude = 80.9)))
        assertFalse(NotesBackupRules.isValid(dto(u1).copy(tag = "UNKNOWN")))
    }

    @Test fun exportRoundTrip() {
        val note = Note(5, u1, "T", "B", NoteTag.ROUTE, true, false, 100, 200, 26.0, 80.0, 5f)
        val plan = (NotesBackupRules.plan(backup(note.toDto()), emptySet()) as ImportCheck.Ok).plan
        assertEquals(note.copy(id = 0), plan.toInsert.single())
    }
}

class GuideSearchTest {
    private fun article(id: String, title: String, keywords: List<String>, body: String) = Article(
        id = id, title = title, summary = "summary", keywords = keywords,
        sections = listOf(ArticleSection("steps", "Do this", SectionStyle.STEPS, listOf(body))),
        updated = "2026-10-28",
    )

    private val guide = GuideCollection("first_aid", "First Aid", 1, "2026-10-28", listOf(
        GuideCategory("bleeding", "Bleeding and wounds", listOf(
            article("severe-bleeding", "Severe bleeding", listOf("haemorrhage", "blood"), "Press firmly on the wound"),
            article("nosebleed", "Nosebleeds", listOf("nose"), "Lean forward and pinch the soft part"),
        )),
        GuideCategory("burns", "Burns and injuries", listOf(
            article("burns", "Thermal burns", listOf("scald"), "Cool the burn under cool running water for 20 minutes"),
        )),
    ))

    @Test fun titleMatchesRankAboveBodyMatches() {
        val hits = GuideSearch.searchArticles(CollectionId.FIRST_AID, guide, "bleeding")
        assertEquals("severe-bleeding", hits.first().id)
    }

    @Test fun keywordsAndBodyAreSearchedAndAllTokensRequired() {
        assertEquals(listOf("burns"), GuideSearch.searchArticles(CollectionId.FIRST_AID, guide, "scald").map { it.id })
        assertEquals(listOf("burns"), GuideSearch.searchArticles(CollectionId.FIRST_AID, guide, "running water").map { it.id })
        assertTrue(GuideSearch.searchArticles(CollectionId.FIRST_AID, guide, "running nose").isEmpty())
        assertTrue(GuideSearch.searchArticles(CollectionId.FIRST_AID, guide, "  ").isEmpty())
    }

    @Test fun accentsAndCaseAreIgnored() {
        assertEquals("nosebleed", GuideSearch.searchArticles(CollectionId.FIRST_AID, guide, "NÓSE").first().id)
    }

    private fun recipe(id: String, fuel: FuelUse, water: Int, veg: Boolean, base: List<String>) = Recipe(
        id, id, if (fuel == FuelUse.NONE) RecipeCategory.NO_COOK else RecipeCategory.MINIMAL_EQUIPMENT,
        "s", 2, 5, 10, listOf("bowl"), water, fuel, veg, base, listOf(Ingredient("x", "1")), listOf("step"), updated = "2026-10-28",
    )

    @Test fun recipeFiltersAndCookbookSearch() {
        val recipes = listOf(
            recipe("sattu", FuelUse.NONE, 300, true, listOf("sattu")),
            recipe("khichdi", FuelUse.MEDIUM, 900, true, listOf("rice", "lentils")),
            recipe("egg-bhurji", FuelUse.LOW, 50, false, listOf("eggs")),
        )
        assertEquals(listOf("sattu"), RecipeFilters.apply(recipes, setOf(RecipeFilter.NO_COOKING)).map { it.id })
        assertEquals(listOf("sattu", "egg-bhurji"), RecipeFilters.apply(recipes, setOf(RecipeFilter.LIMITED_FUEL)).map { it.id })
        assertEquals(listOf("sattu"), RecipeFilters.apply(recipes, setOf(RecipeFilter.LIMITED_WATER, RecipeFilter.VEGETARIAN)).map { it.id })
        assertEquals(listOf("khichdi"), RecipeFilters.apply(recipes, emptySet(), baseIngredient = "Rice").map { it.id })
        val cookbook = Cookbook(1, "2026-10-28", recipes, emptyList())
        assertEquals("khichdi", GuideSearch.searchCookbook(cookbook, "lentils").first().id)
    }
}

class RationAndEmergencyTest {

    @Test fun rationTotals() {
        val r = RationPlanner.plan(RationInput(
            people = 4, days = 3, mealsPerDay = 2, waterLitresPerPersonPerDay = 3.0,
            staples = listOf(StapleAmount("Rice", 75.0)), waterAvailableLitres = 20.0, cookedMealsFuelCanCover = 4,
        )) as RationResult.Ok
        assertEquals(24, r.plan.totalMeals)
        assertEquals(36.0, r.plan.waterNeededLitres, 1e-9)
        assertEquals(1800.0, r.plan.staples.single().totalGrams, 1e-9)
        assertEquals(1, r.plan.waterDaysCovered) // 20 L / 12 L per day
        assertEquals(2, r.plan.fuelDaysCovered)
        assertEquals(8, r.plan.noCookMealsNeeded) // (6 group meals - 4) * 4 people
    }

    @Test fun rationValidation() {
        assertTrue(RationPlanner.plan(RationInput(people = 0, days = 1)) is RationResult.Invalid)
        assertTrue(RationPlanner.plan(RationInput(people = 1, days = 1, waterLitresPerPersonPerDay = 0.0)) is RationResult.Invalid)
        assertEquals(2.5, RationPlanner.scaleFactor(2, 5), 1e-9)
    }

    @Test fun emergencyMessageContainsLocationAndNeverClaimsDelivery() {
        val msg = EmergencyMessage.build(
            LocationForMessage(Coordinate(26.8467, 80.9462), 12.4f, 30_000), includeLocation = true,
            extraNote = "Near the bus stand", senderName = "Asha",
        )
        assertTrue(msg.startsWith("EMERGENCY: I need help. This is Asha."))
        assertTrue(msg.contains("26.84670, 80.94620 (±12 m, just now)"))
        assertTrue(msg.contains("https://www.openstreetmap.org/?mlat=26.84670&mlon=80.94620"))
        assertTrue(msg.endsWith("Near the bus stand"))
        assertFalse(msg.contains("sent", ignoreCase = true))
        val noFix = EmergencyMessage.build(null, includeLocation = true, extraNote = "", senderName = null)
        assertTrue(noFix.contains("My location is not available."))
        val noLoc = EmergencyMessage.build(null, includeLocation = false, extraNote = "", senderName = null)
        assertEquals("EMERGENCY: I need help.", noLoc)
    }

    @Test fun phoneNumbers() {
        assertEquals("+919876543210", PhoneNumbers.normalize("+91 98765-43210"))
        assertEquals("112", PhoneNumbers.normalize(" 112 "))
        assertTrue(PhoneNumbers.isPlausible("112"))
        assertFalse(PhoneNumbers.isPlausible("12"))
    }

    @Test fun checkInStates() {
        assertEquals(CheckInStatus.Idle, CheckIn.status(null, 0))
        assertEquals(CheckInStatus.Active(1000, 400), CheckIn.status(1000, 600))
        assertEquals(CheckInStatus.Overdue(1000, 50), CheckIn.status(1000, 1050))
        assertThrows(IllegalArgumentException::class.java) { CheckIn.deadline(0, 1000) }
        assertEquals(3_600_000L + 10, CheckIn.deadline(10, 3_600_000))
        assertEquals(5000L, CheckIn.extend(1000, 2000, 3000)) // overdue: extends from now
        assertEquals("1:00:00", CheckIn.formatRemaining(3_600_000))
        assertEquals("4:05", CheckIn.formatRemaining(245_000))
    }
}
