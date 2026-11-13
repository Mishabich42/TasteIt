package com.example.tesltls.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.tesltls.R
import com.example.tesltls.di.ServiceLocator
import com.example.tesltls.domain.model.AppLanguage
import com.example.tesltls.ui.components.AppIcon
import com.example.tesltls.ui.components.ScreenHeader
import com.example.tesltls.ui.strings.LocalAppStrings
import com.example.tesltls.ui.theme.FoodColors

@Composable
fun ProfileScreen() {
    val strings = LocalAppStrings.current
    val localStore = ServiceLocator.localStore
    val currentLanguage by localStore.selectedLanguage.collectAsStateWithLifecycle()

    val userId by produceState<String?>(initialValue = null) {
        value = try {
            ServiceLocator.ensureUserId()
        } catch (_: Exception) {
            null
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        contentPadding = PaddingValues(bottom = 32.dp)
    ) {
        item {
            ScreenHeader(title = strings.profileTitle, subtitle = strings.profileSub)
            Spacer(Modifier.height(16.dp))
        }

        // ── Картка акаунта ──
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(FoodColors.CardGreen)
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(FoodColors.IconBadge),
                    contentAlignment = Alignment.Center
                ) {
                    AppIcon(R.drawable.ic_person, tint = FoodColors.Green, size = 26.dp)
                }
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(
                        text = strings.guest,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = FoodColors.TextPrimary
                    )
                    Text(
                        text = userId?.let { "ID: ${it.take(8)}…" } ?: strings.connecting,
                        fontSize = 13.sp,
                        color = FoodColors.TextSecondary
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
        }

        // ── Блок Google Translate (Gemini API) ──
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(FoodColors.CardNeutral)
                    .padding(18.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = strings.googleTranslate,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = FoodColors.TextPrimary
                            )
                            Spacer(Modifier.width(8.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(FoodColors.GreenSoft)
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "Gemini API",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = FoodColors.Green
                                )
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = strings.autoTranslateSub,
                            fontSize = 13.sp,
                            color = FoodColors.TextSecondary,
                            lineHeight = 18.sp
                        )
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
        }

        // ── Блок Темної теми ──
        item {
            val isDarkTheme by localStore.darkThemeEnabled.collectAsStateWithLifecycle()
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(FoodColors.CardNeutral)
                    .padding(18.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = "Темна тема",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = FoodColors.TextPrimary
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Увімкнути темне оформлення інтерфейсу",
                            fontSize = 13.sp,
                            color = FoodColors.TextSecondary,
                            lineHeight = 18.sp
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Switch(
                        checked = isDarkTheme,
                        onCheckedChange = { localStore.setDarkTheme(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = FoodColors.Green,
                            uncheckedThumbColor = Color.White,
                            uncheckedTrackColor = FoodColors.Divider
                        )
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
        }

        // ── Вибір мови перекладу (Випадаючий список) ──
        item {
            Text(
                text = strings.languageSectionTitle,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = FoodColors.TextPrimary,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            var expanded by remember { mutableStateOf(false) }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(FoodColors.CardNeutral)
                    .clickable { expanded = true }
                    .padding(horizontal = 16.dp, vertical = 16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = currentLanguage.flagEmoji,
                        fontSize = 24.sp
                    )
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = currentLanguage.displayName,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = FoodColors.TextPrimary
                        )
                        Text(
                            text = currentLanguage.code.uppercase(),
                            fontSize = 11.sp,
                            color = FoodColors.TextSecondary
                        )
                    }
                    AppIcon(
                        id = R.drawable.ic_chevron_right,
                        tint = FoodColors.TextSecondary,
                        size = 20.dp
                    )
                }

                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                    modifier = Modifier
                        .background(FoodColors.CardNeutral)
                        .width(280.dp)
                ) {
                    AppLanguage.entries.forEach { language ->
                        val isSelected = language == currentLanguage
                        DropdownMenuItem(
                            text = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(text = language.flagEmoji, fontSize = 22.sp)
                                    Spacer(Modifier.width(12.dp))
                                    Text(
                                        text = language.displayName,
                                        fontSize = 15.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = FoodColors.TextPrimary,
                                        modifier = Modifier.weight(1f)
                                    )
                                    if (isSelected) {
                                        AppIcon(
                                            id = R.drawable.ic_check,
                                            tint = FoodColors.Green,
                                            size = 16.dp
                                        )
                                    }
                                }
                            },
                            onClick = {
                                localStore.setLanguage(language)
                                expanded = false
                            }
                        )
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        item {
            Spacer(Modifier.height(16.dp))
            Text(
                text = strings.profileFooterNote,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                color = FoodColors.TextMuted,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }
    }
}
