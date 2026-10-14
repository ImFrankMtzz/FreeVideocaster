# Registro de pruebas de la versión 0.2

Probado el 2026-10-13 en un Pixel 8 Pro (Android 17, API 37) y un Roku TV KD28X (Roku OS 15.3.4, 720p).
La parte automática: 33 pruebas unitarias sin fallos y Lint sin avisos.

## Verificado en los equipos reales

| Prueba | Resultado |
|---|---|
| Instalación y apertura en Android 17; permisos de red local y de notificaciones | Correcto |
| Aviso de notificaciones: «Activar» → diálogo del sistema → permiso concedido → el aviso se oculta | Correcto |
| Envío con título de release: el Roku recibe el título limpio; la app muestra etiquetas (año, resolución, fuente, códec, audio) | Correcto |
| Retomar: al reenviar el mismo enlace ofrece «Continuar desde 3:38 de 10:34»; el Roku arranca en ese punto | Correcto |
| Progreso guardado al detener; barra «Viste hasta…» y botones Continuar / Inicio / Cola en el historial | Correcto |
| Títulos antiguos del historial limpiados al leerlos | Correcto |
| Diagnóstico: todas las comprobaciones en ✔ con el Roku bien configurado | Correcto |
| Diagnóstico con enlace caducado (HTTP 404): explica que expiró y qué hacer; el aviso al enviar ofrece «Diagnosticar» con el enlace pegado | Correcto |
| Cola real: elementos leídos del Roku, quitar uno, vaciar cola | Correcto |
| Control: −10 s, +10 s, +30 s, Play/Pausa, Detener | Correcto |
| Servicio de notificación: arranca al enviar, es de primer plano (`connectedDevice`) y se apaga solo ~19 s tras terminar; no deja notificación ni sesión | Correcto |
| Canal v2 en el Roku real: estado en reposo, reproduciendo, pausado, con cola y tras un error; `start` arranca en el punto pedido; ruta inexistente responde 404 | Correcto |
| Título con acentos y símbolos (ñ, í, «», caracteres CJK) por la vía normal y por trozos | Correcto |
| Enlace largo (572 caracteres) en cinco trozos | Correcto |

## Errores encontrados al probar (ya corregidos)

1. **Falso aviso «audio DTS»** con audio AAC: el detector buscaba «dts» dentro de «aac_a**dts**». Ahora compara palabras.
2. **El servicio no se apagaba**: el estado «sin reproducción» no cambia entre consultas y el vigilante solo se
   despertaba con cambios. Ahora tiene su propio reloj.
3. **Carrera de cola en el canal**: una orden «a la cola» que llegaba milisegundos después de «reproducir» reemplazaba
   el video. Ahora el canal se considera activo desde que se pide el video.
4. **URL sin ruta** devolvía el nombre del servidor como título.
5. Una llamada a una API de Android 12 sin protección (Lint).
6. Los nombres `.m3u8` y `.mpd` conservaban la extensión.

## Sin verificar

| Qué | Por qué |
|---|---|
| Botones de la notificación y de la pantalla de bloqueo | La herramienta de prueba no puede ver la barra de notificaciones en Android 17; probar a mano. |
| Teclas multimedia (`cmd media_session dispatch`) | Durante la prueba había una llamada en curso y la sesión de telefonía captura esas teclas. Nuestra sesión estaba activa, `PLAYING` y con todas las acciones. |
| Flujo real desde Stremio con la v0.2 | Probar a mano; pasar el texto de «Ver detalles técnicos» (muestra si Stremio manda subtítulos). |
| Navegador integrado | No se probó. |
| Atrás, Inicio y Volumen del control; volumen en el TV | No se probaron. |
| Menú Compartir (`ACTION_SEND`) | No se probó. |
| Búsqueda automática del Roku (SSDP) desde el teléfono y la IP manual, tras pasar la red a modo AP | No se probaron en esta sesión. |

## Cómo repetir lo que falta

1. Reinstala el canal con `scripts\deploy-roku.ps1` y la app con `out\FreeVideocaster-debug.apk`.
2. Envía un episodio desde Stremio (reproductor externo). Abre la barra de notificaciones y comprueba −10 s, Pausa,
   +10 s y Detener; luego bloquea la pantalla y repite.
3. Cierra el video a la mitad, vuelve a enviarlo y comprueba «Continuar desde…».
4. En «Ver detalles técnicos», copia el texto y pásalo para diseñar los subtítulos.
5. Prueba Atrás, Inicio, Vol −, Silencio y Vol + en la pestaña Control, y el navegador con una página de video.
