package com.drivedeck.mobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private val Bg = Color(0xFF0B1018)
private val Panel = Color(0xFF151E2B)
private val Muted = Color(0xFF9AA8B8)
private val Accent = Color(0xFF75D6C2)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { DriveDeckHome() }
    }
}

private data class AppItem(val title: String, val icon: ImageVector, val tint: Color)

@Composable
private fun DriveDeckHome() {
    var tab by remember { mutableStateOf("Главная") }
    val now = LocalDateTime.now()
    val time = now.format(DateTimeFormatter.ofPattern("HH:mm"))
    val date = now.format(DateTimeFormatter.ofPattern("d MMMM yyyy"))

    MaterialTheme(colorScheme = darkColorScheme(
        background = Bg, surface = Panel, primary = Accent,
        onBackground = Color.White, onSurface = Color.White
    )) {
        Surface(Modifier.fillMaxSize(), color = Bg) {
            Column(
                Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 22.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("DRIVEDECK", color = Accent, fontSize = 12.sp,
                            fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                        Text("Твой телефон. Твой ритм.", color = Muted, fontSize = 13.sp)
                    }
                    IconButton(onClick = { tab = "Настройки" }) {
                        Icon(Icons.Default.Settings, contentDescription = "Настройки")
                    }
                }

                Row(verticalAlignment = Alignment.Bottom) {
                    Column(Modifier.weight(1f)) {
                        Text(time, fontSize = 56.sp, fontWeight = FontWeight.Light)
                        Text(date, color = Muted, fontSize = 13.sp)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Icon(Icons.Default.WbCloudy, contentDescription = null,
                            tint = Accent, modifier = Modifier.size(30.dp))
                        Text("Погода", fontWeight = FontWeight.SemiBold)
                        Text("Скоро появится", color = Muted, fontSize = 11.sp)
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatusCard("МУЗЫКА", "Не воспроизводится", Icons.Default.MusicNote, Modifier.weight(1f))
                    StatusCard("СВЯЗЬ", "Bluetooth", Icons.Default.Bluetooth, Modifier.weight(1f))
                }

                Text(
                    when (tab) {
                        "Приложения" -> "Приложения"
                        "Настройки" -> "Настройки"
                        else -> "Быстрый запуск"
                    },
                    fontSize = 18.sp, fontWeight = FontWeight.SemiBold
                )

                if (tab == "Настройки") {
                    Card(colors = CardDefaults.cardColors(containerColor = Panel),
                        shape = RoundedCornerShape(20.dp)) {
                        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("Персонализация", fontWeight = FontWeight.SemiBold)
                            Text("Тёмная тема включена", color = Muted)
                            Text("Виджеты, погода и музыкальные интеграции добавим далее.",
                                color = Muted)
                        }
                    }
                    Spacer(Modifier.weight(1f))
                } else {
                    val apps = listOf(
                        AppItem("Телефон", Icons.Default.Call, Color(0xFF72D6B4)),
                        AppItem("Карты", Icons.Default.Map, Color(0xFF83B7F5)),
                        AppItem("Музыка", Icons.Default.MusicNote, Color(0xFFC49AF5)),
                        AppItem("Камера", Icons.Default.CameraAlt, Color(0xFFF2B879)),
                        AppItem("Сообщения", Icons.Default.Message, Color(0xFF82C9E8)),
                        AppItem("Настройки", Icons.Default.Settings, Color(0xFFADB8C7))
                    )
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(apps) { app ->
                            Column(
                                Modifier.fillMaxWidth()
                                    .background(Panel, RoundedCornerShape(20.dp))
                                    .clickable { tab = if (app.title == "Настройки") "Настройки" else "Приложения" }
                                    .padding(vertical = 18.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Box(Modifier.size(48.dp)
                                    .background(app.tint.copy(alpha = 0.15f), CircleShape),
                                    contentAlignment = Alignment.Center) {
                                    Icon(app.icon, contentDescription = app.title,
                                        tint = app.tint, modifier = Modifier.size(25.dp))
                                }
                                Spacer(Modifier.height(9.dp))
                                Text(app.title, fontSize = 12.sp)
                            }
                        }
                    }
                }

                NavigationBar(containerColor = Panel, tonalElevation = 0.dp) {
                    listOf("Главная", "Приложения", "Настройки").forEach { item ->
                        NavigationBarItem(
                            selected = tab == item,
                            onClick = { tab = item },
                            icon = {
                                Icon(when (item) {
                                    "Главная" -> Icons.Default.Home
                                    "Приложения" -> Icons.Default.GridView
                                    else -> Icons.Default.Settings
                                }, contentDescription = item)
                            },
                            label = { Text(item, fontSize = 10.sp) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Accent, selectedTextColor = Accent,
                                indicatorColor = Accent.copy(alpha = 0.14f),
                                unselectedIconColor = Muted, unselectedTextColor = Muted
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusCard(title: String, value: String, icon: ImageVector, modifier: Modifier = Modifier) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = Panel),
        shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(icon, contentDescription = null, tint = Accent, modifier = Modifier.size(22.dp))
            Text(title, color = Muted, fontSize = 9.sp, fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp)
            Text(value, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        }
    }
}
