# Compatibilidad con versiones de Android

`minSdk 24` (Android 7.0) y `targetSdk 37` (Android 17). Probado el 2026-10-14.

## Qué se probó y dónde

La batería completa se repitió en emuladores de Android (Google APIs, x86_64) con un **Roku falso** en el PC que imita
la API del Roku (ECP en el puerto 8060 y el estado del canal en el 8061). Pasos: abrir la app, conectar el Roku por IP,
simular a Stremio con un enlace, enviar, comprobar la pantalla «Reproduciendo», el servicio en primer plano y la
notificación, y disparar sus acciones (pausa, +10 s, −10 s, detener).

| Android | API | Dónde | Resultado |
|---|---|---|---|
| 7.0 | 24 | Emulador | Abre, muestra «Sin Wi-Fi» (la imagen no simula Wi-Fi). El envío no se pudo probar |
| 7.1 | 25 | Emulador | Abre; el servicio publica la notificación (prioridad baja, 4 acciones). El envío no se pudo probar (sin Wi-Fi en la imagen) |
| 8.0 | 26 | Emulador | Batería completa correcta; el bloqueador de anuncios también |
| 11 | 30 | Emulador | Batería completa correcta |
| 14 | 34 | Emulador | Batería completa correcta; permiso de notificaciones; servicio `connectedDevice` |
| 16 | 36 | Emulador | Batería completa correcta |
| 17 | 37 | **Pixel 8 Pro y Roku reales** | Todo probado, ver `pruebas-v0.2.md`, `pruebas-v0.3.md` y `pruebas-v0.4.md` |

Sin probar: 9, 10, 12, 13 y 15; dependen de las mismas reglas que sus vecinas probadas.

## Qué cambia entre versiones y cómo se maneja

| Versión | Diferencia | En el código |
|---|---|---|
| Android 7 | No hay íconos adaptables, canales de notificación ni `getForegroundService` | Ícono de respaldo en `res/mipmap-anydpi`; `CastSessionService` usa `Notification.Builder(this)` con prioridad baja y `PendingIntent.getService` |
| Android 8+ | Canal de notificación obligatorio | Se crea solo desde la API 26 |
| Android 12+ | Colores dinámicos; límites para iniciar servicios en segundo plano | Respaldo verde de la marca en versiones anteriores; el servicio solo se inicia con la app a la vista y un fallo no rompe el envío |
| Android 13+ | Permiso de notificaciones | Aviso en la app; sin el permiso todo funciona menos la notificación |
| Android 14+ | Tipo de servicio en primer plano | `connectedDevice` |
| Android 17 | Permiso de red local | Se pide en la app (`net/LocalNetwork`) |

## Limitaciones conocidas

- Las imágenes de emulador de Android 7.x no simulan Wi-Fi; la app exige Wi-Fi o Ethernet para hablar con el Roku, así
  que ahí no se pudo completar el envío. Está pendiente probarlo en un teléfono real con Android 7.
- WebView depende de la versión del componente «Android System WebView» del teléfono. El navegador integrado y las
  reglas de ocultar elementos con `:has()` necesitan un WebView reciente.
- Los textos de la app están en español y no hay traducciones.
