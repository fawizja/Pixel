# Grafiki do podmiany

Wszystkie obrazki gry są dziś rysowane przez kod (`TileArt`). Plik PNG o nazwie z tej listy, położony w
`assets/art/`, zastępuje wygenerowany obrazek; brakujące pliki zostają wygenerowane, więc można podmieniać
po kawałku.

- PNG z przezroczystym tłem, kwadrat. Najlepiej 16×16 px; większe (32×32, 64×64) gra zmniejsza bez rozmywania.
- Kafelki podłoża wypełniają cały kwadrat; postacie, potwory i przedmioty stoją na przezroczystym tle,
  stopami przy dolnej krawędzi.
- Kolorowe stroje (/strój) są generowane z kolorów; profesja z podmienioną postacią zawsze wygląda jak na grafice.
- Grafiki muszą być własne albo na wolnej licencji (bez kopii z Tibii i innych gier komercyjnych).

Razem: 67 kafelków, 119 przedmiotów, 42 potworów, 24 NPC, 16 obrazków postaci, 6 obiektów, 4 pupili.

Lista jest generowana: `python3 tools/art/list_graphics.py`.

## Postacie graczy (`assets/art/characters/`)

Każda profesja w 4 kierunkach.

| Plik | Co to jest |
|---|---|
| `characters/paladin_down.png` | Paladyn, przód |
| `characters/paladin_up.png` | Paladyn, tył |
| `characters/paladin_left.png` | Paladyn, w lewo |
| `characters/paladin_right.png` | Paladyn, w prawo |
| `characters/druid_down.png` | Druid, przód |
| `characters/druid_up.png` | Druid, tył |
| `characters/druid_left.png` | Druid, w lewo |
| `characters/druid_right.png` | Druid, w prawo |
| `characters/sorcerer_down.png` | Czarodziej, przód |
| `characters/sorcerer_up.png` | Czarodziej, tył |
| `characters/sorcerer_left.png` | Czarodziej, w lewo |
| `characters/sorcerer_right.png` | Czarodziej, w prawo |
| `characters/monk_down.png` | Mnich, przód |
| `characters/monk_up.png` | Mnich, tył |
| `characters/monk_left.png` | Mnich, w lewo |
| `characters/monk_right.png` | Mnich, w prawo |

## Potwory (`assets/art/monsters/`)

| Plik | Co to jest |
|---|---|
| `monsters/abyss_lord.png` | Pan Otchłani, poziom 950 |
| `monsters/abyssal_demon.png` | Demon Otchłani, poziom 800 |
| `monsters/bandit.png` | Bandyta, poziom 15 |
| `monsters/boar.png` | Dzik, poziom 10 |
| `monsters/bone_dragon.png` | Kościany smok, poziom 880 |
| `monsters/cave_troll.png` | Troll jaskiniowy, poziom 32 |
| `monsters/crocodile.png` | Krokodyl, poziom 55 |
| `monsters/crystal_golem.png` | Kryształowy golem, poziom 45 |
| `monsters/cursed_treant.png` | Przeklęty drzewiec, poziom 240 |
| `monsters/fire_elemental.png` | Żywiołak ognia, poziom 320 |
| `monsters/forest_spider.png` | Pająk leśny, poziom 12 |
| `monsters/frost_giant.png` | Mroźny olbrzym, poziom 430 |
| `monsters/frost_queen.png` | Ysolde, Królowa Lodu, poziom 150, boss |
| `monsters/frost_witch.png` | Mroźna wiedźma, poziom 85 |
| `monsters/goblin.png` | Goblin, poziom 12 |
| `monsters/goblin_shaman.png` | Goblin szaman, poziom 18 |
| `monsters/grimtooth.png` | Grimtooth, Król Kopalni, poziom 60, boss |
| `monsters/hrimgar.png` | Hrimgar, Król Lodowców, poziom 560, boss |
| `monsters/hyena.png` | Hiena, poziom 45 |
| `monsters/ice_dragon.png` | Lodowy smok, poziom 520 |
| `monsters/ice_golem.png` | Lodowy golem, poziom 120 |
| `monsters/ignaroth.png` | Ignaroth Roztopiony, poziom 400, boss |
| `monsters/jungle_hunter.png` | Łowca z dżungli, poziom 65 |
| `monsters/jungle_serpent.png` | Wąż z dżungli, poziom 75 |
| `monsters/khazuul.png` | Kha'zuul Pusty, poziom 750, boss |
| `monsters/lava_golem.png` | Lawowy golem, poziom 370 |
| `monsters/morgath.png` | Mor'gath, Serce Otchłani, poziom 1000, boss |
| `monsters/mummy.png` | Mumia, poziom 200 |
| `monsters/pharaoh.png` | Faraon Neferakh, poziom 230, boss |
| `monsters/ruin_colossus.png` | Kolos z ruin, poziom 200, boss |
| `monsters/sand_titan.png` | Piaskowy tytan, poziom 650 |
| `monsters/sand_wraith.png` | Piaskowy upiór, poziom 130 |
| `monsters/scorpion.png` | Skorpion, poziom 60 |
| `monsters/shadow_stalker.png` | Łowca cieni, poziom 590 |
| `monsters/skeleton.png` | Szkielet, poziom 22 |
| `monsters/snow_wolf.png` | Śnieżny wilk, poziom 40 |
| `monsters/storm_harpy.png` | Harpia burzy, poziom 470 |
| `monsters/temple_guardian.png` | Strażnik świątyni, poziom 180 |
| `monsters/void_priest.png` | Kapłan pustki, poziom 700 |
| `monsters/wolf.png` | Wilk, poziom 8 |
| `monsters/wraith_knight.png` | Upiorny rycerz, poziom 280 |
| `monsters/yeti.png` | Yeti, poziom 70 |

## NPC (`assets/art/npcs/`)

| Plik | Co to jest |
|---|---|
| `npcs/camp_banker.png` | Skarbniczka Wena |
| `npcs/camp_guide.png` | Zwiadowczyni Ilka |
| `npcs/camp_merchant.png` | Kwatermistrz Bren |
| `npcs/frost_banker.png` | Bankier Frostholm |
| `npcs/frost_carrier.png` | Woźnica Frostholm |
| `npcs/frost_guide.png` | Przewodnik Frostholm |
| `npcs/frost_merchant.png` | Kupiec Frostholm |
| `npcs/frost_priest.png` | Kapłan Frostholm |
| `npcs/qas_banker.png` | Bankier Qasirah |
| `npcs/qas_carrier.png` | Woźnica Qasirah |
| `npcs/qas_guide.png` | Przewodnik Qasirah |
| `npcs/qas_merchant.png` | Kupiec Qasirah |
| `npcs/qas_priest.png` | Kapłan Qasirah |
| `npcs/selv_banker.png` | Bankier Selvaran |
| `npcs/selv_carrier.png` | Woźnica Selvaran |
| `npcs/selv_guide.png` | Przewodnik Selvaran |
| `npcs/selv_merchant.png` | Kupiec Selvaran |
| `npcs/selv_priest.png` | Kapłan Selvaran |
| `npcs/varden_banker.png` | Bankier Aldric |
| `npcs/varden_carrier.png` | Woźnica Jarek |
| `npcs/varden_guide.png` | Strażnik Oren |
| `npcs/varden_mage.png` | Czarodziejka Mirela |
| `npcs/varden_merchant.png` | Kupiec Tomas |
| `npcs/varden_priest.png` | Kapłan Benedykt |

## Przedmioty (`assets/art/items/`)

| Plik | Co to jest |
|---|---|
| `items/abyssal_ember.png` | Żar Otchłani (material) |
| `items/blank_rune.png` | Pusta runa (rune) |
| `items/boar_tusk.png` | Kieł dzika (material) |
| `items/bone.png` | Kość (material) |
| `items/bone_shield.png` | Kościana tarcza (shield) |
| `items/boulder_rune.png` | Runa głazu (rune) |
| `items/bread.png` | Chleb (food) |
| `items/challenge_scroll.png` | Zwój wyzwania (tool) |
| `items/cloth_wraps.png` | Płócienne owijki (weapon) |
| `items/colossus_core.png` | Rdzeń kolosa (material) |
| `items/croc_leather.png` | Skóra krokodyla (material) |
| `items/croc_legs.png` | Krokodyle spodnie (armor) |
| `items/crown_of_the_abyss.png` | Korona Otchłani (armor) |
| `items/crystal_coin.png` | Kryształowa moneta (currency) |
| `items/crystal_shard.png` | Odłamek kryształu (material) |
| `items/dawnspear.png` | Włócznia świtu (weapon) |
| `items/desert_knuckles.png` | Pustynne kastety (weapon) |
| `items/exp_scroll.png` | Zwój nauki (tool) |
| `items/fireburst_rune.png` | Runa wybuchu ognia (rune) |
| `items/fish.png` | Ryba (food) |
| `items/fishing_rod.png` | Wędka (tool) |
| `items/fists_of_the_abyss.png` | Pięści Otchłani (weapon) |
| `items/frost_blade.png` | Mroźne ostrze (weapon) |
| `items/frost_pelt.png` | Mroźne futro (material) |
| `items/frost_tiara.png` | Mroźny diadem (armor) |
| `items/frostbound_fists.png` | Skute lodem pięści (weapon) |
| `items/frostfang_spear.png` | Włócznia mroźnego kła (weapon) |
| `items/frozen_heart.png` | Zamarznięte serce (material) |
| `items/fur_armor.png` | Futrzana zbroja (armor) |
| `items/glacial_core.png` | Lodowy rdzeń (material) |
| `items/glacial_helm.png` | Lodowy hełm (armor) |
| `items/glacial_legs.png` | Lodowe nagolenniki (armor) |
| `items/glacial_plate.png` | Lodowa płytówka (armor) |
| `items/glacial_shield.png` | Lodowa tarcza (shield) |
| `items/goblin_ear.png` | Ucho goblina (material) |
| `items/gold_coin.png` | Złota moneta (currency) |
| `items/great_health_potion.png` | Silna mikstura zdrowia (potion) |
| `items/great_mana_potion.png` | Silna mikstura many (potion) |
| `items/grimtooth_crown.png` | Korona Grimtootha (armor) |
| `items/healing_herb.png` | Ziele lecznicze (material) |
| `items/healing_rune.png` | Runa leczenia (rune) |
| `items/health_potion.png` | Mikstura zdrowia (potion) |
| `items/hunting_spear.png` | Włócznia myśliwska (weapon) |
| `items/hyena_fang.png` | Kieł hieny (material) |
| `items/ice_cod.png` | Lodowy dorsz (food) |
| `items/iron_knuckles.png` | Żelazne kastety (weapon) |
| `items/iron_ore.png` | Ruda żelaza (material) |
| `items/iron_sword.png` | Żelazny miecz (weapon) |
| `items/jungle_idol.png` | Posążek z dżungli (material) |
| `items/leather_armor.png` | Skórzana zbroja (armor) |
| `items/leather_boots.png` | Skórzane buty (armor) |
| `items/leather_helmet.png` | Skórzany hełm (armor) |
| `items/leather_legs.png` | Skórzane spodnie (armor) |
| `items/magma_spear.png` | Magmowa włócznia (weapon) |
| `items/mana_potion.png` | Mikstura many (potion) |
| `items/meat.png` | Mięso (food) |
| `items/miner_helmet.png` | Hełm górnika (armor) |
| `items/molten_knuckles.png` | Stopione kastety (weapon) |
| `items/mummy_wrap.png` | Bandaż mumii (material) |
| `items/oasis_carp.png` | Karp z oazy (food) |
| `items/obsidian_armor.png` | Obsydianowa zbroja (armor) |
| `items/obsidian_helmet.png` | Obsydianowy hełm (armor) |
| `items/obsidian_legs.png` | Obsydianowe nagolenniki (armor) |
| `items/obsidian_shard.png` | Odłamek obsydianu (material) |
| `items/obsidian_shield.png` | Obsydianowa tarcza (shield) |
| `items/obsidian_wand.png` | Obsydianowa różdżka (weapon) |
| `items/pearl.png` | Perła (material) |
| `items/pet_cat.png` | Kocię (tool) |
| `items/pet_dog.png` | Szczenię (tool) |
| `items/pet_fennec.png` | Fenek (tool) |
| `items/pet_raven.png` | Kruk (tool) |
| `items/pharaoh_scepter.png` | Berło faraona (weapon) |
| `items/platinum_coin.png` | Platynowa moneta (currency) |
| `items/return_scroll.png` | Zwój powrotu (tool) |
| `items/rod_of_eternal_winter.png` | Laska wiecznej zimy (weapon) |
| `items/rod_of_frost.png` | Laska mrozu (weapon) |
| `items/rod_of_hoarfrost.png` | Laska szronu (weapon) |
| `items/rod_of_stoneroot.png` | Laska kamiennego korzenia (weapon) |
| `items/rod_of_twilight.png` | Laska zmierzchu (weapon) |
| `items/rod_of_vines.png` | Laska pnączy (weapon) |
| `items/rope.png` | Lina (tool) |
| `items/saddle.png` | Siodło (tool) |
| `items/sandstone_shield.png` | Tarcza z piaskowca (shield) |
| `items/scarab_gem.png` | Klejnot skarabeusza (material) |
| `items/scarab_helmet.png` | Hełm skarabeusza (armor) |
| `items/scorpion_tail.png` | Ogon skorpiona (material) |
| `items/serpent_spear.png` | Wężowa włócznia (weapon) |
| `items/shadow_aegis.png` | Egida cienia (shield) |
| `items/shadow_essence.png` | Esencja cienia (material) |
| `items/shadow_hood.png` | Kaptur cienia (armor) |
| `items/shadow_legs.png` | Spodnie cienia (armor) |
| `items/shadowpiercer.png` | Przebijacz cienia (weapon) |
| `items/shadowweave_armor.png` | Zbroja z cienia (armor) |
| `items/shovel.png` | Łopata (tool) |
| `items/snake_skin.png` | Wężowa skóra (material) |
| `items/spear.png` | Włócznia (ammunition) |
| `items/spider_silk.png` | Pajęcza nić (material) |
| `items/starfall_wand.png` | Różdżka spadających gwiazd (weapon) |
| `items/steel_spear.png` | Stalowa włócznia (weapon) |
| `items/stormcaller_wand.png` | Różdżka wołająca burzę (weapon) |
| `items/strong_health_potion.png` | Potężna mikstura zdrowia (potion) |
| `items/strong_mana_potion.png` | Potężna mikstura many (potion) |
| `items/studded_armor.png` | Ćwiekowana zbroja (armor) |
| `items/supreme_health_potion.png` | Najwyższa mikstura zdrowia (potion) |
| `items/supreme_mana_potion.png` | Najwyższa mikstura many (potion) |
| `items/swamp_eel.png` | Bagienny węgorz (food) |
| `items/thornroot_rod.png` | Laska cierniowego korzenia (weapon) |
| `items/torch.png` | Pochodnia (light) |
| `items/training_sword.png` | Miecz ćwiczebny (weapon) |
| `items/troll_hide.png` | Skóra trolla (material) |
| `items/umbral_grips.png` | Rękawice mroku (weapon) |
| `items/wand_of_embers.png` | Różdżka żaru (weapon) |
| `items/wand_of_inferno.png` | Różdżka piekła (weapon) |
| `items/wand_of_sparks.png` | Różdżka iskier (weapon) |
| `items/wand_of_storms.png` | Różdżka burz (weapon) |
| `items/wolf_pelt.png` | Wilcze futro (material) |
| `items/wooden_shield.png` | Drewniana tarcza (shield) |
| `items/worm.png` | Robak (bait) |
| `items/yeti_fur.png` | Sierść yeti (material) |

## Kafelki mapy (`assets/art/tiles/`)

Warstwa: ground = podłoże, decoration = trawa i kwiaty, object = drzewa i meble, wall = ściany.

| Plik | Co to jest |
|---|---|
| `tiles/grass.png` | ground, temperate |
| `tiles/cobblestone.png` | ground, town |
| `tiles/dirt.png` | ground, temperate |
| `tiles/water.png` | ground, temperate |
| `tiles/wood_floor.png` | ground, town |
| `tiles/stone_floor.png` | ground, town |
| `tiles/field.png` | ground, temperate |
| `tiles/cave_floor.png` | ground, underground |
| `tiles/bridge.png` | ground, temperate |
| `tiles/sand.png` | ground, desert |
| `tiles/snow.png` | ground, north |
| `tiles/jungle_grass.png` | ground, equatorial |
| `tiles/ice.png` | ground, north |
| `tiles/swamp.png` | ground, equatorial |
| `tiles/sandstone_floor.png` | ground, desert |
| `tiles/frozen_floor.png` | ground, north |
| `tiles/tomb_floor.png` | ground, desert |
| `tiles/tall_grass.png` | decoration, temperate |
| `tiles/flowers.png` | decoration, temperate |
| `tiles/wheat.png` | decoration, temperate |
| `tiles/rug.png` | decoration, town |
| `tiles/snow_tuft.png` | decoration, north |
| `tiles/fern.png` | decoration, equatorial |
| `tiles/dry_grass.png` | decoration, desert |
| `tiles/tree.png` | object, temperate |
| `tiles/rock.png` | object, temperate |
| `tiles/fountain.png` | object, town |
| `tiles/street_lamp.png` | object, town |
| `tiles/wall_torch.png` | object, town |
| `tiles/counter.png` | object, town |
| `tiles/fence.png` | object, temperate |
| `tiles/crystal.png` | object, underground |
| `tiles/rubble.png` | object, underground |
| `tiles/stairs_up.png` | object, any |
| `tiles/stairs_down.png` | object, any |
| `tiles/ladder.png` | object, any |
| `tiles/hole.png` | object, any |
| `tiles/campfire.png` | object, temperate |
| `tiles/door_open.png` | object, town |
| `tiles/altar.png` | object, town |
| `tiles/bush.png` | object, temperate |
| `tiles/crate.png` | object, town |
| `tiles/pine.png` | object, north |
| `tiles/palm.png` | object, equatorial |
| `tiles/cactus.png` | object, desert |
| `tiles/snow_rock.png` | object, north |
| `tiles/sandstone_rock.png` | object, desert |
| `tiles/ice_crystal.png` | object, north |
| `tiles/rope_spot.png` | object, any |
| `tiles/glow_moss.png` | object, equatorial |
| `tiles/magma.png` | object, underground |
| `tiles/stone_wall.png` | wall, town |
| `tiles/cave_wall.png` | wall, underground |
| `tiles/wood_wall.png` | wall, town |
| `tiles/ice_wall.png` | wall, north |
| `tiles/sandstone_wall.png` | wall, desert |
| `tiles/mossy_wall.png` | wall, equatorial |
| `tiles/log_wall.png` | wall, north |
| `tiles/blighted_grass.png` | ground, temperate |
| `tiles/ash.png` | ground, equatorial |
| `tiles/dark_sand.png` | ground, desert |
| `tiles/lava.png` | ground, equatorial |
| `tiles/abyss_floor.png` | ground, underground |
| `tiles/bones.png` | decoration, desert |
| `tiles/dead_tree.png` | object, temperate |
| `tiles/obsidian_rock.png` | object, equatorial |
| `tiles/obsidian_wall.png` | wall, underground |

## Obiekty (`assets/art/objects/`)

| Plik | Co to jest |
|---|---|
| `objects/chest_closed.png` | skrzynia skarbów, zamknięta |
| `objects/chest_open.png` | skrzynia skarbów, otwarta |
| `objects/training_dummy.png` | manekin treningowy |
| `objects/spike_trap.png` | pułapka z kolcami |
| `objects/horse_right.png` | koń, w prawo |
| `objects/horse_left.png` | koń, w lewo |

## Pupile (`assets/art/pets/`)

| Plik | Co to jest |
|---|---|
| `pets/pet_dog.png` | szczenię |
| `pets/pet_cat.png` | kot |
| `pets/pet_raven.png` | kruk |
| `pets/pet_fennec.png` | fenek |
