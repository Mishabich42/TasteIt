package com.example.tesltls.ui.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.example.tesltls.R
import com.example.tesltls.domain.model.Dish
import com.example.tesltls.domain.repository.FoodRepository
import com.example.tesltls.domain.repository.searchDishesFlow
import com.example.tesltls.ui.FoodCategory
import com.example.tesltls.ui.common.toUserMessage
import com.example.tesltls.ui.components.AppIcon
import com.example.tesltls.ui.components.DishRow
import com.example.tesltls.ui.theme.FoodColors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn

// ───────────────────────── ViewModel ─────────────────────────

class HomeViewModel(repository: FoodRepository) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    fun onQueryChange(value: String) {
        _query.value = value
    }

    /**
     * Результати пошуку: debounce 300 мс, скасування застарілих запитів —
     * усе всередині searchDishesFlow(). null = результату ще немає.
     */
    val searchResults: StateFlow<Result<List<Dish>>?> =
        repository.searchDishesFlow(_query)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

// ───────────────────────── Екран ─────────────────────────

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onCategoryClick: (FoodCategory) -> Unit,
    onDishClick: (String) -> Unit
) {
    val query by viewModel.query.collectAsStateWithLifecycle()
    val results by viewModel.searchResults.collectAsStateWithLifecycle()
    val currentResults = results // локальна копія, щоб працював smart-cast

    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "header", span = { GridItemSpan(maxLineSpan) }) { HomeHeader() }

        item(key = "search", span = { GridItemSpan(maxLineSpan) }) {
            SearchField(value = query, onValueChange = viewModel::onQueryChange)
        }

        if (query.isBlank()) {
            // ── Режим "категорії" ──
            item(key = "categories_title", span = { GridItemSpan(maxLineSpan) }) {
                CategoriesTitle(count = FoodCategory.entries.size)
            }
            items(FoodCategory.entries, key = { it.name }) { category ->
                CategoryTile(category = category, onClick = { onCategoryClick(category) })
            }
        } else {
            // ── Режим "результати пошуку" ──
            when {
                currentResults == null -> item(key = "loading", span = { GridItemSpan(maxLineSpan) }) {
                    Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = FoodColors.Green)
                    }
                }

                currentResults.isFailure -> item(key = "error", span = { GridItemSpan(maxLineSpan) }) {
                    CenteredHint(currentResults.exceptionOrNull()?.toUserMessage().orEmpty())
                }

                currentResults.getOrNull().isNullOrEmpty() ->
                    item(key = "empty", span = { GridItemSpan(maxLineSpan) }) {
                        CenteredHint("Нічого не знайдено за запитом «${query.trim()}»")
                    }

                else -> items(
                    items = currentResults.getOrNull().orEmpty(),
                    key = { it.id },
                    span = { GridItemSpan(maxLineSpan) }
                ) { dish ->
                    DishRow(dish = dish, onClick = { onDishClick(dish.id) })
                }
            }
        }
    }
}

// ───────────────────────── Частини екрана ─────────────────────────

@Composable
private fun HomeHeader() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.Top
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = "GOOD FOOD, GOOD MOOD",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                color = FoodColors.GreenLabel
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Що вам хотілося б сьогодні?",
                fontSize = 30.sp,
                lineHeight = 34.sp,
                fontWeight = FontWeight.ExtraBold,
                color = FoodColors.TextPrimary
            )
        }
        Spacer(Modifier.size(12.dp))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(18.dp))
                .background(FoodColors.GreenSoft)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Text("Ням", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = FoodColors.TextPrimary)
        }
    }
}

@Composable
private fun SearchField(value: String, onValueChange: (String) -> Unit) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        placeholder = { Text("Пошук", color = FoodColors.TextMuted) },
        leadingIcon = { AppIcon(R.drawable.ic_search, tint = FoodColors.TextSecondary, size = 20.dp) },
        trailingIcon = {
            if (value.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .clickable { onValueChange("") },
                    contentAlignment = Alignment.Center
                ) {
                    AppIcon(R.drawable.ic_close, tint = FoodColors.TextSecondary, size = 18.dp, contentDescription = "Очистити")
                }
            }
        },
        shape = RoundedCornerShape(20.dp),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = FoodColors.SearchField,
            unfocusedContainerColor = FoodColors.SearchField,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
            focusedTextColor = FoodColors.TextPrimary,
            unfocusedTextColor = FoodColors.TextPrimary,
            cursorColor = FoodColors.Green
        )
    )
}

@Composable
private fun CategoriesTitle(count: Int) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Категорії",
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            color = FoodColors.TextPrimary,
            modifier = Modifier.weight(1f)
        )
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(FoodColors.Pill)
                .padding(horizontal = 10.dp, vertical = 3.dp)
        ) {
            Text("$count", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = FoodColors.TextSecondary)
        }
    }
}

@Composable
private fun CategoryTile(category: FoodCategory, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(22.dp))
            .background(category.color)
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Image(
            painter = painterResource(category.icon),
            contentDescription = null,
            modifier = Modifier.size(48.dp)
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = category.title,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = FoodColors.TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun CenteredHint(text: String) {
    Box(Modifier.fillMaxWidth().padding(vertical = 32.dp), contentAlignment = Alignment.Center) {
        Text(text, color = FoodColors.TextSecondary, fontSize = 15.sp, textAlign = TextAlign.Center)
    }
}
