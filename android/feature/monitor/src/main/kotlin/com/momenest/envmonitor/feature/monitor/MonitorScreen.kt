package com.momenest.envmonitor.feature.monitor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.momenest.envmonitor.ble.ConnectionState

@Composable
fun MonitorScreen(
    state: MonitorUiState,
    onConnectClick: () -> Unit,
    onDisconnectClick: () -> Unit,
    onNavigateToOta: () -> Unit
) {
    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "環境監測器",
                style = MaterialTheme.typography.headlineMedium
            )
            
            Spacer(Modifier.height(16.dp))
            
            Text(
                text = state.statusLine,
                style = MaterialTheme.typography.bodyLarge
            )

            Spacer(Modifier.height(32.dp))

            if (state.connection is ConnectionState.Disconnected || state.connection is ConnectionState.Failed) {
                Button(onClick = onConnectClick) {
                    Text("搜尋並連線")
                }
            } else if (state.connection is ConnectionState.Connected) {
                Button(onClick = onDisconnectClick) {
                    Text("中斷連線")
                }
                
                Spacer(Modifier.height(16.dp))
                
                Button(onClick = onNavigateToOta) {
                    Text("前往韌體更新")
                }
            }
        }
    }
}
