# Grafiki do podmiany

Obrazki gry są rysowane przez kod (`TileArt`). Plik PNG o nazwie z tej listy, położony w
`assets/art/`, zastępuje wygenerowany obrazek; brakujące pliki zostają wygenerowane, więc można podmieniać
po kawałku.

- PNG z przezroczystym tłem, kwadrat. 16×16 px albo 32×32 px (większe gra zmniejsza do 32 bez rozmywania).
- Kafelki podłoża wypełniają cały kwadrat; postacie, potwory i przedmioty stoją na przezroczystym tle,
  stopami przy dolnej krawędzi.
- Kolorowe stroje (/strój) są generowane z kolorów; profesja z podmienioną postacią zawsze wygląda jak na grafice.
- Grafiki muszą być własne albo na wolnej licencji (bez kopii z Tibii i innych gier komercyjnych).
- Kolumna „Jest”: plik już istnieje. Obecne pliki pochodzą z OpenTibia Sprite Pack (CC BY 4.0), autorzy w
  docs/CREDITS.md i w grze (/autorzy). Mapowanie nazw: `tools/art/otsp_import.py`.
- Podłoża z krawędziami (trawy, ziemia, piasek, glina, śnieg, lód, bruk) to autokafelki z paczki WsT
  (`assets/art/autotiles/`, `tools/art/wst_import.py`, warunki w docs/CREDITS.md). Pozostałe obrzeża: kawałki OTSP
  w `assets/art/borders/` albo składane z maski. Własne rysunki zostały tylko tam, gdzie paczka nic nie ma: stragan, łódź,
  ster, namiot orków, wiatrak (`tools/art/draw_town_props.py`).
- Interfejs ma dwa wyglądy (ustawienie „Wygląd interfejsu”): nowy z paczki Zidane42 (`assets/ui/zidane/`, paski,
  okna, przyciski, pola z ramkami rzadkości, przełączniki; `tools/art/zidane_import.py`, warunki w docs/CREDITS.md;
  elementy 9-slice, 1 piksel grafiki = 1 jednostka interfejsu, filtr Nearest) i klasyczny (panele i pola WsT,
  `assets/ui/wst/`, ramki OTClientV8). Ramka rzadkości wg ceny (`Rarity`): zwykły szary, niepospolity zielony,
  rzadki niebieski, epicki fioletowy, legendarny złoty; przedmiot tylko z bossów o stopień wyżej (legendarny z bossa -
  mityczny czerwony).
- Przedmioty o id 256-399 (zestawy Kogucie, Cukrowe, Zarazy, Zamieci, Smocze, Białego Słońca, Feniksa,
  Odmętów, Księżycowe, Anielskie, Piekielne i kilka pojedynczych) narysował **Hinas (OTLand) – przedmioty**:
  grafiki udostępnione publicznie bez licencji, użyte z podaniem autora (docs/CREDITS.md, `tools/art/hinas_import.py`).
- Runy 0.240 (id 400-423: pola, ściany, bomby, odtrutka, paraliż, przekonanie, ożywienie zwłok i inne) to przebarwione
  runy OTSP (leczenia, wybuchu ognia, lawiny, nagłej śmierci) - `tools/art/rune_tints.py`.

Razem: 144 kafelków, 374 przedmiotów, 127 potworów, 160 NPC, 16 obrazków postaci, 9 obiektów, 4 pupili. Podmienione: 804.

Lista jest generowana: `python3 tools/art/list_graphics.py`.

## Postacie graczy (`assets/art/characters/`)

Każda profesja w 4 kierunkach.

| Plik | Co to jest | Jest |
|---|---|---|
| `characters/paladin_down.png` | Paladyn, przód | tak |
| `characters/paladin_up.png` | Paladyn, tył | tak |
| `characters/paladin_left.png` | Paladyn, w lewo | tak |
| `characters/paladin_right.png` | Paladyn, w prawo | tak |
| `characters/druid_down.png` | Druid, przód | tak |
| `characters/druid_up.png` | Druid, tył | tak |
| `characters/druid_left.png` | Druid, w lewo | tak |
| `characters/druid_right.png` | Druid, w prawo | tak |
| `characters/sorcerer_down.png` | Czarodziej, przód | tak |
| `characters/sorcerer_up.png` | Czarodziej, tył | tak |
| `characters/sorcerer_left.png` | Czarodziej, w lewo | tak |
| `characters/sorcerer_right.png` | Czarodziej, w prawo | tak |
| `characters/monk_down.png` | Mnich, przód | tak |
| `characters/monk_up.png` | Mnich, tył | tak |
| `characters/monk_left.png` | Mnich, w lewo | tak |
| `characters/monk_right.png` | Mnich, w prawo | tak |

## Potwory (`assets/art/monsters/`)

| Plik | Co to jest | Jest |
|---|---|---|
| `monsters/abyss_crawler.png` | Czerw Otchłani, poziom 760 | tak |
| `monsters/abyss_lord.png` | Pan Otchłani, poziom 950 | tak |
| `monsters/abyssal_demon.png` | Demon Otchłani, poziom 800 | tak |
| `monsters/ash_magus.png` | Mag Popiołu, poziom 360 | tak |
| `monsters/bandit.png` | Bandyta, poziom 15 | tak |
| `monsters/barrow_lantern.png` | Latarnik z kurhanu, poziom 38 | tak |
| `monsters/blade_warden.png` | Wirujący strażnik, poziom 215 | tak |
| `monsters/bloodbeard.png` | Kapitan Krwawa Broda, poziom 420, boss | tak |
| `monsters/boar.png` | Dzik, poziom 15 | tak |
| `monsters/bog_wraith.png` | Bagienna zjawa, poziom 80 | tak |
| `monsters/bone_dragon.png` | Kościany smok, poziom 880 | tak |
| `monsters/burrow_larva.png` | Ryjąca larwa, poziom 9 | tak |
| `monsters/candle_acolyte.png` | Akolita Czarnej Świecy, poziom 30 | tak |
| `monsters/carrion_crow.png` | Padlinożerna wrona, poziom 11 | tak |
| `monsters/cave_centipede.png` | Jaskiniowa wij, poziom 16 | tak |
| `monsters/cave_troll.png` | Troll jaskiniowy, poziom 30 | tak |
| `monsters/cinder_salamander.png` | Popielna salamandra, poziom 420 | tak |
| `monsters/coral_crab.png` | Krab koralowy, poziom 310 | tak |
| `monsters/crimson_shade.png` | Krwawa zmora, poziom 265 | tak |
| `monsters/crocodile.png` | Krokodyl, poziom 22 | tak |
| `monsters/crystal_golem.png` | Kryształowy golem, poziom 45 | tak |
| `monsters/cursed_treant.png` | Przeklęty drzewiec, poziom 240 | tak |
| `monsters/deep_behemot.png` | Behemot, poziom 120 | tak |
| `monsters/deep_worm.png` | Czerw padlinożerny, poziom 20 | tak |
| `monsters/drowned_sailor.png` | Topielec, poziom 330 | tak |
| `monsters/dune_drake.png` | Wydmowy smok, poziom 410 | tak |
| `monsters/dune_zealot.png` | Fanatyk Białego Słońca, poziom 125 | tak |
| `monsters/dusk_gargoyle.png` | Zmierzchnik, poziom 620 | tak |
| `monsters/dwarf.png` | Krasnolud, poziom 10 | tak |
| `monsters/dwarf_earthcaller.png` | Krasnolud geomanta, poziom 34 | tak |
| `monsters/dwarf_miner.png` | Krasnolud górnik, poziom 12 | tak |
| `monsters/dwarf_soldier.png` | Krasnolud żołnierz, poziom 16 | tak |
| `monsters/dwarf_warden.png` | Krasnolud strażnik, poziom 26 | tak |
| `monsters/earth_spirit.png` | Żywiołak ziemi, poziom 55 | tak |
| `monsters/elf.png` | Elf, poziom 16 | tak |
| `monsters/elf_arcanist.png` | Elfi arkanista, poziom 30 | tak |
| `monsters/elf_scout.png` | Zwiadowca elfów, poziom 22 | tak |
| `monsters/ember_beetle.png` | Żarowy chrząszcz, poziom 335 | tak |
| `monsters/ember_hound.png` | Ogar Pożogi, poziom 520 | tak |
| `monsters/fire_elemental.png` | Żywiołak ognia, poziom 320 | tak |
| `monsters/forest_spider.png` | Pająk leśny, poziom 9 | tak |
| `monsters/frost_boar.png` | Szroniasty odyniec, poziom 48 | tak |
| `monsters/frost_centipede.png` | Lodowa wij, poziom 115 | tak |
| `monsters/frost_giant.png` | Mroźny olbrzym, poziom 430 | tak |
| `monsters/frost_queen.png` | Ysolde, Królowa Lodu, poziom 150, boss | tak |
| `monsters/frost_witch.png` | Mroźna wiedźma, poziom 75 | tak |
| `monsters/giant_rat.png` | Wielki szczur, poziom 5 | tak |
| `monsters/gilded_beetle.png` | Złotopancerz, poziom 105 | tak |
| `monsters/goblin.png` | Goblin, poziom 10 | tak |
| `monsters/goblin_shaman.png` | Goblin szaman, poziom 12 | tak |
| `monsters/grimtooth.png` | Grimtooth, Król Kopalni, poziom 60, boss | tak |
| `monsters/grove_crone.png` | Guślarka Gaju, poziom 22 | tak |
| `monsters/grove_huntress.png` | Łowczyni Gaju, poziom 18 | tak |
| `monsters/grove_matriarch.png` | Matrona Gaju, poziom 40 | tak |
| `monsters/grove_shieldmaiden.png` | Tarczowniczka Gaju, poziom 24 | tak |
| `monsters/hrimgar.png` | Hrimgar, Król Lodowców, poziom 560, boss | tak |
| `monsters/hyena.png` | Hiena, poziom 10 | tak |
| `monsters/ice_dragon.png` | Lodowy smok, poziom 520 | tak |
| `monsters/ice_golem.png` | Lodowy golem, poziom 120 | tak |
| `monsters/ignaroth.png` | Ignaroth Roztopiony, poziom 400, boss | tak |
| `monsters/jungle_hunter.png` | Łowca z dżungli, poziom 65 | tak |
| `monsters/jungle_serpent.png` | Wąż z dżungli, poziom 75 | tak |
| `monsters/khazuul.png` | Kha'zuul Pusty, poziom 750, boss | tak |
| `monsters/lava_golem.png` | Lawowy golem, poziom 370 | tak |
| `monsters/magma_colossus.png` | Magmowy kolos, poziom 620 | tak |
| `monsters/magma_drake.png` | Smok magmy, poziom 690 | tak |
| `monsters/mine_grub.png` | Czerw gnilny, poziom 8 | tak |
| `monsters/mist_wraith.png` | Upiór mgły, poziom 540 | tak |
| `monsters/morgath.png` | Mor'gath, Serce Otchłani, poziom 1000, boss | tak |
| `monsters/morvena.png` | Morvena, Pani Mgieł, poziom 640, boss | tak |
| `monsters/mummy.png` | Mumia, poziom 200 | tak |
| `monsters/obsidian_sentinel.png` | Obsydianowy wartownik, poziom 930 | tak |
| `monsters/oneeye_brute.png` | Jednooki olbrzym, poziom 30 | tak |
| `monsters/oneeye_hauler.png` | Jednooki tragarz, poziom 36 | tak |
| `monsters/oneeye_smith.png` | Jednooki kowal, poziom 42 | tak |
| `monsters/orc.png` | Ork, poziom 12 | tak |
| `monsters/orc_berserker.png` | Ork berserker, poziom 32 | tak |
| `monsters/orc_cult_fanatic.png` | Fanatyk orczego kultu, poziom 60 | tak |
| `monsters/orc_cult_priest.png` | Kapłan orczego kultu, poziom 55 | tak |
| `monsters/orc_leader.png` | Ork przywódca, poziom 44 | tak |
| `monsters/orc_marauder.png` | Ork maruder, poziom 36 | tak |
| `monsters/orc_rider.png` | Ork jeździec, poziom 28 | tak |
| `monsters/orc_shaman.png` | Orczy szaman, poziom 22 | tak |
| `monsters/orc_spearman.png` | Ork włócznik, poziom 16 | tak |
| `monsters/orc_warlord.png` | Gruzhak, Orczy Wódz, poziom 50 | tak |
| `monsters/orc_warrior.png` | Ork wojownik, poziom 18 | tak |
| `monsters/pharaoh.png` | Faraon Neferakh, poziom 230, boss | tak |
| `monsters/pirate_cutthroat.png` | Pirat rzezimieszek, poziom 350 | tak |
| `monsters/pirate_gunner.png` | Pirat kanonier, poziom 400 | tak |
| `monsters/pyrrax.png` | Pyrrax, Serce Wulkanu, poziom 780, boss | tak |
| `monsters/quarry_brute.png` | Łamacz kamieni, poziom 24 | tak |
| `monsters/rime_crawler.png` | Szronowy czerw, poziom 98 | tak |
| `monsters/rime_gargoyle.png` | Szronowy gargulec, poziom 500 | tak |
| `monsters/rock_golem.png` | Kamienny golem, poziom 32 | tak |
| `monsters/ruin_colossus.png` | Kolos z ruin, poziom 200, boss | tak |
| `monsters/sand_devourer.png` | Piaskowy pożeracz, poziom 64 | tak |
| `monsters/sand_gargoyle.png` | Piaskowy gargulec, poziom 165 | tak |
| `monsters/sand_titan.png` | Piaskowy tytan, poziom 650 | tak |
| `monsters/sand_wraith.png` | Piaskowy upiór, poziom 130 | tak |
| `monsters/scorpion.png` | Skorpion, poziom 14 | tak |
| `monsters/sewer_rat.png` | Szczur kanałowy, poziom 3 | tak |
| `monsters/shadow_centipede.png` | Cienista wij, poziom 680 | tak |
| `monsters/shadow_stalker.png` | Łowca cieni, poziom 590 | tak |
| `monsters/skaldra.png` | Skaldra, Lodowa Matka, poziom 540, boss | tak |
| `monsters/skeleton.png` | Szkielet, poziom 13 | tak |
| `monsters/snare_setter.png` | Sidlarz, poziom 14 | tak |
| `monsters/snow_wolf.png` | Śnieżny wilk, poziom 10 | tak |
| `monsters/spark_wisp.png` | Iskrzyk, poziom 26 | tak |
| `monsters/stone_sentinel.png` | Kamienny wartownik, poziom 140 | tak |
| `monsters/storm_harpy.png` | Harpia burzy, poziom 470 | tak |
| `monsters/storm_wisp.png` | Burzowy ognik, poziom 445 | tak |
| `monsters/striped_prowler.png` | Pręgowany skradacz, poziom 72 | tak |
| `monsters/suweren.png` | Suweren Ostrzy, poziom 640, boss | tak |
| `monsters/swamp_hornet.png` | Bagienny szerszeń, poziom 58 | tak |
| `monsters/szlamiara.png` | Szlamiara, Matka Topieli, poziom 95, boss | tak |
| `monsters/temple_guardian.png` | Strażnik świątyni, poziom 180 | tak |
| `monsters/tunnel_fence.png` | Paser z tuneli, poziom 34 | tak |
| `monsters/urgoth.png` | Urgoth, Pan Twierdzy, poziom 75, boss | tak |
| `monsters/velmira.png` | Velmira, Szkarłatna Pani, poziom 290, boss | tak |
| `monsters/void_priest.png` | Kapłan pustki, poziom 700 | tak |
| `monsters/winged_spawn.png` | Skrzydlaty pomiot, poziom 860 | tak |
| `monsters/wolf.png` | Wilk, poziom 8 | tak |
| `monsters/wraith_knight.png` | Upiorny rycerz, poziom 280 | tak |
| `monsters/wroniec.png` | Wroniec, Kruczy Kniaź, poziom 32, boss | tak |
| `monsters/wyvern.png` | Wiwerna, poziom 300 | tak |
| `monsters/wyvern_matriarch.png` | Matka Wiwern, poziom 360, boss | tak |
| `monsters/yeti.png` | Yeti, poziom 65 | tak |

## NPC (`assets/art/npcs/`)

NPC rysowani są z OpenTibia Sprite Pack: stroje ludzi z maską kolorów (`tools/art/otsp_import.py`, tabele `_ROLE`,
`RULER_COLORS`, `DWARF_NPCS`). Krasnoludy z Durnkaru (`durn_*`) to te same stroje ról z rudą/siwą brodą, ściśnięte w pionie
jak potwory `dwarf*`. Pliki są odtwarzalne: `python3 tools/art/otsp_import.py <client_files>`.

| Plik | Co to jest | Jest |
|---|---|---|
| `npcs/camp_banker.png` | Skarbniczka Wena | tak |
| `npcs/camp_guide.png` | Zwiadowczyni Ilka | tak |
| `npcs/camp_merchant.png` | Kwatermistrz Bren | tak |
| `npcs/coral_captain.png` | Sternik Lucjan | tak |
| `npcs/durn_armorer.png` | Płatnerz Hagar | tak |
| `npcs/durn_banker.png` | Bankier Grimbald | tak |
| `npcs/durn_carrier.png` | Wózkarz Brodrik | tak |
| `npcs/durn_guard_east.png` | Strażnik bramy Sigvald | tak |
| `npcs/durn_guard_west.png` | Strażnik bramy Hroald | tak |
| `npcs/durn_guide.png` | Przewodnik Dvalin | tak |
| `npcs/durn_housing.png` | Zarządca Durgan | tak |
| `npcs/durn_imbuer.png` | Zaklinacz Runnar | tak |
| `npcs/durn_innkeeper.png` | Karczmarz Ulfrik | tak |
| `npcs/durn_mage.png` | Runiarz Odvar | tak |
| `npcs/durn_merchant.png` | Kupiec Gunnar | tak |
| `npcs/durn_petkeeper.png` | Opiekun zwierząt Bodvar | tak |
| `npcs/durn_priest.png` | Kapłan Thorvin | tak |
| `npcs/durn_ruler.png` | Król Thorgrim | tak |
| `npcs/durn_smith.png` | Kowal Brokk | tak |
| `npcs/durn_tools.png` | Narzędziownik Skagi | tak |
| `npcs/east_captain.png` | Kapitan Wiesław | tak |
| `npcs/elandor_banker.png` | Skarbnik Faelar | tak |
| `npcs/elandor_bowyer.png` | Łuczarz Thalion | tak |
| `npcs/elandor_elder.png` | Starszy Aelindor | tak |
| `npcs/elandor_herbalist.png` | Zielarka Ilyana | tak |
| `npcs/elandor_priestess.png` | Kapłanka Liriel | tak |
| `npcs/frost_banker.png` | Bankier Frostholm | tak |
| `npcs/frost_captain.png` | Kapitan Ulf | tak |
| `npcs/frost_carrier.png` | Woźnica Frostholm | tak |
| `npcs/frost_guide.png` | Przewodnik Frostholm | tak |
| `npcs/frost_housing.png` | Zarządca Bjorn | tak |
| `npcs/frost_imbuer.png` | Zaklinaczka Ludwika | tak |
| `npcs/frost_innkeeper.png` | Karczmarka Sigrun | tak |
| `npcs/frost_mage.png` | Mag Runolf | tak |
| `npcs/frost_merchant.png` | Kupiec Frostholm | tak |
| `npcs/frost_petkeeper.png` | Opiekunka zwierząt Halszka | tak |
| `npcs/frost_priest.png` | Kapłan Frostholm | tak |
| `npcs/frost_ruler.png` | Królowa Świętosława | tak |
| `npcs/frost_smith.png` | Kowal Hrodgar | tak |
| `npcs/guard_1.png` | Strażniczka Wiesława | tak |
| `npcs/guard_10.png` | Kusznik Jarogniew | tak |
| `npcs/guard_11.png` | Strażnik Sobiesław | tak |
| `npcs/guard_12.png` | Kusznik Ziemowit | tak |
| `npcs/guard_13.png` | Kusznik Mirosław | tak |
| `npcs/guard_14.png` | Halabardnik Siemowit | tak |
| `npcs/guard_15.png` | Kusznik Wszebor | tak |
| `npcs/guard_16.png` | Kusznik Chwalibóg | tak |
| `npcs/guard_17.png` | Kusznik Przemysław | tak |
| `npcs/guard_18.png` | Kusznik Mścisław | tak |
| `npcs/guard_19.png` | Kusznik Domarad | tak |
| `npcs/guard_2.png` | Kusznik Budzisław | tak |
| `npcs/guard_20.png` | Halabardnik Dobromir | tak |
| `npcs/guard_21.png` | Strażnik Wielisław | tak |
| `npcs/guard_22.png` | Strażnik Bolesław | tak |
| `npcs/guard_23.png` | Strażnik Bronisław | tak |
| `npcs/guard_24.png` | Halabardnik Jaromir | tak |
| `npcs/guard_3.png` | Strażniczka Jaromira | tak |
| `npcs/guard_4.png` | Strażnik Unisław | tak |
| `npcs/guard_5.png` | Halabardnik Świętopełk | tak |
| `npcs/guard_6.png` | Łuczniczka Bolesława | tak |
| `npcs/guard_7.png` | Strażniczka Dobrosława | tak |
| `npcs/guard_8.png` | Strażniczka Sławomira | tak |
| `npcs/guard_9.png` | Strażnik Gniewomir | tak |
| `npcs/gull_ferryman.png` | Przewoźnik Bolko | tak |
| `npcs/ice_captain.png` | Kapitan Torvald | tak |
| `npcs/isle0_ferryman.png` | Przewoźnik Gniewko | tak |
| `npcs/isle10_ferryman.png` | Przewoźnik Racibor | tak |
| `npcs/isle11_ferryman.png` | Przewoźniczka Bogumiła | tak |
| `npcs/isle12_ferryman.png` | Przewoźnik Dobromir | tak |
| `npcs/isle13_ferryman.png` | Przewoźniczka Sława | tak |
| `npcs/isle14_ferryman.png` | Przewoźnik Ciechosław | tak |
| `npcs/isle15_ferryman.png` | Przewoźniczka Grzymisława | tak |
| `npcs/isle16_ferryman.png` | Przewoźnik Mirogniew | tak |
| `npcs/isle17_ferryman.png` | Przewoźniczka Wanda | tak |
| `npcs/isle1_ferryman.png` | Przewoźniczka Jagna | tak |
| `npcs/isle2_ferryman.png` | Przewoźnik Wojsław | tak |
| `npcs/isle3_ferryman.png` | Przewoźniczka Dobrawa | tak |
| `npcs/isle4_ferryman.png` | Przewoźnik Sambor | tak |
| `npcs/isle5_ferryman.png` | Przewoźniczka Mila | tak |
| `npcs/isle6_ferryman.png` | Przewoźnik Bratumił | tak |
| `npcs/isle7_ferryman.png` | Przewoźniczka Ludmiła | tak |
| `npcs/isle8_ferryman.png` | Przewoźnik Zbysław | tak |
| `npcs/isle9_ferryman.png` | Przewoźniczka Wiesna | tak |
| `npcs/isle_guide.png` | Admirał Wenda | tak |
| `npcs/isle_merchant.png` | Kwatermistrz Olaf | tak |
| `npcs/lighthouse_ferryman.png` | Latarniczka Wanda | tak |
| `npcs/mist_captain.png` | Przewoźnik Gniewko | tak |
| `npcs/nerys_banker.png` | Bankierka Selma | tak |
| `npcs/nerys_captain.png` | Kapitanka Maren | tak |
| `npcs/nerys_carrier.png` | Woźnica Nerys | tak |
| `npcs/nerys_guide.png` | Przewodnik Nerys | tak |
| `npcs/nerys_housing.png` | Zarządca Ilian | tak |
| `npcs/nerys_imbuer.png` | Zaklinaczka Aniela | tak |
| `npcs/nerys_innkeeper.png` | Karczmarka Lirena | tak |
| `npcs/nerys_mage.png` | Czarodziejka Ysolde | tak |
| `npcs/nerys_merchant.png` | Kupiec Nerys | tak |
| `npcs/nerys_petkeeper.png` | Opiekunka zwierząt Lilia | tak |
| `npcs/nerys_priest.png` | Kapłan Nerysa | tak |
| `npcs/nerys_ruler.png` | Królowa Jadwiga | tak |
| `npcs/nerys_smith.png` | Płatnerz Corvin | tak |
| `npcs/oaza_merchant.png` | Handlarz Karim | tak |
| `npcs/osada_merchant.png` | Handlarka Jagna | tak |
| `npcs/pirate_captain.png` | Kapitan Czarna Mewa | tak |
| `npcs/pozoga_captain.png` | Kapitan Ignacy Smolarz | tak |
| `npcs/qas_banker.png` | Bankier Qasirah | tak |
| `npcs/qas_captain.png` | Kapitan Rahim | tak |
| `npcs/qas_carrier.png` | Woźnica Qasirah | tak |
| `npcs/qas_guide.png` | Przewodnik Qasirah | tak |
| `npcs/qas_housing.png` | Zarządca Karim | tak |
| `npcs/qas_imbuer.png` | Zaklinaczka Zofia | tak |
| `npcs/qas_innkeeper.png` | Handlarz Omar | tak |
| `npcs/qas_mage.png` | Wróżbitka Leila | tak |
| `npcs/qas_merchant.png` | Kupiec Qasirah | tak |
| `npcs/qas_petkeeper.png` | Opiekun zwierząt Kasper | tak |
| `npcs/qas_priest.png` | Kapłan Qasirah | tak |
| `npcs/qas_ruler.png` | Król Mieszko | tak |
| `npcs/qas_smith.png` | Płatnerz Hamid | tak |
| `npcs/selv_banker.png` | Bankier Selvaran | tak |
| `npcs/selv_captain.png` | Kapitan Bartosz | tak |
| `npcs/selv_carrier.png` | Woźnica Selvaran | tak |
| `npcs/selv_fishmonger.png` | Rybaczka Mara | tak |
| `npcs/selv_guide.png` | Przewodnik Selvaran | tak |
| `npcs/selv_housing.png` | Zarządca Tomasz | tak |
| `npcs/selv_imbuer.png` | Zaklinacz Ignacy | tak |
| `npcs/selv_mage.png` | Zielarka Selvaran | tak |
| `npcs/selv_merchant.png` | Kupiec Selvaran | tak |
| `npcs/selv_petkeeper.png` | Opiekun zwierząt Florian | tak |
| `npcs/selv_priest.png` | Kapłan Selvaran | tak |
| `npcs/selv_ruler.png` | Lord Radosław | tak |
| `npcs/selv_smith.png` | Płatnerz Selvaran | tak |
| `npcs/valmir_banker.png` | Bankier Ludomir | tak |
| `npcs/valmir_captain.png` | Kapitan Radomir | tak |
| `npcs/valmir_carrier.png` | Woźnica Valmiru | tak |
| `npcs/valmir_guide.png` | Przewodniczka Valmiru | tak |
| `npcs/valmir_housing.png` | Zarządczyni Grażyna | tak |
| `npcs/valmir_imbuer.png` | Zaklinacz Hieronim | tak |
| `npcs/valmir_innkeeper.png` | Karczmarz Bogdan | tak |
| `npcs/valmir_mage.png` | Czarodziej Wiesław | tak |
| `npcs/valmir_merchant.png` | Kupiec Zbyszko | tak |
| `npcs/valmir_petkeeper.png` | Opiekunka zwierząt Marianna | tak |
| `npcs/valmir_priest.png` | Kapłanka Mirosława | tak |
| `npcs/valmir_ruler.png` | Lord Bogumir | tak |
| `npcs/valmir_smith.png` | Kowalka Dobrawa | tak |
| `npcs/varden_archmage.png` | Arcymag Eldrin | tak |
| `npcs/varden_banker.png` | Bankier Aldric | tak |
| `npcs/varden_captain.png` | Kapitan Edmund | tak |
| `npcs/varden_carrier.png` | Woźnica Jarek | tak |
| `npcs/varden_guide.png` | Strażnik Oren | tak |
| `npcs/varden_housing.png` | Zarządca Leopold | tak |
| `npcs/varden_imbuer.png` | Zaklinacz Teodor | tak |
| `npcs/varden_innkeeper.png` | Karczmarz Wit | tak |
| `npcs/varden_king.png` | Król Aurelian | tak |
| `npcs/varden_mage.png` | Czarodziejka Mirela | tak |
| `npcs/varden_merchant.png` | Kupiec Tomas | tak |
| `npcs/varden_petkeeper.png` | Opiekun zwierząt Maciej | tak |
| `npcs/varden_priest.png` | Kapłan Benedykt | tak |
| `npcs/varden_smith.png` | Kowal Bogdan | tak |
| `npcs/volcano_captain.png` | Kapitan Ognik | tak |
| `npcs/wierzbowo_merchant.png` | Sołtys Bogumił | tak |
| `npcs/wola_merchant.png` | Rybaczka Halina | tak |

## Przedmioty (`assets/art/items/`)

| Plik | Co to jest | Jest |
|---|---|---|
| `items/abyss_hammer.png` | Młot Otchłani (weapon) | tak |
| `items/abyssal_ember.png` | Żar Otchłani (material) | tak |
| `items/abyssal_greatsword.png` | Wielki miecz Otchłani (weapon) | tak |
| `items/abyssal_treads.png` | Kroki Otchłani (armor) |  |
| `items/angel_amulet.png` | Anielski amulet (armor) | tak |
| `items/angel_armor.png` | Anielska zbroja (armor) | tak |
| `items/angel_axe.png` | Anielski topór (weapon) | tak |
| `items/angel_boots.png` | Anielskie buty (armor) | tak |
| `items/angel_bow.png` | Anielski łuk (weapon) | tak |
| `items/angel_helmet.png` | Anielski hełm (armor) | tak |
| `items/angel_legs.png` | Anielskie nagolenniki (armor) | tak |
| `items/angel_rod.png` | Anielska laska (weapon) | tak |
| `items/angel_shield.png` | Anielska tarcza (shield) | tak |
| `items/angel_spellbook.png` | Anielska księga (shield) | tak |
| `items/angel_sword.png` | Anielski miecz (weapon) | tak |
| `items/angel_wand.png` | Anielska różdżka (weapon) | tak |
| `items/animate_dead_rune.png` | Runa ożywienia zwłok (rune) | tak |
| `items/antidote_rune.png` | Runa odtrutki (rune) | tak |
| `items/avalanche_rune.png` | Runa lawiny (rune) | tak |
| `items/backpack.png` | Plecak (tool) | tak |
| `items/barbarian_axe.png` | Topór barbarzyńcy (weapon) | tak |
| `items/battle_axe.png` | Topór bojowy (weapon) | tak |
| `items/black_feather.png` | Czarne pióro (material) | tak |
| `items/blank_rune.png` | Pusta runa (rune) | tak |
| `items/blizzard_amulet.png` | Amulet zamieci (armor) | tak |
| `items/blizzard_armor.png` | Zbroja zamieci (armor) | tak |
| `items/blizzard_axe.png` | Topór zamieci (weapon) | tak |
| `items/blizzard_boots.png` | Buty zamieci (armor) | tak |
| `items/blizzard_bow.png` | Łuk zamieci (weapon) | tak |
| `items/blizzard_helmet.png` | Hełm zamieci (armor) | tak |
| `items/blizzard_legs.png` | Nagolenniki zamieci (armor) | tak |
| `items/blizzard_rod.png` | Laska zamieci (weapon) | tak |
| `items/blizzard_shield.png` | Tarcza zamieci (shield) | tak |
| `items/blizzard_spellbook.png` | Księga zamieci (shield) | tak |
| `items/blizzard_sword.png` | Ostrze zamieci (weapon) | tak |
| `items/blizzard_wand.png` | Różdżka zamieci (weapon) | tak |
| `items/blueberry.png` | Jagody (food) |  |
| `items/boar_tusk.png` | Kieł dzika (material) | tak |
| `items/bone.png` | Kość (material) | tak |
| `items/bone_shield.png` | Kościana tarcza (shield) | tak |
| `items/boulder_rune.png` | Runa stalagmitu (rune) | tak |
| `items/bread.png` | Chleb (food) | tak |
| `items/candy_amulet.png` | Cukierkowy naszyjnik (armor) | tak |
| `items/candy_armor.png` | Czekoladowa zbroja (armor) | tak |
| `items/candy_boots.png` | Cukrowe buty (armor) | tak |
| `items/candy_bow.png` | Cukrowy łuk (weapon) | tak |
| `items/candy_helmet.png` | Babeczkowy hełm (armor) | tak |
| `items/candy_legs.png` | Cukrowe nagolenniki (armor) | tak |
| `items/candy_shield.png` | Lizakowa tarcza (shield) | tak |
| `items/candy_spellbook.png` | Cukrowa księga (shield) | tak |
| `items/candy_sword.png` | Cukrowy miecz (weapon) | tak |
| `items/candy_wand.png` | Cukrowa różdżka (weapon) | tak |
| `items/challenge_scroll.png` | Zwój wyzwania (tool) | tak |
| `items/chitin_plate.png` | Płytka pancerza (material) | tak |
| `items/cloth_wraps.png` | Płócienne owijki (weapon) | tak |
| `items/colossus_core.png` | Rdzeń kolosa (material) | tak |
| `items/composite_bow.png` | Łuk kompozytowy (weapon) | tak |
| `items/convince_creature_rune.png` | Runa przekonania (rune) | tak |
| `items/coral_armor.png` | Koralowa zbroja (armor) | tak |
| `items/coral_boots.png` | Koralowe buty (armor) |  |
| `items/coral_helmet.png` | Hełm głębin (armor) | tak |
| `items/coral_legs.png` | Koralowe nagolenniki (armor) | tak |
| `items/coral_shield.png` | Tarcza z muszli (shield) | tak |
| `items/coral_warhammer.png` | Koralowy młot (weapon) | tak |
| `items/crab_pincer.png` | Szczypce kraba koralowego (material) | tak |
| `items/croc_leather.png` | Skóra krokodyla (material) | tak |
| `items/croc_legs.png` | Krokodyle spodnie (armor) | tak |
| `items/crossbow.png` | Kusza (weapon) | tak |
| `items/crow_feather_hood.png` | Kaptur z kruczych piór (armor) | tak |
| `items/crown_of_the_abyss.png` | Korona Otchłani (armor) | tak |
| `items/crusader_axe.png` | Topór krzyżowca (weapon) | tak |
| `items/crystal_coin.png` | Kryształowa moneta (currency) | tak |
| `items/crystal_shard.png` | Odłamek kryształu (material) | tak |
| `items/crystal_shortsword.png` | Kryształowy krótki miecz (weapon) | tak |
| `items/crystal_sword.png` | Kryształowy miecz (weapon) | tak |
| `items/dawn_bow.png` | Łuk świtu (weapon) | tak |
| `items/dawnspear.png` | Włócznia świtu (weapon) | tak |
| `items/deep_amulet.png` | Amulet odmętów (armor) | tak |
| `items/deep_armor.png` | Zbroja odmętów (armor) | tak |
| `items/deep_boots.png` | Buty odmętów (armor) | tak |
| `items/deep_bow.png` | Łuk odmętów (weapon) | tak |
| `items/deep_hammer.png` | Młot odmętów (weapon) | tak |
| `items/deep_helmet.png` | Hełm odmętów (armor) | tak |
| `items/deep_legs.png` | Nagolenniki odmętów (armor) | tak |
| `items/deep_rod.png` | Laska odmętów (weapon) | tak |
| `items/deep_shield.png` | Tarcza odmętów (shield) | tak |
| `items/deep_spellbook.png` | Księga odmętów (shield) | tak |
| `items/deep_trident.png` | Trójząb odmętów (weapon) | tak |
| `items/deep_wand.png` | Różdżka odmętów (weapon) | tak |
| `items/desert_knuckles.png` | Pustynne kastety (weapon) | tak |
| `items/desert_scimitar.png` | Pustynny sejmitar (weapon) | tak |
| `items/destroy_field_rune.png` | Runa niszczenia pól (rune) | tak |
| `items/dragon_amulet.png` | Smoczy talizman (armor) | tak |
| `items/dragon_armor.png` | Smocza zbroja (armor) | tak |
| `items/dragon_axe.png` | Smoczy topór (weapon) | tak |
| `items/dragon_boots.png` | Smocze buty (armor) | tak |
| `items/dragon_bow.png` | Smoczy łuk (weapon) | tak |
| `items/dragon_helmet.png` | Smoczy hełm (armor) | tak |
| `items/dragon_legs.png` | Smocze nagolenniki (armor) | tak |
| `items/dragon_rod.png` | Smocza laska (weapon) | tak |
| `items/dragon_shield.png` | Smocza tarcza (shield) | tak |
| `items/dragon_spellbook.png` | Księga gaju (shield) | tak |
| `items/dragon_sword.png` | Smoczy miecz (weapon) | tak |
| `items/dragon_wand.png` | Smocza różdżka (weapon) | tak |
| `items/drake_horn.png` | Róg magmowego smoka (material) | tak |
| `items/drowned_ring.png` | Pierścień topielca (material) | tak |
| `items/ectoplasm.png` | Ektoplazma (material) | tak |
| `items/elven_bow.png` | Elfi łuk (weapon) | tak |
| `items/emerald_amulet.png` | Szmaragdowy amulet (armor) | tak |
| `items/energy_bomb_rune.png` | Runa bomby energii (rune) | tak |
| `items/energy_field_rune.png` | Runa pola energii (rune) | tak |
| `items/energy_wall_rune.png` | Runa ściany energii (rune) | tak |
| `items/exalted_core.png` | Wzniosły rdzeń (material) |  |
| `items/exp_scroll.png` | Zwój nauki (tool) | tak |
| `items/explosion_rune.png` | Runa eksplozji (rune) | tak |
| `items/fire_bomb_rune.png` | Runa bomby ognia (rune) | tak |
| `items/fire_field_rune.png` | Runa pola ognia (rune) | tak |
| `items/fire_wall_rune.png` | Runa ściany ognia (rune) | tak |
| `items/fireburst_rune.png` | Runa kuli ognia (rune) | tak |
| `items/fish.png` | Ryba (food) |  |
| `items/fishing_rod.png` | Wędka (tool) | tak |
| `items/fists_of_the_abyss.png` | Pięści Otchłani (weapon) | tak |
| `items/forest_hood.png` | Leśny kaptur (armor) | tak |
| `items/forge_sliver.png` | Odłamek kuźni (material) |  |
| `items/frost_blade.png` | Mroźne ostrze (weapon) | tak |
| `items/frost_bow.png` | Łuk mroźnego kła (weapon) | tak |
| `items/frost_longsword.png` | Mroźny miecz długi (weapon) | tak |
| `items/frost_pelt.png` | Mroźne futro (material) | tak |
| `items/frost_tiara.png` | Mroźny diadem (armor) | tak |
| `items/frostbound_fists.png` | Skute lodem pięści (weapon) | tak |
| `items/frostfang_spear.png` | Włócznia mroźnego kła (weapon) | tak |
| `items/frozen_heart.png` | Zamarznięte serce (material) | tak |
| `items/fur_armor.png` | Futrzana zbroja (armor) | tak |
| `items/gargoyle_shard.png` | Odłamek gargulca (material) | tak |
| `items/gem_shield.png` | Tarcza z klejnotem (shield) | tak |
| `items/glacial_boots.png` | Lodowe buty (armor) |  |
| `items/glacial_core.png` | Lodowy rdzeń (material) | tak |
| `items/glacial_helm.png` | Lodowy hełm (armor) | tak |
| `items/glacial_legs.png` | Lodowe nagolenniki (armor) | tak |
| `items/glacial_plate.png` | Lodowa płytówka (armor) | tak |
| `items/glacial_shield.png` | Lodowa tarcza (shield) | tak |
| `items/glacier_axe.png` | Lodowcowy topór (weapon) | tak |
| `items/glacier_crossbow.png` | Kusza lodowca (weapon) | tak |
| `items/goblin_ear.png` | Ucho goblina (material) | tak |
| `items/gold_coin.png` | Złota moneta (currency) | tak |
| `items/great_fireball_rune.png` | Runa wielkiej kuli ognia (rune) | tak |
| `items/great_health_potion.png` | Silna mikstura zdrowia (potion) | tak |
| `items/great_mana_potion.png` | Silna mikstura many (potion) | tak |
| `items/great_spirit_potion.png` | Silna mikstura ducha (potion) | tak |
| `items/greater_gem_fragment.png` | Większy okruch klejnotu (material) | tak |
| `items/greater_guardian_gem.png` | Większy klejnot strażnika (material) | tak |
| `items/greater_marksman_gem.png` | Większy klejnot strzelca (material) | tak |
| `items/greater_mystic_gem.png` | Większy klejnot mistyka (material) | tak |
| `items/greater_sage_gem.png` | Większy klejnot mędrca (material) | tak |
| `items/greater_spiritualist_gem.png` | Większy klejnot spirytualisty (material) | tak |
| `items/grimtooth_crown.png` | Korona Grimtootha (armor) | tak |
| `items/guardian_gem.png` | Klejnot strażnika (material) | tak |
| `items/hand_axe.png` | Toporek (weapon) | tak |
| `items/healing_herb.png` | Ziele lecznicze (material) | tak |
| `items/healing_rune.png` | Runa silnego leczenia (rune) | tak |
| `items/health_potion.png` | Mikstura zdrowia (potion) | tak |
| `items/heavy_crossbow.png` | Ciężka kusza (weapon) | tak |
| `items/heavy_magic_missile_rune.png` | Runa ciężkiego pocisku (rune) | tak |
| `items/holy_missile_rune.png` | Runa świętego pocisku (rune) | tak |
| `items/horned_helmet.png` | Rogaty hełm (armor) | tak |
| `items/hunting_bow.png` | Łuk myśliwski (weapon) | tak |
| `items/hunting_spear.png` | Włócznia myśliwska (weapon) | tak |
| `items/hyena_fang.png` | Kieł hieny (material) | tak |
| `items/ice_cod.png` | Lodowy dorsz (food) |  |
| `items/icicle_rune.png` | Runa sopla (rune) | tak |
| `items/infernal_amulet.png` | Piekielny amulet (armor) | tak |
| `items/infernal_armor.png` | Piekielna zbroja (armor) | tak |
| `items/infernal_axe.png` | Piekielny topór (weapon) | tak |
| `items/infernal_boots.png` | Piekielne buty (armor) | tak |
| `items/infernal_bow.png` | Piekielny łuk (weapon) | tak |
| `items/infernal_helmet.png` | Piekielny hełm (armor) | tak |
| `items/infernal_legs.png` | Piekielne nagolenniki (armor) | tak |
| `items/infernal_rod.png` | Piekielna laska (weapon) | tak |
| `items/infernal_shield.png` | Piekielna tarcza (shield) | tak |
| `items/infernal_spellbook.png` | Piekielna księga (shield) | tak |
| `items/infernal_sword.png` | Piekielny miecz (weapon) | tak |
| `items/infernal_wand.png` | Piekielna różdżka (weapon) | tak |
| `items/iron_knuckles.png` | Żelazne kastety (weapon) | tak |
| `items/iron_ore.png` | Ruda żelaza (material) | tak |
| `items/iron_sword.png` | Żelazny miecz (weapon) | tak |
| `items/jungle_idol.png` | Posążek z dżungli (material) | tak |
| `items/knight_sword.png` | Miecz rycerski (weapon) | tak |
| `items/leather_armor.png` | Skórzana zbroja (armor) | tak |
| `items/leather_boots.png` | Skórzane buty (armor) | tak |
| `items/leather_helmet.png` | Skórzany hełm (armor) | tak |
| `items/leather_legs.png` | Skórzane spodnie (armor) | tak |
| `items/lesser_gem_fragment.png` | Mniejszy okruch klejnotu (material) | tak |
| `items/lesser_guardian_gem.png` | Mniejszy klejnot strażnika (material) | tak |
| `items/lesser_marksman_gem.png` | Mniejszy klejnot strzelca (material) | tak |
| `items/lesser_mystic_gem.png` | Mniejszy klejnot mistyka (material) | tak |
| `items/lesser_sage_gem.png` | Mniejszy klejnot mędrca (material) | tak |
| `items/lesser_spiritualist_gem.png` | Mniejszy klejnot spirytualisty (material) | tak |
| `items/light_magic_missile_rune.png` | Runa lekkiego pocisku (rune) | tak |
| `items/luck_potion.png` | Mikstura szczęścia (potion) | tak |
| `items/mace.png` | Buzdygan (weapon) | tak |
| `items/magic_wall_rune.png` | Runa magicznej ściany (rune) | tak |
| `items/magma_bow.png` | Magmowy łuk (weapon) | tak |
| `items/magma_cleaver.png` | Magmowy tasak (weapon) | tak |
| `items/magma_spear.png` | Magmowa włócznia (weapon) | tak |
| `items/mana_potion.png` | Mikstura many (potion) | tak |
| `items/marksman_gem.png` | Klejnot strzelca (material) | tak |
| `items/meat.png` | Mięso (food) | tak |
| `items/miner_helmet.png` | Hełm górnika (armor) | tak |
| `items/mirewood_rod.png` | Laska z Topieli (weapon) | tak |
| `items/mist_essence.png` | Esencja mgły (material) | tak |
| `items/molten_knuckles.png` | Stopione kastety (weapon) | tak |
| `items/moon_amulet.png` | Księżycowy amulet (armor) | tak |
| `items/moon_armor.png` | Księżycowa zbroja (armor) | tak |
| `items/moon_axe.png` | Księżycowy topór (weapon) | tak |
| `items/moon_boots.png` | Księżycowe buty (armor) | tak |
| `items/moon_bow.png` | Księżycowy łuk (weapon) | tak |
| `items/moon_helmet.png` | Księżycowy hełm (armor) | tak |
| `items/moon_legs.png` | Księżycowe nagolenniki (armor) | tak |
| `items/moon_shield.png` | Księżycowa tarcza (shield) | tak |
| `items/moon_spellbook.png` | Księżycowa księga (shield) | tak |
| `items/moon_sword.png` | Księżycowy miecz (weapon) | tak |
| `items/moon_wand.png` | Księżycowa różdżka (weapon) | tak |
| `items/mummy_wrap.png` | Bandaż mumii (material) | tak |
| `items/mystic_gem.png` | Klejnot mistyka (material) | tak |
| `items/oasis_carp.png` | Karp z oazy (food) |  |
| `items/obsidian_armor.png` | Obsydianowa zbroja (armor) | tak |
| `items/obsidian_axe.png` | Obsydianowy topór (weapon) | tak |
| `items/obsidian_boots.png` | Obsydianowe buty (armor) |  |
| `items/obsidian_helmet.png` | Obsydianowy hełm (armor) | tak |
| `items/obsidian_legs.png` | Obsydianowe nagolenniki (armor) | tak |
| `items/obsidian_shard.png` | Odłamek obsydianu (material) | tak |
| `items/obsidian_shield.png` | Obsydianowa tarcza (shield) | tak |
| `items/obsidian_wand.png` | Obsydianowa różdżka (weapon) | tak |
| `items/paralyse_rune.png` | Runa paraliżu (rune) | tak |
| `items/pearl.png` | Perła (material) | tak |
| `items/pet_cat.png` | Kocię (tool) | tak |
| `items/pet_dog.png` | Szczenię (tool) | tak |
| `items/pet_fennec.png` | Fenek (tool) | tak |
| `items/pet_gull.png` | Mewka (tool) | tak |
| `items/pet_parrot.png` | Papużka (tool) | tak |
| `items/pet_raven.png` | Kruk (tool) | tak |
| `items/pharaoh_scepter.png` | Berło faraona (weapon) | tak |
| `items/phoenix_amulet.png` | Amulet feniksa (armor) | tak |
| `items/phoenix_armor.png` | Zbroja feniksa (armor) | tak |
| `items/phoenix_axe.png` | Topór feniksa (weapon) | tak |
| `items/phoenix_boots.png` | Buty feniksa (armor) | tak |
| `items/phoenix_bow.png` | Łuk feniksa (weapon) | tak |
| `items/phoenix_helmet.png` | Hełm feniksa (armor) | tak |
| `items/phoenix_legs.png` | Nagolenniki feniksa (armor) | tak |
| `items/phoenix_mace.png` | Buzdygan feniksa (weapon) | tak |
| `items/phoenix_shield.png` | Tarcza feniksa (shield) | tak |
| `items/phoenix_spellbook.png` | Księga feniksa (shield) | tak |
| `items/phoenix_sword.png` | Miecz feniksa (weapon) | tak |
| `items/phoenix_wand.png` | Różdżka feniksa (weapon) | tak |
| `items/pirate_hook.png` | Hak pirata (material) | tak |
| `items/plague_armor.png` | Zbroja zarazy (armor) | tak |
| `items/plague_axe.png` | Topór zarazy (weapon) | tak |
| `items/plague_boots.png` | Buty zarazy (armor) | tak |
| `items/plague_bow.png` | Łuk zarazy (weapon) | tak |
| `items/plague_helmet.png` | Hełm zarazy (armor) | tak |
| `items/plague_legs.png` | Nagolenniki zarazy (armor) | tak |
| `items/plague_mask.png` | Maska zarazy (armor) | tak |
| `items/plague_rod.png` | Laska zarazy (weapon) | tak |
| `items/plague_shield.png` | Tarcza zarazy (shield) | tak |
| `items/plague_spellbook.png` | Księga zarazy (shield) | tak |
| `items/plague_sword.png` | Ostrze zarazy (weapon) | tak |
| `items/plague_wand.png` | Różdżka zarazy (weapon) | tak |
| `items/platinum_coin.png` | Platynowa moneta (currency) | tak |
| `items/poison_bomb_rune.png` | Runa bomby trucizny (rune) | tak |
| `items/poison_field_rune.png` | Runa pola trucizny (rune) | tak |
| `items/poison_wall_rune.png` | Runa ściany trucizny (rune) | tak |
| `items/powder_horn.png` | Róg z prochem (material) | tak |
| `items/raspberry.png` | Maliny (food) |  |
| `items/reef_gauntlets.png` | Rękawice rafy (weapon) | tak |
| `items/return_scroll.png` | Zwój powrotu (tool) | tak |
| `items/rod_of_eternal_winter.png` | Laska wiecznej zimy (weapon) | tak |
| `items/rod_of_frost.png` | Laska mrozu (weapon) | tak |
| `items/rod_of_hoarfrost.png` | Laska szronu (weapon) | tak |
| `items/rod_of_stoneroot.png` | Laska kamiennego korzenia (weapon) | tak |
| `items/rod_of_tides.png` | Laska przypływów (weapon) | tak |
| `items/rod_of_twilight.png` | Laska zmierzchu (weapon) | tak |
| `items/rod_of_vines.png` | Laska pnączy (weapon) | tak |
| `items/rooster_amulet.png` | Koguci amulet (armor) | tak |
| `items/rooster_armor.png` | Kogucia zbroja (armor) | tak |
| `items/rooster_boots.png` | Kogucie buty (armor) | tak |
| `items/rooster_bow.png` | Koguci łuk (weapon) | tak |
| `items/rooster_club.png` | Kogucia pałka (weapon) | tak |
| `items/rooster_helmet.png` | Koguci hełm (armor) | tak |
| `items/rooster_legs.png` | Kogucie nagolenniki (armor) | tak |
| `items/rooster_rod.png` | Kogucia laska (weapon) | tak |
| `items/rooster_shield.png` | Kogucia tarcza (shield) | tak |
| `items/rooster_spellbook.png` | Kogucia księga (shield) | tak |
| `items/rooster_sword.png` | Koguci miecz (weapon) | tak |
| `items/rooster_wand.png` | Kogucia różdżka (weapon) | tak |
| `items/rope.png` | Lina (tool) | tak |
| `items/saddle.png` | Siodło (tool) | tak |
| `items/sage_gem.png` | Klejnot mędrca (material) | tak |
| `items/sandstone_shield.png` | Tarcza z piaskowca (shield) | tak |
| `items/scarab_gem.png` | Klejnot skarabeusza (material) | tak |
| `items/scarab_helmet.png` | Hełm skarabeusza (armor) | tak |
| `items/scorpion_tail.png` | Ogon skorpiona (material) | tak |
| `items/serpent_bow.png` | Wężowy łuk (weapon) | tak |
| `items/serpent_spear.png` | Wężowa włócznia (weapon) | tak |
| `items/shadow_aegis.png` | Egida cienia (shield) | tak |
| `items/shadow_boots.png` | Buty cienia (armor) |  |
| `items/shadow_bow.png` | Łuk cienia (weapon) | tak |
| `items/shadow_essence.png` | Esencja cienia (material) | tak |
| `items/shadow_hood.png` | Kaptur cienia (armor) | tak |
| `items/shadow_legs.png` | Spodnie cienia (armor) | tak |
| `items/shadowpiercer.png` | Przebijacz cienia (weapon) | tak |
| `items/shadowweave_armor.png` | Zbroja z cienia (armor) | tak |
| `items/short_bow.png` | Krótki łuk (weapon) | tak |
| `items/shovel.png` | Łopata (tool) | tak |
| `items/skull_axe.png` | Topór czaszek (weapon) | tak |
| `items/snake_skin.png` | Wężowa skóra (material) | tak |
| `items/soulfire_rune.png` | Runa ognia duszy (rune) | tak |
| `items/spear.png` | Włócznia (ammunition) | tak |
| `items/spider_silk.png` | Pajęcza nić (material) | tak |
| `items/spiritualist_gem.png` | Klejnot spirytualisty (material) | tak |
| `items/starfall_wand.png` | Różdżka spadających gwiazd (weapon) | tak |
| `items/steel_spear.png` | Stalowa włócznia (weapon) | tak |
| `items/stone_shower_rune.png` | Runa deszczu kamieni (rune) | tak |
| `items/stoneskin_potion.png` | Mikstura kamiennej skóry (potion) | tak |
| `items/stormcaller_wand.png` | Różdżka wołająca burzę (weapon) | tak |
| `items/strength_potion.png` | Mikstura siły (potion) | tak |
| `items/strong_health_potion.png` | Potężna mikstura zdrowia (potion) | tak |
| `items/strong_mana_potion.png` | Potężna mikstura many (potion) | tak |
| `items/studded_armor.png` | Ćwiekowana zbroja (armor) | tak |
| `items/sudden_death_rune.png` | Runa nagłej śmierci (rune) | tak |
| `items/sun_amulet.png` | Talizman słońca (armor) | tak |
| `items/sun_armor.png` | Słoneczna zbroja (armor) | tak |
| `items/sun_axe.png` | Słoneczny topór (weapon) | tak |
| `items/sun_boots.png` | Słoneczne buty (armor) | tak |
| `items/sun_bow.png` | Słoneczny łuk (weapon) | tak |
| `items/sun_helmet.png` | Hełm kobry (armor) | tak |
| `items/sun_legs.png` | Słoneczne nagolenniki (armor) | tak |
| `items/sun_rod.png` | Laska słońca (weapon) | tak |
| `items/sun_shield.png` | Tarcza słońca (shield) | tak |
| `items/sun_spellbook.png` | Księga anchu (shield) | tak |
| `items/sun_sword.png` | Słoneczny sejmitar (weapon) | tak |
| `items/sun_wand.png` | Słoneczna różdżka (weapon) | tak |
| `items/supreme_health_potion.png` | Najwyższa mikstura zdrowia (potion) | tak |
| `items/supreme_mana_potion.png` | Najwyższa mikstura many (potion) | tak |
| `items/swamp_eel.png` | Bagienny węgorz (food) |  |
| `items/swiftness_potion.png` | Mikstura szybkości (potion) | tak |
| `items/thornroot_rod.png` | Laska cierniowego korzenia (weapon) | tak |
| `items/thunderstorm_rune.png` | Runa burzy (rune) | tak |
| `items/tidal_bow.png` | Łuk przypływu (weapon) | tak |
| `items/tidal_harpoon.png` | Harpun przypływu (weapon) | tak |
| `items/torch.png` | Pochodnia (light) | tak |
| `items/training_sword.png` | Miecz ćwiczebny (weapon) | tak |
| `items/traveler_boots.png` | Buty wędrowca (armor) | tak |
| `items/troll_hide.png` | Skóra trolla (material) | tak |
| `items/ultimate_healing_rune.png` | Runa ostatecznego leczenia (rune) | tak |
| `items/ultimate_health_potion.png` | Ostateczna mikstura zdrowia (potion) | tak |
| `items/ultimate_mana_potion.png` | Ostateczna mikstura many (potion) | tak |
| `items/ultimate_spirit_potion.png` | Ostateczna mikstura ducha (potion) | tak |
| `items/umbral_blade.png` | Ostrze mroku (weapon) | tak |
| `items/umbral_grips.png` | Rękawice mroku (weapon) | tak |
| `items/wand_of_embers.png` | Różdżka żaru (weapon) | tak |
| `items/wand_of_inferno.png` | Różdżka piekła (weapon) | tak |
| `items/wand_of_sparks.png` | Różdżka iskier (weapon) | tak |
| `items/wand_of_storms.png` | Różdżka burz (weapon) | tak |
| `items/wand_of_the_deep.png` | Różdżka głębin (weapon) | tak |
| `items/war_hammer.png` | Młot bojowy (weapon) | tak |
| `items/warlord_helmet.png` | Hełm Pana Twierdzy (armor) | tak |
| `items/wild_growth_rune.png` | Runa dzikiego wzrostu (rune) | tak |
| `items/wolf_pelt.png` | Wilcze futro (material) | tak |
| `items/wolf_spellbook.png` | Księga wilka (shield) | tak |
| `items/wooden_shield.png` | Drewniana tarcza (shield) | tak |
| `items/worm.png` | Robak (bait) | tak |
| `items/worm_slime.png` | Śluz czerwia (material) | tak |
| `items/wyvern_scale.png` | Łuska wiwerny (material) | tak |
| `items/yeti_fur.png` | Sierść yeti (material) | tak |

## Kafelki mapy (`assets/art/tiles/`)

Warstwa: ground = podłoże, decoration = trawa i kwiaty, object = drzewa i meble, wall = ściany.

| Plik | Co to jest | Jest |
|---|---|---|
| `tiles/grass.png` | ground, temperate | tak |
| `tiles/cobblestone.png` | ground, town | tak |
| `tiles/dirt.png` | ground, temperate | tak |
| `tiles/water.png` | ground, temperate | tak |
| `tiles/wood_floor.png` | ground, town | tak |
| `tiles/stone_floor.png` | ground, town | tak |
| `tiles/field.png` | ground, temperate | tak |
| `tiles/cave_floor.png` | ground, underground | tak |
| `tiles/bridge.png` | ground, temperate | tak |
| `tiles/sand.png` | ground, desert | tak |
| `tiles/snow.png` | ground, north | tak |
| `tiles/jungle_grass.png` | ground, equatorial | tak |
| `tiles/ice.png` | ground, north | tak |
| `tiles/swamp.png` | ground, equatorial | tak |
| `tiles/sandstone_floor.png` | ground, desert | tak |
| `tiles/frozen_floor.png` | ground, north | tak |
| `tiles/tomb_floor.png` | ground, desert | tak |
| `tiles/tall_grass.png` | decoration, temperate | tak |
| `tiles/flowers.png` | decoration, temperate | tak |
| `tiles/wheat.png` | decoration, temperate | tak |
| `tiles/rug.png` | decoration, town | tak |
| `tiles/snow_tuft.png` | decoration, north | tak |
| `tiles/fern.png` | decoration, equatorial | tak |
| `tiles/dry_grass.png` | decoration, desert | tak |
| `tiles/tree.png` | object, temperate | tak |
| `tiles/rock.png` | object, temperate | tak |
| `tiles/fountain.png` | object, town | tak |
| `tiles/street_lamp.png` | object, town | tak |
| `tiles/wall_torch.png` | object, town | tak |
| `tiles/counter.png` | object, town | tak |
| `tiles/fence.png` | object, temperate | tak |
| `tiles/crystal.png` | object, underground | tak |
| `tiles/rubble.png` | object, underground | tak |
| `tiles/stairs_up.png` | object, any | tak |
| `tiles/stairs_down.png` | object, any | tak |
| `tiles/ladder.png` | object, any | tak |
| `tiles/hole.png` | object, any | tak |
| `tiles/campfire.png` | object, temperate | tak |
| `tiles/door_open.png` | object, town | tak |
| `tiles/altar.png` | object, town | tak |
| `tiles/bush.png` | object, temperate | tak |
| `tiles/crate.png` | object, town | tak |
| `tiles/pine.png` | object, north | tak |
| `tiles/palm.png` | object, equatorial | tak |
| `tiles/cactus.png` | object, desert | tak |
| `tiles/snow_rock.png` | object, north | tak |
| `tiles/sandstone_rock.png` | object, desert | tak |
| `tiles/ice_crystal.png` | object, north | tak |
| `tiles/rope_spot.png` | object, any | tak |
| `tiles/glow_moss.png` | object, equatorial | tak |
| `tiles/magma.png` | object, underground | tak |
| `tiles/stone_wall.png` | wall, town | tak |
| `tiles/cave_wall.png` | wall, underground | tak |
| `tiles/wood_wall.png` | wall, town | tak |
| `tiles/ice_wall.png` | wall, north |  |
| `tiles/sandstone_wall.png` | wall, desert | tak |
| `tiles/mossy_wall.png` | wall, equatorial |  |
| `tiles/log_wall.png` | wall, north |  |
| `tiles/blighted_grass.png` | ground, temperate | tak |
| `tiles/ash.png` | ground, equatorial | tak |
| `tiles/dark_sand.png` | ground, desert | tak |
| `tiles/lava.png` | ground, equatorial | tak |
| `tiles/abyss_floor.png` | ground, underground | tak |
| `tiles/bones.png` | decoration, desert | tak |
| `tiles/dead_tree.png` | object, temperate | tak |
| `tiles/obsidian_rock.png` | object, equatorial | tak |
| `tiles/obsidian_wall.png` | wall, underground |  |
| `tiles/shells.png` | decoration, desert | tak |
| `tiles/mountain.png` | wall, temperate | tak |
| `tiles/snow_mountain.png` | wall, north | tak |
| `tiles/sand_cliff.png` | wall, desert | tak |
| `tiles/bed.png` | object, town | tak |
| `tiles/bookcase.png` | object, town | tak |
| `tiles/barrel.png` | object, town | tak |
| `tiles/table.png` | object, town | tak |
| `tiles/statue.png` | object, town | tak |
| `tiles/sand_statue.png` | object, desert | tak |
| `tiles/jungle_statue.png` | object, equatorial | tak |
| `tiles/cannon.png` | object, town | tak |
| `tiles/chest.png` | object, town | tak |
| `tiles/lumber.png` | object, town | tak |
| `tiles/pyramid.png` | wall, desert | tak |
| `tiles/depot_locker.png` | object, town | tak |
| `tiles/paving.png` | ground, town | tak |
| `tiles/brick_paving.png` | ground, town | tak |
| `tiles/marble_floor.png` | ground, town | tak |
| `tiles/plaster_wall.png` | wall, town |  |
| `tiles/whitewash_wall.png` | wall, town |  |
| `tiles/timber_wall.png` | wall, town |  |
| `tiles/chair.png` | object, town | tak |
| `tiles/wardrobe.png` | object, town | tak |
| `tiles/potted_plant.png` | object, town | tak |
| `tiles/well.png` | object, town | tak |
| `tiles/firewood.png` | object, town | tak |
| `tiles/banner.png` | object, town | tak |
| `tiles/lectern.png` | object, town | tak |
| `tiles/showcase.png` | object, town | tak |
| `tiles/bucket.png` | object, town | tak |
| `tiles/bench.png` | object, town | tak |
| `tiles/anvil.png` | object, town | tak |
| `tiles/forge.png` | object, town | tak |
| `tiles/market_stall.png` | object, town | tak |
| `tiles/signpost.png` | object, town | tak |
| `tiles/hay_bale.png` | object, town | tak |
| `tiles/rowboat.png` | object, town | tak |
| `tiles/fishing_net.png` | object, town | tak |
| `tiles/flower_box.png` | object, town | tak |
| `tiles/ship_deck.png` | ground, town | tak |
| `tiles/ship_hull.png` | wall, town |  |
| `tiles/ship_mast.png` | object, town | tak |
| `tiles/ship_wheel.png` | object, town | tak |
| `tiles/rope_bridge.png` | ground, town | tak |
| `tiles/chasm.png` | ground, mountain | tak |
| `tiles/candle.png` | object, town | tak |
| `tiles/carpet.png` | decoration, town | tak |
| `tiles/windmill.png` | object, town | tak |
| `tiles/broken_wall.png` | object, town | tak |
| `tiles/orc_tent.png` | object, town | tak |
| `tiles/throne.png` | object, town | tak |
| `tiles/cobweb.png` | decoration, mountain | tak |
| `tiles/snowman.png` | object, town | tak |
| `tiles/clay_pot.png` | object, town | tak |
| `tiles/sack.png` | object, town | tak |
| `tiles/sewer_grate.png` | object, town | tak |
| `tiles/jungle_tree.png` | object, equatorial | tak |
| `tiles/jungle_bush.png` | object, equatorial | tak |
| `tiles/jungle_plant.png` | decoration, equatorial | tak |
| `tiles/mushrooms.png` | decoration, temperate | tak |
| `tiles/reeds.png` | decoration, temperate | tak |
| `tiles/frost_pine.png` | object, north | tak |
| `tiles/frost_bush.png` | object, north | tak |
| `tiles/dry_bush.png` | object, desert | tak |
| `tiles/dry_shrub.png` | decoration, desert | tak |
| `tiles/autumn_tree.png` | object, temperate | tak |
| `tiles/burnt_tree.png` | object, temperate | tak |
| `tiles/burnt_beam.png` | object, town | tak |
| `tiles/debris.png` | decoration, town | tak |
| `tiles/corpse.png` | decoration, town | tak |
| `tiles/blood.png` | decoration, town | tak |
| `tiles/wall_lantern.png` | object, town |  |
| `tiles/door_level.png` | object, town |  |
| `tiles/lever.png` | object, town |  |
| `tiles/boss_plate.png` | decoration, town |  |
| `tiles/boss_portal.png` | object, town |  |

## Obiekty (`assets/art/objects/`)

| Plik | Co to jest | Jest |
|---|---|---|
| `objects/chest_closed.png` | skrzynia skarbów, zamknięta | tak |
| `objects/chest_open.png` | skrzynia skarbów, otwarta |  |
| `objects/training_dummy.png` | manekin treningowy | tak |
| `objects/spike_trap.png` | pułapka z kolcami |  |
| `objects/roof_red.png` | dach z dachówki (drewniane domy) | tak |
| `objects/roof_slate.png` | dach z łupku (kamienne budynki) | tak |
| `objects/roof_sand.png` | dach z piaskowej dachówki (budynki z piaskowca) | tak |
| `objects/horse_right.png` | koń, w prawo |  |
| `objects/horse_left.png` | koń, w lewo |  |

## Pupile (`assets/art/pets/`)

| Plik | Co to jest | Jest |
|---|---|---|
| `pets/pet_dog.png` | szczenię | tak |
| `pets/pet_cat.png` | kot | tak |
| `pets/pet_raven.png` | kruk | tak |
| `pets/pet_fennec.png` | fenek | tak |
