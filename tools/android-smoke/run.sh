#!/usr/bin/env bash
# Test dymny APK na emulatorze/telefonie (adb): instalacja, start gry lokalnej z pominieciem menu,
# 75 s gry, zrzut ekranu, logcat. Niepowodzenie: awaria procesu albo brak wpisu "Klatki:" (gra nie renderuje).
# Uzycie: tools/android-smoke/run.sh <katalog z APK> <katalog wynikow>
set -u
APK_DIR=${1:-android/build/outputs/apk/debug}
OUT=${2:-build/android-smoke}
PKG=pl.fawizja.pixel
mkdir -p "$OUT"
APK=$(ls "$APK_DIR"/*.apk | head -1)
echo "APK: $APK"
adb install -r "$APK" || exit 1
adb logcat -c
adb shell am start -W -n "$PKG/pl.fawizja.pixel.android.AndroidLauncher" --es autostart Test:MONK
sleep 75
adb exec-out screencap -p > "$OUT/android.png"
adb logcat -d > "$OUT/logcat.txt"
grep -E "Pixel|AndroidRuntime" "$OUT/logcat.txt" > "$OUT/pixel.txt" || true
cat "$OUT/pixel.txt" | tail -40
if grep -q "FATAL EXCEPTION" "$OUT/logcat.txt"; then
  echo "BLAD: awaria aplikacji"; grep -A 25 "FATAL EXCEPTION" "$OUT/logcat.txt" | head -60; exit 1
fi
if ! adb shell pidof "$PKG" > /dev/null; then echo "BLAD: proces gry nie dziala"; exit 1; fi
if ! grep -q "Klatki:" "$OUT/pixel.txt"; then echo "BLAD: brak pomiaru klatek (gra nie renderuje?)"; exit 1; fi
grep "Klatki:" "$OUT/pixel.txt" > "$OUT/frames.txt"
echo "OK: gra dziala na emulatorze"
