package pl.fawizja.pixel.world;

/** Miasto: nazwa i swiatynia (miejsce startu i odrodzenia). Swiat ma najwyzej 6 miast. */
public record Town(String name, Position temple) {
    public static final int MAX_TOWNS = 6;
}
