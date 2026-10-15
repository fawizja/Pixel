package pl.fawizja.pixel.character;

import java.util.Objects;
import pl.fawizja.pixel.balance.Balance;
import pl.fawizja.pixel.economy.BankAccount;
import pl.fawizja.pixel.inventory.AutoLootFilter;
import pl.fawizja.pixel.inventory.Container;
import pl.fawizja.pixel.inventory.Equipment;
import pl.fawizja.pixel.item.ItemRegistry;
import pl.fawizja.pixel.vocation.Vocation;
import pl.fawizja.pixel.world.Position;

/** Stan postaci gracza. Zdrowie, mana i doswiadczenie sa zawsze w poprawnych zakresach. */
public final class PlayerCharacter {

    private final String id;
    private final String name;
    private final Vocation vocation;
    private int level;
    private long experience;
    private int health;
    private int mana;
    private Position position;
    private final Container backpack;
    private final Equipment equipment;
    private final BankAccount bank;
    /** Depozyt przedmiotow w banku: wspolny dla wszystkich bankierow, nie liczy sie do udzwigu. */
    private final Container depot;
    private final pl.fawizja.pixel.friends.FriendList friends = new pl.fawizja.pixel.friends.FriendList();

    public static final int DEPOT_SLOTS = 60;
    private final Skills skills = new Skills();
    private final AutoLootFilter autoLootFilter = new AutoLootFilter();
    private final pl.fawizja.pixel.quest.QuestLog quests = new pl.fawizja.pixel.quest.QuestLog();
    private String homeTown;
    private boolean running;
    private boolean torchLit;
    private boolean defensive;
    private long regenAccumulatorMs;
    /** Pozostaly czas sytosci (podwojna regeneracja) i swiatla z czaru. */
    private long fedMs;
    private long lightBuffMs;
    private int lightBuffRadius;
    /** Pozostaly czas palenia biezacej pochodni (0 = nowa, nieuzywana). */
    private long torchBurnMs;

    /** Jedna pochodnia pali sie 30 minut czasu gry. */
    public static final long TORCH_BURN_MS = 30 * 60_000L;

    /** Wynik palenia pochodni w kroku symulacji. */
    public enum TorchTick { NONE, NEXT_LIT, BURNED_OUT }

    /** Maksymalna sytosc: 20 minut. */
    public static final long MAX_FED_MS = 20 * 60_000L;

    public PlayerCharacter(String id, String name, Vocation vocation, int level, Position position,
                           ItemRegistry registry, long bankBalance, String homeTown) {
        this.id = Objects.requireNonNull(id);
        this.name = validateName(name);
        this.vocation = Objects.requireNonNull(vocation);
        if (level < Balance.MIN_LEVEL || level > Balance.MAX_LEVEL) throw new IllegalArgumentException("level " + level);
        this.level = level;
        this.experience = Balance.experienceForLevel(level);
        this.position = Objects.requireNonNull(position);
        this.backpack = Container.backpack(registry);
        this.equipment = new Equipment(registry);
        this.bank = new BankAccount(bankBalance);
        this.depot = new Container(registry, DEPOT_SLOTS);
        this.homeTown = homeTown;
        this.health = maxHealth();
        this.mana = maxMana();
    }

    /**
     * Klient online: przepisuje stan postaci przyslany przez serwer (pozycje ustawia osobno klatka stanu,
     * zeby ruch byl plynny). Ta sama postac: imie i profesja musza sie zgadzac.
     */
    public void copyStateFrom(PlayerCharacter o) {
        if (!o.name.equals(name) || o.vocation != vocation) throw new IllegalArgumentException("different character");
        level = o.level;
        experience = o.experience;
        health = o.health;
        mana = o.mana;
        backpack.clear();
        for (int i = 0; i < o.backpack.size(); i++) {
            if (o.backpack.get(i) != null) backpack.putInSlot(i, o.backpack.get(i));
        }
        for (pl.fawizja.pixel.item.EquipSlot s : pl.fawizja.pixel.item.EquipSlot.values()) {
            equipment.setDirect(s, o.equipment.get(s));
        }
        bank.mirror(o.bank);
        friends.copyFrom(o.friends);
        depot.clear();
        for (int i = 0; i < o.depot.size(); i++) {
            if (o.depot.get(i) != null) depot.putInSlot(i, o.depot.get(i));
        }
        for (Skill s : Skill.values()) skills.set(s, o.skills.level(s), o.skills.tries(s));
        for (Integer id : new java.util.ArrayList<>(autoLootFilter.ignoredIds())) autoLootFilter.allow(id);
        for (Integer id : o.autoLootFilter.ignoredIds()) autoLootFilter.ignore(id);
        quests.mirror(o.quests);
        homeTown = o.homeTown;
        running = o.running;
        torchLit = o.torchLit;
        defensive = o.defensive;
        fedMs = o.fedMs;
        lightBuffMs = o.lightBuffMs;
        lightBuffRadius = o.lightBuffRadius;
        torchBurnMs = o.torchBurnMs;
    }

    public static String validateName(String name) {
        if (name == null) throw new IllegalArgumentException("name is null");
        String n = name.trim();
        if (n.length() < 3 || n.length() > 20 || !n.matches("[A-Za-z][A-Za-z ']*[A-Za-z]")) {
            throw new IllegalArgumentException("Invalid character name: " + name);
        }
        return n;
    }

    public String id() { return id; }
    public String name() { return name; }
    public Vocation vocation() { return vocation; }
    public int level() { return level; }
    public long experience() { return experience; }
    public int health() { return health; }
    public int mana() { return mana; }
    public int maxHealth() { return Balance.maxHealth(vocation, level); }
    public int maxMana() { return Balance.maxMana(vocation, level); }
    public int capacity() { return Balance.capacity(vocation, level); }
    public int speed() { return Balance.speed(level); }
    public Position position() { return position; }
    public Container backpack() { return backpack; }
    public Equipment equipment() { return equipment; }
    public BankAccount bank() { return bank; }
    public Container depot() { return depot; }
    public pl.fawizja.pixel.friends.FriendList friends() { return friends; }
    public Skills skills() { return skills; }
    public AutoLootFilter autoLootFilter() { return autoLootFilter; }
    public String homeTown() { return homeTown; }
    public pl.fawizja.pixel.quest.QuestLog quests() { return quests; }

    /** Zamieszkanie w miescie (u kaplana): tam postac odradza sie po smierci. */
    public void setHomeTown(String town) { this.homeTown = Objects.requireNonNull(town); }
    public boolean isDead() { return health <= 0; }
    public boolean isRunning() { return running; }
    public void setRunning(boolean running) { this.running = running; }
    public boolean isTorchLit() { return torchLit; }
    /** Postawa obronna (tarcza w HUD): mniej obrazen zadawanych i otrzymywanych. */
    public boolean isDefensive() { return defensive; }
    public void setDefensive(boolean defensive) { this.defensive = defensive; }

    /** Zapala lub gasi pochodnie; zapalic mozna tylko majac pochodnie w plecaku. */
    public boolean setTorchLit(boolean lit, int torchItemId) {
        if (lit && backpack.count(torchItemId) == 0) return false;
        torchLit = lit;
        if (lit && torchBurnMs <= 0) torchBurnMs = TORCH_BURN_MS;
        return true;
    }

    public long torchBurnMs() { return torchBurnMs; }

    /**
     * Zapalona pochodnia sie wypala: po TORCH_BURN_MS znika z plecaka, a jesli sa kolejne,
     * zapala sie nastepna. Zgaszona pochodnia nie traci paliwa.
     */
    public TorchTick tickTorch(long deltaMs, int torchItemId) {
        if (!torchLit || deltaMs <= 0) return TorchTick.NONE;
        if (backpack.count(torchItemId) == 0) {
            torchLit = false;
            torchBurnMs = 0;
            return TorchTick.BURNED_OUT;
        }
        torchBurnMs -= deltaMs;
        if (torchBurnMs > 0) return TorchTick.NONE;
        backpack.remove(torchItemId, 1);
        if (backpack.count(torchItemId) > 0) {
            torchBurnMs = TORCH_BURN_MS;
            return TorchTick.NEXT_LIT;
        }
        torchLit = false;
        torchBurnMs = 0;
        return TorchTick.BURNED_OUT;
    }

    public long lightBuffMs() { return lightBuffMs; }

    /** Przywrocenie stanu ulotnego z zapisu (sytosc, swiatlo z czaru, pochodnia). */
    public void restoreTimers(long fedMs, long lightBuffMs, int lightBuffRadius, boolean torchLit, long torchBurnMs,
                              int torchItemId) {
        this.fedMs = Math.max(0, Math.min(MAX_FED_MS, fedMs));
        this.lightBuffMs = Math.max(0, lightBuffMs);
        this.lightBuffRadius = Math.max(0, lightBuffRadius);
        this.torchBurnMs = Math.max(0, Math.min(TORCH_BURN_MS, torchBurnMs));
        this.torchLit = torchLit && backpack.count(torchItemId) > 0;
    }

    public void setPosition(Position position) { this.position = Objects.requireNonNull(position); }

    /** Postep do nastepnego poziomu w zakresie 0..1. */
    public double levelProgress() {
        if (level >= Balance.MAX_LEVEL) return 1.0;
        long cur = Balance.experienceForLevel(level);
        long next = Balance.experienceForLevel(level + 1);
        return (double) (experience - cur) / (double) (next - cur);
    }

    /**
     * Dodaje doswiadczenie (z nasyceniem do maksimum) i zwraca liczbe zdobytych poziomow.
     * HP i mana rosna o przyrost maksymalnych wartosci.
     */
    public int addExperience(long amount) {
        if (amount <= 0) return 0;
        int oldMaxHp = maxHealth();
        int oldMaxMana = maxMana();
        experience = Balance.addExperience(experience, amount);
        int newLevel = Balance.levelForExperience(experience);
        int gained = newLevel - level;
        if (gained > 0) {
            level = newLevel;
            health = Math.min(maxHealth(), health + (maxHealth() - oldMaxHp));
            mana = Math.min(maxMana(), mana + (maxMana() - oldMaxMana));
        }
        return gained;
    }

    /** Zadaje obrazenia; zwraca faktycznie odjete HP. */
    public int damage(int amount) {
        if (amount <= 0 || isDead()) return 0;
        int dealt = Math.min(health, amount);
        health -= dealt;
        return dealt;
    }

    public int heal(int amount) {
        if (amount <= 0 || isDead()) return 0;
        int healed = Math.min(maxHealth() - health, amount);
        health += healed;
        return healed;
    }

    public int restoreMana(int amount) {
        if (amount <= 0) return 0;
        int restored = Math.min(maxMana() - mana, amount);
        mana += restored;
        return restored;
    }

    public boolean spendMana(int amount) {
        if (amount < 0 || amount > mana) return false;
        mana -= amount;
        return true;
    }

    /** Regeneracja zalezna od czasu gry (wywolywana z petli symulacji). */
    public void tickRegeneration(long deltaMs) {
        if (isDead() || deltaMs <= 0) return;
        lightBuffMs = Math.max(0, lightBuffMs - deltaMs);
        regenAccumulatorMs += deltaMs;
        long tick = Balance.REGEN_TICK_SECONDS * 1000L;
        while (regenAccumulatorMs >= tick) {
            regenAccumulatorMs -= tick;
            int mult = fedMs > 0 ? 2 : 1;
            heal(Balance.healthRegenPerTick(vocation, level) * mult);
            restoreMana(Balance.manaRegenPerTick(vocation, level) * mult);
            fedMs = Math.max(0, fedMs - tick);
        }
    }

    public long fedMs() { return fedMs; }

    /** Jedzenie: dodaje sytosc; false, gdy postac jest najedzona (nic nie zuzyto). */
    public boolean feed(long ms) {
        if (ms <= 0 || fedMs + ms > MAX_FED_MS) return false;
        fedMs += ms;
        return true;
    }

    public void addLightBuff(int radius, long ms) {
        lightBuffRadius = Math.max(0, radius);
        lightBuffMs = Math.max(lightBuffMs, ms);
    }

    public int lightBuffRadius() { return lightBuffMs > 0 ? lightBuffRadius : 0; }

    /** Kara za smierc: utrata doswiadczenia (poziom moze spasc, ale nie ponizej minLevel). */
    public void applyDeathPenalty(int minLevel) {
        experience = Balance.experienceAfterDeath(experience, minLevel);
        level = Balance.levelForExperience(experience);
    }

    /** Odrodzenie z pelnym zdrowiem i mana. */
    public void revive(Position at) {
        position = Objects.requireNonNull(at);
        health = maxHealth();
        mana = maxMana();
        regenAccumulatorMs = 0;
    }

    /** Odtwarza stan z zapisu (waliduje zakresy). */
    void restoreState(long experience, int health, int mana) {
        if (experience < 0 || experience > Balance.maxExperience()) throw new IllegalArgumentException("experience");
        this.experience = experience;
        this.level = Balance.levelForExperience(experience);
        this.health = Math.max(0, Math.min(maxHealth(), health));
        this.mana = Math.max(0, Math.min(maxMana(), mana));
    }
}
