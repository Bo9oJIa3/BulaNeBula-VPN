package com.bulanebula.vpn

import android.os.Bundle
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            BulaNeBulaTheme {
                var currentScreen by remember { mutableStateOf<AppScreen>(AppScreen.Main) }
                
                when (currentScreen) {
                    is AppScreen.Main -> VpnMainScreen(
                        onNavigateToRecommended = { currentScreen = AppScreen.Recommended }
                    )
                    is AppScreen.Recommended -> RecommendedVpnScreen(
                        onBack = { currentScreen = AppScreen.Main }
                    )
                }
            }
        }
    }
}

sealed class AppScreen {
    object Main : AppScreen()
    object Recommended : AppScreen()
}

enum class VpnState { IDLE, CONNECTING, CONNECTED, ERROR }

data class VpnServer(val id: String, val name: String, val ping: Int)
data class RecommendedVpn(val name: String, val tag: String, val description: String)

@Composable
fun VpnMainScreen(onNavigateToRecommended: () -> Unit) {
    var vpnState by remember { mutableStateOf(VpnState.IDLE) }
    
    // Выбранный сервер (по умолчанию пустой или заглушка, пока сервер не пришлет данные)
    var selectedServer by remember { mutableStateOf<VpnServer?>(null) }
    var showServerDialog by remember { mutableStateOf(false) }
    
    // Динамический список серверов, который заполнит твой бэкенд (до 150 узлов)
    var servers by remember { mutableStateOf<List<VpnServer>>(emptyList()) }
    
    val coroutineScope = rememberCoroutineScope()

    // Имитация загрузки списка серверов с твоего бэкенда при старте
    LaunchedEffect(Unit) {
        // Здесь в будущем ты сделаешь запрос к своему серверу за актуальным списком узлов
        // servers = api.fetchServers()
        
        // Пока что ставим примеры, которые прилетели от сервера:
        servers = listOf(
            VpnServer("1", "Server Alpha", 25),
            VpnServer("2", "Server Beta", 40)
        )
        if (selectedServer == null && servers.isNotEmpty()) {
            selectedServer = servers.first()
        }
    }

    val buttonColor by animateColorAsState(
        targetValue = when (vpnState) {
            VpnState.IDLE -> Color(0xFFFF3344)       // Помидор
            VpnState.CONNECTING -> Color(0xFFFF8800) // Процесс трансформации
            VpnState.CONNECTED -> Color(0xFF00FF88)  // Успех (банка в сети)
            VpnState.ERROR -> Color(0xFF7A1C1C)      // Ошибка (отлипание)
        },
        animationSpec = tween(durationMillis = 500),
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
                // Шапка с названием и кнопкой перехода в Томатный Альянс
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
                    
                    IconButton(onClick = onNavigateToRecommended) {
                        Text(text = "🍅", fontSize = 22.sp)
                    }
                }

                // Кнопка выбора сервера из тех, что прислал сервер
                Surface(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { showServerDialog = true },
                    color = Color(0xFF1A1A24)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (selectedServer != null) {
                                "🌍 Узел: ${selectedServer?.name} (${selectedServer?.ping}ms)"
                            } else {
                                "🌍 Загрузка узлов..."
                            },
                            color = Color.White,
                            fontSize = 14.sp
                        )
                    }
                }

                // Главный элемент: Помидор превращается в банку томатной пасты!
                Box(
                    modifier = Modifier
                        .size(200.dp)
                        .clickable {
                            if (selectedServer != null && (vpnState == VpnState.IDLE || vpnState == VpnState.ERROR)) {
                                vpnState = VpnState.CONNECTING
                                coroutineScope.launch {
                                    delay(2500) // Имитация подключения к выбранному узлу
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

                // Подсказка внизу
                Text(
                    text = when (vpnState) {
                        VpnState.IDLE -> "Нажми, чтобы запустить банку в космос"
                        VpnState.CONNECTING -> "Трансформируем помидор в томатную пасту..."
                        VpnState.CONNECTED -> "Туннель защищен. Концентрат свободы активен!"
                        VpnState.ERROR -> "Ошибка связи! Кнопка отлипла, пробуй снова."
                    },
                    color = Color(0xFF8C8C9E),
                    fontSize = 14.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }

            // Всплывающее окно выбора серверов из полученного от бэкенда списка
            if (showServerDialog) {
                AlertDialog(
                    onDismissRequest = { showServerDialog = false },
                    title = { Text("Выбор узла (${servers.size} доступно)", color = Color.White) },
                    text = {
                        Box(
                            modifier = Modifier
                                .height(350.dp)
                                .fillMaxWidth()
                        ) {
                            LazyColumn(
                                verticalArrangement = Arrangement.spacedBy(4.dp),
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
                                        Row(
                                            modifier = Modifier.padding(12.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = server.name,
                                                color = Color.White,
                                                fontSize = 14.sp
                                            )
                                            Text(
                                                text = "${server.ping} ms",
                                                color = Color(0xFF00FF88),
                                                fontSize = 13.sp
                                            )
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

// Экран "Томатный Альянс" (Рекомендованные VPN и рандомайзер)
@Composable
fun RecommendedVpnScreen(onBack: () -> Unit) {
    val recommendedList = remember {
        listOf(
            RecommendedVpn("BulaNeBula Core", "🍅 Родная банка", "Оригинальный клиент с открытым кодом и томатным концентратом."),
            RecommendedVpn("OpenTunnel X", "⚡ Быстрый как кетчуп", "Надежный классический инструмент для обхода преград."),
            RecommendedVpn("CyberTomato Proxy", "🥫 Защита пастой", "Сверхсекретный узел для экстремальных условий киберпанка.")
        )
    }

    var randomPick by remember { mutableStateOf<RecommendedVpn?>(null) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFF0D0D12)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "🍅 Томатный Альянс",
                    color = Color(0xFF00FF88
                                  
