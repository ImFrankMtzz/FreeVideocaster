# Registro de pruebas de la versión 0.3 (rediseño)

Probado el 2026-10-14 en un Pixel 8 Pro (Android 17, API 37) y un Roku TV KD28X (Roku OS 15.3.4, 720p), con una
VPN activa en el teléfono durante buena parte de la sesión. La parte automática: 45 pruebas unitarias sin fallos y
Lint sin avisos.

## Verificado en los equipos reales

| Prueba | Resultado |
|---|---|
| Inicio: tarjeta de conexión («Conectado»), «Seguir viendo» con miniaturas y barra de avance, tarjeta de enlace | Correcto |
| Hoja «Conectar a tu TV»: búsqueda automática encontró los dos Roku de la red, marca el elegido, «Agregar por IP», compatibilidad | Correcto, también con VPN |
| «Enviar un enlace» con un magnet: aviso claro en español y botones desactivados; el campo queda sobre el teclado | Correcto |
| Pantalla de recepción (la de Stremio): miniatura, título limpio, etiquetas, conexión y botón principal | Correcto |
| Mini-reproductor sobre las pestañas: título, estado, pausa; tocarlo abre el control | Correcto |
| Control: saltos circulares (flechas en el sentido correcto), pausa grande, cruceta, Atrás, «Inicio Roku», balancín de volumen | Correcto en pantalla y con el Roku aceptando las órdenes |
| Selector de **subtítulos**: lista con idiomas, cambiar a otro, desactivar y volver; el botón muestra el idioma actual | Correcto, verificado en el estado del Roku |
| Selector de **audio**: dos pistas del mismo idioma distinguidas por nombre; cambio verificado en el Roku | Correcto |
| Historial: miniaturas, avance, Continuar / Inicio / Cola | Correcto |
| Diagnóstico: nota informativa cuando hay VPN | Correcto |
| Navegador: detecta el MP4, envía al Roku con título limpio; al tocar la barra selecciona todo el texto; la tecla de acción del teclado lo cierra | Correcto |
| Servicio de notificación: sus botones apuntan a las acciones correctas (verificado con `dumpsys activity intents`) | Correcto |

## Errores encontrados al probar (ya corregidos)

1. **Con una VPN activa la app mostraba «Sin respuesta»**: las conexiones salían por la VPN y no llegaban al Roku. Ahora
   las órdenes al Roku y la búsqueda (SSDP) se atan al Wi-Fi (`WifiTracker` + `WifiSocketFactory`); la comprobación de
   enlaces conserva la ruta normal.
2. Las flechas de los botones de salto estaban al revés.
3. El botón del control «Inicio» se llamaba igual que la pestaña «Inicio» (confuso para lectores de pantalla y
   pruebas): ahora es «Inicio Roku».
4. En el navegador, al tocar la barra el texto anterior quedaba y lo nuevo se insertaba en el medio.
5. Un video enviado desde el navegador se titulaba con su propia dirección («test-videos co uk/vids/…»): las
   direcciones ya cuentan como título inútil y se usa el nombre del archivo.

## Sin verificar

| Qué | Por qué |
|---|---|
| Botones de la notificación y de la pantalla de bloqueo, pulsados de verdad | La herramienta de prueba no ve la barra de notificaciones en Android 17. |
| Teclas multimedia físicas (audífonos) | El sistema las reserva para otra app (YouTube); no afecta a la notificación. |
| Que el volumen del TV cambie al usar el balancín, y el efecto de las flechas de la cruceta en los menús | Se comprobó que el Roku acepta las órdenes, no el resultado en pantalla. |
| Audio y subtítulos con un **MKV real de TorBox** (varias pistas incrustadas) | Se probó con un video HLS de prueba. En MKV los nombres de las pistas pueden venir distintos. **Actualización:** ya se probó con MKV reales de TorBox el 2026-10-14; ver `pruebas-subtitulos.md`. |
| Botón «Probar con un video de ejemplo» | Solo aparece sin historial; no se vació el tuyo para probarlo. |
| Flujo real desde Stremio con la v0.3 | Probar a mano. |
