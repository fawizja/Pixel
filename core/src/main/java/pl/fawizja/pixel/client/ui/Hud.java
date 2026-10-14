package pl.fawizja.pixel.client.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import java.util.ArrayList;
import java.util.List;
import pl.fawizja.pixel.character.PlayerCharacter;
import pl.fawizja.pixel.client.render.TileArt;
import pl.fawizja.pixel.game.EventLog;
import pl.fawizja.pixel.game.GameSession;
import pl.fawizja.pixel.monster.Monster;
import pl.fawizja.pixel.character.Skill;
import pl.fawizja.pixel.character.Skills;
import pl.fawizja.pixel.magic.CastResult;
import pl.fawizja.pixel.magic.CraftingSystem;
import pl.fawizja.pixel.magic.Recipe;
import pl.fawizja.pixel.magic.SpellKind;
import pl.fawizja.pixel.magic.SpellType;
import pl.fawizja.pixel.item.EquipSlot;
import pl.fawizja.pixel.item.ItemStack;
import pl.fawizja.pixel.item.ItemType;
import pl.fawizja.pixel.time.WorldClock;

/**
 * Interfejs mobilny rysowany w jednostkach niezaleznych od gestosci ekranu
 * (ScreenViewport ze skala zalezna od DPI), dzieki czemu przyciski i joystick
 * maja podobny fizyczny rozmiar na roznych telefonach.
 */
public final class Hud {

    public interface Actions {
        void onButton(String id);
    }

    private final ScreenViewport viewport = new ScreenViewport();
    private final TileArt art;
    private final BitmapFont font;
    private final GlyphLayout layout = new GlyphLayout();
    private final VirtualJoystick joystick = new VirtualJoystick();
    private final List<HudButton> buttons = new ArrayList<>();
    private final HudButton use = new HudButton("use", "UZYJ");
    private final HudButton run = new HudButton("run", "BIEG");
    private final HudButton torch = new HudButton("torch", "POCH");
    private final HudButton bag = new HudButton("bag", "PLECAK");
    private final HudButton pick = new HudButton("pick", "PODNIES");
    private final HudButton diag = new HudButton("diag", "i");
    private final HudButton target = new HudButton("target", "CEL");
    private final HudButton defensive = new HudButton("defensive", "TARCZA");
    private final HudButton book = new HudButton("book", "KSIEGA");
    private final HudButton fish = new HudButton("fish", "LOW");
    private final HudButton talk = new HudButton("talk", "ROZMOWA");
    private final HudButton chat = new HudButton("chat", "CZAT");
    private boolean chatEnabled;
    /** Okno menu pokazuje rozmowe z NPC zamiast ksiegi. */
    private boolean npcMode;
    private pl.fawizja.pixel.npc.Npc npc;
    private int npcPage;
    private int npcQty = 1;
    private static final int[] QUANTITIES = {1, 5, 25, 100};
    private boolean charOpen;
    private final HudButton potionHp = new HudButton("potion_hp", "HP");
    private final HudButton potionMp = new HudButton("potion_mp", "MP");
    private final HudButton[] spellButtons = new HudButton[4];
    private final String[] barSpells = new String[4];

    private boolean menuOpen;
    private float menuX, menuY, menuW, menuH, rowH;
    private final List<MenuRow> menuRows = new ArrayList<>();
    private final HudButton menuClose = new HudButton("menu_close", "X");

    /** Wiersz ksiegi: czar albo receptura. */
    private static final class MenuRow {
        final String action;
        final String label;
        final String detail;
        final boolean enabled;
        float x, y, w, h;

        MenuRow(String action, String label, String detail, boolean enabled) {
            this.action = action;
            this.label = label;
            this.detail = detail;
            this.enabled = enabled;
        }
    }
    private final Vector2 tmp = new Vector2();
    private float uiScale = 1f;
    private float w;
    private float h;

    private boolean bagOpen;
    private int selectedSlot = -1;
    private float bagX, bagY, slotSize;
    private final HudButton bagEquip = new HudButton("bag_equip", "ZALOZ");
    private final HudButton bagDrop = new HudButton("bag_drop", "WYRZUC");
    private final HudButton bagUse = new HudButton("bag_use", "UZYJ");
    private final HudButton bagClose = new HudButton("bag_close", "X");
    private boolean diagnosticsOpen;

    public Hud(TileArt art, BitmapFont font) {
        this.art = art;
        this.font = font;
        buttons.add(use);
        buttons.add(run);
        buttons.add(torch);
        buttons.add(bag);
        buttons.add(pick);
        buttons.add(diag);
        buttons.add(target);
        buttons.add(defensive);
        buttons.add(book);
        buttons.add(fish);
        buttons.add(talk);
        buttons.add(chat);
        buttons.add(potionHp);
        buttons.add(potionMp);
        for (int i = 0; i < spellButtons.length; i++) {
            spellButtons[i] = new HudButton("spell:" + i, "");
            buttons.add(spellButtons[i]);
        }
    }

    /** Przycisk czatu (tylko w grze online). */
    public void setChatEnabled(boolean enabled) { chatEnabled = enabled; }

    public void resize(int width, int height) {
        float density = Gdx.graphics.getDensity();
        uiScale = Math.max(1f, Math.min(3.5f, density));
        viewport.setUnitsPerPixel(1f / uiScale);
        viewport.update(width, height, true);
        w = viewport.getWorldWidth();
        h = viewport.getWorldHeight();
        float margin = 16;
        float jr = Math.min(70, Math.min(w, h) * 0.16f);
        joystick.layout(margin + jr * 1.3f, margin + jr * 1.3f, jr);
        float br = Math.max(26, jr * 0.48f);
        float bx = w - margin - br;
        float by = margin + br;
        run.x = bx; run.y = by; run.r = br;
        use.x = bx - br * 2.4f; use.y = by; use.r = br;
        torch.x = bx; torch.y = by + br * 2.4f; torch.r = br;
        bag.x = bx - br * 2.4f; bag.y = by + br * 2.4f; bag.r = br;
        pick.x = bx - br * 4.8f; pick.y = by; pick.r = br;
        target.x = bx - br * 4.8f; target.y = by + br * 2.4f; target.r = br;
        defensive.x = bx; defensive.y = by + br * 4.8f; defensive.r = br;
        book.x = bx - br * 2.4f; book.y = by + br * 4.8f; book.r = br;
        fish.x = bx - br * 4.8f; fish.y = by + br * 4.8f; fish.r = br;
        talk.x = bx; talk.y = by + br * 7.2f; talk.r = br;
        chat.x = bx - br * 2.4f; chat.y = by + br * 7.2f; chat.r = br;
        // pasek czarow i mikstur: rzad nad joystickiem/przyciskami, od lewej
        float sr = Math.max(20, br * 0.72f);
        float sx0 = margin + jr * 2.6f + sr + 12;
        float sy = margin + sr;
        for (int i = 0; i < spellButtons.length; i++) {
            spellButtons[i].x = sx0 + i * sr * 2.3f;
            spellButtons[i].y = sy;
            spellButtons[i].r = sr;
        }
        potionHp.x = sx0 + 4 * sr * 2.3f; potionHp.y = sy; potionHp.r = sr;
        potionMp.x = sx0 + 5 * sr * 2.3f; potionMp.y = sy; potionMp.r = sr;
        if (potionMp.x + sr > pick.x - br - 6) {
            // waski ekran: pasek w drugim rzedzie nad joystickiem
            float sy2 = margin + jr * 2.6f + sr + 8;
            for (int i = 0; i < spellButtons.length; i++) { spellButtons[i].x = margin + sr + i * sr * 2.3f; spellButtons[i].y = sy2; }
            potionHp.x = margin + sr + 4 * sr * 2.3f; potionHp.y = sy2;
            potionMp.x = margin + sr + 5 * sr * 2.3f; potionMp.y = sy2;
        }
        menuW = Math.min(w - 40, 620);
        menuH = Math.min(h - 40, 400);
        menuX = (w - menuW) / 2f;
        menuY = (h - menuH) / 2f;
        rowH = Math.max(26, Math.min(36, (menuH - 50) / 10f));
        menuClose.x = menuX + menuW - 16; menuClose.y = menuY + menuH - 16; menuClose.r = 14;        diag.x = w - margin - 14; diag.y = h - margin - 14; diag.r = 14;
        slotSize = Math.min(44, Math.min(w, h) / 9f);
        bagX = w / 2f - slotSize * 2.5f;
        bagY = h / 2f - slotSize * 2f;
        float bbr = slotSize * 0.55f;
        bagEquip.x = bagX + slotSize * 1.0f; bagEquip.y = bagY - bbr * 1.4f; bagEquip.r = bbr;
        bagDrop.x = bagX + slotSize * 4.0f; bagDrop.y = bagY - bbr * 1.4f; bagDrop.r = bbr;
        bagUse.x = bagX + slotSize * 2.5f; bagUse.y = bagY - bbr * 1.4f; bagUse.r = bbr;
        bagClose.x = bagX + slotSize * 5f + 4; bagClose.y = bagY + slotSize * 4f + 18; bagClose.r = 14;
    }

    public VirtualJoystick joystick() { return joystick; }
    public boolean bagOpen() { return bagOpen; }
    public void toggleBag() { bagOpen = !bagOpen; selectedSlot = -1; if (bagOpen) menuOpen = false; }
    public void toggleBook() {
        menuOpen = !(menuOpen && !npcMode);
        npcMode = false;
        if (menuOpen) bagOpen = false;
    }

    /** Otwiera lub zamyka rozmowe z NPC w zasiegu. */
    public void toggleNpc(GameSession session) {
        if (menuOpen && npcMode) {
            menuOpen = false;
            npcMode = false;
        } else {
            openNpc(session);
        }
    }

    public void openNpc(GameSession session) {
        pl.fawizja.pixel.npc.Npc n = session.npcNearby();
        if (n == null) return;
        if (n != npc) npcPage = 0;
        npc = n;
        npcMode = true;
        menuOpen = true;
        bagOpen = false;
    }

    public boolean npcOpen() { return menuOpen && npcMode; }
    public boolean bookOpen() { return menuOpen; }
    public void toggleCharacter() { charOpen = !charOpen; }
    public boolean characterOpen() { return charOpen; }

    /** Klucz czaru na pasku (0..3) albo null. */
    public String barSpell(int i) { return i >= 0 && i < barSpells.length ? barSpells[i] : null; }

    /**
     * Automatyczny pasek: najlepsze leczenie, najlepszy atak na cel, atak obszarowy (lub drugi atak),
     * poswiata. Gracz nie musi niczego ustawiac na telefonie.
     */
    public void updateBar(GameSession session) {
        List<SpellType> known = session.spells().knownSpells();
        SpellType heal = null, attack = null, attack2 = null, area = null, extra = null;
        for (SpellType sp : known) {
            switch (sp.kind()) {
                case HEAL_SELF: heal = sp; break;
                case ATTACK_TARGET: attack2 = attack; attack = sp; break;
                case ATTACK_AREA: area = sp; break;
                case LIGHT: extra = sp; break;
                default:
            }
        }
        SpellType[] bar = {heal, attack, area != null ? area : attack2, extra};
        for (int i = 0; i < 4; i++) {
            barSpells[i] = bar[i] == null ? null : bar[i].key();
            spellButtons[i].visible = bar[i] != null;
            spellButtons[i].label = bar[i] == null ? "" : shortName(bar[i].name());
        }
    }

    private static String shortName(String name) {
        String first = name.split(" ")[0];
        return first.length() > 7 ? first.substring(0, 6) + "." : first;
    }

    private void rebuildMenu(GameSession session) {
        menuRows.clear();
        if (npcMode) {
            rebuildNpcMenu(session);
            return;
        }
        PlayerCharacter pc = session.character();
        for (SpellType sp : session.spells().registry().forVocation(pc.vocation())) {
            boolean ok = pc.level() >= sp.minLevel() && pc.skills().level(Skill.MAGIC) >= sp.minMagic();
            String detail = sp.mana() + " many, poz. " + sp.minLevel() + (sp.minMagic() > 0 ? ", magia " + sp.minMagic() : "");
            menuRows.add(new MenuRow("cast:" + sp.key(), sp.name(), detail, ok));
        }
        for (Recipe rc : session.recipes().all()) {
            StringBuilder d = new StringBuilder();
            for (java.util.Map.Entry<Integer, Integer> e : rc.ingredients().entrySet()) {
                if (d.length() > 0) d.append(", ");
                d.append(e.getValue()).append("x ").append(session.items().get(e.getKey()).name());
            }
            if (rc.goldCost() > 0) d.append(", ").append(rc.goldCost()).append(" gp");
            boolean ok = CraftingSystem.canCraft(pc, rc) == CastResult.OK;
            menuRows.add(new MenuRow("craft:" + rc.key(),
                    rc.resultCount() + "x " + session.items().get(rc.resultItemId()).name(), d.toString(), ok));
        }
        // dwie kolumny: czary po lewej, receptury po prawej
        float colW = (menuW - 30) / 2f;
        int spellsCount = 0;
        for (MenuRow row : menuRows) if (row.action.startsWith("cast:")) spellsCount++;
        int li = 0, ri = 0;
        for (MenuRow row : menuRows) {
            boolean left = row.action.startsWith("cast:");
            int idx = left ? li++ : ri++;
            row.x = menuX + 10 + (left ? 0 : colW + 10);
            row.y = menuY + menuH - 40 - (idx + 1) * rowH;
            row.w = colW;
            row.h = rowH - 3;
        }
    }
    private void rebuildNpcMenu(GameSession session) {
        PlayerCharacter pc = session.character();
        List<MenuRow> left = new ArrayList<>();
        List<MenuRow> right = new ArrayList<>();
        int perCol = Math.max(3, (int) ((menuH - 70) / rowH));
        switch (npc.role()) {
            case MERCHANT: {
                pl.fawizja.pixel.npc.Shop shop = session.shopOf(npc);
                List<pl.fawizja.pixel.npc.ShopOffer> sold = new ArrayList<>();
                for (pl.fawizja.pixel.npc.ShopOffer o : shop.offers()) if (o.sold()) sold.add(o);
                left.add(new MenuRow("npc:qty", "Ilosc: " + npcQty + " (zmien)", "dotknij towaru, aby kupic", true));
                int per = perCol - 2;
                int pages = Math.max(1, (sold.size() + per - 1) / per);
                if (npcPage >= pages) npcPage = 0;
                long gold = session.gold();
                for (int i = npcPage * per; i < Math.min(sold.size(), (npcPage + 1) * per); i++) {
                    pl.fawizja.pixel.npc.ShopOffer o = sold.get(i);
                    long cost = o.buyPrice() * npcQty;
                    left.add(new MenuRow("npc:buy:" + o.itemId() + ":" + npcQty, session.items().get(o.itemId()).name(),
                            o.buyPrice() + " gp" + (npcQty > 1 ? ", razem " + cost + " gp" : ""), gold >= cost));
                }
                if (pages > 1) left.add(new MenuRow("npc:page", "Nastepna strona", (npcPage + 1) + " / " + pages, true));
                right.add(new MenuRow("npc:none", "Skup", "dotknij przedmiotu, aby sprzedac wszystkie sztuki", false));
                java.util.Set<Integer> seen = new java.util.LinkedHashSet<>();
                for (ItemStack s : pc.backpack().contents()) if (s != null) seen.add(s.itemId());
                for (int id : seen) {
                    long value = pl.fawizja.pixel.npc.TradeSystem.sellValue(pc.backpack(), shop, id);
                    if (value <= 0 || right.size() >= perCol) continue;
                    right.add(new MenuRow("npc:sell:" + id, pc.backpack().count(id) + "x " + session.items().get(id).name(),
                            "sprzedaj za " + value + " gp", true));
                }
                if (right.size() == 1) right.add(new MenuRow("npc:none", "Nic do sprzedania", "kupiec skupuje lupy z potworow", false));
                break;
            }
            case BANKER: {
                long bal = pc.bank().balance();
                left.add(new MenuRow("npc:none", "Saldo: " + String.format("%,d gp", bal), "konto wspolne dla wszystkich miast", false));
                left.add(new MenuRow("npc:deposit", "Wplac wszystkie monety", String.format("w plecaku: %,d gp", session.gold()), session.gold() > 0));
                for (long a : new long[] {1_000, 10_000, 100_000, 1_000_000}) {
                    left.add(new MenuRow("npc:withdraw:" + a, String.format("Wyplac %,d gp", a), "monety trafia do plecaka", bal >= a));
                }
                break;
            }
            case PRIEST: {
                left.add(new MenuRow("npc:heal", "Ulecz mnie", "pelne zdrowie i mana", pc.health() < pc.maxHealth() || pc.mana() < pc.maxMana()));
                boolean home = npc.town().equalsIgnoreCase(pc.homeTown());
                left.add(new MenuRow("npc:settle", "Zamieszkaj w " + npc.town(),
                        home ? "juz tu mieszkasz" : "teraz: " + pc.homeTown() + " - tu bedziesz sie odradzac", !home));
                break;
            }
            case GUIDE: {
                pl.fawizja.pixel.quest.QuestSystem qs = session.quests();
                if (qs == null) break;
                for (pl.fawizja.pixel.quest.QuestType q : qs.registry().byGiver(npc.key())) {
                    String reward = q.rewardExp() + " exp" + (q.rewardGold() > 0 ? ", " + q.rewardGold() + " gp" : "");
                    String detail;
                    boolean ok;
                    switch (pc.quests().state(q.key())) {
                        case DONE: detail = "ukonczone"; ok = false; break;
                        case ACTIVE:
                            ok = qs.ready(pc, q);
                            detail = ok ? "ODDAJ - nagroda: " + reward : "w toku: " + qs.progress(pc, q) + " / " + q.amount();
                            break;
                        default:
                            ok = pc.level() >= q.minLevel();
                            detail = "przyjmij (poz. " + q.minLevel() + "): " + reward;
                    }
                    MenuRow row = new MenuRow("npc:quest:" + q.key(), q.name(), detail, ok);
                    if (left.size() < perCol) left.add(row);
                    else right.add(row);
                }
                break;
            }
            default:
        }
        float colW = (menuW - 30) / 2f;
        for (int c = 0; c < 2; c++) {
            List<MenuRow> col = c == 0 ? left : right;
            for (int i = 0; i < col.size(); i++) {
                MenuRow row = col.get(i);
                row.x = menuX + 10 + c * (colW + 10);
                row.y = menuY + menuH - 52 - (i + 1) * rowH;
                row.w = colW;
                row.h = rowH - 3;
                menuRows.add(row);
            }
        }
    }

    public void toggleDiagnostics() { diagnosticsOpen = !diagnosticsOpen; }
    public boolean diagnosticsOpen() { return diagnosticsOpen; }
    public int selectedSlot() { return selectedSlot; }
    public void clearSelection() { selectedSlot = -1; }

    // ------------------------------------------------------------------ wejscie

    private Vector2 toUi(int sx, int sy) {
        return viewport.unproject(tmp.set(sx, sy));
    }

    /** Zwraca true, jesli dotyk zostal obsluzony przez interfejs. */
    public boolean touchDown(int sx, int sy, int pointer, GameSession session, Actions actions) {
        Vector2 p = toUi(sx, sy);
        if (p.x <= Math.min(240, w * 0.45f) && p.y >= h - 62) {
            // dotkniecie paskow HP/MP: karta postaci
            toggleCharacter();
            return true;
        }
        if (menuOpen) {
            if (menuClose.hit(p.x, p.y)) { menuOpen = false; return true; }
            rebuildMenu(session);
            for (MenuRow row : menuRows) {
                if (p.x >= row.x && p.x <= row.x + row.w && p.y >= row.y && p.y <= row.y + row.h) {
                    if (row.action.equals("npc:qty")) {
                        int i = 0;
                        while (i < QUANTITIES.length && QUANTITIES[i] != npcQty) i++;
                        npcQty = QUANTITIES[(i + 1) % QUANTITIES.length];
                    } else if (row.action.equals("npc:page")) {
                        npcPage++;
                    } else if (!row.action.equals("npc:none")) {
                        actions.onButton(row.action);
                    }
                    return true;
                }
            }
            if (p.x >= menuX && p.x <= menuX + menuW && p.y >= menuY && p.y <= menuY + menuH) return true;
        }
        if (bagOpen) {
            if (bagClose.hit(p.x, p.y)) { toggleBag(); return true; }
            if (selectedSlot >= 0 && bagEquip.hit(p.x, p.y)) { actions.onButton("bag_equip"); return true; }
            if (selectedSlot >= 0 && bagDrop.hit(p.x, p.y)) { actions.onButton("bag_drop"); return true; }
            if (selectedSlot >= 0 && bagUse.hit(p.x, p.y)) { actions.onButton("bag_use"); return true; }
            int slot = slotAt(p.x, p.y);
            if (slot >= 0) {
                if (selectedSlot < 0) {
                    if (session.character().backpack().get(slot) != null) selectedSlot = slot;
                } else if (selectedSlot == slot) {
                    selectedSlot = -1;
                } else {
                    actions.onButton("slot_move:" + selectedSlot + ":" + slot);
                    selectedSlot = -1;
                }
                return true;
            }
        }
        for (HudButton b : buttons) {
            if (b.hit(p.x, p.y)) {
                actions.onButton(b.id);
                return true;
            }
        }
        if (bagOpen && p.x >= bagX - 8 && p.x <= bagX + slotSize * 5 + 8 && p.y >= bagY - 50 && p.y <= bagY + slotSize * 4 + 30) {
            return true;
        }
        return joystick.touchDown(p.x, p.y, pointer);
    }

    public boolean touchDragged(int sx, int sy, int pointer) {
        Vector2 p = toUi(sx, sy);
        return joystick.drag(p.x, p.y, pointer);
    }

    public boolean touchUp(int sx, int sy, int pointer) {
        return joystick.touchUp(pointer);
    }

    private int slotAt(float x, float y) {
        if (x < bagX || y < bagY) return -1;
        int col = (int) ((x - bagX) / slotSize);
        int row = (int) ((y - bagY) / slotSize);
        if (col < 0 || col >= 5 || row < 0 || row >= 4) return -1;
        return (3 - row) * 5 + col;
    }

    // ------------------------------------------------------------------ rysowanie

    public void render(SpriteBatch batch, ShapeRenderer shapes, GameSession session, WorldClock clock,
                       long serverTimeMs, DiagnosticsInfo diagInfo) {
        viewport.apply();
        PlayerCharacter pc = session.character();
        run.active = pc.isRunning();
        torch.active = pc.isTorchLit();
        bag.active = bagOpen;
        pick.visible = session.itemsAtFeet();
        diag.active = diagnosticsOpen;
        defensive.active = pc.isDefensive();
        book.active = menuOpen && !npcMode;
        fish.visible = session.canFish();
        pl.fawizja.pixel.npc.Npc near = session.npcNearby();
        talk.visible = near != null;
        talk.active = npcOpen();
        chat.visible = chatEnabled;
        if (npcMode && near != npc) {
            menuOpen = false;
            npcMode = false;
        }
        updateBar(session);
        int hpCount = pc.backpack().count(session.items().byKey("health_potion").id());
        int mpCount = pc.backpack().count(session.items().byKey("mana_potion").id());
        potionHp.label = "HP " + hpCount;
        potionMp.label = "MP " + mpCount;
        Monster tgt = session.combat().target();
        target.active = tgt != null;

        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapes.setProjectionMatrix(viewport.getCamera().combined);
        shapes.begin(ShapeRenderer.ShapeType.Filled);

        // paski HP / MP / EXP
        float barW = Math.min(220, w * 0.42f);
        float x = 12, y = h - 22;
        bar(shapes, x, y, barW, 12, pc.health() / (float) pc.maxHealth(), new Color(0.85f, 0.15f, 0.2f, 1));
        bar(shapes, x, y - 17, barW, 12, pc.mana() / (float) Math.max(1, pc.maxMana()), new Color(0.2f, 0.35f, 0.95f, 1));
        bar(shapes, x, y - 31, barW, 6, (float) pc.levelProgress(), new Color(0.95f, 0.8f, 0.2f, 1));

        // joystick
        shapes.setColor(1, 1, 1, joystick.active() ? 0.22f : 0.12f);
        shapes.circle(joystick.baseX(), joystick.baseY(), joystick.radius(), 40);
        shapes.setColor(1, 1, 1, joystick.active() ? 0.55f : 0.35f);
        shapes.circle(joystick.knob().x, joystick.knob().y, joystick.radius() * 0.45f, 30);

        for (HudButton b : buttons) {
            if (!b.visible) continue;
            shapes.setColor(b.active ? new Color(0.15f, 0.7f, 0.25f, 0.85f) : new Color(0.08f, 0.08f, 0.1f, 0.6f));
            shapes.circle(b.x, b.y, b.r, 32);
        }
        for (int i = 0; i < spellButtons.length; i++) {
            HudButton b = spellButtons[i];
            if (!b.visible || barSpells[i] == null) continue;
            SpellType sp = session.spells().registry().get(barSpells[i]);
            shapes.setColor(sp.kind() == SpellKind.HEAL_SELF ? new Color(0.2f, 0.6f, 0.3f, 0.55f)
                    : sp.kind().isOffensive() ? new Color(0.65f, 0.2f, 0.15f, 0.55f) : new Color(0.3f, 0.3f, 0.6f, 0.55f));
            shapes.circle(b.x, b.y, b.r * 0.92f, 28);
            long rem = session.spells().cooldowns().remaining(sp, serverTimeMs);
            if (rem > 0) {
                float frac = Math.min(1f, rem / (float) Math.max(sp.cooldownMs(), sp.group().groupCooldownMs));
                shapes.setColor(0, 0, 0, 0.6f);
                shapes.arc(b.x, b.y, b.r * 0.92f, 90, 360 * frac, 28);
            }
            if (pc.mana() < sp.mana()) {
                shapes.setColor(0.1f, 0.1f, 0.4f, 0.55f);
                shapes.circle(b.x, b.y, b.r * 0.92f, 28);
            }
        }

        if (tgt != null) {
            float tw = Math.min(200, w * 0.36f);
            shapes.setColor(0, 0, 0, 0.55f);
            shapes.rect(w / 2f - tw / 2f - 6, h - 44, tw + 12, 38);
            bar(shapes, w / 2f - tw / 2f, h - 38, tw, 10, tgt.healthFraction(), new Color(0.85f, 0.15f, 0.2f, 1));
        }
        if (charOpen) {
            shapes.setColor(0.06f, 0.06f, 0.08f, 0.88f);
            shapes.rect(10, h - 420, 270, 300);
            float yy = h - 250;
            for (Skill sk : Skill.values()) {
                double prog = (double) pc.skills().tries(sk) / Math.max(1, Skills.triesForNext(pc.skills().level(sk)));
                bar(shapes, 150, yy - 10, 110, 6, (float) prog, new Color(0.3f, 0.75f, 0.4f, 1));
                yy -= 22;
            }
        }
        if (bagOpen) drawBagBackground(shapes, session);
        if (menuOpen) {
            rebuildMenu(session);
            shapes.setColor(0.08f, 0.07f, 0.1f, 0.92f);
            shapes.rect(menuX, menuY, menuW, menuH);
            for (MenuRow row : menuRows) {
                shapes.setColor(row.enabled ? new Color(0.22f, 0.3f, 0.25f, 1f) : new Color(0.2f, 0.18f, 0.18f, 1f));
                shapes.rect(row.x, row.y, row.w, row.h);
            }
            shapes.setColor(0.6f, 0.15f, 0.15f, 0.95f);
            shapes.circle(menuClose.x, menuClose.y, menuClose.r, 20);
        }
        if (diagnosticsOpen) {
            shapes.setColor(0, 0, 0, 0.6f);
            shapes.rect(w - 250, h - 210, 238, 170);
        }
        shapes.end();

        batch.setProjectionMatrix(viewport.getCamera().combined);
        batch.begin();
        font.setColor(Color.WHITE);
        text(batch, pc.health() + " / " + pc.maxHealth(), x + 4, y + 10);
        text(batch, pc.mana() + " / " + pc.maxMana(), x + 4, y - 7);
        int minute = clock.minuteOfDay(serverTimeMs);
        String floor = pc.position().z() == 0 ? "0" : (pc.position().z() > 0 ? "+" + pc.position().z() : String.valueOf(pc.position().z()));
        text(batch, pc.name() + "  " + pc.vocation().displayName() + "  Lv " + pc.level(), x, y - 38);
        text(batch, String.format("%02d:%02d  Pietro %s  %,d gp", minute / 60, minute % 60, floor, session.gold()), x, y - 54);

        List<EventLog.Entry> entries = session.log().latest(4);
        float ly = y - 76;
        for (EventLog.Entry e : entries) {
            float age = (serverTimeMs - e.timeMs()) / 1000f;
            if (age > 8f) continue;
            float a = Math.max(0f, Math.min(1f, (8f - age) / 2f));
            font.setColor(e.kind() == EventLog.Kind.WARNING ? new Color(1f, 0.6f, 0.4f, a)
                    : e.kind() == EventLog.Kind.LOOT ? new Color(0.6f, 1f, 0.6f, a) : new Color(1f, 1f, 1f, a));
            text(batch, e.text(), x, ly);
            ly -= 16;
        }
        font.setColor(Color.WHITE);

        for (HudButton b : buttons) {
            if (!b.visible) continue;
            layout.setText(font, b.label);
            float fit = layout.width > b.r * 1.8f ? b.r * 1.8f / layout.width : 1f;
            font.getData().setScale(fit);
            centered(batch, b.label, b.x, b.y);
            font.getData().setScale(1f);
        }
        if (tgt != null) {
            centered(batch, tgt.type().name() + " (Lv " + tgt.type().level() + ")", w / 2f, h - 16);
        }
        if (charOpen) drawCharacterSheet(batch, session);
        if (bagOpen) drawBagContents(batch, session);
        if (menuOpen) {
            if (npcMode) {
                text(batch, npc.name() + " (" + npc.town() + ")", menuX + 10, menuY + menuH - 14);
                font.getData().setScale(0.85f);
                font.setColor(0.85f, 0.85f, 0.7f, 1f);
                text(batch, npc.greeting(), menuX + 10, menuY + menuH - 32);
                font.setColor(Color.WHITE);
                font.getData().setScale(1f);
            } else {
                text(batch, "Czary (dotknij, aby rzucic)", menuX + 10, menuY + menuH - 14);
                text(batch, "Rzemioslo (dotknij, aby wytworzyc)", menuX + 20 + (menuW - 30) / 2f, menuY + menuH - 14);
            }
            centered(batch, "X", menuClose.x, menuClose.y);
            for (MenuRow row : menuRows) {
                font.setColor(row.enabled ? Color.WHITE : Color.GRAY);
                text(batch, row.label, row.x + 4, row.y + row.h - 3);
                font.getData().setScale(0.8f);
                font.setColor(row.enabled ? new Color(0.8f, 0.85f, 0.7f, 1f) : Color.GRAY);
                text(batch, row.detail, row.x + 4, row.y + row.h * 0.45f);
                font.getData().setScale(1f);
            }
            font.setColor(Color.WHITE);
        }
        if (diagnosticsOpen && diagInfo != null) {
            float dy = h - 50;
            for (String line : diagInfo.lines()) {
                text(batch, line, w - 244, dy);
                dy -= 16;
            }
        }
        batch.end();
    }

    private static final String[] SKILL_NAMES = {"Piesci", "Miecz", "Dystans", "Tarcza", "Magia", "Wedkarstwo"};

    private void drawCharacterSheet(SpriteBatch batch, GameSession session) {
        PlayerCharacter pc = session.character();
        float x = 20, y = h - 130;
        text(batch, pc.name() + " - " + pc.vocation().displayName() + ", poziom " + pc.level(), x, y);
        long next = pl.fawizja.pixel.balance.Balance.experienceForLevel(Math.min(1000, pc.level() + 1));
        text(batch, String.format("Exp: %,d / %,d", pc.experience(), next), x, y - 18);
        text(batch, "Szybkosc " + pc.speed() + "   Udzwig " + pc.capacity(), x, y - 36);
        text(batch, "Sytosc: " + (pc.fedMs() / 60_000) + " min   Pochodnia: " + (pc.isTorchLit() ? (pc.torchBurnMs() + 59_999) / 60_000 + " min" : "zgaszona") + "   Zabite: " + session.combat().kills(), x, y - 54);
        float yy = h - 250;
        for (Skill sk : Skill.values()) {
            text(batch, SKILL_NAMES[sk.ordinal()] + "  " + pc.skills().level(sk), x, yy);
            yy -= 22;
        }
        font.getData().setScale(0.8f);
        text(batch, "Dotknij paskow HP/MP, aby zamknac", x, h - 404);
        font.getData().setScale(1f);
    }

    private void drawBagBackground(ShapeRenderer shapes, GameSession session) {
        shapes.setColor(0.1f, 0.08f, 0.06f, 0.88f);
        shapes.rect(bagX - 8, bagY - 50, slotSize * 5 + 16, slotSize * 4 + 80);
        for (int i = 0; i < 20; i++) {
            float sx = bagX + (i % 5) * slotSize;
            float sy = bagY + (3 - i / 5) * slotSize;
            shapes.setColor(i == selectedSlot ? new Color(0.9f, 0.75f, 0.2f, 1f) : new Color(0.35f, 0.3f, 0.25f, 1f));
            shapes.rect(sx + 2, sy + 2, slotSize - 4, slotSize - 4);
            shapes.setColor(0.18f, 0.15f, 0.12f, 1f);
            shapes.rect(sx + 4, sy + 4, slotSize - 8, slotSize - 8);
        }
        if (selectedSlot >= 0) {
            for (HudButton b : new HudButton[] {bagEquip, bagDrop, bagUse}) {
                shapes.setColor(0.25f, 0.45f, 0.3f, 0.95f);
                shapes.rect(b.x - b.r * 1.3f, b.y - b.r * 0.6f, b.r * 2.6f, b.r * 1.2f);
            }
        }
        shapes.setColor(0.6f, 0.15f, 0.15f, 0.95f);
        shapes.circle(bagClose.x, bagClose.y, bagClose.r, 20);
    }

    private void drawBagContents(SpriteBatch batch, GameSession session) {
        PlayerCharacter pc = session.character();
        text(batch, "Plecak (" + (20 - pc.backpack().freeSlots()) + "/20)", bagX, bagY + slotSize * 4 + 22);
        centered(batch, "X", bagClose.x, bagClose.y);
        for (int i = 0; i < 20; i++) {
            ItemStack s = pc.backpack().get(i);
            if (s == null) continue;
            float sx = bagX + (i % 5) * slotSize;
            float sy = bagY + (3 - i / 5) * slotSize;
            TextureRegion r = art.item(s.itemId());
            if (r != null) batch.draw(r, sx + 5, sy + 5, slotSize - 10, slotSize - 10);
            if (s.count() > 1) text(batch, String.valueOf(s.count()), sx + 5, sy + 15);
        }
        if (selectedSlot >= 0) {
            ItemStack s = pc.backpack().get(selectedSlot);
            if (s != null) {
                ItemType t = session.items().get(s.itemId());
                String stats = t.name();
                if (t.attack() > 0) stats += "  atk " + t.attack();
                if (t.defense() > 0) stats += "  def " + t.defense();
                if (t.armor() > 0) stats += "  arm " + t.armor();
                text(batch, stats, bagX, bagY - 12);
                if (t.slot() != EquipSlot.NONE) centered(batch, bagEquip.label, bagEquip.x, bagEquip.y);
                centered(batch, bagDrop.label, bagDrop.x, bagDrop.y);
                centered(batch, bagUse.label, bagUse.x, bagUse.y);
            }
        }
    }

    private void bar(ShapeRenderer s, float x, float y, float width, float height, float fill, Color c) {
        s.setColor(0, 0, 0, 0.55f);
        s.rect(x - 1, y - 1, width + 2, height + 2);
        s.setColor(c);
        s.rect(x, y, width * Math.max(0f, Math.min(1f, fill)), height);
    }

    private void text(SpriteBatch batch, String s, float x, float y) {
        font.draw(batch, s, x, y);
    }

    private void centered(SpriteBatch batch, String s, float cx, float cy) {
        layout.setText(font, s);
        font.draw(batch, s, cx - layout.width / 2f, cy + layout.height / 2f);
    }

    /** Dane do panelu diagnostyki. */
    public interface DiagnosticsInfo {
        List<String> lines();
    }
}
