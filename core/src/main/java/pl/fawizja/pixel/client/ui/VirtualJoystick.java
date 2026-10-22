package pl.fawizja.pixel.client.ui;

import com.badlogic.gdx.math.Vector2;

/** Wirtualny joystick: aktywuje sie dotykiem w swojej strefie, wektor ma dlugosc 0..1. */
public final class VirtualJoystick {

    private float baseX;
    private float baseY;
    private float radius;
    /** Stale miejsce joysticka w rogu; joystick pod kciukiem wraca tu po puszczeniu. */
    private float homeX;
    private float homeY;
    private boolean floating;
    private int pointer = -1;
    private final Vector2 knob = new Vector2();
    private final Vector2 value = new Vector2();

    public void layout(float x, float y, float radius) {
        this.homeX = x;
        this.homeY = y;
        this.radius = radius;
        if (pointer < 0) {
            baseX = x;
            baseY = y;
            knob.set(x, y);
        }
    }

    /** Joystick pod kciukiem: podstawa tam, gdzie palec dotknal ekranu, potem zwykle przeciaganie. */
    public boolean floatAt(float x, float y, int p) {
        if (pointer >= 0) return false;
        baseX = x;
        baseY = y;
        floating = true;
        pointer = p;
        knob.set(x, y);
        value.setZero();
        return true;
    }

    public boolean floating() { return floating && pointer >= 0; }

    /** Strefa aktywacji jest wieksza niz rysowana podstawa (wygodniej na telefonie). */
    public boolean touchDown(float x, float y, int p) {
        if (pointer >= 0) return false;
        if (Vector2.dst(x, y, baseX, baseY) > radius * 1.8f) return false;
        pointer = p;
        drag(x, y, p);
        return true;
    }

    public boolean drag(float x, float y, int p) {
        if (p != pointer) return false;
        value.set(x - baseX, y - baseY);
        if (value.len() > radius) value.setLength(radius);
        knob.set(baseX + value.x, baseY + value.y);
        value.scl(1f / radius);
        return true;
    }

    public boolean touchUp(int p) {
        if (p != pointer) return false;
        pointer = -1;
        floating = false;
        baseX = homeX;
        baseY = homeY;
        value.setZero();
        knob.set(baseX, baseY);
        return true;
    }

    public boolean active() { return pointer >= 0; }
    public Vector2 value() { return value; }
    public float baseX() { return baseX; }
    public float baseY() { return baseY; }
    public float radius() { return radius; }
    public Vector2 knob() { return knob; }
}
