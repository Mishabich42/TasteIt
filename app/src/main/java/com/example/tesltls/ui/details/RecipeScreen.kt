package com.example.tesltls.ui.details

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.tesltls.R
import com.example.tesltls.ui.common.openUrl
import com.example.tesltls.ui.components.AppIcon
import com.example.tesltls.ui.components.DishImage
import com.example.tesltls.ui.components.LoadingBox
import com.example.tesltls.ui.components.MessageBox
import com.example.tesltls.ui.components.ScreenHeader
import com.example.tesltls.ui.strings.LocalAppStrings
import com.example.tesltls.ui.theme.FoodColors

@Composable
fun RecipeScreen(viewModel: DishDetailsViewModel, onBack: () -> Unit) {
    val strings = LocalAppStrings.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val dish = state.dish

    if (state.isLoading) {
        LoadingBox()
        return
    }
    if (dish == null) {
        MessageBox(
            message = state.error ?: strings.dishNotFound,
            actionLabel = strings.tryAgain,
            onAction = viewModel::load
        )
        return
    }

    Column(
        modifier = Modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(bottom = 24.dp)
    ) {
        ScreenHeader(title = strings.recipeTitle, subtitle = dish.name, onBack = onBack)
        Spacer(Modifier.height(16.dp))

        DishImage(
            url = dish.imageUrl,
            category = dish.category,
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .clip(RoundedCornerShape(24.dp))
        )
        Spacer(Modifier.height(20.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(FoodColors.CardGreen)
                .padding(18.dp)
        ) {
            Text(
                text = strings.stepByStep,
                fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold,
                color = FoodColors.TextPrimary
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = dish.recipeText?.takeIf { it.isNotBlank() } ?: strings.noRecipeText,
                fontSize = 15.sp,
                lineHeight = 22.sp,
                color = FoodColors.TextPrimary
            )
        }

        if (!dish.youtubeUrl.isNullOrBlank()) {
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(FoodColors.CardNeutral)
                    .clickable { context.openUrl(dish.youtubeUrl) }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 30.dp, height = 21.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(FoodColors.YouTubeRed),
                    contentAlignment = Alignment.Center
                ) {
                    AppIcon(R.drawable.ic_play, tint = Color.White, size = 13.dp)
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    text = strings.watchOnYoutube,
                    modifier = Modifier.weight(1f),
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = FoodColors.TextPrimary
                )
                AppIcon(R.drawable.ic_arrow_up_right, tint = FoodColors.Green, size = 18.dp)
            }
        }
    }
}
