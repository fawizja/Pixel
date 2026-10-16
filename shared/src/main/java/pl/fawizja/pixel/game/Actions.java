package pl.fawizja.pixel.game;

/**
 * Akcje gracza jako tekst - te same identyfikatory wysyla HUD i klient sieciowy, a serwer
 * wykonuje je na sesji gracza. Akcje zwiazane tylko z widokiem (plecak, ksiega) obsluguje klient.
 */
public final class Actions {

    private Actions() {}

    /** Wykonuje akcje; zwraca false dla nieznanej akcji (serwer ja ignoruje). */
    public static boolean apply(GameSession s, String action, long now) {
        switch (action) {
            case "use": s.useNearby(now); return true;
            case "run": s.toggleRun(now); return true;
            case "mount": s.toggleMount(now); return true;
            case "torch": s.toggleTorch(now); return true;
            case "pick": s.pickUpHere(now); return true;
            case "loot": s.searchCorpse(now); return true;
            case "target": s.cycleTarget(now); return true;
            case "defensive": s.toggleDefensive(now); return true;
            case "fish": s.fish(now); return true;
            case "potion_hp": s.usePotion("health_potion", now); return true;
            case "potion_mp": s.usePotion("mana_potion", now); return true;
            default:
        }
        String[] p = action.split(":");
        try {
            switch (p[0]) {
                case "cast": s.castSpell(p[1], now); return true;
                case "craft": s.craft(p[1], now); return true;
                case "slot_use": s.useSlot(Integer.parseInt(p[1]), now); return true;
                case "slot_equip": s.equipFromSlot(Integer.parseInt(p[1]), now); return true;
                case "slot_drop": s.dropFromSlot(Integer.parseInt(p[1]), now); return true;
                case "slot_move": s.moveSlot(Integer.parseInt(p[1]), Integer.parseInt(p[2])); return true;
                case "npc": return npc(s, p, now);
                default: return false;
            }
        } catch (RuntimeException e) {
            return false; // zle sformatowana akcja od klienta - ignorujemy
        }
    }

    private static boolean npc(GameSession s, String[] p, long now) {
        switch (p[1]) {
            case "buy": s.buy(Integer.parseInt(p[2]), Integer.parseInt(p[3]), now); return true;
            case "sell": s.sellAll(Integer.parseInt(p[2]), now); return true;
            case "deposit": s.depositAll(now); return true;
            case "withdraw": s.withdraw(Long.parseLong(p[2]), now); return true;
            case "store": s.depotStore(Integer.parseInt(p[2]), now); return true;
            case "take": s.depotTake(Integer.parseInt(p[2]), now); return true;
            case "heal": s.priestHeal(now); return true;
            case "travel": s.travel(p[2], now); return true;
            case "settle": s.settleHere(now); return true;
            case "quest": s.questAction(p[2], now); return true;
            case "daily": s.dailyAction(now); return true;
            case "ask": s.askNpc(p[2], now); return true;
            default: return false;
        }
    }
}
