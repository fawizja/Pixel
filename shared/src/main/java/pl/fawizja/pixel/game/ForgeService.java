package pl.fawizja.pixel.game;

import java.util.Random;
import pl.fawizja.pixel.character.PlayerCharacter;
import pl.fawizja.pixel.economy.Money;
import pl.fawizja.pixel.inventory.Container;
import pl.fawizja.pixel.item.EquipSlot;
import pl.fawizja.pixel.item.Forge;
import pl.fawizja.pixel.item.ForgeEffects;
import pl.fawizja.pixel.item.Imbuement;
import pl.fawizja.pixel.item.ItemRegistry;
import pl.fawizja.pixel.item.ItemStack;
import pl.fawizja.pixel.item.ItemType;

/**
 * Kuznia i nasycenia u kowala ({@link Forge}, {@link Imbuement}): fuzja, transfer, wymiana pylu i odlamkow, limit pylu,
 * nasycanie zalozonego sprzetu oraz efekty w czasie gry (Rozped, Transcendencja, uplyw czasu nasycen). Kazda operacja
 * jest atomowa (kopia plecaka, potem zatwierdzenie) i liczona tam, gdzie gra jest zrodlem prawdy (serwer albo offline).
 * Komunikaty zaczynaja sie od {@link #PREFIX} - okno kowala pokazuje ostatni wynik.
 */
public final class ForgeService {

    public static final String PREFIX = "Kuźnia: ";

    private final GameSession session;
    private final Random rng;
    private long effectsCheckedMs = Long.MIN_VALUE;

    ForgeService(GameSession session, Random rng) {
        this.session = session;
        this.rng = rng;
    }

    private PlayerCharacter pc() { return session.character(); }
    private ItemRegistry items() { return session.items(); }
    private EventLog log() { return session.log(); }

    private boolean fail(long now, String msg) {
        log().add(now, EventLog.Kind.WARNING, PREFIX + msg);
        return false;
    }

    private int coreId() { return items().byKey(Forge.CORE_KEY).id(); }
    private int sliverId() { return items().byKey(Forge.SLIVER_KEY).id(); }

    /** Monety z plecaka i konto razem. */
    public long funds() { return Money.totalValue(pc().backpack()) + pc().bank().balance(); }

    /**
     * Platnosc jak w Tibii: najpierw monety z (kopii) plecaka, reszta z konta. Zwraca kwote do pobrania z konta
     * (pobiera wywolujacy po zatwierdzeniu kopii) albo -1, gdy brak pieniedzy.
     */
    private long payFrom(Container tx, long price) {
        if (price <= 0) return 0;
        long cash = Money.totalValue(tx);
        long fromPack = Math.min(cash, price);
        if (fromPack > 0 && !Money.pay(tx, fromPack).ok()) fromPack = 0;
        long fromBank = price - fromPack;
        return fromBank > pc().bank().balance() ? -1 : fromBank;
    }

    private static String gp(long v) { return String.format("%,d gp", v).replace(',', ' '); }

    // ------------------------------------------------------------------ fuzja i transfer

    /** Fuzja dwoch takich samych przedmiotow z plecaka (pola slotA i slotB). */
    public boolean fuse(int slotA, int slotB, boolean coreChance, boolean coreProtect, long now) {
        PlayerCharacter pc = pc();
        Container bp = pc.backpack();
        if (slotA == slotB || slotA < 0 || slotB < 0 || slotA >= bp.size() || slotB >= bp.size()) return fail(now, "wybierz dwa przedmioty z plecaka.");
        ItemStack a = bp.get(slotA);
        ItemStack b = bp.get(slotB);
        if (a == null || b == null || a.itemId() != b.itemId() || a.tier() != b.tier()) {
            return fail(now, "do fuzji potrzeba dwóch takich samych przedmiotów o tym samym tierze.");
        }
        ItemType t = items().get(a.itemId());
        int max = Forge.maxTier(t);
        if (max == 0) return fail(now, t.name() + " nie ma klasy kuźni (broń, hełm, zbroja, spodnie i buty od niepospolitych).");
        if (a.tier() >= max) return fail(now, Forge.name(t, a) + " ma już najwyższy tier klasy " + Forge.classOf(t) + ".");
        int cores = (coreChance ? 1 : 0) + (coreProtect ? 1 : 0);
        if (bp.countPlain(coreId()) < cores) return fail(now, "brakuje rdzeni (" + cores + ").");
        if (pc.forgeDust() < Forge.FUSION_DUST) return fail(now, "fuzja kosztuje " + Forge.FUSION_DUST + " pyłu (masz " + pc.forgeDust() + ").");
        long gold = Forge.fusionGold(t, a.tier() + 1);
        if (funds() < gold) return fail(now, "fuzja kosztuje " + gp(gold) + " (plecak i konto).");
        // pierwszy (zostaje) to ten z wyzszym ulepszeniem albo nasyceniami; drugi moze przepasc
        boolean swap = b.upgrade() > a.upgrade() || (b.upgrade() == a.upgrade() && b.imbuements().size() > a.imbuements().size());
        int firstSlot = swap ? slotB : slotA;
        int secondSlot = swap ? slotA : slotB;
        ItemStack first = bp.get(firstSlot);
        ItemStack second = bp.get(secondSlot);
        Forge.Fusion f = Forge.fuse(a.tier(), max, coreChance, coreProtect, rng);
        Container tx = bp.copy();
        tx.takeFromSlot(firstSlot, 1);
        tx.takeFromSlot(secondSlot, 1);
        tx.putInSlot(firstSlot, first.withTier(f.firstTier()));
        if (f.secondTier() >= 0) tx.putInSlot(secondSlot, second.withTier(f.secondTier()));
        if (f.payCores() && cores > 0 && !tx.remove(coreId(), cores).ok()) return fail(now, "brakuje rdzeni.");
        long fromBank = f.payGold() ? payFrom(tx, gold) : 0;
        if (fromBank < 0) return fail(now, "za mało złota.");
        if (f.payDust() && !pc.spendDust(Forge.FUSION_DUST)) return fail(now, "za mało pyłu.");
        bp.commitFrom(tx);
        if (fromBank > 0) pc.bank().debit(fromBank);
        String paid = (f.payDust() ? Forge.FUSION_DUST + " pyłu" : "bez pyłu") + ", " + (f.payGold() ? gp(gold) : "bez złota")
                + (cores > 0 ? ", " + (f.payCores() ? cores + " rdz." : "rdzenie zostały") : "");
        if (f.success()) {
            String bonus = Forge.bonusText(f.bonus());
            log().add(now, EventLog.Kind.SYSTEM, PREFIX + "fuzja udana! " + Forge.name(t, first.withTier(f.firstTier()))
                    + (bonus.isEmpty() ? "" : "; premia: " + bonus) + " (" + paid + ").");
        } else {
            String second2 = f.secondTier() < 0 ? "drugi przedmiot przepadł" : f.secondTier() == a.tier()
                    ? "drugi przedmiot ocalał bez zmian" : "drugi przedmiot stracił tier (T" + f.secondTier() + ")";
            log().add(now, EventLog.Kind.WARNING, PREFIX + "fuzja nieudana - " + second2 + " (" + paid + ").");
        }
        return f.success();
    }

    /** Transfer tieru z dawcy (tier >= 2) na przedmiot tej samej klasy i rodzaju z tierem 0; dawca przepada. */
    public boolean transfer(int donorSlot, int receiverSlot, long now) {
        PlayerCharacter pc = pc();
        Container bp = pc.backpack();
        if (donorSlot == receiverSlot || donorSlot < 0 || receiverSlot < 0 || donorSlot >= bp.size() || receiverSlot >= bp.size()) {
            return fail(now, "wybierz dawcę i biorcę z plecaka.");
        }
        ItemStack d = bp.get(donorSlot);
        ItemStack r = bp.get(receiverSlot);
        if (d == null || r == null) return fail(now, "wybierz dawcę i biorcę z plecaka.");
        ItemType dt = items().get(d.itemId());
        ItemType rt = items().get(r.itemId());
        if (d.tier() < 2) return fail(now, "dawca musi mieć co najmniej tier 2.");
        if (r.tier() != 0) return fail(now, "biorca musi mieć tier 0.");
        if (Forge.classOf(dt) == 0 || Forge.classOf(dt) != Forge.classOf(rt) || dt.slot() != rt.slot()) {
            return fail(now, "transfer tylko na przedmiot tej samej klasy i rodzaju.");
        }
        int result = d.tier() - 1;
        int cores = Forge.transferCores(dt, result);
        long gold = Forge.transferGold(dt, d.tier());
        if (bp.countPlain(coreId()) < cores) return fail(now, "transfer na T" + result + " wymaga " + cores + " rdz.");
        if (pc.forgeDust() < Forge.TRANSFER_DUST) return fail(now, "transfer kosztuje " + Forge.TRANSFER_DUST + " pyłu.");
        if (funds() < gold) return fail(now, "transfer kosztuje " + gp(gold) + " (plecak i konto).");
        Container tx = bp.copy();
        tx.takeFromSlot(donorSlot, 1);
        tx.takeFromSlot(receiverSlot, 1);
        tx.putInSlot(receiverSlot, r.withTier(result));
        if (cores > 0 && !tx.remove(coreId(), cores).ok()) return fail(now, "brakuje rdzeni.");
        long fromBank = payFrom(tx, gold);
        if (fromBank < 0) return fail(now, "za mało złota.");
        if (!pc.spendDust(Forge.TRANSFER_DUST)) return fail(now, "za mało pyłu.");
        bp.commitFrom(tx);
        if (fromBank > 0) pc.bank().debit(fromBank);
        log().add(now, EventLog.Kind.SYSTEM, PREFIX + "transfer udany: " + Forge.name(rt, r.withTier(result)) + "; "
                + Forge.name(dt, d) + " przepada (" + Forge.TRANSFER_DUST + " pyłu, " + cores + " rdz., " + gp(gold) + ").");
        return true;
    }

    // ------------------------------------------------------------------ wymiana i limit

    /** 60 pylu na 3 odlamki ("slivers") albo 50 odlamkow na rdzen ("core"). */
    public boolean convert(String kind, long now) {
        PlayerCharacter pc = pc();
        Container bp = pc.backpack();
        if ("slivers".equals(kind)) {
            if (pc.forgeDust() < Forge.SLIVER_DUST) return fail(now, "wymiana kosztuje " + Forge.SLIVER_DUST + " pyłu (masz " + pc.forgeDust() + ").");
            ItemStack add = new ItemStack(sliverId(), Forge.SLIVERS_PER_CONVERSION);
            if (!bp.canAdd(add.itemId(), add.count())) return fail(now, "brak miejsca w plecaku.");
            bp.add(add);
            pc.spendDust(Forge.SLIVER_DUST);
            log().add(now, EventLog.Kind.SYSTEM, PREFIX + Forge.SLIVER_DUST + " pyłu na " + Forge.SLIVERS_PER_CONVERSION
                    + " odłamki (pył " + pc.forgeDust() + "/" + pc.dustLimit() + ").");
            return true;
        }
        if ("core".equals(kind)) {
            if (bp.countPlain(sliverId()) < Forge.CORE_SLIVERS) return fail(now, "rdzeń kosztuje " + Forge.CORE_SLIVERS + " odłamków.");
            Container tx = bp.copy();
            tx.remove(sliverId(), Forge.CORE_SLIVERS);
            if (!tx.add(ItemStack.of(coreId())).ok()) return fail(now, "brak miejsca w plecaku.");
            bp.commitFrom(tx);
            log().add(now, EventLog.Kind.SYSTEM, PREFIX + Forge.CORE_SLIVERS + " odłamków na wzniosły rdzeń.");
            return true;
        }
        return false;
    }

    /** Podnosi limit pylu o 1 za (limit - 75) pylu, do 225. */
    public boolean raiseLimit(long now) {
        PlayerCharacter pc = pc();
        int cost = Forge.raiseLimitCost(pc.dustLimit());
        if (cost == 0) return fail(now, "limit pyłu jest już najwyższy (" + Forge.DUST_MAX_LIMIT + ").");
        if (!pc.spendDust(cost)) return fail(now, "podniesienie limitu kosztuje " + cost + " pyłu (masz " + pc.forgeDust() + ").");
        pc.raiseDustLimit();
        log().add(now, EventLog.Kind.SYSTEM, PREFIX + "limit pyłu " + pc.dustLimit() + " (" + cost + " pyłu).");
        return true;
    }

    // ------------------------------------------------------------------ nasycenia

    /** Nasycenie zalozonego przedmiotu: skladniki z plecaka i zloto (plecak, potem konto). */
    public boolean imbue(EquipSlot slot, Imbuement kind, int level, long now) {
        PlayerCharacter pc = pc();
        ItemStack worn = pc.equipment().get(slot);
        if (worn == null) return fail(now, "załóż przedmiot, który chcesz nasycić.");
        ItemType t = items().get(worn.itemId());
        int slots = Imbuement.slots(t);
        if (slots == 0) return fail(now, t.name() + " nie ma miejsc na nasycenia.");
        if (!kind.fits(t)) return fail(now, kind.label() + " nie pasuje do: " + t.name() + ".");
        if (worn.imbuements().size() >= slots) return fail(now, t.name() + ": wszystkie miejsca zajęte (" + slots + ").");
        if (worn.imbuements().conflicts(kind)) return fail(now, t.name() + " ma już nasycenie tego rodzaju.");
        if (level < 1 || level > 3) return false;
        Container tx = pc.backpack().copy();
        for (Imbuement.Ingredient in : kind.ingredients(level)) {
            ItemType it = items().byKeyOrNull(in.itemKey());
            if (it == null || tx.countPlain(it.id()) < in.count()) {
                return fail(now, "brakuje składników: " + in.count() + "x " + (it == null ? in.itemKey() : it.name()) + ".");
            }
            tx.remove(it.id(), in.count());
        }
        long gold = Imbuement.GOLD[level];
        if (Money.totalValue(tx) + pc.bank().balance() < gold) return fail(now, "nasycenie kosztuje " + gp(gold) + ".");
        long fromBank = payFrom(tx, gold);
        if (fromBank < 0) return fail(now, "za mało złota.");
        pc.backpack().commitFrom(tx);
        if (fromBank > 0) pc.bank().debit(fromBank);
        pc.equipment().setDirect(slot, worn.withImbuements(worn.imbuements().with(kind, level)));
        log().add(now, EventLog.Kind.SYSTEM, PREFIX + t.name() + ": " + kind.label() + " (" + Imbuement.LEVELS[level] + ") - "
                + kind.effect(level) + " na 20 h walki (" + gp(gold) + ").");
        return true;
    }

    /** Usuwa nasycenie z zalozonego przedmiotu (index na liscie nasycen). */
    public boolean clearImbuement(EquipSlot slot, int index, long now) {
        PlayerCharacter pc = pc();
        ItemStack worn = pc.equipment().get(slot);
        if (worn == null || index < 0 || index >= worn.imbuements().size()) return fail(now, "nie ma takiego nasycenia.");
        Container tx = pc.backpack().copy();
        long fromBank = payFrom(tx, Imbuement.CLEAR_COST);
        if (fromBank < 0) return fail(now, "usunięcie nasycenia kosztuje " + gp(Imbuement.CLEAR_COST) + ".");
        pc.backpack().commitFrom(tx);
        if (fromBank > 0) pc.bank().debit(fromBank);
        Imbuement k = worn.imbuements().slots().get(index).kind();
        pc.equipment().setDirect(slot, worn.withImbuements(worn.imbuements().without(index)));
        log().add(now, EventLog.Kind.INFO, PREFIX + "usunięto " + k.label() + " (" + gp(Imbuement.CLEAR_COST) + ").");
        return true;
    }

    // ------------------------------------------------------------------ efekty w czasie gry

    /**
     * Krok gry: awatar mija, nasycenia zalozonego sprzetu traca czas tylko w walce poza strefa ochronna; co 2 s
     * Rozped (w walce: -2 s odnowien czarow) i Transcendencja (po agresywnej akcji w ostatnich 2 s: awatar na 7 s).
     */
    void tick(long now, long dt, boolean protectionZone) {
        PlayerCharacter pc = pc();
        pc.tickAvatar(dt);
        if (protectionZone || pc.isDead()) return;
        boolean battle = session.combat().inBattle(now);
        if (battle) {
            for (String gone : pc.equipment().tickImbuements(dt)) {
                log().add(now, EventLog.Kind.WARNING, "Nasycenie wygasło: " + gone + ".");
            }
        }
        if (effectsCheckedMs != Long.MIN_VALUE && now >= effectsCheckedMs && now - effectsCheckedMs < Forge.EFFECT_CHECK_MS) return;
        effectsCheckedMs = now;
        if (battle && ForgeEffects.roll(ForgeEffects.momentumChance(pc), rng)) {
            session.spells().cooldowns().cutAll(Forge.MOMENTUM_CUT_MS, now);
            log().add(now, EventLog.Kind.INFO, "Rozpęd! Odnowienia czarów krótsze o 2 s.");
        }
        if (!pc.avatarActive() && session.combat().aggressiveWithin(now, Forge.EFFECT_CHECK_MS)
                && ForgeEffects.roll(ForgeEffects.transcendenceChance(pc), rng)) {
            pc.startAvatar(Forge.AVATAR_MS);
            log().add(now, EventLog.Kind.SYSTEM, "Transcendencja! Awatar na 7 s: 100% krytyka, -15% obrażeń.");
        }
    }

    /** Pary do fuzji w plecaku: pola dwoch takich samych przedmiotow o tym samym tierze ponizej limitu klasy. */
    public java.util.List<int[]> fusionPairs() {
        java.util.List<int[]> out = new java.util.ArrayList<>();
        Container bp = pc().backpack();
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (int i = 0; i < bp.size(); i++) {
            ItemStack a = bp.get(i);
            if (a == null) continue;
            ItemType t = items().get(a.itemId());
            if (Forge.maxTier(t) <= a.tier() || !seen.add(a.itemId() + "*" + a.tier())) continue;
            for (int j = i + 1; j < bp.size(); j++) {
                ItemStack b = bp.get(j);
                if (b != null && b.itemId() == a.itemId() && b.tier() == a.tier()) {
                    out.add(new int[] {i, j});
                    break;
                }
            }
        }
        return out;
    }
}
