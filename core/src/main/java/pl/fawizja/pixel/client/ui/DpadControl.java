package pl.fawizja.pixel.client.ui;

import pl.fawizja.pixel.movement.DpadInput;
import pl.fawizja.pixel.world.Direction;

/** Krzyzyk na ekranie: staly srodek w rogu, palec (jeden wskaznik) i wcisniety kierunek ({@link DpadInput}). */
public final class DpadControl {

    private float x, y, radius;
    private int pointer = -1;
    private Direction pressed;

    public void layout(float x, float y, float radius) {
        this.x = x;
        this.y = y;
        this.radius = radius;
    }

    public boolean touchDown(float px, float py, int p) {
        if (pointer >= 0 || !DpadInput.hit(px - x, py - y, radius)) return false;
        pointer = p;
        pressed = DpadInput.classify(px - x, py - y, radius);
        return true;
    }

    /** Przesuniecie palca: kierunek liczy sie dalej, takze poza polem dotyku (palec zsuwa sie z krzyzyka). */
    public boolean drag(float px, float py, int p) {
        if (p != pointer) return false;
        pressed = DpadInput.classify(px - x, py - y, radius);
        return true;
    }

    public boolean touchUp(int p) {
        if (p != pointer) return false;
        release();
        return true;
    }

    public void release() {
        pointer = -1;
        pressed = null;
    }

    /** Podglad wcisnietego kierunku (zrzuty ekranu) bez prawdziwego dotyku. */
    public void press(Direction d) {
        pointer = 99;
        pressed = d;
    }

    public boolean active() { return pointer >= 0; }
    /** Wcisniety kierunek albo null (srodek / puszczony). */
    public Direction direction() { return pressed; }
    public float x() { return x; }
    public float y() { return y; }
    public float radius() { return radius; }
}
