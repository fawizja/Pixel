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
    private final Vector2 tmp = new Vector2();
    private float uiScale = 1f;
    private float w;
    private float h;

    private boolean bagOpen;
    private int selectedSlot = -1;
    private float bagX, bagY, slotSize;
    private final HudButton bagEquip = new HudButton("bag_equip", "ZALOZ");
    private final HudButton bagDrop = new HudButton("bag_drop", "WYRZUC");
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
    }

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
        diag.x = w - margin - 14; diag.y = h - margin - 14; diag.r = 14;
        slotSize = Math.min(44, Math.min(w, h) / 9f);
        bagX = w / 2f - slotSize * 2.5f;
        bagY = h / 2f - slotSize * 2f;
        float bbr = slotSize * 0.55f;
        bagEquip.x = bagX + slotSize * 1.0f; bagEquip.y = bagY - bbr * 1.4f; bagEquip.r = bbr;
        bagDrop.x = bagX + slotSize * 4.0f; bagDrop.y = bagY - bbr * 1.4f; bagDrop.r = bbr;
        bagClose.x = bagX + slotSize * 5f + 4; bagClose.y = bagY + slotSize * 4f + 18; bagClose.r = 14;
    }

    public VirtualJoystick joystick() { return joystick; }
    public boolean bagOpen() { return bagOpen; }
    public void toggleBag() { bagOpen = !bagOpen; selectedSlot = -1; }
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
        if (bagOpen) {
            if (bagClose.hit(p.x, p.y)) { toggleBag(); return true; }
            if (selectedSlot >= 0 && bagEquip.hit(p.x, p.y)) { actions.onButton("bag_equip"); return true; }
            if (selectedSlot >= 0 && bagDrop.hit(p.x, p.y)) { actions.onButton("bag_drop"); return true; }
            int slot = slotAt(p.x, p.y);
            if (slot >= 0) {
                if (selectedSlot < 0) {
                    if (session.character().backpack().get(slot) != null) selectedSlot = slot;
                } else if (selectedSlot == slot) {
                    selectedSlot = -1;
                } else {
                    session.moveSlot(selectedSlot, slot);
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

        if (tgt != null) {
            float tw = Math.min(200, w * 0.36f);
            shapes.setColor(0, 0, 0, 0.55f);
            shapes.rect(w / 2f - tw / 2f - 6, h - 44, tw + 12, 38);
            bar(shapes, w / 2f - tw / 2f, h - 38, tw, 10, tgt.healthFraction(), new Color(0.85f, 0.15f, 0.2f, 1));
        }
        if (bagOpen) drawBagBackground(shapes, session);
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
            centered(batch, b.label, b.x, b.y);
        }
        if (tgt != null) {
            centered(batch, tgt.type().name() + " (Lv " + tgt.type().level() + ")", w / 2f, h - 16);
        }
        if (bagOpen) drawBagContents(batch, session);
        if (diagnosticsOpen && diagInfo != null) {
            float dy = h - 50;
            for (String line : diagInfo.lines()) {
                text(batch, line, w - 244, dy);
                dy -= 16;
            }
        }
        batch.end();
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
            for (HudButton b : new HudButton[] {bagEquip, bagDrop}) {
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
