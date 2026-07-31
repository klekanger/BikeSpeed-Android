package no.bikespeed.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import no.bikespeed.app.model.AppLanguage
import no.bikespeed.app.model.MeasurementSystem
import no.bikespeed.app.service.SettingsStore
import no.bikespeed.app.ui.theme.GaugePalette

/**
 * The settings screen: auto-pause toggle, gauge max-speed stepper (stored canonical km/h,
 * stepped in the display unit via [GaugeSpeedStepper]), unit picker, and language picker
 * (persisted now; applied app-wide when the localization step lands).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: SettingsStore,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val autoPauseEnabled by settings.autoPauseEnabledFlow.collectAsState()
    val measurementSystem by settings.measurementSystemFlow.collectAsState()
    val maxGaugeSpeedKmh by settings.maxGaugeSpeedKmhFlow.collectAsState()
    val appLanguage by settings.appLanguageFlow.collectAsState()
    val locale = Locale.getDefault()

    Scaffold(
        modifier = modifier,
        containerColor = Color.Black,
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            SettingsSection(title = "Trip") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Auto-pause", color = Color.White, modifier = Modifier.weight(1f))
                    Switch(
                        checked = autoPauseEnabled,
                        onCheckedChange = { settings.autoPauseEnabled = it },
                        modifier = Modifier.testTag("autoPauseToggle"),
                    )
                }
                Text(
                    text = "Pauses distance and time automatically when you stop.",
                    fontSize = 12.sp,
                    color = Color(0xFF9E9E9E),
                )
            }

            SettingsSection(title = "Gauge") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text("Max speed", color = Color.White, modifier = Modifier.weight(1f))
                    Text(
                        text = measurementSystem.formattedMaxGaugeSpeed(
                            fromCanonicalKmh = maxGaugeSpeedKmh,
                            locale = locale,
                        ),
                        color = GaugePalette.accent,
                        modifier = Modifier.testTag("maxSpeedValue"),
                    )
                    OutlinedIconButton(
                        onClick = {
                            settings.maxGaugeSpeedKmh = GaugeSpeedStepper.step(
                                currentCanonicalKmh = settings.maxGaugeSpeedKmh,
                                system = settings.measurementSystem,
                                direction = GaugeSpeedStepper.Direction.DOWN,
                            )
                        },
                        modifier = Modifier.testTag("maxSpeedDown"),
                    ) { Text("−", color = Color.White) }
                    OutlinedIconButton(
                        onClick = {
                            settings.maxGaugeSpeedKmh = GaugeSpeedStepper.step(
                                currentCanonicalKmh = settings.maxGaugeSpeedKmh,
                                system = settings.measurementSystem,
                                direction = GaugeSpeedStepper.Direction.UP,
                            )
                        },
                        modifier = Modifier.testTag("maxSpeedUp"),
                    ) { Text("+", color = Color.White) }
                }
            }

            SettingsSection(title = "Units") {
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    MeasurementSystem.entries.forEachIndexed { index, system ->
                        SegmentedButton(
                            selected = measurementSystem == system,
                            onClick = { settings.measurementSystem = system },
                            shape = SegmentedButtonDefaults.itemShape(index, MeasurementSystem.entries.size),
                            modifier = Modifier.testTag("unit_${system.storageKey}"),
                        ) {
                            Text(if (system == MeasurementSystem.METRIC) "Metric" else "Imperial")
                        }
                    }
                }
            }

            SettingsSection(title = "Language") {
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    val languages = listOf(
                        AppLanguage.SYSTEM to "System",
                        AppLanguage.NORWEGIAN to "Norsk",
                        AppLanguage.ENGLISH to "English",
                    )
                    languages.forEachIndexed { index, (language, label) ->
                        SegmentedButton(
                            selected = appLanguage == language,
                            onClick = { settings.appLanguage = language },
                            shape = SegmentedButtonDefaults.itemShape(index, languages.size),
                            modifier = Modifier.testTag("language_${language.storageKey}"),
                        ) {
                            Text(label)
                        }
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text("BikeSpeed", fontSize = 12.sp, color = Color.White)
                Text("Lekanger tekst og kode 2026", fontSize = 12.sp, color = Color(0xFF9E9E9E))
                Text("MIT License", fontSize = 12.sp, color = Color(0xFF9E9E9E))
            }
        }
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = GaugePalette.accent,
        )
        content()
    }
}
