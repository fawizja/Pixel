# Uruchomienie serwera Pixel (VPS)

Serwer jest zrodlem prawdy gry online: logowanie, swiat, potwory, walka, PvP, druzyny, handel i gildie.
Klient (telefon, PC) laczy sie z nim szyfrowanym polaczeniem TLS.

## Wymagania

- Linux z Docker (zalecane) albo Java 17 (JRE wystarczy).
- Otwarty port TCP 7171 (albo inny podany przy starcie).
- Ok. 512 MB RAM na serwer z kilkudziesiecioma graczami; test regresji pilnuje petli serwera dla 200 graczy.

## Docker (zalecane)

```bash
./gradlew :server:installDist
docker build -f server/Dockerfile -t pixel-server .
docker run -dit --name pixel -p 7171:7171 -v pixel-data:/data --restart unless-stopped pixel-server
docker logs pixel | grep odcisk      # odcisk certyfikatu TLS
```

Gotowy obraz jest tez budowany i sprawdzany w CI (zadanie `server-image`), ale nie jest publikowany w zadnym rejestrze.

## Bez Dockera

```bash
./gradlew :server:installDist
server/build/install/server/bin/server 7171 /srv/pixel-data
```

Przyklad uslugi systemd (`/etc/systemd/system/pixel.service`):

```ini
[Unit]
Description=Pixel MMORPG server
After=network-online.target

[Service]
User=pixel
ExecStart=/opt/pixel-server/bin/server 7171 /srv/pixel-data
Restart=on-failure
Environment=JAVA_OPTS=-Xmx512m

[Install]
WantedBy=multi-user.target
```

## Bezpieczenstwo i dane

- Przy pierwszym starcie serwer tworzy wlasny certyfikat w `<dane>/tls/` i wypisuje jego odcisk SHA-256.
  Klient zapamietuje odcisk przy pierwszym polaczeniu; zmiana certyfikatu = klienci odmowia polaczenia
  (ochrona przed podszyciem). Nie kasuj `<dane>/tls/`.
- Hasla graczy sa przechowywane tylko jako skroty PBKDF2 (`<dane>/accounts/`).
- Kopie zapasowe: `<dane>/backups/*.zip` przy starcie i co 6 h (7 najnowszych). Zawieraja klucz TLS i skroty hasel -
  przechowuj je jak sam katalog danych. Kopiuj je regularnie poza serwer.
- Serwer ma limity polaczen z jednego adresu, wiadomosci, czatu i zakladania kont (5 na dobe z adresu).
- Nie umieszczaj katalogu danych w repozytorium (`.gitignore` blokuje `server-saves/`, `*.p12`, `keystore.pass`).

## Moderacja

Administratorzy: imiona w pliku `<dane>/admins.txt` (jedno w linii) albo polecenie konsoli `admin Imie`.
Konsola serwera to standardowe wejscie: terminal, a w Dockerze `docker attach pixel` (wyjscie: Ctrl+P, Ctrl+Q).

| Konsola | Czat administratora | Dzialanie |
|---|---|---|
| `say tekst` | `/ogloszenie tekst` | ogloszenie do wszystkich |
| `kick Imie` | `/kick Imie` | wyrzucenie (z zapisem postaci) |
| `ban Imie 24` | `/ban Imie 24` | ban na 24 h (bez liczby albo 0 = na stale), wyrzuca z gry |
| `unban Imie` | `/unban Imie` | zdjecie bana |
| `mute Imie 30` | `/wycisz Imie 30` | wyciszenie czatu na 30 min (0 = zdjecie) |
| `who` | `/kto` | kto jest w grze |
| `status` | `/serwer` | czas dzialania, liczba graczy i potworow, inwazja, zuzycie pamieci |
| `save` | `/zapisz` | natychmiastowy zapis wszystkich postaci w grze |
| `motd tekst` | `/motd tekst` | wiadomosc dnia pokazywana kazdemu przy wejsciu (`motd -` usuwa, `<dane>/motd.txt`) |
| `expevent 50 60` | `/bonusexp 50 60` | +50% doswiadczenia za potwory dla wszystkich przez 60 min (`expevent 0` konczy; bez argumentow - stan) |
| `invasion Varden` | `/inwazja Varden` | inwazja na miasto teraz (bez nazwy - kolejne miasto) |
| `admin Imie` | - | nowy administrator (tylko konsola) |
| `shutdown 5` | - | wylaczenie za 5 min z ostrzezeniami na czacie (30/15/10/5/3/2/1 min, 30 s, 10 s), potem zapis i wyjscie; `shutdown 30s` w sekundach, `shutdown -` odwoluje. Kontener z `--restart unless-stopped` wstaje od razu ponownie (np. po wymianie pliku jar w wolumenie albo dla czystego restartu) |

Bany i wyciszenia sa w `<dane>/moderation.txt` (przetrwaja restart), dzialania administratorow w logu serwera.
Gracze moga pisac prywatnie: `/w Imie, tekst`.

Inwazje na miasta startuja same co 120 minut (gdy ktos jest w grze); odstep zmienia `-Dpixel.invasionMinutes=60`
(w Dockerze: `JAVA_OPTS`). Trwaja do pokonania wszystkich najezdzcow albo 20 minut. Nagrody (100 gp za wroga,
1 000 gp za herszta) trafiaja na konta graczy obecnych w grze przy koncu inwazji. Rynek graczy zapisuje sie w `<dane>/market.txt`, poczta w `<dane>/mail.txt`, nagrody za glowy w `<dane>/bounties.txt`, zamowienia kupna w `<dane>/buyorders.txt`, listy do graczy offline w `<dane>/letters.txt`.

## Aktualizacja

```bash
git pull
./gradlew :server:installDist
docker build -f server/Dockerfile -t pixel-server .
docker stop pixel && docker rm pixel
docker run -dit --name pixel -p 7171:7171 -v pixel-data:/data --restart unless-stopped pixel-server
```

Zatrzymanie serwera zapisuje wszystkie postacie (zapis takze co kilka minut i przy wylogowaniu).
Protokol ma numer wersji - stary klient po aktualizacji serwera dostanie czytelny komunikat.

## Klient

W menu gry: "Graj online", adres `twoj-serwer:7171`, imie i haslo. Pierwsze logowanie nowym imieniem zaklada konto.
