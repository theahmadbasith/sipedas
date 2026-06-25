package com.sipedas.ponorogo.ui.cctv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sipedas.ponorogo.viewmodel.CctvViewModel
import com.sipedas.ponorogo.viewmodel.SipedasViewModel

@Composable
fun CctvScreen(
    cctvViewModel: CctvViewModel = viewModel(),
    mainViewModel: SipedasViewModel,
    isActive: Boolean = true,
    onTitleLogoPositioned: (androidx.compose.ui.geometry.Rect) -> Unit,
    onTitleTextPositioned: (androidx.compose.ui.geometry.Rect) -> Unit,
    onAboutClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val mapUrlState by cctvViewModel.mapUrl.collectAsState()
    val showSettings by cctvViewModel.showSettings.collectAsState()

    Box(modifier = if (isActive) modifier.fillMaxSize().background(MaterialTheme.colorScheme.background) else Modifier.size(0.dp)) {
        CctvWebViewMap(
            url = mapUrlState,
            isActive = isActive,
            refreshEvent = cctvViewModel.refreshMapEvent,
            modifier = Modifier.fillMaxSize()
        )
    }

    if (showSettings && isActive) {
        CctvSettingsDialog(
            viewModel = cctvViewModel,
            onDismiss = { cctvViewModel.setShowSettings(false) }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CctvSettingsDialog(
    viewModel: CctvViewModel,
    onDismiss: () -> Unit
) {
    var mapUrlField by remember { mutableStateOf(viewModel.mapUrl.value) }

    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Pengaturan CCTV",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Text(
                    text = "Tautkan link CCTV yang ingin ditampilkan.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                OutlinedTextField(
                    value = mapUrlField,
                    onValueChange = { mapUrlField = it },
                    label = { Text("Link CCTV Pedestrian") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Link,
                            contentDescription = "Link Icon"
                        )
                    },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp),
                    shape = RoundedCornerShape(12.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Batal")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            viewModel.updateMapUrl(mapUrlField)
                            onDismiss()
                        },
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Simpan")
                    }
                }
            }
        }
    }
}
