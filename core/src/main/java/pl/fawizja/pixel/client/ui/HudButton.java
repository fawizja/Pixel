package pl.fawizja.pixel.client.ui;

/** Okragly przycisk ekranowy z etykieta i stanem aktywnym (np. bieganie). */
public final class HudButton {
    public final String id;
    public String label;
    public float x;
    public float y;
    public float r;
    public boolean active;
    public boolean visible = true;
    /** Polowa szerokosci przycisku prostokatnego (pasek na dole); 0 = przycisk okragly. */
    public float hw;
    /** Ikona przedmiotu nad etykieta (np. miecz na przycisku ataku); null = sama etykieta. */
    public com.badlogic.gdx.graphics.g2d.TextureRegion icon;

    public HudButton(String id, String label) {
        this.id = id;
        this.label = label;
    }

    public boolean hit(float px, float py) {
        float dx = px - x, dy = py - y;
        if (hw > 0) return visible && Math.abs(dx) <= hw + 2 && Math.abs(dy) <= r + 2;
        return visible && dx * dx + dy * dy <= r * r * 1.2f;
    }
}
