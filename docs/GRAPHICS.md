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

Razem: 119 kafelków, 143 przedmiotów, 58 potworów, 63 NPC, 16 obrazków postaci, 9 obiektów, 4 pupili. Podmienione: 396.

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
| `monsters/abyss_lord.png` | Pan Otchłani, poziom 950 | tak |
| `monsters/abyssal_demon.png` | Demon Otchłani, poziom 800 | tak |
| `monsters/bandit.png` | Bandyta, poziom 15 | tak |
| `monsters/bloodbeard.png` | Kapitan Krwawa Broda, poziom 420, boss | tak |
| `monsters/boar.png` | Dzik, poziom 10 | tak |
| `monsters/bone_dragon.png` | Kościany smok, poziom 880 | tak |
| `monsters/cave_troll.png` | Troll jaskiniowy, poziom 32 | tak |
| `monsters/coral_crab.png` | Krab koralowy, poziom 310 | tak |
| `monsters/crocodile.png` | Krokodyl, poziom 55 | tak |
| `monsters/crystal_golem.png` | Kryształowy golem, poziom 45 | tak |
| `monsters/cursed_treant.png` | Przeklęty drzewiec, poziom 240 | tak |
| `monsters/drowned_sailor.png` | Topielec, poziom 330 | tak |
| `monsters/fire_elemental.png` | Żywiołak ognia, poziom 320 | tak |
| `monsters/forest_spider.png` | Pająk leśny, poziom 12 | tak |
| `monsters/frost_giant.png` | Mroźny olbrzym, poziom 430 | tak |
| `monsters/frost_queen.png` | Ysolde, Królowa Lodu, poziom 150, boss | tak |
| `monsters/frost_witch.png` | Mroźna wiedźma, poziom 85 | tak |
| `monsters/goblin.png` | Goblin, poziom 12 | tak |
| `monsters/goblin_shaman.png` | Goblin szaman, poziom 18 | tak |
| `monsters/grimtooth.png` | Grimtooth, Król Kopalni, poziom 60, boss | tak |
| `monsters/hrimgar.png` | Hrimgar, Król Lodowców, poziom 560, boss | tak |
| `monsters/hyena.png` | Hiena, poziom 45 | tak |
| `monsters/ice_dragon.png` | Lodowy smok, poziom 520 | tak |
| `monsters/ice_golem.png` | Lodowy golem, poziom 120 | tak |
| `monsters/ignaroth.png` | Ignaroth Roztopiony, poziom 400, boss | tak |
| `monsters/jungle_hunter.png` | Łowca z dżungli, poziom 65 | tak |
| `monsters/jungle_serpent.png` | Wąż z dżungli, poziom 75 | tak |
| `monsters/khazuul.png` | Kha'zuul Pusty, poziom 750, boss | tak |
| `monsters/lava_golem.png` | Lawowy golem, poziom 370 | tak |
| `monsters/magma_drake.png` | Smok magmy, poziom 690 | tak |
| `monsters/mist_wraith.png` | Upiór mgły, poziom 540 | tak |
| `monsters/morgath.png` | Mor'gath, Serce Otchłani, poziom 1000, boss | tak |
| `monsters/morvena.png` | Morvena, Pani Mgieł, poziom 640, boss | tak |
| `monsters/mummy.png` | Mumia, poziom 200 | tak |
| `monsters/orc_berserker.png` | Ork berserker, poziom 150 | tak |
| `monsters/orc_shaman.png` | Orczy szaman, poziom 125 | tak |
| `monsters/orc_warlord.png` | Gruzhak, Orczy Wódz, poziom 220 | tak |
| `monsters/orc_warrior.png` | Ork wojownik, poziom 110 | tak |
| `monsters/pharaoh.png` | Faraon Neferakh, poziom 230, boss | tak |
| `monsters/pirate_cutthroat.png` | Pirat rzezimieszek, poziom 350 | tak |
| `monsters/pirate_gunner.png` | Pirat kanonier, poziom 400 | tak |
| `monsters/pyrrax.png` | Pyrrax, Serce Wulkanu, poziom 780, boss | tak |
| `monsters/ruin_colossus.png` | Kolos z ruin, poziom 200, boss | tak |
| `monsters/sand_titan.png` | Piaskowy tytan, poziom 650 | tak |
| `monsters/sand_wraith.png` | Piaskowy upiór, poziom 130 | tak |
| `monsters/scorpion.png` | Skorpion, poziom 60 | tak |
| `monsters/shadow_stalker.png` | Łowca cieni, poziom 590 | tak |
| `monsters/skaldra.png` | Skaldra, Lodowa Matka, poziom 540, boss | tak |
| `monsters/skeleton.png` | Szkielet, poziom 22 | tak |
| `monsters/snow_wolf.png` | Śnieżny wilk, poziom 40 | tak |
| `monsters/storm_harpy.png` | Harpia burzy, poziom 470 | tak |
| `monsters/temple_guardian.png` | Strażnik świątyni, poziom 180 | tak |
| `monsters/void_priest.png` | Kapłan pustki, poziom 700 | tak |
| `monsters/wolf.png` | Wilk, poziom 8 | tak |
| `monsters/wraith_knight.png` | Upiorny rycerz, poziom 280 | tak |
| `monsters/wyvern.png` | Wiwerna, poziom 300 | tak |
| `monsters/wyvern_matriarch.png` | Matka Wiwern, poziom 360, boss | tak |
| `monsters/yeti.png` | Yeti, poziom 70 | tak |

## NPC (`assets/art/npcs/`)

| Plik | Co to jest | Jest |
|---|---|---|
| `npcs/camp_banker.png` | Skarbniczka Wena | tak |
| `npcs/camp_guide.png` | Zwiadowczyni Ilka | tak |
| `npcs/camp_merchant.png` | Kwatermistrz Bren | tak |
| `npcs/coral_captain.png` | Sternik Lucjan | tak |
| `npcs/east_captain.png` | Kapitan Wiesław | tak |
| `npcs/frost_banker.png` | Bankier Frostholm | tak |
| `npcs/frost_carrier.png` | Woźnica Frostholm | tak |
| `npcs/frost_guide.png` | Przewodnik Frostholm | tak |
| `npcs/frost_housing.png` | Zarządca Bjorn | tak |
| `npcs/frost_innkeeper.png` | Karczmarka Sigrun | tak |
| `npcs/frost_mage.png` | Mag Runolf | tak |
| `npcs/frost_merchant.png` | Kupiec Frostholm | tak |
| `npcs/frost_priest.png` | Kapłan Frostholm | tak |
| `npcs/frost_smith.png` | Kowal Hrodgar | tak |
| `npcs/guard_1.png` | Strażniczka Dobrosława | tak |
| `npcs/guard_2.png` | Kusznik Wszebor | tak |
| `npcs/guard_3.png` | Strażniczka Wiesława | tak |
| `npcs/guard_4.png` | Strażnik Przemysław | tak |
| `npcs/guard_5.png` | Halabardnik Siemowit | tak |
| `npcs/guard_6.png` | Łuczniczka Zdzisława | tak |
| `npcs/guard_7.png` | Strażniczka Jaromira | tak |
| `npcs/gull_ferryman.png` | Przewoźnik Bolko | tak |
| `npcs/ice_captain.png` | Kapitan Torvald | tak |
| `npcs/isle_guide.png` | Admirał Wenda | tak |
| `npcs/isle_merchant.png` | Kwatermistrz Olaf | tak |
| `npcs/lighthouse_ferryman.png` | Latarniczka Wanda | tak |
| `npcs/mist_captain.png` | Przewoźnik Gniewko | tak |
| `npcs/osada_merchant.png` | Handlarka Jagna | tak |
| `npcs/pirate_captain.png` | Kapitan Czarna Mewa | tak |
| `npcs/qas_banker.png` | Bankier Qasirah | tak |
| `npcs/qas_captain.png` | Kapitan Rahim | tak |
| `npcs/qas_carrier.png` | Woźnica Qasirah | tak |
| `npcs/qas_guide.png` | Przewodnik Qasirah | tak |
| `npcs/qas_housing.png` | Zarządca Karim | tak |
| `npcs/qas_innkeeper.png` | Handlarz Omar | tak |
| `npcs/qas_mage.png` | Wróżbitka Leila | tak |
| `npcs/qas_merchant.png` | Kupiec Qasirah | tak |
| `npcs/qas_priest.png` | Kapłan Qasirah | tak |
| `npcs/qas_smith.png` | Płatnerz Hamid | tak |
| `npcs/selv_banker.png` | Bankier Selvaran | tak |
| `npcs/selv_captain.png` | Kapitan Bartosz | tak |
| `npcs/selv_carrier.png` | Woźnica Selvaran | tak |
| `npcs/selv_fishmonger.png` | Rybaczka Mara | tak |
| `npcs/selv_guide.png` | Przewodnik Selvaran | tak |
| `npcs/selv_housing.png` | Zarządca Tomasz | tak |
| `npcs/selv_mage.png` | Zielarka Selvaran | tak |
| `npcs/selv_merchant.png` | Kupiec Selvaran | tak |
| `npcs/selv_priest.png` | Kapłan Selvaran | tak |
| `npcs/selv_smith.png` | Płatnerz Selvaran | tak |
| `npcs/varden_archmage.png` | Arcymag Eldrin | tak |
| `npcs/varden_banker.png` | Bankier Aldric | tak |
| `npcs/varden_captain.png` | Kapitan Edmund | tak |
| `npcs/varden_carrier.png` | Woźnica Jarek | tak |
| `npcs/varden_guide.png` | Strażnik Oren | tak |
| `npcs/varden_housing.png` | Zarządca Leopold | tak |
| `npcs/varden_innkeeper.png` | Karczmarz Wit | tak |
| `npcs/varden_king.png` | Król Aurelian | tak |
| `npcs/varden_mage.png` | Czarodziejka Mirela | tak |
| `npcs/varden_merchant.png` | Kupiec Tomas | tak |
| `npcs/varden_priest.png` | Kapłan Benedykt | tak |
| `npcs/varden_smith.png` | Kowal Bogdan | tak |
| `npcs/volcano_captain.png` | Kapitan Ognik | tak |
| `npcs/wierzbowo_merchant.png` | Sołtys Bogumił | tak |

## Przedmioty (`assets/art/items/`)

| Plik | Co to jest | Jest |
|---|---|---|
| `items/abyssal_ember.png` | Żar Otchłani (material) | tak |
| `items/avalanche_rune.png` | Runa lawiny (rune) | tak |
| `items/backpack.png` | Plecak (tool) | tak |
| `items/blank_rune.png` | Pusta runa (rune) | tak |
| `items/boar_tusk.png` | Kieł dzika (material) | tak |
| `items/bone.png` | Kość (material) | tak |
| `items/bone_shield.png` | Kościana tarcza (shield) | tak |
| `items/boulder_rune.png` | Runa głazu (rune) | tak |
| `items/bread.png` | Chleb (food) | tak |
| `items/challenge_scroll.png` | Zwój wyzwania (tool) | tak |
| `items/cloth_wraps.png` | Płócienne owijki (weapon) | tak |
| `items/colossus_core.png` | Rdzeń kolosa (material) | tak |
| `items/coral_armor.png` | Koralowa zbroja (armor) | tak |
| `items/coral_helmet.png` | Hełm głębin (armor) | tak |
| `items/coral_legs.png` | Koralowe nagolenniki (armor) | tak |
| `items/coral_shield.png` | Tarcza z muszli (shield) | tak |
| `items/crab_pincer.png` | Szczypce kraba koralowego (material) | tak |
| `items/croc_leather.png` | Skóra krokodyla (material) | tak |
| `items/croc_legs.png` | Krokodyle spodnie (armor) | tak |
| `items/crown_of_the_abyss.png` | Korona Otchłani (armor) | tak |
| `items/crystal_coin.png` | Kryształowa moneta (currency) | tak |
| `items/crystal_shard.png` | Odłamek kryształu (material) | tak |
| `items/dawnspear.png` | Włócznia świtu (weapon) | tak |
| `items/desert_knuckles.png` | Pustynne kastety (weapon) | tak |
| `items/drake_horn.png` | Róg magmowego smoka (material) | tak |
| `items/drowned_ring.png` | Pierścień topielca (material) | tak |
| `items/exp_scroll.png` | Zwój nauki (tool) | tak |
| `items/fireburst_rune.png` | Runa wybuchu ognia (rune) | tak |
| `items/fish.png` | Ryba (food) |  |
| `items/fishing_rod.png` | Wędka (tool) | tak |
| `items/fists_of_the_abyss.png` | Pięści Otchłani (weapon) | tak |
| `items/frost_blade.png` | Mroźne ostrze (weapon) | tak |
| `items/frost_pelt.png` | Mroźne futro (material) | tak |
| `items/frost_tiara.png` | Mroźny diadem (armor) | tak |
| `items/frostbound_fists.png` | Skute lodem pięści (weapon) | tak |
| `items/frostfang_spear.png` | Włócznia mroźnego kła (weapon) | tak |
| `items/frozen_heart.png` | Zamarznięte serce (material) | tak |
| `items/fur_armor.png` | Futrzana zbroja (armor) | tak |
| `items/glacial_core.png` | Lodowy rdzeń (material) | tak |
| `items/glacial_helm.png` | Lodowy hełm (armor) | tak |
| `items/glacial_legs.png` | Lodowe nagolenniki (armor) | tak |
| `items/glacial_plate.png` | Lodowa płytówka (armor) | tak |
| `items/glacial_shield.png` | Lodowa tarcza (shield) | tak |
| `items/goblin_ear.png` | Ucho goblina (material) | tak |
| `items/gold_coin.png` | Złota moneta (currency) | tak |
| `items/great_fireball_rune.png` | Runa wielkiej kuli ognia (rune) | tak |
| `items/great_health_potion.png` | Silna mikstura zdrowia (potion) | tak |
| `items/great_mana_potion.png` | Silna mikstura many (potion) | tak |
| `items/great_spirit_potion.png` | Silna mikstura ducha (potion) | tak |
| `items/grimtooth_crown.png` | Korona Grimtootha (armor) | tak |
| `items/healing_herb.png` | Ziele lecznicze (material) | tak |
| `items/healing_rune.png` | Runa leczenia (rune) | tak |
| `items/health_potion.png` | Mikstura zdrowia (potion) | tak |
| `items/hunting_spear.png` | Włócznia myśliwska (weapon) | tak |
| `items/hyena_fang.png` | Kieł hieny (material) | tak |
| `items/ice_cod.png` | Lodowy dorsz (food) |  |
| `items/iron_knuckles.png` | Żelazne kastety (weapon) | tak |
| `items/iron_ore.png` | Ruda żelaza (material) | tak |
| `items/iron_sword.png` | Żelazny miecz (weapon) | tak |
| `items/jungle_idol.png` | Posążek z dżungli (material) | tak |
| `items/leather_armor.png` | Skórzana zbroja (armor) | tak |
| `items/leather_boots.png` | Skórzane buty (armor) | tak |
| `items/leather_helmet.png` | Skórzany hełm (armor) | tak |
| `items/leather_legs.png` | Skórzane spodnie (armor) | tak |
| `items/magma_spear.png` | Magmowa włócznia (weapon) | tak |
| `items/mana_potion.png` | Mikstura many (potion) | tak |
| `items/meat.png` | Mięso (food) | tak |
| `items/miner_helmet.png` | Hełm górnika (armor) | tak |
| `items/mist_essence.png` | Esencja mgły (material) | tak |
| `items/molten_knuckles.png` | Stopione kastety (weapon) | tak |
| `items/mummy_wrap.png` | Bandaż mumii (material) | tak |
| `items/oasis_carp.png` | Karp z oazy (food) |  |
| `items/obsidian_armor.png` | Obsydianowa zbroja (armor) | tak |
| `items/obsidian_helmet.png` | Obsydianowy hełm (armor) | tak |
| `items/obsidian_legs.png` | Obsydianowe nagolenniki (armor) | tak |
| `items/obsidian_shard.png` | Odłamek obsydianu (material) | tak |
| `items/obsidian_shield.png` | Obsydianowa tarcza (shield) | tak |
| `items/obsidian_wand.png` | Obsydianowa różdżka (weapon) | tak |
| `items/pearl.png` | Perła (material) | tak |
| `items/pet_cat.png` | Kocię (tool) | tak |
| `items/pet_dog.png` | Szczenię (tool) | tak |
| `items/pet_fennec.png` | Fenek (tool) | tak |
| `items/pet_raven.png` | Kruk (tool) | tak |
| `items/pharaoh_scepter.png` | Berło faraona (weapon) | tak |
| `items/pirate_hook.png` | Hak pirata (material) | tak |
| `items/platinum_coin.png` | Platynowa moneta (currency) | tak |
| `items/powder_horn.png` | Róg z prochem (material) | tak |
| `items/reef_gauntlets.png` | Rękawice rafy (weapon) | tak |
| `items/return_scroll.png` | Zwój powrotu (tool) | tak |
| `items/rod_of_eternal_winter.png` | Laska wiecznej zimy (weapon) | tak |
| `items/rod_of_frost.png` | Laska mrozu (weapon) | tak |
| `items/rod_of_hoarfrost.png` | Laska szronu (weapon) | tak |
| `items/rod_of_stoneroot.png` | Laska kamiennego korzenia (weapon) | tak |
| `items/rod_of_tides.png` | Laska przypływów (weapon) | tak |
| `items/rod_of_twilight.png` | Laska zmierzchu (weapon) | tak |
| `items/rod_of_vines.png` | Laska pnączy (weapon) | tak |
| `items/rope.png` | Lina (tool) | tak |
| `items/saddle.png` | Siodło (tool) | tak |
| `items/sandstone_shield.png` | Tarcza z piaskowca (shield) | tak |
| `items/scarab_gem.png` | Klejnot skarabeusza (material) | tak |
| `items/scarab_helmet.png` | Hełm skarabeusza (armor) | tak |
| `items/scorpion_tail.png` | Ogon skorpiona (material) | tak |
| `items/serpent_spear.png` | Wężowa włócznia (weapon) | tak |
| `items/shadow_aegis.png` | Egida cienia (shield) | tak |
| `items/shadow_essence.png` | Esencja cienia (material) | tak |
| `items/shadow_hood.png` | Kaptur cienia (armor) | tak |
| `items/shadow_legs.png` | Spodnie cienia (armor) | tak |
| `items/shadowpiercer.png` | Przebijacz cienia (weapon) | tak |
| `items/shadowweave_armor.png` | Zbroja z cienia (armor) | tak |
| `items/shovel.png` | Łopata (tool) | tak |
| `items/snake_skin.png` | Wężowa skóra (material) | tak |
| `items/spear.png` | Włócznia (ammunition) | tak |
| `items/spider_silk.png` | Pajęcza nić (material) | tak |
| `items/starfall_wand.png` | Różdżka spadających gwiazd (weapon) | tak |
| `items/steel_spear.png` | Stalowa włócznia (weapon) | tak |
| `items/stormcaller_wand.png` | Różdżka wołająca burzę (weapon) | tak |
| `items/strong_health_potion.png` | Potężna mikstura zdrowia (potion) | tak |
| `items/strong_mana_potion.png` | Potężna mikstura many (potion) | tak |
| `items/studded_armor.png` | Ćwiekowana zbroja (armor) | tak |
| `items/sudden_death_rune.png` | Runa nagłej śmierci (rune) | tak |
| `items/supreme_health_potion.png` | Najwyższa mikstura zdrowia (potion) | tak |
| `items/supreme_mana_potion.png` | Najwyższa mikstura many (potion) | tak |
| `items/swamp_eel.png` | Bagienny węgorz (food) |  |
| `items/thornroot_rod.png` | Laska cierniowego korzenia (weapon) | tak |
| `items/tidal_harpoon.png` | Harpun przypływu (weapon) | tak |
| `items/torch.png` | Pochodnia (light) | tak |
| `items/training_sword.png` | Miecz ćwiczebny (weapon) | tak |
| `items/troll_hide.png` | Skóra trolla (material) | tak |
| `items/ultimate_healing_rune.png` | Runa ostatecznego leczenia (rune) | tak |
| `items/ultimate_health_potion.png` | Ostateczna mikstura zdrowia (potion) | tak |
| `items/ultimate_mana_potion.png` | Ostateczna mikstura many (potion) | tak |
| `items/ultimate_spirit_potion.png` | Ostateczna mikstura ducha (potion) | tak |
| `items/umbral_grips.png` | Rękawice mroku (weapon) | tak |
| `items/wand_of_embers.png` | Różdżka żaru (weapon) | tak |
| `items/wand_of_inferno.png` | Różdżka piekła (weapon) | tak |
| `items/wand_of_sparks.png` | Różdżka iskier (weapon) | tak |
| `items/wand_of_storms.png` | Różdżka burz (weapon) | tak |
| `items/wand_of_the_deep.png` | Różdżka głębin (weapon) | tak |
| `items/wolf_pelt.png` | Wilcze futro (material) | tak |
| `items/wooden_shield.png` | Drewniana tarcza (shield) | tak |
| `items/worm.png` | Robak (bait) | tak |
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
