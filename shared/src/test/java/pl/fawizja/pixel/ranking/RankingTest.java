package pl.fawizja.pixel.ranking;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import org.junit.jupiter.api.Test;
import pl.fawizja.pixel.character.Skill;
import pl.fawizja.pixel.vocation.Vocation;

class RankingTest {

    @Test
    void ordersByExperienceThenNameAndFiltersByVocation() {
        Ranking r = new Ranking();
        r.put(new Ranking.Entry("Ula", Vocation.DRUID, 40, 100_000));
        r.put(new Ranking.Entry("Kuba", Vocation.PALADIN, 50, 200_000));
        r.put(new Ranking.Entry("ala", Vocation.DRUID, 40, 100_000));
        r.put(new Ranking.Entry("Ula", Vocation.DRUID, 60, 300_000)); // nowszy zapis tej samej postaci
        List<Ranking.Entry> top = r.top(10, null);
        assertEquals(List.of("Ula", "Kuba", "ala"), top.stream().map(Ranking.Entry::name).toList());
        assertEquals(3, r.size(null));
        assertEquals(2, r.position("kuba", null));
        assertEquals(2, r.position("Ala", Vocation.DRUID));
        assertEquals(0, r.position("Kuba", Vocation.DRUID), "not in the druid ranking");
        assertEquals(1, r.top(1, null).size());
        r.remove("ULA");
        assertEquals("Kuba", r.top(10, null).get(0).name());
    }

    @Test
    void skillRankingOrdersBySkillLevel() {
        Ranking r = new Ranking();
        r.put(new Ranking.Entry("Ula", Vocation.DRUID, 60, 300_000, new int[] {10, 10, 10, 12, 70, 20}));
        r.put(new Ranking.Entry("Ola", Vocation.SORCERER, 40, 100_000, new int[] {10, 10, 10, 12, 75, 30}));
        r.put(new Ranking.Entry("Kuba", Vocation.PALADIN, 50, 200_000));
        assertEquals(List.of("Ola", "Ula", "Kuba"),
                r.topSkill(10, Skill.MAGIC).stream().map(Ranking.Entry::name).toList());
        assertEquals(2, r.positionSkill("ula", Skill.MAGIC));
        assertEquals(List.of("Ula", "Ola"), r.topSkill(2, Skill.SWORD).stream().map(Ranking.Entry::name).toList(), "ties by experience");
        assertEquals(0, r.top(10, null).get(1).skill(Skill.FISHING), "old entries have no skills");
    }

    @Test
    void skillFromPolishWords() {
        assertEquals(Skill.MAGIC, Ranking.skillFrom("magia"));
        assertEquals(Skill.FIST, Ranking.skillFrom("pięści"));
        assertEquals(Skill.FISHING, Ranking.skillFrom("Wędkarstwo"));
        assertEquals(Skill.FISHING, Ranking.skillFrom("ryby"));
        assertEquals(Skill.DISTANCE, Ranking.skillFrom("dyst"));
        assertEquals(Skill.SWORD, Ranking.skillFrom("miecz"));
        assertNull(Ranking.vocationFrom("magia"), "magia is a skill, not the sorcerer");
        assertNull(Ranking.skillFrom("ma"));
        assertNull(Ranking.skillFrom("rycerz"));
    }

    @Test
    void vocationFromPolishOrEnglishWords() {
        assertEquals(Vocation.DRUID, Ranking.vocationFrom("druid"));
        assertEquals(Vocation.SORCERER, Ranking.vocationFrom("Mag"));
        assertEquals(Vocation.SORCERER, Ranking.vocationFrom("sorc"));
        assertEquals(Vocation.MONK, Ranking.vocationFrom("mnich"));
        assertEquals(Vocation.PALADIN, Ranking.vocationFrom("paladyn"));
        assertEquals(Vocation.KNIGHT, Ranking.vocationFrom("rycerz"));
        assertEquals(Vocation.KNIGHT, Ranking.vocationFrom("knight"));
        assertNull(Ranking.vocationFrom("krasnolud"));
        assertNull(Ranking.vocationFrom(" "));
    }

    @Test
    void viewSurvivesTheWireAndSkipsBrokenRows() {
        RankingView v = new RankingView("Ranking serwera", 2, 30,
                List.of(new RankingView.Row(1, "Stary Wilk", "Monk", 120), new RankingView.Row(2, "Ula", "Druid", 99)));
        String[] f = v.encode();
        String[] msg = new String[f.length + 1];
        msg[0] = "RANK";
        System.arraycopy(f, 0, msg, 1, f.length);
        assertEquals(v, RankingView.parse(msg));
        assertEquals(1, RankingView.parse(new String[] {"RANK", "T", "0", "1", "1;Ula;Druid;5", "zle"}).rows().size());
        assertNull(RankingView.parse(new String[] {"RANK", "T", "x", "1"}));
        assertNull(RankingView.parse(new String[] {"TS", "-"}));
    }
}
