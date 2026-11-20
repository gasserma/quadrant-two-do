package io.github.gasserma.quadranttwodo.ui.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.gasserma.quadranttwodo.R
import io.github.gasserma.quadranttwodo.core.Settings
import io.github.gasserma.quadranttwodo.core.ThemeMode
import io.github.gasserma.quadranttwodo.core.UrgencyScheme
import io.github.gasserma.quadranttwodo.ui.common.QuadrantMark
import io.github.gasserma.quadranttwodo.ui.theme.SectionHeading
import io.github.gasserma.quadranttwodo.ui.theme.urgencyColorOf
import kotlin.math.roundToInt

/**
 * The five marks previewing a scheme, as (urgency for the fill, urgency for the colour): they fill
 * one, two, three and four cells while the colour runs the whole range, from just done (-1) to a
 * period late (1), so every scheme shows its first colour too.
 */
private val PREVIEW = listOf(-0.75 to -1.0, -0.5 to -0.5, -0.25 to 0.0, 0.0 to 0.5, 0.0 to 1.0)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel, onBack: () -> Unit) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("back")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                    }
                },
                title = { Text(stringResource(R.string.title_settings)) },
            )
        },
    ) { padding ->
        val loaded = settings ?: return@Scaffold
        SettingsContent(
            settings = loaded,
            onTheme = viewModel::setTheme,
            onWidgetOpacity = viewModel::setWidgetOpacity,
            onScheme = viewModel::setScheme,
            modifier = Modifier.padding(padding),
        )
    }
}

@Composable
private fun SettingsContent(
    settings: Settings,
    onTheme: (ThemeMode) -> Unit,
    onWidgetOpacity: (Int) -> Unit,
    onScheme: (UrgencyScheme) -> Unit,
    modifier: Modifier,
) {
    LazyColumn(modifier.fillMaxSize().testTag("settingsList")) {
        item { Section(R.string.settings_theme) { ThemeChoice(settings.theme, onTheme) } }
        item { Section(R.string.settings_widget_opacity) { OpacitySlider(settings.widgetOpacity, onWidgetOpacity) } }
        item { Section(R.string.settings_colours) { Hint(stringResource(R.string.settings_colours_hint)) } }
        items(UrgencyScheme.entries, key = { it.id }) { scheme ->
            SchemeRow(scheme, selected = scheme == settings.scheme, onSelect = { onScheme(scheme) })
        }
    }
}

@Composable
private fun Section(@StringRes heading: Int, content: @Composable () -> Unit) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 4.dp),
    ) {
        Text(stringResource(heading).uppercase(), style = SectionHeading)
        content()
    }
}

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ThemeChoice(current: ThemeMode, onTheme: (ThemeMode) -> Unit) {
    val options = ThemeMode.entries
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, mode ->
            SegmentedButton(
                selected = mode == current,
                onClick = { onTheme(mode) },
                shape = SegmentedButtonDefaults.itemShape(index, options.size),
                modifier = Modifier.testTag("theme:${mode.id}"),
            ) { Text(stringResource(mode.label())) }
        }
    }
    Hint(stringResource(R.string.settings_theme_hint))
}

/** Moves in 5% steps, and saves when the finger lifts rather than on every step. */
@Composable
private fun OpacitySlider(current: Int, onWidgetOpacity: (Int) -> Unit) {
    var value by remember(current) { mutableFloatStateOf(current.toFloat()) }
    Hint(stringResource(R.string.settings_widget_opacity_hint, value.roundToInt()))
    Slider(
        value = value,
        onValueChange = { value = it },
        onValueChangeFinished = { onWidgetOpacity(value.roundToInt()) },
        valueRange = 0f..100f,
        steps = 19,
        modifier = Modifier.testTag("widgetOpacity"),
    )
}

/** A scheme to pick: radio button, name and description, and five marks showing its colours. */
@Composable
private fun SchemeRow(scheme: UrgencyScheme, selected: Boolean, onSelect: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onSelect, role = Role.RadioButton)
            .padding(start = 4.dp, end = 16.dp, top = 6.dp, bottom = 6.dp)
            .testTag("scheme:${scheme.id}"),
    ) {
        RadioButton(selected = selected, onClick = null, modifier = Modifier.padding(12.dp))
        Column(Modifier.weight(1f)) {
            Text(stringResource(scheme.label()), style = MaterialTheme.typography.bodyLarge)
            Text(
                stringResource(scheme.detail()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 6.dp)) {
                for ((fill, colour) in PREVIEW) {
                    QuadrantMark(fill, urgencyColorOf(colour, scheme), Modifier.size(18.dp))
                }
            }
        }
    }
}

@StringRes
private fun ThemeMode.label(): Int = when (this) {
    ThemeMode.System -> R.string.theme_system
    ThemeMode.Light -> R.string.theme_light
    ThemeMode.Dark -> R.string.theme_dark
}

@StringRes
private fun UrgencyScheme.label(): Int = when (this) {
    UrgencyScheme.Dusk -> R.string.scheme_dusk
    UrgencyScheme.TrafficLight -> R.string.scheme_traffic_light
    UrgencyScheme.BlueOrange -> R.string.scheme_blue_orange
    UrgencyScheme.Sunset -> R.string.scheme_sunset
    UrgencyScheme.Forest -> R.string.scheme_forest
    UrgencyScheme.Ocean -> R.string.scheme_ocean
    UrgencyScheme.Berry -> R.string.scheme_berry
    UrgencyScheme.Fire -> R.string.scheme_fire
    UrgencyScheme.Monochrome -> R.string.scheme_monochrome
}

@StringRes
private fun UrgencyScheme.detail(): Int = when (this) {
    UrgencyScheme.Dusk -> R.string.scheme_dusk_detail
    UrgencyScheme.TrafficLight -> R.string.scheme_traffic_light_detail
    UrgencyScheme.BlueOrange -> R.string.scheme_blue_orange_detail
    UrgencyScheme.Sunset -> R.string.scheme_sunset_detail
    UrgencyScheme.Forest -> R.string.scheme_forest_detail
    UrgencyScheme.Ocean -> R.string.scheme_ocean_detail
    UrgencyScheme.Berry -> R.string.scheme_berry_detail
    UrgencyScheme.Fire -> R.string.scheme_fire_detail
    UrgencyScheme.Monochrome -> R.string.scheme_monochrome_detail
}
