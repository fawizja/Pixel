package pl.fawizja.pixel.character;

import pl.fawizja.pixel.item.ItemStack;
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
    private final pl.fawizja.pixel.achievement.Bestiary bestiary = new pl.fawizja.pixel.achievement.Bestiary();
    /** Charmy bestiariusza (punkty, echa, stopnie, przypisania do potworow). */
    private final pl.fawizja.pixel.charm.Charms charms = new pl.fawizja.pixel.charm.Charms();
    private final pl.fawizja.pixel.achievement.Achievements achievements = new pl.fawizja.pixel.achievement.Achievements();
    private final pl.fawizja.pixel.daily.DailyTask daily = new pl.fawizja.pixel.daily.DailyTask();
    private final pl.fawizja.pixel.quest.HuntTask hunt = new pl.fawizja.pixel.quest.HuntTask();
    private final pl.fawizja.pixel.daily.LoginStreak loginStreak = new pl.fawizja.pixel.daily.LoginStreak();
    private final pl.fawizja.pixel.item.Enchantments enchantments = new pl.fawizja.pixel.item.Enchantments();
    /** Harmonia, cnota i spokoj mnicha (inne profesje maja stan poczatkowy). */
    private final pl.fawizja.pixel.vocation.Harmony harmony = new pl.fawizja.pixel.vocation.Harmony();
    /** Kolo Przeznaczenia (punkty promocji od poziomu 51, Dar Zycia, awatar). */
    private final pl.fawizja.pixel.wheel.DestinyWheel wheel = new pl.fawizja.pixel.wheel.DestinyWheel();
    /** Otwarte skrzynie skarbow (identyfikatory). */
    private final java.util.Set<String> openedChests = new java.util.LinkedHashSet<>();
    /** Ukonczone wyprawy na bossa w piec postaci (identyfikatory z boss_quests.csv) - kazda raz na postac. */
    private final java.util.Set<String> completedBossQuests = new java.util.LinkedHashSet<>();
    /**
     * Znaczniki zadan postaci (zapisywane): pokonani bossowie Pieczeci Gasnacej Gwiazdy ({@code boss.<klucz potwora>}) i
     * odblokowania ({@link pl.fawizja.pixel.quest.StarQuest#FLAG_SEALED}). Sprawdzaja je przejscia strzezone mapy
     * ({@link pl.fawizja.pixel.world.QuestGate}).
     */
    private final java.util.Set<String> questFlags = new java.util.LinkedHashSet<>();

    public static final int DEPOT_SLOTS = 60;
    /** Odkryta mapa (mgla minimapy) - nie kopiowana w copyStateFrom: klient online odkrywa sam i dostaje FOG. */
    private final pl.fawizja.pixel.world.ExploredMap explored = new pl.fawizja.pixel.world.ExploredMap();
    private final Skills skills;
    private final AutoLootFilter autoLootFilter = new AutoLootFilter();
    /** Rodzaje przedmiotow oznaczone "Zachowaj": nie ida do hurtowej sprzedazy lupu, depozytu ani na ziemie. */
    private final java.util.Set<Integer> keptItems = new java.util.TreeSet<>();
    private final pl.fawizja.pixel.quest.QuestLog quests = new pl.fawizja.pixel.quest.QuestLog();
    private String homeTown;
    private boolean running;
    private boolean torchLit;
    private pl.fawizja.pixel.combat.FightMode fightMode = pl.fawizja.pixel.combat.FightMode.BALANCED;
    private long regenAccumulatorMs;
    /** Pozostaly czas sytosci (podwojna regeneracja) i swiatla z czaru. */
    private long fedMs;
    private long restedMs;
    private long lightBuffMs;
    private long hasteMs;
    /** Dodatkowe plecaki (po 20 slotow kazdy) podpiete do plecaka glownego. */
    private int extraBags;
    private int hastePercent;
    private int lightBuffRadius;
    /** Pozostaly czas palenia zalozonej pochodni (0 = nowa, nieuzywana). */
    private long torchBurnMs;
    /** Pyl Kuzni (zasob postaci, nie przedmiot) i jego limit ({@link pl.fawizja.pixel.item.Forge}). */
    private int forgeDust;
    private int dustLimit = pl.fawizja.pixel.item.Forge.DUST_START_LIMIT;

    /** Jedna pochodnia pali sie 20 minut czasu gry (liczy sie tylko czas, gdy plonie). */
    public static final long TORCH_BURN_MS = 20 * 60_000L;

    /** Wynik palenia pochodni w kroku symulacji. */
    public enum TorchTick { NONE, BURNED_OUT }

    /** Maksymalna sytosc: 20 minut. */
    public static final long MAX_FED_MS = 20 * 60_000L;

    public PlayerCharacter(String id, String name, Vocation vocation, int level, Position position,
                           ItemRegistry registry, long bankBalance, String homeTown) {
        this.id = Objects.requireNonNull(id);
        this.name = validateName(name);
        this.vocation = Objects.requireNonNull(vocation);
        this.skills = new Skills(vocation);
        if (level < Balance.MIN_LEVEL || level > Balance.MAX_LEVEL) throw new IllegalArgumentException("level " + level);
        this.level = level;
        this.rewardedLevel = level;
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
    public pl.fawizja.pixel.world.ExploredMap explored() { return explored; }

    public void copyStateFrom(PlayerCharacter o) {
        if (!o.name.equals(name) || o.vocation != vocation) throw new IllegalArgumentException("different character");
        level = o.level;
        experience = o.experience;
        health = o.health;
        mana = o.mana;
        backpack.clear();
        backpack.resize(o.backpack.size());
        for (int i = 0; i < o.backpack.size(); i++) {
            if (o.backpack.get(i) != null) backpack.putInSlot(i, o.backpack.get(i));
        }
        for (pl.fawizja.pixel.item.EquipSlot s : pl.fawizja.pixel.item.EquipSlot.values()) {
            equipment.setDirect(s, o.equipment.get(s));
        }
        bank.mirror(o.bank);
        friends.copyFrom(o.friends);
        bestiary.copyFrom(o.bestiary);
        charms.copyFrom(o.charms);
        achievements.copyFrom(o.achievements);
        daily.copyFrom(o.daily);
        hunt.copyFrom(o.hunt);
        loginStreak.copyFrom(o.loginStreak);
        enchantments.copyFrom(o.enchantments);
        harmony.copyFrom(o.harmony);
        wheel.copyFrom(o.wheel);
        openedChests.clear();
        openedChests.addAll(o.openedChests);
        completedBossQuests.clear();
        completedBossQuests.addAll(o.completedBossQuests);
        questFlags.clear();
        questFlags.addAll(o.questFlags);
        crates = o.crates;
        wallLights = o.wallLights;
        actionBar = o.actionBar;
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
        hasMount = o.hasMount;
        mounted = o.mounted;
        blessings = o.blessings;
        premium = o.premium;
        promoted = o.promoted;
        rewardedLevel = o.rewardedLevel;
        stats.copyFrom(o.stats);
        weeklyBossWeek = o.weeklyBossWeek;
        pet = o.pet;
        title = o.title;
        house = o.house;
        houseGuests = o.houseGuests;
        rentPaidUntil = o.rentPaidUntil;
        rentMissed = o.rentMissed;
        outfit = o.outfit;
        tips = o.tips;
        torchLit = o.torchLit;
        fightMode = o.fightMode;
        fedMs = o.fedMs;
        restedMs = o.restedMs;
        stamina.copyFrom(o.stamina);
        lightBuffMs = o.lightBuffMs;
        hasteMs = o.hasteMs;
        extraBags = o.extraBags;
        hastePercent = o.hastePercent;
        lightBuffRadius = o.lightBuffRadius;
        torchBurnMs = o.torchBurnMs;
        forgeDust = o.forgeDust;
        dustLimit = o.dustLimit;
    }

    /** Pyl Kuzni (z potworow pod wplywem i diabolicznych). */
    public int forgeDust() { return forgeDust; }
    /** Limit pylu (100..225). */
    public int dustLimit() { return dustLimit; }

    /** Dodaje pyl do limitu; zwraca, ile faktycznie dodano. */
    public int addDust(int amount) {
        int add = Math.max(0, Math.min(amount, dustLimit - forgeDust));
        forgeDust += add;
        return add;
    }

    public boolean spendDust(int amount) {
        if (amount < 0 || forgeDust < amount) return false;
        forgeDust -= amount;
        return true;
    }

    /** Podnosi limit pylu o 1 (koszt sprawdza wywolujacy). */
    public void raiseDustLimit() {
        dustLimit = Math.min(pl.fawizja.pixel.item.Forge.DUST_MAX_LIMIT, dustLimit + 1);
    }

    /** Awatar z Transcendencji (Kuznia): pozostaly czas; stan ulotny, nie zapisywany. */
    private long avatarMs;

    public boolean avatarActive() { return avatarMs > 0; }
    public long avatarMs() { return avatarMs; }
    public void startAvatar(long ms) { avatarMs = Math.max(avatarMs, ms); }
    public void tickAvatar(long dtMs) { avatarMs = Math.max(0, avatarMs - Math.max(0, dtMs)); }

    public void restoreForge(int dust, int limit) {
        dustLimit = Math.max(pl.fawizja.pixel.item.Forge.DUST_START_LIMIT, Math.min(pl.fawizja.pixel.item.Forge.DUST_MAX_LIMIT, limit));
        forgeDust = Math.max(0, Math.min(dustLimit, dust));
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
    public int maxHealth() { return Balance.maxHealth(vocation, level) + wheelBonus().health; }
    public int maxMana() { return Balance.maxMana(vocation, level) + wheelBonus().mana; }
    public int capacity() { return Balance.capacity(vocation, level) + wheelBonus().capacity; }

    public pl.fawizja.pixel.wheel.DestinyWheel wheel() { return wheel; }

    /**
     * Punkty promocji Kola Przeznaczenia: poziom - 50 od poziomu 51, z promocja i premium; inaczej 0. Kazdy mod klejnotu
     * na stopniu IV dodaje 1 punkt (jak w Tibii).
     */
    public int wheelPoints() {
        int p = pl.fawizja.pixel.wheel.DestinyWheel.available(level, promoted, premium);
        return p > 0 ? p + wheel.gems().maxedMods() : 0;
    }

    /** Premie z kola (oddanie, przekonanie, objawienia); puste bez promocji, premium albo poziomu 51. */
    public pl.fawizja.pixel.wheel.WheelBonus wheelBonus() { return wheel.bonus(vocation, wheelPoints() > 0); }

    /** Po zmianie kola albo utracie poziomu: nadmiar punktow schodzi od zewnatrz, HP i mana nie ponad maksimum. */
    public void refreshWheel() {
        if (wheelPoints() > 0) wheel.trim(wheelPoints());
        health = Math.min(health, maxHealth());
        mana = Math.min(mana, maxMana());
    }
    /** Premia szybkosci na wierzchowcu. */
    public static final int MOUNT_SPEED = 60;

    /** Szybkosc z poziomu, wierzchowca i czaru przyspieszenia (premia procentowa liczona od calosci). */
    public int speed() {
        int base = Balance.speed(level) + (mounted ? MOUNT_SPEED : 0);
        int v = hasteMs > 0 ? base + base * hastePercent / 100 : base;
        // Snajper (utito tempo san) spowalnia jak paraliz w Tibii, zamrozenie tez
        if (spellBuffMs(BUFF_SHARPSHOOTER) > 0) v = v * 70 / 100;
        // lodowa fala i spojrzenie smoka (czary potworow): spowolnienie jak paraliz, nie zapisuje sie w postaci
        if (slowMs > 0) v = Math.max(1, v * (100 - slowPercent) / 100);
        return v;
    }

    /** Najwiecej dodatkowych plecakow: plecak glowny + 3 = 80 slotow. */
    public static final int MAX_EXTRA_BAGS = 3;

    public int extraBags() { return extraBags; }

    /** Liczba stron plecaka (glowny + dodatkowe). */
    public int bagPages() { return 1 + extraBags; }

    /** Podpina kolejny plecak (+20 slotow); false, gdy osiagnieto limit. */
    public boolean attachBag() {
        if (extraBags >= MAX_EXTRA_BAGS) return false;
        extraBags++;
        backpack.resize(bagPages() * pl.fawizja.pixel.inventory.Container.BACKPACK_SLOTS);
        return true;
    }

    /** Odpina ostatni plecak; tylko gdy jego 20 slotow jest pustych. */
    public boolean detachBag() {
        if (extraBags == 0 || !backpack.resize((bagPages() - 1) * pl.fawizja.pixel.inventory.Container.BACKPACK_SLOTS)) return false;
        extraBags--;
        return true;
    }

    /** Odtwarzanie zapisu: liczba plecakow przed wczytaniem slotow. */
    public void restoreBags(int n) {
        while (extraBags > Math.max(0, n) && detachBag()) { }
        while (extraBags < Math.min(MAX_EXTRA_BAGS, n) && attachBag()) { }
    }

    /** Przyspieszenie (utani hur / utani gran hur): nowy czar zastepuje poprzedni, jak w Tibii. */
    public void addHaste(int percent, long ms) {
        hastePercent = Math.max(0, percent);
        hasteMs = Math.max(0, ms);
    }

    /** Spowolnienie od czaru potwora (paraliz): wolniejszy marsz przez ms; silniejsze zastepuje slabsze. */
    public void addSlow(int percent, long ms) {
        if (percent <= 0 || ms <= 0) return;
        if (slowMs > 0 && percent < slowPercent) return;
        slowPercent = Math.min(90, percent);
        slowMs = Math.max(slowMs, ms);
    }

    public boolean slowed() { return slowMs > 0; }
    public int slowPercent() { return slowMs > 0 ? slowPercent : 0; }
    public long slowMs() { return slowMs; }

    private long slowMs;
    private int slowPercent;

    public long hasteMs() { return hasteMs; }
    public int hastePercent() { return hasteMs > 0 ? hastePercent : 0; }

    private boolean hasMount;
    private boolean mounted;
    /** Kupione blogoslawienstwa (maska bitow {@link Blessing#bit()}); zuzywaja sie przy smierci. */
    private int blessings;

    /** Poziom do {@link Blessing#ADVENTURER_LEVEL}: wszystkie zwykle i Zrzadzenie losu za darmo (Blogoslawienstwo Poszukiwacza). */
    public boolean adventurerBlessing() { return level <= Blessing.ADVENTURER_LEVEL; }

    /** Ma blogoslawienstwo (kupione albo z Blogoslawienstwa Poszukiwacza - ono nie obejmuje gorskich). */
    public boolean hasBlessing(Blessing b) {
        return (blessings & b.bit()) != 0 || adventurerBlessing();
    }

    /** Czy blogoslawienstwo zostalo kupione (bez Blogoslawienstwa Poszukiwacza). */
    public boolean boughtBlessing(Blessing b) { return (blessings & b.bit()) != 0; }

    public void addBlessing(Blessing b) { blessings |= b.bit(); }

    public int blessingMask() { return blessings; }

    public void setBlessingMask(int mask) { blessings = mask & ((1 << Blessing.values().length) - 1); }

    /** Ile blogoslawienstw zmniejsza strate (bez Zrzadzenia losu, 0..5). */
    public int blessingCount() {
        int n = 0;
        for (Blessing b : Blessing.values()) if (b.countsForLoss() && hasBlessing(b)) n++;
        return n;
    }

    /** Ma jakiekolwiek blogoslawienstwo zmniejszajace strate. */
    public boolean isBlessed() { return blessingCount() > 0; }

    /** Konto premium (kupione u krola w Varden): rejsy duzymi statkami miedzy portami. */
    private boolean premium;
    /** Promocja profesji (u krola, tylko z premium): znak przy postaci, nazwa jak w Tibii, szybsza regeneracja. */
    private boolean promoted;

    public boolean isPremium() { return premium; }
    public void setPremium(boolean v) { premium = v; }
    public boolean isPromoted() { return promoted; }
    /** Rejsy statkami (kapitanowie): konto premium albo promocja profesji (Darek 2026-11-11). */
    public boolean canSail() { return premium || promoted; }
    public void setPromoted(boolean v) { promoted = v; }

    /** Nazwa profesji z uwzglednieniem promocji (np. Elite Knight). */
    public String vocationTitle() { return promoted ? vocation.promotedName() : vocation.displayName(); }

    /** Najwyzszy poziom, za ktory sprawdzono juz nagrody progow ({@link LevelRewards}). */
    private int rewardedLevel;

    public int rewardedLevel() { return rewardedLevel; }

    /** Liczniki do okna /statystyki. */
    private final CharacterStats stats = new CharacterStats();

    public CharacterStats stats() { return stats; }

    /** Tydzien, w ktorym postac odebrala nagrode za bossa tygodnia (-1 = nigdy). */
    private long weeklyBossWeek = -1;

    public long weeklyBossWeek() { return weeklyBossWeek; }
    public void setWeeklyBossWeek(long week) { weeklyBossWeek = week; }
    public void setRewardedLevel(int v) { rewardedLevel = Math.max(0, v); }

    /** Przywolany pupil (klucz przedmiotu pet_*) albo null; chodzi za postacia i daje premie doswiadczenia. */
    private String pet;

    /** Premia doswiadczenia z potworow, gdy pupil chodzi za postacia (procent, po etapach serwera). */
    public static final int PET_EXP_PERCENT = 5;

    public String pet() { return pet; }

    /** Biezaca premia doswiadczenia od pupila: {@link #PET_EXP_PERCENT} albo 0 bez pupila. */
    public int petExpPercent() { return pet != null ? PET_EXP_PERCENT : 0; }
    public void setPet(String key) { pet = key == null || key.isEmpty() ? null : key; }

    /** Wybrany tytul: klucz zdobytego osiagniecia albo null. */
    private String title;
    /** Pokazane podpowiedzi (bity Tips.Tip). */
    private long tips;

    public boolean tipShown(int bit) { return (tips & (1L << bit)) != 0; }
    public void markTip(int bit) { tips |= 1L << bit; }
    public long tips() { return tips; }
    public void restoreTips(long mask) { tips = mask; }

    /** Kolor stroju (Outfit); 0 = kolor profesji. */
    private int outfit;

    public int outfit() { return outfit; }
    public void setOutfit(int i) { outfit = Outfit.clamp(i); }

    public String title() { return title; }

    /** Dom postaci (id z houses.csv) albo null. */
    private String house;
    /** Chwila (ms od 1970), w ktorej postac zasnela w lozku i sie wylogowala; 0 = wylogowanie bez lozka. */
    private long sleptAt;

    public long sleptAt() { return sleptAt; }
    public void setSleptAt(long wallMs) { sleptAt = Math.max(0, wallMs); }

    public String house() { return house; }
    public void setHouse(String id) { house = id == null || id.isEmpty() || id.equals("-") ? null : id; }

    /** Gra offline: goscie domu ("Imie:full,Imie:entry") i czynsz (oplacony do, ms od 1970; zalegle platnosci). */
    private String houseGuests = "";
    private long rentPaidUntil;
    private int rentMissed;

    public String houseGuests() { return houseGuests; }
    public void setHouseGuests(String g) { houseGuests = g == null ? "" : g; }
    public long rentPaidUntil() { return rentPaidUntil; }
    public int rentMissed() { return rentMissed; }
    public void setRent(long paidUntil, int missed) {
        rentPaidUntil = Math.max(0, paidUntil);
        rentMissed = Math.max(0, missed);
    }
    public void setTitle(String key) { title = key == null || key.isEmpty() || key.equals("-") ? null : key; }

    public boolean hasMount() { return hasMount; }
    public void setHasMount(boolean v) {
        hasMount = v;
        if (!v) mounted = false;
    }
    public boolean isMounted() { return mounted; }
    public void setMounted(boolean v) { mounted = v && hasMount; }
    public Position position() { return position; }
    public Container backpack() { return backpack; }
    public Equipment equipment() { return equipment; }
    public BankAccount bank() { return bank; }
    public Container depot() { return depot; }
    public pl.fawizja.pixel.friends.FriendList friends() { return friends; }
    public pl.fawizja.pixel.achievement.Bestiary bestiary() { return bestiary; }
    public pl.fawizja.pixel.charm.Charms charms() { return charms; }
    public pl.fawizja.pixel.achievement.Achievements achievements() { return achievements; }
    public pl.fawizja.pixel.daily.DailyTask daily() { return daily; }
    /** Zlecenie lowcy (przewodnik). */
    public pl.fawizja.pixel.quest.HuntTask hunt() { return hunt; }
    public pl.fawizja.pixel.daily.LoginStreak loginStreak() { return loginStreak; }
    public pl.fawizja.pixel.item.Enchantments enchantments() { return enchantments; }
    public pl.fawizja.pixel.vocation.Harmony harmony() { return harmony; }

    /**
     * Poziom umiejetnosci do formul walki i czarow: walka piesciami mnicha z cnota sprawiedliwosci +15% (+30% w spokoju),
     * jak premia SKILL_FISTPERCENT w Canary. Pozostale umiejetnosci bez zmian.
     */
    public int effectiveSkill(Skill s) {
        int lvl = skills.level(s) + pl.fawizja.pixel.wheel.WheelEffects.skillBonus(this, s); // Kolo Przeznaczenia
        lvl = (int) Math.round(lvl * spellSkillFactor(s));
        if (s != Skill.FIST || vocation != Vocation.MONK) return lvl;
        return (int) Math.round(lvl * harmony.fistFactor());
    }

    // ------------------------------------------------------------------ stany i wzmocnienia z czarow (0.240)

    /** Wzmocnienia czarow jak w Canary: Krwawy szal, Obronca, Snajper, Szybka stopa (klucze czarow). */
    public static final String BUFF_BLOOD_RAGE = "blood_rage";
    public static final String BUFF_PROTECTOR = "protector";
    public static final String BUFF_SHARPSHOOTER = "sharpshooter";
    public static final String BUFF_SWIFT_FOOT = "swift_foot";

    private final pl.fawizja.pixel.combat.Conditions conditions = new pl.fawizja.pixel.combat.Conditions();
    private final java.util.Map<String, Long> spellBuffs = new java.util.HashMap<>();
    private int magicShield;
    private long magicShieldMs;
    private int lastShieldAbsorbed;
    private long invisibleMs;
    private String illusion;
    private long illusionMs;
    private int regenAmount;
    private long regenIntervalMs;
    private long regenLeftMs;
    private long regenAccMs;

    /** Stany z obrazeniami co tik (zatrucie, podpalenie...); ulotne jak w Tibii po smierci. */
    public pl.fawizja.pixel.combat.Conditions conditions() { return conditions; }

    /** Mnoznik umiejetnosci z wzmocnien: Krwawy szal walka wrecz x1,35, Obronca tarcza x2,2, Snajper dystans x1,4. */
    public double spellSkillFactor(Skill s) {
        if (spellBuffs.isEmpty()) return 1.0;
        if (s.isMeleeWeapon() || s == Skill.FIST) return spellBuffMs(BUFF_BLOOD_RAGE) > 0 ? 1.35 : 1.0;
        if (s == Skill.SHIELDING) return spellBuffMs(BUFF_PROTECTOR) > 0 ? 2.2 : 1.0;
        if (s == Skill.DISTANCE) return spellBuffMs(BUFF_SHARPSHOOTER) > 0 ? 1.4 : 1.0;
        return 1.0;
    }

    /** Mnoznik zadawanych obrazen: Obronca 65%, Szybka stopa 50% (Canary BUFF_DAMAGEDEALT). */
    public double outgoingFactor() {
        if (spellBuffs.isEmpty()) return 1.0;
        double f = 1.0;
        if (spellBuffMs(BUFF_PROTECTOR) > 0) f *= 0.65;
        if (spellBuffMs(BUFF_SWIFT_FOOT) > 0) f *= 0.5;
        return f;
    }

    /** Mnoznik otrzymywanych obrazen: Krwawy szal 115% (Canary BUFF_DAMAGERECEIVED). */
    public double incomingFactor() {
        return !spellBuffs.isEmpty() && spellBuffMs(BUFF_BLOOD_RAGE) > 0 ? 1.15 : 1.0;
    }

    /** Wlacza wzmocnienie czaru na ms (Krwawy szal i Obronca wykluczaja sie jak w Canary - wspolny SUBID). */
    public void addSpellBuff(String key, long ms) {
        if (BUFF_BLOOD_RAGE.equals(key)) spellBuffs.remove(BUFF_PROTECTOR);
        if (BUFF_PROTECTOR.equals(key)) spellBuffs.remove(BUFF_BLOOD_RAGE);
        spellBuffs.put(key, Math.max(0, ms));
    }

    public long spellBuffMs(String key) { return spellBuffs.getOrDefault(key, 0L); }

    /** Aktywne wzmocnienia czarow (klucz -> pozostale ms). */
    public java.util.Map<String, Long> spellBuffs() { return java.util.Collections.unmodifiableMap(spellBuffs); }

    /** Tarcza many (utamo vita): pojemnosc i czas; obrazenia najpierw zabieraja mane, do wyczerpania tarczy. */
    public void setMagicShield(int capacity, long ms) {
        magicShield = Math.max(0, capacity);
        magicShieldMs = Math.max(0, ms);
    }

    public int magicShield() { return magicShieldMs > 0 ? magicShield : 0; }
    public long magicShieldMs() { return magicShield > 0 ? magicShieldMs : 0; }
    /** Ile many zabral ostatni cios zamiast zdrowia (tarcza many). */
    public int lastShieldAbsorbed() { return lastShieldAbsorbed; }

    /** Niewidzialnosc (utana vid): potwory nie widza postaci. */
    public void setInvisible(long ms) { invisibleMs = Math.max(0, ms); }
    public boolean isInvisible() { return invisibleMs > 0; }
    public long invisibleMs() { return invisibleMs; }
    /** Atak konczy niewidzialnosc; true, gdy byla. */
    public boolean breakInvisibility() {
        boolean was = invisibleMs > 0;
        invisibleMs = 0;
        return was;
    }

    /** Iluzja stworzenia (utevo res ina): klucz potwora, ktorego wyglad ma postac, albo null. */
    public void setIllusion(String monsterKey, long ms) {
        illusion = monsterKey;
        illusionMs = monsterKey == null ? 0 : Math.max(0, ms);
    }

    public String illusion() { return illusionMs > 0 ? illusion : null; }
    public long illusionMs() { return illusion == null ? 0 : illusionMs; }

    /** Regeneracja z czaru (utura, utura gran): amount HP co intervalMs przez durationMs. */
    public void startRegeneration(int amount, long intervalMs, long durationMs) {
        regenAmount = Math.max(0, amount);
        regenIntervalMs = Math.max(250, intervalMs);
        regenLeftMs = Math.max(0, durationMs);
        regenAccMs = 0;
    }

    public long spellRegenMs() { return regenLeftMs; }

    /** Uplyw czasu wzmocnien z czarow (wolane z tickRegeneration). */
    private void tickSpellEffects(long deltaMs) {
        if (!spellBuffs.isEmpty()) {
            spellBuffs.replaceAll((k, v) -> v - deltaMs);
            spellBuffs.values().removeIf(v -> v <= 0);
        }
        magicShieldMs = Math.max(0, magicShieldMs - deltaMs);
        invisibleMs = Math.max(0, invisibleMs - deltaMs);
        illusionMs = Math.max(0, illusionMs - deltaMs);
        if (regenLeftMs > 0) {
            long step = Math.min(deltaMs, regenLeftMs);
            regenLeftMs -= step;
            regenAccMs += step;
            while (regenAccMs >= regenIntervalMs) {
                regenAccMs -= regenIntervalMs;
                heal(regenAmount);
            }
        }
    }

    /** Smierc i odrodzenie: stany i wzmocnienia z czarow znikaja. */
    private void clearSpellEffects() {
        conditions.clear();
        spellBuffs.clear();
        magicShield = 0;
        magicShieldMs = 0;
        invisibleMs = 0;
        illusionMs = 0;
        regenLeftMs = 0;
    }
    public java.util.Set<String> openedChests() { return openedChests; }
    public java.util.Set<String> completedBossQuests() { return completedBossQuests; }
    public java.util.Set<String> questFlags() { return questFlags; }
    public boolean hasQuestFlag(String flag) { return questFlags.contains(flag); }
    public boolean setQuestFlag(String flag) { return questFlags.add(flag); }

    /** Czy postac spelnia wymaganie przejscia strzezonego (ukonczone zadanie, pokonany boss albo znacznik). */
    public boolean passes(pl.fawizja.pixel.world.QuestGate gate) {
        return gate == null || gate.passes(this);
    }

    /** Gra offline: przesuniete skrzynie i ich zawartosc ({@link pl.fawizja.pixel.world.Crates#encode()}). */
    private String crates = "";

    public String crates() { return crates; }

    public void setCrates(String state) { crates = state == null ? "" : state; }

    /** Gra offline: zapalone i zgaszone pochodnie na murach ({@link pl.fawizja.pixel.world.WallLights#encode()}). */
    private String wallLights = "";

    public String wallLights() { return wallLights; }

    public void setWallLights(String state) { wallLights = state == null ? "" : state; }

    /**
     * Uklad paska akcji ({@link pl.fawizja.pixel.settings.SpellBar#encode()}), "" = pasek automatyczny. Od zapisu v9
     * w postaci (offline w pliku zapisu, online na serwerze - klient wysyla akcje "bar:..." i dostaje go w SELF).
     */
    private String actionBar = "";

    public String actionBar() { return actionBar; }

    /** Ustawia uklad paska; smieci i nieznane wpisy sa odrzucane (wpis od klienta przechodzi przez SpellBar). */
    public void setActionBar(String encoded) {
        if (encoded == null || encoded.isBlank() || encoded.length() > 2000) {
            actionBar = "";
            return;
        }
        pl.fawizja.pixel.settings.SpellBar bar = pl.fawizja.pixel.settings.SpellBar.decode(encoded);
        actionBar = bar.isCustom() ? bar.encode() : "";
    }
    public Skills skills() { return skills; }
    public AutoLootFilter autoLootFilter() { return autoLootFilter; }
    public java.util.Set<Integer> keptItems() { return keptItems; }
    public boolean isKept(int itemId) { return keptItems.contains(itemId); }
    /** Przelacza oznaczenie "Zachowaj"; zwraca nowy stan. */
    public boolean toggleKept(int itemId) {
        if (keptItems.remove(itemId)) return false;
        keptItems.add(itemId);
        return true;
    }
    public String homeTown() { return homeTown; }
    public pl.fawizja.pixel.quest.QuestLog quests() { return quests; }

    /** Zamieszkanie w miescie (u kaplana): tam postac odradza sie po smierci. */
    public void setHomeTown(String town) { this.homeTown = Objects.requireNonNull(town); }
    public boolean isDead() { return health <= 0; }
    public boolean isRunning() { return running; }
    public void setRunning(boolean running) { this.running = running; }
    public boolean isTorchLit() { return torchLit; }
    /** Tryb walki jak w Tibii (przycisk w HUD): ofensywny, zbalansowany (domyslny) albo defensywny. */
    public pl.fawizja.pixel.combat.FightMode fightMode() { return fightMode; }
    public void setFightMode(pl.fawizja.pixel.combat.FightMode m) { fightMode = m == null ? pl.fawizja.pixel.combat.FightMode.BALANCED : m; }

    // ------------------------------------------------------------------ PvP (tylko w pamieci, bez zapisu)
    // Tryb PvP, czaszka i blokada PZ nie sa kodowane w CharacterCodec ani kopiowane w copyFrom: po zalogowaniu
    // tryb jest wylaczony, a czaszke i blokade ustawia serwer (PvpRules.Status) albo komunikat PVP u klienta.
    private boolean pvpMode;
    private pl.fawizja.pixel.pvp.PvpRules.Skull skull = pl.fawizja.pixel.pvp.PvpRules.Skull.NONE;
    private long skullUntilMs;
    private long pzBlockedUntilMs;

    /** Tryb PvP (przelacznik czaszki): wlaczony = mozna atakowac graczy, obszar trafia graczy. */
    public boolean pvpMode() { return pvpMode; }
    public void setPvpMode(boolean on) { pvpMode = on; }

    /** Czaszka postaci (NONE, WHITE, RED, BLACK) wg ostatniej synchronizacji ze stanem PvP. */
    public pl.fawizja.pixel.pvp.PvpRules.Skull skull() { return skull; }
    /** Czaszka w chwili now (po uplywie czasu - NONE, nawet bez synchronizacji). */
    public pl.fawizja.pixel.pvp.PvpRules.Skull skull(long now) {
        return now < skullUntilMs ? skull : pl.fawizja.pixel.pvp.PvpRules.Skull.NONE;
    }
    /** Pozostaly czas czaszki w ms (0 = brak). */
    public long skullMs(long now) { return skull == pl.fawizja.pixel.pvp.PvpRules.Skull.NONE ? 0 : Math.max(0, skullUntilMs - now); }
    public void setSkull(pl.fawizja.pixel.pvp.PvpRules.Skull s, long untilMs) {
        skull = s == null ? pl.fawizja.pixel.pvp.PvpRules.Skull.NONE : s;
        skullUntilMs = skull == pl.fawizja.pixel.pvp.PvpRules.Skull.NONE ? 0 : untilMs;
    }

    /** Pozostaly czas blokady wejscia do strefy ochronnej w ms (0 = mozna wejsc). */
    public long pzBlockedMs(long now) { return Math.max(0, pzBlockedUntilMs - now); }
    public long pzBlockedUntil() { return pzBlockedUntilMs; }
    /** Blokada PZ do czasu until; tylko wydluza (kazda nowa agresja odnawia licznik). */
    public void blockPzUntil(long untilMs) { pzBlockedUntilMs = Math.max(pzBlockedUntilMs, untilMs); }
    /** Ustawia blokade dokladnie (klient - stan z serwera). */
    public void setPzBlockedUntil(long untilMs) { pzBlockedUntilMs = untilMs; }

    private long logoutBlockedUntilMs;

    /**
     * Pozostaly czas blokady wylogowania w ms (0 = mozna wyjsc): po kazdej walce z graczem, takze dla
     * zaatakowanego (blokada PZ ma tylko atakujacy). Zwraca wieksza z obu blokad.
     */
    public long logoutBlockedMs(long now) { return Math.max(pzBlockedMs(now), Math.max(0, logoutBlockedUntilMs - now)); }
    public void setLogoutBlockedUntil(long untilMs) { logoutBlockedUntilMs = untilMs; }

    /** Czy w polu pochodni (na prawo od butow) jest zalozona pochodnia. */
    public boolean hasTorchEquipped(int torchItemId) {
        pl.fawizja.pixel.item.ItemStack t = equipment.get(pl.fawizja.pixel.item.EquipSlot.LIGHT);
        return t != null && t.itemId() == torchItemId;
    }

    /** Zapala lub gasi pochodnie; zapalic mozna tylko zalozona pochodnie (pole obok butow). */
    public boolean setTorchLit(boolean lit, int torchItemId) {
        if (lit && !hasTorchEquipped(torchItemId)) return false;
        torchLit = lit;
        if (lit && torchBurnMs <= 0) torchBurnMs = TORCH_BURN_MS;
        return true;
    }

    public long torchBurnMs() { return torchBurnMs; }

    /** Pozostaly czas palenia zalozonej pochodni (pelny dla nowej), 0 bez pochodni. */
    public long torchRemainingMs(int torchItemId) {
        if (!hasTorchEquipped(torchItemId)) return 0;
        return torchBurnMs > 0 ? torchBurnMs : TORCH_BURN_MS;
    }

    /**
     * Zapalona pochodnia sie wypala: po TORCH_BURN_MS czasu palenia znika z pola pochodni.
     * Zgaszona pochodnia nie traci paliwa. Zdjeta pochodnia gasnie.
     */
    public TorchTick tickTorch(long deltaMs, int torchItemId) {
        if (!torchLit) return TorchTick.NONE;
        if (!hasTorchEquipped(torchItemId)) {
            torchLit = false;
            return TorchTick.NONE;
        }
        if (deltaMs <= 0) return TorchTick.NONE;
        if (torchBurnMs <= 0) torchBurnMs = TORCH_BURN_MS;
        torchBurnMs -= deltaMs;
        if (torchBurnMs > 0) return TorchTick.NONE;
        equipment.setDirect(pl.fawizja.pixel.item.EquipSlot.LIGHT, null);
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
        this.torchLit = torchLit && hasTorchEquipped(torchItemId);
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
        lastShieldAbsorbed = 0;
        if (amount <= 0 || isDead()) return 0;
        if (magicShieldMs > 0 && magicShield > 0 && mana > 0) {
            // tarcza many jak w Tibii 12+: obrazenia zabieraja mane, az wyczerpie sie pojemnosc tarczy
            int absorbed = Math.min(amount, Math.min(magicShield, mana));
            mana -= absorbed;
            magicShield -= absorbed;
            lastShieldAbsorbed = absorbed;
            amount -= absorbed;
            if (amount <= 0) return 0;
        }
        int saved = pl.fawizja.pixel.wheel.WheelEffects.giftOfLife(this, health, amount); // Dar Zycia (Kolo Przeznaczenia)
        if (saved > 0) {
            int dealt = Math.max(0, health - saved);
            health = saved;
            return dealt;
        }
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
    public void tickRegeneration(long deltaMs) { tickRegeneration(deltaMs, false); }

    /** Odpoczynek w strefie ochronnej dodaje tyle krotnosci regeneracji (x3 glodny, x4 najedzony). */
    public static final int REST_BONUS = 2;

    /** resting = postac odpoczywa w strefie ochronnej (bez blokady po walce). */
    public void tickRegeneration(long deltaMs, boolean resting) {
        if (isDead() || deltaMs <= 0) return;
        lightBuffMs = Math.max(0, lightBuffMs - deltaMs);
        hasteMs = Math.max(0, hasteMs - deltaMs);
        slowMs = Math.max(0, slowMs - deltaMs);
        tickSpellEffects(deltaMs);
        wheel.tick(deltaMs);
        if (resting) restedMs = Math.min(MAX_RESTED_MS, restedMs + deltaMs);
        regenAccumulatorMs += deltaMs;
        long tick = Balance.REGEN_TICK_SECONDS * 1000L;
        while (regenAccumulatorMs >= tick) {
            regenAccumulatorMs -= tick;
            int mult = (fedMs > 0 ? 2 : 1) + (resting ? REST_BONUS : 0);
            int hp = Balance.healthRegenPerTick(vocation, level) * mult;
            int mp = Balance.manaRegenPerTick(vocation, level) * mult;
            // promocja: regeneracja o polowe szybsza (co najmniej 1 punkt wiecej)
            heal(promoted ? hp + Math.max(1, hp / 2) : hp);
            restoreMana(promoted ? mp + Math.max(1, mp / 2) : mp);
            fedMs = Math.max(0, fedMs - tick);
        }
    }

    public long fedMs() { return fedMs; }

    /** Wypoczecie: odpoczynek w strefie ochronnej zbiera czas (do 30 min), a kazda walka zuzywa jego czesc. */
    public static final long MAX_RESTED_MS = 30 * 60_000L;
    /** Tyle wypoczecia zuzywa jedno pokonanie potwora (30 min = 90 walk). */
    public static final long RESTED_PER_KILL_MS = 20_000L;
    /** Premia doswiadczenia za potwora pokonanego wypoczetym (procent). */
    public static final int RESTED_EXP_PERCENT = 50;

    public long restedMs() { return restedMs; }

    private final Stamina stamina = new Stamina();

    /** Stamina jak w Tibii (42:00, zuzycie przy polowaniu, odnowienie offline). */
    public Stamina stamina() { return stamina; }

    /** Ile walk z premia zostalo (zaokraglone w gore). */
    public int restedKills() { return (int) ((restedMs + RESTED_PER_KILL_MS - 1) / RESTED_PER_KILL_MS); }

    /** Zuzywa wypoczecie na jedna walke; false, gdy go nie ma. */
    public boolean useRested() {
        if (restedMs <= 0) return false;
        restedMs = Math.max(0, restedMs - RESTED_PER_KILL_MS);
        return true;
    }

    public void restoreRested(long ms) { restedMs = Math.max(0, Math.min(MAX_RESTED_MS, ms)); }

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

    /** Kara za smierc od potwora (doswiadczenie i umiejetnosci, bez przedmiotow); zwraca, ile blogoslawienstw chronilo. */
    public int applyDeathPenalty(int minLevel) { return applyDeathPenalty(minLevel, 0, false, false); }

    public int applyDeathPenalty(int minLevel, double reductionPercent) { return applyDeathPenalty(minLevel, reductionPercent, false, false); }

    /** Jak {@link #applyDeath}, ale bez utraty przedmiotow (testy i stare wywolania); zwraca liczbe blogoslawienstw. */
    public int applyDeathPenalty(int minLevel, double reductionPercent, boolean pvp, boolean skulled) {
        return applyDeathLoss(minLevel, reductionPercent, pvp, skulled, null).blessings();
    }

    /**
     * Pelna kara za smierc jak w Tibii (Canary {@code Player::death}): strata doswiadczenia, umiejetnosci i poziomu magii
     * wedlug {@link Balance#deathLossFraction}, zuzycie blogoslawienstw i utrata przedmiotow do ciala.
     * {@code reductionPercent} to dodatkowo talizman Blogoslawienstwo (Canary); {@code pvp} - smierc z reki gracza;
     * {@code skulled} - ofiara ma czerwona albo czarna czaszke (traci caly plecak i ekwipunek, Zrzadzenie losu nie chroni).
     * Zuzycie: Blogoslawienstwo Poszukiwacza nic nie zuzywa; smierc z reki gracza bez czaszki zuzywa tylko Zrzadzenie losu;
     * kazda inna smierc zabiera piec zwyklych (Zrzadzenie losu zostaje) i Amulet Straty. Wypadle stosy sa w wyniku
     * (juz zdjete z postaci) - wywolujacy kladzie je do ciala.
     */
    public DeathLoss applyDeath(int minLevel, double reductionPercent, boolean pvp, boolean skulled, java.util.Random rng) {
        return applyDeathLoss(minLevel, reductionPercent, pvp, skulled, rng == null ? new java.util.Random() : rng);
    }

    private DeathLoss applyDeathLoss(int minLevel, double reductionPercent, boolean pvp, boolean skulled, java.util.Random rng) {
        int held = blessingCount();
        stats.recordDeath();
        double fraction = Balance.deathLossFraction(level, experience, held, promoted);
        if (reductionPercent > 0) fraction *= 1.0 - Math.min(100, reductionPercent) / 100.0;
        int oldLevel = level;
        int skillLevels = skills.loseAll(fraction);
        long before = experience;
        experience = Balance.experienceAfterDeath(experience, minLevel, fraction);
        level = Balance.levelForExperience(experience);
        boolean twistUsed = false;
        if (!adventurerBlessing()) {
            if (pvp && !skulled && boughtBlessing(Blessing.TWIST)) {
                blessings &= ~Blessing.TWIST.bit();
                twistUsed = true;
            } else {
                blessings &= Blessing.TWIST.bit();
            }
        }
        boolean aol = false, backpackDropped = false;
        java.util.List<ItemStack> dropped = new java.util.ArrayList<>();
        if (rng != null) {
            ItemStack amulet = equipment.get(pl.fawizja.pixel.item.EquipSlot.AMULET);
            aol = amulet != null && DeathLoss.AMULET_OF_LOSS.equals(backpack.registry().get(amulet.itemId()).key());
            boolean consumed = !adventurerBlessing() && !twistUsed;   // zuzycie blogoslawienstw zabiera tez Amulet Straty
            if (skulled) {
                // czerwona i czarna czaszka: przepada wszystko (takze Amulet Straty)
                backpackDropped = dropBackpack(dropped);
                dropWorn(dropped, 1.0, null);
                aol = false;
            } else if (aol) {
                // amulet chroni caly ekwipunek (plecak tez); ulega zniszczeniu razem ze zwyklymi blogoslawienstwami
                if (consumed) equipment.setDirect(pl.fawizja.pixel.item.EquipSlot.AMULET, null);
                aol = consumed;
            } else {
                if (rng.nextDouble() < DeathLoss.backpackChance(held)) backpackDropped = dropBackpack(dropped);
                dropWorn(dropped, DeathLoss.itemChance(held), rng);
            }
        }
        refreshWheel(); // utrata poziomu: mniej punktow promocji
        return new DeathLoss(held, fraction, before - experience, oldLevel - level, skillLevels, pvp, skulled, twistUsed,
                aol, backpackDropped, java.util.Collections.unmodifiableList(dropped));
    }

    private boolean dropBackpack(java.util.List<ItemStack> out) {
        boolean any = false;
        for (int i = 0; i < backpack.size(); i++) {
            ItemStack st = backpack.get(i);
            if (st == null) continue;
            ItemStack taken = backpack.takeFromSlot(i, st.count());
            if (taken != null) {
                out.add(taken);
                any = true;
            }
        }
        return any;
    }

    /** Zalozone przedmioty: kazdy wypada z szansa chance (rng == null: wszystkie). */
    private void dropWorn(java.util.List<ItemStack> out, double chance, java.util.Random rng) {
        for (pl.fawizja.pixel.item.EquipSlot slot : pl.fawizja.pixel.item.EquipSlot.values()) {
            ItemStack worn = equipment.get(slot);
            if (worn == null) continue;
            if (rng != null && rng.nextDouble() >= chance) continue;
            equipment.setDirect(slot, null);
            out.add(worn);
        }
    }

    /** Odrodzenie z pelnym zdrowiem i mana. */
    public void revive(Position at) {
        position = Objects.requireNonNull(at);
        health = maxHealth();
        mana = maxMana();
        regenAccumulatorMs = 0;
        clearSpellEffects();
        harmony.empty(); // jak w Canary: smierc zeruje harmonie mnicha (cnota zostaje)
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
