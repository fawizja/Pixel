package pl.fawizja.pixel.client.render;

import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import pl.fawizja.pixel.npc.Npc;
import pl.fawizja.pixel.npc.NpcRole;
import pl.fawizja.pixel.npc.Shop;
import pl.fawizja.pixel.npc.ShopOffer;
import pl.fawizja.pixel.npc.ShopRegistry;
import pl.fawizja.pixel.world.FloorMap;
import pl.fawizja.pixel.world.Layer;
import pl.fawizja.pixel.world.Position;
import pl.fawizja.pixel.world.TileType;
import pl.fawizja.pixel.world.WorldMap;

/**
 * Towary na ladzie: na stolach, ladach i skrzyniach obok kupca leza poglądowo przedmioty z jego sklepu
 * (to, co sprzedaje, i jedna rzecz, ktora skupuje - z zlotym znaczkiem monety). Tylko grafika.
 */
public final class ShopDisplays {

    private static final Set<String> STANDS = Set.of("counter", "table", "showcase", "crate", "barrel", "market_stall");

    /** Przedmiot wystawiony na polu: bought = kupiec to skupuje. */
    public record Display(int x, int y, int z, int itemId, boolean bought) {}

    private final List<Display> displays = new ArrayList<>();

    public ShopDisplays(WorldMap world, Iterable<Npc> npcs, ShopRegistry shops) {
        if (shops == null) return;
        for (Npc n : npcs) {
            if (n.role() != NpcRole.MERCHANT || n.shopKey() == null) continue;
            Shop shop = shops.get(n.shopKey());
            if (shop == null) continue;
            List<ShopOffer> sold = new ArrayList<>(), bought = new ArrayList<>();
            for (ShopOffer o : shop.offers()) (o.sold() ? sold : bought).add(o);
            List<ShopOffer> show = new ArrayList<>();
            for (int i = 0; i < sold.size() && show.size() < 2; i++) show.add(sold.get(i * Math.max(1, sold.size() / 2)));
            if (!bought.isEmpty()) show.add(bought.get(bought.size() / 2));
            Position p = n.position();
            FloorMap f = world.floor(p.z());
            if (f == null) continue;
            int k = 0;
            for (int[] d : new int[][] {{0, -1}, {-1, 0}, {1, 0}, {0, 1}, {-1, -1}, {1, -1}, {-1, 1}, {1, 1}, {-2, 0}, {2, 0}, {0, -2}}) {
                if (k >= show.size()) break;
                int x = p.x() + d[0], y = p.y() + d[1];
                if (!f.inBounds(x, y)) continue;
                TileType t = world.tiles().get(f.get(Layer.OBJECT, x, y));
                if (t == null || !STANDS.contains(t.key())) continue;
                ShopOffer o = show.get(k++);
                displays.add(new Display(x, y, p.z(), o.itemId(), !o.sold()));
            }
        }
    }

    public List<Display> all() { return displays; }

    public void drawRow(SpriteBatch b, TileArt art, WorldRenderer wr, int row, int z) {
        for (int i = 0; i < displays.size(); i++) {
            Display d = displays.get(i);
            if (d.y() != row || d.z() != z) continue;
            TextureRegion r = art.item(d.itemId());
            if (r == null) continue;
            float wy = wr.worldY(d.y());
            b.draw(r, d.x() + 0.2f, wy + 0.3f, 0.6f, 0.6f);
            if (d.bought() && art.white() != null) {
                // skup: zlota moneta w rogu
                b.setColor(0.25f, 0.18f, 0f, 1f);
                b.draw(art.white(), d.x() + 0.66f, wy + 0.24f, 0.2f, 0.2f);
                b.setColor(1f, 0.82f, 0.25f, 1f);
                b.draw(art.white(), d.x() + 0.68f, wy + 0.26f, 0.16f, 0.16f);
                b.setColor(1f, 1f, 1f, 1f);
            }
        }
    }
}
