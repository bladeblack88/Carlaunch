package com.drivedeck.mobile

import android.Manifest
import android.app.Notification
import android.bluetooth.BluetoothManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.location.Criteria
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.coroutines.resume

private val Bg = Color(0xFF090E16)
private val Panel = Color(0xFF151F2D)
private val Muted = Color(0xFF9AA8B8)
private val Accent = Color(0xFF75D6C2)

private data class InstalledApp(val label: String, val packageName: String, val icon: Bitmap)
private data class WeatherInfo(val temperature: String, val condition: String, val wind: String)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { DriveDeckHome() }
    }
}

class DriveDeckNotificationListener : NotificationListenerService() {
    override fun onNotificationPosted(sbn: StatusBarNotification?) {}
    override fun onNotificationRemoved(sbn: StatusBarNotification?) {}
}

private fun Drawable.toBitmap(): Bitmap {
    val bitmap = Bitmap.createBitmap(intrinsicWidth.coerceAtLeast(1), intrinsicHeight.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    setBounds(0, 0, canvas.width, canvas.height)
    draw(canvas)
    return bitmap
}

private fun weatherDescription(code: Int): String = when (code) {
    0 -> "Ясно"
    1, 2 -> "Переменная облачность"
    3 -> "Облачно"
    45, 48 -> "Туман"
    51, 53, 55 -> "Морось"
    61, 63, 65 -> "Дождь"
    71, 73, 75, 77 -> "Снег"
    80, 81, 82 -> "Ливень"
    95, 96, 99 -> "Гроза"
    else -> "Погода"
}

private suspend fun getCurrentLocation(context: Context): Location? = withContext(Dispatchers.Main) {
    val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    val provider = manager.getProviders(true).firstOrNull { it == LocationManager.NETWORK_PROVIDER }
        ?: manager.getProviders(true).firstOrNull() ?: return@withContext null
    @Suppress("MissingPermission")
    val cached = try { manager.getLastKnownLocation(provider) } catch (_: SecurityException) { null }
    if (cached != null) return@withContext cached
    withTimeoutOrNull(12000) {
        suspendCancellableCoroutine { continuation ->
            val listener = object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    if (continuation.isActive) continuation.resume(location)
                    try { manager.removeUpdates(this) } catch (_: Exception) {}
                }
            }
            try {
                @Suppress("MissingPermission")
                manager.requestSingleUpdate(provider, listener, Looper.getMainLooper())
                continuation.invokeOnCancellation { try { manager.removeUpdates(listener) } catch (_: Exception) {} }
            } catch (_: Exception) {
                if (continuation.isActive) continuation.resume(null)
            }
        }
    }
}

private suspend fun loadWeather(location: Location): WeatherInfo = withContext(Dispatchers.IO) {
    val url = URL("https://api.open-meteo.com/v1/forecast?latitude=${location.latitude}&longitude=${location.longitude}&current=temperature_2m,weather_code,wind_speed_10m&timezone=auto")
    val connection = (url.openConnection() as HttpURLConnection).apply {
        connectTimeout = 10000
        readTimeout = 10000
        requestMethod = "GET"
    }
    try {
        val json = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
        val current = json.getJSONObject("current")
        WeatherInfo(
            "${current.getDouble("temperature_2m").toInt()}°",
            weatherDescription(current.getInt("weather_code")),
            "${current.getDouble("wind_speed_10m").toInt()} км/ч"
        )
    } finally { connection.disconnect() }
}

@Composable
private fun DriveDeckHome() {
    val context = LocalContext.current
    val activity = context as ComponentActivity
    val prefs = remember { context.getSharedPreferences("drivedeck", 0) }
    var tab by remember { mutableStateOf("Главная") }
    var choosing by remember { mutableStateOf(false) }
    var apps by remember { mutableStateOf<List<InstalledApp>>(emptyList()) }
    var selected by remember { mutableStateOf(prefs.getStringSet("selected_apps", emptySet())?.toSet() ?: emptySet()) }
    var loading by remember { mutableStateOf(true) }
    var columns by remember { mutableIntStateOf(prefs.getInt("columns", if (context.resources.configuration.screenWidthDp >= 700) 5 else 3).coerceIn(3, 5)) }
    var clock by remember { mutableStateOf(LocalDateTime.now()) }
    var weather by remember { mutableStateOf<WeatherInfo?>(null) }
    var weatherMessage by remember { mutableStateOf("Нажми обновить") }
    var weatherLoading by remember { mutableStateOf(false) }
    var bluetoothEnabled by remember { mutableStateOf(false) }
    var bluetoothName by remember { mutableStateOf("Проверка Bluetooth…") }
    var mediaController by remember { mutableStateOf<MediaController?>(null) }
    var trackTitle by remember { mutableStateOf("Нет активного трека") }
    var trackArtist by remember { mutableStateOf("Запусти музыку в любом приложении") }
    var isPlaying by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        if (grants[Manifest.permission.ACCESS_FINE_LOCATION] == true || grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true) {
            // Weather is refreshed by the dashboard button.
        }
        if (Build.VERSION.SDK_INT >= 31 && grants[Manifest.permission.BLUETOOTH_CONNECT] == true) {
            refreshBluetooth(context) { enabled, name -> bluetoothEnabled = enabled; bluetoothName = name }
        }
    }
    val homeApps = apps.filter { it.packageName in selected }

    LaunchedEffect(Unit) {
        apps = withContext(Dispatchers.IO) {
            val query = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            @Suppress("DEPRECATION")
            context.packageManager.queryIntentActivities(query, 0).mapNotNull { info ->
                try {
                    InstalledApp(info.loadLabel(context.packageManager).toString(), info.activityInfo.packageName,
                        info.loadIcon(context.packageManager).toBitmap())
                } catch (_: Exception) { null }
            }.distinctBy { it.packageName }.sortedBy { it.label.lowercase() }
        }
        if (selected.isEmpty()) selected = apps.filter { it.packageName != context.packageName }.take(6).map { it.packageName }.toSet()
        loading = false
        refreshBluetooth(context) { enabled, name -> bluetoothEnabled = enabled; bluetoothName = name }
    }
    LaunchedEffect(Unit) {
        while (true) { clock = LocalDateTime.now(); delay(1000) }
    }
    LaunchedEffect(Unit) {
        while (true) {
            try {
                val manager = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
                val component = ComponentName(context, DriveDeckNotificationListener::class.java)
                val controller = manager.getActiveSessions(component).firstOrNull()
                mediaController = controller
                val metadata = controller?.metadata
                trackTitle = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: "Нет активного трека"
                trackArtist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST)
                    ?: metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST) ?: "Запусти музыку в любом приложении"
                isPlaying = controller?.playbackState?.state == android.media.session.PlaybackState.STATE_PLAYING
            } catch (_: SecurityException) {
                mediaController = null
                trackTitle = "Нужно разрешение"
                trackArtist = "Разреши доступ к уведомлениям"
            } catch (_: Exception) {}
            delay(1800)
        }
    }

    fun saveSelection(next: Set<String>) {
        selected = next
        prefs.edit().putStringSet("selected_apps", next).apply()
    }
    fun openNotificationAccess() {
        context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
    }
    fun refreshWeather() {
        if (context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION))
            weatherMessage = "Разреши доступ к местоположению"
            return
        }
        weatherLoading = true
        weatherMessage = "Определяем местоположение…"
        // The request is performed in a lifecycle-aware Compose coroutine below.
    }

    MaterialTheme(colorScheme = darkColorScheme(background = Bg, surface = Panel, primary = Accent,
        onBackground = Color.White, onSurface = Color.White)) {
        Surface(Modifier.fillMaxSize(), color = Bg) {
            Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("DRIVEDECK", color = Accent, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                        Text("Твой телефон. Твой ритм.", color = Muted, fontSize = 12.sp)
                    }
                    IconButton(onClick = { tab = "Настройки"; choosing = false }) { Icon(Icons.Default.Settings, "Настройки") }
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
                        if (loading) LoadingBox() else LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(apps, key = { it.packageName }) { app ->
                                val checked = app.packageName in selected
                                Row(Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(14.dp))
                                    .clickable { saveSelection(if (checked) selected - app.packageName else selected + app.packageName) }
                                    .padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Image(app.icon.asImageBitmap(), app.label, Modifier.size(38.dp))
                                    Text(app.label, Modifier.weight(1f).padding(start = 12.dp), fontSize = 14.sp)
                                    Checkbox(checked, { value -> saveSelection(if (value) selected + app.packageName else selected - app.packageName) },
                                        colors = CheckboxDefaults.colors(checkedColor = Accent))
                                }
                            }
                        }
                    }
                    tab == "Настройки" -> {
                        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("Настройки лаунчера", fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                            SettingsCard("Главный экран", "Выбрано приложений: ${selected.size}") {
                                Button(onClick = { choosing = true }, colors = ButtonDefaults.buttonColors(containerColor = Accent)) {
                                    Icon(Icons.Default.Apps, null, tint = Bg); Spacer(Modifier.width(8.dp)); Text("Выбрать приложения", color = Bg)
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("Колонки сетки", Modifier.weight(1f), color = Muted)
                                    IconButton(onClick = { columns = (columns - 1).coerceAtLeast(3); prefs.edit().putInt("columns", columns).apply() }) { Icon(Icons.Default.Remove, "Меньше") }
                                    Text("$columns", fontWeight = FontWeight.Bold)
                                    IconButton(onClick = { columns = (columns + 1).coerceAtMost(5); prefs.edit().putInt("columns", columns).apply() }) { Icon(Icons.Default.Add, "Больше") }
                                }
                            }
                            SettingsCard("Интеграции", "Музыка, Bluetooth и погода") {
                                Button(onClick = { openNotificationAccess() }, colors = ButtonDefaults.buttonColors(containerColor = Panel)) {
                                    Icon(Icons.Default.Notifications, null); Spacer(Modifier.width(8.dp)); Text("Разрешить управление музыкой")
                                }
                                Button(onClick = { context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) }, colors = ButtonDefaults.buttonColors(containerColor = Panel)) {
                                    Icon(Icons.Default.Bluetooth, null); Spacer(Modifier.width(8.dp)); Text("Настройки Bluetooth")
                                }
                                Button(onClick = { context.startActivity(Intent(Intent.ACTION_SET_WALLPAPER)) }, colors = ButtonDefaults.buttonColors(containerColor = Panel)) {
                                    Icon(Icons.Default.Wallpaper, null); Spacer(Modifier.width(8.dp)); Text("Изменить обои")
                                }
                            }
                            SettingsCard("Автомобильный режим", "Назначь DriveDeck Mobile приложением главного экрана Android в системных настройках.") {
                                Button(onClick = { context.startActivity(Intent(Settings.ACTION_HOME_SETTINGS)) }, colors = ButtonDefaults.buttonColors(containerColor = Accent)) {
                                    Text("Выбрать лаунчер по умолчанию", color = Bg)
                                }
                            }
                        }
                    }
                    tab == "Приложения" -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Все приложения", Modifier.weight(1f), fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                            IconButton(onClick = { choosing = true }) { Icon(Icons.Default.Edit, "Избранное", tint = Accent) }
                        }
                        if (loading) LoadingBox() else AppGrid(apps, columns) { app ->
                            context.packageManager.getLaunchIntentForPackage(app.packageName)?.let(context::startActivity)
                        }
                    }
                    else -> {
                        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(verticalAlignment = Alignment.Bottom) {
                                Column(Modifier.weight(1f)) {
                                    Text(clock.format(DateTimeFormatter.ofPattern("HH:mm")), fontSize = 62.sp, fontWeight = FontWeight.Light, lineHeight = 64.sp)
                                    Text(clock.format(DateTimeFormatter.ofPattern("EEEE, d MMMM")), color = Muted, fontSize = 13.sp)
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(weather?.temperature ?: "--°", fontSize = 32.sp, color = Accent, fontWeight = FontWeight.Light)
                                    Text(weather?.condition ?: "Погода", fontSize = 12.sp)
                                    Text(weather?.wind ?: "Нажми обновить", color = Muted, fontSize = 10.sp)
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                DashboardCard("ПОГОДА", weatherMessage, Icons.Default.Cloud, Modifier.weight(1f),
                                    action = { if (!weatherLoading) refreshWeather() }, actionLabel = if (weatherLoading) "Загрузка…" else "Обновить")
                                DashboardCard("BLUETOOTH", bluetoothName, Icons.Default.Bluetooth, Modifier.weight(1f),
                                    action = {
                                        if (Build.VERSION.SDK_INT >= 31 && context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                                            permissionLauncher.launch(arrayOf(Manifest.permission.BLUETOOTH_CONNECT))
                                        } else context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
                                    },
                                    actionLabel = if (bluetoothEnabled) "Управление" else "Подключить")
                            }
                            Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(22.dp)) {
                                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.MusicNote, tint = Accent, contentDescription = null)
                                        Text("СЕЙЧАС ИГРАЕТ", Modifier.weight(1f).padding(start = 8.dp), color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                                        if (mediaController == null) TextButton(onClick = { openNotificationAccess() }) { Text("Разрешить", color = Accent) }
                                    }
                                    Text(trackTitle, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                                    Text(trackArtist, color = Muted, fontSize = 12.sp, maxLines = 1)
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                                        IconButton(onClick = { mediaController?.transportControls?.skipToPrevious() }) { Icon(Icons.Default.SkipPrevious, "Предыдущий трек", tint = Color.White) }
                                        IconButton(onClick = {
                                            val controls = mediaController?.transportControls
                                            if (isPlaying) controls?.pause() else controls?.play()
                                        }, modifier = Modifier.size(52.dp)) {
                                            Icon(if (isPlaying) Icons.Default.PauseCircleFilled else Icons.Default.PlayCircleFilled,
                                                "Воспроизведение", tint = Accent, modifier = Modifier.size(46.dp))
                                        }
                                        IconButton(onClick = { mediaController?.transportControls?.skipToNext() }) { Icon(Icons.Default.SkipNext, "Следующий трек", tint = Color.White) }
                                    }
                                }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Быстрый запуск", Modifier.weight(1f), fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
                                TextButton(onClick = { choosing = true }) { Text("Изменить", color = Accent) }
                            }
                            if (loading) LoadingBox() else if (homeApps.isEmpty()) {
                                Text("Добавь приложения на главный экран", color = Muted)
                            } else AppGrid(homeApps, columns) { app ->
                                context.packageManager.getLaunchIntentForPackage(app.packageName)?.let(context::startActivity)
                            }
                        }
                    }
                }

                if (!choosing) {
                    NavigationBar(containerColor = Panel, tonalElevation = 0.dp) {
                        listOf("Главная", "Приложения", "Настройки").forEach { item ->
                            NavigationBarItem(selected = tab == item, onClick = { tab = item }, icon = {
                                Icon(when (item) { "Главная" -> Icons.Default.Home; "Приложения" -> Icons.Default.GridView; else -> Icons.Default.Settings }, item)
                            }, label = { Text(item, fontSize = 10.sp) },
                                colors = NavigationBarItemDefaults.colors(selectedIconColor = Accent, selectedTextColor = Accent,
                                    indicatorColor = Accent.copy(alpha = 0.14f), unselectedIconColor = Muted, unselectedTextColor = Muted))
                        }
                    }
                }
            }
        }
    }

    // Weather refresh is triggered after the permission result or by tapping the dashboard card.
    LaunchedEffect(weatherLoading) {
        if (weatherLoading) {
            try {
                val location = getCurrentLocation(context)
                if (location == null) weatherMessage = "Не удалось определить местоположение"
                else {
                    weather = loadWeather(location)
                    weatherMessage = "Обновлено по вашему местоположению"
                }
            } catch (e: Exception) {
                weatherMessage = "Нет данных. Проверь интернет"
            } finally { weatherLoading = false }
        }
    }
}

private fun refreshBluetooth(context: Context, update: (Boolean, String) -> Unit) {
    try {
        if (Build.VERSION.SDK_INT >= 31 &&
            context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            update(false, "Нужно разрешение")
            return
        }
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val adapter = manager.adapter
        val enabled = adapter?.isEnabled == true
        update(enabled, if (enabled) "Bluetooth включён" else "Bluetooth выключен")
    } catch (_: SecurityException) { update(false, "Нет разрешения Bluetooth") }
    catch (_: Exception) { update(false, "Bluetooth недоступен") }
}

@Composable
private fun AppGrid(apps: List<InstalledApp>, columns: Int, onLaunch: (InstalledApp) -> Unit) {
    LazyVerticalGrid(columns = GridCells.Fixed(columns), modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp, max = 520.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        gridItems(apps, key = { it.packageName }) { app ->
            Column(Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(18.dp)).clickable { onLaunch(app) }
                .padding(vertical = 12.dp, horizontal = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Image(app.icon.asImageBitmap(), app.label, Modifier.size(44.dp))
                Spacer(Modifier.height(6.dp))
                Text(app.label, fontSize = 11.sp, maxLines = 2, color = Color.White)
            }
        }
    }
}

@Composable
private fun DashboardCard(title: String, value: String, icon: ImageVector, modifier: Modifier = Modifier,
                          action: () -> Unit, actionLabel: String) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(icon, null, tint = Accent, modifier = Modifier.size(22.dp))
            Text(title, color = Muted, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            Text(value, fontSize = 12.sp, fontWeight = FontWeight.Medium, minLines = 2, maxLines = 2)
            TextButton(onClick = action, contentPadding = PaddingValues(0.dp)) { Text(actionLabel, color = Accent, fontSize = 11.sp) }
        }
    }
}

@Composable
private fun SettingsCard(title: String, description: String, content: @Composable ColumnScope.() -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            Text(description, color = Muted, fontSize = 12.sp)
            content()
        }
    }
}

@Composable
private fun LoadingBox() {
    Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = Accent)
    }
}
