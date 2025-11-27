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

    SettingsChipRow(MR.strings.pref_translation_target) {
        languages.forEach { (label, value) ->
            FilterChip(
                selected = targetLang == value,
                onClick = { screenModel.preferences.translationTargetLanguage().set(value) },
                label = { Text(label) },
            )
        }
    }
}
