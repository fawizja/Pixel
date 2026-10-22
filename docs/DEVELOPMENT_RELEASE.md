# Pixel - wydanie development (rozbudowa w stylu Tibii, etapy 1-20)

Wydanie z brancha `development` (master prompt z 2026-10-22). Branch `main` zostaje dzialajaca wersja;
`development` zawiera cala zawartosc `main` (ostatnio 0ba79e6, 0.196.0) i 20 etapow rozbudowy.

## Pobranie

- APK (debug): https://github.com/fawizja/Pixel/releases/tag/development-apk (plik `Pixel-development.apk`,
  CI buduje go po kazdym pushu na `development`). Przed instalacja odinstaluj poprzednia wersje - kazda
  kompilacja debug ma inny klucz podpisu.
- Kod: archiwum ZIP brancha `development` (`git archive`) albo https://github.com/fawizja/Pixel/archive/refs/heads/development.zip

## Zalozenia serwera

Kazda profesja (paladyn, druid, czarodziej, mnich) startuje na poziomie 300 z 10 000 000 gp w banku
(`data/starting.properties`, pilnuje `StartingConfigFileTest` i `BriefAcceptanceTest`). Weteran dostaje
obsydianowy ekwipunek profesji, mikstury i runy na 300 poziom oraz drugi plecak (40 miejsc).

## Co zawiera (skrot etapow)

1. Audyt i plan. 2. Grafika OTSP (CC BY 4.0) i OTClientV8 (MIT), renderowanie warstw.
3. Mapa w warstwach (podloze, obrzeza, dekoracje, obiekty, mury), pietra. 4. Autorska geografia swiata.
5. Cztery miasta: Varden (krolewskie), Selvaran (portowe), Frostholm (gorskie), Qasirah (egzotyczne).
6. Depozyty, banki, swiatynie, sklepy, domy. 7. Mosty, statki i podroze (kapitanowie, wozniacy, wyspy).
8. Expowiska na powierzchni. 9. Wielopoziomowe lochy. 10. Potwory, bossowie, loot.
11. Profesje i poziomy do 300+. 12. Ruch i walka: utani hur / utani gran hur, krzywa szybkosci, kolizje graczy.
13. Czary 120-300, inkantacje wpisywane na czacie, runy i mikstury. 14. Wiele plecakow, przeciaganie, monety
(100 gp = 1 platyna, 100 platyny = 1 krysztal). 15. NPC, zadania i ekonomia wysp.
16. Interfejs w stylu OTClient V8 (zakladki konsoli, stronicowana ksiega czarow).
17. Sterowanie mobilne (joystick pod kciukiem, przytrzymanie = podglad pola). 18. Optymalizacja rysowania.
19. Testy koncowe (test odbiorczy briefu, regresja GUI 44 scen). 20. To wydanie.

Szczegoly kazdego etapu, commity i wyniki testow: `PROJECT_PROGRESS.md`. Galeria zrzutow z gry po etapach
jest podlinkowana w `PROJECT_PROGRESS.md`.

## Kompilacja i testy

```
./gradlew test                     # testy (shared, core, server, desktop)
./gradlew :desktop:run             # gra na PC
./gradlew :server:run              # serwer
./gradlew :android:assembleDebug   # APK (wymaga Android SDK)
tools/gui-regression/run.sh        # regresja GUI pod Xvfb (44 sceny)
```

## Znane ograniczenia

- APK jest w wersji debug (do testow), nie podpisanym wydaniem do sklepu.
- Gesty dotykowe (przeciaganie przedmiotow, joystick pod kciukiem, przytrzymanie) nie maja testow
  automatycznych - sprawdzone kompilacja, zrzutami z gry i testami logiki; warto sprawdzic na telefonie.
- Zysk z optymalizacji rysowania zmierzony liczba wywolan (251 -> 109 na klatke); czasu klatki na telefonie
  nie mierzono w chmurze.
