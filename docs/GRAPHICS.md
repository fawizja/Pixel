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

Razem: 67 kafelków, 119 przedmiotów, 42 potworów, 26 NPC, 16 obrazków postaci, 8 obiektów, 4 pupili. Podmienione: 184.

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
| `monsters/abyss_lord.png` | Pan Otchłani, poziom 950 |  |
| `monsters/abyssal_demon.png` | Demon Otchłani, poziom 800 | tak |
| `monsters/bandit.png` | Bandyta, poziom 15 | tak |
| `monsters/boar.png` | Dzik, poziom 10 | tak |
| `monsters/bone_dragon.png` | Kościany smok, poziom 880 |  |
| `monsters/cave_troll.png` | Troll jaskiniowy, poziom 32 | tak |
| `monsters/crocodile.png` | Krokodyl, poziom 55 | tak |
| `monsters/crystal_golem.png` | Kryształowy golem, poziom 45 |  |
| `monsters/cursed_treant.png` | Przeklęty drzewiec, poziom 240 |  |
| `monsters/fire_elemental.png` | Żywiołak ognia, poziom 320 | tak |
| `monsters/forest_spider.png` | Pająk leśny, poziom 12 | tak |
| `monsters/frost_giant.png` | Mroźny olbrzym, poziom 430 |  |
| `monsters/frost_queen.png` | Ysolde, Królowa Lodu, poziom 150, boss |  |
| `monsters/frost_witch.png` | Mroźna wiedźma, poziom 85 | tak |
| `monsters/goblin.png` | Goblin, poziom 12 | tak |
| `monsters/goblin_shaman.png` | Goblin szaman, poziom 18 | tak |
| `monsters/grimtooth.png` | Grimtooth, Król Kopalni, poziom 60, boss |  |
| `monsters/hrimgar.png` | Hrimgar, Król Lodowców, poziom 560, boss |  |
| `monsters/hyena.png` | Hiena, poziom 45 |  |
| `monsters/ice_dragon.png` | Lodowy smok, poziom 520 |  |
| `monsters/ice_golem.png` | Lodowy golem, poziom 120 |  |
| `monsters/ignaroth.png` | Ignaroth Roztopiony, poziom 400, boss |  |
| `monsters/jungle_hunter.png` | Łowca z dżungli, poziom 65 | tak |
| `monsters/jungle_serpent.png` | Wąż z dżungli, poziom 75 | tak |
| `monsters/khazuul.png` | Kha'zuul Pusty, poziom 750, boss |  |
| `monsters/lava_golem.png` | Lawowy golem, poziom 370 |  |
| `monsters/morgath.png` | Mor'gath, Serce Otchłani, poziom 1000, boss |  |
| `monsters/mummy.png` | Mumia, poziom 200 | tak |
| `monsters/pharaoh.png` | Faraon Neferakh, poziom 230, boss | tak |
| `monsters/ruin_colossus.png` | Kolos z ruin, poziom 200, boss |  |
| `monsters/sand_titan.png` | Piaskowy tytan, poziom 650 |  |
| `monsters/sand_wraith.png` | Piaskowy upiór, poziom 130 | tak |
| `monsters/scorpion.png` | Skorpion, poziom 60 | tak |
| `monsters/shadow_stalker.png` | Łowca cieni, poziom 590 | tak |
| `monsters/skeleton.png` | Szkielet, poziom 22 | tak |
| `monsters/snow_wolf.png` | Śnieżny wilk, poziom 40 |  |
| `monsters/storm_harpy.png` | Harpia burzy, poziom 470 | tak |
| `monsters/temple_guardian.png` | Strażnik świątyni, poziom 180 | tak |
| `monsters/void_priest.png` | Kapłan pustki, poziom 700 | tak |
| `monsters/wolf.png` | Wilk, poziom 8 |  |
| `monsters/wraith_knight.png` | Upiorny rycerz, poziom 280 | tak |
| `monsters/yeti.png` | Yeti, poziom 70 |  |

## NPC (`assets/art/npcs/`)

| Plik | Co to jest | Jest |
|---|---|---|
| `npcs/camp_banker.png` | Skarbniczka Wena | tak |
| `npcs/camp_guide.png` | Zwiadowczyni Ilka | tak |
| `npcs/camp_merchant.png` | Kwatermistrz Bren | tak |
| `npcs/frost_banker.png` | Bankier Frostholm | tak |
| `npcs/frost_carrier.png` | Woźnica Frostholm | tak |
| `npcs/frost_guide.png` | Przewodnik Frostholm | tak |
| `npcs/frost_merchant.png` | Kupiec Frostholm | tak |
| `npcs/frost_priest.png` | Kapłan Frostholm | tak |
| `npcs/qas_banker.png` | Bankier Qasirah | tak |
| `npcs/qas_carrier.png` | Woźnica Qasirah | tak |
| `npcs/qas_guide.png` | Przewodnik Qasirah | tak |
| `npcs/qas_merchant.png` | Kupiec Qasirah | tak |
| `npcs/qas_priest.png` | Kapłan Qasirah | tak |
| `npcs/selv_banker.png` | Bankier Selvaran | tak |
| `npcs/selv_carrier.png` | Woźnica Selvaran | tak |
| `npcs/selv_guide.png` | Przewodnik Selvaran | tak |
| `npcs/selv_mage.png` | Zielarka Selvaran | tak |
| `npcs/selv_merchant.png` | Kupiec Selvaran | tak |
| `npcs/selv_priest.png` | Kapłan Selvaran | tak |
| `npcs/selv_smith.png` | Płatnerz Selvaran | tak |
| `npcs/varden_banker.png` | Bankier Aldric | tak |
| `npcs/varden_carrier.png` | Woźnica Jarek | tak |
| `npcs/varden_guide.png` | Strażnik Oren | tak |
| `npcs/varden_mage.png` | Czarodziejka Mirela | tak |
| `npcs/varden_merchant.png` | Kupiec Tomas | tak |
| `npcs/varden_priest.png` | Kapłan Benedykt | tak |

## Przedmioty (`assets/art/items/`)

| Plik | Co to jest | Jest |
|---|---|---|
| `items/abyssal_ember.png` | Żar Otchłani (material) |  |
| `items/blank_rune.png` | Pusta runa (rune) |  |
| `items/boar_tusk.png` | Kieł dzika (material) |  |
| `items/bone.png` | Kość (material) | tak |
| `items/bone_shield.png` | Kościana tarcza (shield) | tak |
| `items/boulder_rune.png` | Runa głazu (rune) |  |
| `items/bread.png` | Chleb (food) | tak |
| `items/challenge_scroll.png` | Zwój wyzwania (tool) |  |
| `items/cloth_wraps.png` | Płócienne owijki (weapon) |  |
| `items/colossus_core.png` | Rdzeń kolosa (material) |  |
| `items/croc_leather.png` | Skóra krokodyla (material) |  |
| `items/croc_legs.png` | Krokodyle spodnie (armor) | tak |
| `items/crown_of_the_abyss.png` | Korona Otchłani (armor) | tak |
| `items/crystal_coin.png` | Kryształowa moneta (currency) | tak |
| `items/crystal_shard.png` | Odłamek kryształu (material) | tak |
| `items/dawnspear.png` | Włócznia świtu (weapon) | tak |
| `items/desert_knuckles.png` | Pustynne kastety (weapon) | tak |
| `items/exp_scroll.png` | Zwój nauki (tool) |  |
| `items/fireburst_rune.png` | Runa wybuchu ognia (rune) |  |
| `items/fish.png` | Ryba (food) |  |
| `items/fishing_rod.png` | Wędka (tool) |  |
| `items/fists_of_the_abyss.png` | Pięści Otchłani (weapon) |  |
| `items/frost_blade.png` | Mroźne ostrze (weapon) | tak |
| `items/frost_pelt.png` | Mroźne futro (material) |  |
| `items/frost_tiara.png` | Mroźny diadem (armor) | tak |
| `items/frostbound_fists.png` | Skute lodem pięści (weapon) |  |
| `items/frostfang_spear.png` | Włócznia mroźnego kła (weapon) | tak |
| `items/frozen_heart.png` | Zamarznięte serce (material) |  |
| `items/fur_armor.png` | Futrzana zbroja (armor) | tak |
| `items/glacial_core.png` | Lodowy rdzeń (material) |  |
| `items/glacial_helm.png` | Lodowy hełm (armor) | tak |
| `items/glacial_legs.png` | Lodowe nagolenniki (armor) | tak |
| `items/glacial_plate.png` | Lodowa płytówka (armor) | tak |
| `items/glacial_shield.png` | Lodowa tarcza (shield) | tak |
| `items/goblin_ear.png` | Ucho goblina (material) |  |
| `items/gold_coin.png` | Złota moneta (currency) | tak |
| `items/great_health_potion.png` | Silna mikstura zdrowia (potion) | tak |
| `items/great_mana_potion.png` | Silna mikstura many (potion) | tak |
| `items/grimtooth_crown.png` | Korona Grimtootha (armor) | tak |
| `items/healing_herb.png` | Ziele lecznicze (material) |  |
| `items/healing_rune.png` | Runa leczenia (rune) |  |
| `items/health_potion.png` | Mikstura zdrowia (potion) | tak |
| `items/hunting_spear.png` | Włócznia myśliwska (weapon) | tak |
| `items/hyena_fang.png` | Kieł hieny (material) |  |
| `items/ice_cod.png` | Lodowy dorsz (food) |  |
| `items/iron_knuckles.png` | Żelazne kastety (weapon) | tak |
| `items/iron_ore.png` | Ruda żelaza (material) | tak |
| `items/iron_sword.png` | Żelazny miecz (weapon) | tak |
| `items/jungle_idol.png` | Posążek z dżungli (material) |  |
| `items/leather_armor.png` | Skórzana zbroja (armor) | tak |
| `items/leather_boots.png` | Skórzane buty (armor) | tak |
| `items/leather_helmet.png` | Skórzany hełm (armor) | tak |
| `items/leather_legs.png` | Skórzane spodnie (armor) | tak |
| `items/magma_spear.png` | Magmowa włócznia (weapon) | tak |
| `items/mana_potion.png` | Mikstura many (potion) | tak |
| `items/meat.png` | Mięso (food) | tak |
| `items/miner_helmet.png` | Hełm górnika (armor) | tak |
| `items/molten_knuckles.png` | Stopione kastety (weapon) |  |
| `items/mummy_wrap.png` | Bandaż mumii (material) |  |
| `items/oasis_carp.png` | Karp z oazy (food) |  |
| `items/obsidian_armor.png` | Obsydianowa zbroja (armor) | tak |
| `items/obsidian_helmet.png` | Obsydianowy hełm (armor) | tak |
| `items/obsidian_legs.png` | Obsydianowe nagolenniki (armor) | tak |
| `items/obsidian_shard.png` | Odłamek obsydianu (material) | tak |
| `items/obsidian_shield.png` | Obsydianowa tarcza (shield) | tak |
| `items/obsidian_wand.png` | Obsydianowa różdżka (weapon) | tak |
| `items/pearl.png` | Perła (material) | tak |
| `items/pet_cat.png` | Kocię (tool) |  |
| `items/pet_dog.png` | Szczenię (tool) |  |
| `items/pet_fennec.png` | Fenek (tool) |  |
| `items/pet_raven.png` | Kruk (tool) |  |
| `items/pharaoh_scepter.png` | Berło faraona (weapon) | tak |
| `items/platinum_coin.png` | Platynowa moneta (currency) | tak |
| `items/return_scroll.png` | Zwój powrotu (tool) |  |
| `items/rod_of_eternal_winter.png` | Laska wiecznej zimy (weapon) | tak |
| `items/rod_of_frost.png` | Laska mrozu (weapon) | tak |
| `items/rod_of_hoarfrost.png` | Laska szronu (weapon) | tak |
| `items/rod_of_stoneroot.png` | Laska kamiennego korzenia (weapon) | tak |
| `items/rod_of_twilight.png` | Laska zmierzchu (weapon) | tak |
| `items/rod_of_vines.png` | Laska pnączy (weapon) | tak |
| `items/rope.png` | Lina (tool) | tak |
| `items/saddle.png` | Siodło (tool) |  |
| `items/sandstone_shield.png` | Tarcza z piaskowca (shield) | tak |
| `items/scarab_gem.png` | Klejnot skarabeusza (material) | tak |
| `items/scarab_helmet.png` | Hełm skarabeusza (armor) | tak |
| `items/scorpion_tail.png` | Ogon skorpiona (material) |  |
| `items/serpent_spear.png` | Wężowa włócznia (weapon) | tak |
| `items/shadow_aegis.png` | Egida cienia (shield) | tak |
| `items/shadow_essence.png` | Esencja cienia (material) |  |
| `items/shadow_hood.png` | Kaptur cienia (armor) | tak |
| `items/shadow_legs.png` | Spodnie cienia (armor) | tak |
| `items/shadowpiercer.png` | Przebijacz cienia (weapon) | tak |
| `items/shadowweave_armor.png` | Zbroja z cienia (armor) | tak |
| `items/shovel.png` | Łopata (tool) | tak |
| `items/snake_skin.png` | Wężowa skóra (material) |  |
| `items/spear.png` | Włócznia (ammunition) | tak |
| `items/spider_silk.png` | Pajęcza nić (material) | tak |
| `items/starfall_wand.png` | Różdżka spadających gwiazd (weapon) | tak |
| `items/steel_spear.png` | Stalowa włócznia (weapon) | tak |
| `items/stormcaller_wand.png` | Różdżka wołająca burzę (weapon) | tak |
| `items/strong_health_potion.png` | Potężna mikstura zdrowia (potion) |  |
| `items/strong_mana_potion.png` | Potężna mikstura many (potion) |  |
| `items/studded_armor.png` | Ćwiekowana zbroja (armor) | tak |
| `items/supreme_health_potion.png` | Najwyższa mikstura zdrowia (potion) |  |
| `items/supreme_mana_potion.png` | Najwyższa mikstura many (potion) |  |
| `items/swamp_eel.png` | Bagienny węgorz (food) |  |
| `items/thornroot_rod.png` | Laska cierniowego korzenia (weapon) | tak |
| `items/torch.png` | Pochodnia (light) | tak |
| `items/training_sword.png` | Miecz ćwiczebny (weapon) | tak |
| `items/troll_hide.png` | Skóra trolla (material) |  |
| `items/umbral_grips.png` | Rękawice mroku (weapon) |  |
| `items/wand_of_embers.png` | Różdżka żaru (weapon) | tak |
| `items/wand_of_inferno.png` | Różdżka piekła (weapon) | tak |
| `items/wand_of_sparks.png` | Różdżka iskier (weapon) | tak |
| `items/wand_of_storms.png` | Różdżka burz (weapon) | tak |
| `items/wolf_pelt.png` | Wilcze futro (material) | tak |
| `items/wooden_shield.png` | Drewniana tarcza (shield) | tak |
| `items/worm.png` | Robak (bait) |  |
| `items/yeti_fur.png` | Sierść yeti (material) |  |

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
| `tiles/tall_grass.png` | decoration, temperate |  |
| `tiles/flowers.png` | decoration, temperate | tak |
| `tiles/wheat.png` | decoration, temperate |  |
| `tiles/rug.png` | decoration, town |  |
| `tiles/snow_tuft.png` | decoration, north |  |
| `tiles/fern.png` | decoration, equatorial | tak |
| `tiles/dry_grass.png` | decoration, desert |  |
| `tiles/tree.png` | object, temperate | tak |
| `tiles/rock.png` | object, temperate | tak |
| `tiles/fountain.png` | object, town |  |
| `tiles/street_lamp.png` | object, town |  |
| `tiles/wall_torch.png` | object, town |  |
| `tiles/counter.png` | object, town |  |
| `tiles/fence.png` | object, temperate |  |
| `tiles/crystal.png` | object, underground | tak |
| `tiles/rubble.png` | object, underground | tak |
| `tiles/stairs_up.png` | object, any |  |
| `tiles/stairs_down.png` | object, any |  |
| `tiles/ladder.png` | object, any |  |
| `tiles/hole.png` | object, any | tak |
| `tiles/campfire.png` | object, temperate | tak |
| `tiles/door_open.png` | object, town |  |
| `tiles/altar.png` | object, town |  |
| `tiles/bush.png` | object, temperate | tak |
| `tiles/crate.png` | object, town | tak |
| `tiles/pine.png` | object, north | tak |
| `tiles/palm.png` | object, equatorial |  |
| `tiles/cactus.png` | object, desert |  |
| `tiles/snow_rock.png` | object, north | tak |
| `tiles/sandstone_rock.png` | object, desert | tak |
| `tiles/ice_crystal.png` | object, north | tak |
| `tiles/rope_spot.png` | object, any | tak |
| `tiles/glow_moss.png` | object, equatorial |  |
| `tiles/magma.png` | object, underground |  |
| `tiles/stone_wall.png` | wall, town | tak |
| `tiles/cave_wall.png` | wall, underground |  |
| `tiles/wood_wall.png` | wall, town | tak |
| `tiles/ice_wall.png` | wall, north |  |
| `tiles/sandstone_wall.png` | wall, desert | tak |
| `tiles/mossy_wall.png` | wall, equatorial |  |
| `tiles/log_wall.png` | wall, north |  |
| `tiles/blighted_grass.png` | ground, temperate | tak |
| `tiles/ash.png` | ground, equatorial | tak |
| `tiles/dark_sand.png` | ground, desert | tak |
| `tiles/lava.png` | ground, equatorial |  |
| `tiles/abyss_floor.png` | ground, underground | tak |
| `tiles/bones.png` | decoration, desert |  |
| `tiles/dead_tree.png` | object, temperate | tak |
| `tiles/obsidian_rock.png` | object, equatorial | tak |
| `tiles/obsidian_wall.png` | wall, underground |  |

## Obiekty (`assets/art/objects/`)

| Plik | Co to jest | Jest |
|---|---|---|
| `objects/chest_closed.png` | skrzynia skarbów, zamknięta |  |
| `objects/chest_open.png` | skrzynia skarbów, otwarta |  |
| `objects/training_dummy.png` | manekin treningowy |  |
| `objects/spike_trap.png` | pułapka z kolcami |  |
| `objects/roof_red.png` | dach z dachówki (drewniane domy) | tak |
| `objects/roof_slate.png` | dach z łupku (kamienne budynki) | tak |
| `objects/horse_right.png` | koń, w prawo |  |
| `objects/horse_left.png` | koń, w lewo |  |

## Pupile (`assets/art/pets/`)

| Plik | Co to jest | Jest |
|---|---|---|
| `pets/pet_dog.png` | szczenię |  |
| `pets/pet_cat.png` | kot | tak |
| `pets/pet_raven.png` | kruk | tak |
| `pets/pet_fennec.png` | fenek |  |
