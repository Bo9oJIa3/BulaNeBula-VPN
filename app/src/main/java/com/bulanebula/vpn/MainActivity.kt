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
    
    var selectedServer by remember { mutableStateOf<VpnServer?>(null) }
    var showServerDialog by remember { mutableStateOf(false) }
    var servers by remember { mutableStateOf<List<VpnServer>>(emptyList()) }
    
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
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
            VpnState.IDLE -> Color(0xFFFF3344)
            VpnState.CONNECTING -> Color(0xFFFF8800)
            VpnState.CONNECTED -> Color(0xFF00FF88)
            VpnState.ERROR -> Color(0xFF7A1C1C)
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
                            
