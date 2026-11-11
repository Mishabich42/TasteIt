package com.example.tesltls.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.tesltls.R
import com.example.tesltls.di.ServiceLocator
import com.example.tesltls.ui.components.AppIcon
import com.example.tesltls.ui.components.ScreenHeader
import com.example.tesltls.ui.theme.FoodColors

@Composable
fun ProfileScreen() {
    // id поточного (анонімного) користувача; null, поки вхід не завершився
    val userId by produceState<String?>(initialValue = null) {
        value = try {
            ServiceLocator.ensureUserId()
        } catch (e: Exception) {
            null
        }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        ScreenHeader(title = "Профіль", subtitle = "Ваш акаунт у Food Tinder")
        Spacer(Modifier.height(16.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(FoodColors.CardGreen)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(52.dp).clip(CircleShape).background(FoodColors.IconBadge),
                contentAlignment = Alignment.Center
            ) {
                AppIcon(R.drawable.ic_person, tint = FoodColors.Green, size = 26.dp)
            }
            Spacer(Modifier.width(14.dp))
            Column {
                Text(
                    text = "Гість",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = FoodColors.TextPrimary
                )
                Text(
                    text = userId?.let { "ID: ${it.take(8)}…" } ?: "Підключення…",
                    fontSize = 13.sp,
                    color = FoodColors.TextSecondary
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        Text(
            text = "Ви користуєтесь застосунком анонімно: ваші свайпи та улюблені страви " +
                "зберігаються на цьому пристрої. Вхід через email чи Google можна додати пізніше.",
            fontSize = 14.sp,
            lineHeight = 20.sp,
            color = FoodColors.TextSecondary
        )
    }
}
