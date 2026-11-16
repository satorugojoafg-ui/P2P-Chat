package com.example.localization

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import com.example.models.UiLanguage

val LocalAppStrings = staticCompositionLocalOf { PersianStrings }

fun stringsFor(language: UiLanguage): AppStrings = when (language) {
    UiLanguage.PERSIAN -> PersianStrings
    UiLanguage.ENGLISH -> EnglishStrings
}

@Composable
fun ProvideLocalization(
    language: UiLanguage,
    content: @Composable () -> Unit
) {
    val strings = stringsFor(language)
    val layoutDirection = when (language) {
        UiLanguage.PERSIAN -> LayoutDirection.Rtl
        UiLanguage.ENGLISH -> LayoutDirection.Ltr
    }
    CompositionLocalProvider(
        LocalAppStrings provides strings,
        LocalLayoutDirection provides layoutDirection,
        content = content
    )
}
