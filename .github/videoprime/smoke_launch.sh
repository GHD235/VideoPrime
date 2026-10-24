#!/usr/bin/env bash
# VideoPrime: instala la APK en el emulador, la abre y comprueba que sigue viva sin cierres.
# Uso: smoke_launch.sh <apk> <archivo de resultado>
# Escribe "ok" o "crash" en el archivo de resultado; si el emulador falla antes, no escribe nada.
set -u
apk="$1"
result="$2"
package="$(aapt2 dump packagename "$apk" 2>/dev/null || true)"
[ -n "$package" ] || package="com.ghd235.videoprime.continuous"
echo "Paquete: $package"

adb install -r "$apk" || { echo "No se pudo instalar la APK (¿emulador?)"; exit 1; }
adb logcat -c
adb shell am start -W -n "$package/org.schabi.newpipe.MainActivity" || exit 1
# tiempo para que arranque, pinte el Inicio y lance sus trabajos en segundo plano
sleep 25

# un cierre de la app deja "Process: <paquete>" en su FATAL EXCEPTION
if { adb logcat -d -b crash; adb logcat -d; } 2>/dev/null | grep -qF "Process: $package"; then
  echo "La app se cerró al arrancar:"
  { adb logcat -d -b crash; adb logcat -d; } 2>/dev/null | grep -A40 "FATAL EXCEPTION" | head -80
  # the run's logs cannot always be downloaded: the trace also goes to an annotation
  trace="$({ adb logcat -d -b crash; adb logcat -d; } 2>/dev/null | grep -A25 "FATAL EXCEPTION" | head -26 \
    | sed -E 's/^.*AndroidRuntime: //' | tr '\n' '|' )"
  echo "::error title=Cierre al arrancar::${trace}"
  echo crash > "$result"
  exit 1
fi
if [ -z "$(adb shell pidof "$package" | tr -d '\r')" ]; then
  echo "La app no sigue abierta tras arrancar"
  adb logcat -d | tail -80
  echo crash > "$result"
  exit 1
fi
echo ok > "$result"
echo "La app arranca y sigue abierta"
