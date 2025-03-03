package com.example.llama.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

/**
 * Verbesserte Haupt-UI Komponente für autistische Nutzer
 * Implementiert konsistente Interaktionsmuster, klare visuelle Hierarchie und reduzierte Überreizung
 */
@Composable
fun UserInterface(
    spokenText: String,
    assistantResponse: String,
    explanation: String,
    isLoading: Boolean,
    onListenClick: () -> Unit,
    onRepeatClick: () -> Unit,
    onSettingsClick: () -> Unit,
    showSettingsDialog: Boolean,
    onDismissSettings: () -> Unit,
    ttsPitch: Float,
    ttsSpeed: Float,
    ttsVolume: Float,
    onTtsPitchChange: (Float) -> Unit,
    onTtsSpeedChange: (Float) -> Unit,
    onTtsVolumeChange: (Float) -> Unit,
    isDarkMode: Boolean,
    onDarkModeChange: (Boolean) -> Unit,
    explainEnabled: Boolean,
    onExplainChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    var showExplanation by remember { mutableStateOf(false) }
    
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Konsistente Kopfzeile
            Text(
                "Sprachassistent",
                style = TextStyle(
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                ),
                modifier = Modifier.padding(bottom = 24.dp)
            )
            
            // Klare Aktionsschaltfläche mit Konsistenz im Design
            Button(
                onClick = onListenClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Ersetze Icons.Filled.Mic mit einem Text-Symbol
                    Text(
                        text = "🎤",  // Mikrofon-Emoji als Symbol
                        style = TextStyle(fontSize = 22.sp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Sprechen",
                        style = TextStyle(fontSize = 18.sp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Klar abgegrenzter Bereich für die Nutzer-Eingabe
            DisplayCard(
                title = "Deine Nachricht:",
                content = spokenText,
                backgroundColor = MaterialTheme.colorScheme.surfaceVariant
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Lade-Indikator für visuelle Rückmeldung
            AnimatedVisibility(
                visible = isLoading,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .size(48.dp)
                        .padding(8.dp)
                )
            }
            
            // Klar abgegrenzter Bereich für die Assistenten-Antwort
            DisplayCard(
                title = "Assistenten-Antwort:",
                content = assistantResponse,
                backgroundColor = MaterialTheme.colorScheme.primaryContainer
            )
            
            // Erklärung (xAI) kann angezeigt werden, wenn aktiviert
            if (explainEnabled && explanation.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = { showExplanation = !showExplanation }
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "ℹ️", // Info-Emoji
                                style = TextStyle(fontSize = 20.sp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                if (showExplanation) "Erklärung ausblenden" else "Erklärung anzeigen",
                                style = TextStyle(fontSize = 14.sp)
                            )
                        }
                    }
                }
                
                AnimatedVisibility(
                    visible = showExplanation,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    DisplayCard(
                        title = "Erklärung:",
                        content = explanation,
                        backgroundColor = MaterialTheme.colorScheme.tertiaryContainer
                    )
                }
            }
            
            Spacer(modifier = Modifier.weight(1f))
            
            // Konsistente Aktionsleiste am unteren Bildschirmrand
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Wiederholen Button
                ActionButton(
                    onClick = onRepeatClick,
                    icon = "🔄", // Wiederholen-Emoji
                    text = "Wiederholen"
                )
                
                // Einstellungen Button
                ActionButton(
                    onClick = onSettingsClick,
                    icon = "⚙️", // Einstellungen-Emoji
                    text = "Einstellungen"
                )
            }
        }
        
        // Einstellungs-Dialog
        if (showSettingsDialog) {
            SettingsDialog(
                ttsPitch = ttsPitch,
                ttsSpeed = ttsSpeed,
                ttsVolume = ttsVolume,
                isDarkMode = isDarkMode,
                onTtsPitchChange = onTtsPitchChange,
                onTtsSpeedChange = onTtsSpeedChange,
                onTtsVolumeChange = onTtsVolumeChange,
                onDarkModeChange = onDarkModeChange,
                explainEnabled = explainEnabled,
                onExplainChange = onExplainChange,
                onDismiss = onDismissSettings
            )
        }
    }
}

/**
 * Karte zur Anzeige von Nachrichten mit klarer visueller Struktur
 */
@Composable
fun DisplayCard(
    title: String,
    content: String,
    backgroundColor: Color
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Text(
            text = title,
            style = TextStyle(
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            ),
            modifier = Modifier.padding(bottom = 4.dp)
        )
        
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .clip(RoundedCornerShape(8.dp))
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(8.dp)
                ),
            color = backgroundColor
        ) {
            Text(
                text = if (content.isEmpty()) "Warte auf Eingabe..." else content,
                style = TextStyle(fontSize = 16.sp),
                modifier = Modifier.padding(12.dp)
            )
        }
    }
}

/**
 * Konsistenter Aktions-Button mit Icon und Text
 */
@Composable
fun ActionButton(
    onClick: () -> Unit,
    icon: String,
    text: String
) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.padding(horizontal = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = icon,
                style = TextStyle(fontSize = 20.sp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(text)
        }
    }
}

/**
 * Einfacher Einstellungsdialog
 */
@Composable
fun SettingsDialog(
    ttsPitch: Float,
    ttsSpeed: Float,
    ttsVolume: Float,
    isDarkMode: Boolean,
    onTtsPitchChange: (Float) -> Unit,
    onTtsSpeedChange: (Float) -> Unit,
    onTtsVolumeChange: (Float) -> Unit,
    onDarkModeChange: (Boolean) -> Unit,
    explainEnabled: Boolean,
    onExplainChange: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth()
            ) {
                Text(
                    "Einstellungen",
                    style = TextStyle(
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                
                // Sprachwiedergabe-Einstellungen
                SettingsSection(title = "Sprachwiedergabe") {
                    // Stimmlage
                    SettingsSlider(
                        title = "Stimmlage",
                        value = ttsPitch,
                        onValueChange = onTtsPitchChange,
                        valueRange = 0.5f..1.5f,
                        steps = 4,
                        lowValueLabel = "Tiefer",
                        highValueLabel = "Höher"
                    )
                    
                    // Sprechgeschwindigkeit
                    SettingsSlider(
                        title = "Geschwindigkeit",
                        value = ttsSpeed,
                        onValueChange = onTtsSpeedChange,
                        valueRange = 0.7f..1.3f,
                        steps = 3,
                        lowValueLabel = "Langsamer",
                        highValueLabel = "Schneller"
                    )
                    
                    // Lautstärke
                    SettingsSlider(
                        title = "Lautstärke",
                        value = ttsVolume,
                        onValueChange = onTtsVolumeChange,
                        valueRange = 0.0f..1.0f,
                        steps = 4,
                        lowValueLabel = "Leiser",
                        highValueLabel = "Lauter"
                    )
                }
                
                Divider(modifier = Modifier.padding(vertical = 16.dp))
                
                // App-Einstellungen
                SettingsSection(title = "App-Einstellungen") {
                    // Dark Mode
                    SettingsToggle(
                        title = "Dunkler Modus",
                        description = "Dunkle Farben für das Erscheinungsbild",
                        checked = isDarkMode,
                        onCheckedChange = onDarkModeChange
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // Erklärungen
                    SettingsToggle(
                        title = "Erklärungen anzeigen",
                        description = "Zusätzliche Informationen zu Antworten anzeigen",
                        checked = explainEnabled,
                        onCheckedChange = onExplainChange
                    )
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Schließen-Button
                Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Text("Schließen")
                }
            }
        }
    }
}

/**
 * Abschnitt für Einstellungsgruppen
 */
@Composable
fun SettingsSection(
    title: String,
    content: @Composable () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = TextStyle(
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            ),
            modifier = Modifier.padding(bottom = 12.dp)
        )
        content()
    }
}

/**
 * Einstellungs-Slider mit klaren Labels
 */
@Composable
fun SettingsSlider(
    title: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    lowValueLabel: String,
    highValueLabel: String
) {
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Text(
            text = title,
            style = TextStyle(fontSize = 14.sp)
        )
        
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
            modifier = Modifier.padding(vertical = 8.dp)
        )
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = lowValueLabel,
                style = TextStyle(fontSize = 12.sp)
            )
            Text(
                text = highValueLabel,
                style = TextStyle(fontSize = 12.sp)
            )
        }
    }
}

/**
 * Einstellungs-Toggle mit Beschreibung
 */
@Composable
fun SettingsToggle(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = TextStyle(fontSize = 14.sp)
            )
            Text(
                text = description,
                style = TextStyle(
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        }
        
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors()
        )
    }
}
