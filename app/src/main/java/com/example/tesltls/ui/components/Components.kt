package com.example.tesltls.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.example.tesltls.R
import com.example.tesltls.domain.model.Dish
import com.example.tesltls.ui.FoodCategory
import com.example.tesltls.ui.theme.FoodColors

// ───────────────────────── Іконка ─────────────────────────

/** Векторна іконка з res/drawable, що фарбується в [tint]. */
@Composable
fun AppIcon(
    @DrawableRes id: Int,
    modifier: Modifier = Modifier,
    tint: Color = FoodColors.TextPrimary,
    size: Dp = 24.dp,
    contentDescription: String? = null
) {
    Icon(
        painter = painterResource(id),
        contentDescription = contentDescription,
        modifier = modifier.size(size),
        tint = tint
    )
}

// ───────────────────────── Нижня навігація ─────────────────────────

enum class BottomTab(@DrawableRes val icon: Int) {
    SEARCH(R.drawable.ic_grid),
    FAVORITES(R.drawable.ic_heart),
    PROFILE(R.drawable.ic_person)
}

@Composable
fun FoodBottomBar(selected: BottomTab, onSelect: (BottomTab) -> Unit) {
    val strings = com.example.tesltls.ui.strings.LocalAppStrings.current
    Column(Modifier.background(FoodColors.BottomBar)) {
        HorizontalDivider(color = FoodColors.Divider)
        Row(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(vertical = 8.dp)
        ) {
            BottomTab.entries.forEach { tab ->
                val isSelected = tab == selected
                val color = if (isSelected) FoodColors.Green else FoodColors.TextMuted
                val label = when (tab) {
                    BottomTab.SEARCH -> strings.navSearch
                    BottomTab.FAVORITES -> strings.navFavorites
                    BottomTab.PROFILE -> strings.navProfile
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { onSelect(tab) }
                        .padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    AppIcon(tab.icon, tint = color, size = 26.dp, contentDescription = label)
                    Text(
                        text = label,
                        color = color,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

// ───────────────────────── Заголовки ─────────────────────────

/** Квадратна кнопка "назад" (світло-зелена, як у макеті). */
@Composable
fun BackButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(FoodColors.SearchField)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        AppIcon(R.drawable.ic_chevron_left, size = 22.dp, contentDescription = "Назад")
    }
}

@Composable
fun ScreenHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null
) {
    Column(modifier.fillMaxWidth().padding(top = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) {
                BackButton(onBack)
                Spacer(Modifier.width(12.dp))
            }
            Text(
                text = title,
                fontSize = 24.sp,
                fontWeight = FontWeight.ExtraBold,
                color = FoodColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (subtitle != null) {
            Text(
                text = subtitle,
                fontSize = 13.sp,
                color = FoodColors.TextSecondary,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
    }
}

// ───────────────────────── Стани екрана ─────────────────────────

@Composable
fun LoadingBox(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = FoodColors.Green)
    }
}

/** Повідомлення по центру з необов'язковою кнопкою (помилка / порожній список). */
@Composable
fun MessageBox(
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Column(
        modifier = modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = message,
            textAlign = TextAlign.Center,
            color = FoodColors.TextSecondary,
            fontSize = 15.sp
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.size(16.dp))
            PrimaryButton(actionLabel, onAction)
        }
    }
}

@Composable
fun PrimaryButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = FoodColors.Green,
            contentColor = Color.White
        )
    ) {
        Text(label, fontWeight = FontWeight.SemiBold)
    }
}

// ───────────────────────── Картинка страви ─────────────────────────

/**
 * Фото страви з URL. Поки фото вантажиться (або його немає) — показуємо
 * іконку категорії на кольоровому фоні.
 * Обрізання кутів задавайте через [modifier] (`Modifier.clip(...)`).
 */
@Composable
fun DishImage(
    url: String?,
    category: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop
) {
    val cat = FoodCategory.fromDb(category)
    Box(
        modifier = modifier.background(cat?.color ?: FoodColors.Pill),
        contentAlignment = Alignment.Center
    ) {
        if (cat != null) {
            Image(
                painter = painterResource(cat.icon),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(0.45f)
            )
        }
        if (!url.isNullOrBlank()) {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = contentScale,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

/** Рядок зі стравою (результати пошуку, "Улюблене"). */
@Composable
fun DishRow(dish: Dish, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val strings = com.example.tesltls.ui.strings.LocalAppStrings.current
    val cat = FoodCategory.fromDb(dish.category)
    val catTitle = cat?.let { strings.categoryTitle(it) } ?: dish.category
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background((cat?.color ?: FoodColors.Pill).copy(alpha = 0.6f))
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        DishImage(
            url = dish.imageUrl,
            category = dish.category,
            modifier = Modifier.size(64.dp).clip(RoundedCornerShape(14.dp))
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = catTitle.uppercase(),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                color = FoodColors.GreenLabel
            )
            Text(
                text = dish.name,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = FoodColors.TextPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        AppIcon(R.drawable.ic_chevron_right, tint = FoodColors.TextSecondary, size = 20.dp)
    }
}

// ───────────────────────── Біла "пігулка"-посилання ─────────────────────────

/** Біла кнопка з підписом і стрілкою ↗ (Glovo, Bolt Food, "Маршрут"). */
@Composable
fun LinkPill(
    label: String,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int = R.drawable.ic_arrow_up_right,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Row(
        modifier = modifier
            .alpha(if (enabled) 1f else 0.45f)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = FoodColors.TextPrimary
        )
        Spacer(Modifier.width(10.dp))
        AppIcon(icon, tint = FoodColors.Green, size = 16.dp)
    }
}
