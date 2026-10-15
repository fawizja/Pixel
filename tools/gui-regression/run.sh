#!/usr/bin/env bash
# Regresja GUI (etap 16): zrzuty kluczowych miejsc porownane z wzorcami w tools/gui-regression/reference.
#   tools/gui-regression/run.sh            porownanie (kod wyjscia 1 przy roznicy)
#   tools/gui-regression/run.sh --update   zapis nowych wzorcow (po zamierzonej zmianie wygladu)
# Wymaga ./gradlew :desktop:installDist i ekranu (DISPLAY, np. Xvfb albo xvfb-run).
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
HERE="$ROOT/tools/gui-regression"
BIN="$ROOT/desktop/build/install/desktop/bin/desktop"
LIB="$ROOT/desktop/build/install/desktop/lib"
OUT="${GUI_REGRESSION_OUT:-$ROOT/build/gui-regression}"
THRESHOLD="${GUI_REGRESSION_THRESHOLD:-0.03}"
[ -x "$BIN" ] || { echo "Brak $BIN - uruchom ./gradlew :desktop:installDist"; exit 2; }
rm -rf "$OUT"
mkdir -p "$OUT/shots" "$OUT/home"
missing=0
while IFS='|' read -r name opts; do
  case "$name" in ''|'#'*) continue ;; esac
  rm -rf "$OUT/home" "$OUT/save"
  mkdir -p "$OUT/home"
  # osobny HOME: zapamietany login i ustawienia gracza nie wplywaja na wyglad
  HOME="$OUT/home" JAVA_OPTS="$opts -Duser.home=$OUT/home -Dpixel.seed=1 -Dpixel.frames=45 -Dpixel.saveDir=$OUT/save -Dpixel.screenshot=$OUT/shots/$name.png" \
    timeout 120 "$BIN" > "$OUT/$name.log" 2>&1 || true
  if [ -f "$OUT/shots/$name.png" ]; then echo "zrzut: $name"; else echo "BLAD: brak zrzutu $name (log: $OUT/$name.log)"; missing=$((missing + 1)); fi
done < "$HERE/scenes.txt"
if [ "${1:-}" = "--update" ]; then
  [ "$missing" -eq 0 ] || { echo "Nie zapisano wzorcow: brakuje $missing zrzutow"; exit 1; }
  rm -f "$HERE"/reference/*.png
  java -cp "$LIB/*" pl.fawizja.pixel.desktop.ScreenshotCompare update "$HERE/reference" "$OUT/shots"
else
  java -cp "$LIB/*" pl.fawizja.pixel.desktop.ScreenshotCompare compare "$HERE/reference" "$OUT/shots" "$OUT/diff" "$THRESHOLD"
fi
