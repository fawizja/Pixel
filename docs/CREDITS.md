# Autorzy grafik

## WsT - "Free 32X32 tiles and borders - Top down"

Podłoża z krawędziami (trawy, ziemia, piasek, glina, śnieg, lód, bruk, posadzka otchłani: `assets/art/autotiles/`
i odpowiadające im `assets/art/tiles/`) pochodzą z paczki **WsT**: https://westudesign.itch.io (kupiona przez
Darka). Warunki autora: użycie w projektach komercyjnych i niekomercyjnych, bez odsprzedawania i rozpowszechniania
samych grafik. Dlatego w repozytorium są tylko wycięte kawałki używane przez grę, bez oryginalnych arkuszy
(narzędzie: `tools/art/wst_import.py`).

## WsT - "Alchemy & Potion Icons" i "Icon Frames & UI Slots"

Ikony mikstur, esencji i zwojów (`assets/art/items/`, lista w `tools/art/wst_icons_import.py`) oraz ramki pól
i panele okien interfejsu (`assets/ui/wst/`) pochodzą z dwóch płatnych paczek **WsT** (https://westudesign.itch.io, kupione przez
Darka). Licencja pozwala używać ich w grach, także komercyjnych, ale zabrania udostępniania samych plików. W repozytorium są tylko
elementy używane przez grę, bez oryginalnych paczek.

## Zidane42 - "Pixel RPG UI Kit - HUD, Buttons, Icons, Inventory & Slots (9-Slice)"

Wygląd interfejsu „nowy” (ustawienie „Wygląd interfejsu: nowy / klasyczny”, domyślnie nowy): ramki i wypełnienia pasków
zdrowia, many i doświadczenia, ramka okien, przyciski, pola i ramki rzadkości przedmiotów oraz przełączniki ustawień
(`assets/ui/zidane/`) pochodzą z płatnej paczki **Zidane42** (itch.io, kupiona przez Darka). Licencja paczki nie była
dostępna przy imporcie, więc obowiązuje ta sama zasada co przy paczkach WsT: użycie tylko w grze, bez rozpowszechniania
samej paczki. W repozytorium są tylko wycięte kawałki używane przez grę (narzędzie: `tools/art/zidane_import.py`;
oryginał poza repozytorium: `tools/art/zidane/`, kopia w plikach projektu `pixel-zidane-ui/pack_ui_1.2.zip`).
Ikon 24 px z paczki nie używamy (przedmioty mają ikony OTSP 32 px).
## Hinas (OTLand) – przedmioty

Od 2026-11-10 zestawy ekwipunku i część pojedynczych przedmiotów (137 obrazków w `assets/art/items/`: hełmy, zbroje,
nagolenniki, buty, tarcze, miecze, topory, buzdygany, łuki, różdżki, laski, księgi czarów i amulety; lista kluczy
w `tools/art/hinas_import.py`, statystyki w `items.csv`, id 256..392) narysował **Hinas** (spriter z forum OTLand).

- Grafiki zostały udostępnione publicznie na OTLand. Plik z podziękowaniami w paczce zawiera tylko "Hinas", **bez
  licencji**. Właściciel projektu zdecydował się ich użyć z podaniem autora (tutaj, w `docs/GRAPHICS.md` i w oknie
  `/autorzy` w grze). Jeśli autor się nie zgadza, grafiki zostaną usunięte.
- Zmiany: obrazki 32x32 kopiowane bez skalowania i rozmywania; tylko w jednym (`set 13/11.png`, Kogucia laska)
  czarne tło zamieniono na przezroczyste. Oryginały (luźne PNG i rozpakowane zestawy) leżą poza repozytorium w `tools/art/hinas/`
  (w `.gitignore`), tak jak źródła WsT.
- Nie użyto: złotych nagolenników i złotego hełmu oraz kompletu kolorowych plecaków (za bliskie znanym przedmiotom
  Tibii - przerysowania grafik CipSoft), skrzyni (brak przedmiotu) i dwóch prawie-duplikatów z `set 7`. Z tej samej
  paczki nie użyto też niczego od innych autorów (LeoTBR "Some Edited Weapons", bałwan `.aec`, ikony 16x16 "Icon Store").

## OpenTibia Sprite Pack (CC BY 4.0)

Część grafik w `assets/art/` (podłoże, drzewa i kamienie, potwory, NPC, postacie graczy, przedmioty) pochodzi z
**OpenTibia Sprite Pack** — darmowych grafik społeczności OpenTibia: https://github.com/peonso/opentibia_sprite_pack

- Licencja: Creative Commons Attribution 4.0 International (CC BY 4.0),
  https://creativecommons.org/licenses/by/4.0/ (pełny tekst: `assets/art/LICENSE-OTSP.txt`).
- Zmiany: obrazki wycięte z plików `otsp.dat`/`otsp.spr` narzędziem `tools/art/otsp_import.py`, przycięte do zawartości,
  stroje ludzi pokolorowane maską kolorów, nazwane według listy `docs/GRAPHICS.md`. Mapowanie nazw jest w tym skrypcie.
- To grafiki twórców społeczności, nie oryginalne grafiki Tibii (CipSoft).
- Własne grafiki projektu (licencja projektu, nie OTSP): kowadło, palenisko kuźni, stragan, drogowskaz, bela siana,
  łódka, sieci rybackie, skrzynka z kwiatami, a od 2026-10-23 także świecznik, chodnik, tron, ster, maszt, pokład,
  most wiszący, rozpadlina, ruiny muru, namiot orków i wiatrak - rysowane skryptem `tools/art/draw_town_props.py`.
- Orki (2026-10-23) to przebarwione stroje OTSP (goblin, okultysta, jaszczuroczłek), król i strażnicy - stroje OTSP z maską kolorów.
- Kratka ściekowa (2026-10-27) to grafika OTSP; szczur kanałowy i wielki szczur to przebarwiony szczur OTSP, a NPC Valmir, Nerys i nowi strażnicy - stroje OTSP z maską kolorów.
- Roślinność wg klimatu, zwęglone drzewa i belki, gruz, krew i ciała w napadniętych wioskach (2026-10-28) to grafiki OTSP, część przebarwiona (szron, susza, zwęglenie).
- Runy czarów jak w Tibii (0.240: pola, ściany, bomby, odtrutka, paraliż i inne) to przebarwione runy OTSP (`tools/art/rune_tints.py`); pola na ziemi rysujemy klatkami efektów OTSP (płomień, energia, kałuża szlamu, magia, ziemia).

Autorzy paczki (plik AUTHORS.md paczki, bez zmian):

Many people contributed for this release, some did so years ago, when
they made the sprites present here available free of charge. In
alphabetical order:

- 2Rec - [@OTLand](https://otland.net/members/2rec.187741/);
- 3visan (aka Vittu) - [@TibiaKing](http://www.tibiaking.com/forum/profile/19276-3visan/);
- Anevis (aka Caynez) - [@OTLand](https://otland.net/members/anevis.175097/), [@OTRealm](https://otrealm.com/index.php?members/caynez.51/);
- Andreéw - [@OTLand](https://otland.net/members/andreew.211874/);
- Arkan'. - [@OTLand](https://otland.net/members/arkan.214264/);
- BeachboyX - [@OTRealm](https://sprites.otrealm.com/);
- BrenoNeto - [@XTibia](http://www.xtibia.com/forum/profile/356307-brenoneto/);
- Cat - [@ZezeniaForum](https://www.zezeniaonline.com/forum/thread/15599);
- Celix - [@PhobosForum](http://forum.phobosonline.com/memberlist.php?mode=viewprofile&u=20);
- Corvo (aka handland) - [@OTLand](https://otland.net/members/handland1.225765/);
- dami1310 - [@OTLand](https://otland.net/members/dami1310.174641/);
- Daniel - [@OTRealm](https://sprites.otrealm.com/), [@TibiaKing](http://www.tibiaking.com/forum/profile/25583-daniel/);
- DawTorr - [@OTLand](https://otland.net/members/dawtorr.200757/);
- Despute - [@OTLand](https://otland.net/members/despute.212598/);
- ElderDark - [@TibiaKing](http://www.tibiaking.com/forum/profile/24698-elderdark/);
- Erick Etchebeur - [@OTRealm](https://otrealm.com/index.php?members/erick-etchebeur.7/), [@OTLand](https://otland.net/members/erick-etchebeur.212333/);
- Frevius - [@XTibia](http://www.xtibia.com/forum/profile/41440-frenvius/);
- Galiant (aka Andre) - [@OTLand](https://otland.net/members/galiant.211412/);
- GhostX - [@OTLand](https://otland.net/members/ghostx.145618/);
- HalfAway - [@OTLand](https://otland.net/members/halfaway.142275/), [@OTRealm](https://otrealm.com/index.php?members/halfaway.1/);
- Hunter Killer (aka Made of Clay) - [@OpenTibia](https://opentibia.net/topic/129940-who-deleted-my-thead/);
- Jacobs - [@OTLand](https://otland.net/members/jacobs.203121/);
- Landera - [@OTLand](https://otland.net/members/landera.146831/);
- Leshrot - [@OTLand](https://otland.net/members/leshrot.169503/), [@TibiaKing](http://www.tibiaking.com/forum/profile/40651-leshrot/), [@XTibia](http://www.xtibia.com/forum/profile/388631-leshrot/), [@OTRealm](https://otrealm.com/index.php?members/leshrot.50/);
- lionkobin - [@XTibia](http://www.xtibia.com/forum/profile/294150-lionkobin/);
- Madarada - [@OTLand](https://otland.net/members/madarada.222164/), [@XTibia](http://www.xtibia.com/forum/profile/379350-madarada/), [@TibiaKing](http://www.tibiaking.com/forum/profile/106486-madarada/);
- markindoot - [@XTibia](http://www.xtibia.com/forum/profile/357805-markindoot/);
- maxmillerxd - [@TibiaKing](http://www.tibiaking.com/forum/profile/15396-maxmillerxd/);
- Nechros - [@TibiaKing](http://www.tibiaking.com/forum/profile/40715-nechros/);
- Nogard - [@TibiaKing](http://www.tibiaking.com/forum/profile/38543-nogard/), [@XTibia](http://www.xtibia.com/forum/profile/397698-nogard/);
- Nordberg - [@PhobosForum](http://forum.phobosonline.com/memberlist.php?mode=viewprofile&u=126);
- Noxz - [@OTLand](https://otland.net/members/noxz.6166/);
- Nu77 - [@OTLand](https://otland.net/members/nu77.204078/);
- Peonso - [@OTLand](https://otland.net/members/peonso.5289/);
- Ranzor - [@OTLand](https://otland.net/members/ranzor.217381/);
- Sam Drost - [@OTLand](https://otland.net/members/sam-drost.223008/);
- Saphron - [@OTLand](https://otland.net/members/saphron.91841/), [@OTRealm](https://otrealm.com/index.php?members/saphron.14/);
- Sherice - [@OTLand](https://otland.net/members/sherice.150418/);
- Shiva Shadowsong - [@OTLand](https://otland.net/members/shadowsong.78705/), [@OTRealm](https://otrealm.com/index.php?members/shiva-shadowsong.15/);
- Slaake - [@TibiaKing](http://www.tibiaking.com/forum/profile/35794-slaake/);
- Somni - [@OTLand](https://otland.net/members/somni.6137/);
- Thorn - [@OTLand](https://otland.net/members/thorn.166053/), [@OTRealm](https://otrealm.com/index.php?members/thorn.130/);
- Tokume - [@PhobosForum](http://forum.phobosonline.com/memberlist.php?mode=viewprofile&u=1484);
- Venesis - [@OTLand](https://otland.net/members/venesis.23089/);
- wesleyt10 - [@XTibia](http://www.xtibia.com/forum/profile/360593-wesleyt10/);
- Way20 - [@OTLand](https://otland.net/threads/way-gallery.224923/);
- Weto - [@OTLand](https://otland.net/members/weto.209826/), [@TibiaKing](http://www.tibiaking.com/forum/profile/28864-ewerton-weto-costa/), [@XTibia](http://www.xtibia.com/forum/profile/226928-eltoo/);
- Worr - [@OTLand](https://otland.net/members/worr.110421/);

Some people didn't make the sprites themselves, but paid for them and made them available. The artist is already credited above, here the list of Mecenas:
- Sajgon - [@OTLand](https://otland.net/members/sajgon.221620/);

## OTClientV8 (MIT)

Ramki okien (`window.png`), przyciski dolnego paska (`button.png`) oraz nakładka pasków (`progressbar.png`),
małe okna (`miniwindow.png`, `miniwindow_buttons.png`), pola przedmiotów (`item.png`) i ramka panelu (`panel_map.png`) w `assets/ui/otc/` pochodzą z klienta **OTClientV8**:
https://github.com/OTCv8/otclientv8 (na bazie OTClient, https://github.com/edubart/otclient).

Copyright (c) 2010-2017 OTClient, Copyright (c) 2018-2023 OTClientV8. Licencja MIT,
pełny tekst: `assets/ui/otc/LICENSE-OTCV8.txt`. Pliki są użyte bez zmian; gra rysuje je jako
rozciągane ramki (9-patch). Nie użyto ikon z napisami ani grafik postaci z tego klienta.


