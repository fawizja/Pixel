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
docker run -d --name pixel -p 7171:7171 -v pixel-data:/data --restart unless-stopped pixel-server
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

## Aktualizacja

```bash
git pull
./gradlew :server:installDist
docker build -f server/Dockerfile -t pixel-server .
docker stop pixel && docker rm pixel
docker run -d --name pixel -p 7171:7171 -v pixel-data:/data --restart unless-stopped pixel-server
```

Zatrzymanie serwera zapisuje wszystkie postacie (zapis takze co kilka minut i przy wylogowaniu).
Protokol ma numer wersji - stary klient po aktualizacji serwera dostanie czytelny komunikat.

## Klient

W menu gry: "Graj online", adres `twoj-serwer:7171`, imie i haslo. Pierwsze logowanie nowym imieniem zaklada konto.
