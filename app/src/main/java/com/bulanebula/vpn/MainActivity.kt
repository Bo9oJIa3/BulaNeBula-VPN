package com.bulanebula.vpn

import android.app.Activity
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.util.Base64
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
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
import java.net.URLDecoder

class MainActivity : ComponentActivity() {

    private var onVpnPermissionResult: ((Boolean) -> Unit)? = null

    private val vpnPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val granted = result.resultCode == Activity.RESULT_OK
        onVpnPermissionResult?.invoke(granted)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            BulaNeBulaTheme {
                VpnMainScreen(
                    onRequestVpnPermission = { callback ->
                        onVpnPermissionResult = callback
                        val intent = VpnService.prepare(this)
                        if (intent != null) {
                            vpnPermissionLauncher.launch(intent)
                        } else {
                            callback(true)
                        }
                    },
                    onStartVpnService = { server ->
                        val serviceIntent = Intent(this, BulaVpnService::class.java).apply {
                            putExtra("SERVER_ADDRESS", server.address)
                        }
                        startService(serviceIntent)
                    },
                    onStopVpnService = {
                        val serviceIntent = Intent(this, BulaVpnService::class.java)
                        stopService(serviceIntent)
                    }
                )
            }
        }
    }
}

enum class VpnState { IDLE, CONNECTING, CONNECTED, ERROR }

data class VpnServer(val name: String, val address: String)

@Composable
fun VpnMainScreen(
    onRequestVpnPermission: ((Boolean) -> Unit) -> Unit,
    onStartVpnService: (VpnServer) -> Unit,
    onStopVpnService: () -> Unit
) {
    var vpnState by remember { mutableStateOf(VpnState.IDLE) }
    var subLink by remember { mutableStateOf("") }
    var servers by remember { mutableStateOf<List<VpnServer>>(emptyList()) }
    var selectedServer by remember { mutableStateOf<VpnServer?>(null) }
    var showSubDialog by remember { mutableStateOf(false) }
    var showServerDialog by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    
    // Секундомер сеанса (в секундах)
    var sessionSeconds by remember { mutableStateOf(0) }
    
    val coroutineScope = rememberCoroutineScope()

    // Запуск секундомера при подключении
    LaunchedEffect(vpnState) {
        if (vpnState == VpnState.CONNECTED) {
            sessionSeconds = 0
            while (true) {
                delay(1000)
                sessionSeconds++
            }
        }
    }

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
                            text = if (isLoading) "Загружаем подписку..." else (selectedServer?.name ?: (if (servers.isEmpty()) "Нажмите «Подписка» для ввода ссылки 👆" else "Выберите узел из списка")),
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                        )
                    }
                }

                // Центральная кнопка (Томат / Паста / Секундомер / Шмяк)
                Box(
                    modifier = Modifier
                        .size(200.dp)
                        .clickable {
                            if (subLink.isBlank() || selectedServer == null) {
                                vpnState = VpnState.ERROR
                                errorMessage = "Выберите узел и подписку!"
                            } else if (vpnState == VpnState.IDLE || vpnState == VpnState.ERROR) {
                                onRequestVpnPermission { granted ->
                                    if (granted) {
                                        vpnState = VpnState.CONNECTING
                                        coroutineScope.launch {
                                            try {
                                                onStartVpnService(selectedServer!!)
                                                vpnState = VpnState.CONNECTED
                                            } catch (e: Exception) {
                                                vpnState = VpnState.ERROR
                                                errorMessage = "Ошибка запуска туннеля"
                                            }
                                        }
                                    } else {
                                        vpnState = VpnState.ERROR
                                        errorMessage = "Пользователь отклонил VPN-доступ"
                                    }
                                }
                            } else if (vpnState == VpnState.CONNECTED) {
                                onStopVpnService()
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
                            VpnState.CONNECTED -> {
                                val mins = sessionSeconds / 60
                                val secs = sessionSeconds % 60
                                String.format("%02d:%02d", mins, secs)
                            }
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
                        VpnState.CONNECTING -> "Запрос системных прав..."
                        VpnState.CONNECTED -> "Туннель защищен. Секундомер активен!"
                        VpnState.ERROR -> if (errorMessage.isNotBlank()) errorMessage else "Помидорка шмякнулась 🍅"
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
                                text = "Введите URL вашей подписки:",
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
                                    
                                    coroutineScope.launch {
                                        val (fetchedServers, error) = fetchSubscriptionServers(subLink)
                                        isLoading = false
                                        if (fetchedServers.isNotEmpty()) {
                                            servers = fetchedServers
                                            selectedServer = servers.firstOrNull()
                                            if (vpnState == VpnState.ERROR) vpnState = VpnState.IDLE
                                        } else {
                                            vpnState = VpnState.ERROR
                                            errorMessage = error.ifBlank { "Не удалось загрузить узлы" }
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

// Всеядный парсер подписок
suspend fun fetchSubscriptionServers(urlString: String): Pair<List<VpnServer>, String> = withContext(Dispatchers.IO) {
    try {
        val url = URL(urlString)
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 10000
        connection.readTimeout = 10000
        connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
        connection.instanceFollowRedirects = true

        val responseCode = connection.responseCode
        if (responseCode == 200) {
            val rawBody = connection.inputStream.bufferedReader().use { it.readText() }.trim()
            if (rawBody.isBlank()) {
                return@withContext Pair(emptyList(), "Подписка пуста")
            }

            val decodedString = try {
                val cleanBase64 = rawBody.replace("\\s".toRegex(), "")
                val bytes = Base64.decode(cleanBase64, Base64.DEFAULT)
                String(bytes, Charsets.UTF_8)
            } catch (e: Exception) {
                rawBody
            }

            val lines = decodedString.split("\n", "\r\n").map { it.trim() }.filter { it.isNotBlank() }
            val parsedList = mutableListOf<VpnServer>()

            for ((index, line) in lines.withIndex()) {
                val serverName = if (line.contains("#")) {
                    try {
                        val decodedName = URLDecoder.decode(line.substringAfterLast("#"), "UTF-8")
                        if (decodedName.isNotBlank()) decodedName else "Узел #${index + 1}"
                    } catch (e: Exception) {
                        "Узел #${index + 1}"
                    }
                } else {
                    "Узел #${index + 1}"
                }

                parsedList.add(VpnServer(serverName, line))
            }

            if (parsedList.isNotEmpty()) {
                return@withContext Pair(parsedList, "")
            } else {
                return@withContext Pair(emptyList(), "Не найдены узлы в подписке")
            }
        } else {
            return@withContext Pair(emptyList(), "HTTP Ошибка: $responseCode")
        }
    } catch (e: Exception) {
        return@withContext Pair(emptyList(), "Сбой сети: ${e.localizedMessage ?: e.javaClass.simpleName}")
    }
}

fun DrawScope.drawTomatoOrPaste(state: VpnState, color: Color) {
    val width = size.width
    val height = size.height

    when (state) {
        VpnState.                                         
