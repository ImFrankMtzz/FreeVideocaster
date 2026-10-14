package io.github.freevideocaster.adblock

/** Tipos de petición que distinguen las reglas (`$script`, `$image`…). Son banderas que se combinan con `or`. */
object RequestType {
    const val DOCUMENT = 1
    const val SCRIPT = 2
    const val IMAGE = 4
    const val STYLESHEET = 8
    const val FONT = 16
    const val MEDIA = 32
    const val XHR = 64
    const val SUBDOCUMENT = 128
    const val OTHER = 256
    const val POPUP = 512

    /** Lo que una regla sin opciones de tipo cubre: todo menos la página en sí y las ventanas emergentes. */
    const val ANY_SUBRESOURCE = SCRIPT or IMAGE or STYLESHEET or FONT or MEDIA or XHR or SUBDOCUMENT or OTHER

    /** Petición sin extensión reconocible: puede ser un script, un XHR, un iframe… */
    const val UNKNOWN = SCRIPT or XHR or SUBDOCUMENT or OTHER or MEDIA or FONT

    /** WebView no dice qué tipo de recurso es cada petición; se deduce de la extensión y de la cabecera Accept. */
    fun guess(url: String, isMainFrame: Boolean, accept: String?): Int {
        if (isMainFrame) return DOCUMENT
        val path = url.substringBefore('#').substringBefore('?').lowercase()
        val ext = path.substringAfterLast('/', "").substringAfterLast('.', "")
        return when {
            ext in IMAGE_EXT -> IMAGE
            ext == "js" || ext == "mjs" -> SCRIPT
            ext == "css" -> STYLESHEET
            ext in FONT_EXT -> FONT
            ext in MEDIA_EXT -> MEDIA
            ext == "html" || ext == "htm" -> SUBDOCUMENT
            accept != null && "image/" in accept && "text/html" !in accept -> IMAGE
            accept != null && "text/css" in accept -> STYLESHEET
            accept != null && "text/html" in accept -> SUBDOCUMENT
            else -> UNKNOWN
        }
    }

    private val IMAGE_EXT = setOf("png", "jpg", "jpeg", "gif", "webp", "svg", "ico", "bmp", "avif")
    private val FONT_EXT = setOf("woff", "woff2", "ttf", "otf", "eot")
    private val MEDIA_EXT = setOf("mp4", "webm", "m3u8", "mpd", "mp3", "ogg", "m4s", "ts", "mkv")
}

/**
 * Motor de bloqueo de anuncios que lee listas con la sintaxis de Adblock Plus (EasyList, EasyPrivacy…).
 *
 * Cubre lo que importa para un navegador pequeño: reglas por dominio (`||host^`), patrones con `*` y `^`,
 * opciones de tipo, `third-party` y `domain=`, excepciones (`@@`) y ocultar elementos (`##`, `#@#`).
 * Lo que no entiende (regex, `$redirect`, `$csp`, selectores extendidos…) se descarta sin afectar al resto.
 *
 * Una vez construido solo se lee, así que es seguro usarlo desde varios hilos.
 */
class FilterEngine private constructor() {

    private class Rule(
        val pureHost: Boolean,
        /** Subcadena que debe aparecer en la dirección (en minúsculas): descarta casi todo sin usar regex. */
        val literal: String,
        private val regexSource: String,
        val types: Int,
        val excludedTypes: Int,
        /** 0 = da igual, 1 = solo de terceros, -1 = solo del propio sitio. */
        val party: Int,
        val includeDomains: List<String>,
        val excludeDomains: List<String>,
        val important: Boolean,
    ) {
        val regex: Regex by lazy(LazyThreadSafetyMode.PUBLICATION) { Regex(regexSource) }
    }

    private val blockByHost = HashMap<String, MutableList<Rule>>()
    private val blockGeneral = ArrayList<Rule>()
    private val allowByHost = HashMap<String, MutableList<Rule>>()
    private val allowGeneral = ArrayList<Rule>()

    private val pageExempt = HashSet<String>()
    private val elemhideExempt = HashSet<String>()
    private val genericHideExempt = HashSet<String>()

    private val genericHide = LinkedHashSet<String>()
    private val hideByDomain = HashMap<String, MutableList<String>>()
    private val unhideByDomain = HashMap<String, MutableList<String>>()

    /** Reglas de red aceptadas (para mostrar en ajustes y en las pruebas). */
    var networkRuleCount = 0
        private set

    val cosmeticRuleCount get() = genericHide.size + hideByDomain.values.sumOf { it.size }

    private val genericCss: String by lazy(LazyThreadSafetyMode.PUBLICATION) { cssFor(genericHide) }

    // --- Consultas ---------------------------------------------------------------------------

    /**
     * true si hay que bloquear [url]. [pageUrl] es la página que la pide (para `third-party` y `domain=`) y
     * [type] el tipo de recurso (ver [RequestType]); con [RequestType.POPUP] se pregunta por una navegación
     * que no pidió la persona.
     */
    fun shouldBlock(url: String, pageUrl: String, type: Int): Boolean {
        val lower = url.lowercase()
        val host = hostOf(lower) ?: return false
        val pageHost = hostOf(pageUrl.lowercase()).orEmpty()
        if (pageHost.isNotEmpty() && isPageExempt(pageHost)) return false

        val thirdParty = pageHost.isNotEmpty() && baseDomain(host) != baseDomain(pageHost)
        val blocking = firstMatch(blockByHost, blockGeneral, lower, host, pageHost, type, thirdParty) ?: return false
        if (blocking.important) return true
        return firstMatch(allowByHost, allowGeneral, lower, host, pageHost, type, thirdParty) == null
    }

    /** La página tiene una excepción `@@||sitio^$document`: no se bloquea nada en ella. */
    fun isPageExempt(pageHost: String): Boolean = hostChain(pageHost).any { it in pageExempt }

    /** CSS que oculta los recuadros de anuncios de la página de [pageHost] (vacío si no hay nada que ocultar). */
    fun cosmeticCss(pageHost: String): String {
        val chain = hostChain(pageHost)
        if (chain.any { it in elemhideExempt || it in pageExempt }) return ""
        val unhide = chain.flatMap { unhideByDomain[it].orEmpty() }.toSet()
        val specific = chain.flatMap { hideByDomain[it].orEmpty() }.filter { it !in unhide }
        val generic = when {
            chain.any { it in genericHideExempt } -> ""
            unhide.isEmpty() -> genericCss
            else -> cssFor(genericHide.filter { it !in unhide })
        }
        return generic + cssFor(specific)
    }

    private fun firstMatch(
        byHost: Map<String, List<Rule>>,
        general: List<Rule>,
        url: String,
        host: String,
        pageHost: String,
        type: Int,
        thirdParty: Boolean,
    ): Rule? {
        for (key in hostChain(host)) {
            val rules = byHost[key] ?: continue
            for (rule in rules) if (matches(rule, url, pageHost, type, thirdParty)) return rule
        }
        for (rule in general) if (matches(rule, url, pageHost, type, thirdParty)) return rule
        return null
    }

    private fun matches(rule: Rule, url: String, pageHost: String, type: Int, thirdParty: Boolean): Boolean {
        if (!typeMatches(rule, type)) return false
        if (rule.party == 1 && !thirdParty) return false
        if (rule.party == -1 && thirdParty) return false
        if (rule.includeDomains.isNotEmpty() && rule.includeDomains.none { inDomain(pageHost, it) }) return false
        if (rule.excludeDomains.any { inDomain(pageHost, it) }) return false
        if (rule.pureHost) return true
        if (rule.literal.isNotEmpty() && rule.literal !in url) return false
        return rule.regex.containsMatchIn(url)
    }

    private fun typeMatches(rule: Rule, type: Int): Boolean {
        if (type == RequestType.POPUP) {
            // Una regla de dominio sin opciones también delata una redirección a un anunciante.
            return rule.types and RequestType.POPUP != 0 ||
                (rule.types == 0 && rule.excludedTypes == 0 && rule.pureHost)
        }
        val covered = (if (rule.types != 0) rule.types else RequestType.ANY_SUBRESOURCE) and rule.excludedTypes.inv()
        return covered and type != 0
    }

    // --- Lectura de listas -------------------------------------------------------------------

    private fun addLine(raw: String) {
        val line = raw.trim()
        if (line.isEmpty() || line[0] == '!' || line[0] == '[') return
        val cosmetic = COSMETIC.matchEntire(line)
        if (cosmetic != null) {
            addCosmetic(cosmetic.groupValues[1], cosmetic.groupValues[2], cosmetic.groupValues[3])
            return
        }
        addNetwork(line)
    }

    private fun addCosmetic(domains: String, marker: String, selector: String) {
        if (marker != "##" && marker != "#@#") return // #?#, #$#, #%#: sintaxis extendida de otros bloqueadores
        if (selector.isBlank() || UNSUPPORTED_SELECTOR.containsMatchIn(selector)) return
        if ('{' in selector || '}' in selector) return
        val list = if (domains.isBlank()) emptyList() else domains.lowercase().split(',')
        if (list.any { it.startsWith("~") || it.isBlank() }) return // exclusiones por dominio: no se admiten
        if (marker == "##") {
            if (list.isEmpty()) genericHide += selector else list.forEach { hideByDomain.getOrPut(it) { ArrayList() } += selector }
        } else {
            list.forEach { unhideByDomain.getOrPut(it) { ArrayList() } += selector }
        }
    }

    private fun addNetwork(original: String) {
        var line = original
        val exception = line.startsWith("@@")
        if (exception) line = line.substring(2)

        // Opciones tras el último «$» (siempre que lo que sigue parezca una lista de opciones).
        var optionText = ""
        val dollar = line.lastIndexOf('$')
        if (dollar >= 0 && OPTIONS.matches(line.substring(dollar + 1))) {
            optionText = line.substring(dollar + 1)
            line = line.substring(0, dollar)
        }
        if (line.startsWith("/") && line.endsWith("/") && line.length > 1) return // regex: no se admiten

        var types = 0
        var excluded = 0
        var party = 0
        var important = false
        var elemhide = false
        var genericHideFlag = false
        val include = ArrayList<String>()
        val exclude = ArrayList<String>()
        if (optionText.isNotEmpty()) {
            for (option in optionText.lowercase().split(',')) {
                val negated = option.startsWith("~")
                val name = option.removePrefix("~").substringBefore('=')
                when (name) {
                    "third-party", "3p" -> party = if (negated) -1 else 1
                    "first-party", "1p" -> party = if (negated) 1 else -1
                    "important" -> important = true
                    "match-case", "collapse" -> Unit
                    "domain" -> option.substringAfter('=').split('|').filter { it.isNotBlank() }.forEach {
                        if (it.startsWith("~")) exclude += it.substring(1) else include += it
                    }
                    "elemhide" -> elemhide = true
                    "generichide" -> genericHideFlag = true
                    "badfilter" -> return
                    else -> {
                        val flag = typeFlag(name) ?: return // opción desconocida ($redirect, $csp…): se descarta la regla
                        if (negated) excluded = excluded or flag else types = types or flag
                    }
                }
            }
        }

        var body = line.lowercase()

        // Excepciones que apagan funciones en una página entera.
        if (exception && (elemhide || genericHideFlag || types and RequestType.DOCUMENT != 0)) {
            val host = pageHostOf(body) ?: return
            if (elemhide) elemhideExempt += host
            if (genericHideFlag) genericHideExempt += host
            if (types and RequestType.DOCUMENT != 0) pageExempt += host
            return
        }
        if (elemhide || genericHideFlag) return
        // Reglas que bloquean la página principal: se ignoran (nunca se rompe la navegación normal).
        if (!exception && types and RequestType.DOCUMENT != 0 && types and RequestType.POPUP == 0) return
        types = types and RequestType.DOCUMENT.inv()

        var host: String? = null
        var pureHost = false
        var prefix = ""
        if (body.startsWith("||")) {
            val rest = body.substring(2)
            var end = rest.indexOfFirst { it in "/^*?:|" }
            if (end < 0) end = rest.length
            val candidate = rest.substring(0, end)
            val next = rest.getOrNull(end)
            if (candidate.isNotEmpty() && candidate.all { it.isLetterOrDigit() || it == '.' || it == '-' || it == '_' } &&
                (next == null || next == '^' || next == '/' || next == '?')
            ) {
                host = candidate
                val remainder = rest.substring(end)
                pureHost = remainder.isEmpty() || remainder == "^"
                body = remainder
                prefix = "^(?:[a-z][a-z0-9+.-]*:)?//(?:[^/?#]*\\.)?" + Regex.escape(candidate)
            } else {
                prefix = "^(?:[a-z][a-z0-9+.-]*:)?//(?:[^/?#]*\\.)?"
                body = rest
            }
        } else if (body.startsWith("|")) {
            prefix = "^"
            body = body.substring(1)
        }

        val endAnchor = body.endsWith("|")
        if (endAnchor) body = body.dropLast(1)
        if (host == null) body = body.trim('*')

        val literal = LITERAL_SPLIT.split(body).maxByOrNull { it.length }.orEmpty()
        if (!pureHost && host == null && literal.length < MIN_LITERAL) return // demasiado genérica

        val regexSource = if (pureHost) "" else buildString {
            append(prefix)
            append(patternToRegex(body))
            if (endAnchor) append('$')
        }
        // La mayoría de las reglas son «bloquear todo este dominio»: comparten un solo objeto para ahorrar memoria.
        val plainDomain = pureHost && types == 0 && excluded == 0 && party == 0 &&
            include.isEmpty() && exclude.isEmpty() && !important
        val rule = if (plainDomain) PLAIN_DOMAIN
        else Rule(pureHost, literal, regexSource, types, excluded, party, include, exclude, important)
        val byHost = if (exception) allowByHost else blockByHost
        val general = if (exception) allowGeneral else blockGeneral
        if (host != null) {
            val list = byHost.getOrPut(host) { ArrayList(1) }
            if (plainDomain && list.any { it === PLAIN_DOMAIN }) return // ya estaba (listas repetidas)
            list += rule
        } else {
            general += rule
        }
        networkRuleCount++
    }

    private fun typeFlag(name: String): Int? = when (name) {
        "script" -> RequestType.SCRIPT
        "image" -> RequestType.IMAGE
        "stylesheet", "css" -> RequestType.STYLESHEET
        "font" -> RequestType.FONT
        "media" -> RequestType.MEDIA
        "xmlhttprequest", "xhr" -> RequestType.XHR
        "subdocument", "frame" -> RequestType.SUBDOCUMENT
        "document", "doc" -> RequestType.DOCUMENT
        "popup" -> RequestType.POPUP
        "other", "ping", "websocket", "object", "object-subrequest", "webrtc" -> RequestType.OTHER
        "all" -> RequestType.ANY_SUBRESOURCE or RequestType.POPUP
        else -> null
    }

    /** `||sitio.com^` → «sitio.com»; null si la regla no es de ese tipo. */
    private fun pageHostOf(body: String): String? {
        if (!body.startsWith("||")) return null
        val rest = body.substring(2)
        val end = rest.indexOfFirst { it in "/^*?:|" }.let { if (it < 0) rest.length else it }
        return rest.substring(0, end).takeIf { it.isNotEmpty() }
    }

    private fun patternToRegex(pattern: String): String = buildString {
        for (ch in pattern) {
            when (ch) {
                '*' -> append(".*")
                '^' -> append("(?:[^a-z0-9_.%-]|$)")
                else -> if (ch.isLetterOrDigit()) append(ch) else append('\\').append(ch)
            }
        }
    }

    private fun cssFor(selectors: Collection<String>): String {
        if (selectors.isEmpty()) return ""
        // Si un selector falla, el navegador descarta toda su regla: por eso se reparten en grupos pequeños.
        return selectors.chunked(CSS_GROUP).joinToString("") { it.joinToString(",") + "{display:none!important}\n" }
    }

    companion object {
        private val PLAIN_DOMAIN = Rule(true, "", "", 0, 0, 0, emptyList(), emptyList(), false)
        private const val MIN_LITERAL = 3
        private const val CSS_GROUP = 25

        private val COSMETIC = Regex("""^([^/*|@"!]*?)(##|#@#|#\?#|#\$#|#%#|#@\$#|#@\?#)(.+)$""")
        private val OPTIONS = Regex("""^~?[a-z0-9_-]+(=[^,]*)?(,~?[a-z0-9_-]+(=[^,]*)?)*$""", RegexOption.IGNORE_CASE)
        private val LITERAL_SPLIT = Regex("""[*^|]""")
        private val UNSUPPORTED_SELECTOR = Regex(
            """:(-abp-[a-z-]+|-ext-[a-z-]+|has-text|xpath|contains|matches-|upward|remove|style|nth-ancestor|min-text|watch-attr|others|if|if-not)\(|^\+js""",
        )

        fun parse(vararg lists: String): FilterEngine = parse(lists.asList())

        fun parse(lists: List<String>): FilterEngine {
            val engine = FilterEngine()
            for (text in lists) text.lineSequence().forEach { engine.addLine(it) }
            return engine
        }

        /** Dirección → servidor en minúsculas («https://a.b.com:8080/x» → «a.b.com»). */
        fun hostOf(url: String): String? {
            val afterScheme = url.substringAfter("://", "")
            if (afterScheme.isEmpty()) return null
            val authority = afterScheme.substringBefore('/').substringBefore('?').substringBefore('#')
            val host = authority.substringAfterLast('@').substringBefore(':')
            return host.takeIf { it.isNotEmpty() }
        }

        /** «a.b.example.com» → [«a.b.example.com», «b.example.com», «example.com»]. */
        internal fun hostChain(host: String): List<String> {
            val chain = ArrayList<String>(4)
            var current = host
            while (true) {
                chain += current
                val dot = current.indexOf('.')
                if (dot < 0 || current.indexOf('.', dot + 1) < 0) break
                current = current.substring(dot + 1)
            }
            return chain
        }

        private fun inDomain(host: String, domain: String) = host == domain || host.endsWith(".$domain")

        private val SECOND_LEVEL = setOf("co", "com", "org", "net", "gov", "edu", "ac", "or", "ne", "go")

        /** Dominio "registrable" aproximado: «cdn.sitio.com» → «sitio.com»; «a.sitio.co.uk» → «sitio.co.uk». */
        fun baseDomain(host: String): String {
            val labels = host.split('.')
            if (labels.size <= 2) return host
            val keep = if (labels.last().length == 2 && labels[labels.size - 2] in SECOND_LEVEL) 3 else 2
            return labels.takeLast(keep).joinToString(".")
        }
    }
}
