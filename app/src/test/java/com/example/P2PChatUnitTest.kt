package com.example

import com.example.api.ApiConfig
import com.example.api.ApiErrorHandler
import com.example.api.ApiResult
import com.example.api.AppError
import com.example.database.DateTimeUtils
import com.example.localization.AppStrings
import com.example.localization.EnglishStrings
import com.example.localization.PersianStrings
import com.example.models.ApiResponse
import com.example.models.ChatWallpaper
import com.example.models.FontSizeOption
import com.example.models.ThemeMode
import com.example.models.UiLanguage
import com.example.models.UserSettings
import com.example.models.VisibilityScope
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response
import kotlin.reflect.full.memberProperties

class P2PChatUnitTest {

    private val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
    private val errorHandler = ApiErrorHandler(moshi)

    @Test
    fun baseUrlMatchesProductionServer() {
        assertEquals("https://omitok07090.pythonanywhere.com/", ApiConfig.BASE_URL)
        assertTrue(ApiConfig.BASE_URL.startsWith("https://"))
    }

    @Test
    fun defaultLanguageIsPersian() {
        assertEquals(UiLanguage.PERSIAN, UiLanguage.fromCode(null))
        assertEquals(UiLanguage.ENGLISH, UiLanguage.fromCode("en"))
        assertEquals(UiLanguage.PERSIAN, UiLanguage.fromCode("fa"))
    }

    @Test
    fun userSettingsDefaultsAndEnumsParseCleanly() {
        val defaults = UserSettings()
        assertEquals(UiLanguage.PERSIAN, defaults.language)
        assertEquals(ThemeMode.DARK, defaults.themeMode)
        assertEquals(FontSizeOption.NORMAL, defaults.fontSize)
        assertEquals(ChatWallpaper.COSMIC_GRAPHITE, defaults.wallpaper)
        assertEquals(VisibilityScope.EVERYONE, defaults.profileVisibility)
        assertFalse(defaults.appLockEnabled)

        assertEquals(ThemeMode.LIGHT, ThemeMode.fromKey("light"))
        assertEquals(ThemeMode.DARK, ThemeMode.fromKey("invalid"))
        assertEquals(FontSizeOption.LARGE, FontSizeOption.fromKey("large"))
        assertEquals(ChatWallpaper.NEON_AURORA, ChatWallpaper.fromKey("aurora"))
        assertEquals(VisibilityScope.CONTACTS, VisibilityScope.fromKey("contacts"))
    }

    @Test
    fun visibleStringsExposeZeroServerOrBackendDetails() {
        val forbiddenTerms = listOf(
            "pythonanywhere",
            "flask",
            "omitok07090",
            "localhost",
            "127.0.0.1",
            "/api/",
            "api/health",
            "sqlite",
            "سرور",
            "فلاسک",
            "پایتون"
        )
        val packs = listOf(EnglishStrings, PersianStrings)
        for (pack in packs) {
            for (prop in AppStrings::class.memberProperties) {
                val value = prop.get(pack) as? String ?: continue
                assertTrue("Property ${prop.name} must not be blank", value.isNotBlank())
                val lower = value.lowercase()
                for (term in forbiddenTerms) {
                    assertFalse(
                        "Forbidden term '$term' found in ${pack.language}.${prop.name}: $value",
                        lower.contains(term)
                    )
                }
            }
        }
    }

    @Test
    fun isoTimestampParsesCleanly() {
        val millis = DateTimeUtils.parseIsoToMillis("2026-11-16T12:49:25.544470+00:00")
        assertTrue(millis > 0L)
    }

    @Test
    fun errorHandlerMapsAllHttpStatusCodes() = runTest {
        val jsonType = "application/json".toMediaType()
        val cases = listOf(
            400 to AppError.BadRequest,
            401 to AppError.Unauthorized,
            403 to AppError.Forbidden,
            404 to AppError.NotFound,
            413 to AppError.PayloadTooLarge,
            500 to AppError.ServerError
        )
        for ((code, expected) in cases) {
            val res = errorHandler.safeCall<String>(emitUnauthorizedOn401 = false) {
                Response.error<ApiResponse<String>>(
                    code,
                    """{"success":false,"error":"err"}""".toResponseBody(jsonType)
                )
            }
            assertTrue("Expected failure for $code", res is ApiResult.Failure)
            val err = (res as ApiResult.Failure).error
            assertEquals(expected, err)
            assertTrue(err.message(PersianStrings).isNotBlank())
            assertTrue(err.message(EnglishStrings).isNotBlank())
        }
    }
}
