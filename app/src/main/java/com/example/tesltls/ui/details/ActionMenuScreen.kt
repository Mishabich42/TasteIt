package com.example.tesltls.ui.details

import android.widget.Toast
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.tesltls.R
import com.example.tesltls.domain.model.Dish
import com.example.tesltls.ui.FoodCategory
import com.example.tesltls.ui.common.formatPrice
import com.example.tesltls.ui.common.openUrl
import com.example.tesltls.ui.components.AppIcon
import com.example.tesltls.ui.components.DishImage
import com.example.tesltls.ui.components.LinkPill
import com.example.tesltls.ui.components.LoadingBox
import com.example.tesltls.ui.components.MessageBox
import com.example.tesltls.ui.components.ScreenHeader
import com.example.tesltls.ui.theme.FoodColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActionMenuScreen(
    viewModel: DishDetailsViewModel,
    onBack: () -> Unit,
    onOpenRecipe: () -> Unit,
    onOpenPlaces: () -> Unit,
    onExitToMenu: () -> Unit
) {
    val strings = com.example.tesltls.ui.strings.LocalAppStrings.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var sheetService by remember { mutableStateOf<DeliveryService?>(null) }

    val dish = state.dish
    if (state.isLoading) {
        LoadingBox()
        return
    }
    if (dish == null) {
        MessageBox(
            message = state.error ?: "—",
            actionLabel = strings.tryAgain,
            onAction = viewModel::load
        )
        return
    }

    /** Доставка: 0 закладів — повідомлення, 1 — одразу відкриваємо, кілька — даємо вибрати. */
    fun onDeliveryClick(service: DeliveryService) {
        val options = state.places.filter { it.place.deliveryUrl(service) != null }
        when (options.size) {
            0 -> Toast.makeText(
                context, "${strings.noDeliveryForService} ${service.title}", Toast.LENGTH_SHORT
            ).show()
            1 -> context.openUrl(options.first().place.deliveryUrl(service)!!)
            else -> sheetService = service
        }
    }

    val hasGlovo = state.places.any { it.place.deliveryUrl(DeliveryService.GLOVO) != null }
    val hasBolt = state.places.any { it.place.deliveryUrl(DeliveryService.BOLT) != null }

    Column(
        modifier = Modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(bottom = 24.dp)
    ) {
        ScreenHeader(
            title = strings.tastyChoice,
            subtitle = strings.tastyChoiceSub,
            onBack = onBack
        )
        Spacer(Modifier.height(16.dp))

        DishInfoCard(dish)

        Spacer(Modifier.height(20.dp))
        Text(
            text = strings.whatNext,
            fontSize = 18.sp,
            fontWeight = FontWeight.ExtraBold,
            color = FoodColors.TextPrimary
        )
        Spacer(Modifier.height(12.dp))

        // ── Рецепт ──
        ActionCard(
            color = FoodColors.CardGreen,
            icon = R.drawable.ic_book,
            title = strings.recipeTitle,
            subtitle = strings.recipeSub,
            onClick = onOpenRecipe
        ) {
            if (!dish.youtubeUrl.isNullOrBlank()) {
                Spacer(Modifier.height(14.dp))
                YouTubePill(onClick = { context.openUrl(dish.youtubeUrl) })
            }
        }
        Spacer(Modifier.height(12.dp))

        // ── Заклади поруч ──
        ActionCard(
            color = FoodColors.CardPlaces,
            icon = R.drawable.ic_pin,
            title = strings.placesTitle,
            subtitle = strings.placesSub,
            onClick = onOpenPlaces
        )
        Spacer(Modifier.height(12.dp))

        // ── Доставка ──
        ActionCard(
            color = FoodColors.CardDelivery,
            icon = R.drawable.ic_bag,
            title = strings.deliveryTitle,
            subtitle = strings.deliverySub
        ) {
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                LinkPill(
                    label = DeliveryService.GLOVO.title,
                    modifier = Modifier.weight(1f),
                    enabled = hasGlovo,
                    onClick = { onDeliveryClick(DeliveryService.GLOVO) }
                )
                LinkPill(
                    label = DeliveryService.BOLT.title,
                    modifier = Modifier.weight(1f),
                    enabled = hasBolt,
                    onClick = { onDeliveryClick(DeliveryService.BOLT) }
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = "Доступність залежить від закладу.",
                fontSize = 11.sp,
                color = FoodColors.TextSecondary
            )
        }
        Spacer(Modifier.height(12.dp))

        // ── Вихід у меню ──
        ActionCard(
            color = FoodColors.CardNeutral,
            icon = R.drawable.ic_exit,
            title = strings.toMenu,
            subtitle = strings.categories,
            onClick = onExitToMenu
        )
    }

    // Список закладів для вибраного сервісу доставки
    sheetService?.let { service ->
        ModalBottomSheet(
            onDismissRequest = { sheetService = null },
            sheetState = rememberModalBottomSheetState(),
            containerColor = FoodColors.Background
        ) {
            Column(Modifier.padding(horizontal = 20.dp).navigationBarsPadding()) {
                Text(
                    text = "${service.title}: оберіть заклад",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = FoodColors.TextPrimary
                )
                Spacer(Modifier.height(12.dp))
                state.places
                    .filter { it.place.deliveryUrl(service) != null }
                    .forEach { item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(FoodColors.SearchField)
                                .clickable {
                                    context.openUrl(item.place.deliveryUrl(service)!!)
                                    sheetService = null
                                }
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = item.place.name,
                                modifier = Modifier.weight(1f),
                                fontWeight = FontWeight.SemiBold,
                                color = FoodColors.TextPrimary
                            )
                            formatPrice(item.price)?.let {
                                Text(it, color = FoodColors.TextSecondary, fontSize = 14.sp)
                                Spacer(Modifier.width(8.dp))
                            }
                            AppIcon(R.drawable.ic_arrow_up_right, tint = FoodColors.Green, size = 16.dp)
                        }
                    }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

// ───────────────────────── Частини екрана ─────────────────────────

@Composable
private fun DishInfoCard(dish: Dish) {
    val strings = com.example.tesltls.ui.strings.LocalAppStrings.current
    val category = FoodCategory.fromDb(dish.category)
    val catTitle = category?.let { strings.categoryTitle(it) } ?: dish.category
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background((category?.color ?: FoodColors.Pill).copy(alpha = 0.6f))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        DishImage(
            url = dish.imageUrl,
            category = dish.category,
            modifier = Modifier.size(width = 112.dp, height = 100.dp).clip(RoundedCornerShape(18.dp))
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = catTitle.uppercase(),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                color = FoodColors.GreenLabel
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = dish.name,
                fontSize = 22.sp,
                lineHeight = 26.sp,
                fontWeight = FontWeight.ExtraBold,
                color = FoodColors.TextPrimary,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Кольорова картка дії. Якщо задано [onClick] — клікабельний заголовок зі стрілкою ›.
 * Додатковий вміст ([content]) показується під заголовком.
 */
@Composable
private fun ActionCard(
    color: Color,
    @DrawableRes icon: Int,
    title: String,
    subtitle: String,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(color)
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (onClick != null) Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick)
                    else Modifier
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(FoodColors.IconBadge),
                contentAlignment = Alignment.Center
            ) {
                AppIcon(icon, tint = FoodColors.Green, size = 22.dp)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = FoodColors.TextPrimary
                )
                Text(
                    text = subtitle,
                    fontSize = 14.sp,
                    lineHeight = 19.sp,
                    color = FoodColors.TextSecondary
                )
            }
            if (onClick != null) {
                Spacer(Modifier.width(8.dp))
                AppIcon(R.drawable.ic_chevron_right, tint = FoodColors.TextPrimary, size = 20.dp)
            }
        }
        content()
    }
}

/** Біла смужка "Дивитися відеорецепт ... YouTube" з червоним значком. */
@Composable
private fun YouTubePill(onClick: () -> Unit) {
    val strings = com.example.tesltls.ui.strings.LocalAppStrings.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(width = 24.dp, height = 17.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(FoodColors.YouTubeRed),
            contentAlignment = Alignment.Center
        ) {
            AppIcon(R.drawable.ic_play, tint = Color.White, size = 11.dp)
        }
        Spacer(Modifier.width(10.dp))
        Text(
            text = strings.watchOnYoutube,
            modifier = Modifier.weight(1f),
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = FoodColors.TextPrimary
        )
        Text(text = "YouTube", fontSize = 11.sp, color = FoodColors.TextSecondary)
    }
}
