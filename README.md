# FreeVideocaster

Envía videos desde Android (Stremio + TorBox, enlaces directos) a un Roku.
Código abierto (GPL-3.0), sin anuncios y sin telemetría. La única consulta a servicios externos es la búsqueda
opcional de subtítulos (ver «Subtítulos en línea» abajo), que se puede apagar en Ajustes.

| Carpeta | Qué es |
|---|---|
| `roku-receiver/` | Canal receptor para el Roku (BrightScript). Se instala en modo desarrollador. |
| `android/` | App Android (Kotlin + Jetpack Compose + OkHttp). Ábrela con Android Studio. |
| `scripts/` | Utilidades PowerShell para Windows (pruebas por ECP e instalación del canal) y `fake-roku.py`, un Roku falso para probar la app en un emulador. |
| `out/` | Archivos generados: `FreeVideocaster-debug.apk` y el ZIP del canal. |
| `docs/` | Notas del proyecto: `revision-wvc.md` (funciones de referencia de otra app similar), `mejoras.md` (hoja de ruta), `compatibilidad-android.md` (versiones de Android probadas) y `pruebas-v0.2.md`, `pruebas-v0.3.md`, `pruebas-v0.4.md` y `pruebas-subtitulos.md` (qué se probó y qué no). |

> Para ejecutar los scripts: `powershell -ExecutionPolicy Bypass -File .\scripts\<script>.ps1 ...`
> (así no hace falta cambiar la política de ejecución del equipo).

## Cómo funciona

```
Stremio ──(reproductor externo)──▶ App Android ──(ECP, HTTP :8060)──▶ Canal FreeVideocaster en el Roku
                                                                              │
                                              TorBox (HTTPS) ◀────────────────┘  el Roku descarga el video directo
```

La app no transmite el video: solo le pasa el enlace al Roku. Después puedes apagar la pantalla del teléfono.

## Instalación

### 1. Roku
1. **Configuración → Sistema → Configuración avanzada del sistema → Control por aplicaciones móviles →
   Acceso a la red = Predeterminado** (pasa a "Permisivo" solo si "Predeterminado" bloquea los comandos).
2. **Modo desarrollador**: con el control, `Inicio ×3, Arriba ×2, Derecha, Izquierda, Derecha, Izquierda, Derecha`.
   Acepta, elige una contraseña y deja que reinicie. Comprueba que `http://IP_DEL_ROKU` en el navegador del
   PC muestra el *Development Application Installer*.
3. **Instala el canal**:
   ```powershell
   powershell -ExecutionPolicy Bypass -File .\scripts\deploy-roku.ps1 -RokuIp 192.168.1.50
   ```
   Se abre solo en el Roku y muestra "Esperando un enlace…" con su IP.

### 2. Android
**Requisitos:** Android 7.0 o superior (el envío está probado de Android 8 a 17; en 7.0 y 7.1 la app abre, pero el
envío no se ha probado; ver `docs/compatibilidad-android.md`), el
celular en la misma red Wi-Fi que el Roku, y un JDK 17+ con el SDK de Android si vas a compilarla tú.

Elige una opción:
- **Compilar el APK (sin Android Studio):** desde la raíz del repositorio,
  `android\gradlew.bat -p android assembleDebug`. El APK queda en `android\app\build\outputs\apk\debug\`
  (en Linux o macOS: `./android/gradlew -p android assembleDebug`). Cópialo al celular y ábrelo (Android pedirá
  permitir "instalar apps desconocidas"), o con el celular por USB: `adb install -r app-debug.apk`.
- **Con Android Studio:** *File → Open →* carpeta `android`, espera a que sincronice y pulsa ▶ *Run* con el
  celular conectado por USB (depuración USB activada).

Los APK que publica el autor van firmados con su clave privada, que no forma parte del repositorio. El que compiles
tú saldrá firmado con la clave de depuración (`assembleDebug`) o sin firmar (`assembleRelease`); funciona igual, pero
Android no lo tratará como una actualización de la versión publicada (otra firma).

Al abrir la app: concede el permiso de **notificaciones** (Android 13+) y el de **red local** (solo Android 17),
y pulsa **Buscar** para encontrar tu Roku.

### 3. Stremio
En los ajustes del reproductor de Stremio, activa el **reproductor externo** y elige FreeVideocaster
(o la opción de preguntar cada vez). Al reproducir un episodio se abre la pantalla "Enviar al Roku".

## Pruebas finales (en este orden)

Marca cada punto. Si algo falla, anota el mensaje exacto (la app y el canal muestran el motivo).

**A. Roku sin app** (opcional, pero da información útil)
- [ ] `test-ecp.ps1 -RokuIp IP -VideoUrl "https://...TorBox..."` → guarda el resumen final.
- [ ] `send-to-roku.ps1 -RokuIp IP -Url "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4" -Format mp4` → se reproduce.
- [ ] Lo mismo con un enlace real de TorBox (`-Format mkv`).
- [ ] Reinicia el Roku: el canal sigue instalado (aparece como "dev" en la pantalla de inicio).

**B. Fase 1: Stremio → Roku**
- [ ] La app aparece como reproductor externo en Stremio.
- [ ] "Reproducir ahora" → el Roku empieza en menos de ~5 s y la app dice "Reproduciendo en …".
- [ ] "Ver detalles técnicos" → muestra lo que manda Stremio. **No pegues ese texto en ningún chat ni reporte sin revisarlo:
  la dirección que manda Stremio lleva dentro tu clave de TorBox (codificada).**
- [ ] Un video con audio DTS/TrueHD: la app avisa del tipo de audio.
- [ ] Con el Roku apagado: error claro en pocos segundos.
- [ ] Sin Wi-Fi (solo datos móviles): aviso "Sin Wi-Fi".
- [ ] Negando el permiso de red local: aviso con botón para concederlo.

**C. Fase 2: descubrimiento**
- [ ] "Buscar Roku" lo encuentra en menos de ~3 s.
- [ ] "Usar esta IP" con la IP manual funciona.
- [ ] Cierra y abre la app: recuerda el Roku.
- [ ] (Si puedes) cambia la IP del Roku en el router: al enviar, la app lo vuelve a encontrar.

**D. Fase 3: navegador**
- [ ] Abre `https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4` → aparece en "Videos detectados" y "Enviar" lo reproduce.
- [ ] Abre `https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8` → se detecta como HLS y se reproduce.
- [ ] Una página real con video: dale play en la página y comprueba si se detecta.

**E. Fase 4: control remoto, cola e historial**
- [ ] Pestaña Control: Play/Pausa, −10/+10 s, −30/+30 s, la barra para saltar, Atrás e Inicio.
- [ ] Volumen (solo aparece si el Roku es un televisor).
- [ ] Envía 3 videos con "Añadir a la cola": se reproducen seguidos; "Siguiente" salta; "Vaciar cola" la limpia.
- [ ] Historial: sobrevive a cerrar la app; ▶ vuelve a enviar; la papelera borra.

**F. Novedades de la versión 0.2** (reinstala el canal con `deploy-roku.ps1` y la app nueva)
- [ ] Títulos limpios: un archivo tipo `Serie.S01E03.1080p.WEB-DL…` aparece como «Serie S01E03» con sus etiquetas.
- [ ] Retomar: ve un video más de 1 minuto, ciérralo y vuelve a enviarlo desde Stremio: ofrece «Continuar desde …».
- [ ] Historial: muestra la barra «Viste hasta…» y los botones Continuar / Inicio / Cola.
- [ ] Notificación: al enviar un video (con las notificaciones permitidas) aparece con −10 s, Pausa, +10 s y Detener,
      también en la pantalla de bloqueo; desaparece sola un rato después de terminar.
- [ ] Control: la cola muestra los elementos reales del Roku y la papelera quita uno.
- [ ] Diagnóstico (botón en «Enviar»): todo en ✔ con el Roku bien; apaga el Roku o el Wi-Fi y comprueba que explica qué falla.
- [ ] Un enlace caducado: el diagnóstico (y el aviso al enviar) dicen que expiró.

**G. Rediseño (versión 0.3)** (reinstala el canal con `deploy-roku.ps1` y la app nueva)
- [ ] Inicio: tarjeta de conexión («Conectado»), «Seguir viendo» con los últimos videos, y «Cómo empezar» si no hay historial.
- [ ] Tocar la tarjeta de conexión abre «Conectar a tu TV» (búsqueda, IP manual, compatibilidad).
- [ ] «Enviar un enlace» avisa con claridad si pegas un magnet o una página web.
- [ ] Barra de reproducción fija sobre las pestañas mientras algo suena; tocarla abre el control.
- [ ] Control: saltos de 10 y 30 s (mantener pulsado repite), pausa grande, cruceta con OK, Atrás, Inicio y volumen.
- [ ] Un video con varios idiomas: los botones «Audio» y «Subtítulos» abren la lista y cambian la pista de verdad.

## Detalles técnicos

### Parámetros que acepta el canal
Se envían a `POST http://ROKU:8060/launch/dev?...` (abre el canal) o `POST http://ROKU:8060/input?...` (canal abierto).

| Parámetro | Ejemplo / valores |
|---|---|
| `action` | `play` (por defecto), `queue`, `next`, `toggle`, `pause`, `resume`, `skip`, `seek`, `stop`, `clearqueue`, `dequeue`, `audio`, `subtitle` |
| `url` | URL del video |
| `title` | `Episodio 3` |
| `format` | `mp4`, `mkv`, `hls`, `dash` (si falta, se deduce o lo detecta Roku) |
| `sub` | URL de subtítulos `.srt` o `.vtt`; una sola pista, empieza encendida |
| `subs` | Varias pistas externas, una por línea: `idioma\|nombre\|url` (p. ej. `spa\|En línea 1\|https://…`). Empiezan apagadas y se eligen después con `subtitle` |
| `headers` | Cabeceras HTTP, una por línea: `Referer: …` |
| `delta` / `pos` | Segundos para `skip` (relativo) y `seek` (absoluto) |
| `start` | Segundo en el que empieza el video (para retomar) |
| `key` | Identificador opaco del video; el canal lo devuelve en su estado |
| `index` | Posición (desde 0) del elemento de la cola que se quita con `dequeue` |
| `id` | Identificador de la pista para `audio` y `subtitle` (con `subtitle`, `off` desactiva los subtítulos) |

**Estado en vivo (canal v2):** mientras el canal está abierto, `GET http://ROKU:8061/status` devuelve un JSON
de solo lectura con `state` (`idle`, `play`, `pause`, `buffering`), `key`, `title`, `position` y `duration` (ms),
`queue` (lista de `{key, title}`), `error` (motivo del último fallo), `seq` (sube con cada video nuevo) y las pistas:
`audio` y `subs` (listas de `{id, lang, name}`) con `audiocur` y `subcur` (la activa; `subcur` vacío = desactivados).
La app lo consulta cada segundo para la pantalla de control, la notificación y para guardar el progreso.
No incluye la URL del video.

**Enlaces largos:** Roku documenta un máximo de 255 caracteres para `contentId`. Si un valor supera
240 caracteres, la app abre el canal y le envía la consulta completa en trozos
(`/input?cid=ID&i=N&n=TOTAL&chunk=…`), que el canal reconstruye.

### Limitaciones conocidas
- El estado en vivo (posición, cola, errores) solo existe con el canal v2 abierto; con un canal antiguo la app
  recurre a lo que informa el reproductor del Roku, que es bastante menos.
- Retomar funciona por enlace de Stremio (la URL original, antes de las redirecciones), no por título.
- Subtítulos: solo URLs `http(s)`; los archivos guardados en el teléfono no se pueden enviar. Las pistas externas
  tienen que ir **con el video** (`sub`/`subs` en la orden de reproducción): añadirlas con el video en marcha lo congela.

- Navegador: no funciona con videos con DRM ni con los que solo existen como `blob:`. Para los detectados,
  la app pasa al Roku las cookies de la página, por la red local y sin cifrar (ECP solo usa HTTP).
- El modo desarrollador de Roku admite un solo canal instalado a la vez.
- Bloqueador de anuncios: funciona solo dentro del navegador de la app y reduce los anuncios, no garantiza quitarlos
  todos. Corta peticiones y ventanas emergentes y oculta recuadros con las listas públicas; los anuncios dentro de
  un video (o los que se sirven desde el mismo dominio de la página) pueden pasar.

### Subtítulos en línea
Al enviar un video la app consulta Cinemeta (para saber qué película o serie es) y el complemento OpenSubtitles v3 de
Stremio (los archivos `.srt`), sin cuenta ni clave. Se envía solo el título, el año, la temporada y el episodio; nunca la
dirección del video. Ofrece hasta dos versiones en español y dos en inglés, ordenadas por parecido con el nombre del
archivo, y el Roku las lista como «Español · En línea 1»… Se apaga en Ajustes → Subtítulos. Si la búsqueda falla, el video
se envía igual.

### Bloqueador de anuncios y listas de filtros
El navegador integrado usa las listas **EasyList**, **EasyPrivacy** y **EasyList Spanish** (con la sintaxis de
Adblock Plus), más unas reglas propias en `android/app/src/main/assets/filters/extras.txt`. Las listas van dentro
del APK y no se descarga nada por sí solo: en *Ajustes → Actualizar listas* se pueden bajar las más recientes.
Todo el filtrado ocurre en el teléfono. EasyList y EasyPrivacy son © de sus autores (https://easylist.to), con
licencia doble GPL-3.0+ / CC BY-SA 3.0, compatible con este proyecto.

### Diagnóstico
- Consola del canal: `telnet IP_DEL_ROKU 8085` (mensajes `[FreeVideocaster]`). En Windows, el cliente
  telnet se activa en "Características opcionales", o usa la extensión **BrightScript Language** de VS Code.
- Validar el canal sin Roku: `npx -p brighterscript bsc --rootDir roku-receiver --createPackage false --copyToStaging false`.
- Pruebas unitarias de la app: en `android/`, `gradlew test`.
