package com.example.ui

import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Hd
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.model.ScreenZoomManager
import com.example.network.VideoDownloadManager

@Composable
fun DownloadAndEditDialog(
    initialTitle: String,
    episodeNumber: String? = null,
    currentMediaUrl: String?,
    currentZoomPreset: String,
    onApplyZoomPreset: (String) -> Unit,
    onOpenFocusCalibration: () -> Unit,
    onTriggerForcePlay: () -> Unit,
    onDismissRequest: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    BackHandler {
        onDismissRequest()
    }

    var selectedTab by remember { mutableIntStateOf(0) }
    var selectedQuality by remember { mutableStateOf(VideoDownloadManager.VideoQuality.HD_720P) }
    var selectedFolder by remember { mutableStateOf(VideoDownloadManager.TargetFolder.DOWNLOADS) }

    val defaultSuggestedName = remember(initialTitle, episodeNumber, selectedQuality) {
        VideoDownloadManager.suggestFileName(initialTitle, episodeNumber, selectedQuality)
    }

    var customFileName by remember { mutableStateOf(defaultSuggestedName) }

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xB3000000))
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier
                    .widthIn(max = 760.dp)
                    .fillMaxWidth()
                    .fillMaxHeight(0.92f),
                shape = RoundedCornerShape(20.dp),
                color = Color(0xFF13151D),
                border = BorderStroke(1.5.dp, Color(0xFFE50914)),
                shadowElevation = 24.dp
            ) {
                Column(
                    modifier = Modifier.fillMaxSize()
                ) {
                    // 1. Barra de Título no estilo Janela de PC
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(Color(0xFF1E202B), Color(0xFF161720))
                                )
                            )
                            .padding(horizontal = 18.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = Color(0xFFE50914).copy(alpha = 0.2f),
                                modifier = Modifier.size(36.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Download,
                                        contentDescription = null,
                                        tint = Color(0xFFE50914),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "A2 Miguel • Central de Download & Edição",
                                    color = Color.White,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "Janela dedicada para PC e TV: personalize pasta, qualidade e nome do arquivo",
                                    color = Color(0xFFAAAAAA),
                                    fontSize = 11.sp
                                )
                            }
                        }

                        // Botão de Fechar Janela [X]
                        IconButton(
                            onClick = onDismissRequest,
                            modifier = Modifier
                                .size(36.dp)
                                .background(Color(0xFF282B37), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Fechar janela",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    // 2. Abas de Navegação
                    TabRow(
                        selectedTabIndex = selectedTab,
                        containerColor = Color(0xFF181A23),
                        contentColor = Color(0xFFE50914),
                        indicator = { tabPositions ->
                            TabRowDefaults.Indicator(
                                modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                                color = Color(0xFFE50914),
                                height = 3.dp
                            )
                        }
                    ) {
                        Tab(
                            selected = selectedTab == 0,
                            onClick = { selectedTab = 0 },
                            text = {
                                Text(
                                    "📥 Baixar Vídeo",
                                    fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal,
                                    color = if (selectedTab == 0) Color.White else Color.Gray,
                                    fontSize = 13.sp
                                )
                            }
                        )
                        Tab(
                            selected = selectedTab == 1,
                            onClick = { selectedTab = 1 },
                            text = {
                                Text(
                                    "⚙️ Editar o App",
                                    fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal,
                                    color = if (selectedTab == 1) Color.White else Color.Gray,
                                    fontSize = 13.sp
                                )
                            }
                        )
                        Tab(
                            selected = selectedTab == 2,
                            onClick = { selectedTab = 2 },
                            text = {
                                Text(
                                    "💻 Modo PC & Release",
                                    fontWeight = if (selectedTab == 2) FontWeight.Bold else FontWeight.Normal,
                                    color = if (selectedTab == 2) Color.White else Color.Gray,
                                    fontSize = 13.sp
                                )
                            }
                        )
                    }

                    // 3. Conteúdo da Aba Selecionada (com rolagem suave)
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        when (selectedTab) {
                            0 -> DownloadTabContent(
                                customFileName = customFileName,
                                onFileNameChange = { customFileName = it },
                                selectedQuality = selectedQuality,
                                onQualityChange = { q ->
                                    selectedQuality = q
                                    customFileName = VideoDownloadManager.suggestFileName(initialTitle, episodeNumber, q)
                                },
                                selectedFolder = selectedFolder,
                                onFolderChange = { selectedFolder = it },
                                onResetDefaultName = {
                                    customFileName = VideoDownloadManager.suggestFileName(initialTitle, episodeNumber, selectedQuality)
                                },
                                onStartDownload = {
                                    val urlToDownload = currentMediaUrl ?: ""
                                    if (urlToDownload.isBlank()) {
                                        Toast.makeText(
                                            context,
                                            "Aguarde o vídeo iniciar para capturar o stream direto, ou use o botão 'Forçar Play'!",
                                            Toast.LENGTH_LONG
                                        ).show()
                                    } else {
                                        VideoDownloadManager.startDownload(
                                            context = context,
                                            mediaUrl = urlToDownload,
                                            customFileName = customFileName,
                                            targetFolder = selectedFolder,
                                            quality = selectedQuality
                                        )
                                        onDismissRequest()
                                    }
                                },
                                onCopyPcCommand = {
                                    val url = currentMediaUrl ?: "URL_DO_VIDEO"
                                    val cmd = VideoDownloadManager.generatePcDownloadCommand(url, customFileName)
                                    clipboardManager.setText(AnnotatedString(cmd))
                                    Toast.makeText(context, "Comando de download para PC copiado!", Toast.LENGTH_SHORT).show()
                                }
                            )

                            1 -> AppSettingsTabContent(
                                currentZoomPreset = currentZoomPreset,
                                onApplyZoomPreset = onApplyZoomPreset,
                                onOpenFocusCalibration = {
                                    onDismissRequest()
                                    onOpenFocusCalibration()
                                },
                                onTriggerForcePlay = onTriggerForcePlay
                            )

                            2 -> PcCompanionTabContent(
                                onCopyPcUrl = {
                                    clipboardManager.setText(AnnotatedString("https://ais-pre-6r5myv5rfy2ejl7ibgbd7v-719019642347.us-west2.run.app"))
                                    Toast.makeText(context, "Link do app copiado para abrir no PC!", Toast.LENGTH_SHORT).show()
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DownloadTabContent(
    customFileName: String,
    onFileNameChange: (String) -> Unit,
    selectedQuality: VideoDownloadManager.VideoQuality,
    onQualityChange: (VideoDownloadManager.VideoQuality) -> Unit,
    selectedFolder: VideoDownloadManager.TargetFolder,
    onFolderChange: (VideoDownloadManager.TargetFolder) -> Unit,
    onResetDefaultName: () -> Unit,
    onStartDownload: () -> Unit,
    onCopyPcCommand: () -> Unit
) {
    // 1. Campo de Nome do Arquivo
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "📝 NOME DO ARQUIVO (EDITÁVEL):",
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Restaurar Padrão",
                color = Color(0xFF64B5F6),
                fontSize = 11.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .clickable { onResetDefaultName() }
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }

        OutlinedTextField(
            value = customFileName,
            onValueChange = onFileNameChange,
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = Color(0xFFE50914),
                unfocusedBorderColor = Color(0xFF3A3D4C),
                focusedContainerColor = Color(0xFF1B1D27),
                unfocusedContainerColor = Color(0xFF1B1D27)
            ),
            shape = RoundedCornerShape(10.dp),
            singleLine = true,
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Hd,
                    contentDescription = null,
                    tint = Color(0xFFE50914)
                )
            }
        )
        Text(
            text = "O arquivo será salvo com a extensão .mp4 automaticamente.",
            color = Color.Gray,
            fontSize = 10.sp
        )
    }

    // 2. Seleção de Qualidade do Vídeo
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "🎬 QUALIDADE DO VÍDEO:",
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            VideoDownloadManager.VideoQuality.values().forEach { quality ->
                val isSelected = quality == selectedQuality
                Surface(
                    onClick = { onQualityChange(quality) },
                    shape = RoundedCornerShape(10.dp),
                    color = if (isSelected) Color(0xFFE50914) else Color(0xFF222532),
                    border = BorderStroke(
                        1.dp,
                        if (isSelected) Color(0xFFFF5252) else Color(0xFF333748)
                    ),
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = quality.label,
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        )
                        Text(
                            text = quality.description,
                            color = if (isSelected) Color(0xFFFFCDD2) else Color.Gray,
                            fontSize = 9.sp,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }

    // 3. Pasta de Destino / Salvamento
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "📂 PASTA DE SALVAMENTO NO DISPOSITIVO / PC:",
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            VideoDownloadManager.TargetFolder.values().forEach { folder ->
                val isSelected = folder == selectedFolder
                Surface(
                    onClick = { onFolderChange(folder) },
                    shape = RoundedCornerShape(10.dp),
                    color = if (isSelected) Color(0xFF0077B6) else Color(0xFF202330),
                    border = BorderStroke(
                        1.dp,
                        if (isSelected) Color(0xFF48CAE4) else Color(0xFF303444)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Folder,
                            contentDescription = null,
                            tint = if (isSelected) Color.White else Color(0xFF0077B6),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = folder.displayName,
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
        }
    }

    Divider(color = Color(0xFF2E3244))

    // 4. Botões de Ação
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Button(
            onClick = onStartDownload,
            modifier = Modifier
                .weight(1.4f)
                .height(48.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFFE50914)
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(imageVector = Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("BAIXAR VÍDEO AGORA", fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }

        OutlinedButton(
            onClick = onCopyPcCommand,
            modifier = Modifier
                .weight(1f)
                .height(48.dp),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, Color(0xFF4A4E63)),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = Color.White
            )
        ) {
            Icon(imageVector = Icons.Default.Computer, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Comando PC", fontSize = 12.sp)
        }
    }
}

@Composable
private fun AppSettingsTabContent(
    currentZoomPreset: String,
    onApplyZoomPreset: (String) -> Unit,
    onOpenFocusCalibration: () -> Unit,
    onTriggerForcePlay: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1B1E29)),
        border = BorderStroke(1.dp, Color(0xFF2F3345))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = Icons.Default.ZoomIn, contentDescription = null, tint = Color(0xFF0077B6))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Enquadramento e Zoom da Tela", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 14.sp)
            }
            Text(
                "Ajuste a escala da imagem para eliminar bordas pretas na sua Smart TV ou monitor do PC:",
                color = Color.LightGray,
                fontSize = 12.sp
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ScreenZoomManager.ALL_PRESETS.forEach { (presetKey, presetLabel) ->
                    val isSelected = presetKey == currentZoomPreset
                    Surface(
                        onClick = { onApplyZoomPreset(presetKey) },
                        shape = RoundedCornerShape(8.dp),
                        color = if (isSelected) Color(0xFF0077B6) else Color(0xFF242735),
                        border = BorderStroke(1.dp, if (isSelected) Color(0xFF48CAE4) else Color(0xFF35394C)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = presetLabel,
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(vertical = 10.dp)
                        )
                    }
                }
            }
        }
    }

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1B1E29)),
        border = BorderStroke(1.dp, Color(0xFF2F3345))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = Icons.Default.AspectRatio, contentDescription = null, tint = Color(0xFFE50914))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Calibração de Foco & Clique Automático", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 14.sp)
            }
            Text(
                "Mire na tela com as setas do controle remoto para salvar a posição exata do botão Play para todos os filmes e séries.",
                color = Color.LightGray,
                fontSize = 12.sp
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onOpenFocusCalibration,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE50914)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("🎯 Calibrar Ponto de Foco", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }

                OutlinedButton(
                    onClick = onTriggerForcePlay,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, Color(0xFF4A4E63)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Forçar Play", fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun PcCompanionTabContent(
    onCopyPcUrl: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1B1E29)),
        border = BorderStroke(1.dp, Color(0xFF2F3345))
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = Icons.Default.Computer, contentDescription = null, tint = Color(0xFF48CAE4))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Executável e Janela no Computador (Windows)", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 15.sp)
            }

            Text(
                text = "O A2 Miguel inclui suporte nativo para ser executado como um programa de computador independente no Windows!",
                color = Color.White,
                fontSize = 13.sp
            )

            Surface(
                color = Color(0xFF10121A),
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, Color(0xFF2E3244)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("💡 Como usar no PC:", color = Color(0xFFFFD54F), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Text("1. Na pasta do projeto baixada, dê dois cliques em 'Abrir-no-PC.bat'.", color = Color.LightGray, fontSize = 11.sp)
                    Text("2. O programa abrirá em uma janela nativa limpa, sem barra de navegação, exatamente como um .exe.", color = Color.LightGray, fontSize = 11.sp)
                    Text("3. Para converter para um .exe real com ícone, use utilitários como 'Bat To Exe Converter'.", color = Color.LightGray, fontSize = 11.sp)
                }
            }

            Button(
                onClick = onCopyPcUrl,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0077B6)),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(imageVector = Icons.Default.OpenInBrowser, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Copiar Link de Acesso Web para o PC", fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        }
    }
}
