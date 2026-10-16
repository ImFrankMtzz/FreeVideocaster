# Hoja de ruta de FreeVideocaster

Esfuerzo: **P** pequeño, **M** mediano, **G** grande. Estado a 2026-10-16.

## Hecho (versión 0.2)

| Mejora | Estado |
|---|---|
| Títulos limpios a partir de nombres de release («Serie.S01E03.1080p…» → «Serie S01E03») | Hecho y probado |
| Retomar donde se quedó (guarda posición por enlace de Stremio) | Hecho y probado |
| Diagnóstico de conexión con soluciones en español e informe copiable | Hecho y probado |
| Notificación con controles y sesión multimedia | Hecha y probada (botones confirmados en el teléfono) |
| Canal v2: estado en vivo en el puerto 8061 (posición, cola, error) | Hecho y probado en el Roku |
| Cola real (leída del canal) con quitar elemento | Hecho y probado |

## Hecho en el rediseño (versión 0.3)

| Mejora | Estado |
|---|---|
| Inicio con tarjeta de conexión, «Seguir viendo» y guía «Cómo empezar» con video de ejemplo | Hecho y probado (el botón de ejemplo, no) |
| «Conectar a tu TV»: hoja con búsqueda, IP manual, compatibilidad y diagnóstico | Hecho y probado |
| Aviso claro al pegar un magnet o una página web | Hecho y probado |
| Mini-reproductor fijo sobre las pestañas | Hecho y probado |
| Control remoto con cruceta, balancín de volumen, saltos circulares y pausa grande | Hecho y probado (efecto del volumen en el TV sin verificar) |
| Elegir pista de audio y de subtítulos incrustados | Hecho y probado con un video HLS; con MKV real se probó después (ver 1.1.0 y `pruebas-subtitulos.md`) |
| Las órdenes al Roku funcionan con una VPN activa | Hecho y probado |
| Navegador: seleccionar todo al tocar la barra, cerrar el teclado, títulos limpios | Hecho y probado |

## Hecho en la versión 0.4 y publicado como 1.0.0 (ver `pruebas-v0.4.md`)

| Mejora | Estado |
|---|---|
| Ajustes: tema Sistema/Claro/Oscuro, negro AMOLED, colores, buscador | Hecho y probado |
| Google como buscador y página de inicio del navegador | Hecho y probado |
| Bloqueador de anuncios propio (EasyList/EasyPrivacy/Spanish) con contador y interruptor | Hecho y probado en el teléfono y con uso real; falta medir cuántos anuncios deja pasar por sitio |
| Pantalla «Reproduciendo» tras enviar desde Stremio, con título real del archivo | Hecho y probado |
| Enviar automáticamente y volver a Stremio solo (ajustes, desactivados de fábrica) | Hecho y probado |

## Hecho en la versión 1.1.0 (Release publicada el 2026-10-14; ver `pruebas-subtitulos.md`)

| Mejora | Estado |
|---|---|
| Subtítulos incrustados en MKV: empiezan apagados y se dibujan al elegirlos (cambio de audio en silencio) | Hecho y probado; sin probar con un solo audio ni en pausa |
| Subtítulos en línea (OpenSubtitles v3 + Cinemeta): hasta 2 versiones en español y 2 en inglés, con interruptor en Ajustes | Hecho y probado con una serie; falta probar películas |
| Nombres de pista limpios (sin direcciones de sitios) | Hecho y probado |

## Hecho en la versión 1.1.1 (Release publicada el 2026-10-16)

| Mejora | Estado |
|---|---|
| La app ya no se cierra al abrir «Cambiar de TV» mientras el Wi-Fi se reconecta (la búsqueda del Roku avisa en vez de fallar) | Hecho; el usuario comprobó que se muestra el aviso. Un cierre real quedó registrado en el teléfono (`ENETUNREACH`) |

## Hecho en la versión 1.1.2 (Release publicada el 2026-10-16)

| Mejora | Estado |
|---|---|
| Ocultar la clave de TorBox en «Ver detalles técnicos» y en el botón Copiar (`media/UrlRedactor`: tramos en base64 de la ruta, `token=`/`key=`… de la consulta y usuario:clave del enlace) | Hecho con 8 pruebas unitarias; falta probarlo en el teléfono con un envío real desde Stremio |

## Siguiente bloque recomendado

| Mejora | Detalle | Esfuerzo |
|---|---|---|
| **Asistente de primer uso** | Permisos, buscar Roku, estado del canal y botón **«Enviar video de prueba»**; después se oculta la tarjeta «Desde Stremio». | M |
| Segundos de salto configurables y más ajustes | Ver «Ajustes pequeños»; ya existe la hoja de Ajustes donde añadirlos. | P |

## Decisión de alcance (2026-10-14)

Por ahora **solo Roku**. Otros receptores (DLNA/UPnP y Kodi como primeros candidatos; Chromecast y Fire TV más
costosos) y otros modelos de Android se añaden cuando la app esté pulida y antes del lanzamiento público. Requiere
primero una interfaz común de «receptor» (hoy `Caster` llama directo a `EcpClient`/`ChannelClient`) y poder probar
con equipos reales de cada tipo. No mostrar tipos de dispositivo que no funcionen.

## Ajustes pequeños

| Mejora | Esfuerzo |
|---|---|
| Segundos de salto configurables (10, 15, 30) | P |
| Mantener el Wi-Fi activo durante la reproducción (para que la notificación responda con pantalla apagada) | P |
| Mantener pulsado −/+ para avanzar o retroceder; vibración al tocar | P |
| Probar con letra grande; pasar a dos filas si los botones no caben | P |
| Aviso fijo de problema en la tarjeta de estado, con botón de diagnóstico | P |
| Exportar e importar historial y ajustes en un archivo | P |
| Pantalla «Privacidad» (qué se guarda y que nada sale del teléfono) | P |
| Informe de errores copiable si la app falla | P |

## Subtítulos y audio

Hecho (2026-10-14, ver `pruebas-subtitulos.md`): los subtítulos incrustados empiezan apagados y, al elegirlos, se
dibujan de verdad en MKV (el canal cambia de audio y vuelve, en silencio). Un MKV con una sola pista de audio no
puede usar ese truco: **sin probar**. **Subtítulos en línea (OpenSubtitles v3 + Cinemeta):** hecho y probado con una
serie; hasta 2 versiones en español y 2 en inglés, listadas como «En línea N»; interruptor en Ajustes.

| Mejora | Esfuerzo |
|---|---|
| Subtítulos por URL pegada y archivo del teléfono (la búsqueda en línea ya está), más estilo | G |
| Mostrar qué obra encontró la búsqueda y poder corregirla; elegir otros idiomas | M |
| Idioma preferido (p. ej. audio en español latino) aplicado solo al empezar cada video | M |

## Funciones de referencia aún sin cubrir (ver `revision-wvc.md`)

| Mejora | Esfuerzo |
|---|---|
| Pausar al entrar una llamada y reanudar al colgar (requiere permiso de teléfono; opcional) | M |
| Reproducir en el teléfono / abrir con otra app | M |
| Archivos del teléfono (mini servidor dentro de la app) | G |
| IPTV con listas M3U | M |
| Proxy desde el teléfono (solo si algún enlace queda atado a la IP) | G |

## Ideas nuevas

| Mejora | Notas | Esfuerzo |
|---|---|---|
| Cruceta y teclas del Roku (flechas, OK, Info, repetir 10 s) | Para manejar menús sin el control. | M |
| Encender el TV al enviar | Si responde a `PowerOn`; **verificar** en el KD28X. | P |
| Cola reordenable | Arrastrar para cambiar el orden. | M |
| Mosaico de ajustes rápidos y accesos directos del ícono | «Control», «Diagnóstico», «Último video». | M |
| Widget de inicio | Pausa, saltos y volumen. | M |
| Textos en recursos e inglés | Hoy están dentro del código, en español. | M |

## Lado Roku

| Mejora | Notas | Esfuerzo |
|---|---|---|
| Pantalla de reproducción propia | Título, progreso y cola visibles en la TV. | M |
| Pantalla en reposo útil | IP, últimos videos y cola. | P |
| **Quitar el modo desarrollador** | Es la mayor fricción para otras personas. Roku permite canales «no certificados» con enlace de instalación; **verificar sus límites** antes de decidir. | G |

## Proyecto abierto

| Mejora | Esfuerzo |
|---|---|
| CI en GitHub: compilar, probar, Lint y publicar el APK firmado en cada versión | M |
| Capturas y guía de instalación ilustrada en el README | P |

## Decisiones técnicas que condicionan lo anterior

1. **El canal informa a la app** (v2, puerto 8061, solo lectura). Es la base de las pistas de audio, la cola real, el
   progreso y los errores con motivo. El JSON no incluye la URL del video.
2. **Retomar se indexa por el enlace original de Stremio** (antes de las redirecciones), no por el título, porque
   el enlace final de TorBox cambia en cada sesión.
3. **El servicio de notificación usa el tipo `connectedDevice`** y se apaga solo (~15 s tras terminar). Las acciones
   de la notificación y la sesión multimedia envían las mismas órdenes que la pantalla de control.
