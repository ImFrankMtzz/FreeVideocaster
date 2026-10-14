# Registro de pruebas de la versión 0.4 (ajustes, bloqueador de anuncios, pantalla «Reproduciendo»)

Probado el 2026-10-14 en un Pixel 8 Pro (Android 17, API 37) y un Roku TV KD28X (Roku OS 15.3.4), con el teléfono
por USB. Automático: 73 pruebas unitarias sin fallos (19 del motor de filtros, 5 contra las listas reales) y Lint limpio.

## Confirmado por ti (uso real, 2026-10-14)

| Qué | Resultado |
|---|---|
| Botones de la notificación y de la pantalla de bloqueo | Funcionan |
| Volumen subir y bajar en el TV | Funciona |
| **Silenciar** | Funciona |
| **Flujo real desde Stremio** con la v0.4 | Funciona |
| Navegador con el bloqueador reproduciendo un video en un sitio real | Funciona; pruebas satisfactorias desde el día anterior |

## Verificado en los equipos reales

| Prueba | Resultado |
|---|---|
| Ajustes: tema Oscuro, Claro, Sistema, negro AMOLED (fondo negro puro) | Correcto; cambia al instante y los íconos de la barra siguen visibles |
| Con los valores por defecto el Inicio se ve igual que antes | Correcto (solo se añade el engranaje) |
| Navegador abre Google; buscar desde la barra usa `google.com/search` | Correcto |
| Bloqueador con una página de prueba propia | Correcto: 5 de 5 scripts/píxeles de anunciantes cortados y jQuery (CDN legítimo) cargó; ambos recuadros de anuncio ocultos; ventana emergente sin toque bloqueada; `intent://` y salto automático a otro sitio bloqueados (con el bloqueo apagado el `intent://` rompe la página) |
| Ventanas con toque | `window.open` y `target=_blank` se abren en la misma vista; un `window.open` a un anunciante con toque se bloquea |
| Contador «Anuncios: N» | Correcto tras corregir un error (se reiniciaba tarde y perdía los primeros anuncios): 7 en la página de prueba |
| Interruptor del bloqueo (píldora «Anuncios») | Apaga, recarga y muestra «Bloqueo apagado»; la página de prueba cambia de comportamiento |
| Google (inicio y resultados) con el bloqueo encendido | Se ve y funciona bien (18 y 19 peticiones cortadas) |
| Ajustes → Actualizar listas | Correcto: «Listas actualizadas (3 de 3)» y fecha |
| Stremio simulado (`adb`, título «playback»): pantalla de recepción | Muestra el nombre real del archivo, ya no «Video sin título» |
| Tras enviar: pantalla «Reproduciendo» | Estado en vivo, avance, pausa, saltos de 10 s, botón grande «Abrir el control» (abre el Control) y «Volver a Stremio» |
| Envío automático + volver a la app anterior | Correcto: el Roku empezó en <1 s y la app volvió sola a ~1,8 s |
| Envío automático con un punto guardado | Correcto: «Continuando solo en 3 s…» y luego envía |
| «Probar con un video de ejemplo» (con historial vacío temporal) | Correcto: el Roku reproduce «Video de ejemplo» y aparece el mini-reproductor |
| Silenciar: la app envía la orden sin error | Correcto en la app (ver pendientes) |
| Cierres inesperados en el registro del sistema | Ninguno |

## Errores encontrados al probar (ya corregidos)

1. El contador de anuncios bloqueados se reiniciaba con `onPageStarted`, que llega tarde, y borraba los primeros
   anuncios ya contados. Ahora se reinicia al llegar la petición de la página principal.

## Sin verificar

| Qué | Por qué |
|---|---|
| Medir el bloqueador: cuántos anuncios quedan en cada sitio | El uso real en un sitio de video fue satisfactorio, pero no se midió. Si algún sitio se rompe, apagar el bloqueo con la píldora «Anuncios» y anotar cuál. |
| Colores «Verde FreeVideocaster» y cambio de buscador a DuckDuckGo/Bing | No se probaron en pantalla (el buscador sí está cubierto por pruebas unitarias). |
| Tema Claro con el teléfono en modo oscuro del sistema | El teléfono estaba en modo claro; la combinación inversa no se pudo ver. |
| Memoria con ~146 000 reglas cargadas | No se midió; no se notó lentitud ni cierres. |
| Añadir a la cola desde la pantalla nueva y el aviso de error de reproducción | No se probaron. |

## Observaciones

- Con el tema claro de la app, Google se mostró en oscuro dentro del navegador: lo decide la propia página.
- Una página pública de pruebas de bloqueadores (d3ward) está archivada y no mostró resultados, por eso se usó una página propia.

## Límites conocidos del bloqueador

- Solo actúa en el navegador de la app, no en Stremio ni en el Roku.
- No ve el tipo real de cada recurso (WebView no lo dice), lo deduce de la extensión y de la cabecera `Accept`.
- No entiende reglas de expresión regular ni opciones como `$redirect` o `$csp`, ni selectores extendidos
  (`:has-text()`, `:xpath()`); las descarta sin afectar al resto.
- Los anuncios dentro de un video (pre-roll) y los que se sirven desde el propio dominio de la página pasan.
- Las excepciones por dominio de «ocultar elementos» con `~dominio` no se admiten.
