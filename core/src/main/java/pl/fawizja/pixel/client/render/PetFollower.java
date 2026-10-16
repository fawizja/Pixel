package pl.fawizja.pixel.client.render;

import pl.fawizja.pixel.world.Position;

/**
 * Pupil idzie jedno pole za wlascicielem: celem jest pole, ktore wlasciciel wlasnie opuscil.
 * Pozycja zbliza sie do celu plynnie; przy teleporcie albo zmianie pietra pupil pojawia sie od razu obok.
 */
public final class PetFollower {
    private float x, y;
    private int goalX, goalY;
    private Position last;
    private boolean left;

    public void update(Position owner, long dtMs) {
        if (last == null || owner.z() != last.z() || owner.distanceTo(last) > 1) {
            // pierwszy raz albo skok: pupil stoi tuz obok wlasciciela
            goalX = owner.x() - 1;
            goalY = owner.y();
            x = goalX;
            y = goalY;
            last = owner;
            return;
        }
        if (!owner.equals(last)) {
            goalX = last.x();
            goalY = last.y();
            last = owner;
        }
        if (Math.abs(goalX - x) > 0.01f) left = goalX < x;
        float k = Math.min(1f, Math.max(0, dtMs) / 180f);
        x += (goalX - x) * k;
        y += (goalY - y) * k;
    }

    public float x() { return x; }
    public float y() { return y; }
    public int z() { return last == null ? 0 : last.z(); }
    /** Wiersz mapy do kolejnosci rysowania. */
    public int row() { return Math.round(y); }
    public boolean facingLeft() { return left; }
}
