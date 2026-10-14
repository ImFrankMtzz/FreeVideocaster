package io.github.freevideocaster.roku

/** Nombres legibles para las pistas: «Español (Latino)», «Inglés», «Pista 2»… */
object TrackLabels {

    private val LANGUAGES = mapOf(
        "eng" to "Inglés", "en" to "Inglés",
        "spa" to "Español", "es" to "Español",
        "fre" to "Francés", "fra" to "Francés", "fr" to "Francés",
        "por" to "Portugués", "pt" to "Portugués",
        "ita" to "Italiano", "it" to "Italiano",
        "ger" to "Alemán", "deu" to "Alemán", "de" to "Alemán",
        "jpn" to "Japonés", "ja" to "Japonés",
        "kor" to "Coreano", "ko" to "Coreano",
        "chi" to "Chino", "zho" to "Chino", "zh" to "Chino",
        "rus" to "Ruso", "ru" to "Ruso",
        "ara" to "Árabe", "ar" to "Árabe",
        "hin" to "Hindi", "hi" to "Hindi",
        "dut" to "Neerlandés", "nld" to "Neerlandés", "nl" to "Neerlandés",
        "pol" to "Polaco", "pl" to "Polaco",
        "tur" to "Turco", "tr" to "Turco",
        "swe" to "Sueco", "sv" to "Sueco",
        "cat" to "Catalán", "ca" to "Catalán",
    )

    /** Nombre del idioma a partir de su código ISO (2 o 3 letras); null si no se conoce. */
    fun languageName(code: String): String? = LANGUAGES[code.trim().lowercase()]

    /**
     * Etiqueta para mostrar. Usa el idioma y, si el nombre de la pista aporta algo (Latino, Castellano,
     * «Comentarios», un título propio…), lo añade. [index] sirve de último recurso («Pista 2»).
     */
    fun label(track: Track, index: Int): String {
        val language = languageName(track.language)
        val name = track.name.trim()
        val lower = name.lowercase()

        // Variantes del español que los archivos suelen indicar en el nombre.
        if (language == "Español") {
            when {
                "latino" in lower || Regex("""\blat\b""").containsMatchIn(lower) -> return "Español (Latino)"
                "castellano" in lower || "españa" in lower || "spain" in lower -> return "Español (España)"
            }
        }
        if (name.isEmpty()) return language ?: "Pista ${index + 1}"
        if (language == null) return name
        // El nombre es solo el idioma repetido («Español», «Spanish», «spa»): no se duplica.
        val nameLanguage = languageName(name) ?: LANGUAGE_BY_ENGLISH_NAME[lower]
        return if (nameLanguage == language) language else "$language · $name"
    }

    private val LANGUAGE_BY_ENGLISH_NAME = mapOf(
        "english" to "Inglés", "spanish" to "Español", "french" to "Francés", "portuguese" to "Portugués",
        "italian" to "Italiano", "german" to "Alemán", "japanese" to "Japonés", "korean" to "Coreano",
        "chinese" to "Chino", "russian" to "Ruso", "français" to "Francés", "español" to "Español",
        "deutsch" to "Alemán", "italiano" to "Italiano", "português" to "Portugués", "日本語" to "Japonés",
    )
}
