# Changelog

## [0.181.0] - 2026-10-21

### Dodano
- Nowa grafika: 180 obrazków z OpenTibia Sprite Pack (darmowe grafiki społeczności OpenTibia, licencja CC BY 4.0):
  trawa, piasek, bruk, woda i inne podłoża, drzewa i kamienie, gobliny, dziki, szkielety i 19 innych potworów,
  wszyscy NPC, postacie czterech profesji w 4 kierunkach, broń, zbroje, mikstury i monety.
- Grafiki 32×32 są wyświetlane w pełnej rozdzielczości (osobny atlas), więc nie tracą szczegółów.
- /autorzy: lista autorów grafik w grze (pełna lista też w docs/CREDITS.md).
## [0.180.0] - 2026-10-21

### Dodano
- Grafiki od grafików: plik PNG w `assets/art/` (np. `monsters/wolf.png`, `characters/paladin_down.png`) zastępuje
  obrazek rysowany przez kod, a brakujące zostają takie jak dotąd. Pełna lista 278 nazw: docs/GRAPHICS.md.
## [0.179.0] - 2026-10-21

### Dodano
- Rzut kostką: /kostka (k6) albo /kostka 20. Wynik losuje serwer, więc nikt go nie podrobi, a widzą go wszyscy
  w dymku nad postacią. Kostka jest też w oknie „Emotki”.
## [0.178.0] - 2026-10-21

### Dodano
- Osiągnięcie „Zielarz” za zebranie 50 ziół leczniczych (daje też tytuł). Liczbę zebranych ziół widać w /statystyki.
## [0.177.2] - 2026-10-21

### Dodano
- Pułapki z kolcami widać na minimapie i dużej mapie jako pomarańczowe punkty.
## [0.177.1] - 2026-10-21

### Zmieniono
- Marsz po dotknięciu mapy, znacznika albo łowiska omija pułapki z kolcami, jeśli jest inna droga.
## [0.177.0] - 2026-10-21

### Dodano
- Pułapki z kolcami w głębszych lochach (piętra -2 do -5): metalowe płyty w korytarzach. Wejście na płytę zabiera
  5% zdrowia na każde piętro głębokości (na -5 to 25%), ale pułapka nigdy nie zabija. Widać je na mapie, więc
  można je omijać.
## [0.176.0] - 2026-10-21

### Dodano
- Zbieranie ziół: UŻYJ obok krzaka (wokół Varden jest ich 18) daje 1–2 ziela lecznicze, a krzak odrasta po
  10 minutach. Z 4 ziół i 10 gp wytworzysz w księdze miksturę zdrowia; ziele skupuje też kupiec w Varden.
## [0.175.0] - 2026-10-21

### Dodano
- Okno „Emotki” (/emotki albo z listy /pomoc): dotknięcie emotki od razu ją wykonuje, bez pisania na telefonie.
## [0.174.1] - 2026-10-21

### Dodano
- Emotki poruszają postacią: przy /taniec i /hura podskakuje, przy /machaj, /brawo i /śmiech lekko się kołysze,
  a przy /ukłon, /siad i /zzz przykuca. Ruch widać też u innych graczy, dopóki wisi dymek.
## [0.174.0] - 2026-10-21

### Dodano
- Emotki na czacie: /taniec, /machaj, /ukłon, /śmiech, /brawo, /hura, /płacz, /siad, /zzz oraz /ja tekst.
  Nad postacią pojawia się niebieski dymek, np. „* tańczy *”, widoczny dla innych graczy online.
## [0.173.0] - 2026-10-21

### Dodano
- Przycisk ZACHOWAJ pod zaznaczonym przedmiotem w plecaku. Zachowany rodzaj przedmiotu ma złoty róg slotu i nie
  zostanie sprzedany ani odłożony do depozytu jednym dotknięciem („cały łup”) ani wyrzucony przypadkiem.
  ODBLOKUJ zdejmuje oznaczenie. Oznaczenia zapisują się z postacią, także na serwerze.
## [0.172.0] - 2026-10-21

### Dodano
- Przycisk SORTUJ w plecaku: łączy rozrzucone stosy (np. mikstury) i układa przedmioty według rodzaju
  (monety, broń, tarcze, zbroje, amunicja, mikstury...), lepiej ulepszone najpierw. Działa też w grze online.
## [0.171.0] - 2026-10-21

### Dodano
- Czary na poziom 100, po jednym dla profesji: Meteor (czarodziej), Śnieżyca (druid), Ostrze światła (paladyn)
  i Pięść feniksa (mnich). Dotąd najsilniejsze czary kończyły się na poziomie 60.
## [0.170.1] - 2026-10-21

### Dodano
- Dotknięcie potwora w oknie "Gdzie polować" prowadzi postać do jego najbliższego legowiska (albo mówi, w której
  krainie go szukać, gdy jest daleko).
## [0.170.0] - 2026-10-21

### Dodano
- "Gdzie polować?" u przewodnika i `/łowiska`: lista potworów dobranych do poziomu postaci z doświadczeniem
  i krainami, w których żyją (potwór dnia oznaczony).
## [0.169.0] - 2026-10-21

### Dodano
- Okno Wydarzenia pokazuje miejsce w rankingu tygodnia, zdobyte doświadczenie i ile zostało do nagród.
## [0.168.0] - 2026-10-20

### Dodano
- Nagrody rankingu tygodnia: w poniedziałek trzy osoby z największym przyrostem doświadczenia dostają 100 000,
  50 000 i 25 000 gp na konto (nieobecni po wejściu do gry), a serwer ogłasza podium wszystkim graczom.
## [0.167.0] - 2026-10-20

### Dodano
- Ranking tygodnia: `/ranking tydzień` pokazuje, kto zdobył najwięcej doświadczenia od poniedziałku. W każdym tygodniu
  wszyscy zaczynają od zera, więc nowi gracze też mają szansę na podium.
## [0.166.2] - 2026-10-20

### Naprawiono
- Sklep: włócznie do rzucania pokazywały "atak +20" (porównanie z pustym miejscem na amunicję). Teraz: "amunicja, atak 20".
## [0.166.1] - 2026-10-20

### Testy
- Nowa scena regresji GUI `shopgear`: strona sprzętu u kupca z porównaniem do założonego ("atak -11, obrona +8", "nie dla ciebie").
## [0.166.0] - 2026-10-20

### Dodano
- Administracja: `reports` w konsoli albo `/zgłoszenia` w grze pokazuje 5 ostatnich zgłoszeń graczy.
## [0.165.0] - 2026-10-20

### Dodano
- `/zgłoś Imię, powód`: zgłoszenie gracza do administracji. Administratorzy w grze widzą je od razu, a serwer zapisuje
  wszystkie w `reports.txt`. Jedno zgłoszenie na minutę.
## [0.164.0] - 2026-10-20

### Dodano
- Potężne mikstury zdrowia (420-560) i many (240-330) od poziomu 100, między silnymi a najwyższymi: w sklepach
  Mroźnych Pustkowi, Selvaran, Qasirah i obozu, czasem z Strażnika świątyni i Mumii. Przyciski HP/MP biorą je same.
## [0.163.0] - 2026-10-16

### Dodano
- Oglądanie innego gracza: `/obejrzyj Imię` albo "Obejrzyj" po dotknięciu postaci pokazuje profesję, poziom, gildię,
  tytuł i założony sprzęt z ulepszeniami. Oglądany dostaje o tym informację.
## [0.162.0] - 2026-10-16

### Dodano
- `/gildia członkowie`: lista członków gildii z poziomami - najpierw obecni w grze, przy pozostałych kiedy ostatnio grali.
## [0.161.0] - 2026-10-16

### Dodano
- Znajomi: przy osobach poza grą widać, kiedy ostatnio grały ("3 h temu", "5 dni temu").
## [0.160.0] - 2026-10-16

### Dodano
- Serwer: polecenie konsoli `shutdown 5` wyłącza serwer po odliczaniu z ogłoszeniami na czacie (30 min ... 10 s),
  zapisuje wszystkie postacie i kończy proces, więc aktualizacja na VPS nie przerywa nikomu walki bez ostrzeżenia.
  `shutdown -` odwołuje.
- Test pilnujący, by klient nie używał `Stream.toList()`, `String.formatted()` ani `Files.readString/writeString`
  (brak na Androidzie 11).
## [0.159.0] - 2026-10-16

### Dodano
- Sklep: przy broni i zbroi widać, co zmieni się względem założonego przedmiotu (np. "atak +5, pancerz -1"),
  czy profesja może go nosić i od którego poziomu.
## [0.158.0] - 2026-10-16

### Dodano
- 7 receptur dla poziomów 20-70 z materiałów krain: kastety, futrzana zbroja, tarcza z piaskowca, nogawice
  z krokodyla, wężowa włócznia, pustynne kastety i hełm skarabeusza. Dotąd rzemiosło miało dziurę między
  poziomem 15 a 250.
## [0.157.0] - 2026-10-16

### Dodano
- Android: przycisk Wstecz zamyka otwarte okno (mapa, rozmowa, plecak, karta postaci, osiągnięcia...).
  Gdy nic nie jest otwarte, dwukrotne Wstecz w ciągu 2 sekund wychodzi z gry zamiast zamykać ją od razu.

### Naprawiono
- Esc przy otwartym oknie osiągnięć, rankingu albo rynku otwierał księgę czarów zamiast zamknąć okno.
## [0.156.0] - 2026-10-16

### Zmieniono
- Rozmowa z przewodnikiem jest czytelniejsza: najpierw zadania do oddania, w toku i dostępne, z przyszłych
  tylko najbliższe, a ukończone zebrane w jeden wiersz. Wróciły ukryte wcześniej tematy rozmowy.
## [0.155.0] - 2026-10-16

### Dodano
- Znacznik „Oddaj zadanie” na mapie: gdy zadanie dnia albo zlecenie łowcy jest wykonane, mapa wskazuje
  najbliższego przewodnika.
## [0.154.0] - 2026-10-16

### Dodano
- Znacznik „Łowisko” na minimapie i dużej mapie: najbliższe miejsce potworów z zadania dnia albo zlecenia łowcy.

### Naprawiono
- Dziennik zdarzeń nie zasłania już tytułu dużej mapy.
## [0.153.0] - 2026-10-16

### Dodano
- Okno /wydarzenia pokazuje w wierszu „Premia exp” także sztandar Twojej gildii i jego pozostały czas.
## [0.152.0] - 2026-10-16

### Dodano
- Sztandar gildii: lider za 100 000 gp ze skarbca daje wszystkim członkom +10% doświadczenia za potwory
  na godzinę (`/gildia sztandar`; kolejny przedłuża czas).
## [0.151.0] - 2026-10-16

### Dodano
- Wiadomość prywatna (`/w Imię, tekst`) do gracza, którego nie ma w grze, czeka jako list i pokazuje się
  po jego wejściu (do 20 listów na gracza).
## [0.150.0] - 2026-10-16

### Dodano
- Osiągnięcia „Kronikarz” (40 różnych potworów) i „Legenda krainy” (40 zadań). Razem 29 osiągnięć.
## [0.149.0] - 2026-10-16

### Dodano
- `/cena` w grze sieciowej podaje też ceny z rynku graczy: ostatnią sprzedaż, najtańszą ofertę i ile płacą
  kupujący w zamówieniach (za sztukę).
## [0.148.0] - 2026-10-16

### Dodano
- Zmiana zadania dnia u przewodnika: raz dziennie, za 50 gp za poziom postaci, losuje innego potwora
  (postęp od zera).
## [0.147.0] - 2026-10-16

### Zmieniono
- Okno /pomoc podpowiada `/szukam` (drużyna) i `/kupię` (zamówienia kupna u bankiera).
## [0.146.0] - 2026-10-16

### Dodano
- `/szukam` (gra sieciowa): ogłoszenie, że szukasz drużyny. Gracze o podobnym poziomie bez drużyny dostają
  wiadomość z poleceniem `/zaproś`, a lista `/gracze` pokazuje „szuka drużyny”.
## [0.145.0] - 2026-10-16

### Dodano
- Nagłówek bestiariusza pokazuje, ile gatunków potworów już poznano spośród wszystkich w grze.
## [0.144.0] - 2026-10-16

### Dodano
- Wibracje na telefonie przy alarmie niskiego zdrowia (ustawienia, strona 2; domyślnie włączone).
## [0.143.0] - 2026-10-16

### Dodano
- Licznik rzadkich łupów (w /statystyki) i dwa osiągnięcia: „Szczęściarz” (pierwszy rzadki łup)
  i „Ulubieniec losu” (10 rzadkich łupów). Razem 27 osiągnięć.
## [0.142.1] - 2026-10-16

### Naprawiono
- Niestabilny test sieciowy nagród za głowy (CI 0.140.0): cel regenerował się między chybionymi strzałami.
## [0.142.0] - 2026-10-16

### Zmieniono
- Zamówienia kupna wygasają po 7 dniach, a zablokowane złoto wraca na konto (graczom offline przy następnym wejściu).
## [0.141.0] - 2026-10-16

### Dodano
- Licznik wygranych pojedynków (w /statystyki) i osiągnięcie „Mistrz pojedynków” za 10 zwycięstw (razem 25).
## [0.140.0] - 2026-10-16

### Dodano
- 7 nowych zadań na potwory, które ich dotąd nie miały: dziki, pająki leśne i kryształowe golemy w Varden
  (poziomy 10-40) oraz żywiołaki ognia, harpie burzy, piaskowe tytany i kościane smoki w obozie (300-870).
## [0.139.0] - 2026-10-16

### Dodano
- U bankiera wiersz „Zamówienia kupna” otwiera listę zamówień jednym dotknięciem.
## [0.138.0] - 2026-10-16

### Dodano
- Zamówienia kupna (gra sieciowa): u bankiera `/kupię 20 wilcze futro 2000` blokuje złoto z konta i ogłasza
  zamówienie. `/kupię` otwiera listę, `/sprzedaj 3` realizuje cudze zamówienie (złoto bez prowizji od razu na konto,
  przedmioty pocztą do depozytu kupującego), `/kupię anuluj 3` zwraca złoto.
## [0.137.0] - 2026-10-16

### Dodano
- Osiągnięcie „Wypoczęty łowca”: pokonaj 100 potworów z premią wypoczęcia (razem 24 osiągnięcia).
## [0.136.0] - 2026-10-16

### Dodano
- Lista /gracze pokazuje nagrodę za głowę przy postaciach, za które ktoś ją wyznaczył.
## [0.135.0] - 2026-10-16

### Dodano
- Podpowiedź o odpoczynku: po pierwszych walkach, gdy postać wróci do strefy ochronnej, dziennik wyjaśnia
  szybszą regenerację i premię za wypoczęcie.
## [0.134.0] - 2026-10-16

### Dodano
- Wypoczęcie: czas odpoczynku w strefie ochronnej (do 30 minut) zamienia się w premię +50% doświadczenia
  za kolejne pokonane potwory (20 sekund odpoczynku na walkę, 30 minut = 90 walk). Karta postaci pokazuje,
  na ile walk wystarczy wypoczęcia, a wpis w dzienniku dopisuje „wypoczęty”.
## [0.133.1] - 2026-10-16

### Naprawiono
- Gra na Androidzie zamykała się przy starcie od 0.131.0 (wczytywanie okna „Co nowego” używało metody Javy,
  której Android 11 nie ma). Nowy test pilnuje takich wywołań w kodzie klienta.
## [0.133.0] - 2026-10-16

### Dodano
- Odpoczynek: w strefie ochronnej (świątynia, miasto) zdrowie i mana odnawiają się trzy razy szybciej, a po
  jedzeniu cztery razy. Nie działa w czasie blokady strefy po walce. Karta postaci pokazuje, kiedy odpoczywasz.
## [0.132.0] - 2026-10-16

### Zmieniono
- Okno `/pomoc` ma wiersz `/nowości` i wspomina o `/idź łowisko`, `/nagroda` i wpłatach do gildii; krótsze opisy
  nie wychodzą już poza okno.
## [0.131.0] - 2026-10-16

### Dodano
- Okno „Co nowego w grze” (`/nowości` albo `/zmiany`) z punktami pięciu ostatnich wersji. Po aktualizacji
  gry otwiera się samo raz; dziennik zmian jest dołączony do gry.
## [0.130.0] - 2026-10-16

### Dodano
- 9 nowych czarów na poziomy 40-60: Płomienny grot i Burza energii (czarodziej), Lodowy kolec i Gniew ziemi
  (druid), Boska włócznia i Boski gniew (paladyn), Pięść smoka, Burza ciosów i Głęboka medytacja (mnich).
  Symulacje endgame nadal przechodzą.

### Naprawiono
- Księga czarów nie wychodzi poza okno: przy wielu czarach pokazuje te najbliższe poziomowi postaci,
  a lista receptur ma stronę dopasowaną do wysokości okna.
## [0.129.0] - 2026-10-16

### Dodano
- Dziennik zadań pokazuje zlecenie łowcy z postępem; dotknięcie go (albo zadania dnia w toku) prowadzi na łowisko.

### Naprawiono
- Dziennik zdarzeń nie zasłania już nagłówka otwartego okna (np. dziennika zadań).
## [0.128.0] - 2026-10-16

### Dodano
- `/idź łowisko` prowadzi do najbliższego legowiska potwora ze zlecenia łowcy (a bez zlecenia z zadania dnia)
  na tym piętrze; zlecenie przypomina o tym poleceniu.
## [0.127.0] - 2026-10-16

### Dodano
- Zadanie dnia i zlecenie łowcy mówią, gdzie szukać potwora, np. „pokonaj 50x Wilk (Krainy Varden)”.
## [0.126.0] - 2026-10-16

### Dodano
- Karta potwora w bestiariuszu podaje, gdzie go szukać: krainy z jego legowiskami (z dopiskiem „nocą”, gdy
  pojawia się tylko po zmroku).
## [0.125.0] - 2026-10-16

### Dodano
- Okno `/wydarzenia` w grze online pokazuje trwającą premię doświadczenia i ile minut jej zostało.
## [0.124.0] - 2026-10-16

### Dodano
- Wydarzenie premii doświadczenia: administrator włącza `/bonusexp 50 60` (albo `expevent 50 60` w konsoli),
  a wszyscy dostają +50% doświadczenia za potwory przez 60 minut. Start i koniec są ogłaszane, a wchodzący
  gracze widzą, ile zostało.
## [0.123.0] - 2026-10-16

### Dodano
- Skarbiec gildii (online): `/gildia wpłać kwota` przelewa złoto z konta do wspólnego skarbca, `/gildia wypłać kwota`
  może tylko lider. Każda wpłata i wypłata jest ogłaszana członkom, stan skarbca widać w `/gildia`.
## [0.122.0] - 2026-10-16

### Dodano
- Karta postaci pokazuje maksymalny atak z umiejętnością, a u broni dystansowej założoną amunicję i jej liczbę.

### Naprawiono
- Dziennik zdarzeń i śledzenie zadań nie nachodzą już na otwartą kartę postaci; karta jest szersza
  i mieści cały tekst.
## [0.121.0] - 2026-10-16

### Dodano
- Nagrody za głowy (online): `/nagroda imię, kwota` dokłada złoto z konta do nagrody za postać (min. 1 000 gp),
  `/nagrody` pokazuje listę. Kto pokona tę postać w walce PvP, dostaje całość na konto (nie działa w pojedynku
  ani w tej samej drużynie lub gildii). Serwer ogłasza wpłaty i odbiór, nagrody przetrwają restart.
## [0.120.0] - 2026-10-16

### Dodano
- Nowa podpowiedź dla paladynów: gdy gniazdo amunicji jest puste, a w plecaku są włócznie, gra podpowiada,
  żeby je założyć (od 0.118.0 zwiększają obrażenia).
## [0.119.0] - 2026-10-16

### Dodano
- Ranking umiejętności: `/ranking magia`, `miecz`, `dystans`, `pięści`, `tarcza`, `wędkarstwo` pokazuje
  najlepszych w danej umiejętności (także graczy offline z zapisów) i Twoje miejsce.
## [0.118.0] - 2026-10-16

### Dodano
- Amunicja ma znaczenie: włócznie w gnieździe amunicji zwiększają obrażenia włóczni paladyna o 1/4 swojego
  ataku, każdy strzał zużywa jedną, a gdy stos się skończy, uzupełnia się z plecaka (z komunikatem).
  Bez amunicji broń dystansowa działa jak dotąd.
## [0.117.0] - 2026-10-16

### Dodano
- Wiadomość dnia serwera: administrator ustawia ją `/motd tekst` w grze albo `MOTD tekst` w konsoli
  (`/motd -` usuwa). Każdy wchodzący gracz widzi ją w dzienniku; zapis w `motd.txt` przetrwa restart.
## [0.116.0] - 2026-10-16

### Dodano
- Online: serwer wysyła stan wyzwania (komunikat CHAL), więc fala i pozostały czas są widoczne na ekranie
  także w grze sieciowej, nie tylko offline.
## [0.115.0] - 2026-10-16

### Dodano
- Nad wybranym celem widać imię potwora z oceną trudności względem poziomu postaci (bardzo łatwy, łatwy,
  wyrównany, trudny, śmiertelny) w kolorze od szarego do czerwonego.

### Zmieniono
- Krótsza pierwsza podpowiedź, mieści się w szerokości ekranu telefonu.
## [0.114.0] - 2026-10-16

### Dodano
- `/podpowiedzi` wyłącza podpowiedzi dla nowych postaci (działa też online); pierwsza podpowiedź o tym mówi.
## [0.113.0] - 2026-10-16

### Dodano
- Podpowiedzi dla nowych postaci (do poziomu 29): sterowanie, walka z pierwszym potworem, łupy z ciała,
  niskie zdrowie, przewodnik z zadaniami i pełny plecak. Każda pokazuje się raz w dzienniku, gdy pasuje
  do sytuacji; pokazane zapisują się w postaci (online liczy je serwer). Wszystkie sceny GUI odświeżone.
## [0.112.0] - 2026-10-16

### Dodano
- `/idź kupiec` (także bank, kapłan, przewodnik, woźnica albo fragment imienia): postać idzie do najbliższego
  takiego NPC na piętrze, a po dojściu otwiera się rozmowa. Podpowiedź w oknie `/pomoc`.
## [0.111.0] - 2026-10-16

### Dodano
- Licznik ukończonych wyzwań w statystykach postaci (zapisywany; starsze zapisy zaczynają od zera)
  i osiągnięcie „Pogromca fal” za 5 wyzwań; razem 23 osiągnięcia.
## [0.110.0] - 2026-10-16

### Dodano
- Trwające wyzwanie jest widoczne na ekranie nad śledzeniem zadań: numer fali, potwór i pozostały czas.
## [0.109.0] - 2026-10-16

### Dodano
- Zwój wyzwania (1 000 gp u kupców ogólnych w 4 miastach): odczytany poza miastem przywołuje 3 fale potworów
  dobranych do poziomu (3, 4 i 5 sztuk). Kolejna fala rusza po pokonaniu poprzedniej; po ostatniej złoto na konto
  i premia doświadczenia. Wyzwanie przepada po 5 minutach, po śmierci albo po odejściu dalej niż 12 pól.
## [0.108.0] - 2026-10-16

### Dodano
- `/cena nazwa`: wartość przedmiotu i lista kupców NPC ze wszystkich miast, od najwyższej ceny skupu,
  z ceną sprzedaży (np. `/cena wilcze futro`).

### Naprawiono
- Wyścig w teście sieciowym drużyny (sprawdzenie postępu zlecenia przed jego zapisaniem).
## [0.107.0] - 2026-10-16

### Dodano
- Śledzenie zadań na ekranie pokazuje też postęp zlecenia łowcy, gdy jest wolny wiersz.
## [0.106.0] - 2026-10-16

### Zmieniono
- Online w drużynie: potwór pokonany przez drużynę obok (w zasięgu podziału doświadczenia) liczy się każdemu
  członkowi do zadania dnia, zlecenia łowcy i zadań na potwory, nie tylko temu, kto zadał ostatni cios.
## [0.105.0] - 2026-10-16

### Dodano
- Osiągnięcie „Zawodowy łowca” (tytuł) za 10 oddanych zleceń łowcy; razem 22 osiągnięcia.
- Okno `/statystyki` pokazuje liczbę oddanych zleceń łowcy.
## [0.104.0] - 2026-10-16

### Dodano
- Zlecenia łowcy u przewodnika: pokonaj 50 potworów jednego rodzaju dobranego do poziomu, nagroda w złocie
  na konto i doświadczeniu. Bez limitu dziennego (jedno zlecenie naraz), można porzucić i wziąć nowe.
  Postęp zapisuje się w postaci i widać go w oknie `/wydarzenia`.
## [0.103.0] - 2026-10-16

### Zmieniono
- „Uzupełnij zapasy” kupuje najsilniejsze mikstury zdrowia i many, których postać może używać i które sprzedaje
  ten kupiec (np. druid od poziomu 50 dostaje silne mikstury many, ale nie silne mikstury zdrowia).
## [0.102.0] - 2026-10-16

### Zmieniono
- Przyciski HP i MP (oraz klawisze i automatyczna mikstura) piją najsilniejszą miksturę, którą postać ma w plecaku
  i może użyć (najwyższa, silna, zwykła), a licznik na przycisku pokazuje liczbę właśnie tych mikstur.
## [0.101.0] - 2026-10-16

### Dodano
- Ignorowanie graczy: `/ignoruj Imię` ukrywa (albo przywraca) wiadomości gracza w czacie ogólnym, prywatnym, gildii
  i drużynie; `/ignoruj` pokazuje listę. Lista jest zapisana na urządzeniu dla każdej postaci, najwyżej 50 imion.
  Ogłoszeń administratora nie da się ukryć.
## [0.100.0] - 2026-10-16

### Dodano
- Online: ostrzeżenie w dzienniku, gdy w zasięgu wzroku pojawi się gracz z czerwoną czaszką
  (raz na każde pojawienie się; po zniknięciu z widoku i powrocie ostrzega znowu).
## [0.99.0] - 2026-10-16

### Dodano
- Kolor stroju: `/strój` otwiera okno z 8 kolorami (kolor profesji, czerwony, niebieski, zielony, czarny, biały,
  fioletowy, złoty), `/strój złoty` zakłada od razu. Kolor jest zapisywany w postaci i online widzą go inni gracze.
- `/strój` w oknie poleceń (zamiast `/rynek`, który działa dalej u bankiera); nowa scena GUI outfit (35 scen).
## [0.98.0] - 2026-10-16

### Dodano
- Polecenia serwera `status` (czas działania, gracze, potwory, inwazja, pamięć) i `save` (zapis wszystkich postaci)
  w konsoli oraz dla administratorów w czacie: `/serwer`, `/zapisz`. Opis w docs/SERVER.md.
## [0.97.0] - 2026-10-16

### Dodano
- Komunikat w dzienniku, gdy boss się odrodzi („Boss Grimtooth odrodził się (…)”); online liczy go serwer
  dla każdego gracza. Bossowie żywi przy wejściu do gry nie są ogłaszani.
## [0.96.0] - 2026-10-16

### Zmieniono
- Okno `/znaczniki` pokazuje także bossa tygodnia i miejsce ostatniej śmierci; dotknięcie wiersza prowadzi postać na miejsce.
## [0.95.0] - 2026-10-16

### Dodano
- Miejsce ostatniej śmierci jest zaznaczone na minimapie i dużej mapie („Miejsce śmierci”), także po śmierci z ręki gracza.
## [0.94.0] - 2026-10-16

### Zmieniono
- „Uzupełnij zapasy” u kupca dokupuje teraz także runy niesione w plecaku (do 20) i amunicję
  (z plecaka i z gniazda amunicji, do 100), a nie tylko mikstury.
- Jaśniejszy komunikat, gdy zapasy są pełne albo brakuje monet.
## [0.93.0] - 2026-10-16

### Dodano
- Miejsce bossa tygodnia jest zaznaczone na minimapie i dużej mapie (żółta kropka z podpisem „Boss tygodnia: …”
  na mapie piętra, na którym się odradza).
- Rozszerzony test bossa tygodnia.

## [0.92.0] - 2026-10-16

### Dodano
- 2 nowe osiągnięcia (razem 21): „Łowca tygodnia” za pokonanie bossa tygodnia i „Stały bywalec” za 10 godzin gry
  (czas z okna `/statystyki`). Oba dają tytuł nad imieniem.
- 1 nowy test. Sceny `achievements` i `stats` odświeżone.

## [0.91.0] - 2026-10-16

### Dodano
- W depozycie u bankiera wiersz „Odłóż cały łup”: jednym dotknięciem przenosi wszystkie materiały z plecaka
  do depozytu, bez przedmiotów potrzebnych do aktywnych zadań i bez zapasów (akcja `npc:storeloot`, działa też online).
- 1 nowy test; łącznie 343. Scena `depot` odświeżona.

## [0.90.0] - 2026-10-16

### Dodano
- Analiza polowania liczy zużyte zapasy (mikstury, runy, amunicja: sztuki i wartość) i pokazuje „Bilans polowania”:
  złoto i łup minus koszt zapasów, także na godzinę.

### Zmieniono
- „Wartość przedmiotów” w analizie nie liczy już zapasów, więc wypite mikstury nie zaniżają łupu.
- 1 nowy test; łącznie 342.

## [0.89.0] - 2026-10-16

### Dodano
- 5 nowych zadań strażnika obozu na wschodzie, wypełniających luki poziomów 230-900:
  „Upiorna straż” (270, upiorni rycerze), „Mroźni olbrzymi” (420), „Polowanie w cieniu” (570, łowcy cieni),
  „Kapłani pustki” (700) i „Demony u bram” (780). Test `everyQuestTargetCanBeFoundInTheWorld` potwierdza, że cele występują w świecie.

## [0.88.0] - 2026-10-16

### Dodano
- Karta postaci pokazuje postęp do następnego poziomu w procentach: przy doświadczeniu („Exp: … (42%)”)
  i przy każdej umiejętności („Miecz 12 (35%)”).
- 1 nowy test; łącznie 341. Scena `character` odświeżona.

## [0.87.0] - 2026-10-16

### Dodano
- Ustawienie „Cel sam” (druga strona panelu): gdy postać nie ma celu i jest poza strefą ochronną, sama wybiera
  potwora stojącego najwyżej 3 pola obok (działa też online, przez to samo polecenie co przycisk CEL).
- 1 nowy test i rozszerzony test ustawień; łącznie 340.

## [0.86.0] - 2026-10-16

### Dodano
- Śledzenie zadań na ekranie: pod stanem postaci widać postęp aktywnego zadania („Zadanie: Plaga wilków 3/10”)
  i zadania dnia, a gdy cel jest osiągnięty, przypomnienie o odbiorze nagrody. Najwyżej 2 wiersze.
- 1 nowy test; łącznie 339. Scena `journal` odświeżona.

## [0.85.0] - 2026-10-16

### Dodano
- Ustawienie „Jedzenie samo”: gdy sytości zostaje mniej niż minuta, postać zjada pierwsze jedzenie z plecaka
  (sytość podwaja regenerację).
- Panel ustawień ma strony: ostatni wiersz „Więcej ustawień (strona 1 z 2)” przełącza stronę.

### Zmieniono
- Krótszy opis ustawienia leczenia („Leczenie samo”), żeby mieścił się w wierszu.
- 1 nowy test; łącznie 338.

## [0.84.0] - 2026-10-16

### Dodano
- Alarm niskiego zdrowia: gdy życie spadnie poniżej 25%, krawędzie ekranu pulsują na czerwono, a co 4 s gra
  krótki sygnał (nowy dźwięk ALARM, głośność z ustawień). Znika, gdy zdrowie wróci powyżej progu.
- 1 nowy test; łącznie 337.

## [0.83.0] - 2026-10-16

### Dodano
- Historia czatu: strzałki w górę i w dół w polu czatu (desktop) przywracają 20 ostatnich wiadomości i poleceń.
  Na Androidzie okno czatu podpowiada ostatnią wiadomość („Ostatnio: …”).
- 1 nowy test; łącznie 336.

## [0.82.0] - 2026-10-16

### Dodano
- Wyszukiwanie przedmiotów `/szukaj nazwa`: okno z liczbą sztuk w plecaku, w depozycie i informacją, czy przedmiot
  jest założony. Działa bez polskich znaków i wielkości liter, także po angielskim kluczu (np. `/szukaj futro`).

### Zmieniono
- Wiersz `/statystyki` w `/pomoc` podpowiada `/szukaj`.
- 1 nowy test; łącznie 335.

## [0.81.0] - 2026-10-16

### Dodano
- U kupca wiersz „Uzupełnij mikstury”: jednym dotknięciem dokupuje mikstury zdrowia i many do 20 sztuk każdej,
  tyle, na ile starczy monet w plecaku (akcja `npc:refill`, działa też online).
- 1 nowy test; łącznie 334. Scena `shop` odświeżona.

## [0.80.0] - 2026-10-16

### Dodano
- Boss tygodnia: co 7 dni jeden boss wspólny dla wszystkich graczy. Pierwsze pokonanie w tygodniu daje postaci
  złoto na konto (2× doświadczenie bossa, co najmniej 5 000 gp); liczy się też jego elitarna odmiana.
- Wiersz „Boss tygodnia” w oknie `/wydarzenia` (nagroda albo „nagroda odebrana, nowy za N dni”).
- Zapis odebranej nagrody (`wboss`). 2 nowe testy; łącznie 333.

## [0.79.0] - 2026-10-16

### Dodano
- Ostrzeżenia o końcu zapasów na polowaniu: gdy mikstur, run albo amunicji w plecaku spadnie do 5 sztuk
  („Zostało tylko 5: Mikstura zdrowia.”) i gdy skończą się całkiem. W strefie ochronnej (zakupy, depozyt) bez komunikatów.
- 1 nowy test; łącznie 331.

## [0.78.0] - 2026-10-16

### Dodano
- Okno „Ostatnie łupy” (`/łupy`, `/łup`): 30 ostatnich pokonanych potworów i co z nich wypadło (także to, co zostało w ciele),
  z czasem („3 min temu”) i oznaczeniem rzadkiego łupu. Działa lokalnie i online (żądanie `LOOTLOG`).

### Zmieniono
- Wiersz `/analiza` w oknie `/pomoc` wskazuje też `/łupy`.
- 1 nowy test i rozszerzony test rzadkiego łupu; łącznie 330.

## [0.77.0] - 2026-10-16

### Zmieniono
- Ustawienie automatycznego leczenia najpierw rzuca najsilniejszy dostępny czar leczący (Opatrzenie ran, Uzdrowienie),
  a miksturę zdrowia pije dopiero, gdy brak many albo czar się odnawia. Wiersz ustawień: „Leczenie samo (czar, potem mikstura)”.
- 1 nowy test; łącznie 329.

## [0.76.0] - 2026-10-16

### Dodano
- Okno „Statystyki” (`/statystyki`, `/staty`): czas gry, poziom (z najwyższym osiągniętym), pokonane potwory i gatunki,
  elity i bossowie, śmierci, ukończone zadania, otwarte skrzynie, osiągnięcia i złoto w banku.
- Postać liczy czas gry, śmierci i najwyższy poziom (zapis `stats`, przenoszony też do klienta online).
- Scena regresji GUI `stats` (34 sceny).

### Zmieniono
- W oknie `/pomoc` wiersz `/zadania` zastąpiony wierszem `/statystyki` (dziennik zadań nadal pod `/zadania`, klawiszem Z i w KSIĘDZE).
- 2 nowe testy; łącznie 328.

## [0.75.0] - 2026-10-16

### Dodano
- Ustawienie „Mikstura many sama” (wyłączona, poniżej 30%, 50% albo 70% MP), niezależne od mikstury zdrowia.
  Zdrowie ma pierwszeństwo: mikstura many nie pije się w tej samej chwili co mikstura zdrowia.

### Zmieniono
- Panel ustawień przesunięty wyżej, żeby dziewiąty wiersz nie zachodził na przycisk KSIĘGA.
- 1 nowy test; łącznie 326.

## [0.74.0] - 2026-10-16

### Dodano
- Okno „Wydarzenia” (`/wydarzenia` albo `/dziś`): następna albo trwająca inwazja (z serwera), potwór dnia, postęp
  zadania dnia, seria logowania, najbliższa nagroda za poziom i aktywne zaklęcia z pozostałym czasem.
- Scena GUI `events` (33 sceny). W oknie `/pomoc` wiersz `/wydarzenia` zastąpił `/potwór` (polecenie dalej działa).
- 2 nowe testy (w tym sieciowy); łącznie 325.

## [0.73.0] - 2026-10-16

### Dodano
- „Sprzedaj cały łup” u kupca: jedno dotknięcie sprzedaje wszystkie materiały z potworów, które kupiec skupuje.
  Przedmioty potrzebne do aktywnych zadań zbierania zostają w plecaku; wyposażenie, mikstury i narzędzia też.
- 1 nowy test; łącznie 323.

## [0.72.0] - 2026-10-16

### Dodano
- Ranking gildii: `/ranking gildie` pokazuje 10 najsilniejszych gildii według sumy poziomów członków (także
  offline) i miejsce twojej gildii.
- 1 nowy test i rozszerzony test sieciowy gildii; łącznie 322.

## [0.71.0] - 2026-10-16

### Dodano
- Własne znaczniki na mapie: `/znacznik nazwa` zapisuje miejsce, w którym stoisz (żółta kropka na minimapie,
  podpis na mapie piętra), `/znaczniki` pokazuje listę z odległością, a dotknięcie prowadzi postać na miejsce.
  `/znacznik usuń nazwa` kasuje. Do 20 znaczników na postać, zapisywane na urządzeniu.
- Scena GUI `markers` (32 sceny). Wiersz `/znaczniki` w oknie `/pomoc`.
- 1 nowy test; łącznie 321.

## [0.70.0] - 2026-10-16

### Dodano
- Nagrody za progi poziomu 10, 20, 30, 50, 75, 100, 150, 200 i 250: złoto na konto (od 1 000 gp do 625 000 gp)
  i od poziomu 20 zwoje nauki (do plecaka, a gdy brak miejsca do depozytu). Każdy próg raz na postać, także po
  śmierci i ponownym awansie. Istniejące postacie dostają nagrody od swojego obecnego poziomu w górę.
- 2 nowe testy; łącznie 320.

## [0.69.0] - 2026-10-16

### Dodano
- Wiadomość dnia gildii: lider ustawia ją poleceniem `/gildia wiadomość tekst` (pusty tekst usuwa, do 120 znaków).
  Członkowie dostają ją od razu, przy każdym wejściu do gry i w `/gildia`. Zapisywana w guilds.txt
  (stare pliki bez wiadomości wczytują się bez zmian).
- 1 nowy test i rozszerzony test sieciowy gildii; łącznie 318.

## [0.68.0] - 2026-10-16

### Dodano
- Zwój nauki (u kupców w czterech miastach, 2000 gp): UŻYJ w plecaku daje +30% doświadczenia za potwory
  przez godzinę polowania (do 3 godzin zapasu; czas płynie tylko poza strefą ochronną). Działa też w drużynie
  i łączy się z potworem dnia. Pozostały czas widać w oknie analizy polowania.
- 1 nowy test; łącznie 317.

## [0.67.0] - 2026-10-16

### Dodano
- Przelewy złota u bankiera: `/przelew Imię, 5000` (także `5 000`, `5k`, `2kk`). Złoto schodzi z konta nadawcy
  i trafia na konto odbiorcy; gracz offline dostaje je przy następnym wejściu.
- Aktywne zaklęcia z pozostałym czasem w oknie analizy polowania (klawisz H).
- 2 nowe testy (w tym sieciowy); łącznie 316.

## [0.66.0] - 2026-10-16

### Dodano
- Zaklęcia u kowala (okno ulepszania u każdego kupca): kradzież życia (6% zadanych obrażeń leczy),
  kradzież many (3% wraca jako mana) i cios krytyczny (10% szans na +50% obrażeń, także czarami).
  Jeden zakup to godzina polowania, zapas do 3 godzin; czas płynie tylko poza strefą ochronną.
  Cena rośnie z poziomem postaci. Zaklęcia są zapisywane i działają online.
- 2 nowe testy; łącznie 314.

## [0.65.0] - 2026-10-16

### Dodano
- Selvaran: „Skóry węży” (15 wężowych skór, poziom 85) i „Strażnicy świątyni” (10 strażników świątyni, poziom 150,
  po „Wyprawie badaczy”, nagroda 20 wielkich mikstur zdrowia). Frostholm: „Serce mrozu” (3 zamarznięte serca,
  poziom 110, po „Zamieci”, nagroda 15 wielkich mikstur many). Zamyka luki poziomów 70-190 i 80-140.
- Nowe tematy rozmowy u przewodników: świątynia, golemy.

## [0.64.0] - 2026-10-16

### Dodano
- Łańcuch zadań w Qasirah dla poziomów 45-170 (dotąd luka między 55 a 180): „Napadnięta karawana” (12 kłów hien),
  „Szepty w piasku” (10 piaskowych upiorów, nagroda 10 silnych mikstur many) i „Złodziej klejnotów”
  (3 klejnoty skarabeuszy, nagroda Tarcza z piaskowca). Nowy temat rozmowy u przewodnika: karawana.
- Test: cel każdego zadania da się zdobyć w świecie (potwór ma spawn albo przedmiot wypada z takiego potwora).
- 1 nowy test; łącznie 312.

## [0.63.0] - 2026-10-16

### Dodano
- Okno „Polecenia” (`/pomoc`, także po nieznanym poleceniu): polecenia bez argumentów wykonują się po dotknięciu
  (bez pisania na telefonie), a te z argumentami mają przykład i opis. Akcja wiersza `cmd:/polecenie`.
- Scena GUI `help` (31 scen).
- 1 nowy test; łącznie 311.

## [0.62.0] - 2026-10-16

### Dodano
- Kowalstwo u kupców („Ulepsz wyposażenie”): założoną broń, tarczę i zbroję można ulepszyć do +5. Broń zyskuje
  6% ataku na poziom (co najmniej +1), zbroja i tarcza +1 pancerza i obrony. Szansa 100/90/75/55/35%, cena
  max(200 gp × poziom², 2 × wartość × poziom); nieudana próba zabiera tylko złoto.
- Poziom ulepszenia jest częścią przedmiotu (`ItemStack.upgrade`), zapis `idxilość+poziom`. Plecak pokazuje „+N”,
  a porównanie przedmiotów liczy ulepszenia.
- Ulepszonych przedmiotów nie można sprzedać hurtem u kupca, oddać w handlu, wystawić na rynku ani wysłać pocztą
  (liczą się tylko zwykłe sztuki). Depozyt przenosi je bez utraty poziomu. Przy zdejmowaniu przedmiotów
  (płatności, zadania) najpierw idą zwykłe sztuki.
- Scena GUI `smith` (30 scen), opcja `-Dpixel.open=smith`.
- 3 nowe testy; łącznie 310.

## [0.61.0] - 2026-10-16

### Dodano
- `/gracze` (albo `/online`): okno „W grze” z listą graczy online (profesja, poziom, gildia, tytuł),
  od najwyższego poziomu. Bez położenia graczy. Żądanie `WHO` dla każdego (dotąd tylko administrator: `/kto`).
- 1 nowy test sieciowy; łącznie 307.

## [0.60.0] - 2026-10-16

### Dodano
- Karta potwora w bestiariuszu: dotknięcie potwora pokazuje poziom, zdrowie, doświadczenie i atak; od 10
  pokonanych także odporności i pancerz, a od 25 listę łupów z szansami. Działa też online (dane z klienta).
- 2 nowe testy; łącznie 306.

## [0.59.0] - 2026-10-16

### Dodano
- Tytuły: zdobyte osiągnięcie w oknie osiągnięć (O) można dotknąć, aby nosić jego nazwę jako tytuł (ponowne
  dotknięcie go zdejmuje). Tytuł widać na karcie postaci, a online inni gracze widzą go złotym napisem nad
  imieniem (komunikat PL, pole 13; żądanie `TITLE klucz|-`). Zapis postaci (`title`).
- 2 nowe testy (w tym sieciowy: pupil i tytuł widoczne dla innego gracza); łącznie 304.

## [0.58.0] - 2026-10-16

### Dodano
- Pupile: Szczenię (Varden), Kocię (Selvaran), Kruk (Frostholm) i Fenek (Qasirah) u kupców po 5 000 gp.
  UŻYJ w plecaku przywołuje pupila, który chodzi pole za postacią (ponowne UŻYJ go odsyła). Przedmiot się
  nie zużywa; gdy zniknie z plecaka (sprzedaż, handel, wyrzucenie), pupil odchodzi. Zapis postaci (`pet`)
  i online: inni gracze widzą pupila (komunikat PL, pole 12).
- Opcja deweloperska `-Dpixel.open=pet` (`-Dpixel.pet=pet_cat` itd.), scena GUI `pet` (29 scen).
- 2 nowe testy; łącznie 302.

## [0.57.0] - 2026-10-16

### Dodano
- Rzadki łup: przedmiot z szansą bazową najwyżej 1% (dla elit liczona przed podwojeniem) daje wpis
  „Rzadki łup: …!” w dzienniku, a na serwerze pozostali gracze widzą „Imię zdobywa rzadki łup: przedmiot (potwór)!”.
- Potwór dnia ma złoty romb nad głową w świecie (także online).
- 2 nowe testy; łącznie 300.

## [0.56.0] - 2026-10-16

### Dodano
- Potwór dnia: każdego dnia jeden rodzaj potwora (nie boss, ze stałych spawnów) daje +50% doświadczenia
  i +50% szansy na każdy łup, także w odmianie elitarnej i przy podziale w drużynie. Wybór zależy tylko
  od daty, więc klient i serwer liczą go tak samo. Ogłoszenie w dzienniku po wejściu i o północy, wiersz
  w analizie polowania (H) i polecenie `/potwór` (albo `/bonus`).
- Opcja deweloperska `-Dpixel.day=N` ustala dzień gry; przy `-Dpixel.hour` dzień jest stały (regresja GUI).
- 3 nowe testy; łącznie 298.

## [0.55.0] - 2026-10-16

### Dodano
- 4 nowe osiągnięcia (łącznie 19): Czempion (pokonaj potwora elitarnego), Łowca elity (25 elitarnych),
  Mistrz broni (40 w umiejętności walki), Poszukiwacz skarbów (10 skrzyń). Postać liczy pokonane
  potwory elitarne (zapis `elites`).
- Okno osiągnięć ma strony, gdy lista nie mieści się w dwóch kolumnach (mały ekran).
- 1 nowy test; łącznie 295.

## [0.54.0] - 2026-10-16

### Dodano
- Potwory elitarne: około 4% zwykłych potworów pojawia się jako „(elita)”: większe i złociste, 2x zdrowie,
  1,3x obrażenia, 3x doświadczenie i podwójna szansa na każdy łup. Bossowie nie mają odmiany elitarnej.
  Los zależy od numeru potwora (bez zużywania generatora, więc spawny zostają powtarzalne). Online serwer
  oznacza elitę w klatce stanu (`MO ... E`, starsze klienty ignorują to pole).
- `MonsterType.eliteVariant/isElite/eliteRoll`. 3 nowe testy; łącznie 294.

### Poprawiono
- Polskie litery składane w czcionce (ą, ę, ś, ż i inne) nie miały ciemnego obrysu jak reszta liter, więc
  znikały na jasnym tle (śnieg, piasek). Teraz mają obrys jak pozostałe litery. Wzorce regresji GUI odświeżone.

## [0.53.0] - 2026-10-16

### Dodano
- Podsumowanie śmierci: po śmierci samo otwiera się okno „Ostatnia śmierć” z zabójcą, stratą doświadczenia
  i obrażeniami z ostatnich 30 s według źródła (potwory i gracze, suma i liczba ciosów). Ponownie: `/śmierć`.
  Online serwer odsyła podsumowanie na komunikat `DEATH` (odpowiedź `DEATHLOG` z wierszami „etykieta;opis”),
  a klient prosi o nie sam po komunikacie o śmierci.
- `combat/DeathRecap`, `CombatSystem.recordDamage/lastDeath`, trafienia PvP też trafiają do podsumowania,
  podgląd `-Dpixel.open=death`. 3 nowe testy (w tym sieciowy) i nowe sprawdzenia w teście śmierci; łącznie 291.

## [0.52.0] - 2026-10-16

### Dodano
- Ustawienie „Mikstura zdrowia sama”: wyłączona, poniżej 30%, 50% albo 70% HP. Klient sam używa mikstury
  zdrowia z plecaka (ta sama akcja co przycisk HP, najwyżej raz na sekundę, więc online decyduje serwer).
  Domyślnie wyłączone.
- Ustawienie „Pogoda: pokazuj/ukryj” (słabsze telefony, nagrania).
- 1 nowy test; łącznie 288.

### Poprawiono
- Test pojedynku online czasem nie przechodził pod obciążeniem. Prawdopodobna przyczyna (wniosek, nie
  potwierdzona): potwór z okolicy dobijał przegranego przy 1 HP. Test usuwa teraz potwory z okolicy pola walki.

## [0.51.0] - 2026-10-16

### Dodano
- Błogosławieństwo u kapłana: za monety z plecaka (200 gp za poziom, co najmniej 1000 gp) najbliższa śmierć
  zabiera 2,5% zamiast 5% doświadczenia. Działa raz, także przy śmierci z ręki gracza (plecak przy czerwonej
  czaszce i tak zostaje na ziemi). Zapisywane w postaci (`bless`), widoczne na karcie postaci i w rozmowie.
- Karta postaci pokazuje miasto zamieszkania. Nazwy umiejętności z `Skill.label()`.
- 2 nowe testy; łącznie 287.

### Poprawiono
- Niestabilny test sieciowy znajomych: lista była pobierana, zanim serwer obsłużył wyjście znajomego.

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
