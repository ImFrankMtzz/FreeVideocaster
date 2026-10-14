# Referencia: Web Video Caster 6.0.3

Nota de trabajo del 2026-10-13, hecha usando la app en un Pixel 8 Pro (Android 17) y un Roku TV. Sirve como referencia
de funciones y de decisiones de diseño para FreeVideocaster. Describe lo observado en esa versión concreta, que puede
haber cambiado desde entonces. No incluye datos personales de quien la probó. Web Video Caster es una marca de sus
respectivos autores y este proyecto no tiene relación con ellos.

## Qué ofrece

| Área | Contenido |
|---|---|
| **Menú lateral** | Navegador, archivos del teléfono, IPTV, contenido reciente, listas de reproducción, favoritos, historial del navegador, más visitados, descargas, preguntas frecuentes, acerca de, modo oscuro, copia de seguridad y restauración, ajustes, salir, «borrar todos los datos y salir». |
| **Navegador** | Barra de dirección, pestañas, favoritos, más visitados, bloqueo de pop-ups, ícono amarillo cuando detecta un video. |
| **Al recibir un enlace** | Hoja con seis opciones: conectar, descargar, añadir a lista, abrir con…, reproducir en la app, más opciones. |
| **Selector de dispositivo** | Rokus encontrados, receptor web, receptor para Smart TV, «agregar IP de Roku», «reiniciar Wi-Fi», soporte. |
| **Control remoto** | Barra de tiempo, anterior/siguiente, saltos, repetir, pausa y parar; **subtítulos**, **pistas de audio**, audio en el teléfono y volumen; mini barra de control en las demás pantallas. |
| **Subtítulos** | OpenSubtitles, archivo local, URL, incrustados, descargados antes y estilo. |
| **Ajustes de transmisión** | Segundos de salto configurables, pausar en llamadas, retomar donde quedó, usar el teléfono como proxy, no enviar anuncios de video, convertir M3U8 a TS, resolver redirecciones, abrir el control automáticamente, gestos. |
| **Ajustes de detección** | Borrar la lista al cambiar de página, detección avanzada, ocultar M3U8 sin audio, verificar transmisiones en vivo. |
| **Otros** | IPTV, scrobble, descargas, listas de reproducción, wake-lock y proxy para M3U8. |

**Cómo llega al Roku:** instala en el televisor su propio canal («Web Video Caster – Receiver», de la tienda de
Roku). Es el mismo enfoque que usa FreeVideocaster, con la diferencia de que el nuestro se instala en modo desarrollador.

## Qué hacemos distinto

Lo siguiente se observó en la versión 6.0.3 y es lo que orientó las decisiones de diseño de FreeVideocaster:

1. **Sin anuncios.** En esa versión se vio publicidad en varias pantallas (un banner fijo, anuncios entre los
   recientes y un video al conectar). FreeVideocaster no incluye anuncios ni telemetría.
2. **Enviar sin interrupciones.** En una prueba, una ventana de venta apareció después de elegir el video y, al
   cerrarla, el video no se había enviado. Aquí el envío nunca depende de una ventana intermedia.
3. **Menos pasos.** El camino observado fue: enlace → hoja → conectar → elegir Roku → anuncio → ventana → volver →
   tocar el ícono de detección → tocar el video. En FreeVideocaster son uno o dos toques.
4. **Pantalla de control más despejada.** Allí un carrusel de consejos ocupaba cerca de un tercio de la pantalla.
   Aquí el control muestra solo lo necesario, con el mini-reproductor siempre visible.
5. **Un solo idioma.** Se vieron menús en español junto a textos en inglés («Subtitles from URL», «Phone Audio
   Options»). Aquí toda la interfaz está en español.
6. **Acciones destructivas fuera del paso.** El menú principal tenía «Borrar todos los datos y salir» junto a
   «Salir». Aquí no hay un botón así a la mano.
7. **Contenido reciente legible.** Se vieron miniaturas negras y títulos como «n» o «playback». Aquí los nombres de
   release se convierten en títulos y etiquetas legibles.
8. **Diagnóstico que explica.** «Solucionar problemas» ofrecía enlaces y reiniciar el Wi-Fi. Aquí el diagnóstico
   indica la causa y qué hacer en cada caso.

## Qué tomamos como idea y qué no

- **Funciones que adoptamos (la idea, no el diseño):** retomar donde quedó, cola, subtítulos de varias fuentes,
  pistas de audio, control con barra de tiempo y notificación con controles.
- **Lo que decidimos no hacer:** anuncios, ventanas de venta, carruseles de consejos, menú lateral recargado y un
  botón de borrar datos a la mano.
- **Nuestro enfoque:** «mandar y controlar» en vez de «navegar con botón de enviar», navegación inferior en vez de
  menú lateral y un diagnóstico que explica cada fallo.
