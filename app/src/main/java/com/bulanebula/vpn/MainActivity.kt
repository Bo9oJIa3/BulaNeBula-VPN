package com.bulanebula.vpn

import android.app.Activity
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bulanebula.vpn.service.VpnConnectionService
import com.bulanebula.vpn.utils.KeyParser

enum class VpnStatus {
    DISCONNECTED, CONNECTING, CONNECTED, ERROR_SQUASH
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            BulaNeBulaScreen(
                onStartVpn = { startService(Intent(this, VpnConnectionService::class.java)) },
                onStopVpn = {
                    val intent = Intent(this, VpnConnectionService::class.java).apply {
                        action = VpnConnectionService.ACTION_DISCONNECT
                    }
                    startService(intent)
                }
            )
        }
    }
}

@Composable
fun BulaNeBulaScreen(onStartVpn: () -> Unit, onStopVpn: () -> Unit) {
    var status by remember { mutableStateOf(VpnStatus.DISCONNECTED) }
    var keyInput by remember { mutableStateOf("") }
    var parsedNodeName by remember { mutableStateOf("Узел не выбран") }

    val vpnPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            status = VpnStatus.CONNECTED
            onStartVpn()
        } else {
            status = VpnStatus.ERROR_SQUASH
        }
    }

    val context = androidx.compose.ui.platform.LocalContext.current

    // Анимация расплющивания помидора
    val scaleX by animateFloatAsState(
        targetValue = when (status) {
            VpnStatus.ERROR_SQUASH -> 1.45f
            VpnStatus.CONNECTING -> 0.92f
            else -> 1f
        },
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "scaleX"
    )
    val scaleY by animateFloatAsState(
        targetValue = when (status) {
            VpnStatus.ERROR_SQUASH -> 0.55f
            VpnStatus.CONNECTING -> 1.08f
            else -> 1f
        },
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "scaleY"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0D0D12))
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "BulaNeBula",
                color = Color(0xFF00FF88),
                fontSize = 32.sp,
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                text = "Была не была, хуже не будет",
                color = Color(0xFF8E8E9E),
                fontSize = 14.sp
            )
        }

        // Кастомная векторная кнопка-помидор
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(220.dp)
                .graphicsLayer(scaleX = scaleX, scaleY = scaleY)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    if (status == VpnStatus.CONNECTED) {
                        status = VpnStatus.DISCONNECTED
                        onStopVpn()
                    } else {
                        val parsed = KeyParser.parse(keyInput)
                        if (parsed == null) {
                            status = VpnStatus.ERROR_SQUASH
                            parsedNodeName = "Кривой ключ! Помидор в лепёшку!"
                        } else {
                            parsedNodeName = "${parsed.name} (${parsed.protocol.uppercase()})"
                            val intent = VpnService.prepare(context)
                            if (intent != null) {
                                vpnPermissionLauncher.launch(intent)
                            } else {
                                status = VpnStatus.CONNECTED
                                onStartVpn()
                            }
                        }
                    }
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val tomatoColor = when (status) {
                    VpnStatus.CONNECTED -> Color(0xFF00E676)
                    VpnStatus.ERROR_SQUASH -> Color(0xFFFF1744)
                    else -> Color(0xFFFF3D00)
                }
                // Тело помидора
                drawCircle(color = tomatoColor, radius = size.minDimension / 2.2f)
                // Блик
                drawCircle(
                    color = Color.White.copy(alpha = 0.25f),
                    radius = size.minDimension / 9f,
                    center = Offset(size.width * 0.35f, size.height * 0.35f)
                )
                // Хвостик-листик
                val leafPath = Path().apply {
                    moveTo(size.width * 0.5f, size.height * 0.12f)
                    lineTo(size.width * 0.42f, size.height * 0.04f)
                    lineTo(size.width * 0.58f, size.height * 0.04f)
                    close()
                }
                drawPath(leafPath, color = Color(0xFF00C853))
            }
            Text(
                text = when (status) {
                    VpnStatus.CONNECTED -> "ПАШЕТ"
                    VpnStatus.ERROR_SQUASH -> "ШМЯК!"
                    else -> "ПУСК"
                },
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Black
            )
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = parsedNodeName,
                color = Color(0xFF00E5FF),
                fontSize = 14.sp,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            OutlinedTextField(
                value = keyInput,
                onValueChange = {
                    keyInput = it
                    if (status == VpnStatus.ERROR_SQUASH) status = VpnStatus.DISCONNECTED
                },
                placeholder = { Text("Вставь vless:// или ss:// ключ...", color = Color.Gray) },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF00FF88),
                    unfocusedBorderColor = Color(0xFF2C2C3A),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
