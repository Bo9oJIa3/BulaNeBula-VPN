package com.bulanebula.vpn

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
                VpnMainScreen()
            }
        }
    }
}

// Состояния подключения VPN
enum class VpnState {
    IDLE, CONNECTING, CONNECTED, ERROR
}

data class VpnServer(val id: Int, val name: String, val ping: Int)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VpnMainScreen() {
    var vpnState by remember { mutableStateOf(VpnState.IDLE) }
    var selectedServer by remember { mutableStateOf(VpnServer(1, "Cyber-Tokyo 01", 34)) }
    var showServerDialog by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    // Список доступных серверов (до 150 узлов)
    val servers = remember {
        listOf(
            VpnServer(1, "Cyber-Tokyo 01", 34),
            VpnServer(2, "Neon-Berlin 02", 45),
            VpnServer(3, "Matrix-Frankfurt 03", 52),
            VpnServer(4, "Silicon-Tokyo 04", 68)
        )
    }

    // Анимация изменения цвета кнопки в зависимости от состояния
    val buttonColor by animateColorAsState(
        targetValue = when (vpnState) {
            VpnState.IDLE -> Color(0xFFFF3344)       // Сочный красный помидор
            VpnState.CONNECTING -> Color(0xFFFF8800) // Оранжевый процесс
            VpnState.CONNECTED -> Color(0xFF00FF88)  // Неоново-зеленый успех
            VpnState.ERROR -> Color(0xFF7A1C1C)      // Темно-алый провал (шмяк!)
        },
        animationSpec = tween(durationMillis = 500),
        label = "ColorAnim"
    )

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFF0D0D12) // Тёмный киберпанк-фон
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Шапка: Название и выбор сервера
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "BulaNeBula VPN",
                        color = Color(0xFF00FF88),
                        fontSize = 28.sp,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    // Кнопка выбора сервера
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
                                text = "🌍 Сервер: ${selectedServer.name} (${selectedServer.ping}ms)",
                                color = Color.White,
                                fontSize = 14.sp
                            )
                        }
                    }
                }

                // Главный элемент: Помидор превращается в банку томатной пасты!
                Box(
                    modifier = Modifier
                        .size(200.dp)
                        .clickable {
                            if (vpnState == VpnState.IDLE || vpnState == VpnState.ERROR) {
                                vpnState = VpnState.CONNECTING
                                coroutineScope.launch {
                                    delay(2500) // Имитация подключения к серверу
                                    
                                    // Здесь будет логика VpnService. 
                                    // Если произошла ошибка сети — ставим vpnState = VpnState.ERROR (кнопка отлипнет!)
                                    // Если успешно — vpnState = VpnState.CONNECTED
                                    vpnState = VpnState.CONNECTED 
                                }
                            } else if (vpnState == VpnState.CONNECTED) {
                                vpnState = VpnState.IDLE // Отключение
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

                // Статус-подсказка внизу
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

            // Всплывающее окно выбора серверов (до 150 штук)
            if (showServerDialog) {
                AlertDialog(
                    onDismissRequest = { showServerDialog = false },
                    title = { Text("Выбор узла", color = Color.White) },
                    text = {
                        Column {
                            servers.forEach { server ->
                                TextButton(
                                    onClick = {
                                        selectedServer = server
                                        showServerDialog = false
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = "${server.name} —${server.ping}ms",
                                        color = Color(0xFF00FF88)
                                    )
                                }
                            }
                        }
                    },
                    confirmButton = {
                        Text
                        
