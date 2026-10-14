package io.github.freevideocaster.roku

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TrackLabelsTest {

    private fun label(language: String, name: String = "", index: Int = 0) =
        TrackLabels.label(Track("id", language, name), index)

    @Test
    fun languageCodesBecomeSpanishNames() {
        assertEquals("Inglés", label("eng"))
        assertEquals("Inglés", label("en"))
        assertEquals("Español", label("spa"))
        assertEquals("Francés", label("fr"))
        assertEquals("Japonés", label("ja"))
    }

    @Test
    fun spanishVariantsAreRecognizedFromTheTrackName() {
        assertEquals("Español (Latino)", label("spa", "Latino"))
        assertEquals("Español (Latino)", label("spa", "Español Latino 5.1"))
        assertEquals("Español (Latino)", label("es", "Audio Lat"))
        assertEquals("Español (España)", label("spa", "Castellano"))
    }

    @Test
    fun nameThatRepeatsTheLanguageIsNotDuplicated() {
        assertEquals("Español", label("es", "Español"))
        assertEquals("Inglés", label("eng", "English"))
        assertEquals("Francés", label("fr", "Français"))
        assertEquals("Japonés", label("ja", "日本語"))
    }

    @Test
    fun usefulNamesAreAppended() {
        // Dos pistas en el mismo idioma deben distinguirse.
        assertEquals("Inglés · BipBop Audio 1", label("eng", "BipBop Audio 1"))
        assertEquals("Inglés · Comentarios del director", label("en", "Comentarios del director"))
    }

    @Test
    fun unknownLanguageFallsBackToTheNameOrAPosition() {
        assertEquals("Pista principal", label("und", "Pista principal"))
        assertEquals("Pista 3", label("", "", index = 2))
        assertEquals("Pista 1", label("xx"))
    }

    @Test
    fun languageNameLookupIsCaseInsensitive() {
        assertEquals("Inglés", TrackLabels.languageName(" ENG "))
        assertNull(TrackLabels.languageName("zzz"))
    }
}
