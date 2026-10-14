package pl.fawizja.pixel.monster;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import pl.fawizja.pixel.world.Position;
import pl.fawizja.pixel.world.WorldMap;

/** Wczytuje /maps/&lt;mapa&gt;/spawns.csv i sprawdza, czy spawny leza na przechodnich kafelkach poza strefa ochronna. */
public final class SpawnTable {

    private SpawnTable() {}

    public static List<SpawnPoint> load(String mapName, WorldMap world, MonsterRegistry monsters) throws IOException {
        List<SpawnPoint> list = new ArrayList<>();
        int n = 0;
        for (String line : MonsterRegistry.readLines("/maps/" + mapName + "/spawns.csv")) {
            n++;
            String t = line.trim();
            if (t.isEmpty() || t.startsWith("#")) continue;
            String[] f = t.split(";");
            if (f.length != 7) throw new IOException("spawns.csv line " + n + ": expected 7 fields");
            try {
                monsters.get(f[0].trim());
                Position c = new Position(Integer.parseInt(f[1].trim()), Integer.parseInt(f[2].trim()), Integer.parseInt(f[3].trim()));
                if (!world.isWalkable(c)) throw new IOException("spawns.csv line " + n + ": center not walkable " + c);
                if (world.isProtectionZone(c)) throw new IOException("spawns.csv line " + n + ": spawn inside protection zone");
                list.add(new SpawnPoint(f[0].trim(), c, Integer.parseInt(f[4].trim()), Integer.parseInt(f[5].trim()),
                        Integer.parseInt(f[6].trim())));
            } catch (IllegalArgumentException e) {
                throw new IOException("spawns.csv line " + n + ": " + e.getMessage(), e);
            }
        }
        return list;
    }
}
