# Changelog

## [0.50.0] - 2026-10-16

### Dodano
- Woźnice: w każdym z 4 miast przy fontannie przed świątynią stoi woźnica (nowa rola NPC `CARRIER`).
  W rozmowie wybierasz miasto, płacisz monetami z plecaka (3 gp za pole odległości, co najmniej 100 gp;
  z Varden: Frostholm 160, Selvaran 260, Qasirah 340 gp) i stajesz obok woźnicy w mieście docelowym.
  Po walce z graczem trzeba odczekać blokadę. Działa lokalnie i online (akcja `npc:travel:Miasto`).
- `npc/Travel` (ceny i trasy), rozmowa o podróży, strój woźnicy (kapelusz, bat), woźnice w generatorze mapy.
  3 nowe testy; łącznie 285.

## [0.49.0] - 2026-10-16

### Dodano
- Pogoda na powierzchni: w około 30% dwudziestominutowych okien pada. Rodzaj zależy od krainy: deszcz
  (Varden, dżungla), śnieg (mroźne krainy), burza piaskowa (pustynia Qasirah). Opady narastają i zanikają
  na brzegach okna, lekko przyciemniają świat, a zmiana pogody pojawia się w dzienniku. Pod ziemią zawsze
  pogodnie. Pogoda wynika z zegara i nazwy krainy, więc gracze online widzą to samo bez nowych komunikatów.
  Przy oszczędzaniu zasobów rysowanych jest mniej kropli.
- Analiza polowania liczy też wartość zebranych przedmiotów (cena z items.csv) i jej tempo na godzinę.
- `world/Weather`, `client/render/WeatherRenderer` (bez stanu, bez alokacji), opcja `-Dpixel.weather=rain|snow|sandstorm|clear`,
  scena `rain` w regresji GUI (28 scen). Manekin ma ciemny obrys (widoczny na piasku). 3 nowe testy; łącznie 282.

## [0.48.0] - 2026-10-16

### Dodano
- Manekiny treningowe: po 2 w strefie ochronnej każdego miasta (w Varden przy fontannie przed świątynią).
  UŻYJ obok manekina (albo dotknięcie go) zaczyna trening: co 3 s próba umiejętności broni (pięści, miecz,
  dystans albo magia, zależnie od broni) i co 6 s tarczy, bez doświadczenia i bez kosztów. Ruch, śmierć albo
  ponowne UŻYJ kończy trening; awans umiejętności pojawia się w dzienniku. Dotknięcie dalekiego manekina
  prowadzi do niego. Działa lokalnie i online (logika w sesji, serwer liczy te same miejsca z mapy).
- `training/TrainingDummies` (miejsca liczone powtarzalnie z mapy i pozycji NPC), `Skill.label()`, grafika
  `TileArt.dummy`. 2 nowe testy; łącznie 279.

## [0.47.0] - 2026-10-16

### Dodano
- Analiza polowania: okno z czasem polowania, zdobytym doświadczeniem, liczbą zabitych potworów i bilansem złota
  w plecaku, każde także na godzinę, oraz czasem do następnego poziomu przy obecnym tempie. Otwiera je klawisz H,
  `/analiza` (albo `/polowanie`) lub wiersz w księdze; okno odświeża się co sekundę, „Zacznij od nowa” zeruje liczniki.
  Liczy z danych postaci, więc działa lokalnie i online.
- `stats/HuntAnalyzer`, `Hud.infoOpen/updateInfo`, wiersze okna informacyjnego mogą mieć akcję. 3 nowe testy; łącznie 277.

## [0.46.0] - 2026-10-16

### Dodano
- Zwój powrotu (`return_scroll`, 300 gp u kupców we wszystkich 4 miastach): użyty z plecaka po 3 s skupienia
  przenosi do świątyni miasta postaci. Ruch albo otrzymane obrażenia przerywają skupienie (zwój zostaje),
  nie działa w czasie blokady po walce z graczem. Zwój znika dopiero po udanym przeniesieniu. Działa lokalnie
  i online (logika w sesji, serwer wykonuje ją przy ACT slot_use).
- Test zwoju (przeniesienie, przerwanie ruchem); łącznie 274.

## [0.45.0] - 2026-10-16

### Dodano
- Opis przedmiotu w plecaku: po dotknięciu przedmiotu nad plecakiem pojawia się ramka ze statystykami i różnicą
  względem tego, co postać ma założone w tym miejscu (np. „atak 24 (+4)”), oraz z wymaganiami: poziom
  („za niski”), profesje („nie dla ciebie”) i wartość.
- `item/ItemCompare`, scena `bagitem` w regresji GUI (27 scen). 2 nowe testy; łącznie 273.

## [0.44.0] - 2026-10-16

### Dodano
- Licznik bossów: okno „Bossowie” (wiersz u każdego przewodnika albo `/bossowie`) z listą bossów świata, miejscem
  (kraina i piętro) i stanem: „żyje”, „odrodzi się za X min” (albo h) lub „odradza się lada chwila”.
  Online dane przychodzą z serwera (komunikat BOSS), lokalnie z własnego świata.
- Okno informacyjne w HUD (`Hud.openInfo`) do takich list. Scena `bosses` w regresji GUI (26 scen).
- 3 nowe testy (raport i czas odrodzenia, komunikat serwera, komenda czatu); łącznie 271.

## [0.43.0] - 2026-10-15

### Dodano
- Automatyczne łupy (domyślnie włączone, przełącznik „Łupy z ciał” w panelu ustawień pod „i”): ciało z łupem
  na polu postaci albo obok jest przeszukiwane samo, co 0,8 s, lokalnie i online. Przy pełnym plecaku najwyżej
  dwie próby na ciało, bez zalewania dziennika ostrzeżeniami.
- Ustawienie zapisywane (`loot` w Preferences). Sprawdzone w grze: walka z goblinami w kopalni, 78 gp z ciał
  trafiło do plecaka bez dotykania. Test ustawień; łącznie 269.

## [0.42.0] - 2026-10-15

### Dodano
- Dziennik zadań (klawisz Z, `/zadania`, wiersz „Dziennik zadań” w księdze): zadanie dnia, zadania w toku
  z postępem i zleceniodawcą (ODDAJ, gdy gotowe) oraz nowe zadania dostępne na obecnym poziomie.
- Dotknięcie zadania prowadzi postać do zleceniodawcy (zadanie dnia: do najbliższego przewodnika) i otwiera
  rozmowę po dojściu. Sprawdzone w grze: spod bramy Varden do Strażnika Orena.
- Opcja deweloperska `-Dpixel.button=akcja`, scena `journal` w regresji GUI (25 scen). Testy 268/268.

## [0.41.0] - 2026-10-15

### Dodano
- Dymki czatu: wiadomość z czatu ogólnego pojawia się nad głową mówiącego gracza (także nad własną postacią)
  na 4-9 s, z ciemnym tłem, zawijana do kilku linii i wygaszana na końcu. Czat prywatny, drużyny i gildii
  zostaje tylko w dzienniku.
- Scena `chat` w regresji GUI (24 sceny), opcja `-Dpixel.open=chat`. Testy 268/268.

## [0.40.0] - 2026-10-15

### Dodano
- Nagroda za codzienne wejście do gry: seria 7 dni (300, 500, 800, 1 000, 1 500, 2 000 i 5 000 gp, razy poziom/10
  od poziomu 20), złoto na konto, raz dziennie, z komunikatem w dzienniku. Przerwa zeruje serię, po 7. dniu
  zaczyna się nowy tydzień. Działa lokalnie i online (serwer liczy dzień), zapis `login`.
- 3 nowe testy (seria i zapis, nagroda w sesji); testy sieciowe sald uwzględniają nagrodę. Łącznie 268.
  Odświeżone wzorce GUI (komunikat w dzienniku).

## [0.39.0] - 2026-10-15

### Dodano
- Pojedynki (online): `/pojedynek Imię` wyzywa gracza stojącego najwyżej 8 pól dalej, ta sama komenda drugiej
  strony przyjmuje wyzwanie (ważne minutę); także wiersz „Pojedynek” w menu gracza. Walka do pierwszej krwi:
  cios, który by zabił, kończy pojedynek przy 1 HP przegranego. Bez czaszek, blokady strefy ochronnej i ochrony
  poziomu 30 (pojedynkować się mogą też nowe postacie i członkowie drużyny), obrażenia jak w PvP (x0,5).
- Koniec: zwycięstwo, `/pojedynek poddaj`, wyjście z gry (walkower), śmierć od potwora lub remis po 5 minutach.
  Obie strony przestają się atakować, gracze w pobliżu widzą wynik.
- Test sieciowy pojedynku (poziom 8, 1 HP, brak czaszki, potem zwykłe zasady) i test komend; łącznie 265.

## [0.38.0] - 2026-10-15

### Dodano
- Dotknij, aby iść: dotknięcie wolnego pola na ekranie prowadzi tam postać tą samą trasą co marsz z mapy
  (potwór dalej oznacza cel, gracz online otwiera menu gracza).
- Dotknięcie dalekiego NPC prowadzi na pole obok niego i samo otwiera rozmowę po dojściu; dotknięcie dalekiego
  ciała z łupem prowadzi obok niego.
- Opcja deweloperska `-Dpixel.tap=x,y` (dotknięcie pola). Sprawdzone w grze: marsz spod bramy do Strażnika Orena
  kończy się otwartą rozmową. Regresja GUI bez zmian (23/23), testy 264/264.

## [0.37.0] - 2026-10-15

### Dodano
- Marsz do celu: dotknięcie miejsca na dużej mapie wyznacza najkrótszą trasę po piętrze (z ominięciem schodów
  i drabin) i postać sama tam idzie, lokalnie i online. Cel widać na minimapie i dużej mapie (niebieski znacznik).
  Joystick albo klawisze przerywają marsz; zepchnięta z trasy postać wyznacza ją od nowa.
- `Pathfinder.path` - pełna trasa BFS na tablicach (całe piętro w jednym wywołaniu); opcja deweloperska
  `-Dpixel.walkto=x,y`. 2 nowe testy (ciągłość trasy, cele nieosiągalne); łącznie 264.

## [0.36.0] - 2026-10-15

### Dodano
- Minimapa w prawym górnym rogu (60 x 60 pól wokół postaci): ściany, woda, przeszkody, schody i drabiny (żółte),
  czerwony znacznik gracza. Działa na każdym piętrze; na niskim ekranie maleje albo znika, by nie zasłaniać przycisków.
- Duża mapa całego piętra z nazwami miast: dotknięcie minimapy albo klawisz J (zamyka dotknięcie, J lub Esc).
- Nowa scena `map` w regresji GUI (23 sceny), odświeżone wzorce scen z minimapą; 2 nowe testy kolorów; łącznie 262.

## [0.35.0] - 2026-10-15

### Dodano
- Skrzynie skarbów: po 4 na każdym piętrze lochów (-1..-5) i po 2 na wyżynach (+1..+3), z dala od schodów i przejść,
  zawsze osiągalne z miasta. Każda postać otwiera każdą skrzynię raz (UŻYJ obok skrzyni): złoto od 500 gp na -1
  do 40 500 gp na -5 i mikstury coraz lepsze z głębokością. Pełny plecak - skrzynia czeka.
- Skrzynie rysowane na mapie (zamknięta / otwarta dla tej postaci), działają lokalnie i online; zapis `chests`.
- Nowa scena `chest` w regresji GUI (22 sceny); 2 nowe testy (rozmieszczenie, osiągalność, otwieranie raz,
  zapis); łącznie 260.

## [0.34.0] - 2026-10-15

### Dodano
- Poczta (online, u bankiera): `/wyślij Imię, 5 wilcze futro` wysyła przedmioty z plecaka do depozytu innej postaci.
  Odbiorca w grze dostaje je od razu (z wiadomością), a nieobecny - przy następnym wejściu. Gdy depozyt jest pełny,
  przesyłka czeka. Do 50 czekających przesyłek na odbiorcę, zapis w `mail.txt` na serwerze (przetrwa restart),
  usunięcie konta kasuje jego przesyłki. Złota nie wysyła się pocztą.
- 2 nowe testy (skrzynka i zapis, sieciowy do gracza w grze i offline); łącznie 258.

## [0.33.0] - 2026-10-15

### Dodano
- Wierzchowiec: siodło (50 000 gp u kupców w Varden, Frostholm, Qasirah i Selvaran) użyte z plecaka oswaja konia
  na stałe. Przycisk KOŃ (klawisz M) - wsiadanie i zsiadanie, +60 szybkości (na poziomie 8 o ok. 25% szybciej).
  Pod ziemią nie można jechać - przy zejściu postać zostawia konia.
- Koń rysowany pod postacią (własny sprite w atlasie), widoczny też u innych graczy (pole `M` w komunikacie PL).
- Zapis: flagi `H` (ma konia) i `M` (jedzie).
- Nowa scena `mount` w regresji GUI (21 scen); 2 nowe testy (sklep, siodło, szybkość, zapis, podziemia; sieciowy:
  inni widzą jeźdźca); łącznie 256.

## [0.32.0] - 2026-10-15

### Dodano
- Zadanie dnia u przewodnika w każdym mieście: raz dziennie (data kalendarzowa) pokonaj 20-40 potworów
  jednego rodzaju dobranego do poziomu (nie boss, między połową a 120% poziomu postaci). Nagroda: podwójne
  doświadczenie za te potwory i złoto na konto. Postęp w oknie przewodnika ("Zadanie dnia: Wilk 12 / 31"),
  wpis w dzienniku po wykonaniu. Działa lokalnie i online; zapis w postaci (`daily`).
- Nowa scena `guide` w regresji GUI (20 scen).
- 2 nowe testy (dobór potwora dla poziomów 8-1000, cały przebieg dnia z nagrodą i nowym dniem); łącznie 254.

### Poprawiono
- "ukończone" z polską literą w liście zadań przewodnika.

## [0.31.0] - 2026-10-15

### Dodano
- Inwazje na miasta (serwer): co 120 minut (`-Dpixel.invasionMinutes`) grupa potworów z hersztem pojawia się
  za murami kolejnego miasta - Varden (bandyci, troll), Frostholm (śnieżne wilki, yeti), Qasirah (hieny, skorpion),
  Selvaran (krokodyle, wąż). Ogłoszenie dla wszystkich z kierunkiem ataku, komunikaty o hersztcie i połowie wrogów.
- Każdy pokonany najeźdźca liczy się graczowi; po obronie (albo po 20 minutach) 100 gp za wroga i 1 000 gp
  za herszta na konto, ogłoszenie najlepszego obrońcy. Najeźdźcy się nie odradzają, po czasie odchodzą.
- Administrator: `/inwazja [miasto]` (konsola: `invasion`) uruchamia inwazję od razu.
- Wchodzący do gry w czasie inwazji dostaje informację, ile wrogów zostało.
- 4 nowe testy (liczenie i nagrody, miejsca zbiórki we wszystkich miastach, potwory bez odradzania, sieciowy
  od ogłoszenia do nagrody); łącznie 252.

## [0.30.0] - 2026-10-15

### Dodano
- Bestiariusz: postać liczy pokonane potwory każdego rodzaju (i bossów); zapis `kill.<potwór>`.
- 15 osiągnięć (poziom 20/50/100/300, 100/1 000/10 000 potworów, 10/30 rodzajów, pierwszy boss i 5 bossów,
  5/20 zadań, 1 000 000 gp na koncie, 5 znajomych). Zdobyte raz zostają; wpis "Osiągnięcie zdobyte: ..." w dzienniku.
  Działa w grze lokalnej i online (liczy serwer).
- Okno osiągnięć z postępem (klawisz O, `/osiągnięcia`) przełączane na bestiariusz (`/bestiariusz`). Karta postaci
  pokazuje liczbę pokonanych potworów i osiągnięć.
- `-Dpixel.open=achievements|bestiary`; 2 nowe sceny w regresji GUI (19 scen), wzorzec `character` odświeżony.
- 3 nowe testy osiągnięć i bestiariusza, liczenie w teście zadań; łącznie 248.

## [0.29.0] - 2026-10-15

### Dodano
- Rynek graczy (online, u bankiera w każdym mieście): `/rynek wystaw 5 wilcze futro 300` wystawia przedmioty
  z plecaka za cenę za całość, `/rynek` pokazuje okno (cudze oferty po lewej - dotknięcie kupuje,
  własne po prawej - dotknięcie wycofuje), `/rynek nazwa` szuka, `/rynek kup 12`, `/rynek wycofaj 12`.
  U bankiera nowy wiersz "Rynek graczy".
- Kupujący płaci z konta, a gdy na nim brakuje - monetami z plecaka. Sprzedawca dostaje cenę minus 2% prowizji
  na konto od razu, a gdy jest offline - przy następnym wejściu do gry (z wiadomością).
- Do 10 ofert na gracza; oferty i zaległe wypłaty w `market.txt` na serwerze (zapis atomowy), przetrwają restart.
  Po każdej transakcji zapis postaci (bez podwajania przedmiotów po awarii). Usunięcie konta kasuje jego oferty.
- `-Dpixel.open=market` - podgląd; nowa scena `market` w regresji GUI (17 scen).
- 4 nowe testy (rynek, zapis, komunikat, sieciowy z zakupem od gracza offline); łącznie 245.

## [0.28.0] - 2026-10-15

### Dodano
- Lista znajomych (do 30 postaci): `/znajomi` otwiera okno (w grze po lewej, poza grą po prawej, z profesją
  i poziomem), `/znajomi dodaj Imię`, `/znajomi usuń Imię`. W menu gracza (dotknięcie postaci) nowy wiersz
  "Dodaj do znajomych". Dotknięcie znajomego w grze otwiera czat z gotowym `/w Imię, `.
- Powiadomienia: "Znajomy X jest teraz w grze." / "wyszedł z gry.", a po wejściu lista znajomych online.
- Serwer przyjmuje tylko istniejące postacie (online albo z rankingu); imię zapisuje tak, jak ma je postać.
  Lista zapisuje się z postacią (`fr.<i>`).
- `-Dpixel.open=friends` - podgląd; nowa scena `friends` w regresji GUI (16 scen), wzorzec `player` odświeżony.
- 4 nowe testy (lista, zapis, komunikat, sieciowy); łącznie 241.

## [0.27.0] - 2026-10-15

### Dodano
- Depozyt przedmiotow w banku: 60 miejsc, wspolny dla wszystkich miast (jak konto). U bankiera wiersz
  "Depozyt przedmiotów" przelacza okno na widok depozytu: po lewej "Wyjmij Nx Nazwa", po prawej "Odłóż Nx Nazwa"
  z plecaka (wszystkie sztuki, ile sie zmiesci). Monety nie trafiaja do depozytu - od tego jest konto.
- Depozyt zapisuje sie razem z postacia (`dp.<slot>` w zapisie) i dziala online (akcje `npc:store:<id>`,
  `npc:take:<id>` wykonuje serwer, stan wraca w SELF).
- `-Dpixel.open=depot` - podglad; nowa scena `depot` w regresji GUI (15 scen).
- Test `depotStoresItemsOnlyAtABankerKeepsCoinsOutAndSurvivesSaving`; lacznie 237 testow.

## [0.26.0] - 2026-10-15

### Dodano
- Ranking postaci na serwerze: `/ranking` (cały serwer) albo `/ranking druid|paladin|mag|mnich` otwiera okno
  z 10 pierwszymi miejscami (imie, profesja, poziom) i miejscem gracza. Kolejnosc wg doswiadczenia.
- Ranking obejmuje tez postacie offline: serwer buduje go przy starcie z zapisow, odswieza przy kazdym zapisie,
  usuwa konto po jego skasowaniu.
- `-Dpixel.open=ranking` - podglad okna; nowa scena `ranking` w regresji GUI (14 scen).
- 5 nowych testow, w tym sieciowy (ranking po restarcie serwera, filtr profesji) - lacznie 236.

## [0.25.0] - 2026-10-15

### Zmieniono
- Polskie nazwy wszystkich 108 przedmiotow i 42 potworow (np. Mikstura zdrowia, Wilcze futro, Wilk,
  Grimtooth, Król Kopalni). Klucze danych (`health_potion`, `wolf`) bez zmian, zapisy postaci zgodne.
- Handel i `/oferuj` przyjmuja polska nazwe (z polskimi znakami albo bez) i angielski klucz:
  `/oferuj 5 mikstura zdrowia` = `/oferuj 5 health potion`.
- Waluty: Zlota, Platynowa i Krysztalowa moneta.

## [0.24.0] - 2026-10-15

### Dodano
- Polskie litery w grze: czcionka domyslna libGDX uzupelniona w pamieci o ą ć ę ł ń ś ź ż (i wielkie), skladane
  z liter bazowych, rysowanej kreski / kropki i ogonka z odbitej cedylli (`client/ui/PolishFont`, bez nowych plikow).
  Czat z polskimi znakami wreszcie sie wyswietla.
- Polecenia czatu dzialaja z polskimi znakami i bez (`/zaproś` = `/zapros`, `/gildia załóż` = `/gildia zaloz`),
  handel przyjmuje `złoto`.
- Test `PolishFontTest` (kazda litera istnieje i ma znak diakrytyczny); lacznie 232 testy.

### Zmieniono
- Napisy interfejsu, komunikaty serwera, opisy przedmiotow, potworow, czarow, zadan, NPC i rozmowy z pelnymi polskimi
  znakami (np. UŻYJ, KSIĘGA, ŁÓW, "Zginąłeś", "Wędkarstwo"). Tematy rozmow z NPC wyswietlane z polskimi znakami.
- Wzorce regresji GUI odswiezone (13 scen).

## [0.23.0] - 2026-10-15

### Dodano
- Prywatne wiadomosci: `/w Imie, tekst` (z limitem spamu).
- Moderacja serwera: administratorzy z `admins.txt`, polecenia czatu `/ogloszenie`, `/kick`, `/ban Imie [godziny]`,
  `/unban`, `/wycisz Imie [minuty]`, `/kto` oraz te same polecenia w konsoli serwera (stdin, `docker attach`).
  Bany i wyciszenia zapisywane w `moderation.txt`, przetrwaja restart; zbanowany nie zaloguje sie do konca bana,
  wyciszony nie pisze na zadnym czacie.
- 2 nowe testy, w tym sieciowy (bany po restarcie i wygasanie) (lacznie 231).

## [0.22.0] - 2026-10-15

### Dodano
- Obraz Docker serwera (`server/Dockerfile`, uzytkownik bez uprawnien root, dane w wolumenie `/data`) i instrukcja
  `docs/SERVER.md`: Docker, systemd, kopie zapasowe, bezpieczenstwo, aktualizacja.
- Zadanie CI `server-image`: budowa obrazu, start kontenera, sprawdzenie TLS i restartu z zachowaniem certyfikatu.

## [0.21.2] - 2026-10-15

### Dodano
- Panel druzyny po lewej stronie ekranu: czlonkowie z HP w procentach (zielone / zolte / czerwone), "daleko" gdy
  poza zasiegiem wzroku - leczacy od razu widzi, komu pomoc.
- `-Dpixel.open=party` - podglad panelu; nowa scena `party` w regresji GUI (13 scen).

## [0.21.1] - 2026-10-15

### Zmieniono
- Dotkniecie innego gracza otwiera menu gracza: Handel, Zapros do druzyny, Zapros do gildii, Atakuj / Przerwij atak.
  Atak wymaga swiadomego wyboru (wczesniej dotkniecie od razu atakowalo - latwo bylo przypadkiem dostac czaszke).
  Czlonka wlasnej druzyny nie mozna wybrac do ataku.

### Dodano
- `-Dpixel.open=player` - podglad menu gracza; nowa scena `player` w regresji GUI (12 scen).

## [0.21.0] - 2026-10-15

### Dodano
- Gildie (trwale, zapisywane na serwerze w `guilds.txt`, objete kopiami zapasowymi): `/gildia zaloz Nazwa`
  (50 000 gp z plecaka), `/gildia zapros Imie`, `/gildia przyjmij Nazwa`, `/gildia wyrzuc Imie`, `/gildia opusc`,
  `/gildia` (informacje), czat gildii `/g tekst`. Do 50 czlonkow; po wyjsciu lidera przywodztwo przechodzi na
  najdluzej obecnego czlonka, pusta gildia znika. Nazwa gildii widoczna przy imieniu gracza.
- 4 nowe testy, w tym sieciowy z restartem serwera (lacznie 229).

## [0.20.1] - 2026-10-15

### Dodano
- Okno handlu w interfejsie (wygodne na telefonie): oferty obu stron ze stanem akceptacji, AKCEPTUJ, ANULUJ,
  dodawanie zlota (+100 / +1 000 / +10 000 gp) i przedmiotow z plecaka jednym dotknieciem. Otwiera sie samo po
  rozpoczeciu wymiany (`/handel Imie`), zamkniecie X anuluje wymiane. Serwer wysyla stan wymiany (`TS`).
- `-Dpixel.open=trade` - podglad okna handlu; nowa scena `trade` w regresji GUI (11 scen).
- 1 nowy test, sieciowy test handlu sprawdza tez stan okna (lacznie 225).

## [0.20.0] - 2026-10-15

### Dodano
- Handel miedzy graczami online, twarza w twarz (maks. 2 pola): `/handel Imie` (druga strona odpowiada tym samym),
  `/oferuj 5 health potion`, `/oferuj 1000 gp`, `/akceptuj`, `/anuluj`. Kazda zmiana oferty kasuje akceptacje,
  wymiana dzieje sie po akceptacji obu stron atomowo (albo cala, albo wcale), a potem oba zapisy postaci sa zapisywane.
  Wymiane przerywa oddalenie, wylogowanie albo 3 minuty bez zmian.
- 4 nowe testy, w tym sieciowy (lacznie 224).

## [0.19.0] - 2026-10-15

### Dodano
- Druzyny online (do 8 osob): `/zapros Imie`, `/przyjmij Imie` (zaproszenie wazne 60 s), `/opusc`, czat druzyny `/d tekst`.
- Wspolne doswiadczenie: gdy co najmniej dwoch czlonkow jest na tym samym pietrze w promieniu 30 kafelkow i poziomy sa
  zblizone (najnizszy >= 2/3 najwyzszego), kazdy dostaje (1 + 20% za kazdego kolejnego) / liczba osob doswiadczenia,
  jakie zdobylby sam: 2 osoby po 60%, 4 po 40%. Wysoki poziom nie "wyholuje" nowej postaci.
- Czlonkowie druzyny nie moga sie atakowac; ich imiona sa niebieskie z dopiskiem [druzyna].
- 5 nowych testow, w tym sieciowy (lacznie 220).

## [0.18.1] - 2026-10-15

### Dodano
- Pomiar plynnosci: klient co 30 s zapisuje w logu FPS, mediane, 95. percentyl i najdluzsza klatke
  (na Androidzie logcat, tag `Pixel`).
- Test APK na emulatorze Androida w CI (zadanie `android-smoke`): instalacja, start gry z pominieciem menu,
  75 s gry, zrzut ekranu i logcat jako artefakt. Ten sam skrypt `tools/android-smoke/run.sh` dziala z telefonem przez adb.
- APK debug przyjmuje `--es autostart Imie:PROFESJA` (tylko wersja debug).
- 1 nowy test (lacznie 215).

## [0.18.0] - 2026-10-15

### Dodano
- Dzwieki: 13 efektow syntezowanych w kodzie (trafienie, rana, blok, strzal, leczenie, szesc zywiolow czarow,
  awans, smierc). Graja przy zdarzeniach walki, ciszej z daleka, takze online.
- Ustawienie glosnosci w panelu ustawien: wylaczony / 30 / 60 / 100%.
- Animacja ataku wrecz: atakujacy (gracz, potwor, inny gracz) robi krotki wypad w strone celu, na celu widac ciecie.
- 5 nowych testow (lacznie 214).

## [0.17.1] - 2026-10-15

### Dodano
- Wyzyny poza wieza Varden: Mrozne Turnie (pietra +1..+3, poziomy 40-90, wilki sniezne, yeti, lodowe wiedzmy)
  i Plaskowyz Qasirah (pietra +1..+2, poziomy 45-130, skorpiony, hieny, piaskowe upiory). Wejscia po
  schodach z powierzchni, generowane deterministycznie.
- Generator mapy zapisuje teraz takze nocne spawny (osme pole "noc"), wiec `spawns.csv` nie wymaga recznych zmian.

### Poprawiono
- Niestabilny test sieciowy: losowe haslo magazynu TLS zaczynajace sie od "-J" bylo czytane przez keytool jako
  opcja JVM (czerwone CI dla 2cdebc8). Haslo ma teraz staly prefiks.

## [0.17.0] - 2026-10-15

### Dodano
- Ciala potworow: lup, ktorego nie zabral autoloot, czeka w ciele do przeszukania (UZYJ, PODNIES albo dotkniecie
  obok). Cialo znika po 3 minutach. Widoczne takze online.
- Przedmioty na ziemi znikaja po 30 minutach (serwer nie gromadzi ich bez konca).
- 4 nowe testy, w tym sieciowy (lacznie 209).

## [0.16.2] - 2026-10-15

### Dodano
- Wlasny uklad paska czarow: w ksiedze "Ustaw pasek czarow", potem dotkniecie czaru; zapisywany osobno dla
  kazdej postaci. Puste przyciski dzialaja jak dotad automatycznie.
- 2 nowe testy (lacznie 206).

### Poprawiono
- Regresja GUI izoluje katalog domowy Javy (`-Duser.home`), wiec zapamietany login i ustawienia z komputera
  nie wplywaja na zrzuty. Wzorce odswiezone.

## [0.16.1] - 2026-10-15

### Dodano
- Efekty graficzne: pociski czarow, run i atakow dystansowych (takze potworow), fala czarow obszarowych,
  iskry leczenia. Kolory wg zywiolu, uproszczone w trybie oszczedzania baterii. Dzialaja takze online.
- `-Dpixel.fx=true` - podglad efektow na zrzucie ekranu.
- Testy czarow sprawdzaja emitowane efekty (lacznie 204).

## [0.16.0] - 2026-10-15

### Dodano
- Zadania wieloetapowe z nagrodami w przedmiotach (`data/quest_chains.csv`): przemytnicy Varden (3 etapy),
  zaginiona wyprawa w Selvaran (2 etapy), zamiec we Frostholm. Nastepny etap pojawia sie po ukonczeniu
  poprzedniego; przy pelnym plecaku nagroda czeka.
- Rozmowa z NPC slowami kluczowymi (`data/dialogues.csv`): tematy w oknie rozmowy i czat obok NPC
  (takze w grze lokalnej), z tolerancja polskich znakow i odmiany. Wskazowki o miastach, lochach, rybach i nocy.
- Wzorce regresji GUI zaktualizowane (przycisk CZAT obok NPC, tematy w oknie kupca).
- 6 nowych testow (lacznie 204).

## [0.15.3] - 2026-10-15

### Dodano
- Nocne spawny: o zmroku na powierzchni pojawia sie 6 dodatkowych grup nocnych drapieznikow (wilki i bandyci
  przy Varden, sniezne wilki, hieny, piaskowe upiory, upiorni rycerze). Za dnia znikaja, ale nigdy na oczach
  gracza ani w trakcie walki. Spawny przy Varden nie przekraczaja poziomu 15.
- 2 nowe testy (lacznie 198).

## [0.15.2] - 2026-10-15

### Dodano
- Ryby zalezne od biomu (`data/fishing.csv`): lodowy dorsz we Mroznych Pustkowiach, wegorz bagienny w dzungli
  Selvaran (lowi sie z bagien), karp z oazy w Qasirah. Wymagaja progu umiejetnosci, dluzej syca,
  skupuje je lokalny kupiec. Ryby maja w plecaku wlasna ikone.
- Test ryb biomow na prawdziwej mapie (lacznie 196 testow).

## [0.15.1] - 2026-10-15

### Dodano
- Automatyczna regresja GUI: `tools/gui-regression/run.sh` robi zrzuty 10 kluczowych scen (menu, logowanie,
  Varden w dzien i w nocy, plecak, ksiega czarow, karta postaci, sklep, kopalnia, oboz na wschodzie)
  i porownuje je z wzorcami (polowa rozdzielczosci, tolerancja koloru, prog 3% pikseli). Nowe zadanie CI.
- `-Dpixel.seed` (stale ziarno potworow) i `-Dpixel.open=diag` w launcherze desktopowym.
- 2 nowe testy porownywarki (lacznie 195).

## [0.15.0] - 2026-10-15

### Dodano
- Ustawienia gracza pod panelem diagnostyki (`i` / F3), zmieniane dotknieciem: limit FPS 30/60/90/120,
  oszczedzanie baterii (30 FPS i swiatlo w polowie jakosci), jakosc swiatla, liczby obrazen.
- Ustawienia zapisywane w Preferences `pixel-settings`, bledne wartosci wracaja do domyslnych.
  Ochrona zasobow 90%/75% moze je tylko dodatkowo obnizyc.
- 2 nowe testy (lacznie 193).

## [0.14.1] - 2026-10-15

### Dodano
- Zmiana hasla (`/haslo stare nowe`) i usuniecie konta wraz z postacia (`/usun-konto haslo`) z okna czatu;
  hasla nie trafiaja na czat. Te same blokady po blednych probach co przy logowaniu.
- Limit 5 nowych kont na dobe z jednego adresu IP.
- 3 nowe testy (lacznie 191).

## [0.14.0] - 2026-10-15

### Dodano
- PvP czarami i runami: ofensywne czary i runy trafiaja gracza wybranego jako cel PvP (gdy nie ma celu-potwora),
  z tymi samymi zasadami co atak (ochrona do poziomu 30, PZ, roznica poziomow, czaszki, blokada PZ, obrazenia x0,5).
  Gdy zasady odmawiaja, mana i runa nie sa zuzywane.
- Test PvP czarami (lacznie 188 testow).

## [0.13.2] - 2026-10-15

### Dodano
- PvP: po kazdym ataku na gracza strefa ochronna jest zamknieta dla obu stron przez 60 s (takze schodami).
- Czaszki i nieusprawiedliwione zabojstwa zapisywane na dysku serwera - przetrwaja restart.
- Protokol 3: niezmieniona klatka stanu wysylana jako krotkie `KEEP` (mniej transferu na telefonie).
- Test budzetu wydajnosci serwera: 200 graczy na calej mapie, ok. 0,5 ms na tik AI.
- 4 nowe testy (lacznie 187).

## [0.13.1] - 2026-10-15

### Dodano
- Oboz Zwiadowcow za mostem: kwatermistrz (mikstury, skup materialow i sprzetu endgame), skarbniczka
  (wspolne konto bankowe) i zwiadowczyni z 9 zadaniami dla poziomow 230-900 (w tym na 4 bossow).
  Kaplana nie ma - oboz nie jest miastem, odrodzenie zostaje w swiatyni.
- Najsilniejsze mikstury: Supreme Health Potion (800-1100 HP, paladyn i mnich) i Supreme Mana Potion
  (400-600 many), od poziomu 200.
- 28 receptur kuzni endgame: kazdy element sprzetu progow 250/450/700/850 (bez korony Otchlani) z materialow
  swojego progu i poprzedniego oraz zlota. Wykucie i sprzedaz zawsze sie nie oplaca.
- Ksiega rzemiosla pokazuje receptury do poziomu postaci + 100 i ma strony.

### Naprawiono
- Test sieciowy walki teleportuje postac obok zywego wilka (wczesniej czasem czekal na wilka, ktory odszedl).

## [0.13.0] - 2026-10-15

### Dodano
- Endgame dla poziomow 230-1000: wschodni kontynent za ciesnina (mapa 256 x 232), most ze straznikiem
  wpuszczajacym dopiero od poziomu 200 i oboz (strefa ochronna) za mostem.
- 5 krain: Przeklete Knieje (230-300), Spalone Wyzyny (300-400, krater Ignarotha), Lodowe Szczyty
  (400-560, wnetrze gor +1..+2 z Hrimgarem), Pustkowia Cienia (550-750, ruiny Kha'zuula) i Otchlan
  (pietra -1..-5, 750-1000, Mor'gath na -5).
- 17 potworow (w tym 4 bossow dla druzyn), 33 przedmioty: 4 komplety broni (poziom 250/450/700/850),
  3 komplety zbroi, korona Otchlani, 4 materialy; lupy dla wszystkich nowych potworow.
- Nowe kafelki: przekleta trawa, popiol, lawa, ciemny piasek, kosci, martwe drzewa, obsydian, posadzka Otchlani.
- Przejscia strzezone poziomem (`gates=` w map.properties), sprawdzane takze przy przewidywaniu ruchu online.
- Symulacje endgame z leczeniem (`EndgameSimulationTest`), testy straznika mostu (lacznie 181 testow).

### Zmieniono
- Generator mapy: zachodni kontynent losowany ta sama sekwencja co wczesniej (identyczny poza przedluzeniem
  wschodniego traktu na most).

## [0.12.1] - 2026-10-14

### Dodano
- Odpornosc serwera: wysylka do kazdego klienta w osobnym watku (wolny telefon nie spowalnia swiata),
  limit 300 polaczen i 8 z jednego adresu, 40 wiadomosci/s (zapas 80), linia do 2048 znakow,
  15 s na zalogowanie, czat najwyzej 5 wiadomosci na 10 s.
- Kopie zapasowe katalogu serwera (zip w `backups/`) przy starcie i co 6 h, 7 najnowszych.
- 3 nowe testy (lacznie 174).

## [0.12.0] - 2026-10-14

### Dodano
- Szyfrowanie TLS miedzy klientem a serwerem. Serwer przy pierwszym starcie tworzy wlasny certyfikat
  (w katalogu zapisow, poza repozytorium) i wypisuje jego odcisk.
- Klient zapamietuje odcisk certyfikatu przy pierwszym polaczeniu; gdy sie zmieni, odmawia polaczenia
  przed wyslaniem hasla i proponuje "Zapomnij certyfikat" (na wypadek przeinstalowania serwera).
- Opcja serwera `--no-tls` (tylko testy).
- 1 nowy test TLS (lacznie 171); recznie sprawdzone pierwsze polaczenie i podmieniony certyfikat.

## [0.11.1] - 2026-10-14

### Dodano
- Przewidywanie ruchu w kliencie online (`game.MovePredictor`): postac rusza od razu, serwer tylko
  potwierdza kroki; przy blokadzie, teleporcie lub smierci obowiazuje pozycja serwera.
  Licznik poprawek w panelu diagnostyki (F3 / przycisk i).
- 2 testy symulujace opoznienie sieci (lacznie 170).

### Naprawiono
- Serwer zapisuje postac przed usunieciem gracza z listy (niestabilny test sieciowy).
- Test wspolnych potworow nie zalezy juz od tego, czy obie klatki pochodza z tego samego kroku.

## [0.11.0] - 2026-10-14 - etap 14: konta, logowanie, czat

### Dodano
- Menu startowe: "Graj lokalnie" (zapis na urzadzeniu) albo "Graj online".
- Ekran logowania (adres serwera, imie, haslo, profesja nowego konta) - dziala tez na Androidzie,
  wiec APK moze grac online; adres i imie sa zapamietywane, haslo nigdy.
- Konta na serwerze: pierwsze logowanie zaklada konto, haslo przechowywane tylko jako skrot
  PBKDF2-HMAC-SHA256 z losowa sola; 5 blednych prob = blokada na minute. Protokol w wersji 2.
- Czat: przycisk CZAT (Android - okno wpisywania), Enter na komputerze.
- Po utracie polaczenia klient wraca do menu z komunikatem.
- Uprawnienie INTERNET w APK.
- 1 nowy test kont przez TCP (lacznie 168); logowanie i czat sprawdzone recznie wpisywaniem z klawiatury.

### Jeszcze nie
- Polaczenie nie jest szyfrowane (TLS) - serwera nie nalezy wystawiac publicznie.

## [0.10.0] - 2026-10-14 - etap 13: PvP

### Dodano
- Walka gracz kontra gracz na serwerze: dotkniecie innego gracza rozpoczyna atak, drugie dotkniecie go konczy.
- Ochrona: do poziomu 30 nie mozna atakowac graczy ani byc atakowanym; w strefie ochronnej nie ma walki;
  nie mozna atakowac postaci o ponad polowe slabszej (chyba ze ma czaszke).
- Czaszki: biala za atak na niewinnego (15 min), czerwona za 3 nieusprawiedliwione zabojstwa w ciagu 24 h (6 h).
  Smierc z czerwona czaszka zostawia caly plecak na ziemi. Zabijanie graczy nie daje doswiadczenia.
- Imiona graczy z czaszka sa biale lub czerwone, atakowany gracz ma czerwona ramke.
- 6 nowych testow (lacznie 167), w tym symulacja pojedynkow wszystkich par profesji.

## [0.9.0] - 2026-10-14 - etap 14: serwer i tryb online

### Dodano (czesc 2: klient online)
- Klient libGDX laczy sie z serwerem (`-Dpixel.server=host:port`): ruch i akcje ida na serwer,
  ekran pokazuje stan z serwera - postac, potwory, innych graczy (z imionami i % zdrowia),
  przedmioty na ziemi, liczby obrazen, dziennik i czat. Bez serwera gra przechodzi w tryb lokalny.
- Zegar gry zgrany z czasem serwera; zapis postaci online robi tylko serwer.
- Gracze logujacy sie w to samo miejsce dostaja wolne pole obok (nie stoja jeden na drugim).
- 4 nowe testy (lacznie 161): replika klienta (stan postaci, pozycja, potwory) i przedmioty/zdarzenia walki przez TCP.

### Dodano (czesc 1: serwer)

### Dodano
- Modul `server`: autorytatywny serwer gry (TCP, protokol tekstowy `net.Protocol`), jeden wspolny swiat:
  potwory i przedmioty na ziemi wspolne dla wszystkich graczy, petla 20 krokow/s, stan 10 razy/s.
- Logowanie imieniem i profesja, zapis postaci na serwerze (co minute i przy wyjsciu), odrzucanie
  podwojnych logowan i zlej wersji protokolu, czat.
- `NetClient` (bez libGDX) i `Actions` - te same akcje co w HUD wykonywane na serwerze.
- Potwory poluja na najblizszego z wielu graczy.
- 4 nowe testy, w tym 3 przez prawdziwe polaczenia TCP (lacznie 157).

### Jeszcze nie
- Brak kont z haslem, szyfrowania i PvP; na Androidzie nie ma jeszcze ekranu wpisania adresu serwera
  (APK gra lokalnie), brak okna czatu do pisania (czat jest odbierany i wyswietlany).

## [0.8.0] - 2026-10-14

### Dodano
- NPC w czterech miastach: kupcy, bankierzy, kaplani i przewodnicy z zadaniami.
- Handel: kupno w ilosciach 1/5/25/100, sprzedaz lupow, ceny w data/shops.csv.
- Bank w grze (wplaty i wyplaty), leczenie u kaplana i wybor miasta odrodzenia.
- 13 zadan (zabij potwory / przynies lupy) z nagrodami exp i zlota; postep jest zapisywany.
- Okno rozmowy z NPC, imiona nad NPC, przycisk ROZMOWA.
- 6 nowych testow (lacznie 153), w tym kontrola, ze handlem, rzemioslem i zadaniami nie da sie "drukowac" zlota.

## [0.7.0] - 2026-10-14

### Dodano
- Noc wplywa na potwory: wilki, hieny, bandyci i nieumarli w nocy widza dalej i atakuja szybciej.
- Komunikaty o zapadajacej nocy i wschodzie dnia.
- Opcja deweloperska `-Dpixel.hour` (godzina gry na starcie).
- 5 nowych testow (lacznie 147).

### Zmieniono
- Zegar swiata jest czescia logiki gry (przygotowanie pod czas z serwera).
- Ostrzezenie o niebezpiecznym pietrze bierze pod uwage tylko okolice, a nie caly swiat.

## [0.6.0] - 2026-10-14

### Dodano
- Lochy biomow siegaja pietra -5: dziury, drabiny i miejsca, z ktorych wychodzi sie tylko z lina.
- Trzej bossowie: Frost Queen Ysolde (150), Ruin Colossus (200), Pharaoh Neferakh (230) i ich lupy.
- Nowe zrodla swiatla: swiecacy mech, magma, swiatlo nad miejscem na line.
- Pochodnie sie wypalaja (30 minut), czas widac na karcie postaci.
- 6 nowych testow (lacznie 142).

### Zmieniono
- Sytosc, swiatlo z czaru i zapalona pochodnia sa zapisywane razem z postacia.

## [0.5.0] - 2026-10-14

### Dodano
- Swiat z czterema miastami: Frostholm (snieg), Varden (start), Selvaran (dzungla) i Qasirah (pustynia),
  polaczonymi drogami; mapa 160 x 232 na 11 pietrach.
- Lochy: Lodowe Jaskinie, Zatopione Ruiny, Grobowce Qasirah.
- 12 potworow biomow (poziomy 40-200), nowe materialy, bron i zbroje na poziom 40-100.
- Regiony z zalecanym poziomem i ostrzezeniem przy wejsciu na zbyt niebezpieczny teren.
- Nowe kafelki: lod, bagno, piaskowiec, sosny, palmy, kaktusy, sciany lodowe i z piaskowca.
- 7 nowych testow swiata (lacznie 136).

### Zmieniono
- Zapis postaci w wersji 3; stare zapisy sa przenoszone na nowa mape automatycznie.
- Odrodzenie po smierci w swiatyni miasta postaci.
- Generator map nie stawia dekoracji, ktore odcinalyby fragment mapy (naprawiony niedostepny spawn szkieletow).

## [0.4.0] - 2026-10-14

### Dodano
- Lowienie ryb: wedka, robaki, szansa rosnaca z umiejetnoscia, odpoczynek lowiska, rzadkie perly.
- Karta postaci z umiejetnosciami i postepem, sytoscia i liczba zabitych potworow.
- 7 nowych testow (lacznie 129).

### Zmieniono
- Umiejetnosci walki i tarczy nie rosna na zbyt slabych potworach.

## [0.3.0] - 2026-10-14

### Dodano
- Czary dla 4 profesji: leczenie (Druid najsilniejsze), ataki na cel (ogien, energia, lod, ziemia,
  swiete, fizyczne), ataki obszarowe, poswiata, przywolanie wloczni, zaklinanie run.
- Runy glazu, wybuchu ognia i leczenia; mikstury zdrowia i many (takze silne od poziomu 50);
  jedzenie (chleb, mieso, ryba) podwajajace regeneracje.
- Crafting: 8 receptur z materialow z lupow (zbroje, miecz, tarcza, wlocznie, puste runy, pochodnie).
- Trening poziomu magii, czasy odnowienia czarow i grup, komunikaty o bledach (mana, poziom, zasieg...).
- Klient: pasek czarow z odnowieniem, przyciski HP/MP z licznikiem, ksiega czarow i rzemiosla,
  przycisk UZYJ w plecaku, skroty 1-4, Q, F, K.
- 16 nowych testow (lacznie 122), w tym symulacje z botem leczacym sie czarami i miksturami.

### Zmieniono
- Grimtooth uderza mocniej (40-160): bez tego postac na poziomie 8 mogla go pokonac zapasem mikstur.
- Potwory trafione z daleka (czar, runa, dystans) od razu ruszaja na gracza.

## [0.2.0] - 2026-10-14

### Dodano
- Potwory: wilk, dzik, lesny pajak, bandyta, goblin, goblin szaman, szkielet, troll jaskiniowy,
  krysztalowy golem i boss Grimtooth (dane w `monsters.csv`, lupy w `loot.csv`, spawny w `spawns.csv`).
- AI potworow: aggro, poscig, ucieczka (osaczony walczy dalej), utrzymywanie dystansu, leczenie
  sojusznikow, powrot na spawn, regeneracja poza walka, mechaniki bossa.
- Walka: wybor celu, atak automatyczny, wrecz / dystans / magia, pancerz, tarcza, odpornosci,
  postawa obronna, automatyczne przelaczenie na atakujacego, gdy cel ucieknie poza zasieg.
- Smierc gracza z kara 5% exp i odrodzeniem w swiatyni; ostrzezenie przy wejsciu na zbyt trudne pietro.
- Lupy z autolootem; gdy plecak pelny, lup zostaje na ziemi.
- Klient: grafika potworow, paski zycia, obwodka i panel celu, liczby obrazen, przyciski CEL i TARCZA.
- 30 nowych testow (lacznie 106): AI, walka, symulacje profesji, bezpieczenstwo poczatkujacych.

### Zmieniono
- Obrazenia magiczne rosna szybciej z poziomem magii (dzielnik 25 zamiast 40) - wynik symulacji.
- Stado wilkow pod Varden zmniejszone z 3 do 2 (nowa postac stojaca w miejscu ginela po ~80 s).

## [0.1.0] - 2026-10-14

Pierwsza wersja projektu (repozytorium bylo puste).

### Dodano
- Projekt Gradle: moduly `shared` (logika), `core` (klient libGDX 1.13.5), `desktop` (LWJGL3), `android`.
- Postac: start na poziomie 8 z 10 000 gp w plecaku, krzywe doswiadczenia i statystyk do poziomu 1000.
- Profesje Paladin, Druid, Sorcerer i Monk z roznymi profilami i zestawami startowymi.
- Przedmioty z pliku danych, plecak 20 slotow, ekwipunek, wyrzucanie i podnoszenie, autoloot (lista ignorowanych).
- Waluty Gold / Platinum / Crystal, wymiana, platnosci z reszta, bank.
- Mapa kafelkowa z warstwami, kolizje, pietra -5..+5 (wieza i kopalnia w Varden), schody, dziury, drabiny.
- Miasto startowe Varden z okolica (generator `tools/mapgen/generate_varden.py`).
- Ruch w 8 kierunkach, joystick, HUD (HP, MP, exp, zloto, pora dnia, dziennik), okno plecaka.
- Zegar swiata (dzien i noc), oswietlenie podziemi, pochodnie, latarnie i ogniska.
- Ochrona zasobow 90% / 75% (NORMAL / LIMITED / RECOVERING) i panel diagnostyki.
- Bezpieczny zapis postaci (CRC32, wersje, migracja, kopia zapasowa), autozapis.
- 76 testow JUnit, CI w GitHub Actions z budowa APK.
