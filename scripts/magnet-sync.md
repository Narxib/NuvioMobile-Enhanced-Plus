# Mantenimiento del fork de magnet

## Remotos

| Remoto | Repositorio | Uso |
|---|---|---|
| `upstream` | `luqmanfadlli/NuvioMobile-Enhanced` | Código original, nunca se escribe |
| `origin` | `Narxib/NuvioMobile-Enhanced-Plus` | Tu fork, ahí vive la rama magnet |

Ramas del fork:

- `feature/magnet-links`: trabajo principal. Contiene **solo** los commits del feature magnet encima de `upstream/enhanced`.
- `enhanced`: espejo del original, sin cambios propios. El script lo actualiza en cada sync. No se desarrolla sobre él.

## Sincronizar con una actualización del original

```powershell
git switch feature/magnet-links
.\scripts\sync-upstream.ps1
```

El script:

1. Exige árbol limpio y rama distinta de `enhanced`.
2. Hace `git fetch upstream --prune --tags`.
3. Crea una rama de respaldo `backup/pre-sync-<fecha>` y una etiqueta `upstream-base-<fecha>`.
4. Reaplica tus commits con `git rebase upstream/enhanced`.
5. Compila el APK debug para detectar roturas de API.
6. Con `-Push`, publica con `--force-with-lease`.

Opciones: `-Push`, `-SkipBuild`.

Si el rebase se para por conflictos: resuélvelos, `git add`, `git rebase --continue`. Para deshacerlo todo, `git rebase --abort`. Si la compilación falla tras el rebase, `git reset --hard backup/pre-sync-<fecha>`.

`git rerere` está activado: los conflictos ya resueltos se reaplican solos en el siguiente sync.

## Por qué rebase y no merge

Un merge arrastra commits de sincronización y hace reaparecer conflictos ya resueltos. El rebase mantiene el fork como dos commits limpios sobre upstream, con un diff mínimo y fácil de auditar. La rama es solo tuya, así que reescribir su historial es seguro (`--force-with-lease`).

## Puntos de integración con el código original

Todo el feature vive en `composeApp/src/commonMain/kotlin/com/nuvio/app/features/magnet/`. Fuera de ahí solo hay enganches mínimos:

| Archivo | Cambio | Riesgo de conflicto |
|---|---|---|
| `AndroidManifest.xml` | intent-filters `magnet` y `SEND` | bajo |
| `LinkActivity.kt` | reenvío de intents magnet | bajo |
| `AppUrlBridge.kt` | `AppDeepLink.Magnet` | bajo |
| `MainAppContent.kt` | consumo del deep link | bajo |
| `Routes.kt`, `AppNavigationSupport.kt` | ruta y `entry<MagnetRoute>` | bajo |
| `iosApp/iosApp/Info.plist` | esquema `magnet` | bajo |
| `values/magnet_strings.xml`, `values-es/magnet_strings.xml` | textos del feature | nulo |

Reglas para que siga siendo barato:

- No reformatear ni "limpiar" archivos del original. Cada línea tocada fuera de `features/magnet` es un posible conflicto.
- Los textos nuevos van en `magnet_strings.xml`, nunca en `strings.xml`.
- Los tests del feature viven en `commonTest/.../features/magnet/`.
- Tras cada sync, instalar el APK y probar un magnet de película y uno de serie.

## Si algún día quieres upstreamearlo

Con dos commits aislados, el feature se puede enviar como PR a `enhanced`. Mientras tanto, el rebase es suficiente.
