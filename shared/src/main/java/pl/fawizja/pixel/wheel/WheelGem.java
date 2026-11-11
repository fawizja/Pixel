package pl.fawizja.pixel.wheel;

/**
 * Odkryty klejnot Pracowni klejnotow (Canary PlayerWheelGem): numer, jakosc, domena (powinowactwo), mody
 * i blokada. Mod 2 tylko od zwyklego, mod najwyzszy tylko w wiekszym. Domena zmienia sie tylko przez
 * {@link GemAtelier#switchDomain}; blokada chroni przed rozbiciem i zmiana domeny.
 */
public final class WheelGem {
    public final int id;
    public final GemQuality quality;
    public final BasicGemMod basic1;
    public final BasicGemMod basic2;
    public final SupremeGemMod supreme;
    WheelDomain domain;
    boolean locked;

    WheelGem(int id, GemQuality quality, WheelDomain domain, BasicGemMod basic1, BasicGemMod basic2, SupremeGemMod supreme, boolean locked) {
        this.id = id;
        this.quality = quality;
        this.domain = domain;
        this.basic1 = basic1;
        this.basic2 = quality.basicMods() > 1 ? basic2 : null;
        this.supreme = quality.hasSupreme() ? supreme : null;
        this.locked = locked;
    }

    public WheelDomain domain() { return domain; }
    public boolean locked() { return locked; }

    /** Tekst zapisu: "12,R,G,FIRE_RES,HEALTH,-,1". */
    String encode() {
        return id + "," + quality.code + "," + domain.code + "," + basic1.name() + "," + (basic2 == null ? "-" : basic2.name())
                + "," + (supreme == null ? "-" : supreme.name()) + "," + (locked ? 1 : 0);
    }

    /** Odczyt; null przy blednym wpisie. */
    static WheelGem decode(String s) {
        String[] f = s.split(",");
        if (f.length < 7) return null;
        try {
            int id = Integer.parseInt(f[0]);
            GemQuality q = f[1].isEmpty() ? null : GemQuality.ofCode(f[1].charAt(0));
            WheelDomain d = f[2].isEmpty() ? null : WheelDomain.ofCode(f[2].charAt(0));
            BasicGemMod b1 = BasicGemMod.parse(f[3]);
            BasicGemMod b2 = "-".equals(f[4]) ? null : BasicGemMod.parse(f[4]);
            SupremeGemMod sp = "-".equals(f[5]) ? null : SupremeGemMod.parse(f[5]);
            if (id <= 0 || q == null || d == null || b1 == null) return null;
            if (q.basicMods() > 1 && b2 == null) return null;
            if (q.hasSupreme() && sp == null) return null;
            return new WheelGem(id, q, d, b1, b2, sp, "1".equals(f[6]));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    WheelGem copy() { return new WheelGem(id, quality, domain, basic1, basic2, supreme, locked); }

    /** Nazwa do list: "Większy klejnot (fioletowy) #12". */
    public String title() {
        return quality.title() + " (" + adjective(domain) + ") #" + id;
    }

    /** Przymiotnik domeny w rodzaju meskim: "zielony", "czerwony". */
    public static String adjective(WheelDomain d) {
        switch (d) {
            case GREEN: return "zielony";
            case RED: return "czerwony";
            case PURPLE: return "fioletowy";
            default: return "niebieski";
        }
    }
}
