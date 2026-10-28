// SPDX-License-Identifier: GPL-3.0-or-later
package app.projecthaven.ui.screens.guides

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.LocalHospital
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.projecthaven.data.guides.GuideLibrary
import app.projecthaven.domain.guides.Article
import app.projecthaven.domain.guides.ArticleSection
import app.projecthaven.domain.guides.CollectionId
import app.projecthaven.domain.guides.GuideKeys
import app.projecthaven.domain.guides.GuideSearch
import app.projecthaven.domain.guides.ReviewStatus
import app.projecthaven.domain.guides.SearchHit
import app.projecthaven.domain.guides.SectionStyle
import app.projecthaven.domain.sos.EmergencyNumbers
import app.projecthaven.ui.LocalAppContainer
import app.projecthaven.ui.components.EmptyState
import app.projecthaven.ui.components.GroupDivider
import app.projecthaven.ui.components.HavenCard
import app.projecthaven.ui.components.HavenScreen
import app.projecthaven.ui.components.Hint
import app.projecthaven.ui.components.ListGroup
import app.projecthaven.ui.components.ListRow
import app.projecthaven.ui.components.LocalShowMessage
import app.projecthaven.ui.components.SearchField
import app.projecthaven.ui.components.SectionLabel
import app.projecthaven.ui.components.SystemActions
import app.projecthaven.ui.navigation.Routes
import app.projecthaven.ui.theme.Dimens
import app.projecthaven.ui.theme.Haven
import kotlinx.coroutines.launch

/** The bundled library, loaded from APK assets. Never touches the network. */
@Composable
fun rememberLibrary(): Result<GuideLibrary>? {
    val container = LocalAppContainer.current
    val state by produceState<Result<GuideLibrary>?>(null, container) {
        value = runCatching { container.guides.library() }
    }
    return state
}

@Composable
private fun LibraryGate(title: String, back: (() -> Unit)?, content: @Composable (GuideLibrary) -> Unit) {
    val lib = rememberLibrary()
    when {
        lib == null -> HavenScreen(title, onBack = back) { }
        lib.isFailure -> HavenScreen(title, onBack = back) {
            EmptyState(Icons.AutoMirrored.Rounded.MenuBook, "Guides couldn't be opened", "Reinstalling the app restores them.")
        }
        else -> content(lib.getOrThrow())
    }
}

fun routeFor(hit: SearchHit): String =
    if (hit.isRecipe) Routes.recipe(hit.id) else Routes.article(hit.collection.key, hit.id)

@Composable
fun GuidesScreen(go: (String) -> Unit) = LibraryGate("Guides", null) { lib ->
    var query by rememberSaveable { mutableStateOf("") }
    val hits = remember(query, lib) { if (query.isBlank()) emptyList() else lib.searchAll(query) }
    HavenScreen("Guides") {
        SearchField(query, { query = it }, placeholder = "Search all guides")
        if (query.isNotBlank()) {
            SearchResults(hits, go)
        } else {
            ListGroup {
                ListRow("First Aid", subtitle = "${lib.firstAid.categories.sumOf { it.articles.size }} topics",
                    icon = Icons.Rounded.LocalHospital, iconTint = Haven.colors.emergency, chevron = true,
                    onClick = { go(Routes.collection(CollectionId.FIRST_AID.key)) })
                GroupDivider()
                ListRow("Survival Handbook", subtitle = "${lib.survival.categories.sumOf { it.articles.size }} topics",
                    icon = Icons.AutoMirrored.Rounded.MenuBook, chevron = true,
                    onClick = { go(Routes.collection(CollectionId.SURVIVAL.key)) })
                GroupDivider()
                ListRow("Emergency Cookbook", subtitle = "${lib.cookbook.recipes.size} recipes", icon = Icons.Rounded.Restaurant,
                    chevron = true, onClick = { go(Routes.COOKBOOK) })
                GroupDivider()
                ListRow("Saved", icon = Icons.Rounded.Bookmark, chevron = true, onClick = { go(Routes.SAVED) })
            }
            Hint("All guides work offline.")
        }
    }
}

@Composable
private fun SearchResults(hits: List<SearchHit>, go: (String) -> Unit) {
    if (hits.isEmpty()) {
        EmptyState(Icons.Rounded.Search, "No results", "Try a different word.")
        return
    }
    ListGroup {
        hits.take(60).forEachIndexed { i, h ->
            if (i > 0) GroupDivider(inset = 16.dp)
            ListRow(h.title, subtitle = "${h.collection.title} · ${h.subtitle}", chevron = true, onClick = { go(routeFor(h)) })
        }
    }
}

@Composable
fun CollectionScreen(key: String, back: () -> Unit, go: (String) -> Unit) {
    val id = CollectionId.fromKey(key)
    if (id == null || id == CollectionId.COOKBOOK) {
        if (id == CollectionId.COOKBOOK) CookbookScreen(back, go) else HavenScreen("Guides", onBack = back) { EmptyState(Icons.AutoMirrored.Rounded.MenuBook, "Guide not found") }
        return
    }
    LibraryGate(id.title, back) { lib ->
        val guide = lib.collection(id) ?: return@LibraryGate
        val context = LocalContext.current
        var query by rememberSaveable { mutableStateOf("") }
        val expanded = remember { mutableStateOf(setOf<String>()) }
        val hits = remember(query) { if (query.isBlank()) emptyList() else GuideSearch.searchArticles(id, guide, query) }
        HavenScreen(id.title, onBack = back, largeTitle = true) {
            if (id == CollectionId.FIRST_AID) {
                HavenCard(Modifier.fillMaxWidth(), color = Haven.colors.emergency,
                    onClick = { SystemActions.dial(context, EmergencyNumbers.INDIA_EMERGENCY) }, onClickLabel = "Call 112") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Phone, contentDescription = null, tint = Haven.colors.onEmergency)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Life-threatening? Call 112 first", style = Haven.type.headline, color = Haven.colors.onEmergency)
                            Text("Not a substitute for medical care", style = Haven.type.footnote, color = Haven.colors.onEmergency.copy(alpha = 0.85f))
                        }
                    }
                }
            }
            SearchField(query, { query = it }, placeholder = "Search ${id.title.lowercase()}")
            if (query.isNotBlank()) {
                SearchResults(hits, go)
            } else {
                guide.categories.forEach { category ->
                    val open = category.id in expanded.value
                    ListGroup {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = 56.dp)
                                .clickable(role = Role.Button) {
                                    expanded.value = if (open) expanded.value - category.id else expanded.value + category.id
                                }
                                .padding(horizontal = 16.dp)
                                .semantics { contentDescription = "${category.title}, ${category.articles.size} topics, ${if (open) "expanded" else "collapsed"}" },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(category.title, style = Haven.type.headline, color = Haven.colors.text, modifier = Modifier.weight(1f))
                            Text("${category.articles.size}", style = Haven.type.callout, color = Haven.colors.textSecondary)
                            Spacer(Modifier.width(8.dp))
                            Icon(if (open) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, contentDescription = null, tint = Haven.colors.textSecondary)
                        }
                        AnimatedVisibility(visible = open) {
                            Column {
                                category.articles.forEach { a ->
                                    GroupDivider(inset = 16.dp)
                                    ListRow(a.title, subtitle = a.summary, chevron = true, onClick = { go(Routes.article(id.key, a.id)) })
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ArticleScreen(collectionKey: String, articleId: String, back: () -> Unit) {
    val id = CollectionId.fromKey(collectionKey)
    LibraryGate(id?.title ?: "Guide", back) { lib ->
        val article = id?.let { lib.article(it, articleId) }
        if (id == null || article == null) {
            HavenScreen("Guide", onBack = back) { EmptyState(Icons.AutoMirrored.Rounded.MenuBook, "Topic not found") }
            return@LibraryGate
        }
        ArticleContent(id, article, back)
    }
}

@Composable
private fun ArticleContent(collection: CollectionId, article: Article, back: () -> Unit) {
    val container = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    val show = LocalShowMessage.current
    val bookmarks by container.settings.bookmarks.collectAsStateWithLifecycle(initialValue = emptySet())
    val done by container.settings.checklistDone.collectAsStateWithLifecycle(initialValue = emptySet())
    val key = GuideKeys.article(collection, article.id)
    val saved = key in bookmarks

    HavenScreen(article.title, onBack = back, largeTitle = true, actions = {
        IconButton(onClick = {
            scope.launch { container.settings.setBookmarked(key, !saved); show(if (saved) "Removed from saved" else "Saved") }
        }) {
            Icon(if (saved) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder,
                contentDescription = if (saved) "Remove from saved" else "Save", tint = Haven.colors.accent)
        }
    }) {
        Text(article.summary, style = Haven.type.body, color = Haven.colors.textSecondary)
        article.sections.forEach { section ->
            SectionView(section, done = { i -> GuideKeys.checklistItem(collection, article.id, section.id, i) in done }) { i, checked ->
                scope.launch { container.settings.setChecklistItem(GuideKeys.checklistItem(collection, article.id, section.id, i), checked) }
            }
        }
        if (article.sections.any { it.style == SectionStyle.CHECKLIST }) {
            Text(
                "Reset checklist", style = Haven.type.callout, color = Haven.colors.accent,
                modifier = Modifier.padding(4.dp).clickable(role = Role.Button) {
                    scope.launch { container.settings.resetChecklist("${collection.key}/${article.id}/") }
                },
            )
        }
        References(article)
    }
}

@Composable
fun SectionView(section: ArticleSection, done: (Int) -> Boolean = { false }, onCheck: (Int, Boolean) -> Unit = { _, _ -> }) {
    SectionLabel(section.title)
    val container = when (section.style) {
        SectionStyle.WARNING -> Haven.colors.emergency.copy(alpha = if (Haven.colors.isDark) 0.18f else 0.10f)
        else -> Haven.colors.surface
    }
    HavenCard(Modifier.fillMaxWidth(), color = container, contentPadding = 14.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            section.items.forEachIndexed { i, item ->
                when (section.style) {
                    SectionStyle.STEPS -> NumberedItem(i + 1, item)
                    SectionStyle.CHECKLIST -> {
                        val checked = done(i)
                        Row(
                            Modifier.fillMaxWidth().clickable(role = Role.Checkbox) { onCheck(i, !checked) },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked = checked, onCheckedChange = { onCheck(i, it) },
                                colors = CheckboxDefaults.colors(checkedColor = Haven.colors.accent, checkmarkColor = Haven.colors.onAccent, uncheckedColor = Haven.colors.textSecondary))
                            Text(item, style = Haven.type.body, color = if (checked) Haven.colors.textSecondary else Haven.colors.text)
                        }
                    }
                    SectionStyle.AVOID -> BulletItem("✕", item, Haven.colors.emergency)
                    SectionStyle.WARNING -> BulletItem("!", item, Haven.colors.emergency)
                    SectionStyle.BULLETS -> BulletItem("•", item, Haven.colors.accent)
                    SectionStyle.TEXT -> Text(item, style = Haven.type.body, color = Haven.colors.text)
                }
            }
        }
    }
}

@Composable
private fun NumberedItem(n: Int, text: String) {
    Row {
        Text("$n", style = Haven.type.headline, color = Haven.colors.accent, modifier = Modifier.width(28.dp))
        Text(text, style = Haven.type.body, color = Haven.colors.text)
    }
}

@Composable
private fun BulletItem(mark: String, text: String, color: androidx.compose.ui.graphics.Color) {
    Row {
        Text(mark, style = Haven.type.headline, color = color, modifier = Modifier.width(24.dp))
        Text(text, style = Haven.type.body, color = Haven.colors.text)
    }
}

@Composable
fun References(article: Article) {
    val context = LocalContext.current
    if (article.references.isNotEmpty()) {
        SectionLabel("Sources")
        Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(horizontal = 4.dp)) {
            article.references.forEach { r ->
                val label = "${r.publisher} — ${r.title}"
                if (r.url != null) {
                    Text(label, style = Haven.type.footnote, color = Haven.colors.accent,
                        modifier = Modifier.clickable(role = Role.Button) { SystemActions.openUrl(context, r.url) })
                } else {
                    Text(label, style = Haven.type.footnote, color = Haven.colors.textSecondary)
                }
            }
        }
    }
    val review = if (article.review.status == ReviewStatus.REVIEWED)
        "Reviewed${article.review.reviewedOn?.let { " $it" } ?: ""}"
    else "Not yet clinically reviewed"
    Hint("Updated ${article.updated} · $review", Modifier.padding(top = 8.dp))
}

@Composable
fun SavedScreen(back: () -> Unit, go: (String) -> Unit) = LibraryGate("Saved", back) { lib ->
    val container = LocalAppContainer.current
    val bookmarks by container.settings.bookmarks.collectAsStateWithLifecycle(initialValue = emptySet())
    val entries = bookmarks.mapNotNull { key ->
        val parts = key.split('/', limit = 2)
        if (parts.size != 2) return@mapNotNull null
        if (parts[0] == "recipe") lib.recipe(parts[1])?.let { Triple(it.title, "Recipe", Routes.recipe(it.id)) }
        else CollectionId.fromKey(parts[0])?.let { c -> lib.article(c, parts[1])?.let { Triple(it.title, c.title, Routes.article(c.key, it.id)) } }
    }.sortedBy { it.first }
    HavenScreen("Saved", onBack = back, largeTitle = false) {
        if (entries.isEmpty()) {
            EmptyState(Icons.Rounded.BookmarkBorder, "Nothing saved yet", "Tap the bookmark on any topic.")
        } else {
            ListGroup {
                entries.forEachIndexed { i, (title, sub, route) ->
                    if (i > 0) GroupDivider(inset = 16.dp)
                    ListRow(title, subtitle = sub, chevron = true, onClick = { go(route) })
                }
            }
        }
    }
}
