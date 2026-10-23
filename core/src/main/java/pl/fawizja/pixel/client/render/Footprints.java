package pl.fawizja.pixel.client.render;

import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import pl.fawizja.pixel.world.FloorMap;
import pl.fawizja.pixel.world.Layer;
import pl.fawizja.pixel.world.Position;
import pl.fawizja.pixel.world.TileType;
import pl.fawizja.pixel.world.WorldMap;

/**
 * Slady na sniegu: kto zejdzie z pola sniegu, zostawia na nim pare odciskow stop zwroconych w strone kroku.
 * Slady bledna i znikaja po {@link #LIFETIME_MS}; tylko grafika klienta, bez wplywu na gre.
 */
public final class Footprints {

    public static final long LIFETIME_MS = 40_000;
    private static final int MAX = 600;

    private static final class Print {
        final int x, y, z, dx, dy;
        final long at;

        Print(int x, int y, int z, int dx, int dy, long at) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.dx = dx;
            this.dy = dy;
            this.at = at;
        }
    }

    private final WorldMap world;
    private final boolean[] snowy;
    private final ArrayDeque<Print> prints = new ArrayDeque<>();
    private final Map<String, Position> last = new HashMap<>();

    public Footprints(WorldMap world) {
        this.world = world;
        int max = 0;
        for (TileType t : world.tiles().all()) max = Math.max(max, t.id());
        snowy = new boolean[max + 1];
        for (String key : new String[] {"snow", "deep_snow", "snow_path"}) {
            TileType t = world.tiles().byKeyOrNull(key);
            if (t != null) snowy[t.id()] = true;
        }
    }

    /** Pozycja chodzacego (gracz, inny gracz, potwor) w tej klatce; krok z pola sniegu zostawia na nim slad. */
    public void track(String who, Position now, long nowMs) {
        if (last.size() > 4000) last.clear();
        Position prev = last.put(who, now);
        if (prev == null || prev.equals(now) || prev.z() != now.z() || prev.distanceTo(now) > 1) return;
        if (!isSnow(prev)) return;
        prints.addLast(new Print(prev.x(), prev.y(), prev.z(), Integer.signum(now.x() - prev.x()),
                Integer.signum(now.y() - prev.y()), nowMs));
        while (prints.size() > MAX) prints.removeFirst();
    }

    public void forget(String who) { last.remove(who); }

    public int size() { return prints.size(); }

    boolean isSnow(Position p) {
        FloorMap f = world.floor(p.z());
        if (f == null || !f.inBounds(p.x(), p.y())) return false;
        int g = f.get(Layer.GROUND, p.x(), p.y());
        return g > 0 && g < snowy.length && snowy[g];
    }

    /** Rysuje slady jednego rzedu mapy (wywolanie z RowDrawer, przed postaciami tego rzedu). */
    public void drawRow(SpriteBatch b, TextureRegion white, WorldRenderer wr, int row, int z, long nowMs) {
        if (prints.isEmpty() || white == null) return;
        Iterator<Print> it = prints.iterator();
        while (it.hasNext()) {
            Print p = it.next();
            if (nowMs - p.at > LIFETIME_MS) {
                it.remove();
                continue;
            }
            if (p.y != row || p.z != z) continue;
            float fade = 1f - (nowMs - p.at) / (float) LIFETIME_MS;
            float wy = wr.worldY(p.y);
            float cx = p.x + 0.5f, cy = wy + 0.5f;
            // dwie stopy obok siebie, przesuniete poprzecznie do kierunku kroku
            float px = -p.dy * 0.12f, py = p.dx * 0.12f;
            float ax = p.dx * 0.12f, ay = -p.dy * 0.12f;
            b.setColor(0.42f, 0.5f, 0.62f, 0.55f * fade);
            foot(b, white, cx + px - ax, cy + py - ay);
            foot(b, white, cx - px + ax, cy - py + ay);
        }
        b.setColor(1f, 1f, 1f, 1f);
    }

    private static void foot(SpriteBatch b, TextureRegion white, float x, float y) {
        b.draw(white, x - 0.06f, y - 0.09f, 0.12f, 0.18f);
        b.draw(white, x - 0.08f, y - 0.05f, 0.16f, 0.1f);
    }
}
