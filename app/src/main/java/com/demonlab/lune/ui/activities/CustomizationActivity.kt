package com.demonlab.lune.ui.activities

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.demonlab.lune.R
import com.demonlab.lune.tools.SettingsManager
import com.demonlab.lune.ui.theme.LuneTheme

class CustomizationActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val settingsManager = SettingsManager.getInstance(this)
        enableEdgeToEdge()
        setContent {
            val themeMode = settingsManager.themeMode
            val systemInDarkTheme = isSystemInDarkTheme()
            val targetDarkTheme = when (themeMode) {
                1 -> false
                2 -> true
                else -> systemInDarkTheme
            }

            var useCustomColors by remember { mutableStateOf(settingsManager.useCustomColors) }
            var customColorPalette by remember { mutableIntStateOf(settingsManager.customColorPalette) }

            LuneTheme(
                darkTheme = targetDarkTheme,
                useCustomColors = useCustomColors,
                customColorPalette = customColorPalette
            ) {
                CustomizationScreen(
                    onBack = { finish() },
                    settingsManager = settingsManager,
                    useCustomColors = useCustomColors,
                    customColorPalette = customColorPalette,
                    onCustomColorsChanged = {
                        useCustomColors = it
                        settingsManager.useCustomColors = it
                    },
                    onPaletteChanged = {
                        customColorPalette = it
                        settingsManager.customColorPalette = it
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomizationScreen(
    onBack: () -> Unit,
    settingsManager: SettingsManager,
    useCustomColors: Boolean,
    customColorPalette: Int,
    onCustomColorsChanged: (Boolean) -> Unit,
    onPaletteChanged: (Int) -> Unit
) {
    var showCustomTitleDialog by remember { mutableStateOf(false) }
    var customTitle by remember { mutableStateOf(settingsManager.customTitle) }
    
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    if (showCustomTitleDialog) {
        var tempTitle by remember { mutableStateOf(customTitle) }
        AlertDialog(
            onDismissRequest = { showCustomTitleDialog = false },
            title = { Text(stringResource(R.string.custom_title)) },
            text = {
                OutlinedTextField(
                    value = tempTitle,
                    onValueChange = { tempTitle = it },
                    label = { Text("Título") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        IconButton(onClick = { tempTitle = "" }) {
                            Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.restore_default_title))
                        }
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    customTitle = tempTitle
                    settingsManager.customTitle = tempTitle
                    showCustomTitleDialog = false
                }) {
                    Text(stringResource(R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showCustomTitleDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
            shape = RoundedCornerShape(28.dp)
        )
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { 
                    Text(
                        stringResource(R.string.customization),
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.headlineLarge,
                        color = MaterialTheme.colorScheme.primary
                    ) 
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack, 
                                    contentDescription = stringResource(R.string.cd_back),
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }
                },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            SettingsSection(title = stringResource(R.string.customization)) {
                SettingsPreferenceItem(
                    headlineText = stringResource(R.string.custom_title),
                    supportingText = if (customTitle.isEmpty()) "Lune" else customTitle,
                    icon = Icons.Default.Edit,
                    position = SectionPosition.FIRST,
                    onClick = { showCustomTitleDialog = true }
                )

                SettingsPreferenceItem(
                    headlineText = stringResource(R.string.use_custom_colors),
                    supportingText = stringResource(R.string.use_custom_colors_desc),
                    icon = Icons.Default.Palette,
                    position = if (useCustomColors) SectionPosition.MIDDLE else SectionPosition.LAST,
                    trailingContent = {
                        Switch(
                            checked = useCustomColors,
                            onCheckedChange = onCustomColorsChanged,
                            thumbContent = {
                                Icon(
                                    imageVector = if (useCustomColors) Icons.Default.Check else Icons.Default.Close,
                                    contentDescription = null,
                                    modifier = Modifier.size(SwitchDefaults.IconSize)
                                )
                            }
                        )
                    }
                )

                if (useCustomColors) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 1.dp),
                        shape = RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp, bottomStart = 28.dp, bottomEnd = 28.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        tonalElevation = 1.dp
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = stringResource(R.string.color_palette),
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                val palettes = listOf(
                                    0 to Color(0xFF6650a4), // Default Purple
                                    1 to Color(0xFFB04B38), // Sunset Peach
                                    2 to Color(0xFF386B52), // Sage Green
                                    3 to Color(0xFF2E6580), // Ocean Breeze
                                    4 to Color(0xFF6E568F), // Lavender Mist
                                    5 to Color(0xFF7F5700)  // Warm Amber
                                )
                                palettes.forEach { (index, color) ->
                                    val isSelected = customColorPalette == index
                                    Box(
                                        modifier = Modifier
                                            .size(48.dp)
                                            .clip(CircleShape)
                                            .clickable { onPaletteChanged(index) },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Surface(
                                            shape = CircleShape,
                                            color = color,
                                            modifier = Modifier.fillMaxSize(),
                                            border = if (isSelected) androidx.compose.foundation.BorderStroke(
                                                3.dp,
                                                MaterialTheme.colorScheme.onSurface
                                            ) else null
                                        ) {
                                            if (isSelected) {
                                                Box(
                                                    contentAlignment = Alignment.Center,
                                                    modifier = Modifier.fillMaxSize()
                                                ) {
                                                    Icon(
                                                        Icons.Default.Brush,
                                                        contentDescription = null,
                                                        tint = Color.White,
                                                        modifier = Modifier.size(18.dp)
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
            }
        }
    }
}
