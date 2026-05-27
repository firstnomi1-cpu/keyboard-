package com.example

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.delay
import java.util.Random

// Represents a golden floating UI particle spawned on key tap
data class KeyboardParticle(
    val id: Long,
    val initialX: Float,
    val initialY: Float,
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    var alpha: Float,
    var scale: Float,
    val color: Color
)

class MainActivity : ComponentActivity() {
    private val audioEngine = AudioEngine()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Scaffold(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("main_scaffold")
                ) { innerPadding ->
                    ShahKeyboardScreen(
                        audioEngine = audioEngine,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

@Composable
fun ShahKeyboardScreen(audioEngine: AudioEngine, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var hasMicPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasMicPermission = isGranted
        if (!isGranted) {
            Toast.makeText(context, "Microphone permission is required for voiceboards!", Toast.LENGTH_LONG).show()
        }
    }

    // Trigger permission check on start
    LaunchedEffect(Unit) {
        if (!hasMicPermission) {
            launcher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    // Composition States
    var currentTextMessage by remember { mutableStateOf("SILENCE! Hear the grand royal decree...") }
    val recordVolumeState by audioEngine.recordingAmplitude.collectAsState()
    var isCurrentlyRecording by remember { mutableStateOf(false) }
    var selectedVoicePreset by remember { mutableStateOf(VoicePreset.THE_SHAH) }
    var voiceToKeysMappingEnabled by remember { mutableStateOf(false) }

    // Visual Customizations
    var selectedThemeName by remember { mutableStateOf("Imperial Velvet") }
    var selectedTonePreset by remember { mutableStateOf("royal_harps") } // royal_harps, electric_glitch, standard_tick
    var keyboardShiftOn by remember { mutableStateOf(false) }
    var activeSubView by remember { mutableStateOf("keyboard") } // keyboard, soundboard

    // Particle Burst List
    var particles by remember { mutableStateOf(listOf<KeyboardParticle>()) }
    val particleRandom = remember { Random() }

    // Continuous ticker loop to animate physics particles
    LaunchedEffect(particles) {
        if (particles.isNotEmpty()) {
            delay(16) // ~60fps rendering frame
            particles = particles.map { p ->
                p.copy(
                    x = p.x + p.vx,
                    y = p.y + p.vy,
                    vy = p.vy + 0.35f, // simulated gravity pulling them down
                    alpha = (p.alpha - 0.05f).coerceAtLeast(0f),
                    scale = (p.scale * 0.96f).coerceAtLeast(0.1f)
                )
            }.filter { it.alpha > 0f }
        }
    }

    // Themes Mapping
    val backgroundBrush = when (selectedThemeName) {
        "Neon Obsidian" -> {
            Brush.verticalGradient(
                colors = listOf(Color(0xFF0F0F14), Color(0xFF1E1D22))
            )
        }
        "Sapphire Diamond" -> {
            Brush.verticalGradient(
                colors = listOf(Color(0xFF081226), Color(0xFF101B2E))
            )
        }
        else -> { // Imperial Velvet
            Brush.verticalGradient(
                colors = listOf(Color(0xFF12030A), Color(0xFF2E0916), Color(0xFF1F050F))
            )
        }
    }

    val goldPrimaryColor = when (selectedThemeName) {
        "Neon Obsidian" -> Color(0xFFFFD13B)
        "Sapphire Diamond" -> Color(0xFF70D6FF)
        else -> Color(0xFFFFD700)
    }

    val keyAccentGrad = when (selectedThemeName) {
        "Neon Obsidian" -> Brush.verticalGradient(colors = listOf(Color(0xFF2C2A20), Color(0xFF1F1E19)))
        "Sapphire Diamond" -> Brush.verticalGradient(colors = listOf(Color(0xFF10284A), Color(0xFF09142E)))
        else -> Brush.verticalGradient(colors = listOf(Color(0xFF6E1B32), Color(0xFF3B0715)))
    }

    fun spawnParticlesForKey(row: Int, keyIndex: Int) {
        // Appraise relative coordinates for tap to spawn floating gold elements
        val startX = 60f + (keyIndex * 33f) + (row * 15f)
        val startY = 100f + (row * 60f)
        val newParticles = List(7) {
            KeyboardParticle(
                id = System.nanoTime() + it,
                initialX = startX,
                initialY = startY,
                x = startX,
                y = startY,
                vx = (particleRandom.nextFloat() * 12f) - 6f,
                vy = (particleRandom.nextFloat() * -10f) - 4f, // initial upwards leap
                alpha = 1.0f,
                scale = 1.0f + particleRandom.nextFloat(),
                color = if (it % 2 == 0) goldPrimaryColor else Color(0xFFFFF8DC)
            )
        }
        particles = particles + newParticles
    }

    // Share & Export message preset template
    fun shareMessagePackage() {
        val presetInfo = if (audioEngine.hasRecordings()) {
            " (Preposed voice tape: ${selectedVoicePreset.displayName} Filter Applied!)"
        } else ""

        val sharingText = "👑 [Shah Keyboard Customizer] 👑\n\n$currentTextMessage\n\n$presetInfo"
        try {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, sharingText)
            }
            context.startActivity(Intent.createChooser(intent, "Propose Royal Decree"))
        } catch (e: Exception) {
            Toast.makeText(context, "Drafting shared message failed", Toast.LENGTH_SHORT).show()
        }
    }

    // Raw Clipboard operations
    fun copyToClipboard() {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Royal Message", currentTextMessage)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, "Sovereign message sealed to clipboard!", Toast.LENGTH_SHORT).show()
    }

    // Render Hub UI Layout
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundBrush)
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        // I. Header Banner Row
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 6.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.04f)),
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, goldPrimaryColor.copy(alpha = 0.3f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Audiotrack,
                        contentDescription = "Royal Crown Icon",
                        tint = goldPrimaryColor,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "SHAH BOARD",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Serif,
                            color = goldPrimaryColor,
                            letterSpacing = 1.5.sp
                        )
                        Text(
                            text = "Kingly Voice changer & Soundboard",
                            fontSize = 11.sp,
                            color = Color.White.copy(alpha = 0.6f),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
                Text(
                    text = "COURT TIME: 19:15 UTC",
                    fontSize = 10.sp,
                    color = goldPrimaryColor.copy(alpha = 0.8f),
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier
                        .border(1.dp, goldPrimaryColor.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 6.dp, vertical = 3.dp)
                )
            }
        }

        // II. Message Composition Slate & Preview Letter Paper
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 4.dp),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1128).copy(alpha = 0.7f)),
            border = BorderStroke(1.dp, goldPrimaryColor.copy(alpha = 0.25f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "ROYAL DECREE COMPOSER",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = goldPrimaryColor,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    IconButton(
                        onClick = { currentTextMessage = "" },
                        modifier = Modifier
                            .size(26.dp)
                            .testTag("clear_text_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteSweep,
                            contentDescription = "Clear Slate",
                            tint = Color.Red.copy(alpha = 0.8f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Editable looking styled box for typed text
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(90.dp)
                        .background(Color.Black.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
                        .border(0.5.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                        .padding(10.dp)
                ) {
                    if (currentTextMessage.isEmpty()) {
                        Text(
                            text = "Type using the Shah Keyboard below...",
                            fontSize = 14.sp,
                            color = Color.White.copy(alpha = 0.3f),
                            fontFamily = FontFamily.Serif
                        )
                    } else {
                        Text(
                            text = currentTextMessage,
                            fontSize = 15.sp,
                            color = Color.White,
                            fontFamily = FontFamily.Serif,
                            lineHeight = 20.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Preset Template Quick Injection Buttons
                Text(
                    text = "Insert Kingly Formats:",
                    fontSize = 10.sp,
                    color = Color.White.copy(alpha = 0.5f),
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val templates = listOf(
                        "👑 Decree:" to "SILENCE! By order of the grand sovereign Shah: ",
                        "⚔️ War Cry:" to "TO ARMS! Our alliance demands complete victory! ",
                        "📜 Coronation:" to "Hark! The golden trumpet rings of royal succession: ",
                        "🙏 Apology:" to "Thy apologies have been accepted into the King's keeping: "
                    )
                    items(templates) { (label, content) ->
                        Box(
                            modifier = Modifier
                                .background(goldPrimaryColor.copy(alpha = 0.08f), RoundedCornerShape(20.dp))
                                .border(0.6.dp, goldPrimaryColor.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
                                .clickable {
                                    currentTextMessage = content + currentTextMessage
                                }
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Text(
                                text = label,
                                fontSize = 11.sp,
                                color = goldPrimaryColor,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Action icons row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = { copyToClipboard() },
                        colors = ButtonDefaults.buttonColors(containerColor = goldPrimaryColor),
                        modifier = Modifier
                            .weight(1f)
                            .height(38.dp)
                            .testTag("copy_text_button"),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy text icon",
                            tint = Color.Black,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Copy Decree", fontSize = 12.sp, color = Color.Black, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = { shareMessagePackage() },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.1f)),
                        modifier = Modifier
                            .weight(1f)
                            .height(38.dp)
                            .border(1.dp, goldPrimaryColor.copy(alpha = 0.4f), RoundedCornerShape(30.dp))
                            .testTag("share_text_button"),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Share text icon",
                            tint = goldPrimaryColor,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Share Packet", fontSize = 12.sp, color = goldPrimaryColor, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // III. Voice Recorder & Voice board Processor (Sovereign Mic)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 4.dp),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.15f)),
            border = BorderStroke(1.dp, goldPrimaryColor.copy(alpha = 0.2f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "VOICEBOARD MODULATOR",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = goldPrimaryColor,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (voiceToKeysMappingEnabled) goldPrimaryColor.copy(alpha = 0.15f)
                                else Color.White.copy(alpha = 0.05f)
                            )
                            .clickable {
                                voiceToKeysMappingEnabled = !voiceToKeysMappingEnabled
                                if (voiceToKeysMappingEnabled && !audioEngine.hasRecordings()) {
                                    Toast.makeText(context, "Record a voice snippet first!", Toast.LENGTH_SHORT).show()
                                }
                            }
                            .border(
                                1.dp,
                                if (voiceToKeysMappingEnabled) goldPrimaryColor else Color.Transparent,
                                RoundedCornerShape(8.dp)
                            )
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.KeyboardVoice,
                            contentDescription = "Speak voice key mode",
                            tint = if (voiceToKeysMappingEnabled) goldPrimaryColor else Color.White.copy(alpha = 0.4f),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Voice to Keys Mode",
                            fontSize = 10.sp,
                            color = if (voiceToKeysMappingEnabled) goldPrimaryColor else Color.White.copy(alpha = 0.7f),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Record / Stop Record Button
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.width(75.dp)
                    ) {
                        IconButton(
                            onClick = {
                                if (!hasMicPermission) {
                                    launcher.launch(Manifest.permission.RECORD_AUDIO)
                                } else {
                                    if (isCurrentlyRecording) {
                                        val hadData = audioEngine.stopRecording()
                                        isCurrentlyRecording = false
                                        if (hadData) {
                                            Toast.makeText(context, "Voice sealed! Select a filter below for key tape.", Toast.LENGTH_SHORT).show()
                                        }
                                    } else {
                                        audioEngine.startRecording()
                                        isCurrentlyRecording = true
                                    }
                                }
                            },
                            modifier = Modifier
                                .size(50.dp)
                                .shadow(8.dp, CircleShape)
                                .background(
                                    if (isCurrentlyRecording) Color.Red else goldPrimaryColor,
                                    CircleShape
                                )
                                .testTag("mic_record_toggle")
                        ) {
                            Icon(
                                imageVector = if (isCurrentlyRecording) Icons.Default.Stop else Icons.Default.Mic,
                                contentDescription = "Sovereign Mic Record Icon",
                                tint = if (isCurrentlyRecording) Color.White else Color.Black,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (isCurrentlyRecording) "Stop" else "Speak Tape",
                            fontSize = 10.sp,
                            color = if (isCurrentlyRecording) Color.Red else Color.White,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    // Simulated Live Oscillating Bar Soundwave
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                            .background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val barCount = 18
                            val widthOffset = size.width / barCount
                            val baseHeight = size.height
                            val ampFactor = if (isCurrentlyRecording) recordVolumeState else 0.05f

                            // Generate pseudo-heights dynamically if recording to simulate a professional visualizer
                            for (b in 0 until barCount) {
                                val randomMultiplier = if (isCurrentlyRecording) {
                                    0.2f + 0.8f * kotlin.math.sin(b.toDouble() * 0.7 + System.currentTimeMillis() * 0.015).toFloat()
                                } else {
                                    0.1f + 0.1f * kotlin.math.sin(b.toDouble() * 0.4).toFloat()
                                }

                                val barHeight = (baseHeight * ampFactor * randomMultiplier * 0.95f).coerceAtLeast(4f)
                                val xPos = b * widthOffset + (widthOffset / 4)
                                val yStart = (baseHeight - barHeight) / 2f

                                drawRoundRect(
                                    color = goldPrimaryColor.copy(alpha = if (isCurrentlyRecording) 0.9f else 0.4f),
                                    topLeft = Offset(xPos, yStart),
                                    size = androidx.compose.ui.geometry.Size(widthOffset / 2f, barHeight),
                                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f, 4f)
                                )
                            }
                        }
                    }

                    // Playback Preview button
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.width(62.dp)
                    ) {
                        IconButton(
                            onClick = {
                                if (audioEngine.hasRecordings()) {
                                    audioEngine.playProcessed(selectedVoicePreset)
                                } else {
                                    Toast.makeText(context, "No voice recorded! Record first.", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier
                                .size(40.dp)
                                .background(Color.White.copy(alpha = 0.08f), CircleShape)
                                .border(1.dp, goldPrimaryColor.copy(alpha = 0.5f), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Preview Sound Filter",
                                tint = goldPrimaryColor,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Preview",
                            fontSize = 10.sp,
                            color = Color.White.copy(alpha = 0.8f),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Scrollable filters mapping list
                Text(
                    text = "Apply Royal Vocal Presets:",
                    fontSize = 10.sp,
                    color = Color.White.copy(alpha = 0.5f),
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(VoicePreset.values()) { filter ->
                        val isSelected = filter == selectedVoicePreset
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(24.dp))
                                .background(
                                    if (isSelected) goldPrimaryColor.copy(alpha = 0.25f)
                                    else Color.White.copy(alpha = 0.04f)
                                )
                                .border(
                                    1.dp,
                                    if (isSelected) goldPrimaryColor else Color.White.copy(alpha = 0.15f),
                                    RoundedCornerShape(24.dp)
                                )
                                .clickable {
                                    selectedVoicePreset = filter
                                    if (audioEngine.hasRecordings()) {
                                        audioEngine.playProcessed(filter)
                                    }
                                }
                                .padding(horizontal = 14.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = when (filter) {
                                    VoicePreset.ORIGINAL -> Icons.Default.Accessibility
                                    VoicePreset.THE_SHAH -> Icons.Default.AccountBalance
                                    VoicePreset.GOLDEN_ELF -> Icons.Default.ChildCare
                                    VoicePreset.CYBER_COMMANDER -> Icons.Default.SmartToy
                                    VoicePreset.COURT_PREACHER -> Icons.Default.GraphicEq
                                    VoicePreset.ECHO_CHAMBER -> Icons.Default.Hearing
                                },
                                contentDescription = filter.displayName,
                                tint = if (isSelected) goldPrimaryColor else Color.White.copy(alpha = 0.6f),
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Column {
                                Text(
                                    text = filter.displayName,
                                    fontSize = 11.sp,
                                    color = if (isSelected) goldPrimaryColor else Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = filter.description,
                                    fontSize = 8.sp,
                                    color = Color.White.copy(alpha = 0.5f),
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
        }

        // IV. Royal Multi-tab Controls (Keyboard view vs Sovereign Fanfares View)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { activeSubView = "keyboard" },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (activeSubView == "keyboard") goldPrimaryColor.copy(alpha = 0.15f)
                    else Color.Transparent
                ),
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(
                    1.dp,
                    if (activeSubView == "keyboard") goldPrimaryColor else Color.White.copy(alpha = 0.15f)
                ),
                modifier = Modifier
                    .weight(1f)
                    .height(36.dp),
                contentPadding = PaddingValues(0.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Keyboard,
                    contentDescription = "Animate Keyboard Icon",
                    tint = if (activeSubView == "keyboard") goldPrimaryColor else Color.White.copy(alpha = 0.6f),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    "Animated Keyboard",
                    fontSize = 12.sp,
                    color = if (activeSubView == "keyboard") goldPrimaryColor else Color.White.copy(alpha = 0.7f),
                    fontWeight = FontWeight.Bold
                )
            }

            Button(
                onClick = { activeSubView = "soundboard" },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (activeSubView == "soundboard") goldPrimaryColor.copy(alpha = 0.15f)
                    else Color.Transparent
                ),
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(
                    1.dp,
                    if (activeSubView == "soundboard") goldPrimaryColor else Color.White.copy(alpha = 0.15f)
                ),
                modifier = Modifier
                    .weight(1f)
                    .height(36.dp),
                contentPadding = PaddingValues(0.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.GridView,
                    contentDescription = "Active soundboard trigger",
                    tint = if (activeSubView == "soundboard") goldPrimaryColor else Color.White.copy(alpha = 0.6f),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    "Sovereign Soundboard",
                    fontSize = 12.sp,
                    color = if (activeSubView == "soundboard") goldPrimaryColor else Color.White.copy(alpha = 0.7f),
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // V. Subview Panel Display (Active Dynamic Content View)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(Color.Black.copy(alpha = 0.25f))
        ) {
            if (activeSubView == "keyboard") {
                // RENDER ANIMATED KEYBOARD CONTROLLER WITH CUSTOM THEMES
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 8.dp)
                ) {
                    // Customizable Settings Row (Theme Selecor & Key Tones)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Themes choice dropdown
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "Theme: ",
                                fontSize = 10.sp,
                                color = Color.White.copy(alpha = 0.5f),
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            listOf("Imperial Velvet", "Neon Obsidian", "Sapphire Diamond").forEach { themeName ->
                                Box(
                                    modifier = Modifier
                                        .padding(horizontal = 2.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(
                                            if (selectedThemeName == themeName) goldPrimaryColor.copy(alpha = 0.2f)
                                            else Color.White.copy(alpha = 0.05f)
                                        )
                                        .border(
                                            0.5.dp,
                                            if (selectedThemeName == themeName) goldPrimaryColor else Color.White.copy(alpha = 0.1f),
                                            RoundedCornerShape(6.dp)
                                        )
                                        .clickable { selectedThemeName = themeName }
                                        .padding(horizontal = 6.dp, vertical = 3.dp)
                                ) {
                                    Text(
                                        text = themeName.substringBefore(" "),
                                        fontSize = 9.sp,
                                        color = if (selectedThemeName == themeName) goldPrimaryColor else Color.White.copy(alpha = 0.6f),
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        // Tone selection profile
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "Sound: ",
                                fontSize = 10.sp,
                                color = Color.White.copy(alpha = 0.5f),
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            listOf("royal_harps" to "Harp", "electric_glitch" to "Robot", "standard_tick" to "Click").forEach { (type, label) ->
                                Box(
                                    modifier = Modifier
                                        .padding(horizontal = 2.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(
                                            if (selectedTonePreset == type) goldPrimaryColor.copy(alpha = 0.2f)
                                            else Color.White.copy(alpha = 0.05f)
                                        )
                                        .border(
                                            0.5.dp,
                                            if (selectedTonePreset == type) goldPrimaryColor else Color.White.copy(alpha = 0.1f),
                                            RoundedCornerShape(6.dp)
                                        )
                                        .clickable { selectedTonePreset = type }
                                        .padding(horizontal = 6.dp, vertical = 3.dp)
                                ) {
                                    Text(
                                        text = label,
                                        fontSize = 9.sp,
                                        color = if (selectedTonePreset == type) goldPrimaryColor else Color.White.copy(alpha = 0.6f),
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // THE QWERTY BOARD LAYOUT WRAPPER
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        // Drawing active physical particles floating over keys
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            clipRect {
                                particles.forEach { p ->
                                    drawCircle(
                                        color = p.color.copy(alpha = p.alpha),
                                        radius = (8f * p.scale).coerceAtLeast(1f),
                                        center = Offset(p.x, p.y)
                                    )
                                }
                            }
                        }

                        // Active physical board layout keys
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            val rows = if (keyboardShiftOn) {
                                listOf(
                                    "1234567890".toList(),
                                    "QWERTYUIOP".toList(),
                                    "ASDFGHJKL".toList(),
                                    listOf('⇧') + "ZXCVBNM".toList() + listOf('⌫')
                                )
                            } else {
                                listOf(
                                    "1234567890".toList(),
                                    "qwertyuiop".toList(),
                                    "asdfghjkl".toList(),
                                    listOf('⇧') + "zxcvbnm".toList() + listOf('⌫')
                                )
                            }

                            rows.forEachIndexed { rIndex, rowChars ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    rowChars.forEachIndexed { charIndex, item ->
                                        val isUtilityKey = item == '⇧' || item == '⌫'
                                        val keyColor = if (isUtilityKey) goldPrimaryColor.copy(alpha = 0.2f) else Color.Transparent

                                        Box(
                                            modifier = Modifier
                                                .padding(horizontal = 2.5.dp)
                                                .weight(if (item == '⌫' || item == '⇧') 1.4f else 1f)
                                                .height(46.dp)
                                                .shadow(
                                                    elevation = 3.dp,
                                                    shape = RoundedCornerShape(8.dp)
                                                )
                                                .background(keyAccentGrad, RoundedCornerShape(8.dp))
                                                .background(keyColor, RoundedCornerShape(8.dp))
                                                .border(
                                                    0.5.dp,
                                                    if (isUtilityKey) goldPrimaryColor.copy(alpha = 0.8f)
                                                    else goldPrimaryColor.copy(alpha = 0.35f),
                                                    RoundedCornerShape(8.dp)
                                                )
                                                .clickable {
                                                    spawnParticlesForKey(rIndex, charIndex)

                                                    // Vocalized Key sounds override
                                                    if (voiceToKeysMappingEnabled && audioEngine.hasRecordings()) {
                                                        // Distribute character codes to play customized pitch offsets
                                                        audioEngine.playProcessed(selectedVoicePreset)
                                                    } else {
                                                        // standard instrument beep synth
                                                        audioEngine.playKeyPressBeep(item, selectedTonePreset)
                                                    }

                                                    when (item) {
                                                        '⇧' -> keyboardShiftOn = !keyboardShiftOn
                                                        '⌫' -> {
                                                            if (currentTextMessage.isNotEmpty()) {
                                                                currentTextMessage = currentTextMessage.dropLast(1)
                                                            }
                                                        }
                                                        else -> {
                                                            currentTextMessage += item
                                                        }
                                                    }
                                                }
                                                .testTag("key_$item"),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = item.toString(),
                                                fontSize = if (isUtilityKey) 15.sp else 16.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isUtilityKey) goldPrimaryColor else Color.White
                                            )
                                        }
                                    }
                                }
                            }

                            // Space, Period & Action controls row
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 2.dp),
                                horizontalArrangement = Arrangement.Center
                            ) {
                                // Keyboard mode descriptor or custom quick injects
                                Box(
                                    modifier = Modifier
                                        .padding(horizontal = 3.dp)
                                        .weight(1.5f)
                                        .height(46.dp)
                                        .background(keyAccentGrad, RoundedCornerShape(8.dp))
                                        .border(
                                            0.5.dp,
                                            goldPrimaryColor.copy(alpha = 0.4f),
                                            RoundedCornerShape(8.dp)
                                        )
                                        .clickable {
                                            currentTextMessage += ", "
                                            audioEngine.playKeyPressBeep(',', selectedTonePreset)
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(",", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                }

                                // Interactive space bar
                                Box(
                                    modifier = Modifier
                                        .padding(horizontal = 3.dp)
                                        .weight(5f)
                                        .height(46.dp)
                                        .shadow(elevation = 2.dp, shape = RoundedCornerShape(8.dp))
                                        .background(keyAccentGrad, RoundedCornerShape(8.dp))
                                        .border(
                                            1.dp,
                                            goldPrimaryColor.copy(alpha = 0.7f),
                                            RoundedCornerShape(8.dp)
                                        )
                                        .clickable {
                                            spawnParticlesForKey(3, 4)
                                            audioEngine.playKeyPressBeep(' ', selectedTonePreset)
                                            currentTextMessage += " "
                                        }
                                        .testTag("key_space"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "SPACE DECREE",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = goldPrimaryColor,
                                        letterSpacing = 1.sp
                                    )
                                }

                                Box(
                                    modifier = Modifier
                                        .padding(horizontal = 3.dp)
                                        .weight(1.5f)
                                        .height(46.dp)
                                        .background(keyAccentGrad, RoundedCornerShape(8.dp))
                                        .border(
                                            0.5.dp,
                                            goldPrimaryColor.copy(alpha = 0.4f),
                                            RoundedCornerShape(8.dp)
                                        )
                                        .clickable {
                                            currentTextMessage += "."
                                            audioEngine.playKeyPressBeep('.', selectedTonePreset)
                                        }
                                        .testTag("key_period"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(".", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                }
                            }
                        }
                    }
                }
            } else {
                // RENDER ROYAL TRUMPETS SOVEREIGN VOICE BOARD EFFECTS GRID
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(14.dp)
                ) {
                    Text(
                        text = "SOVEREIGN VOICE BOARD EFFECTS",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = goldPrimaryColor,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    val soundOptions = listOf(
                        Triple("fanfare", "Triumphant Fanfare", "Synthesize full imperial brass horn chords"),
                        Triple("gong", "Palace Chime Gong", "A cavernous sub-frequency royal gong ring"),
                        Triple("bells", "Cathedral Carillons", "Ringing ancient bell echo frequencies"),
                        Triple("clash", "Saber Duel Blade", "Fierce metal noise slide swipe")
                    )

                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(soundOptions) { (key, title, desc) ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(110.dp)
                                    .clickable {
                                        audioEngine.playProceduralSound(key)
                                        currentTextMessage += " [$title Played!] "
                                    }
                                    .testTag("soundboard_card_$key"),
                                colors = CardDefaults.cardColors(
                                    containerColor = Color.White.copy(alpha = 0.04f)
                                ),
                                border = BorderStroke(1.dp, goldPrimaryColor.copy(alpha = 0.3f)),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(10.dp),
                                    verticalArrangement = Arrangement.Center,
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(
                                        imageVector = when (key) {
                                            "fanfare" -> Icons.Default.Campaign
                                            "gong" -> Icons.Default.Brightness5
                                            "bells" -> Icons.Default.NotificationsActive
                                            else -> Icons.Default.FlashOn
                                        },
                                        contentDescription = title,
                                        tint = goldPrimaryColor,
                                        modifier = Modifier.size(28.dp)
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = title,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        textAlign = TextAlign.Center
                                    )
                                    Text(
                                        text = desc,
                                        fontSize = 8.5.sp,
                                        color = Color.White.copy(alpha = 0.5f),
                                        textAlign = TextAlign.Center,
                                        lineHeight = 11.sp
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
