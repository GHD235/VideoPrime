# Firma persistente de las APK nightly

El workflow `.github/workflows/videoprime-apk.yml` compila la variante `continuous`, que se firma
con la clave de depuración (`~/.android/debug.keystore`). En GitHub Actions esa clave se genera
nueva en cada ejecución, así que Android no deja instalar una APK encima de otra.
Para evitarlo, guarda una clave fija como secret:

```sh
keytool -genkeypair -v -keystore videoprime-debug.keystore \
  -storepass android -keypass android -alias androiddebugkey \
  -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=VideoPrime Debug"
base64 -w0 videoprime-debug.keystore   # copia la salida
```

En GitHub: VideoPrime → Settings → Secrets and variables → Actions → New repository secret,
nombre `VIDEOPRIME_DEBUG_KEYSTORE`, valor = la salida de `base64`.
Guarda el `.keystore` en un sitio seguro y **no** lo subas al repo.

Esta clave es solo para las nightly. Para una versión "release" real se creará otra clave
con contraseñas propias.
