package com.bulanebula.vpn

import android.os.Bundle
import android.util.Base64
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            BulaNeBulaTheme {
                VpnMainScreen()
            }
        }
    }
}

enum class VpnState { IDLE, CONNECTING, CONNECTED, ERROR }

data class VpnServer(val name: String, val address: String)

@Composable
fun VpnMainScreen() {
    var vpnState by remember { mutableStateOf(VpnState.IDLE) }
    var subLink by remember { mutableStateOf("") }
    var servers by remember { mutableStateOf<List<VpnServer>>(emptyList()) }
    var selectedServer by remember { mutableStateOf<VpnServer?>(null) }
    var showSubDialog by remember { mutableStateOf(false) }
    var showServerDialog by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    
    val coroutineScope = rememberCoroutineScope()

    val buttonColor by animateColorAsState(
        targetValue = when (vpnState) {
            VpnState.IDLE -> Color(0xFFFF3344)
            VpnState.CONNECTING -> Color(0xFFFF8800)
            VpnState.CONNECTED -> Color(0xFF00FF88)
            VpnState.ERROR -> Color(0xFF7A1C1C)
        },
        animationSpec = tween(durationMillis = 300),
        label = "ColorAnim"
    )

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFF0D0D12)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Шапка приложения
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "BulaNeBula VPN",
                        color = Color(0xFF00FF88),
                        fontSize = 24.sp,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                    )
                    
                    Button(
                        onClick = { showSubDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1A1A24))
                    ) {
                        Text(text = "📥 Подписка", color = Color(0xFF00FF88), fontSize = 14.sp)
                    }
                }

                // Выбранный узел
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { showServerDialog = true },
                    color = Color(0xFF1A1A24)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "🌍 Активный узел:",
                            color = Color(0xFF8C8C9E),
                            fontSize = 12.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (isLoading) "Загрузка серверов..." else (selectedServer?.name ?: (if (servers.isEmpty()) "Нажмите «Подписка» для ввода ссылки 👆" else "Выберите узел из списка")),
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                        )
                    }
                }

                // Центральная кнопка (Томат / Паста / Шмяк)
                Box(
                    modifier = Modifier
                        .size(200.dp)
                        .clickable {
                            if (subLink.isBlank() || selectedServer == null) {
                                vpnState = VpnState.ERROR
                            } else if (vpnState == VpnState.IDLE || vpnState == VpnState.ERROR) {
                                vpnState = VpnState.CONNECTING
                                coroutineScope.launch {
                                    delay(2000)
                                    vpnState = VpnState.CONNECTED 
                                }
                            } else if (vpnState == VpnState.CONNECTED) {
                                vpnState = VpnState.IDLE
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        drawTomatoOrPaste(vpnState, buttonColor)
                    }
                    
                    Text(
                        text = when (vpnState) {
                            VpnState.IDLE -> "ТАП!"
                            VpnState.CONNECTING -> "ПАСТА..."
                            VpnState.CONNECTED -> "В СЕТИ"
                            VpnState.ERROR -> "ШМЯК!"
                        },
                        color = Color.White,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                        fontSize = 18.sp
                    )
                }

                // Статусный текст
                Text(
                    text = when (vpnState) {
                        VpnState.IDLE -> if (servers.isEmpty()) "Добавьте ссылку подписки, чтобы появились узлы" else "Нажмите для запуска туннеля"
                        VpnState.CONNECTING -> "Трансформируем соединение..."
                        VpnState.CONNECTED -> "Туннель защищен. Концентрат активен!"
                        VpnState.ERROR -> if (errorMessage.isNotBlank()) errorMessage else "Нет подписки или узла! Помидорка шмякнулась 🍅"
                    },
                    color = Color(0xFF8C8C9E),
                    fontSize = 14.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }

            // Окно ввода ссылки подписки
            if (showSubDialog) {
                AlertDialog(
                    onDismissRequest = { showSubDialog = false },
                    title = { Text("Ссылка подписки", color = Color.White) },
                    text = {
                        Column {
                            Text(
                                text = "Введите URL вашей подписки (серверы загружатся автоматически):",
                                color = Color(0xFF8C8C9E),
                                fontSize = 13.sp
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            OutlinedTextField(
                                value = subLink,
                                onValueChange = { subLink = it },
                                placeholder = { Text("https://...", color = Color.Gray) },
                                modifier = Modifier.fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color(0xFF00FF88),
                                    unfocusedBorderColor = Color(0xFF3F3F4E),
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White
                                )
                            )
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                if (subLink.isNotBlank()) {
                                    showSubDialog = false
                                    isLoading = true
                                    errorMessage = ""
                                    
                                    // Запускаем асинхронную загрузку подписки в фоновом потоке
                                    coroutineScope.launch {
                                        val fetchedServers = fetchSubscriptionServers(subLink)
                                        isLoading = false
                                        if (fetchedServers.isNotEmpty()) {
                                            servers = fetchedServers
                                            selectedServer = servers.firstOrNull()
                                            if (vpnState == VpnState.ERROR) vpnState = VpnState.IDLE
                                        } else {
                                            vpnState = VpnState.ERROR
                                            errorMessage = "Не удалось загрузить узлы из подписки"
                                        }
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00FF88))
                        ) {
                            Text("Загрузить", color = Color.Black, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showSubDialog = false }) {
                            Text("Отмена", color = Color.White)
                        }
                    },
                    containerColor = Color(0xFF1A1A24)
                )
            }

            // Окно выбора узла
            if (showServerDialog) {
                AlertDialog(
                    onDismissRequest = { showServerDialog = false },
                    title = { Text("Список узлов (${servers.size})", color = Color.White) },
                    text = {
                        Box(
                            modifier = Modifier
                                .height(300.dp)
                                .fillMaxWidth()
                        ) {
                            if (servers.isEmpty()) {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text("Список пуст. Сначала загрузите ссылку подписки.", color = Color(0xFF8C8C9E), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                                }
                            } else {
                                LazyColumn(
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    items(servers) { server ->
                                        Surface(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(8.dp))
                                                .clickable {
                                                    selectedServer = server
                                                    showServerDialog = false
                                                },
                                            color = Color(0xFF252533)
                                        ) {
                                            Column(modifier = Modifier.padding(12.dp)) {
                                                Text(
                                                    text = server.name,
                                                    color = Color.White,
                                                    fontSize = 14.sp,
                                                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                                                )
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Text(
                                                    text = server.address,
                                                    color = Color(0xFF8C8C9E),
                                                    fontSize = 11.sp,
                                                    maxLines = 1
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { showServerDialog = false }) {
                            Text("Закрыть", color = Color.White)
                        }
                    },
                    containerColor = Color(0xFF1A1A24)
                )
            }
        }
    }
}

// Функция для скачивания и парсинга подписки по URL
suspend fun fetchSubscriptionServers(urlString: String): List<VpnServer> = withContext(Dispatchers.IO) {
    try {
        val url = URL(urlString)
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 5000
        connection.readTimeout = 5000

        if (connection.responseCode == 200) {
            val rawBody = connection.inputStream.bufferedReader().use { it.readText() }.trim()
            
            // Пробуем раскодировать из Base64 (стандартный формат подписок)
            val decodedString = try {
                val bytes = Base64.decode(rawBody, Base64.DEFAULT)
                String(bytes, Charsets.UTF_8)
            } catch (e: Exception) {
                rawBody // Если это не base64, используем текст как есть
            }

            // Разбиваем по строкам (каждая строка — отдельный конфиг/сервер)
            val lines = decodedString.split("\n", "\r\n").filter { it.isNotBlank() }
            
            val parsedList = mutableListOf<VpnServer>()
            for ((index, line) in lines.withIndex()) {
                parsedList.add(VpnServer("Узел #${index + 1}", line.trim()))
            }
            return@withContext parsedList
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    emptyList()
}

fun DrawScope.drawTomatoOrPaste(state: VpnState, color: Color) {
    val width = size.width
    val height = size.height

    when (state) {
        VpnState.CONNECTING, VpnState.CONNECTED -> {
            drawRoundRect(
                color = color,
                topLeft = Offset(width * 0.25f, height * 0.2f),
                size = Size(width * 0.5f, height * 0.7f),
                cornerRadius = CornerRadius(16f, 16f)
            )
            drawRoundRect(
                color = Color(0xFFCCCCCC),
                topLeft = Offset(width * 0.22f, height * 0.15f),
                size = Size(width * 0.56f, height * 0.08f),
                cornerRadius = CornerRadius(8f, 8f)
            )
        }
        VpnState.ERROR -> {
            drawOval(
                color = color,
                topLeft = Offset(width * 0.15f, height * 0.45f),
                size = Size(width * 0.7f, height * 0.35f)
            )
            drawCircle(
                color = Color(0xFF00FF88),
                radius = width * 0.06f,
                center = Offset(width * 0.5f, height * 0.4f)
            )
        }
        VpnState.IDLE -> {
            drawCircle(
                color = color,
                radius = width * 0.35f,
                center = Offset(width * 0.5f, height * 0.55f)
            )
            drawCircle(
                color = Color(0xFF00FF88),
                radius = width * 0.08f,
                center = Offset(width * 0.5f, height * 0.25f)
            )
        }
    }
}

@Composable
fun BulaNeBulaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(),
        content = content
    )
}
