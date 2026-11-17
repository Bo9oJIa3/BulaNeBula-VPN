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
                                VpnState.ID
                                
