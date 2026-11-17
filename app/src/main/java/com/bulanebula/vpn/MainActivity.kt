package com.bulanebula.vpn

import android.app.Activity
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.util.Base64
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
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
            MaterialTheme(colorScheme = lightColorScheme()) {
                MangaVpnScreen(
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
fun MangaVpnScreen(
    onRequestVpnPermission: ((Boolean) -> Unit) -> Unit,
    onStartVpnService: (VpnServer) -> Unit,
    onStopVpnService: () -> Unit
) {
    var vpnState by remember { mutableStateOf(VpnState.IDLE) }
    var subLink by remember { mutableStateOf("") }
    var servers by remember { mutableStateOf<List<VpnServer>>(emptyList()) }
    var selectedServer by remember { mutableStateOf<VpnServer?>(null) }
    var showSubGrid by remember { mutableStateOf(false) }
    var showServerDialog by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    var sessionSeconds by remember { mutableStateOf(0) }
    
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(vpnState) {
        if (vpnState == VpnState.CONNECTED) {
            sessionSeconds = 0
            while (true) {
                delay(1000)
                sessionSeconds++
            }
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color.White
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            
            // Фоновый холст для цветочков сверху и травы снизу
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawMangaEnvironment()
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Шапка с логотипом blnbl и кнопкой подписки
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier.size(90.dp, 36.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            drawBlnblLogo()
                        }
                    }

                    OutlinedButton(
                        onClick = { showSubGrid = true },
                        shape = RoundedCornerShape(4.dp),
                        border = BorderStroke(2.dp, Color.Black),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Black)
                    ) {
                        Text(text = "📥 Подписка", fontSize = 12.sp)
                    }
                }

                // ПЕРЕКЛЮЧАТЕЛЬ ПОСЕРЕДИНЕ
                Box(
                    modifier = Modifier
                        .size(130.dp, 190.dp)
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
                                                errorMessage = "Ошибка"
                                            }
                                        }
                                    } else {
                                        vpnState = VpnState.ERROR
                                        errorMessage = "Нет прав"
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
                        drawMangaSwitchExact(vpnState == VpnState.CONNECTED || vpnState == VpnState.CONNECTING)
                    }
                }

                // ПУНКТИРНЫЙ ПРЯМОУГОЛЬНИК ДЛЯ СПИСКА СЕРВЕРОВ
                val dotPattern = Stroke(
                    width = 4f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 12f), 0f)
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp)
                        .drawBehind {
                            drawRoundRect(
                                color = Color.Black,
                                topLeft = Offset.Zero,
                                size = size,
                                cornerRadius = CornerRadius(12f, 12f),
                                style = dotPattern
                            )
                        }
                        .clickable { showServerDialog = true }
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "⚡ ВЫБОР УЗЛА",
                            color = Color.DarkGray,
                            fontSize = 11.sp,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = if (isLoading) "Загрузка..." else (selectedServer?.name ?: "Нажмите сюда, чтобы выбрать узел"),
                            color = Color.Black,
                            fontSize = 14.sp,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = when (vpnState) {
                                VpnState.CONNECTED -> {
                                    val m = sessionSeconds / 60
                                    val s = sessionSeconds % 60
                                    "Туннель активен (%02d:%02d)".format(m, s)
                                }
                                VpnState.CONNECTING -> "Подключение..."
                                VpnState.ERROR -> errorMessage
                                VpnState.IDLE -> if (servers.isEmpty()) "Сначала добавьте подписку" else "Нажмите для смены"
                            },
                            color = if (vpnState == VpnState.ERROR) Color.Red else Color.Gray,
                            fontSize = 12.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }

            // Окно подписки
            if (showSubGrid) {
                AlertDialog(
                    onDismissRequest = { showSubGrid = false },
                    title = { Text("Ссылка подписки", color = Color.Black) },
                    text = {
                        OutlinedTextField(
                            value = subLink,
                            onValueChange = { subLink = it },
                            placeholder = { Text("https://...") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                if (subLink.isNotBlank()) {
                                    showSubGrid = false
                                    isLoading = true
                                    coroutineScope.launch {
                                        val (fetchedServers, error) = fetchSubscriptionServers(subLink)
                                        isLoading = false
                                        if (fetchedServers.isNotEmpty()) {
                                            servers = fetchedServers
                                            selectedServer = servers.firstOrNull()
                                            if (vpnState == VpnState.ERROR) vpnState = VpnState.IDLE
                                        } else {
                                            vpnState = VpnState.ERROR
                                            errorMessage = error
                                        }
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Black)
                        ) {
                            Text("Загрузить", color = Color.White)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showSubGrid = false }) {
                            Text("Отмена", color = Color.Black)
                        }
                    },
                    containerColor = Color.White
                )
            }

            // Окно выбора узла
            if (showServerDialog) {
                AlertDialog(
                    onDismissRequest = { showServerDialog = false },
                    title = { Text("Список узлов (${servers.size})", color = Color.Black) },
                    text = {
                        Box(modifier = Modifier.height(300.dp).fillMaxWidth()) {
                            if (servers.isEmpty()) {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text("Список пуст", color = Color.Gray)
                                }
                            } else {
                                LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    items(servers) { server ->
                                        Surface(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(6.dp))
                                                .clickable {
                                                    selectedServer = server
                                                    showServerDialog = false
                                                },
                                            color = Color(0xFFF2F2F2),
                                            shape = RoundedCornerShape(6.dp),
                                            border = BorderStroke(1.dp, Color.Black)
                                        ) {
                                            Column(modifier = Modifier.padding(10.dp)) {
                                                Text(text = server.name, color = Color.Black, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                                                Text(text = server.address, color = Color.Gray, fontSize = 11.sp, maxLines = 1)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { showServerDialog = false }) {
                            Text("Закрыть", color = Color.Black)
                        }
                    },
                    containerColor = Color.White
                )
            }
        }
    }
}

// Загрузчик подписок
suspend fun fetchSubscriptionServers(urlString: String): Pair<List<VpnServer>, String> = withContext(Dispatchers.IO) {
    try {
        val url = URL(urlString)
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 10000
        connection.readTimeout = 10000
        connection.setRequestProperty("User-Agent", "Mozilla/5.0")
        connection.instanceFollowRedirects = true

        if (connection.responseCode == 200) {
            val rawBody = connection.inputStream.bufferedReader().use { it.readText() }.trim()
            if (rawBody.isBlank()) return@withContext Pair(emptyList(), "Пусто")

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
                        URLDecoder.decode(line.substringAfterLast("#"), "UTF-8")
                    } catch (e: Exception) {
                        "Узел #${index + 1}"
                    }
                } else {
                    "Узел #${index + 1}"
                }
                parsedList.add(VpnServer(serverName, line))
            }
            Pair(parsedList, "")
        } else {
            Pair(emptyList(), "HTTP Ошибка")
        }
    } catch (e: Exception) {
        Pair(emptyList(), "Сбой сети")
    }
}

// Отрисовка фирменного логотипа blnbl
fun DrawScope.drawBlnblLogo() {
    val h = size.height
    val path = Path().apply {
        addRect(Rect(0f, 0f, 6f, h))
        addOval(Rect(4f, h * 0.4f, 16f, h))
        
        addRect(Rect(22f, 0f, 28f, h))
        
        addRect(Rect(34f, h * 0.35f, 40f, h))
        addRect(Rect(46f, h * 0.35f, 52f, h))
        addRect(Rect(34f, h * 0.35f, 52f, h * 0.55f))
        
        addRect(Rect(58f, 0f, 64f, h))
        addOval(Rect(62f, h * 0.4f, 74f, h))
        
        addRect(Rect(80f, 0f, 86f, h))
    }
    drawPath(path, color = Color.Black)
}

// Фоновые цветочки и трава
fun DrawScope.drawMangaEnvironment() {
    val w = size.width
    val h = size.height

    val flowerOffsets = listOf(
        Offset(50f, 40f), Offset(90f, 80f), Offset(150f, 30f), Offset(220f, 90f),
        Offset(300f, 50f), Offset(380f, 70f), Offset(450f, 30f), Offset(530f, 80f),
        Offset(600f, 40f), Offset(680f, 90f), Offset(750f, 50f), Offset(820f, 30f)
    )
    
    for ((index, pos) in flowerOffsets.withIndex()) {
        val scale = if (index % 2 == 0) 1.2f else 0.8f
        val color = if (index % 3 == 0) Color.Black else Color(0xFF555555)
        drawCherryBlossom(pos, scale, color)
    }

    val grassPath = Path().apply {
        moveTo(0f, h)
        var currentX = 0f
        var isUp = true
        while (currentX < w + 20f) {
            val stepX = 25f + (currentX.toInt() % 15)
            val stepY = if (isUp) h - 110f else h - 70f
            lineTo(currentX, stepY)
            currentX += stepX
            isUp = !isUp
        }
        lineTo(w, h)
        close()
    }
    drawPath(grassPath, color = Color.Black)
    
    var x = 15f
    while (x < w) {
        drawLine(
            color = Color.Black,
            start = Offset(x, h),
            end = Offset(x + 5f, h - 85f),
            strokeWidth = 3f
        )
        x += 35f
    }
}

fun DrawScope.drawCherryBlossom(center: Offset, scale: Float, color: Color) {
    val radius = 10f * scale
    for (i in 0..4) {
        val angle = i * (Math.PI * 2 / 5)
        val petalCenter = Offset(
            center.x + (radius * kotlin.math.cos(angle)).toFloat(),
            center.
