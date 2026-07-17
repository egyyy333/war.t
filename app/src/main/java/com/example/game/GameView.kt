package com.example.game

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch


@OptIn(ExperimentalAnimationApi::class)
@Composable
fun TrenchWarGameApp(
    viewModel: GameViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var isMuted by remember { mutableStateOf(false) }

    LaunchedEffect(isMuted) {
        GameAssets.isMuted = isMuted
    }

    // Screen State: "MENU" or "PLAY"
    var screenState by remember { mutableStateOf("MENU") }
    var selectedStage by remember { mutableIntStateOf(1) }

    // Effect to pre-load assets on start
    LaunchedEffect(Unit) {
        GameAssets.loadAll(context)
    }

    // Local state for tracking particles/visual explosions
    val explosionsList = remember { mutableStateListOf<ExplosionVisual>() }

    // Monitor engine projectiles to spawn visual explosions when grenades hit
    LaunchedEffect(viewModel.projectiles.size) {
        // Find any grenade projectiles at 100% progress and trigger visual explosion particles
        val hitGrenades = viewModel.projectiles.filter { it.isGrenade && it.progress >= 0.95f }
        hitGrenades.forEach { g ->
            if (explosionsList.none { it.id == g.id }) {
                explosionsList.add(ExplosionVisual(g.id, g.endX, g.endY, System.currentTimeMillis()))
                if (!isMuted) {
                    GameAssets.playExplosion()
                }
            }
        }
    }

    // Periodic cleanup of old visual explosions
    LaunchedEffect(Unit) {
        while (true) {
            val now = System.currentTimeMillis()
            explosionsList.removeAll { now - it.startTime > 600 }
            delay(100)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF1E2124))
    ) {
        if (!GameAssets.isLoaded) {
            LoadingSplashScreen()
        } else if (screenState == "MENU") {
            MainMenuScreen(
                onStartGame = { faction, stage ->
                    viewModel.startGame(faction, stage)
                    screenState = "PLAY"
                },
                selectedStage = selectedStage,
                onStageSelected = { selectedStage = it }
            )
        } else {
            GameplayScreen(
                viewModel = viewModel,
                uiState = uiState,
                explosions = explosionsList,
                isMuted = isMuted,
                onToggleMute = { isMuted = !isMuted },
                onExitToMenu = {
                    viewModel.pauseGame()
                    screenState = "MENU"
                }
            )
        }
    }
}

@Composable
fun LoadingSplashScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    colors = listOf(Color(0xFF11141A), Color(0xFF1A2634)),
                    start = Offset(0f, 0f),
                    end = Offset(1000f, 1000f)
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(24.dp)
        ) {
            // Star Emblem with glow
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFF39C12).copy(alpha = 0.1f))
                    .border(2.dp, Color(0xFFF39C12), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = null,
                    tint = Color(0xFFF39C12),
                    modifier = Modifier.size(44.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "حرب الخنادق — الخلاص",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFF39C12),
                    fontSize = 24.sp,
                    letterSpacing = 1.sp
                ),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "جاري تهيئة الموارد والأصوات القتالية...",
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 14.sp
                ),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(24.dp))

            CircularProgressIndicator(
                color = Color(0xFFF39C12),
                strokeWidth = 3.dp,
                modifier = Modifier.size(36.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Box for loading logs to show progress
            Box(
                modifier = Modifier
                    .widthIn(max = 400.dp)
                    .height(120.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black.copy(alpha = 0.4f))
                    .padding(12.dp)
            ) {
                LazyColumn(
                    reverseLayout = true,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(GameAssets.loadLogs.toList().asReversed()) { log ->
                        Text(
                            text = log,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 11.sp,
                                color = if (log.contains("❌")) Color(0xFFE74C3C) else if (log.contains("⚠️")) Color(0xFFF1C40F) else Color.White
                            )
                        )
                    }
                }
            }
        }
    }
}

// Data class to handle local Canvas explosion particles
data class ExplosionVisual(
    val id: String,
    val x: Float,
    val y: Float,
    val startTime: Long
)

@Composable
fun MainMenuScreen(
    onStartGame: (Faction, Int) -> Unit,
    selectedStage: Int,
    onStageSelected: (Int) -> Unit
) {
    var selectedFaction by remember { mutableStateOf(Faction.ALLIES) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    colors = listOf(Color(0xFF11141A), Color(0xFF1A2634)),
                    start = Offset(0f, 0f),
                    end = Offset(1000f, 1000f)
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left Column: Title & Play Action (42% width)
            Column(
                modifier = Modifier
                    .weight(0.42f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Glow Military Star Emblem
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFF39C12).copy(alpha = 0.1f))
                        .border(2.dp, Color(0xFFF39C12), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = null,
                        tint = Color(0xFFF39C12),
                        modifier = Modifier.size(36.dp)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Elegant Title
                Text(
                    text = "حرب الخنادق — الخلاص",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFF39C12),
                        fontSize = 20.sp,
                        letterSpacing = 1.sp
                    ),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "قُد جيشك للسيطرة على خنادق الجبهة وتحقيق النصر الحاسم!",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 11.sp
                    ),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Large Start Button - Very prominent and clickable
                val isAssetsLoaded = GameAssets.isLoaded
                Button(
                    onClick = { if (isAssetsLoaded) onStartGame(selectedFaction, selectedStage) },
                    enabled = isAssetsLoaded,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isAssetsLoaded) Color(0xFF2ECC71) else Color(0xFF7F8C8D),
                        disabledContainerColor = Color(0xFF5D6D7E)
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("start_battle_button")
                ) {
                    if (isAssetsLoaded) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "ابدأ المعركة القتالية الآن",
                            style = MaterialTheme.typography.titleMedium.copy(
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        )
                    } else {
                        CircularProgressIndicator(
                            color = Color.White,
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "جاري تحميل الموارد والأصوات...",
                            style = MaterialTheme.typography.titleMedium.copy(
                                color = Color.White.copy(alpha = 0.8f),
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                
                val context = LocalContext.current
                var showDiagnostics by remember { mutableStateOf(false) }
                
                TextButton(
                    onClick = { showDiagnostics = true },
                    modifier = Modifier.testTag("show_diagnostics_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = Color(0xFFF39C12),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "فحص تشخيص النظام وتحميل الملفات",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = Color(0xFFF39C12),
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 11.sp
                        )
                    )
                }

                if (showDiagnostics) {
                    AlertDialog(
                        onDismissRequest = { showDiagnostics = false },
                        title = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Build,
                                    contentDescription = null,
                                    tint = Color(0xFFF39C12)
                                )
                                Text(
                                    text = "تشخيص تحميل موارد وأصوات اللعبة",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                )
                            }
                        },
                        text = {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 250.dp)
                            ) {
                                Text(
                                    text = "حالة الملفات وسجل التحميل الحالي في الذاكرة:",
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                    modifier = Modifier.padding(bottom = 6.dp)
                                )
                                LazyColumn(
                                    modifier = Modifier
                                        .weight(1f)
                                        .background(Color.Black.copy(alpha = 0.3f))
                                        .padding(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    items(GameAssets.loadLogs) { log ->
                                        Text(
                                            text = log,
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                fontSize = 10.sp,
                                                color = Color.White
                                            )
                                        )
                                    }
                                    if (GameAssets.loadLogs.isEmpty()) {
                                        item {
                                            Text(
                                                text = "لا توجد سجلات بعد.",
                                                style = MaterialTheme.typography.bodySmall.copy(color = Color.LightGray)
                                            )
                                        }
                                    }
                                }
                            }
                        },
                        confirmButton = {
                            val scope = rememberCoroutineScope()
                            Button(
                                onClick = {
                                    scope.launch(Dispatchers.IO) {
                                        GameAssets.loadAll(context, force = true)
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE67E22))
                            ) {
                                Text("إعادة تحميل قسري للملفات", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold, color = Color.White))
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showDiagnostics = false }) {
                                Text("إغلاق", color = Color.White)
                            }
                        },
                        containerColor = Color(0xFF1E2634),
                        titleContentColor = Color.White,
                        textContentColor = Color.White
                    )
                }
            }

            // Right Column: Selection Setup (58% width)
            Column(
                modifier = Modifier
                    .weight(0.58f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "إعدادات المعركة الحربية",
                    style = MaterialTheme.typography.titleSmall.copy(
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    ),
                    modifier = Modifier
                        .align(Alignment.Start)
                        .padding(bottom = 6.dp)
                )

                // Factions Selection Side-by-Side
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Allies Card (الحلفاء)
                    FactionCard(
                        title = "قوات الحلفاء",
                        description = "الدفاع المنظم والإمدادات المستقرة.",
                        colorAccent = Color(0xFF3498DB),
                        isSelected = selectedFaction == Faction.ALLIES,
                        onClick = { selectedFaction = Faction.ALLIES },
                        modifier = Modifier.weight(1f)
                    )

                    // Axis Card (المحور)
                    FactionCard(
                        title = "قوات المحور",
                        description = "الهجوم العنيف والضربات المباغتة.",
                        colorAccent = Color(0xFFE74C3C),
                        isSelected = selectedFaction == Faction.AXIS,
                        onClick = { selectedFaction = Faction.AXIS },
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Stage Selection Panel
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.05f)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "اختر الجبهة القتالية (المرحلة):",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            listOf(1, 2, 3).forEach { stage ->
                                val stageName = when(stage) {
                                    1 -> "التدريب"
                                    2 -> "الصمود"
                                    else -> "الحرب الشاملة"
                                }
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(
                                            if (selectedStage == stage) Color(0xFFF39C12)
                                            else Color.White.copy(alpha = 0.08f)
                                        )
                                        .clickable { onStageSelected(stage) }
                                        .padding(vertical = 6.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = stageName,
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            color = if (selectedStage == stage) Color.Black else Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RowScope.FactionCard(
    title: String,
    description: String,
    colorAccent: Color,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) colorAccent.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.04f)
        ),
        shape = RoundedCornerShape(10.dp),
        modifier = modifier
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) colorAccent else Color.White.copy(alpha = 0.1f),
                shape = RoundedCornerShape(10.dp)
            )
            .clickable { onClick() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(colorAccent),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isSelected) Icons.Default.Check else Icons.Default.Star,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(14.dp)
                )
            }

            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    fontSize = 12.sp
                ),
                textAlign = TextAlign.Center
            )

            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall.copy(
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 9.sp
                ),
                textAlign = TextAlign.Center,
                maxLines = 2
            )
        }
    }
}

@Composable
fun GameplayScreen(
    viewModel: GameViewModel,
    uiState: GameUIState,
    explosions: List<ExplosionVisual>,
    isMuted: Boolean,
    onToggleMute: () -> Unit,
    onExitToMenu: () -> Unit
) {
    var showPauseDialog by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        // The Game Arena Render Canvas
        Box(
            modifier = Modifier
                .fillMaxSize()
        ) {
            GameCanvas(
                viewModel = viewModel,
                explosions = explosions
            )
        }

        // HUD Overlay elements
        HUDOverlay(
            uiState = uiState,
            viewModel = viewModel,
            isMuted = isMuted,
            onToggleMute = onToggleMute,
            onPauseClick = {
                viewModel.pauseGame()
                showPauseDialog = true
            }
        )

        // Game Over Overlay
        if (uiState.isGameOver) {
            GameOverOverlay(
                didWin = uiState.didPlayerWin,
                stage = uiState.gameStage,
                onRestart = {
                    viewModel.startGame(uiState.playerFaction, uiState.gameStage)
                },
                onExit = onExitToMenu
            )
        }

        // Pause Dialog
        if (showPauseDialog) {
            AlertDialog(
                onDismissRequest = {
                    showPauseDialog = false
                    viewModel.resumeGame()
                },
                title = {
                    Text(
                        text = "تم إيقاف اللعبة مؤقتاً",
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                text = {
                    Text(
                        text = "هل ترغب في الاستمرار بالقتال أم العودة للقائمة الرئيسية؟",
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                confirmButton = {
                    Button(
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2ECC71)),
                        onClick = {
                            showPauseDialog = false
                            viewModel.resumeGame()
                        }
                    ) {
                        Text("متابعة القتال", color = Color.White)
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            showPauseDialog = false
                            onExitToMenu()
                        }
                    ) {
                        Text("الخروج للقائمة", color = MaterialTheme.colorScheme.error)
                    }
                }
            )
        }
    }
}

@Composable
fun HUDOverlay(
    uiState: GameUIState,
    viewModel: GameViewModel,
    isMuted: Boolean,
    onToggleMute: () -> Unit,
    onPauseClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp)
    ) {
        // 1. TOP BAR OVERLAY
        val alivePlayerSoldiers = viewModel.playerSquads.flatMap { it.soldiers }.count { !it.isDead }
        val aliveEnemySoldiers = viewModel.enemySquads.flatMap { it.soldiers }.count { !it.isDead }
        val playerIsAllies = uiState.playerFaction == Faction.ALLIES
        val alliesCount = if (playerIsAllies) alivePlayerSoldiers else aliveEnemySoldiers
        val axisCount = if (playerIsAllies) aliveEnemySoldiers else alivePlayerSoldiers

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Pause and Mute Buttons
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconButton(
                    onClick = onPauseClick,
                    modifier = Modifier
                        .size(40.dp)
                        .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                        .testTag("pause_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Pause,
                        contentDescription = "Pause",
                        tint = Color.White
                    )
                }

                IconButton(
                    onClick = onToggleMute,
                    modifier = Modifier
                        .size(40.dp)
                        .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                ) {
                    Icon(
                        imageVector = if (isMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                        contentDescription = "Mute",
                        tint = Color.White
                    )
                }
            }

            // Central Stage & Faction Badge Row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(16.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                // Allies badge
                Box(
                    modifier = Modifier
                        .background(Color(0xFF2980B9), RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "الحلفاء: $alliesCount",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    )
                }

                // Stage Info
                Text(
                    text = when(uiState.gameStage) {
                        1 -> "المرحلة الأولى — التدريب"
                        2 -> "المرحلة الثانية — الصمود"
                        else -> "المرحلة الثالثة — الحسم"
                    },
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFF1C40F)
                    ),
                    modifier = Modifier.padding(horizontal = 4.dp)
                )

                // Axis badge
                Box(
                    modifier = Modifier
                        .background(Color(0xFFC0392B), RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "المحور: $axisCount",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    )
                }
            }

            // Supply Panel (الإمدادات)
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF2C3E50).copy(alpha = 0.85f)),
                shape = RoundedCornerShape(8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ShoppingCart,
                        contentDescription = null,
                        tint = Color(0xFF2ECC71),
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "الإمدادات: ${uiState.playerSupply.toInt()}/${uiState.maxSupply.toInt()}",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    )
                }
            }
        }

        // 2. MIDDLE FLOATING CONTROLS FOR TRENCH RELEASE (TRENCH HUBS)
        // Draw release squad option over Right Trench (X ~ 720) and Left Trench (X ~ 280)
        // These bubbles represent squads currently inside and let players command them to exit
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.Center)
                .padding(horizontal = 40.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Left Trench Hub (X = 280f)
            TrenchControlHub(
                trenchId = 0,
                occupiedBy = uiState.leftTrenchOccupiedBy,
                playerFaction = uiState.playerFaction,
                squadsInTrench = viewModel.playerSquads.filter { it.isInTrench && it.trenchIndex == 0 },
                onOrderExit = { viewModel.orderSquadExitTrench(it.id) }
            )

            // Right Trench Hub (X = 720f)
            TrenchControlHub(
                trenchId = 1,
                occupiedBy = uiState.rightTrenchOccupiedBy,
                playerFaction = uiState.playerFaction,
                squadsInTrench = viewModel.playerSquads.filter { it.isInTrench && it.trenchIndex == 1 },
                onOrderExit = { viewModel.orderSquadExitTrench(it.id) }
            )
        }

        // 3. BOTTOM SOLDIERS ACTION BAR (Wider and more spacious)
        Row(
            modifier = Modifier
                .width(340.dp) // Professional spacious size
                .align(Alignment.BottomEnd)
                .padding(bottom = 8.dp, end = 12.dp)
                .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(10.dp))
                .padding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Card 1: Infantry (المشاة)
            SpawnCardButton(
                title = "مشاة",
                countLabel = "3 جنود",
                cost = viewModel.getUnitCost(UnitType.INFANTRY).toInt(),
                currentSupply = uiState.playerSupply,
                cooldownProgress = viewModel.spawnCooldownInfantry / 120f,
                icon = Icons.Default.Person,
                cardBitmap = GameAssets.cardInfantry,
                onClick = {
                    val success = viewModel.spawnPlayerSquad(UnitType.INFANTRY)
                    if (success && !isMuted) GameAssets.playSpawn()
                },
                modifier = Modifier.weight(1f)
            )

            // Card 2: Grenadiers (قاذفي القنابل)
            SpawnCardButton(
                title = "قاذف قنابل",
                countLabel = "2 جندي",
                cost = viewModel.getUnitCost(UnitType.GRENADIER).toInt(),
                currentSupply = uiState.playerSupply,
                cooldownProgress = viewModel.spawnCooldownGrenadier / 240f,
                icon = Icons.Default.Send,
                cardBitmap = GameAssets.cardGrenadier,
                onClick = {
                    val success = viewModel.spawnPlayerSquad(UnitType.GRENADIER)
                    if (success && !isMuted) GameAssets.playSpawn()
                },
                modifier = Modifier.weight(1f)
            )

            // Card 3: Snipers (القناصة)
            SpawnCardButton(
                title = "قناص",
                countLabel = "1 جندي",
                cost = viewModel.getUnitCost(UnitType.SNIPER).toInt(),
                currentSupply = uiState.playerSupply,
                cooldownProgress = viewModel.spawnCooldownSniper / 360f,
                icon = Icons.Default.Adjust,
                cardBitmap = GameAssets.cardSniper,
                onClick = {
                    val success = viewModel.spawnPlayerSquad(UnitType.SNIPER)
                    if (success && !isMuted) GameAssets.playSpawn()
                },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
fun TrenchControlHub(
    trenchId: Int,
    occupiedBy: Faction?,
    playerFaction: Faction,
    squadsInTrench: List<Squad>,
    onOrderExit: (Squad) -> Unit
) {
    if (occupiedBy != playerFaction || squadsInTrench.isEmpty()) {
        return // Only show player control if player occupies the trench and has squads
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.75f)),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier
            .width(140.dp)
            .shadow(4.dp)
    ) {
        Column(
            modifier = Modifier.padding(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = if (trenchId == 1) "الخندق الدفاعي الأيمن" else "الخندق الأمامي الأيسر",
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFF1C40F)
                ),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Divider(color = Color.White.copy(alpha = 0.15f))

            squadsInTrench.forEachIndexed { i, squad ->
                val squadName = when(squad.type) {
                    UnitType.INFANTRY -> "مشاة x3"
                    UnitType.GRENADIER -> "قاذف x2"
                    UnitType.SNIPER -> "قناص x1"
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xFF2980B9).copy(alpha = 0.3f))
                        .clickable { onOrderExit(squad) }
                        .padding(horizontal = 4.dp, vertical = 3.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.ExitToApp,
                        contentDescription = "Exit Trench",
                        tint = Color.Green,
                        modifier = Modifier.size(10.dp)
                    )
                    Text(
                        text = "مجموعة ${i+1}: $squadName",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    )
                }
            }
        }
    }
}

@Composable
fun SpawnCardButton(
    title: String,
    countLabel: String,
    cost: Int,
    currentSupply: Float,
    cooldownProgress: Float,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    cardBitmap: Bitmap?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val canAfford = currentSupply >= cost
    val coolingDown = cooldownProgress > 0.01f
    val isEnabled = canAfford && !coolingDown

    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isEnabled) Color(0xFF2C3E50) else Color(0xFF1A252F).copy(alpha = 0.6f)
        ),
        shape = RoundedCornerShape(8.dp),
        modifier = modifier
            .shadow(3.dp)
            .height(102.dp) // Professional taller card height
            .clickable(enabled = isEnabled) { onClick() }
            .border(
                width = 1.2.dp,
                color = if (isEnabled) Color(0xFFF39C12) else Color.White.copy(alpha = 0.08f),
                shape = RoundedCornerShape(8.dp)
            )
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                if (cardBitmap != null) {
                    Image(
                        bitmap = cardBitmap.asImageBitmap(),
                        contentDescription = title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(42.dp) // Highly visible professional card artwork size!
                            .clip(RoundedCornerShape(4.dp))
                    )
                } else {
                    // Header / Mini Icon
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (isEnabled) Color(0xFFF1C40F) else Color.White.copy(alpha = 0.4f),
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Title and Squad Count side-by-side or cleanly stacked
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isEnabled) Color.White else Color.White.copy(alpha = 0.5f)
                        ),
                        modifier = Modifier.padding(start = 2.dp)
                    )
                    Text(
                        text = countLabel,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 8.sp,
                            color = Color.LightGray.copy(alpha = 0.8f)
                        ),
                        modifier = Modifier.padding(end = 2.dp)
                    )
                }

                // Cost Badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(3.dp))
                        .background(if (canAfford) Color(0xFF27AE60) else Color(0xFFC0392B))
                        .padding(horizontal = 6.dp, vertical = 1.5.dp)
                ) {
                    Text(
                        text = "إمداد $cost",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 7.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    )
                }
            }

            // Cooldown overlay circle matching exact card bounds
            if (coolingDown) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(Color.Black.copy(alpha = 0.65f)),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        progress = cooldownProgress,
                        color = Color(0xFFE74C3C),
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(24.dp) // Clear progress spinner
                    )
                }
            }
        }
    }
}

@Composable
fun GameOverOverlay(
    didWin: Boolean,
    stage: Int,
    onRestart: () -> Unit,
    onExit: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.82f)),
        contentAlignment = Alignment.Center
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF2C3E50)),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .widthIn(max = 450.dp)
                .padding(24.dp)
                .border(2.dp, if (didWin) Color(0xFF2ECC71) else Color(0xFFE74C3C), RoundedCornerShape(16.dp))
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Icon(
                    imageVector = if (didWin) Icons.Default.CheckCircle else Icons.Default.Warning,
                    contentDescription = null,
                    tint = if (didWin) Color(0xFF2ECC71) else Color(0xFFE74C3C),
                    modifier = Modifier.size(56.dp)
                )

                Text(
                    text = if (didWin) "لقد حققت النصر الحاسم!" else "لقد تم اجتياح خنادقنا!",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    ),
                    textAlign = TextAlign.Center
                )

                Text(
                    text = if (didWin) {
                        "تهانينا القائد! لقد تمكن جنودك البواسل من اختراق خطوط العدو وإحكام السيطرة الكاملة على الجبهة القتالية رقم $stage."
                    } else {
                        "مع الأسف قائد، تمكن العدو من الالتفاف واجتياح موقعنا الدفاعي الخلفي. تراجع لإعادة تنظيم صفوفك."
                    },
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = Color.White.copy(alpha = 0.8f)
                    ),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = onRestart,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF27AE60)),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("خوض المعركة مجدداً", color = Color.White, fontWeight = FontWeight.Bold)
                }

                TextButton(
                    onClick = onExit,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("الخروج إلى الجبهة الرئيسية", color = Color.White.copy(alpha = 0.7f))
                }
            }
        }
    }
}

@Composable
fun GameCanvas(
    viewModel: GameViewModel,
    explosions: List<ExplosionVisual>,
    modifier: Modifier = Modifier
) {
    val stateFlow = viewModel.uiState
    val gameUIState by stateFlow.collectAsState()

    // Using infinite transition to drive custom anim updates
    val infiniteTransition = rememberInfiniteTransition()
    val frameTimer by infiniteTransition.animateValue(
        initialValue = 0,
        targetValue = 1000,
        typeConverter = Int.VectorConverter,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        )
    )

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF27AE60)) // Fallback land color
    ) {
        val width = size.width
        val height = size.height

        // Auto Scaling variables
        val scaleX = width / 1000f
        val scaleY = height / 500f

        // Draw background terrain
        val bgBmp = GameAssets.bgBitmap
        if (bgBmp != null) {
            drawImage(
                image = bgBmp.asImageBitmap(),
                dstSize = IntSize(width.toInt(), height.toInt())
            )
        } else {
            drawTacticalBackground(scaleX, scaleY)
        }

        // Draw Left Trench Excavation Back Wall
        drawTrenchBackWall(viewModel.leftTrench, scaleX, scaleY)

        // Draw Right Trench Excavation Back Wall
        drawTrenchBackWall(viewModel.rightTrench, scaleX, scaleY)

        // Draw Active Soldiers (Behind front walls if inside trenches)
        viewModel.playerSquads.forEach { squad ->
            squad.soldiers.forEach { soldier ->
                if (!soldier.isDead) {
                    drawSoldierEntity(soldier, squad.type, scaleX, scaleY, frameTimer)
                }
            }
        }

        viewModel.enemySquads.forEach { squad ->
            squad.soldiers.forEach { soldier ->
                if (!soldier.isDead) {
                    drawSoldierEntity(soldier, squad.type, scaleX, scaleY, frameTimer)
                }
            }
        }

        // Draw Trench FRONT mud/sandbags on top of soldiers to create realistic depth occlusion!
        drawTrenchFrontWall(viewModel.leftTrench, scaleX, scaleY)
        drawTrenchFrontWall(viewModel.rightTrench, scaleX, scaleY)

        // Draw Projectiles (Bullets Tracers or Flying Grenades)
        viewModel.projectiles.forEach { p ->
            drawProjectileEntity(p, scaleX, scaleY)
        }

        // Draw Explosions
        explosions.forEach { e ->
            val age = System.currentTimeMillis() - e.startTime
            val radius = (age / 600f) * 55f * scaleX
            val alpha = (1.0f - (age / 600f)).coerceIn(0f, 1f)
            
            drawCircle(
                color = Color(0xFFF39C12).copy(alpha = alpha),
                radius = radius,
                center = Offset(e.x * scaleX, e.y * scaleY)
            )
            drawCircle(
                color = Color(0xFFE74C3C).copy(alpha = alpha * 0.8f),
                radius = radius * 0.7f,
                center = Offset(e.x * scaleX, e.y * scaleY)
            )
            drawCircle(
                color = Color(0xFFF1C40F).copy(alpha = alpha * 0.9f),
                radius = radius * 0.4f,
                center = Offset(e.x * scaleX, e.y * scaleY)
            )
        }
    }
}

// Draw a beautiful procedural historical tactical landscape
fun DrawScope.drawTacticalBackground(scaleX: Float, scaleY: Float) {
    val canvasW = size.width
    val canvasH = size.height

    // 1. Sky & Sun
    drawRect(
        brush = Brush.verticalGradient(
            colors = listOf(Color(0xFF2C3E50), Color(0xFF7F8C8D)),
            startY = 0f,
            endY = 320f * scaleY
        ),
        topLeft = Offset(0f, 0f),
        size = Size(canvasW, 320f * scaleY)
    )

    // Sun behind dusty fog
    drawCircle(
        color = Color(0xFFF5B041).copy(alpha = 0.35f),
        radius = 40f * scaleX,
        center = Offset(500f * scaleX, 120f * scaleY)
    )

    // 2. Mountains / Hills in background
    val hillPath = Path().apply {
        moveTo(0f, 320f * scaleY)
        quadraticTo(250f * scaleX, 260f * scaleY, 500f * scaleX, 305f * scaleY)
        quadraticTo(750f * scaleX, 250f * scaleY, canvasW, 320f * scaleY)
        lineTo(canvasW, canvasH)
        lineTo(0f, canvasH)
        close()
    }
    drawPath(hillPath, Color(0xFF2E4053))

    // 3. Ground (Mud / Grass Mix)
    drawRect(
        color = Color(0xFF1E272C), // Mud Slate
        topLeft = Offset(0f, 320f * scaleY),
        size = Size(canvasW, canvasH - 320f * scaleY)
    )

    // Barbed wire visual accents (horizontal fence line)
    val fenceY = 330f * scaleY
    drawLine(
        color = Color(0xFF7F8C8D).copy(alpha = 0.6f),
        start = Offset(0f, fenceY),
        end = Offset(canvasW, fenceY),
        strokeWidth = 2f
    )
    for (x in 0..1000 step 40) {
        // Draw little wire spikes
        val px = x * scaleX
        drawLine(
            color = Color(0xFFBDC3C7),
            start = Offset(px - 5f, fenceY - 6f),
            end = Offset(px + 5f, fenceY + 6f),
            strokeWidth = 1.5f
        )
        drawLine(
            color = Color(0xFFBDC3C7),
            start = Offset(px + 5f, fenceY - 6f),
            end = Offset(px - 5f, fenceY + 6f),
            strokeWidth = 1.5f
        )
    }
}

// Back wall of the trench dugout
fun DrawScope.drawTrenchBackWall(trench: Trench, scaleX: Float, scaleY: Float) {
    val tx = trench.centerX * scaleX
    val tWidth = trench.width * scaleX
    val trenchBmp = GameAssets.trenchBitmap

    if (trenchBmp != null) {
        drawImage(
            image = trenchBmp.asImageBitmap(),
            dstOffset = IntOffset((tx - tWidth/2).toInt(), (210f * scaleY).toInt()),
            dstSize = IntSize(tWidth.toInt(), (240f * scaleY).toInt())
        )
    } else {
        // Draw wood/mud wall back excavation
        drawRect(
            brush = Brush.horizontalGradient(
                colors = listOf(Color(0xFF3E2723), Color(0xFF4E342E), Color(0xFF3E2723)),
                startX = tx - tWidth/2,
                endX = tx + tWidth/2
            ),
            topLeft = Offset(tx - tWidth/2, 210f * scaleY),
            size = Size(tWidth, 240f * scaleY)
        )

        // Wood supports lines (timber logs vertical)
        val leftLogX = tx - tWidth/2.8f
        val rightLogX = tx + tWidth/2.8f
        
        drawRect(
            color = Color(0xFF5D4037),
            topLeft = Offset(leftLogX - 5f, 210f * scaleY),
            size = Size(10f, 240f * scaleY)
        )
        drawRect(
            color = Color(0xFF5D4037),
            topLeft = Offset(rightLogX - 5f, 210f * scaleY),
            size = Size(10f, 240f * scaleY)
        )
    }
}

// Front rim & sandbags overlay on top of soldiers in trenches
fun DrawScope.drawTrenchFrontWall(trench: Trench, scaleX: Float, scaleY: Float) {
    val tx = trench.centerX * scaleX
    val tWidth = trench.width * scaleX

    // Draw front mud lip (covering lower legs)
    drawRect(
        color = Color(0xFF1E272C),
        topLeft = Offset(tx - tWidth/2f, 440f * scaleY),
        size = Size(tWidth, 30f * scaleY)
    )

    // Draw stacked Sandbags around the trench top to front rim!
    val bagW = 45f * scaleX
    val bagH = 15f * scaleY

    val centerOffsetLeft = tx - tWidth/2f
    val centerOffsetRight = tx + tWidth/2f

    // Draw sandbags piling up vertically at the rims
    drawSandbag(centerOffsetLeft - 10f, 240f * scaleY, bagW, bagH)
    drawSandbag(centerOffsetLeft - 15f, 255f * scaleY, bagW, bagH)
    drawSandbag(centerOffsetLeft - 5f, 270f * scaleY, bagW, bagH)

    drawSandbag(centerOffsetRight - bagW + 10f, 240f * scaleY, bagW, bagH)
    drawSandbag(centerOffsetRight - bagW + 15f, 255f * scaleY, bagW, bagH)
    drawSandbag(centerOffsetRight - bagW + 5f, 270f * scaleY, bagW, bagH)
}

fun DrawScope.drawSandbag(x: Float, y: Float, w: Float, h: Float) {
    drawRoundRect(
        color = Color(0xFF9E9D24), // Sand color
        topLeft = Offset(x, y),
        size = Size(w, h),
        cornerRadius = CornerRadius(4f, 4f)
    )
    // Shadow line
    drawLine(
        color = Color(0xFF558B2F),
        start = Offset(x, y + h),
        end = Offset(x + w, y + h),
        strokeWidth = 1.5f
    )
}

fun DrawScope.drawSoldierEntity(
    soldier: Soldier,
    squadType: UnitType,
    scaleX: Float,
    scaleY: Float,
    frameTimer: Int
) {
    val sx = soldier.x * scaleX
    val sy = soldier.y * scaleY
    val isAllies = soldier.faction == Faction.ALLIES
    val isFighting = soldier.state == UnitState.FIGHTING || soldier.state == UnitState.IN_TRENCH

    val bitmap: Bitmap? = when (squadType) {
        UnitType.INFANTRY -> {
            if (isFighting) GameAssets.spriteInfantryShoot else GameAssets.spriteInfantryWalk
        }
        UnitType.SNIPER -> {
            if (isFighting) GameAssets.spriteSniperShoot else GameAssets.spriteSniperWalk
        }
        UnitType.GRENADIER -> {
            GameAssets.spriteGrenadierThrow
        }
    }

    if (bitmap != null) {
        val numFrames = (bitmap.width / bitmap.height).coerceAtLeast(1)
        // Ensure active speed scaling based on animation loop
        val frameIndex = (frameTimer / (1000 / numFrames).coerceAtLeast(1)) % numFrames
        val frameWidth = bitmap.width / numFrames
        val frameHeight = bitmap.height

        val isInTrench = soldier.state == UnitState.IN_TRENCH
        val sWidth = (if (isInTrench) 33f else 38f) * scaleX
        val sHeight = (if (isInTrench) 33f else 38f) * scaleY
        val dstX = sx - sWidth / 2
        val dstY = sy - sHeight + (if (isInTrench) 8f else 12f) * scaleY // adjust to mud floor

        // Flip horizontally for Allies (they move right-to-left) so they face left
        scale(scaleX = if (isAllies) -1f else 1f, scaleY = 1f, pivot = Offset(sx, sy)) {
            drawImage(
                image = bitmap.asImageBitmap(),
                srcOffset = IntOffset(frameIndex * frameWidth, 0),
                srcSize = IntSize(frameWidth, frameHeight),
                dstOffset = IntOffset(dstX.toInt(), dstY.toInt()),
                dstSize = IntSize(sWidth.toInt(), sHeight.toInt())
            )
        }
    } else {
        // --- PROCEDURAL FALLBACK ---
        // Determine color scheme based on Faction
        val coatColor = if (isAllies) Color(0xFF2980B9) else Color(0xFF1E824C) // Allies Blue vs Axis Olive Green
        val helmetColor = if (isAllies) Color(0xFF5D6D7E) else Color(0xFF2C3E50) // Steel Grey vs Dark Blue Grey
        val weaponColor = Color(0xFF4E342E) // Wooden Brown

        // 1. Draw swinging feet if walking
        if (soldier.state == UnitState.WALKING) {
            val swing = sin(frameTimer * 0.1f) * 10f * scaleY
            drawLine(
                color = Color.Black,
                start = Offset(sx - 4f * scaleX, sy),
                end = Offset(sx - 4f * scaleX - swing, sy + 15f * scaleY),
                strokeWidth = 4f * scaleX
            )
            drawLine(
                color = Color.Black,
                start = Offset(sx + 4f * scaleX, sy),
                end = Offset(sx + 4f * scaleX + swing, sy + 15f * scaleY),
                strokeWidth = 4f * scaleX
            )
        }

        // 2. Body (Capsule/Round Coat)
        drawCircle(
            color = coatColor,
            radius = 11f * scaleX,
            center = Offset(sx, sy)
        )

        // Snipers get leafy green Ghillie circles overlay
        if (squadType == UnitType.SNIPER) {
            drawCircle(
                color = Color(0xFF27AE60),
                radius = 6f * scaleX,
                center = Offset(sx - 6f * scaleX, sy - 4f * scaleY)
            )
            drawCircle(
                color = Color(0xFF145A32),
                radius = 5f * scaleX,
                center = Offset(sx + 6f * scaleX, sy + 4f * scaleY)
            )
        }

        // 3. Head (Skin color)
        drawCircle(
            color = Color(0xFFF5CBA7),
            radius = 7f * scaleX,
            center = Offset(sx, sy - 14f * scaleY)
        )

        // 4. Helmet
        drawArc(
            color = helmetColor,
            startAngle = 180f,
            sweepAngle = 180f,
            useCenter = true,
            topLeft = Offset(sx - 8f * scaleX, sy - 21f * scaleY),
            size = Size(16f * scaleX, 14f * scaleY)
        )

        // 5. Weapon holding (Guns pointing left/right depending on faction direction)
        val weaponLeft = isAllies
        val weaponOffset = if (weaponLeft) -15f * scaleX else 15f * scaleX
        
        // Rifle barrel
        drawLine(
            color = Color.DarkGray,
            start = Offset(sx, sy),
            end = Offset(sx + weaponOffset * 1.3f, sy + 2f * scaleY),
            strokeWidth = 2.5f * scaleX
        )
        // Rifle stock
        drawLine(
            color = weaponColor,
            start = Offset(sx - weaponOffset * 0.2f, sy + 3f * scaleY),
            end = Offset(sx + weaponOffset * 0.8f, sy + 3f * scaleY),
            strokeWidth = 4f * scaleX
        )

        // Draw tiny muzzle flash if firing/fighting
        if (soldier.state == UnitState.FIGHTING && soldier.shootCooldown > 65) {
            drawCircle(
                color = Color(0xFFF1C40F),
                radius = 5f * scaleX,
                center = Offset(sx + weaponOffset * 1.4f, sy + 2f * scaleY)
            )
            drawCircle(
                color = Color.White,
                radius = 2.5f * scaleX,
                center = Offset(sx + weaponOffset * 1.4f, sy + 2f * scaleY)
            )
        }
    }

    // 6. Mini Health Bar overlay above head
    val healthPercent = soldier.health / soldier.maxHealth
    val barW = 18f * scaleX
    val barH = 3.5f * scaleY
    val bx = sx - barW/2f
    val by = sy - 28f * scaleY

    drawRect(
        color = Color.Red,
        topLeft = Offset(bx, by),
        size = Size(barW, barH)
    )
    drawRect(
        color = Color.Green,
        topLeft = Offset(bx, by),
        size = Size(barW * healthPercent, barH)
    )
}

fun DrawScope.drawProjectileEntity(p: Projectile, scaleX: Float, scaleY: Float) {
    val cx = p.currentX * scaleX
    val cy = p.currentY * scaleY

    if (p.isGrenade) {
        // Grenade is a small rotating stick or ball
        drawCircle(
            color = Color(0xFF7F8C8D),
            radius = 4f * scaleX,
            center = Offset(cx, cy)
        )
        // Draw trailing spark particles
        drawCircle(
            color = Color(0xFFE74C3C),
            radius = 1.5f * scaleX,
            center = Offset(cx - 3f * scaleX, cy + 2f * scaleY)
        )
    } else {
        // Bullet is a laser/tracer line
        val isAllies = p.faction == Faction.ALLIES
        val traceOffset = if (isAllies) 15f * scaleX else -15f * scaleX
        drawLine(
            color = Color(0xFFF1C40F),
            start = Offset(cx, cy),
            end = Offset(cx + traceOffset, cy),
            strokeWidth = 2f * scaleX
        )
    }
}
