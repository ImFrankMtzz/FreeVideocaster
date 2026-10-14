# Registro de pruebas: subtítulos incrustados en MKV

Probado el 2026-10-14 en un Pixel 8 Pro (Android 17) y un Roku TV KD28X (Roku OS 15.3.4), con videos MKV reales
de TorBox enviados desde Stremio. Cada intento se siguió con un registrador del estado del canal (`:8061/status`)
y la consola de depuración (`telnet <IP> 8085`).

## El problema

El usuario elegía «Español» en el selector y el Roku daba la pista por activa (`subcur = mkv/5`), pero **no se
veía**. Aparecía al cambiar el audio a inglés y volver a español, y entonces se quedaba.

## Qué se probó para forzar el dibujo (sin tocar el audio)

| Intento | Resultado |
|---|---|
| Reposicionar en el mismo punto (`seek`) | No se ve |
| Retroceder 2 s | No se ve |
| Reasignar el mismo audio | No se ve |
| Apagar y volver a encender la pista | No se ve |
| Detener y reiniciar el contenido en la misma posición, con la pista ya elegida | No se ve |
| **Cambiar a la otra pista de audio y volver** (4 s) | **Se ve** y el audio queda en español |

Por eso el canal hace ese cambio solo (`startAudioFlip` en `PlayerScene.brs`), con `mute` activo.

## Confirmado por el usuario con el arreglo instalado

| Qué | Resultado |
|---|---|
| El video empieza sin subtítulos (también tras 15 s sin tocar nada) | Correcto: el Roku no los enciende solo |
| Elegir «Español» | Se ve, sincronizado, el audio sigue en español |
| Elegir «Inglés» | Se ve; en dos versiones de una película salió **atrasado** (no se corrige reposicionando; el español de las mismas versiones sí estaba bien), en otra película salió sincronizado → desfase de esa pista del archivo |
| «Desactivados» | Desaparecen |
| Nombres de pista en la app | «Español» / «Inglés» (se ignora la dirección del sitio que traían como nombre) |

## Subtítulos en línea (OpenSubtitles v3)

Probado el mismo día con un episodio de serie enviado desde Stremio (el título llegaba en español).

| Qué | Resultado |
|---|---|
| Cinemeta encuentra la serie por un título en español | Sí (primer resultado) |
| Pista externa añadida **con el video en marcha** | **Congela** la reproducción (más de 40 s detenida): descartado |
| Pista externa dada **al empezar** | Funciona, sincronizada; convive con las 2 incrustadas |
| Dos pistas externas al empezar, subtítulos apagados | Correcto; 4 pistas en la lista |
| Elegir una externa ya en marcha | Se ve sin pausa y sin el cambio de audio |
| Flujo completo desde la app (búsqueda, envío, selector «En línea», español, inglés, desactivar) | Confirmado por el usuario: funciona correctamente |
| Pruebas unitarias | 87, sin fallos; Lint limpio |

Stremio no manda título, subtítulos ni IMDb; sí `season` y `episode` dentro de la URL. Esa URL contiene la clave de
TorBox: no copiar «Ver detalles técnicos» a ningún sitio.

## Sin probar

- MKV con una sola pista de audio (el truco no aplica: ¿se ve el subtítulo?).
- Elegir un subtítulo con el video en pausa (`flipPending`).
- Pausa breve (~2 s) al elegir un subtítulo: aceptada en las pruebas, sin una opinión formal del usuario.
- Películas (solo se probó una serie) y títulos que Cinemeta resuelva mal (mostraría subtítulos de otra obra; la lista no dice qué obra encontró).
- Sincronía de la 2.ª versión de cada idioma y de videos con otra velocidad de cuadros.
- Sin internet en el teléfono o con el servicio caído: debe enviar el video igual (código con `runCatching`, sin probar en vivo).

## Errores míos durante la investigación

- Se dijo que el Roku encendía el subtítulo solo; en realidad lo elegía el usuario con el teléfono. Una reproducción
  sin tocar nada lo confirmó.
- Un 404 al mandar `id=mkv/6` por `curl` se tomó por un canal cerrado: el `/` iba sin codificar.
