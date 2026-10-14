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

    public HudButton(String id, String label) {
        this.id = id;
        this.label = label;
    }

    public boolean hit(float px, float py) {
        float dx = px - x, dy = py - y;
        return visible && dx * dx + dy * dy <= r * r * 1.2f;
    }
}
