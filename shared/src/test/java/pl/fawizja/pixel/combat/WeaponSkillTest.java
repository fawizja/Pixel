package pl.fawizja.pixel.combat;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;
import pl.fawizja.pixel.TestData;
import pl.fawizja.pixel.character.CharacterCodec;
import pl.fawizja.pixel.character.CharacterFactory;
import pl.fawizja.pixel.character.PlayerCharacter;
import pl.fawizja.pixel.character.Skill;
import pl.fawizja.pixel.item.EquipSlot;
import pl.fawizja.pixel.item.ItemCategory;
import pl.fawizja.pixel.item.ItemStack;
import pl.fawizja.pixel.item.ItemType;
import pl.fawizja.pixel.item.WeaponType;
import pl.fawizja.pixel.magic.SpellSystem;
import pl.fawizja.pixel.monster.Monster;
import pl.fawizja.pixel.monster.MonsterManager;
import pl.fawizja.pixel.monster.SpawnPoint;
import pl.fawizja.pixel.vocation.Vocation;
import pl.fawizja.pixel.world.Position;

/** Topor i maczuga jako osobne umiejetnosci walki obok miecza (jak w Tibii). */
class WeaponSkillTest {

    @Test
    void skillsAreInTibiaOrder() {
        assertEquals(List.of(Skill.FIST, Skill.CLUB, Skill.SWORD, Skill.AXE, Skill.DISTANCE, Skill.SHIELDING,
                Skill.FISHING, Skill.MAGIC), List.of(Skill.values()));
        assertEquals("Topór", Skill.AXE.label());
        assertEquals("Maczuga", Skill.CLUB.label());
        assertEquals(Skill.AXE, pl.fawizja.pixel.ranking.Ranking.skillFrom("topor"));
        assertEquals(Skill.CLUB, pl.fawizja.pixel.ranking.Ranking.skillFrom("maczuga"));
        assertEquals(Skill.MAGIC, pl.fawizja.pixel.ranking.Ranking.skillFrom("mag"));
    }

    @Test
    void everyWeaponDeclaresItsType() {
        for (ItemType t : TestData.ITEMS.all()) {
            if (t.category() != ItemCategory.WEAPON) {
                assertEquals(WeaponType.NONE, t.weaponType(), t.key());
                continue;
            }
            WeaponType w = t.weaponType();
            assertNotEquals(WeaponType.NONE, w, "weapon without type: " + t.key());
            String n = t.name().toLowerCase(java.util.Locale.ROOT);
            if (t.range() > 1) {
                assertEquals(t.damageType() == DamageType.PHYSICAL ? WeaponType.DISTANCE : WeaponType.WAND, w, t.key());
            } else if (n.contains("topór") || n.contains("toporek") || t.key().contains("axe")) {
                assertEquals(WeaponType.AXE, w, t.key());
            } else if (n.contains("młot") || n.contains("buzdygan") || n.contains("maczuga") || t.key().contains("hammer")
                    || t.key().contains("mace")) {
                assertEquals(WeaponType.CLUB, w, t.key());
            } else if (n.contains("miecz") || n.contains("ostrze") || t.key().contains("sword") || t.key().contains("blade")) {
                assertEquals(WeaponType.SWORD, w, t.key());
            }
        }
        assertEquals(WeaponType.AXE, TestData.ITEMS.byKey("hand_axe").weaponType());
        assertEquals(WeaponType.CLUB, TestData.ITEMS.byKey("war_hammer").weaponType());
        assertEquals(WeaponType.CLUB, TestData.ITEMS.byKey("mace").weaponType());
        assertEquals(WeaponType.SWORD, TestData.ITEMS.byKey("knight_sword").weaponType());
        assertEquals(WeaponType.FIST, TestData.ITEMS.byKey("cloth_wraps").weaponType());
    }

    private static PlayerCharacter knightWith(String weapon) {
        PlayerCharacter pc = TestData.newCharacter(Vocation.KNIGHT);
        pc.equipment().setDirect(EquipSlot.WEAPON, ItemStack.of(TestData.ITEMS.byKey(weapon).id()));
        return pc;
    }

    @Test
    void damageUsesTheMatchingSkill() {
        assertEquals(Skill.AXE, CombatFormulas.attackProfile(knightWith("battle_axe"), TestData.ITEMS).skill());
        assertEquals(Skill.CLUB, CombatFormulas.attackProfile(knightWith("mace"), TestData.ITEMS).skill());
        assertEquals(Skill.SWORD, CombatFormulas.attackProfile(knightWith("knight_sword"), TestData.ITEMS).skill());
        PlayerCharacter axe = knightWith("battle_axe");
        int base = CombatFormulas.attackProfile(axe, TestData.ITEMS).maxDamage();
        axe.skills().set(Skill.SWORD, 80, 0);
        assertEquals(base, CombatFormulas.attackProfile(axe, TestData.ITEMS).maxDamage(), "sword skill does not help an axe");
        axe.skills().set(Skill.AXE, 80, 0);
        assertTrue(CombatFormulas.attackProfile(axe, TestData.ITEMS).maxDamage() > base, "axe skill does");
        assertEquals(Skill.AXE, SpellSystem.weaponSkill(axe), "weapon spells use the held weapon's skill");
    }

    @Test
    void fightingTrainsTheSkillOfTheWeapon() {
        PlayerCharacter pc = knightWith("mace");
        pc.setPosition(new Position(10, 12, 0));
        SpawnPoint sp = new SpawnPoint("wolf", new Position(11, 12, 0), 0, 1, 60);
        MonsterManager mm = new MonsterManager(TestData.varden(), CombatTest.REG, List.of(sp), new Random(3));
        mm.populate(null);
        Monster wolf = mm.monsters().get(0);
        CombatSystem cs = CombatTest.system(pc, new Random(5), mm);
        cs.setTarget(wolf);
        for (long t = 0; t < 120_000 && !wolf.isDead(); t += 100) cs.update(t);
        assertTrue(pc.skills().tries(Skill.CLUB) > 0 || pc.skills().level(Skill.CLUB) > 10, "club trained");
        assertEquals(0, pc.skills().tries(Skill.SWORD), "sword untouched");
        assertEquals(0, pc.skills().tries(Skill.AXE), "axe untouched");
    }

    @Test
    void knightsStartWithSwordAxeAndClubAtTheSameLevel() {
        for (int level : new int[] {1, 8, 100, 300}) {
            PlayerCharacter k = new CharacterFactory(pl.fawizja.pixel.config.StartingConfig.loadDefault(), TestData.ITEMS)
                    .create("Rycerz", Vocation.KNIGHT, new Position(10, 10, 0), level);
            int sword = k.skills().level(Skill.SWORD);
            assertEquals(sword, k.skills().level(Skill.AXE), "level " + level);
            assertEquals(sword, k.skills().level(Skill.CLUB), "level " + level);
        }
    }

    @Test
    void oldSavesGetAxeAndClubFromTheSwordSkill() throws Exception {
        PlayerCharacter pc = TestData.newCharacter(Vocation.KNIGHT);
        pc.skills().set(Skill.SWORD, 57, 123);
        CharacterCodec codec = new CharacterCodec(TestData.ITEMS);
        String old = StaminaTest.SaveEdit.dropLines(codec.encode(pc), "skill.AXE=");
        old = StaminaTest.SaveEdit.dropLines(old, "skill.CLUB=");
        PlayerCharacter back = codec.decode(old);
        assertEquals(57, back.skills().level(Skill.AXE));
        assertEquals(57, back.skills().level(Skill.CLUB));
        assertEquals(123, back.skills().tries(Skill.AXE));
        // nowy zapis: kazda umiejetnosc osobno
        back.skills().set(Skill.AXE, 60, 0);
        PlayerCharacter again = codec.decode(codec.encode(back));
        assertEquals(60, again.skills().level(Skill.AXE));
        assertEquals(57, again.skills().level(Skill.CLUB));
        // umiejetnosc z przyszlej wersji nie psuje odczytu
        String future = StaminaTest.SaveEdit.replaceLine(codec.encode(back), "skill.FISHING=", "skill.FISHING=10/0\nskill.LASSO=12/0");
        assertEquals(60, codec.decode(future).skills().level(Skill.AXE));
    }

    @Test
    void wormsFromTheAmmoSlotMoveToTheBackpackOnLoad() throws Exception {
        PlayerCharacter pc = TestData.newCharacter(Vocation.KNIGHT);
        int worm = TestData.ITEMS.byKey("worm").id();
        int before = pc.backpack().count(worm);
        CharacterCodec codec = new CharacterCodec(TestData.ITEMS);
        String save = codec.encode(pc);
        // stary zapis: robaki w polu amunicji
        save = StaminaTest.SaveEdit.rebuild(save, l -> l.startsWith("eq.WEAPON=") ? l + "\neq.AMMO=" + worm + "x25" : l);
        PlayerCharacter back = codec.decode(save);
        assertNull(back.equipment().get(EquipSlot.AMMO), "nothing left in the ammo slot");
        assertEquals(before + 25, back.backpack().count(worm), "worms are in the backpack");
    }
}
