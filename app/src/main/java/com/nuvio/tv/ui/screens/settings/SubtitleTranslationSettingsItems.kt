@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.nuvio.tv.ui.screens.settings

import android.view.KeyEvent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.nuvio.tv.R
import com.nuvio.tv.core.subtitles.OPENROUTER_MODEL_CHOICES
import com.nuvio.tv.core.subtitles.SUBTITLE_TRANSLATION_TARGET_PREFERRED
import com.nuvio.tv.core.subtitles.SubtitleTranslationPreferences
import com.nuvio.tv.core.subtitles.SubtitleTranslationProvider
import com.nuvio.tv.core.subtitles.SubtitleTranslationService
import com.nuvio.tv.data.local.AVAILABLE_SUBTITLE_LANGUAGES
import com.nuvio.tv.ui.components.NuvioDialog
import com.nuvio.tv.ui.theme.NuvioTheme
import kotlinx.coroutines.launch

private const val TEST_SAMPLE_TEXT = "Where were you last night?\nI waited for hours."

private enum class TranslationTextField { ApiKey, Model }

/** AI subtitle translation rows; settings live outside PlayerSettingsDataStore and account sync. */
@Composable
internal fun subtitleTranslationSettingsItems(enabled: Boolean, preferredSubtitleLanguage: String?) {
    val context = LocalContext.current
    SubtitleTranslationPreferences.ensureLoaded(context)
    val settings by SubtitleTranslationPreferences.settings.collectAsStateWithLifecycle()
    var editingField by remember { mutableStateOf<TranslationTextField?>(null) }
    var showTargetDialog by remember { mutableStateOf(false) }
    var testStatus by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val notSet = stringResource(R.string.subtitle_ai_translation_key_missing)
    val builtIn = stringResource(R.string.subtitle_ai_translation_key_built_in)
    val preferredLabel = AVAILABLE_SUBTITLE_LANGUAGES.firstOrNull { it.code == preferredSubtitleLanguage }?.name
        ?: preferredSubtitleLanguage.orEmpty()

    SettingsToggleRow(
        title = stringResource(R.string.subtitle_ai_translation_title),
        subtitle = stringResource(R.string.subtitle_ai_translation_subtitle),
        checked = settings.enabled,
        onToggle = { SubtitleTranslationPreferences.update(context) { it.copy(enabled = !settings.enabled) } },
        enabled = enabled,
    )
    if (!settings.enabled) return

    SettingsActionRow(
        title = stringResource(R.string.subtitle_ai_translation_provider),
        subtitle = stringResource(R.string.subtitle_ai_translation_provider_subtitle),
        value = settings.provider.label,
        enabled = enabled,
        onClick = {
            val providers = SubtitleTranslationProvider.entries
            val next = providers[(providers.indexOf(settings.provider) + 1) % providers.size]
            testStatus = null
            SubtitleTranslationPreferences.update(context) { it.copy(provider = next) }
        },
    )
    SettingsActionRow(
        title = stringResource(R.string.subtitle_ai_translation_key),
        subtitle = stringResource(R.string.subtitle_ai_translation_key_subtitle),
        value = if (settings.apiKey.isNotEmpty() && settings.apiKey == SubtitleTranslationPreferences.builtInOpenRouterKey) {
            builtIn
        } else {
            maskSecret(settings.apiKey, notSet)
        },
        enabled = enabled,
        onClick = { editingField = TranslationTextField.ApiKey },
    )
    if (settings.provider == SubtitleTranslationProvider.OpenRouter) {
        // OpenRouter offers two models; pressing the row switches between them.
        val modelIndex = OPENROUTER_MODEL_CHOICES.indexOfFirst { it.id == settings.model }
        SettingsActionRow(
            title = stringResource(R.string.subtitle_ai_translation_model),
            subtitle = stringResource(R.string.subtitle_ai_translation_model_choice_subtitle),
            value = OPENROUTER_MODEL_CHOICES.getOrNull(modelIndex)?.label ?: settings.model,
            enabled = enabled,
            onClick = {
                val next = OPENROUTER_MODEL_CHOICES[(modelIndex + 1).mod(OPENROUTER_MODEL_CHOICES.size)]
                testStatus = null
                SubtitleTranslationPreferences.update(context) { it.withModel(next.id) }
            },
        )
    } else {
        SettingsActionRow(
            title = stringResource(R.string.subtitle_ai_translation_model),
            subtitle = stringResource(R.string.subtitle_ai_translation_model_subtitle),
            value = settings.model,
            enabled = enabled,
            onClick = { editingField = TranslationTextField.Model },
        )
    }
    SettingsActionRow(
        title = stringResource(R.string.subtitle_ai_translation_target),
        subtitle = stringResource(R.string.subtitle_ai_translation_target_subtitle),
        value = if (settings.targetLanguage == SUBTITLE_TRANSLATION_TARGET_PREFERRED) {
            stringResource(R.string.subtitle_ai_translation_target_preferred, preferredLabel)
        } else {
            AVAILABLE_SUBTITLE_LANGUAGES.firstOrNull { it.code == settings.targetLanguage }?.name ?: settings.targetLanguage
        },
        enabled = enabled,
        onClick = { showTargetDialog = true },
    )
    SettingsToggleRow(
        title = stringResource(R.string.subtitle_ai_translation_prefer_embedded),
        subtitle = stringResource(R.string.subtitle_ai_translation_prefer_embedded_subtitle),
        checked = settings.preferEmbeddedSubtitles,
        onToggle = {
            SubtitleTranslationPreferences.update(context) { it.copy(preferEmbeddedSubtitles = !settings.preferEmbeddedSubtitles) }
        },
        enabled = enabled,
    )
    val testingText = stringResource(R.string.subtitle_ai_translation_testing)
    val noKeyText = stringResource(R.string.subtitle_ai_translation_test_no_key)
    SettingsActionRow(
        title = stringResource(R.string.subtitle_ai_translation_test),
        subtitle = testStatus ?: stringResource(R.string.subtitle_ai_translation_test_subtitle),
        enabled = enabled,
        onClick = {
            if (settings.apiKey.isBlank()) {
                testStatus = noKeyText
                return@SettingsActionRow
            }
            testStatus = testingText
            scope.launch {
                testStatus = SubtitleTranslationService.testTranslation(settings, preferredSubtitleLanguage, TEST_SAMPLE_TEXT).fold(
                    onSuccess = { sample -> context.getString(R.string.subtitle_ai_translation_test_ok, sample) },
                    onFailure = { error -> context.getString(R.string.subtitle_ai_translation_test_failed, error.message.orEmpty()) },
                )
            }
        },
    )

    when (editingField) {
        TranslationTextField.ApiKey -> TranslationTextDialog(
            title = stringResource(R.string.subtitle_ai_translation_key_dialog_title, settings.provider.label),
            subtitle = stringResource(R.string.subtitle_ai_translation_key_subtitle),
            initialValue = "",
            placeholder = if (settings.provider == SubtitleTranslationProvider.OpenAi) "sk-..." else "sk-or-v1-...",
            onClear = if (settings.apiKey.isNotBlank()) {
                { SubtitleTranslationPreferences.update(context) { it.withApiKey("") } }
            } else {
                null
            },
            onSave = { value ->
                testStatus = null
                if (value.isNotBlank()) SubtitleTranslationPreferences.update(context) { it.withApiKey(value) }
            },
            onDismiss = { editingField = null },
        )
        TranslationTextField.Model -> TranslationTextDialog(
            title = stringResource(R.string.subtitle_ai_translation_model),
            subtitle = stringResource(R.string.subtitle_ai_translation_model_subtitle),
            initialValue = settings.configuredModel,
            placeholder = settings.provider.defaultModel,
            onClear = null,
            onSave = { value ->
                testStatus = null
                SubtitleTranslationPreferences.update(context) { it.withModel(value) }
            },
            onDismiss = { editingField = null },
        )
        null -> Unit
    }

    if (showTargetDialog) {
        LanguageSelectionDialog(
            title = stringResource(R.string.subtitle_ai_translation_target),
            selectedLanguage = settings.targetLanguage,
            showNoneOption = false,
            extraOptions = listOf(
                SUBTITLE_TRANSLATION_TARGET_PREFERRED to
                    stringResource(R.string.subtitle_ai_translation_target_preferred, preferredLabel)
            ),
            onLanguageSelected = { language ->
                SubtitleTranslationPreferences.update(context) {
                    it.copy(targetLanguage = language ?: SUBTITLE_TRANSLATION_TARGET_PREFERRED)
                }
                showTargetDialog = false
            },
            onDismiss = { showTargetDialog = false },
        )
    }
}

@Composable
internal fun TranslationTextDialog(
    title: String,
    subtitle: String,
    initialValue: String,
    placeholder: String,
    onClear: (() -> Unit)?,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var value by remember { mutableStateOf(initialValue) }
    var isInputFocused by remember { mutableStateOf(false) }
    val inputFocusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val shape = RoundedCornerShape(10.dp)

    NuvioDialog(onDismiss = onDismiss, title = title, subtitle = subtitle, width = 700.dp) {
        Card(
            onClick = { inputFocusRequester.requestFocus() },
            modifier = Modifier.fillMaxWidth().onFocusChanged { isInputFocused = it.isFocused || it.hasFocus },
            colors = CardDefaults.colors(
                containerColor = NuvioTheme.colors.BackgroundElevated,
                focusedContainerColor = NuvioTheme.colors.BackgroundElevated
            ),
            border = CardDefaults.border(
                border = Border(
                    border = androidx.compose.foundation.BorderStroke(NuvioTheme.spacing.hairline, NuvioTheme.colors.Border),
                    shape = shape
                ),
                focusedBorder = Border(border = NuvioTheme.focusRing.border(NuvioTheme.spacing.xxs), shape = shape)
            ),
            shape = CardDefaults.shape(shape),
            scale = CardDefaults.scale(focusedScale = 1f)
        ) {
            Box(modifier = Modifier.padding(horizontal = 14.dp, vertical = NuvioTheme.spacing.md)) {
                BasicTextField(
                    value = value,
                    onValueChange = { value = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(inputFocusRequester)
                        .onKeyEvent { event ->
                            event.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_CENTER &&
                                event.nativeKeyEvent.action == KeyEvent.ACTION_DOWN
                        },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { keyboardController?.hide() }),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        color = NuvioTheme.colors.TextPrimary,
                        textDirection = TextDirection.Content
                    ),
                    cursorBrush = SolidColor(if (isInputFocused) NuvioTheme.colors.Primary else Color.Transparent),
                    decorationBox = { innerTextField ->
                        if (value.isBlank()) {
                            Text(text = placeholder, style = MaterialTheme.typography.bodyMedium, color = NuvioTheme.colors.TextTertiary)
                        }
                        innerTextField()
                    }
                )
            }
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            val secondaryColors = ButtonDefaults.colors(
                containerColor = NuvioTheme.colors.BackgroundElevated,
                contentColor = NuvioTheme.colors.TextPrimary
            )
            Button(onClick = onDismiss, colors = secondaryColors) { Text(stringResource(R.string.action_cancel)) }
            if (onClear != null) {
                Spacer(modifier = Modifier.width(NuvioTheme.spacing.sm))
                Button(onClick = { onClear(); onDismiss() }, colors = secondaryColors) { Text(stringResource(R.string.action_clear)) }
            }
            Spacer(modifier = Modifier.width(NuvioTheme.spacing.sm))
            Button(
                onClick = { onSave(value.trim()); onDismiss() },
                colors = ButtonDefaults.colors(
                    containerColor = NuvioTheme.colors.BackgroundCard,
                    contentColor = NuvioTheme.colors.TextPrimary
                )
            ) { Text(stringResource(R.string.action_save)) }
        }
    }
}

private fun maskSecret(key: String, notSetLabel: String): String {
    val trimmed = key.trim()
    if (trimmed.isBlank()) return notSetLabel
    return if (trimmed.length <= 4) "••••" else "••••••${trimmed.takeLast(4)}"
}
