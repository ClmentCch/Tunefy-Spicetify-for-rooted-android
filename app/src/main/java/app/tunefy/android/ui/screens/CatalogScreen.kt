package app.tunefy.android.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.tunefy.android.data.model.CatalogItem
import app.tunefy.android.data.model.Category
import app.tunefy.android.data.model.Compatibility
import app.tunefy.android.ui.AppViewModel
import app.tunefy.android.ui.StoreUi
import app.tunefy.android.ui.components.EmptyState
import app.tunefy.android.ui.components.ItemRow
import app.tunefy.android.ui.components.LoadingState
import app.tunefy.android.ui.components.ThemeCard
import app.tunefy.android.ui.components.icon

@Composable
fun SearchScreen(
    viewModel: AppViewModel,
    onOpenItem: (String) -> Unit
) {
    CatalogScreen(
        viewModel = viewModel,
        initialCategory = Category.ALL,
        onOpenItem = onOpenItem,
        autofocusSearch = true
    )
}

@Composable
fun CatalogScreen(
    viewModel: AppViewModel,
    initialCategory: Category = Category.ALL,
    onOpenItem: (String) -> Unit,
    autofocusSearch: Boolean = false
) {
    val index by viewModel.index.collectAsStateWithLifecycle()
    val status by viewModel.repositoryStatus.collectAsStateWithLifecycle()
    val installed by viewModel.installedItems.collectAsStateWithLifecycle()
    val spotify by viewModel.spotify.collectAsStateWithLifecycle()
    val patchBundle by viewModel.patchBundle.collectAsStateWithLifecycle()

    var query by rememberSaveable { mutableStateOf("") }
    var categoryKey by rememberSaveable { mutableStateOf(initialCategory.key) }
    var onlyInstalled by rememberSaveable { mutableStateOf(false) }
    var sortRecent by rememberSaveable { mutableStateOf(false) }

    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(autofocusSearch) {
        if (autofocusSearch) {
            focusRequester.requestFocus()
        }
    }

    fun installedVersion(item: CatalogItem): String? {
        installed.firstOrNull { it.itemId == item.id }?.let { return it.version }
        if (item.packageId == "com.spotify.music" && spotify.installed) return spotify.versionName
        return null
    }

    val selectedCategory = Category.fromKey(categoryKey)
    val results = remember(index, query, categoryKey, onlyInstalled, sortRecent, installed, spotify) {
        index.apps
            .filter { item ->
                (categoryKey == Category.ALL.key || item.category == categoryKey) &&
                    (!onlyInstalled || installedVersion(item) != null) &&
                    (query.isBlank() || matches(item, query))
            }
            .let { list -> if (sortRecent) list.sortedByDescending { it.publishedAt } else list.sortedBy { it.name.lowercase() } }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp)
                .focusRequester(focusRequester),
            placeholder = { Text("Rechercher dans le dépôt TuneFy") },
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { query = "" }) {
                        Icon(Icons.Rounded.Close, contentDescription = "Effacer")
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(50),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() })
        )

        LazyRow(
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(Category.catalogFilters, key = { it.key }) { category ->
                FilterChip(
                    selected = categoryKey == category.key,
                    onClick = { categoryKey = category.key },
                    label = { Text(category.label) },
                    leadingIcon = if (categoryKey == category.key) {
                        { Icon(category.icon(), contentDescription = null, modifier = Modifier.size(15.dp)) }
                    } else null,
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "${results.size} résultat${if (results.size > 1) "s" else ""}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            FilterChip(
                selected = onlyInstalled,
                onClick = { onlyInstalled = !onlyInstalled },
                label = { Text("Installés") }
            )
            FilterChip(
                selected = sortRecent,
                onClick = { sortRecent = !sortRecent },
                label = { Text("Récents") }
            )
        }

        Spacer(Modifier.height(6.dp))

        when {
            index.apps.isEmpty() && status.loading -> LoadingState()
            results.isEmpty() -> EmptyState(
                icon = Icons.Rounded.SearchOff,
                title = "Aucun résultat",
                message = "Aucune ressource Spotify ne correspond à « $query » dans cette catégorie."
            )

            selectedCategory == Category.THEMES -> LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 4.dp, bottom = 28.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                gridItems(results, key = { it.id }) { item ->
                    ThemeCard(
                        item = item,
                        compatibility = StoreUi.compatibility(item, spotify, patchBundle),
                        installedVersion = installedVersion(item),
                        onClick = { onOpenItem(item.id) }
                    )
                }
            }

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 4.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(results, key = { it.id }) { item ->
                    ItemRow(
                        item = item,
                        installedVersion = installedVersion(item),
                        compatibility = StoreUi.compatibility(item, spotify, patchBundle),
                        onClick = { onOpenItem(item.id) }
                    )
                }
            }
        }
    }
}

private fun matches(item: CatalogItem, query: String): Boolean {
    val needle = query.trim()
    if (needle.isBlank()) return true
    return item.name.contains(needle, ignoreCase = true) ||
        item.summary.contains(needle, ignoreCase = true) ||
        item.developer.contains(needle, ignoreCase = true) ||
        item.description.contains(needle, ignoreCase = true) ||
        item.tags.any { it.contains(needle, ignoreCase = true) }
}
