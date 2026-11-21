package com.drivedeck.mobile

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private val Bg = Color(0xFF0B1018)
private val Panel = Color(0xFF151E2B)
private val Muted = Color(0xFF9AA8B8)
private val Accent = Color(0xFF75D6C2)

private data class InstalledApp(val label: String, val packageName: String, val icon: Bitmap)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { DriveDeckHome() }
    }
}

private fun Drawable.toBitmap(): Bitmap {
    val bitmap = Bitmap.createBitmap(intrinsicWidth.coerceAtLeast(1), intrinsicHeight.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    setBounds(0, 0, canvas.width, canvas.height)
    draw(canvas)
    return bitmap
}

@Composable
private fun DriveDeckHome() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("drivedeck", 0) }
    var tab by remember { mutableStateOf("Главная") }
    var choosing by remember { mutableStateOf(false) }
    var apps by remember { mutableStateOf<List<InstalledApp>>(emptyList()) }
    var selected by remember { mutableStateOf(prefs.getStringSet("selected_apps", emptySet())?.toSet() ?: emptySet()) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        apps = withContext(Dispatchers.IO) {
            val query = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            @Suppress("DEPRECATION")
            context.packageManager.queryIntentActivities(query, 0).mapNotNull { info ->
                try {
                    InstalledApp(info.loadLabel(context.packageManager).toString(),
                        info.activityInfo.packageName, info.loadIcon(context.packageManager).toBitmap())
                } catch (_: Exception) { null }
            }.distinctBy { it.packageName }.sortedBy { it.label.lowercase() }
        }
        if (selected.isEmpty()) selected = apps.filter { it.packageName != context.packageName }.take(6).map { it.packageName }.toSet()
        loading = false
    }

    fun saveSelection(next: Set<String>) {
        selected = next
        prefs.edit().putStringSet("selected_apps", next).apply()
    }

    val homeApps = apps.filter { it.packageName in selected }
    val now = LocalDateTime.now()
    MaterialTheme(colorScheme = darkColorScheme(background = Bg, surface = Panel, primary = Accent,
        onBackground = Color.White, onSurface = Color.White)) {
        Surface(Modifier.fillMaxSize(), color = Bg) {
            Column(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("DRIVEDECK", color = Accent, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                        Text("Твой телефон. Твой ритм.", color = Muted, fontSize = 13.sp)
                    }
                    IconButton(onClick = { tab = "Настройки"; choosing = false }) {
                        Icon(Icons.Default.Settings, contentDescription = "Настройки")
                    }
                }

                if (!choosing && tab == "Главная") {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Column(Modifier.weight(1f)) {
                            Text(now.format(DateTimeFormatter.ofPattern("HH:mm")), fontSize = 56.sp, fontWeight = FontWeight.Light)
                            Text(now.format(DateTimeFormatter.ofPattern("d MMMM yyyy")), color = Muted, fontSize = 13.sp)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Icon(Icons.Default.WbCloudy, null, tint = Accent, modifier = Modifier.size(28.dp))
                            Text("Погода", fontWeight = FontWeight.SemiBold)
                            Text("Скоро появится", color = Muted, fontSize = 11.sp)
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        StatusCard("МУЗЫКА", "Не воспроизводится", Icons.Default.MusicNote, Modifier.weight(1f))
                        StatusCard("СВЯЗЬ", "Bluetooth", Icons.Default.Bluetooth, Modifier.weight(1f))
                    }
                }

                when {
                    choosing -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { choosing = false }) { Icon(Icons.Default.ArrowBack, "Назад") }
                            Column(Modifier.weight(1f)) {
                                Text("Выбор приложений", fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
                                Text("Отметь приложения для главного экрана", color = Muted, fontSize = 12.sp)
                            }
                            Text("${selected.size}", color = Accent, fontWeight = FontWeight.Bold)
                        }
                        if (loading) {
                            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = Accent)
                            }
                        } else {
                            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                items(apps, key = { it.packageName }) { app ->
                                    val checked = app.packageName in selected
                                    Row(Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(14.dp))
                                        .clickable { saveSelection(if (checked) selected - app.packageName else selected + app.packageName) }
                                        .padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Image(app.icon.asImageBitmap(), app.label, Modifier.size(38.dp))
                                        Text(app.label, Modifier.weight(1f).padding(start = 12.dp), fontSize = 14.sp)
                                        Checkbox(checked, { value ->
                                            saveSelection(if (value) selected + app.packageName else selected - app.packageName)
                                        }, colors = CheckboxDefaults.colors(checkedColor = Accent))
                                    }
                                }
                            }
                        }
                    }
                    tab == "Настройки" -> {
                        Text("Настройки", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                        Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(20.dp)) {
                            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text("Главный экран", fontWeight = FontWeight.SemiBold)
                                Text("Выбрано приложений: ${selected.size}", color = Muted)
                                Button(onClick = { choosing = true }, colors = ButtonDefaults.buttonColors(containerColor = Accent)) {
                                    Icon(Icons.Default.Apps, null, tint = Bg)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Выбрать приложения", color = Bg)
                                }
                            }
                        }
                        Spacer(Modifier.weight(1f))
                    }
                    else -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(if (tab == "Приложения") "Все приложения" else "Быстрый запуск",
                                Modifier.weight(1f), fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
                            if (tab == "Главная") IconButton(onClick = { choosing = true }) {
                                Icon(Icons.Default.Edit, "Выбрать приложения", tint = Accent)
                            }
                        }
                        if (loading) {
                            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = Accent)
                            }
                        } else {
                            val shown = if (tab == "Приложения") apps else homeApps
                            if (shown.isEmpty()) {
                                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                                    Text("Нажми ✎ и выбери приложения", color = Muted)
                                }
                            } else {
                                LazyVerticalGrid(columns = GridCells.Fixed(3), modifier = Modifier.weight(1f),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    gridItems(shown, key = { it.packageName }) { app ->
                                        Column(Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(20.dp))
                                            .clickable {
                                                context.packageManager.getLaunchIntentForPackage(app.packageName)?.let(context::startActivity)
                                            }.padding(vertical = 14.dp, horizontal = 4.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally) {
                                            Image(app.icon.asImageBitmap(), app.label, Modifier.size(48.dp))
                                            Spacer(Modifier.height(8.dp))
                                            Text(app.label, fontSize = 11.sp, maxLines = 2, color = Color.White)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                if (!choosing) {
                    NavigationBar(containerColor = Panel, tonalElevation = 0.dp) {
                        listOf("Главная", "Приложения", "Настройки").forEach { item ->
                            NavigationBarItem(selected = tab == item, onClick = { tab = item },
                                icon = { Icon(when (item) {
                                    "Главная" -> Icons.Default.Home
                                    "Приложения" -> Icons.Default.GridView
                                    else -> Icons.Default.Settings
                                }, item) }, label = { Text(item, fontSize = 10.sp) },
                                colors = NavigationBarItemDefaults.colors(selectedIconColor = Accent,
                                    selectedTextColor = Accent, indicatorColor = Accent.copy(alpha = 0.14f),
                                    unselectedIconColor = Muted, unselectedTextColor = Muted))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusCard(title: String, value: String, icon: ImageVector, modifier: Modifier = Modifier) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(icon, null, tint = Accent, modifier = Modifier.size(22.dp))
            Text(title, color = Muted, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            Text(value, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        }
    }
}
