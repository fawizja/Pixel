package pl.fawizja.pixel.combat;

import pl.fawizja.pixel.character.Skill;

/** Wynik analizy uzbrojenia: maksymalne obrazenia, zasieg, typ, uzywana umiejetnosc. */
public record AttackProfile(int maxDamage, int range, DamageType type, Skill skill, int intervalMs) {}
