package com.example.tesltls.ui.details

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.tesltls.R
import com.example.tesltls.domain.model.PlaceWithPrice
import com.example.tesltls.ui.common.formatPrice
import com.example.tesltls.ui.common.openMap
import com.example.tesltls.ui.common.openUrl
import com.example.tesltls.ui.components.LinkPill
import com.example.tesltls.ui.components.LoadingBox
import com.example.tesltls.ui.components.MessageBox
import com.example.tesltls.ui.components.ScreenHeader
import com.example.tesltls.ui.theme.FoodColors

@Composable
fun PlacesScreen(viewModel: DishDetailsViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        ScreenHeader(
            title = "Заклади поруч",
            subtitle = state.dish?.name ?: "Де скуштувати цю страву",
            onBack = onBack
        )
        Spacer(Modifier.height(16.dp))

        when {
            state.isLoading -> LoadingBox()
            state.error != null && state.dish == null -> MessageBox(
                message = state.error.orEmpty(),
                actionLabel = "Спробувати ще раз",
                onAction = viewModel::load
            )
            state.places.isEmpty() -> MessageBox(
                message = "Поки що немає закладів, де подають цю страву."
            )
            else -> LazyColumn(
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Заклади вже відсортовані за ціною (від найдешевшого)
                items(state.places, key = { it.place.id }) { item -> PlaceCard(item) }
            }
        }
    }
}

@Composable
private fun PlaceCard(item: PlaceWithPrice) {
    val context = LocalContext.current
    val place = item.place

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(FoodColors.CardPlaces)
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = place.name,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = FoodColors.TextPrimary
                )
                if (!place.address.isNullOrBlank()) {
                    Text(
                        text = place.address,
                        fontSize = 13.sp,
                        color = FoodColors.TextSecondary,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
            formatPrice(item.price)?.let { price ->
                Spacer(Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(FoodColors.IconBadge)
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = price,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = FoodColors.Green
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LinkPill(label = "Маршрут", icon = R.drawable.ic_pin) { context.openMap(place) }
            place.deliveryUrl(DeliveryService.GLOVO)?.let { url ->
                LinkPill(label = "Glovo") { context.openUrl(url) }
            }
            place.deliveryUrl(DeliveryService.BOLT)?.let { url ->
                LinkPill(label = "Bolt") { context.openUrl(url) }
            }
        }
    }
}
