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
            case "defensive": // stara nazwa przycisku "Tarcza" (starsze klienty) - teraz przelacza tryb walki
            case "fightmode": s.cycleFightMode(now); return true;
            case "pvp": s.togglePvpMode(now); return true;
            case "fish": s.fish(now); return true;
            case "sort": s.sortBackpack(now); return true;
            case "bag_detach": s.detachBag(now); return true;
            case "potion_hp": s.usePotion("health_potion", now); return true;
            case "potion_mp": s.usePotion("mana_potion", now); return true;
            case "tips_off": s.disableTips(now); return true;
            default:
        }
        if (action.startsWith("bar:")) {
            // uklad paska akcji postaci (SpellBar.encode; przecinki i dwukropki w srodku) - zapis w postaci, online na serwerze
            s.character().setActionBar(action.substring(4));
            return true;
        }
        String[] p = action.split(":");
        try {
            switch (p[0]) {
                case "cast": // cast:klucz albo cast:klucz:dopisek (utevo res "wilk", exura sio "imie")
                    if (p.length > 2) s.castSpell(p[1], String.join(" ", java.util.Arrays.copyOfRange(p, 2, p.length)), now);
                    else s.castSpell(p[1], now);
                    return true;
                case "open": // open:x:y:z - drzwi, okno albo skrzynia obok postaci
                    s.useAt(new pl.fawizja.pixel.world.Position(Integer.parseInt(p[1]), Integer.parseInt(p[2]), Integer.parseInt(p[3])), now);
                    return true;
                case "craft": s.craft(p[1], now); return true;
                case "fightmode": s.setFightMode(pl.fawizja.pixel.combat.FightMode.parse(p[1]), now); return true;
                case "item": // item:klucz (na cel), item:klucz:self (na siebie), item:klucz:at:x:y:z (celownik)
                    if (p.length == 2) return useBarItem(s, p[1], now);
                    if (p[2].equals("self")) {
                        s.useItemAt(p[1], s.character().position(), now);
                        return true;
                    }
                    if (p[2].equals("at") && p.length == 6) {
                        s.useItemAt(p[1], pos(p, 3), now);
                        return true;
                    }
                    return false;
                case "slot_at": // slot_at:pole:x:y:z - "Uzyj z..." z plecaka (celownik), takze na siebie
                    s.useSlotAt(Integer.parseInt(p[1]), pos(p, 2), now);
                    return true;
                case "combo": // combo:a+b+c - pierwszy czar gotowy do rzucenia
                    s.castCombo(java.util.Arrays.asList(p[1].split("\\+")), now);
                    return true;
                case "slot_use": s.useSlot(Integer.parseInt(p[1]), now); return true;
                case "slot_equip": s.equipFromSlot(Integer.parseInt(p[1]), now); return true;
                case "unequip": s.unequip(pl.fawizja.pixel.item.EquipSlot.valueOf(p[1]), now); return true;
                case "slot_keep": s.toggleKeepSlot(Integer.parseInt(p[1]), now); return true;
                case "slot_drop": s.dropFromSlot(Integer.parseInt(p[1]), now); return true;
                case "slot_move": s.moveSlot(Integer.parseInt(p[1]), Integer.parseInt(p[2])); return true;
                case "npc": return npc(s, p, now);
                case "store": s.storeBuy(p[1], now); return true;
                case "charm": return charm(s, p, now);
                case "wheel": return wheel(s, p, now);
                default: return false;
            }
        } catch (RuntimeException e) {
            return false; // zle sformatowana akcja od klienta - ignorujemy
        }
    }

    private static pl.fawizja.pixel.world.Position pos(String[] p, int i) {
        return new pl.fawizja.pixel.world.Position(Integer.parseInt(p[i]), Integer.parseInt(p[i + 1]), Integer.parseInt(p[i + 2]));
    }

    /** Pole paska akcji z mikstura albo runa: pierwsza taka sztuka z plecaka. */
    private static boolean useBarItem(GameSession s, String key, long now) {
        if (!s.items().containsKey(key)) return false;
        pl.fawizja.pixel.item.ItemCategory c = s.items().byKey(key).category();
        if (c != pl.fawizja.pixel.item.ItemCategory.POTION && c != pl.fawizja.pixel.item.ItemCategory.RUNE) return false;
        s.usePotion(key, now);
        return true;
    }

    /**
     * Kuznia u kowala: npc:forge:fuse:poleA:poleB:rdzenSzansy(0/1):rdzenOchrony(0/1), npc:forge:transfer:dawca:biorca,
     * npc:forge:slivers (60 pylu na 3 odlamki), npc:forge:core (50 odlamkow na rdzen), npc:forge:limit.
     */
    private static boolean forge(GameSession s, String[] p, long now) {
        switch (p[2]) {
            case "fuse":
                s.forgeFuse(Integer.parseInt(p[3]), Integer.parseInt(p[4]), "1".equals(p[5]), "1".equals(p[6]), now);
                return true;
            case "transfer": s.forgeTransfer(Integer.parseInt(p[3]), Integer.parseInt(p[4]), now); return true;
            case "slivers":
            case "core": s.forgeConvert(p[2], now); return true;
            case "limit": s.forgeRaiseLimit(now); return true;
            default: return false;
        }
    }

    /** Charmy bestiariusza: charm:buy:NAZWA, charm:assign:NAZWA:potwor, charm:remove:NAZWA, charm:reset. */
    private static boolean charm(GameSession s, String[] p, long now) {
        switch (p[1]) {
            case "buy": s.buyCharm(pl.fawizja.pixel.charm.Charm.parse(p[2]), now); return true;
            case "assign": s.assignCharm(pl.fawizja.pixel.charm.Charm.parse(p[2]), p[3], now); return true;
            case "remove": s.removeCharm(pl.fawizja.pixel.charm.Charm.parse(p[2]), now); return true;
            case "reset": s.resetCharms(now); return true;
            default: return false;
        }
    }

    /**
     * Kolo Przeznaczenia: wheel:add:KOD:n, wheel:remove:KOD:n, wheel:reset, wheel:save:i, wheel:load:i; klejnoty:
     * wheel:gem:reveal:L|R|G, wheel:gem:lock:ID, wheel:gem:destroy:ID, wheel:gem:switch:ID, wheel:gem:socket:ID,
     * wheel:gem:unsocket:G|R|P|B; stopnie modow: wheel:grade:b:MOD, wheel:grade:s:MOD.
     */
    private static boolean wheel(GameSession s, String[] p, long now) {
        switch (p[1]) {
            case "gem": return wheelGem(s, p, now);
            case "grade":
                if ("b".equals(p[2])) s.gemGradeBasic(pl.fawizja.pixel.wheel.BasicGemMod.parse(p[3]), now);
                else s.gemGradeSupreme(pl.fawizja.pixel.wheel.SupremeGemMod.parse(p[3]), now);
                return true;
            case "add": s.wheelAdd(p[2], Integer.parseInt(p[3]), now); return true;
            case "remove": s.wheelRemove(p[2], Integer.parseInt(p[3]), now); return true;
            case "reset": s.wheelReset(now); return true;
            case "save": s.wheelPresetSave(Integer.parseInt(p[2]), now); return true;
            case "load": s.wheelPresetLoad(Integer.parseInt(p[2]), now); return true;
            default: return false;
        }
    }

    private static boolean wheelGem(GameSession s, String[] p, long now) {
        switch (p[2]) {
            case "reveal": s.gemReveal(pl.fawizja.pixel.wheel.GemQuality.ofCode(p[3].charAt(0)), now); return true;
            case "lock": s.gemLock(Integer.parseInt(p[3]), now); return true;
            case "destroy": s.gemDestroy(Integer.parseInt(p[3]), now); return true;
            case "switch": s.gemSwitch(Integer.parseInt(p[3]), now); return true;
            case "socket": s.gemSocket(Integer.parseInt(p[3]), now); return true;
            case "unsocket": s.gemUnsocket(pl.fawizja.pixel.wheel.WheelDomain.ofCode(p[3].charAt(0)), now); return true;
            default: return false;
        }
    }

    private static boolean npc(GameSession s, String[] p, long now) {
        switch (p[1]) {
            case "buy": s.buy(Integer.parseInt(p[2]), Integer.parseInt(p[3]), now); return true;
            case "sell": s.sellAll(Integer.parseInt(p[2]), now); return true;
            case "sellloot": s.sellLoot(now); return true;
            case "refill": s.refillSupplies(now); return true;
            case "storeloot": s.depotStoreLoot(now); return true;
            case "deposit": s.depositAll(now); return true;
            case "withdraw": s.withdraw(Long.parseLong(p[2]), now); return true;
            case "store": s.depotStore(Integer.parseInt(p[2]), now); return true;
            case "take": s.depotTake(Integer.parseInt(p[2]), now); return true;
            case "crateput": s.cratePut(Integer.parseInt(p[2]), now); return true;
            case "cratetake": s.crateTake(Integer.parseInt(p[2]), now); return true;
            case "heal": s.priestHeal(now); return true;
            case "bless": s.priestBless(now); return true;
            case "premium": s.buyPremium(now); return true;
            case "promote": s.buyPromotion(now); return true;
            case "upgrade": s.upgradeEquipped(pl.fawizja.pixel.item.EquipSlot.valueOf(p[2]), now); return true;
            case "enchant": s.buyEnchantment(pl.fawizja.pixel.item.Enchantments.Kind.valueOf(p[2]), now); return true;
            case "forge": return forge(s, p, now);
            case "imbue": // npc:imbue:SLOT:RODZAJ:STOPIEN
                s.imbue(pl.fawizja.pixel.item.EquipSlot.valueOf(p[2]), pl.fawizja.pixel.item.Imbuement.valueOf(p[3]), Integer.parseInt(p[4]), now);
                return true;
            case "unimbue": s.clearImbuement(pl.fawizja.pixel.item.EquipSlot.valueOf(p[2]), Integer.parseInt(p[3]), now); return true;
            case "travel": s.travel(p[2], now); return true;
            case "settle": s.settleHere(now); return true;
            case "housebuy": s.buyHouse(p[2], now); return true;
            case "houseleave": s.leaveHouse(now); return true;
            case "quest": s.questAction(p[2], now); return true;
            case "daily": s.dailyAction(now); return true;
            case "dailyreroll": s.dailyReroll(now); return true;
            case "hunt": s.huntAction(now); return true;
            case "huntdrop": s.huntDrop(now); return true;
            case "ask": s.askNpc(p[2], now); return true;
            default: return false;
        }
    }
}
