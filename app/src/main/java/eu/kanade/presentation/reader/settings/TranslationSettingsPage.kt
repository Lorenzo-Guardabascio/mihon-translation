package eu.kanade.presentation.reader.settings

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.dp
import eu.kanade.tachiyomi.ui.reader.setting.ReaderSettingsScreenModel
import tachiyomi.presentation.core.components.SettingsChipRow
import tachiyomi.presentation.core.components.SliderItem
import tachiyomi.presentation.core.util.collectAsState
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

private val languages = listOf(
    "English" to "en",
    "Italian" to "it",
    "Spanish" to "es",
    "French" to "fr",
    "German" to "de",
    "Japanese" to "ja",
    "Korean" to "ko",
    "Chinese" to "zh",
)

@Composable
internal fun ColumnScope.TranslationPage(screenModel: ReaderSettingsScreenModel) {
    val targetLang by screenModel.preferences.translationTargetLanguage().collectAsState()
    val sourceLang by screenModel.preferences.translationSourceLanguage().collectAsState()
    val backgroundAlpha by screenModel.preferences.translationBackgroundAlpha().collectAsState()
    val fontSize by screenModel.preferences.translationFontSize().collectAsState()

    SettingsChipRow(MR.strings.pref_translation_source) {
        languages.forEach { (label, value) ->
            FilterChip(
                selected = sourceLang == value,
                onClick = { screenModel.preferences.translationSourceLanguage().set(value) },
                label = { Text(label) },
            )
        }
    }

    SettingsChipRow(MR.strings.pref_translation_target) {
        languages.forEach { (label, value) ->
            FilterChip(
                selected = targetLang == value,
                onClick = { screenModel.preferences.translationTargetLanguage().set(value) },
                label = { Text(label) },
            )
        }
    }

    SliderItem(
        label = stringResource(MR.strings.pref_translation_background_alpha),
        value = (backgroundAlpha * 100).toInt(),
        valueRange = 0..100,
        valueString = "${(backgroundAlpha * 100).toInt()}%",
        onChange = { screenModel.preferences.translationBackgroundAlpha().set(it / 100f) },
    )

    SliderItem(
        label = stringResource(MR.strings.pref_translation_font_size),
        value = (fontSize * 100).toInt(),
        valueRange = 50..200,
        valueString = "${(fontSize * 100).toInt()}%",
        onChange = { screenModel.preferences.translationFontSize().set(it / 100f) },
    )
}
