// SPDX-License-Identifier: GPL-3.0-or-later
package app.projecthaven.domain.guides

import java.text.Normalizer

data class SearchHit(
    val collection: CollectionId,
    val id: String,
    val title: String,
    val subtitle: String,
    val score: Int,
    val isRecipe: Boolean,
)

/**
 * Offline full-text search over bundled guides. Every query word must appear somewhere in
 * the entry (title, keywords, summary or body); matches in the title rank highest.
 */
object GuideSearch {

    fun tokenize(text: String): List<String> =
        normalize(text).split(Regex("[^a-z0-9]+"))
            .filter { it.isNotEmpty() && (it.length >= 2 || it.all(Char::isDigit)) }

    private fun normalize(text: String): String =
        Normalizer.normalize(text, Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .lowercase()

    private class Fields(val title: String, val keywords: String, val summary: String, val body: String)

    private fun score(tokens: List<String>, f: Fields): Int {
        var total = 0
        for (token in tokens) {
            val s = when {
                wordStart(f.title, token) -> 10
                wordStart(f.keywords, token) -> 6
                f.title.contains(token) -> 5
                wordStart(f.summary, token) -> 3
                wordStart(f.body, token) -> 1
                f.body.contains(token) || f.summary.contains(token) || f.keywords.contains(token) -> 1
                else -> return 0
            }
            total += s
        }
        return total
    }

    private fun wordStart(haystack: String, token: String): Boolean {
        var index = haystack.indexOf(token)
        while (index >= 0) {
            if (index == 0 || !haystack[index - 1].isLetterOrDigit()) return true
            index = haystack.indexOf(token, index + 1)
        }
        return false
    }

    fun searchArticles(
        collection: CollectionId,
        guide: GuideCollection,
        query: String,
    ): List<SearchHit> {
        val tokens = tokenize(query)
        if (tokens.isEmpty()) return emptyList()
        return guide.categories.flatMap { category ->
            category.articles.mapNotNull { article ->
                val fields = Fields(
                    title = normalize(article.title),
                    keywords = normalize(article.keywords.joinToString(" ")),
                    summary = normalize(article.summary + " " + category.title),
                    body = normalize(article.sections.joinToString(" ") { it.title + " " + it.items.joinToString(" ") }),
                )
                val s = score(tokens, fields)
                if (s > 0) SearchHit(collection, article.id, article.title, category.title, s, false) else null
            }
        }.sortedWith(compareByDescending<SearchHit> { it.score }.thenBy { it.title })
    }

    fun searchCookbook(cookbook: Cookbook, query: String): List<SearchHit> {
        val tokens = tokenize(query)
        if (tokens.isEmpty()) return emptyList()
        val recipes = cookbook.recipes.mapNotNull { r ->
            val fields = Fields(
                title = normalize(r.title),
                keywords = normalize((r.keywords + r.baseIngredients).joinToString(" ")),
                summary = normalize(r.summary + " " + r.category.title),
                body = normalize((r.ingredients.map { it.item } + r.steps + r.substitutions + r.safety).joinToString(" ")),
            )
            val s = score(tokens, fields)
            if (s > 0) SearchHit(CollectionId.COOKBOOK, r.id, r.title, r.category.title, s, true) else null
        }
        val safety = cookbook.safety.mapNotNull { a ->
            val fields = Fields(
                title = normalize(a.title),
                keywords = normalize(a.keywords.joinToString(" ")),
                summary = normalize(a.summary),
                body = normalize(a.sections.joinToString(" ") { it.items.joinToString(" ") }),
            )
            val s = score(tokens, fields)
            if (s > 0) SearchHit(CollectionId.COOKBOOK, a.id, a.title, "Food & water safety", s, false) else null
        }
        return (recipes + safety).sortedWith(compareByDescending<SearchHit> { it.score }.thenBy { it.title })
    }
}
