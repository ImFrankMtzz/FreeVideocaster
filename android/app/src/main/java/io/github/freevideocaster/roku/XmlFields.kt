package io.github.freevideocaster.roku

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import java.io.StringReader

/**
 * Lectura mínima de las respuestas XML de ECP: el texto y los atributos de la
 * primera aparición de cada etiqueta. Las respuestas de Roku son planas, no hace falta más.
 */
internal class XmlFields(
    val text: Map<String, String>,
    val attrs: Map<String, Map<String, String>>,
) {
    companion object {
        fun parse(xml: String): XmlFields {
            val text = mutableMapOf<String, String>()
            val attrs = mutableMapOf<String, Map<String, String>>()
            try {
                val parser = Xml.newPullParser()
                parser.setInput(StringReader(xml))
                var current: String? = null
                while (true) {
                    when (parser.next()) {
                        XmlPullParser.START_TAG -> {
                            current = parser.name
                            if (parser.attributeCount > 0 && parser.name !in attrs) {
                                attrs[parser.name] = (0 until parser.attributeCount)
                                    .associate { parser.getAttributeName(it) to parser.getAttributeValue(it) }
                            }
                        }
                        XmlPullParser.TEXT -> {
                            val tag = current
                            val value = parser.text.trim()
                            if (tag != null && value.isNotEmpty() && tag !in text) text[tag] = value
                        }
                        XmlPullParser.END_TAG -> current = null
                        XmlPullParser.END_DOCUMENT -> break
                    }
                }
            } catch (e: XmlPullParserException) {
                throw CastException("El Roku devolvió una respuesta que no se pudo leer.")
            }
            return XmlFields(text, attrs)
        }

        /** Lista de <app id="…">Nombre</app> de /query/apps. */
        fun parseApps(xml: String): List<Pair<String, String>> {
            val apps = mutableListOf<Pair<String, String>>()
            try {
                val parser = Xml.newPullParser()
                parser.setInput(StringReader(xml))
                var id: String? = null
                while (true) {
                    when (parser.next()) {
                        XmlPullParser.START_TAG -> id = if (parser.name == "app") parser.getAttributeValue(null, "id") else null
                        XmlPullParser.TEXT -> id?.let { apps += it to parser.text.trim(); id = null }
                        XmlPullParser.END_TAG -> id = null
                        XmlPullParser.END_DOCUMENT -> break
                    }
                }
            } catch (e: XmlPullParserException) {
                throw CastException("El Roku devolvió una respuesta que no se pudo leer.")
            }
            return apps
        }
    }
}
