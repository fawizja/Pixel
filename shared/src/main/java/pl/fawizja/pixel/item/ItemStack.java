package pl.fawizja.pixel.item;

/**
 * Niezmienny stos przedmiotow. Zmiana ilosci zawsze tworzy nowy obiekt.
 * upgrade = poziom ulepszenia u kowala (0..{@link Upgrade#MAX}); tier = poziom z Kuzni ({@link Forge}, 0..{@link Forge#MAX_TIER});
 * imbuements = nasycenia u kowala ({@link Imbuement}). Ulepszenie, tier i nasycenia maja tylko przedmioty pojedyncze.
 */
public record ItemStack(int itemId, int count, int upgrade, int tier, Imbuements imbuements,
                        pl.fawizja.pixel.inventory.Container contents) {
    public ItemStack {
        if (itemId <= 0) throw new IllegalArgumentException("itemId must be > 0");
        if (count <= 0) throw new IllegalArgumentException("count must be > 0");
        if (upgrade < 0 || upgrade > Upgrade.MAX) throw new IllegalArgumentException("upgrade 0.." + Upgrade.MAX);
        if (tier < 0 || tier > Forge.MAX_TIER) throw new IllegalArgumentException("tier 0.." + Forge.MAX_TIER);
        if (imbuements == null) imbuements = Imbuements.NONE;
        if (contents != null && count != 1) throw new IllegalArgumentException("a container item with contents is single");
        if ((upgrade > 0 || tier > 0 || !imbuements.isEmpty()) && count != 1) {
            throw new IllegalArgumentException("upgraded, tiered or imbued items are single");
        }
    }

    /** Bez zawartosci (zwykly przedmiot albo pusty plecak, ktorego pojemnik powstaje przy pierwszym otwarciu). */
    public ItemStack(int itemId, int count, int upgrade, int tier, Imbuements imbuements) {
        this(itemId, count, upgrade, tier, imbuements, null);
    }

    public ItemStack(int itemId, int count, int upgrade) { this(itemId, count, upgrade, 0, Imbuements.NONE); }

    public ItemStack(int itemId, int count) { this(itemId, count, 0); }

    public static ItemStack of(int itemId) { return new ItemStack(itemId, 1); }

    public ItemStack withCount(int newCount) { return new ItemStack(itemId, newCount, upgrade, tier, imbuements, contents); }

    /** Ten sam przedmiot z (innym) pojemnikiem zawartosci - plecak w plecaku. */
    public ItemStack withContents(pl.fawizja.pixel.inventory.Container c) { return new ItemStack(itemId, count, upgrade, tier, imbuements, c); }

    public ItemStack withUpgrade(int level) { return new ItemStack(itemId, count, level, tier, imbuements, contents); }

    public ItemStack withTier(int t) { return new ItemStack(itemId, count, upgrade, t, imbuements, contents); }

    public ItemStack withImbuements(Imbuements im) { return new ItemStack(itemId, count, upgrade, tier, im, contents); }

    /**
     * Zwykla sztuka: bez ulepszenia, tieru i nasycen (tylko takie mozna sprzedac kupcowi, oddac w handlu, wyslac
     * albo laczyc w stosy).
     */
    public boolean isPlain() { return upgrade == 0 && tier == 0 && imbuements.isEmpty() && (contents == null || contents.isEmptyDeep()); }

    /**
     * Zapis tekstowy stosu (zapis postaci v8, skrzynie): "idxilosc", opcjonalnie "+ulepszenie", "*tier" i
     * "#nasycenia" ({@link Imbuements#encode()}), np. "412x1+3*2#VAMPIRISM3@72000". Nie uzywa znakow '/', ':', ';', ','.
     */
    public String encode() {
        StringBuilder b = new StringBuilder().append(itemId).append('x').append(count);
        if (upgrade > 0) b.append('+').append(upgrade);
        if (tier > 0) b.append('*').append(tier);
        if (!imbuements.isEmpty()) b.append('#').append(imbuements.encode());
        if (contents != null && !contents.isEmptyDeep()) b.append(contents.encodeContents());
        return b.toString();
    }

    /** Odczyt {@link #encode()} (takze starszego "idxilosc+ulepszenie" bez tieru i nasycen). */
    public static ItemStack parse(String v) {
        return parse(v, null);
    }

    /**
     * Jak {@link #parse(String)}, ale z zawartoscia plecaka zapisana jako "{rozmiar|pole=stos|...}" (v10,
     * {@link pl.fawizja.pixel.inventory.Container#encodeContents()}); wymaga rejestru do zbudowania pojemnika.
     */
    public static ItemStack parse(String v, ItemRegistry registry) {
        int brace = v.indexOf('{');
        if (brace >= 0) {
            if (registry == null || !v.endsWith("}")) throw new IllegalArgumentException("bad container contents " + v);
            ItemStack base = parse(v.substring(0, brace), null);
            return base.withContents(pl.fawizja.pixel.inventory.Container.decodeContents(registry, v.substring(brace + 1, v.length() - 1)));
        }
        int hash = v.indexOf('#');
        Imbuements im = hash < 0 ? Imbuements.NONE : Imbuements.parse(v.substring(hash + 1));
        String rest = hash < 0 ? v : v.substring(0, hash);
        int star = rest.indexOf('*');
        int tier = star < 0 ? 0 : Integer.parseInt(rest.substring(star + 1).trim());
        if (star >= 0) rest = rest.substring(0, star);
        int plus = rest.indexOf('+');
        int upgrade = plus < 0 ? 0 : Integer.parseInt(rest.substring(plus + 1).trim());
        String[] f = (plus < 0 ? rest : rest.substring(0, plus)).split("x");
        return new ItemStack(Integer.parseInt(f[0].trim()), Integer.parseInt(f[1].trim()), upgrade, tier, im);
    }
}
