// SPDX-License-Identifier: GPL-3.0-or-later
package app.projecthaven.data.guides

import app.projecthaven.domain.guides.Article
import app.projecthaven.domain.guides.CollectionId
import app.projecthaven.domain.guides.Cookbook
import app.projecthaven.domain.guides.GuideCollection
import app.projecthaven.domain.guides.GuideSearch
import app.projecthaven.domain.guides.Recipe
import app.projecthaven.domain.guides.SearchHit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

/** Reads bundled guide text. Implemented by Android assets in the app and by files in tests. */
fun interface GuideSource {
    fun read(name: String): String
}

data class GuideLibrary(
    val firstAid: GuideCollection,
    val survival: GuideCollection,
    val cookbook: Cookbook,
) {
    fun collection(id: CollectionId): GuideCollection? = when (id) {
        CollectionId.FIRST_AID -> firstAid
        CollectionId.SURVIVAL -> survival
        CollectionId.COOKBOOK -> null
    }

    fun article(id: CollectionId, articleId: String): Article? = when (id) {
        CollectionId.COOKBOOK -> cookbook.safety.firstOrNull { it.id == articleId }
        else -> collection(id)?.categories?.asSequence()?.flatMap { it.articles }?.firstOrNull { it.id == articleId }
    }

    fun recipe(id: String): Recipe? = cookbook.recipes.firstOrNull { it.id == id }

    fun searchAll(query: String): List<SearchHit> =
        (GuideSearch.searchArticles(CollectionId.FIRST_AID, firstAid, query) +
            GuideSearch.searchArticles(CollectionId.SURVIVAL, survival, query) +
            GuideSearch.searchCookbook(cookbook, query))
            .sortedByDescending { it.score }
}

/** Loads the bundled, offline guide library once and caches it in memory. */
class GuideRepository(private val source: GuideSource) {

    private val json = Json { ignoreUnknownKeys = true }
    private val mutex = Mutex()
    @Volatile private var cached: GuideLibrary? = null

    suspend fun library(): GuideLibrary = cached ?: mutex.withLock {
        cached ?: withContext(Dispatchers.IO) { parse() }.also { cached = it }
    }

    fun parse(): GuideLibrary = GuideLibrary(
        firstAid = json.decodeFromString(GuideCollection.serializer(), source.read(FIRST_AID)),
        survival = json.decodeFromString(GuideCollection.serializer(), source.read(SURVIVAL)),
        cookbook = json.decodeFromString(Cookbook.serializer(), source.read(COOKBOOK)),
    )

    companion object {
        const val FIRST_AID = "guides/first_aid.json"
        const val SURVIVAL = "guides/survival.json"
        const val COOKBOOK = "guides/cookbook.json"
    }
}
