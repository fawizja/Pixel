package pl.fawizja.pixel.server;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.logging.Logger;
import pl.fawizja.pixel.character.CharacterCodec;
import pl.fawizja.pixel.character.CharacterFactory;
import pl.fawizja.pixel.character.PlayerCharacter;
import pl.fawizja.pixel.config.StartingConfig;
import pl.fawizja.pixel.combat.CombatEvent;
import pl.fawizja.pixel.game.Actions;
import pl.fawizja.pixel.game.EventLog;
import pl.fawizja.pixel.game.GameSession;
import pl.fawizja.pixel.item.ItemRegistry;
import pl.fawizja.pixel.magic.ConsumableRegistry;
import pl.fawizja.pixel.magic.RecipeRegistry;
import pl.fawizja.pixel.magic.SpellRegistry;
import pl.fawizja.pixel.monster.Monster;
import pl.fawizja.pixel.monster.MonsterManager;
import pl.fawizja.pixel.monster.MonsterRegistry;
import pl.fawizja.pixel.monster.SpawnTable;
import pl.fawizja.pixel.net.Protocol;
import pl.fawizja.pixel.npc.NpcRegistry;
import pl.fawizja.pixel.npc.ShopRegistry;
import pl.fawizja.pixel.pvp.PvpRules;
import pl.fawizja.pixel.trade.PlayerTrade;
import pl.fawizja.pixel.quest.QuestRegistry;
import pl.fawizja.pixel.save.SaveStore;
import pl.fawizja.pixel.vocation.Vocation;
import pl.fawizja.pixel.world.Direction;
import pl.fawizja.pixel.world.GroundItems;
import pl.fawizja.pixel.world.MapLoader;
import pl.fawizja.pixel.world.Position;
import pl.fawizja.pixel.world.TileRegistry;
import pl.fawizja.pixel.world.WorldLayout;
import pl.fawizja.pixel.world.WorldMap;

/**
 * Jeden wspolny swiat: potwory, przedmioty na ziemi i sesje wszystkich graczy. Wszystkie metody
 * wywoluje tylko watek petli serwera (bez blokad w logice gry).
 */
final class ServerWorld {
    private static final Logger LOG = Logger.getLogger("pixel.server");
    static final long SAVE_EVERY_MS = 60_000;
    static final long SELF_EVERY_MS = 250;
    /** Czat: najwyzej CHAT_MAX wiadomosci w CHAT_WINDOW_MS. */
    static final int CHAT_MAX = 5;
    static final long CHAT_WINDOW_MS = 10_000;

    final ItemRegistry items = ItemRegistry.loadDefault();
    final WorldMap world;
    final MonsterManager monsters;
    final GroundItems ground = new GroundItems();
    /** Stan PvP po imieniu - przetrwa wylogowanie (czaszki nie znikaja po ponownym wejsciu). */
    private final Map<String, PvpRules.Status> pvp = new java.util.HashMap<>();
    /** Limit zakladania kont: tyle nowych kont z jednego adresu na dobe. */
    static final long NEW_ACCOUNT_WINDOW_MS = 24 * 3_600_000L;
    int maxNewAccountsPerAddress = 5;
    private final Map<java.net.InetAddress, java.util.ArrayDeque<Long>> newAccounts = new java.util.HashMap<>();
    private long lastNow;
    private final Random pvpRng = new Random();
    private final StartingConfig config = StartingConfig.loadDefault();
    private final MonsterRegistry monsterTypes;
    private final NpcRegistry npcs;
    private final ShopRegistry shops;
    private final QuestRegistry quests;
    private final pl.fawizja.pixel.treasure.TreasureChests chests;
    private final SpellRegistry spells;
    private final ConsumableRegistry consumables;
    private final RecipeRegistry recipes;
    private final CharacterCodec codec = new CharacterCodec(items);
    /** Ranking wszystkich postaci (takze offline), budowany z zapisow przy starcie. */
    final pl.fawizja.pixel.ranking.Ranking ranking = new pl.fawizja.pixel.ranking.Ranking();
    private final SaveStore saves;
    private final AccountStore accounts;
    private final Random rng = new Random();
    /** Trwajaca inwazja na miasto (null = spokoj) i czas nastepnej (0 = ustalany przy pierwszym kroku). */
    private pl.fawizja.pixel.event.Invasion invasion;
    private long nextInvasionAt;
    private int nextInvasionPlan = -1;
    private final long invasionEveryMs = Math.max(1, Long.getLong("pixel.invasionMinutes", 120L)) * 60_000L;
    private final Map<Integer, ServerPlayer> players = new LinkedHashMap<>();
    private final pl.fawizja.pixel.party.PartyManager parties = new pl.fawizja.pixel.party.PartyManager();
    /** Otwarte wymiany (klucz: imie malymi literami, obie strony wskazuja ta sama wymiane). */
    private final Map<String, PlayerTrade> trades = new java.util.HashMap<>();
    private final pl.fawizja.pixel.guild.GuildRegistry guilds;
    private final Moderation moderation;
    /** Zegar scienny dla banow i wyciszen (testy moga go podmienic). */
    volatile java.util.function.LongSupplier wallClock = System::currentTimeMillis;
    private final File guildFile;
    private final File marketFile;
    private final File mailFile;
    final pl.fawizja.pixel.mail.Mailbox mail;
    final pl.fawizja.pixel.market.Market market;
    /** Prosby o handel: adresat -> (proszacy -> waznosc). */
    private final Map<String, Map<String, Long>> tradeRequests = new java.util.HashMap<>();
    static final long TRADE_REQUEST_MS = 60_000L;
    private int nextId = 1;

    ServerWorld(File saveDir) throws IOException {
        world = new MapLoader(TileRegistry.loadDefault()).load(WorldLayout.WORLD_MAP);
        monsterTypes = MonsterRegistry.loadDefault(items);
        monsters = new MonsterManager(world, monsterTypes, SpawnTable.load(WorldLayout.WORLD_MAP, world, monsterTypes), rng);
        monsters.populate(null);
        shops = ShopRegistry.loadDefault(items);
        npcs = NpcRegistry.load(WorldLayout.WORLD_MAP, world, shops);
        quests = QuestRegistry.loadDefault(items, monsterTypes);
        chests = pl.fawizja.pixel.treasure.TreasureChests.generate(world);
        spells = SpellRegistry.loadDefault(items);
        consumables = ConsumableRegistry.loadDefault(items);
        recipes = RecipeRegistry.loadDefault(items);
        saves = new SaveStore(saveDir);
        accounts = new AccountStore(saveDir);
        moderation = new Moderation(saveDir);
        guildFile = new File(saveDir, "guilds.txt");
        guilds = pl.fawizja.pixel.guild.GuildRegistry.decode(guildFile.isFile()
                ? new String(java.nio.file.Files.readAllBytes(guildFile.toPath()), java.nio.charset.StandardCharsets.UTF_8) : null);
        marketFile = new File(saveDir, "market.txt");
        market = pl.fawizja.pixel.market.Market.decode(marketFile.isFile()
                ? new String(java.nio.file.Files.readAllBytes(marketFile.toPath()), java.nio.charset.StandardCharsets.UTF_8) : null);
        mailFile = new File(saveDir, "mail.txt");
        mail = pl.fawizja.pixel.mail.Mailbox.decode(mailFile.isFile()
                ? new String(java.nio.file.Files.readAllBytes(mailFile.toPath()), java.nio.charset.StandardCharsets.UTF_8) : null);
        loadRanking(saveDir);
    }

    /** Ranking z zapisow postaci (player_*.save); uszkodzony zapis jest pomijany. */
    private void loadRanking(File saveDir) {
        File[] files = saveDir.listFiles((d, n) -> n.startsWith("player_") && n.endsWith(".save"));
        if (files == null) return;
        for (File f : files) {
            String slot = f.getName().substring(0, f.getName().length() - ".save".length());
            try {
                PlayerCharacter pc = saves.read(slot, codec::decode).value();
                if (pc != null) ranking.put(rankEntry(pc));
            } catch (IOException | RuntimeException e) {
                LOG.warning("ranking: pomijam " + f.getName() + ": " + e.getMessage());
            }
        }
    }

    private static pl.fawizja.pixel.ranking.Ranking.Entry rankEntry(PlayerCharacter pc) {
        return new pl.fawizja.pixel.ranking.Ranking.Entry(pc.name(), pc.vocation(), pc.level(), pc.experience());
    }

    /** /ranking [profesja]: pierwsze miejsca i miejsce pytajacego. */
    private void sendRanking(ServerPlayer p, String arg, long now) {
        pl.fawizja.pixel.vocation.Vocation filter = null;
        if (arg != null && !arg.isBlank()) {
            filter = pl.fawizja.pixel.ranking.Ranking.vocationFrom(arg);
            if (filter == null) {
                warn(p, "Nie znam profesji \"" + arg.trim() + "\". Wpisz: /ranking, /ranking druid, paladin, mag albo mnich.", now);
                return;
            }
        }
        // gracze online: biezacy stan, nie z ostatniego zapisu
        for (ServerPlayer o : players.values()) ranking.put(rankEntry(o.session.character()));
        String title = filter == null ? "Ranking serwera" : "Ranking: " + filter.displayName();
        java.util.List<pl.fawizja.pixel.ranking.RankingView.Row> rows = new java.util.ArrayList<>();
        int i = 1;
        for (pl.fawizja.pixel.ranking.Ranking.Entry e : ranking.top(pl.fawizja.pixel.ranking.Ranking.SHOWN, filter)) {
            rows.add(new pl.fawizja.pixel.ranking.RankingView.Row(i++, e.name(), e.vocation().displayName(), e.level()));
        }
        pl.fawizja.pixel.ranking.RankingView view = new pl.fawizja.pixel.ranking.RankingView(title,
                ranking.position(p.name(), filter), ranking.size(filter), rows);
        String[] f = view.encode();
        String[] msg = new String[f.length + 1];
        msg[0] = "RANK";
        System.arraycopy(f, 0, msg, 1, f.length);
        p.client.send(msg);
    }

    /** FRIEND LIST | ADD imie | REMOVE imie; po kazdej zmianie klient dostaje liste (FRIENDS). */
    private void friendCommand(ServerPlayer p, String[] f, long now) {
        if (f.length < 2) return;
        pl.fawizja.pixel.friends.FriendList list = p.session.character().friends();
        switch (f[1]) {
            case "ADD": {
                if (f.length < 3) return;
                String name = knownName(f[2].trim());
                pl.fawizja.pixel.friends.FriendList.Result r = name == null
                        ? pl.fawizja.pixel.friends.FriendList.Result.UNKNOWN : list.add(p.name(), name);
                if (r != pl.fawizja.pixel.friends.FriendList.Result.OK) {
                    warn(p, r.message, now);
                    return;
                }
                info(p, name + " dodany do znajomych.", now);
                break;
            }
            case "REMOVE": {
                if (f.length < 3) return;
                pl.fawizja.pixel.friends.FriendList.Result r = list.remove(f[2]);
                if (r != pl.fawizja.pixel.friends.FriendList.Result.OK) {
                    warn(p, r.message, now);
                    return;
                }
                info(p, f[2].trim() + " usunięty ze znajomych.", now);
                break;
            }
            case "LIST": break;
            default: return;
        }
        sendFriends(p);
    }

    /** Imie postaci tak, jak je zapisano (online albo w rankingu), null gdy jej nie ma. */
    private String knownName(String name) {
        ServerPlayer o = find(name);
        if (o != null) return o.name();
        pl.fawizja.pixel.ranking.Ranking.Entry e = ranking.get(name);
        return e == null ? null : e.name();
    }

    private void sendFriends(ServerPlayer p) {
        java.util.List<pl.fawizja.pixel.friends.FriendsView.Row> rows = new java.util.ArrayList<>();
        for (String n : p.session.character().friends().names()) {
            ServerPlayer o = find(n);
            if (o != null) {
                PlayerCharacter c = o.session.character();
                rows.add(new pl.fawizja.pixel.friends.FriendsView.Row(c.name(), true, c.vocation().displayName(), c.level()));
            } else {
                pl.fawizja.pixel.ranking.Ranking.Entry e = ranking.get(n);
                rows.add(new pl.fawizja.pixel.friends.FriendsView.Row(n, false,
                        e == null ? "-" : e.vocation().displayName(), e == null ? 0 : e.level()));
            }
        }
        String[] f = new pl.fawizja.pixel.friends.FriendsView(rows).encode();
        String[] msg = new String[f.length + 1];
        msg[0] = "FRIENDS";
        System.arraycopy(f, 0, msg, 1, f.length);
        p.client.send(msg);
    }

    /** Powiadomienie znajomych o wejsciu / wyjsciu gracza. */
    private void tellFriends(ServerPlayer p, String text, long now) {
        for (ServerPlayer o : players.values()) {
            if (o != p && o.session.character().friends().contains(p.name())) {
                o.session.log().add(now, EventLog.Kind.SYSTEM, "Znajomy " + p.name() + " " + text);
            }
        }
    }

    private void saveMail() {
        try {
            File tmp = new File(mailFile.getParentFile(), "mail.txt.tmp");
            java.nio.file.Files.write(tmp.toPath(), mail.encode().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            java.nio.file.Files.move(tmp.toPath(), mailFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            LOG.warning("mail save failed: " + e.getMessage());
        }
    }

    /** MAIL SEND imie tekst: przesylka z plecaka do depozytu innej postaci (takze offline), tylko przy bankierze. */
    private void mailCommand(ServerPlayer p, String[] f, long now) {
        if (f.length < 4 || !f[1].equals("SEND")) return;
        if (!atBanker(p, now)) return;
        String to = knownName(f[2].trim());
        if (to == null) {
            warn(p, "Nie ma takiej postaci.", now);
            return;
        }
        if (to.equalsIgnoreCase(p.name())) {
            warn(p, "Własne rzeczy odłóż do depozytu u bankiera.", now);
            return;
        }
        pl.fawizja.pixel.trade.PlayerTrade.Parsed what = pl.fawizja.pixel.trade.PlayerTrade.parse(f[3], items);
        if (what == null || what.itemId() < 0 || pl.fawizja.pixel.economy.Currency.isCoin(what.itemId())) {
            warn(p, "Nie wiem, co wysłać. Złoto przekaż przez /handel albo rynek.", now);
            return;
        }
        PlayerCharacter pc = p.session.character();
        int count = (int) what.count();
        String label = count + "x " + items.get(what.itemId()).name();
        if (pc.backpack().count(what.itemId()) < count) {
            warn(p, "Nie masz w plecaku " + label + ".", now);
            return;
        }
        if (!mail.canAccept(to)) {
            warn(p, to + " ma za dużo nieodebranych przesyłek.", now);
            return;
        }
        if (!pc.backpack().remove(what.itemId(), count).ok()) return;
        mail.add(new pl.fawizja.pixel.mail.Mailbox.Parcel(to, p.name(), what.itemId(), count));
        ServerPlayer r = find(to);
        if (r != null) deliverMail(r, now);
        saveMail();
        save(p);
        info(p, "Wysłano " + label + " do " + to + (r == null ? " (odbierze po wejściu do gry)." : "."), now);
    }

    /** Dostarcza czekajace przesylki do depozytu odbiorcy (ile sie zmiesci). */
    private void deliverMail(ServerPlayer r, long now) {
        List<pl.fawizja.pixel.mail.Mailbox.Parcel> parcels = mail.take(r.name());
        if (parcels.isEmpty()) return;
        PlayerCharacter pc = r.session.character();
        int delivered = 0;
        for (pl.fawizja.pixel.mail.Mailbox.Parcel parcel : parcels) {
            if (pc.depot().canAdd(parcel.itemId(), parcel.count())
                    && pc.depot().add(new pl.fawizja.pixel.item.ItemStack(parcel.itemId(), parcel.count())).ok()) {
                delivered++;
                r.session.log().add(now, EventLog.Kind.SYSTEM, "Poczta: " + parcel.from() + " przysyła "
                        + parcel.count() + "x " + items.get(parcel.itemId()).name() + " - czeka w depozycie u bankiera.");
            } else {
                mail.add(parcel);
            }
        }
        if (delivered < parcels.size()) {
            r.session.log().add(now, EventLog.Kind.WARNING, "Poczta: depozyt jest pełny, " + (parcels.size() - delivered)
                    + " przesyłek czeka. Zrób miejsce i wejdź ponownie do gry.");
        }
        if (delivered > 0) save(r);
        saveMail();
    }

    /** Zapis rynku: plik tymczasowy i podmiana. */
    private void saveMarket() {
        try {
            File tmp = new File(marketFile.getParentFile(), "market.txt.tmp");
            java.nio.file.Files.write(tmp.toPath(), market.encode().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            java.nio.file.Files.move(tmp.toPath(), marketFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            LOG.warning("market save failed: " + e.getMessage());
        }
    }

    private boolean atBanker(ServerPlayer p, long now) {
        pl.fawizja.pixel.npc.Npc n = p.session.npcNearby();
        if (n != null && n.role() == pl.fawizja.pixel.npc.NpcRole.BANKER) return true;
        warn(p, "Rynek obsługuje bankier - podejdź do niego.", now);
        return false;
    }

    /** MARKET LIST [szukaj] | SELL "5 wilcze futro 300" | BUY id | CANCEL id. */
    private void marketCommand(ServerPlayer p, String[] f, long now) {
        if (f.length < 2) return;
        PlayerCharacter pc = p.session.character();
        String arg = f.length > 2 ? f[2].trim() : "";
        switch (f[1]) {
            case "LIST":
                sendMarket(p, arg);
                return;
            case "SELL": {
                if (!atBanker(p, now)) return;
                int sp = arg.lastIndexOf(' ');
                String priceText = sp < 0 ? "" : arg.substring(sp + 1).toLowerCase(java.util.Locale.ROOT).replace("gp", "");
                long price;
                try {
                    price = Long.parseLong(priceText);
                } catch (NumberFormatException e) {
                    warn(p, "Wpisz: /rynek wystaw 5 wilcze futro 300 (ilość, przedmiot, cena za wszystko w gp).", now);
                    return;
                }
                pl.fawizja.pixel.trade.PlayerTrade.Parsed what = pl.fawizja.pixel.trade.PlayerTrade.parse(arg.substring(0, sp), items);
                if (what == null || what.itemId() < 0 || pl.fawizja.pixel.economy.Currency.isCoin(what.itemId())) {
                    warn(p, "Nie wiem, jaki przedmiot wystawić. Monet nie wystawia się na rynku.", now);
                    return;
                }
                int count = (int) what.count();
                if (pc.backpack().count(what.itemId()) < count) {
                    warn(p, "Nie masz w plecaku " + count + "x " + items.get(what.itemId()).name() + ".", now);
                    return;
                }
                pl.fawizja.pixel.market.Market.Result r = market.canList(p.name(), price);
                if (r != pl.fawizja.pixel.market.Market.Result.OK) {
                    warn(p, r.message, now);
                    return;
                }
                if (!pc.backpack().remove(what.itemId(), count).ok()) return;
                pl.fawizja.pixel.market.Market.Listing l = market.add(p.name(), what.itemId(), count, price);
                saveMarket();
                save(p);
                info(p, "Wystawiono na rynku: " + count + "x " + items.get(l.itemId()).name() + " za "
                        + String.format("%,d gp", price) + " (oferta #" + l.id() + ").", now);
                break;
            }
            case "BUY": {
                if (!atBanker(p, now)) return;
                int id = parseInt(arg);
                pl.fawizja.pixel.market.Market.Result r = market.checkBuy(id, p.name());
                if (r != pl.fawizja.pixel.market.Market.Result.OK) {
                    warn(p, r.message, now);
                    break;
                }
                pl.fawizja.pixel.market.Market.Listing l = market.get(id);
                if (!pc.backpack().canAdd(l.itemId(), l.count())) {
                    warn(p, "Brak miejsca w plecaku.", now);
                    return;
                }
                // najpierw konto, potem monety z plecaka
                boolean paid = pc.bank().debit(l.price()).ok()
                        || pl.fawizja.pixel.economy.Money.pay(pc.backpack(), l.price()).ok();
                if (!paid) {
                    warn(p, "Za mało złota: oferta kosztuje " + String.format("%,d gp", l.price()) + " (konto albo plecak).", now);
                    return;
                }
                market.sold(id);
                pc.backpack().add(new pl.fawizja.pixel.item.ItemStack(l.itemId(), l.count()));
                long earned = l.price() - pl.fawizja.pixel.market.Market.fee(l.price());
                String what = l.count() + "x " + items.get(l.itemId()).name();
                ServerPlayer seller = find(l.seller());
                if (seller != null && seller.session.character().bank().credit(earned).ok()) {
                    seller.session.log().add(now, EventLog.Kind.SYSTEM, "Rynek: " + p.name() + " kupił " + what
                            + ". Na konto wpłynęło " + String.format("%,d gp", earned) + ".");
                    save(seller);
                } else {
                    market.addEarnings(l.seller(), earned);
                }
                saveMarket();
                save(p);
                info(p, "Kupiono " + what + " za " + String.format("%,d gp", l.price()) + ".", now);
                break;
            }
            case "CANCEL": {
                if (!atBanker(p, now)) return;
                int id = parseInt(arg);
                pl.fawizja.pixel.market.Market.Result r = market.checkCancel(id, p.name());
                if (r != pl.fawizja.pixel.market.Market.Result.OK) {
                    warn(p, r.message, now);
                    break;
                }
                pl.fawizja.pixel.market.Market.Listing l = market.get(id);
                if (!pc.backpack().canAdd(l.itemId(), l.count())) {
                    warn(p, "Brak miejsca w plecaku.", now);
                    return;
                }
                market.cancel(id);
                pc.backpack().add(new pl.fawizja.pixel.item.ItemStack(l.itemId(), l.count()));
                saveMarket();
                save(p);
                info(p, "Wycofano ofertę: " + l.count() + "x " + items.get(l.itemId()).name() + " wraca do plecaka.", now);
                break;
            }
            default:
                return;
        }
        sendMarket(p, "");
    }

    /** Okno rynku: najpierw wlasne oferty, potem najnowsze cudze (opcjonalnie tylko pasujace do nazwy). */
    private void sendMarket(ServerPlayer p, String search) {
        String q = search == null ? "" : plainLower(search.trim());
        java.util.List<pl.fawizja.pixel.market.MarketView.Row> rows = new java.util.ArrayList<>();
        for (pl.fawizja.pixel.market.Market.Listing l : market.bySeller(p.name())) rows.add(marketRow(l, true));
        int others = 0;
        for (pl.fawizja.pixel.market.Market.Listing l : market.newestFirst()) {
            if (l.seller().equalsIgnoreCase(p.name())) continue;
            if (!q.isEmpty() && !plainLower(items.get(l.itemId()).name()).contains(q) && !items.get(l.itemId()).key().contains(q.replace(' ', '_'))) continue;
            if (others++ >= 40) break;
            rows.add(marketRow(l, false));
        }
        String[] f = new pl.fawizja.pixel.market.MarketView(market.size(), q.isEmpty() ? null : search.trim(), rows).encode();
        String[] msg = new String[f.length + 1];
        msg[0] = "MARKET";
        System.arraycopy(f, 0, msg, 1, f.length);
        p.client.send(msg);
    }

    private pl.fawizja.pixel.market.MarketView.Row marketRow(pl.fawizja.pixel.market.Market.Listing l, boolean mine) {
        return new pl.fawizja.pixel.market.MarketView.Row(l.id(), items.get(l.itemId()).name(), l.count(), l.price(), l.seller(), mine);
    }

    private static String plainLower(String s) {
        String t = s.toLowerCase(java.util.Locale.ROOT).replace('ł', 'l');
        return java.text.Normalizer.normalize(t, java.text.Normalizer.Form.NFD).replaceAll("\\p{M}", "");
    }

    private String guildTag(String name) {
        pl.fawizja.pixel.guild.GuildRegistry.Guild g = guilds.guildOf(name);
        return g == null ? "-" : g.name();
    }

    /** Zapis gildii: plik tymczasowy i podmiana (bez polowicznego zapisu). */
    private void saveGuilds() {
        try {
            File tmp = new File(guildFile.getParentFile(), "guilds.txt.tmp");
            java.nio.file.Files.write(tmp.toPath(), guilds.encode().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            java.nio.file.Files.move(tmp.toPath(), guildFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            LOG.warning("guild save failed: " + e.getMessage());
        }
    }

    /** Wiadomosc do zalogowanych czlonkow gildii. */
    private void toGuild(pl.fawizja.pixel.guild.GuildRegistry.Guild g, String text, long now) {
        for (String n : g.members()) {
            ServerPlayer m = find(n);
            if (m != null) m.session.log().add(now, EventLog.Kind.SYSTEM, "[Gildia] " + text);
        }
    }

    /** GUILD CREATE nazwa | INVITE imie | ACCEPT nazwa | LEAVE | KICK imie | INFO. */
    private void guildCommand(ServerPlayer p, String[] f, long now) {
        if (f.length < 2) return;
        pl.fawizja.pixel.guild.GuildRegistry.Result r;
        pl.fawizja.pixel.guild.GuildRegistry.Guild mine = guilds.guildOf(p.name());
        switch (f[1]) {
            case "CREATE": {
                if (f.length < 3) return;
                r = guilds.canCreate(p.name(), f[2]);
                if (r == pl.fawizja.pixel.guild.GuildRegistry.Result.OK) {
                    if (!pl.fawizja.pixel.economy.Money.pay(p.session.character().backpack(),
                            pl.fawizja.pixel.guild.GuildRegistry.FOUNDING_COST).ok()) {
                        warn(p, "Założenie gildii kosztuje " + String.format("%,d gp", pl.fawizja.pixel.guild.GuildRegistry.FOUNDING_COST)
                                + " (monety w plecaku).", now);
                        return;
                    }
                    r = guilds.create(p.name(), f[2]);
                }
                if (r != pl.fawizja.pixel.guild.GuildRegistry.Result.OK) {
                    warn(p, r.message, now);
                    return;
                }
                saveGuilds();
                save(p);
                p.session.log().add(now, EventLog.Kind.SYSTEM, "Założono gildię " + f[2].trim() + ". Zapraszaj: /gildia zaproś Imię");
                LOG.info("guild created " + f[2].trim() + " by " + p.name());
                return;
            }
            case "INVITE": {
                if (f.length < 3) return;
                ServerPlayer other = find(f[2]);
                if (other == null) {
                    warn(p, "Gracz " + f[2] + " nie jest w grze.", now);
                    return;
                }
                r = guilds.invite(p.name(), other.name(), now);
                if (r != pl.fawizja.pixel.guild.GuildRegistry.Result.OK) {
                    warn(p, r.message, now);
                    return;
                }
                info(p, "Zaproszono " + other.name() + " do gildii " + mine.name() + ".", now);
                other.session.log().add(now, EventLog.Kind.SYSTEM, p.name() + " zaprasza cię do gildii " + mine.name()
                        + ": /gildia przyjmij " + mine.name());
                return;
            }
            case "ACCEPT": {
                if (f.length < 3) return;
                r = guilds.accept(p.name(), f[2], now);
                if (r != pl.fawizja.pixel.guild.GuildRegistry.Result.OK) {
                    warn(p, r.message, now);
                    return;
                }
                saveGuilds();
                toGuild(guilds.guildOf(p.name()), p.name() + " dołącza do gildii.", now);
                return;
            }
            case "LEAVE": {
                if (mine == null) {
                    warn(p, pl.fawizja.pixel.guild.GuildRegistry.Result.NOT_MEMBER.message, now);
                    return;
                }
                guilds.leave(p.name());
                saveGuilds();
                info(p, "Opuściłeś gildię " + mine.name() + ".", now);
                if (!mine.members().isEmpty()) toGuild(mine, p.name() + " opuszcza gildię. Lider: " + mine.leader(), now);
                return;
            }
            case "KICK": {
                if (f.length < 3) return;
                pl.fawizja.pixel.guild.GuildRegistry.Guild g = mine;
                String target = f[2].trim();
                r = guilds.kick(p.name(), target);
                if (r != pl.fawizja.pixel.guild.GuildRegistry.Result.OK) {
                    warn(p, r.message, now);
                    return;
                }
                saveGuilds();
                toGuild(g, target + " zostaje usunięty z gildii.", now);
                ServerPlayer t = find(target);
                if (t != null) warn(t, "Usunięto cię z gildii " + g.name() + ".", now);
                return;
            }
            default: // INFO
                if (mine == null) {
                    info(p, "Nie należysz do gildii. Załóż: /gildia załóż Nazwa (" + String.format("%,d gp",
                            pl.fawizja.pixel.guild.GuildRegistry.FOUNDING_COST) + ")", now);
                    return;
                }
                List<String> online = new ArrayList<>();
                for (String n : mine.members()) if (find(n) != null) online.add(n);
                info(p, "Gildia " + mine.name() + ": lider " + mine.leader() + ", członków " + mine.members().size()
                        + ", w grze: " + String.join(", ", online), now);
        }
    }

    Collection<ServerPlayer> players() { return Collections.unmodifiableCollection(players.values()); }

    ServerPlayer find(String name) {
        for (ServerPlayer p : players.values()) if (p.name().equalsIgnoreCase(name)) return p;
        return null;
    }

    /** Logowanie: wczytuje zapis postaci o tym imieniu albo tworzy nowa. Zwraca null z komunikatem bledu w client. */
    ServerPlayer join(ClientHandler client, String name, String vocation, String password, long now) {
        try {
            PlayerCharacter.validateName(name);
        } catch (IllegalArgumentException e) {
            client.send("ERR", "Niepoprawne imię: 3-20 liter, spacje i apostrof.");
            return null;
        }
        boolean newAccount = !saved(name) && !accounts.exists(name);
        if (newAccount) {
            try {
                Vocation.valueOf(vocation);
            } catch (IllegalArgumentException e) {
                client.send("ERR", "Nieznana profesja.");
                return null;
            }
            java.util.ArrayDeque<Long> made = newAccounts.computeIfAbsent(client.address(), k -> new java.util.ArrayDeque<>());
            while (!made.isEmpty() && now - made.peekFirst() > NEW_ACCOUNT_WINDOW_MS) made.pollFirst();
            if (made.size() >= maxNewAccountsPerAddress) {
                client.send("ERR", "Za dużo nowych kont z tego adresu - spróbuj jutro.");
                return null;
            }
        }
        switch (accounts.login(name, password, now)) {
            case WRONG_PASSWORD:
                client.send("ERR", "Złe hasło.");
                return null;
            case LOCKED:
                client.send("ERR", "Za dużo błędnych prób - spróbuj za minutę.");
                return null;
            case BAD_PASSWORD:
                client.send("ERR", "Hasło musi mieć " + AccountStore.MIN_PASSWORD + "-" + AccountStore.MAX_PASSWORD + " znaków.");
                return null;
            case CREATED:
                LOG.info("new account " + name);
                newAccounts.computeIfAbsent(client.address(), k -> new java.util.ArrayDeque<>()).addLast(now);
                break;
            default:
        }
        Long banned = moderation.bannedUntil(name, wallClock.getAsLong());
        if (banned != null) {
            client.send("ERR", "Konto zablokowane " + Moderation.describeUntil(banned) + ".");
            return null;
        }
        if (find(name) != null) {
            client.send("ERR", "Postać " + name + " jest już w grze.");
            return null;
        }
        PlayerCharacter pc;
        try {
            pc = load(name);
            if (pc == null) {
                pc = new CharacterFactory(config, items).create(name, Vocation.valueOf(vocation),
                        world.templeOf(config.startTown()));
            }
        } catch (IllegalArgumentException e) {
            client.send("ERR", "Nieprawidłowe dane postaci: " + e.getMessage());
            return null;
        }
        pc.setPosition(freeSpotNear(pc.position()));
        GameSession s = new GameSession(items, world, pc, monsters, ground, new Random(rng.nextLong()),
                config.startLevel(), spells, consumables, recipes);
        s.setTownServices(npcs, shops, quests);
        s.setChests(chests);
        ServerPlayer p = new ServerPlayer(nextId++, s, client, now,
                pvp.computeIfAbsent(pc.name().toLowerCase(java.util.Locale.ROOT), k -> loadPvp(k, now)));
        if (p.pvp.fightUntil() > now) s.lockProtectionZonesUntil(p.pvp.fightUntil());
        s.spells().setPlayerTargeting(new pl.fawizja.pixel.magic.SpellSystem.PlayerTargeting() {
            @Override
            public Position position() {
                ServerPlayer t = p.pvpTarget < 0 ? null : players.get(p.pvpTarget);
                return t == null ? null : t.session.character().position();
            }

            @Override
            public boolean strike(int amount, pl.fawizja.pixel.combat.DamageType type, long t) {
                ServerPlayer v = p.pvpTarget < 0 ? null : players.get(p.pvpTarget);
                if (v == null) return false;
                PvpRules.Outcome o = PvpRules.spellHit(p.session, p.pvp, v.session, v.pvp, amount, type, world, t, pvpRng);
                if (o == PvpRules.Outcome.KILLED) p.pvpTarget = -1;
                if (o == PvpRules.Outcome.DUEL_WON) duelOver(p, v, t);
                return o != PvpRules.Outcome.NOT_ALLOWED;
            }
        });
        s.combat().setExperienceSharer((type, t) -> shareKill(p, type, t));
        s.combat().setDeathListener(m -> invasionKill(p, m));
        s.combat().setRareDropListener((type, item, t) -> {
            String what = items.get(item.itemId()).name() + " (" + type.name() + ")";
            for (ServerPlayer o : players.values()) {
                if (o != p) o.session.log().add(t, EventLog.Kind.SYSTEM, p.name() + " zdobywa rzadki łup: " + what + "!");
            }
        });
        players.put(p.id, p);
        client.send("WELCOME", String.valueOf(p.id), String.valueOf(PixelServer.TICK_MS));
        broadcast(p, "LOG", "INFO", pc.name() + " wchodzi do gry.");
        tellFriends(p, "jest teraz w grze.", lastNow);
        List<String> on = new ArrayList<>();
        for (String n : pc.friends().names()) if (find(n) != null) on.add(find(n).name());
        if (!on.isEmpty()) s.log().add(lastNow, EventLog.Kind.SYSTEM, "Znajomi w grze: " + String.join(", ", on));
        if (invasion != null) {
            s.log().add(lastNow, EventLog.Kind.SYSTEM, "Trwa inwazja: " + invasion.plan().name() + " atakują "
                    + invasion.plan().town() + " (zostało " + invasion.remaining() + " wrogów).");
        }
        deliverMail(p, lastNow);
        long earned = market.takeEarnings(pc.name());
        if (earned > 0) {
            if (pc.bank().credit(earned).ok()) {
                s.log().add(lastNow, EventLog.Kind.SYSTEM, "Rynek: pod Twoją nieobecność sprzedano przedmioty. Na konto wpłynęło "
                        + String.format("%,d gp", earned) + ".");
                save(p);
            } else {
                market.addEarnings(pc.name(), earned);
            }
            saveMarket();
        }
        LOG.info("join " + pc.name() + " (" + players.size() + " online)");
        return p;
    }

    /**
     * Doswiadczenie za potwora zabitego przez p dzielone w druzynie ({@link pl.fawizja.pixel.party.PartyRules}).
     * false = brak warunkow (zabojca dostaje doswiadczenie sam).
     */
    boolean shareKill(ServerPlayer killer, pl.fawizja.pixel.monster.MonsterType type, long now) {
        List<String> names = parties.members(killer.name());
        if (names.size() < 2) return false;
        Position at = killer.session.character().position();
        List<ServerPlayer> eligible = new ArrayList<>();
        List<Integer> levels = new ArrayList<>();
        for (String n : names) {
            ServerPlayer m = find(n);
            if (m == null || m.session.character().isDead()) continue;
            if (!pl.fawizja.pixel.party.PartyRules.inShareRange(at, m.session.character().position())) continue;
            eligible.add(m);
            levels.add(m.session.character().level());
        }
        if (eligible.size() < 2 || !pl.fawizja.pixel.party.PartyRules.levelsCompatible(levels)) return false;
        for (ServerPlayer m : eligible) {
            long solo = m.session.combat().killExperience(type, m.session.character().level());
            long share = pl.fawizja.pixel.party.PartyRules.share(solo, eligible.size());
            m.session.combat().awardExperience(share, (m == killer ? "Pokonano: " : "Drużyna pokonała: ") + type.name(), now);
        }
        return true;
    }

    /** Obsluga polecen druzyny: PARTY INVITE|ACCEPT imie, PARTY LEAVE. */
    private void partyCommand(ServerPlayer p, String[] f, long now) {
        if (f.length < 2) return;
        String sub = f[1];
        if (sub.equals("LEAVE")) {
            if (parties.members(p.name()).isEmpty()) {
                p.session.log().add(now, EventLog.Kind.WARNING, pl.fawizja.pixel.party.PartyManager.Result.NOT_IN_PARTY.message);
                return;
            }
            partyLeft(p, now);
            p.session.log().add(now, EventLog.Kind.INFO, "Opuściłeś drużynę.");
            return;
        }
        if (f.length < 3) return;
        ServerPlayer other = find(f[2]);
        if (other == null) {
            p.session.log().add(now, EventLog.Kind.WARNING, "Gracz " + f[2] + " nie jest w grze.");
            return;
        }
        if (sub.equals("INVITE")) {
            pl.fawizja.pixel.party.PartyManager.Result r = parties.invite(p.name(), other.name(), now);
            if (r != pl.fawizja.pixel.party.PartyManager.Result.OK) {
                p.session.log().add(now, EventLog.Kind.WARNING, r.message);
                return;
            }
            p.session.log().add(now, EventLog.Kind.INFO, "Zaproszono " + other.name() + " do drużyny.");
            other.session.log().add(now, EventLog.Kind.SYSTEM, p.name() + " zaprasza cię do drużyny: /przyjmij " + p.name());
        } else if (sub.equals("ACCEPT")) {
            pl.fawizja.pixel.party.PartyManager.Result r = parties.accept(p.name(), other.name(), now);
            if (r != pl.fawizja.pixel.party.PartyManager.Result.OK) {
                p.session.log().add(now, EventLog.Kind.WARNING, r.message);
                return;
            }
            for (String n : parties.members(p.name())) {
                ServerPlayer m = find(n);
                if (m == null) continue;
                m.session.log().add(now, EventLog.Kind.SYSTEM, p.name() + " dołącza do drużyny.");
            }
            sendParty(parties.members(p.name()));
        }
    }

    private static String lower(String name) { return name.toLowerCase(java.util.Locale.ROOT); }

    /** Wyzwania na pojedynek: wyzywajacy (male litery) -> {wyzwany, czas}. */
    private final Map<String, Object[]> duelInvites = new java.util.HashMap<>();
    static final long DUEL_INVITE_MS = 60_000L;

    /** DUEL REQUEST imie (wyzwanie albo przyjecie wyzwania) | DUEL SURRENDER. */
    private void duelCommand(ServerPlayer p, String[] f, long now) {
        String me = p.name().toLowerCase(java.util.Locale.ROOT);
        if (f.length >= 2 && f[1].equals("SURRENDER")) {
            ServerPlayer o = p.pvp.duelPartner() == null ? null : find(p.pvp.duelPartner());
            if (o == null) {
                warn(p, "Nie walczysz w pojedynku.", now);
                return;
            }
            PvpRules.endDuel(p.pvp, o.pvp);
            info(p, "Poddajesz pojedynek z " + o.name() + ".", now);
            info(o, p.name() + " poddaje się - wygrywasz pojedynek!", now);
            duelOver(o, p, now);
            return;
        }
        if (f.length < 3 || !f[1].equals("REQUEST")) {
            info(p, "Pojedynek: /pojedynek Imię (wyzwanie albo przyjęcie wyzwania), /pojedynek poddaj.", now);
            return;
        }
        ServerPlayer t = find(f[2]);
        if (t == null) {
            warn(p, "Nie ma w grze gracza " + f[2] + ".", now);
            return;
        }
        if (t == p) {
            warn(p, "Nie możesz walczyć sam ze sobą.", now);
            return;
        }
        if (p.pvp.duelPartner() != null || t.pvp.duelPartner() != null) {
            warn(p, (p.pvp.duelPartner() != null ? "Już walczysz" : t.name() + " już walczy") + " w pojedynku.", now);
            return;
        }
        if (p.session.character().position().distanceTo(t.session.character().position()) > 8
                || p.session.character().position().z() != t.session.character().position().z()) {
            warn(p, t.name() + " jest za daleko - podejdź bliżej.", now);
            return;
        }
        String them = t.name().toLowerCase(java.util.Locale.ROOT);
        Object[] theirs = duelInvites.get(them);
        if (theirs != null && theirs[0].equals(me) && now - (Long) theirs[1] <= DUEL_INVITE_MS) {
            duelInvites.remove(them);
            duelInvites.remove(me);
            PvpRules.startDuel(p.name(), p.pvp, t.name(), t.pvp, now);
            String rules = " Do pierwszej krwi (przegrany zostaje z 1 HP), bez czaszek, najwyżej 5 min. Wyjdźcie ze strefy ochronnej i zaatakujcie się.";
            p.session.log().add(now, EventLog.Kind.SYSTEM, "Pojedynek z " + t.name() + "!" + rules);
            t.session.log().add(now, EventLog.Kind.SYSTEM, "Pojedynek z " + p.name() + "!" + rules);
            return;
        }
        duelInvites.put(me, new Object[] {them, now});
        info(p, "Wyzywasz " + t.name() + " na pojedynek (czeka minutę).", now);
        info(t, p.name() + " wyzywa cię na pojedynek. Przyjmij: /pojedynek " + p.name(), now);
    }

    /** Wyjscie z gry w trakcie pojedynku: walkower dla przeciwnika. */
    private void leftDuel(ServerPlayer p) {
        duelInvites.remove(p.name().toLowerCase(java.util.Locale.ROOT));
        String partner = p.pvp.duelPartner();
        if (partner == null) return;
        ServerPlayer o = find(partner);
        if (o == null || o == p) {
            PvpRules.endDuel(p.pvp, p.pvp);
            return;
        }
        PvpRules.endDuel(p.pvp, o.pvp);
        info(o, p.name() + " opuścił grę - wygrywasz pojedynek walkowerem.", lastNow);
        duelOver(o, p, lastNow);
    }

    /** Koniec pojedynku: obie strony przestaja sie atakowac (dalsza walka bylaby zwyklym PvP z czaszka). */
    private void duelOver(ServerPlayer winner, ServerPlayer loser, long now) {
        if (winner.pvpTarget == loser.id) winner.pvpTarget = -1;
        if (loser.pvpTarget == winner.id) loser.pvpTarget = -1;
        for (ServerPlayer o : players.values()) {
            if (o != winner && o != loser && o.session.character().position().distanceTo(winner.session.character().position()) <= Protocol.VIEW_RADIUS) {
                info(o, winner.name() + " wygrywa pojedynek z " + loser.name() + ".", now);
            }
        }
    }

    /** Remis po czasie albo gdy ktos zginie od potwora w trakcie pojedynku. */
    private void updateDuels(long now) {
        for (ServerPlayer p : players.values()) {
            String partner = p.pvp.duelPartner();
            if (partner == null) continue;
            ServerPlayer o = find(partner);
            if (o == null) {
                PvpRules.endDuel(p.pvp, p.pvp);
                continue;
            }
            boolean dead = p.session.character().isDead() || o.session.character().isDead();
            if (now >= p.pvp.duelUntil() || dead) {
                PvpRules.endDuel(p.pvp, o.pvp);
                String why = dead ? "Pojedynek przerwany." : "Czas minął - pojedynek kończy się remisem.";
                info(p, why, now);
                info(o, why, now);
                if (p.pvpTarget == o.id) p.pvpTarget = -1;
                if (o.pvpTarget == p.id) o.pvpTarget = -1;
            }
        }
    }

    private void info(ServerPlayer p, String text, long now) { p.session.log().add(now, EventLog.Kind.INFO, text); }

    private void warn(ServerPlayer p, String text, long now) { p.session.log().add(now, EventLog.Kind.WARNING, text); }

    /** Handel miedzy graczami: TRADE REQUEST imie | OFFER tekst | ACCEPT | CANCEL. */
    private void tradeCommand(ServerPlayer p, String[] f, long now) {
        if (f.length < 2) return;
        PlayerTrade open = trades.get(lower(p.name()));
        if (open != null && tradeBroken(open, now)) {
            cancelTrade(open, "Wymiana anulowana (za daleko albo upłynął czas).", now);
            open = null;
        }
        switch (f[1]) {
            case "REQUEST": {
                if (f.length < 3) return;
                ServerPlayer other = find(f[2]);
                if (other == null || other == p) {
                    warn(p, other == p ? "Nie możesz handlować z samym sobą." : "Gracz " + f[2] + " nie jest w grze.", now);
                    return;
                }
                if (open != null) {
                    warn(p, "Najpierw zakończ obecną wymianę (/anuluj).", now);
                    return;
                }
                PlayerTrade theirs = trades.get(lower(other.name()));
                if (theirs != null && !tradeBroken(theirs, now)) {
                    warn(p, other.name() + " już z kimś handluje.", now);
                    return;
                }
                if (!PlayerTrade.closeEnough(p.session.character().position(), other.session.character().position())) {
                    warn(p, PlayerTrade.Result.TOO_FAR.message, now);
                    return;
                }
                Map<String, Long> mine = tradeRequests.get(lower(p.name()));
                Long asked = mine == null ? null : mine.get(lower(other.name()));
                if (asked != null && now <= asked) {
                    mine.remove(lower(other.name()));
                    PlayerTrade t = new PlayerTrade(other.name(), p.name(), now);
                    trades.put(lower(p.name()), t);
                    trades.put(lower(other.name()), t);
                    String how = "Dodaj ofertę: /oferuj 5 mikstura zdrowia albo /oferuj 1000 gp; potem /akceptuj (albo /anuluj).";
                    info(p, "Wymiana z " + other.name() + " otwarta. " + how, now);
                    info(other, "Wymiana z " + p.name() + " otwarta. " + how, now);
                    sendTradeState(t);
                } else {
                    tradeRequests.computeIfAbsent(lower(other.name()), k -> new java.util.HashMap<>())
                            .put(lower(p.name()), now + TRADE_REQUEST_MS);
                    info(p, "Zaproponowano handel graczowi " + other.name() + ".", now);
                    other.session.log().add(now, EventLog.Kind.SYSTEM, p.name() + " chce handlować: /handel " + p.name());
                }
                return;
            }
            case "CANCEL":
                if (open == null) {
                    warn(p, "Nie masz otwartej wymiany.", now);
                    return;
                }
                cancelTrade(open, p.name() + " anuluje wymianę.", now);
                return;
            default:
        }
        if (open == null) {
            warn(p, "Nie masz otwartej wymiany. Zacznij od /handel Imię.", now);
            return;
        }
        ServerPlayer partner = find(open.partnerOf(p.name()));
        if (partner == null) {
            cancelTrade(open, "Wymiana anulowana - drugi gracz wyszedł.", now);
            return;
        }
        if (f[1].equals("OFFER") && f.length >= 3) {
            PlayerTrade.Result r = open.offer(p.name(), p.session.character().backpack(), items, f[2], now);
            if (r != PlayerTrade.Result.OK) {
                warn(p, r.message, now);
                return;
            }
            String status = "Oferta " + p.name() + ": " + open.offerOf(p.name()).describe(items)
                    + " | Oferta " + partner.name() + ": " + open.offerOf(partner.name()).describe(items) + ". /akceptuj";
            info(p, status, now);
            info(partner, status, now);
            sendTradeState(open);
        } else if (f[1].equals("ACCEPT")) {
            PlayerTrade.Result r = open.accept(p.name());
            if (r == PlayerTrade.Result.WAITING) {
                info(p, r.message, now);
                sendTradeState(open);
                partner.session.log().add(now, EventLog.Kind.SYSTEM, p.name() + " akceptuje wymianę: "
                        + open.offerOf(p.name()).describe(items) + " za " + open.offerOf(partner.name()).describe(items)
                        + ". /akceptuj albo /anuluj");
                return;
            }
            if (r != PlayerTrade.Result.DONE) {
                warn(p, r.message, now);
                return;
            }
            ServerPlayer first = find(open.first());
            ServerPlayer second = find(open.second());
            PlayerTrade.Result done = open.execute(first.session.character().backpack(), second.session.character().backpack());
            trades.remove(lower(first.name()));
            trades.remove(lower(second.name()));
            first.client.send("TS", "-");
            second.client.send("TS", "-");
            for (ServerPlayer x : new ServerPlayer[] {first, second}) {
                if (done == PlayerTrade.Result.DONE) info(x, "Wymiana zakończona: oddano " + open.offerOf(x.name()).describe(items)
                        + ", otrzymano " + open.offerOf(open.partnerOf(x.name())).describe(items) + ".", now);
                else warn(x, done.message, now);
            }
            if (done == PlayerTrade.Result.DONE) {
                LOG.info("trade " + first.name() + " <-> " + second.name() + ": " + open.offerOf(first.name()).describe(items)
                        + " / " + open.offerOf(second.name()).describe(items));
                save(first);
                save(second);
            }
        }
    }

    /** Wylogowanie przerywa wymiane i kasuje prosby (przed usunieciem z listy graczy). */
    private void leftTrade(ServerPlayer p) {
        tradeRequests.remove(lower(p.name()));
        PlayerTrade t = trades.get(lower(p.name()));
        if (t != null) cancelTrade(t, "Wymiana anulowana - " + p.name() + " wychodzi z gry.", lastNow);
    }

    private boolean tradeBroken(PlayerTrade t, long now) {
        ServerPlayer x = find(t.first()), y = find(t.second());
        return x == null || y == null || t.expired(now)
                || !PlayerTrade.closeEnough(x.session.character().position(), y.session.character().position());
    }

    private void cancelTrade(PlayerTrade t, String why, long now) {
        trades.remove(lower(t.first()));
        trades.remove(lower(t.second()));
        for (String n : new String[] {t.first(), t.second()}) {
            ServerPlayer x = find(n);
            if (x == null) continue;
            warn(x, why, now);
            x.client.send("TS", "-");
        }
    }

    /** Stan wymiany dla okna handlu u obu graczy. */
    private void sendTradeState(PlayerTrade t) {
        for (String n : new String[] {t.first(), t.second()}) {
            ServerPlayer x = find(n);
            if (x == null) continue;
            String other = t.partnerOf(n);
            pl.fawizja.pixel.trade.TradeView v = new pl.fawizja.pixel.trade.TradeView(other,
                    t.offerOf(n).describe(items), t.offerOf(other).describe(items), t.accepted(n), t.accepted(other));
            String[] f = v.encode();
            x.client.send("TS", f[0], f[1], f[2], f[3], f[4]);
        }
    }

    /** Wyjscie z druzyny (polecenie, wylogowanie, usuniecie konta) z powiadomieniem pozostalych. */
    private void partyLeft(ServerPlayer p, long now) {
        List<String> rest = parties.leave(p.name());
        if (rest.isEmpty()) return;
        p.client.send("PARTY", "-");
        for (String n : rest) {
            ServerPlayer m = find(n);
            if (m != null) m.session.log().add(now, EventLog.Kind.INFO, p.name() + " opuszcza drużynę.");
        }
        sendParty(rest);
    }

    /** Lista czlonkow do kazdego z nich ("-" gdy druzyna sie rozpadla). */
    private void sendParty(List<String> names) {
        for (String n : names) {
            ServerPlayer m = find(n);
            if (m == null) continue;
            List<String> now = parties.members(n);
            m.client.send("PARTY", now.isEmpty() ? "-" : String.join(",", now));
        }
    }

    boolean sameParty(ServerPlayer a, ServerPlayer b) { return parties.sameParty(a.name(), b.name()); }

    /** Wolne pole najblizej p (bez innego gracza i przejscia), aby gracze nie stali jeden na drugim. */
    Position freeSpotNear(Position p) {
        for (int r = 0; r <= 3; r++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dx = -r; dx <= r; dx++) {
                    if (Math.max(Math.abs(dx), Math.abs(dy)) != r) continue;
                    Position c = p.translate(dx, dy);
                    if (r > 0 && (!world.isWalkable(c) || world.transitionAt(c) != null)) continue;
                    if (!occupied(c)) return c;
                }
            }
        }
        return p;
    }

    private boolean occupied(Position c) {
        for (ServerPlayer o : players.values()) if (o.session.character().position().equals(c)) return true;
        return false;
    }

    private PlayerCharacter load(String name) {
        String slot = "player_" + name.toLowerCase(java.util.Locale.ROOT);
        if (!saves.exists(slot)) return null;
        try {
            return saves.read(slot, codec::decode).value();
        } catch (IOException e) {
            throw new IllegalArgumentException("zapis uszkodzony");
        }
    }

    void save(ServerPlayer p) {
        String slot = "player_" + p.name().toLowerCase(java.util.Locale.ROOT);
        try {
            saves.write(slot, codec.encode(p.session.character()));
            ranking.put(rankEntry(p.session.character()));
            saves.write("pvp_" + p.name().toLowerCase(java.util.Locale.ROOT), p.pvp.encode(lastNow, System.currentTimeMillis()));
        } catch (IOException e) {
            LOG.warning("save failed for " + p.name() + ": " + e.getMessage());
        }
    }

    /** Po usunieciu konta: gracz znika ze swiata bez zapisu, a jego pliki sa kasowane. */
    private void deleteCharacter(ServerPlayer p) {
        String key = p.name().toLowerCase(java.util.Locale.ROOT);
        partyLeft(p, lastNow);
        leftTrade(p);
        leftDuel(p);
        if (guilds.guildOf(p.name()) != null) {
            guilds.forget(p.name());
            saveGuilds();
        }
        market.forget(p.name());
        saveMarket();
        mail.forget(p.name());
        saveMail();
        players.remove(p.id);
        pvp.remove(key);
        ranking.remove(p.name());
        try {
            saves.delete("player_" + key);
            saves.delete("pvp_" + key);
        } catch (IOException e) {
            LOG.warning("delete failed for " + p.name() + ": " + e.getMessage());
        }
        p.client.player = null;
        p.client.send("ERR", "Konto usunięte.");
        p.client.close();
        broadcast(p, "LOG", "INFO", p.name() + " opuszcza grę.");
        LOG.info("account deleted " + p.name());
    }

    boolean saved(String name) { return saves.exists("player_" + name.toLowerCase(java.util.Locale.ROOT)); }

    void leave(ServerPlayer p) {
        if (!players.containsKey(p.id)) return;
        save(p); // najpierw zapis, potem usuniecie z listy graczy
        partyLeft(p, lastNow);
        leftTrade(p);
        leftDuel(p);
        players.remove(p.id);
        tellFriends(p, "wyszedł z gry.", lastNow);
        broadcast(p, "LOG", "INFO", p.name() + " wychodzi z gry.");
        LOG.info("leave " + p.name() + " (" + players.size() + " online)");
    }

    /** Komenda od klienta (juz zalogowanego). */
    void command(ServerPlayer p, String[] f, long now) {
        switch (f[0]) {
            case "MOVE": p.move = f.length > 1 && !f[1].equals("-") ? parseDir(f[1]) : null; break;
            case "ACT": if (f.length > 1) Actions.apply(p.session, f[1], now); break;
            case "ATTACK":
                if (f.length > 1) attackPlayer(p, parseInt(f[1]), now);
                break;
            case "TARGET":
                p.pvpTarget = -1;
                if (f.length > 1) {
                    int id = parseInt(f[1]);
                    for (Monster m : monsters.monsters()) {
                        if (m.id() == id) p.session.targetAt(m.position());
                    }
                }
                break;
            case "PASSWD":
                if (f.length < 3) break;
                switch (accounts.changePassword(p.name(), f[1], f[2], now)) {
                    case OK: p.session.log().add(now, EventLog.Kind.SYSTEM, "Hasło zmienione."); break;
                    case LOCKED: p.session.log().add(now, EventLog.Kind.WARNING, "Za dużo błędnych prób - spróbuj za minutę."); break;
                    case BAD_PASSWORD:
                        p.session.log().add(now, EventLog.Kind.WARNING, "Nowe hasło musi mieć " + AccountStore.MIN_PASSWORD
                                + "-" + AccountStore.MAX_PASSWORD + " znaków.");
                        break;
                    default: p.session.log().add(now, EventLog.Kind.WARNING, "Złe obecne hasło.");
                }
                break;
            case "DELETE":
                if (f.length < 2) break;
                if (accounts.delete(p.name(), f[1], now) != AccountStore.Result.OK) {
                    p.session.log().add(now, EventLog.Kind.WARNING, "Konto nie zostało usunięte - złe hasło.");
                    break;
                }
                deleteCharacter(p);
                break;
            case "PARTY": partyCommand(p, f, now); break;
            case "TRADE": tradeCommand(p, f, now); break;
            case "GUILD": guildCommand(p, f, now); break;
            case "FRIEND": friendCommand(p, f, now); break;
            case "MARKET": marketCommand(p, f, now); break;
            case "MAIL": mailCommand(p, f, now); break;
            case "DUEL": duelCommand(p, f, now); break;
            case "DEATH": {
                // podsumowanie ostatniej smierci tego gracza
                pl.fawizja.pixel.combat.DeathRecap.Report r = p.session.combat().lastDeath();
                List<String> out = new ArrayList<>();
                out.add("DEATHLOG");
                if (r != null) java.util.Collections.addAll(out, pl.fawizja.pixel.combat.DeathRecap.encode(pl.fawizja.pixel.combat.DeathRecap.rows(r)));
                p.client.send(out.toArray(new String[0]));
                break;
            }
            case "BOSS":
                List<String> rows = new ArrayList<>();
                rows.add("BOSS");
                for (MonsterManager.BossStatus b : monsters.bossReport(now)) rows.add(String.join(";", MonsterManager.row(b, now)));
                p.client.send(rows.toArray(new String[0]));
                break;
            case "RANK":
                if (chatAllowed(p, now)) sendRanking(p, f.length > 1 ? f[1] : null, now);
                break;
            case "PM": {
                if (f.length < 3 || f[2].isBlank()) break;
                ServerPlayer to = find(f[1].trim());
                if (to == null) {
                    warn(p, "Gracz " + f[1].trim() + " nie jest w grze.", now);
                    break;
                }
                if (!chatAllowed(p, now)) break;
                String text = f[2].length() > 200 ? f[2].substring(0, 200) : f[2];
                to.client.send("CHAT", "[od " + p.name() + "]", text);
                p.client.send("CHAT", "[do " + to.name() + "]", text);
                break;
            }
            case "ADMIN":
                if (!moderation.isAdmin(p.name())) {
                    warn(p, "Brak uprawnień administratora.", now);
                    break;
                }
                String[] cmd = new String[f.length - 1];
                System.arraycopy(f, 1, cmd, 0, cmd.length);
                String answer = moderate(cmd, p.name(), now);
                info(p, answer, now);
                LOG.info("admin " + p.name() + ": " + String.join(" ", cmd) + " -> " + answer);
                break;
            case "GCHAT":
                if (f.length > 1 && !f[1].isBlank()) {
                    pl.fawizja.pixel.guild.GuildRegistry.Guild g = guilds.guildOf(p.name());
                    if (g == null) {
                        warn(p, pl.fawizja.pixel.guild.GuildRegistry.Result.NOT_MEMBER.message, now);
                        break;
                    }
                    if (!chatAllowed(p, now)) break;
                    String text = f[1].length() > 200 ? f[1].substring(0, 200) : f[1];
                    for (String n : g.members()) {
                        ServerPlayer m = find(n);
                        if (m != null) m.client.send("CHAT", "[" + g.name() + "] " + p.name(), text);
                    }
                }
                break;
            case "PCHAT":
                if (f.length > 1 && !f[1].isBlank()) {
                    List<String> team = parties.members(p.name());
                    if (team.isEmpty()) {
                        p.session.log().add(now, EventLog.Kind.WARNING, pl.fawizja.pixel.party.PartyManager.Result.NOT_IN_PARTY.message);
                        break;
                    }
                    if (!chatAllowed(p, now)) break;
                    String text = f[1].length() > 200 ? f[1].substring(0, 200) : f[1];
                    for (String n : team) {
                        ServerPlayer m = find(n);
                        if (m != null) m.client.send("CHAT", "[Drużyna] " + p.name(), text);
                    }
                }
                break;
            case "CHAT":
                if (f.length > 1 && !f[1].isBlank()) {
                    if (!chatAllowed(p, now)) break;
                    String text = f[1].length() > 200 ? f[1].substring(0, 200) : f[1];
                    for (ServerPlayer o : players.values()) o.client.send("CHAT", p.name(), text);
                }
                break;
            default:
        }
    }

    /**
     * Polecenie moderacji (z czatu administratora albo z konsoli serwera): SAY tekst, KICK imie,
     * BAN imie godziny (0 = na stale), UNBAN imie, MUTE imie minuty (0 = zdejmij), ADMIN imie (tylko konsola),
     * WHO. Zwraca odpowiedz dla wydajacego polecenie.
     */
    String moderate(String[] c, String by, long now) {
        if (c.length == 0) return "Polecenia: SAY, KICK, BAN, UNBAN, MUTE, WHO, INVASION";
        long wall = wallClock.getAsLong();
        try {
            switch (c[0].toUpperCase(java.util.Locale.ROOT)) {
                case "SAY": {
                    if (c.length < 2 || c[1].isBlank()) return "Podaj tekst ogłoszenia.";
                    for (ServerPlayer o : players.values()) o.client.send("CHAT", "[Ogłoszenie]", c[1]);
                    return "Wysłano ogłoszenie do " + players.size() + " graczy.";
                }
                case "KICK": {
                    ServerPlayer t = c.length < 2 ? null : find(c[1].trim());
                    if (t == null) return "Nie ma takiego gracza w grze.";
                    kick(t, "Wyrzucono z serwera (" + by + ").");
                    return "Wyrzucono " + t.name() + ".";
                }
                case "BAN": {
                    if (c.length < 2 || !saved(c[1].trim()) && find(c[1].trim()) == null) return "Nie ma takiej postaci.";
                    long hours = c.length > 2 ? Long.parseLong(c[2].trim()) : 0;
                    moderation.ban(c[1].trim(), hours, wall);
                    ServerPlayer t = find(c[1].trim());
                    String until = Moderation.describeUntil(moderation.bannedUntil(c[1].trim(), wall));
                    if (t != null) kick(t, "Konto zablokowane " + until + ".");
                    return "Zbanowano " + c[1].trim() + " " + until + ".";
                }
                case "UNBAN":
                    if (c.length < 2) return "Podaj imię.";
                    return moderation.unban(c[1].trim()) ? "Zdjęto bana: " + c[1].trim() + "." : "Ta postać nie ma bana.";
                case "MUTE": {
                    if (c.length < 2) return "Podaj imię.";
                    long minutes = c.length > 2 ? Long.parseLong(c[2].trim()) : 60;
                    moderation.mute(c[1].trim(), minutes, wall);
                    ServerPlayer t = find(c[1].trim());
                    if (t != null) warn(t, minutes > 0 ? "Wyciszono cię na " + minutes + " min." : "Wyciszenie zdjęte.", now);
                    return minutes > 0 ? "Wyciszono " + c[1].trim() + " na " + minutes + " min." : "Zdjęto wyciszenie: " + c[1].trim() + ".";
                }
                case "ADMIN":
                    if (!by.equals("konsola")) return "Administratorów dodaje tylko konsola serwera albo plik admins.txt.";
                    if (c.length < 2) return "Podaj imię.";
                    moderation.addAdmin(c[1].trim());
                    return "Dodano administratora: " + c[1].trim() + ".";
                case "INVASION":
                    return startInvasion(c.length > 1 ? c[1] : null, now);
                case "WHO": {
                    List<String> names = new ArrayList<>();
                    for (ServerPlayer o : players.values()) names.add(o.name() + " (" + o.session.character().level() + ")");
                    return players.size() + " w grze: " + String.join(", ", names);
                }
                default:
                    return "Nieznane polecenie. Polecenia: SAY, KICK, BAN, UNBAN, MUTE, WHO, INVASION";
            }
        } catch (NumberFormatException e) {
            return "Czas musi być liczbą.";
        } catch (IOException e) {
            return "Błąd zapisu: " + e.getMessage();
        }
    }

    /** Wyrzucenie: zapis postaci, komunikat i rozlaczenie. */
    void kick(ServerPlayer t, String reason) {
        leave(t);
        t.client.player = null;
        t.client.send("ERR", reason);
        t.client.close();
    }

    /** Limit spamu wspolny dla czatu ogolnego i druzyny. */
    private boolean chatAllowed(ServerPlayer p, long now) {
        if (moderation.isMuted(p.name(), wallClock.getAsLong())) {
            p.session.log().add(now, EventLog.Kind.WARNING, "Jesteś wyciszony przez administratora.");
            return false;
        }
        while (!p.chatTimes.isEmpty() && now - p.chatTimes.peekFirst() > CHAT_WINDOW_MS) p.chatTimes.pollFirst();
        if (p.chatTimes.size() >= CHAT_MAX) {
            p.session.log().add(now, EventLog.Kind.WARNING, "Zwolnij - za dużo wiadomości na czacie.");
            return false;
        }
        p.chatTimes.addLast(now);
        return true;
    }

    /** Wybor gracza do ataku: zasady sprawdzane od razu, by gracz dostal czytelna odpowiedz. */
    void attackPlayer(ServerPlayer p, int id, long now) {
        ServerPlayer t = players.get(id);
        if (t == null) {
            p.pvpTarget = -1;
            return;
        }
        if (sameParty(p, t) && !p.pvp.duelling(t.name())) {
            p.session.log().add(now, EventLog.Kind.WARNING, "Nie atakujesz członka własnej drużyny.");
            p.pvpTarget = -1;
            return;
        }
        PvpRules.Verdict v = PvpRules.check(p.session.character(), t.session.character(), t.pvp, world, now);
        if (v != PvpRules.Verdict.OK) {
            p.session.log().add(now, EventLog.Kind.WARNING, v.message);
            p.pvpTarget = -1;
            return;
        }
        p.pvpTarget = t.id;
        p.session.combat().setTarget(null);
    }

    private static Direction parseDir(String s) {
        try {
            return Direction.valueOf(s);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static int parseInt(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private void broadcast(ServerPlayer except, String... msg) {
        for (ServerPlayer o : players.values()) if (o != except) o.client.send(msg);
    }

    /** Krok swiata: sesje graczy, potem potwory raz dla wszystkich, potem walka. */
    /** Czaszki i nieusprawiedliwione zabojstwa z dysku (przetrwaja restart serwera). */
    private PvpRules.Status loadPvp(String key, long now) {
        try {
            if (saves.exists("pvp_" + key)) {
                PvpRules.Status st = saves.read("pvp_" + key,
                        text -> PvpRules.Status.decode(text, now, System.currentTimeMillis())).value();
                if (st != null) return st;
            }
        } catch (IOException | RuntimeException e) {
            LOG.warning("pvp status of " + key + " unreadable: " + e.getMessage());
        }
        return new PvpRules.Status();
    }

    void tick(long now) {
        lastNow = now;
        for (ServerPlayer p : players.values()) p.session.update(now, p.move);
        updateInvasion(now);
        updateDuels(now);
        if (players.isEmpty()) return;
        List<MonsterManager.Target> targets = new ArrayList<>();
        Map<MonsterManager.Target, ServerPlayer> byTarget = new LinkedHashMap<>();
        for (ServerPlayer p : players.values()) {
            targets.add(p.session.playerTarget());
            byTarget.put(p.session.playerTarget(), p);
        }
        monsters.update(now, targets, new MonsterManager.AttackHandler() {
            @Override
            public void monsterAttacks(Monster m, long t) {
                ServerPlayer p = byTarget.get(m.target());
                if (p != null) p.session.combat().monsterAttacks(m, t);
            }

            @Override
            public void monsterHeals(Monster healer, Monster healed, int amount, long t) {
                for (ServerPlayer p : players.values()) {
                    if (p.session.character().position().distanceTo(healed.position()) <= Protocol.VIEW_RADIUS) {
                        p.session.combat().monsterHeals(healer, healed, amount, t);
                    }
                }
            }
        });
        for (ServerPlayer p : players.values()) {
            if (p.pvpTarget < 0) continue;
            ServerPlayer t = players.get(p.pvpTarget);
            if (t == null || (sameParty(p, t) && !p.pvp.duelling(t.name()))) {
                p.pvpTarget = -1;
                continue;
            }
            PvpRules.Verdict v = PvpRules.check(p.session.character(), t.session.character(), t.pvp, world, now);
            if (v != PvpRules.Verdict.OK) {
                p.session.log().add(now, EventLog.Kind.INFO, "Koniec walki z " + t.name() + ": " + v.message);
                p.pvpTarget = -1;
                continue;
            }
            PvpRules.Outcome o = PvpRules.attack(p.session, p.pvp, t.session, t.pvp, world, now, pvpRng);
            if (o == PvpRules.Outcome.KILLED) p.pvpTarget = -1;
            if (o == PvpRules.Outcome.DUEL_WON) duelOver(p, t, now);
        }
        for (ServerPlayer p : players.values()) {
            if (now - p.lastSaveMs >= SAVE_EVERY_MS) {
                save(p);
                p.lastSaveMs = now;
            }
        }
    }

    /** Ogloszenie wydarzenia dla wszystkich graczy w grze. */
    private void announce(String text, long now) {
        for (ServerPlayer o : players.values()) o.session.log().add(now, EventLog.Kind.SYSTEM, text);
    }

    /** Harmonogram inwazji: co pixel.invasionMinutes (domyslnie 120) minut, gdy ktos jest w grze. */
    private void updateInvasion(long now) {
        if (invasion != null) {
            if (invasion.over(now)) finishInvasion(now);
            return;
        }
        if (nextInvasionAt == 0) nextInvasionAt = now + invasionEveryMs;
        if (now >= nextInvasionAt && !players.isEmpty()) {
            startInvasion(null, now);
            nextInvasionAt = now + invasionEveryMs;
        }
    }

    /** Start inwazji (town = null: kolejne miasto z listy). Zwraca opis dla administratora. */
    String startInvasion(String town, long now) {
        if (invasion != null) return "Trwa już inwazja na " + invasion.plan().town() + ".";
        List<pl.fawizja.pixel.event.Invasion.Plan> plans = pl.fawizja.pixel.event.Invasion.PLANS;
        pl.fawizja.pixel.event.Invasion.Plan plan = null;
        if (town != null && !town.isBlank()) {
            for (pl.fawizja.pixel.event.Invasion.Plan pl0 : plans) if (pl0.town().equalsIgnoreCase(town.trim())) plan = pl0;
            if (plan == null) return "Nie ma planu inwazji dla miasta " + town.trim() + ".";
        } else {
            if (nextInvasionPlan < 0) nextInvasionPlan = rng.nextInt(plans.size());
            plan = plans.get(nextInvasionPlan);
            nextInvasionPlan = (nextInvasionPlan + 1) % plans.size();
        }
        pl.fawizja.pixel.world.Town t = world.town(plan.town());
        Position center = t == null ? null : pl.fawizja.pixel.event.Invasion.gatherPoint(world, t, rng);
        if (center == null) return "Nie znaleziono miejsca na inwazję przy " + plan.town() + ".";
        List<Monster> spawned = new ArrayList<>(monsters.spawnEvent(plan.monster(), center, 6, plan.count(), now));
        List<Monster> leader = monsters.spawnEvent(plan.leader(), center, 2, 1, now);
        spawned.addAll(leader);
        if (spawned.isEmpty()) return "Potwory nie zmieściły się przy " + plan.town() + ".";
        List<Integer> ids = new ArrayList<>();
        for (Monster m : spawned) ids.add(m.id());
        invasion = new pl.fawizja.pixel.event.Invasion(plan, center, ids, leader.isEmpty() ? -1 : leader.get(0).id(), now,
                pl.fawizja.pixel.event.Invasion.DURATION_MS);
        String leaderName = leader.isEmpty() ? "" : ", herszt: " + leader.get(0).type().name();
        announce("Inwazja! " + plan.name() + " atakują " + plan.town() + " " + pl.fawizja.pixel.event.Invasion.direction(t.temple(), center)
                + " (" + spawned.size() + " wrogów" + leaderName + "). Za każdego pokonanego "
                + pl.fawizja.pixel.event.Invasion.REWARD_PER_KILL + " gp na konto po obronie.", now);
        LOG.info("invasion " + plan.town() + " at " + center + " (" + spawned.size() + ")");
        return "Inwazja na " + plan.town() + " rozpoczęta (" + spawned.size() + " wrogów przy " + center.x() + "," + center.y() + ").";
    }

    private void invasionKill(ServerPlayer p, Monster m) {
        if (invasion == null || !invasion.onKill(p.name(), m.id())) return;
        long now = lastNow;
        if (p.name().equals(invasion.leaderSlayer()) && invasion.remaining() > 0 && m.type().key().equals(invasion.plan().leader())) {
            announce(p.name() + " pokonuje herszta najeźdźców! Zostało " + invasion.remaining() + " wrogów.", now);
        } else if (invasion.remaining() == invasion.total() / 2) {
            announce("Inwazja na " + invasion.plan().town() + ": połowa wrogów pokonana, zostało " + invasion.remaining() + ".", now);
        }
    }

    private void finishInvasion(long now) {
        pl.fawizja.pixel.event.Invasion inv = invasion;
        invasion = null;
        monsters.clearEvent(inv.aliveIds());
        List<Map.Entry<String, Integer>> top = inv.ranking();
        String best = top.isEmpty() ? "" : " Najwięcej pokonał(a): " + top.get(0).getKey() + " (" + top.get(0).getValue() + ").";
        if (inv.defended()) announce(inv.plan().town() + " obronione!" + best + " Nagrody trafiły na konta.", now);
        else announce("Najeźdźcy odchodzą od " + inv.plan().town() + " (zostało " + inv.remaining() + ")." + best
                + (top.isEmpty() ? "" : " Za pokonanych są nagrody."), now);
        for (Map.Entry<String, Integer> e : top) {
            ServerPlayer o = find(e.getKey());
            long reward = inv.reward(e.getKey());
            if (o == null || reward <= 0) continue;
            if (o.session.character().bank().credit(reward).ok()) {
                o.session.log().add(now, EventLog.Kind.SYSTEM, "Nagroda za obronę " + inv.plan().town() + ": "
                        + String.format("%,d gp", reward) + " na koncie (" + e.getValue() + " pokonanych).");
                save(o);
            }
        }
        LOG.info("invasion over " + inv.plan().town() + (inv.defended() ? " defended" : " expired"));
    }

    /** Trwajaca inwazja (testy). */
    pl.fawizja.pixel.event.Invasion invasion() { return invasion; }

    /** Wysyla kazdemu graczowi nowe wpisy dziennika, zapis postaci (gdy sie zmienil) i klatke stanu. */
    void sendState(long now) {
        for (ServerPlayer p : players.values()) {
            EventLog log = p.session.log();
            long fresh = log.total() - p.logSent;
            if (fresh > 0) {
                for (EventLog.Entry e : log.latest((int) Math.min(fresh, log.size()))) {
                    p.client.send("LOG", e.kind().name(), e.text());
                }
                p.logSent = log.total();
            }
            long freshEv = p.session.combat().eventTotal() - p.eventsSent;
            if (freshEv > 0) {
                for (CombatEvent e : p.session.combat().latestEvents((int) Math.min(freshEv, 64))) {
                    Position ep = e.position();
                    if (e.from() != null || e.effect() != null) {
                        // efekt graficzny: pocisk (skad) i rodzaj obrazen
                        Position fp = e.from() != null ? e.from() : ep;
                        p.client.send("EV", e.kind().name(), String.valueOf(ep.x()), String.valueOf(ep.y()),
                                String.valueOf(ep.z()), String.valueOf(e.amount()), String.valueOf(fp.x()),
                                String.valueOf(fp.y()), String.valueOf(fp.z()), e.effect() == null ? "-" : e.effect());
                    } else {
                        p.client.send("EV", e.kind().name(), String.valueOf(ep.x()), String.valueOf(ep.y()),
                                String.valueOf(ep.z()), String.valueOf(e.amount()));
                    }
                }
                p.eventsSent = p.session.combat().eventTotal();
            }
            if (now - p.lastSelfMs >= SELF_EVERY_MS) {
                String self = codec.encode(p.session.character());
                if (!self.equals(p.lastSelf)) {
                    p.client.send("SELF", self);
                    p.lastSelf = self;
                }
                p.lastSelfMs = now;
            }
            Position me = p.session.character().position();
            StringBuilder frame = new StringBuilder();
            frame.append(Protocol.line("ME", String.valueOf(me.x()), String.valueOf(me.y()), String.valueOf(me.z()),
                    p.session.facing().name(), p.pvp.skull(now).name())).append('\n');
            frame.append(Protocol.line("TP", p.pvpTarget < 0 ? "-" : String.valueOf(p.pvpTarget))).append('\n');
            Monster tg = p.session.combat().target();
            frame.append(Protocol.line("TG", tg == null || tg.isDead() ? "-" : String.valueOf(tg.id()))).append('\n');
            for (ServerPlayer o : players.values()) {
                Position op = o.session.character().position();
                if (o == p || op.z() != me.z() || op.distanceTo(me) > Protocol.VIEW_RADIUS) continue;
                PlayerCharacter oc = o.session.character();
                frame.append(Protocol.line("PL", String.valueOf(o.id), oc.name(), oc.vocation().name(),
                        String.valueOf(op.x()), String.valueOf(op.y()), String.valueOf(op.z()),
                        String.valueOf(100 * oc.health() / Math.max(1, oc.maxHealth())), o.pvp.skull(now).name(),
                        guildTag(oc.name()), oc.isMounted() ? "M" : "-")).append('\n');
            }
            for (Monster m : monsters.near(me, Protocol.VIEW_RADIUS)) {
                if (m.isDead() || m.position().z() != me.z()) continue;
                Position mp = m.position();
                frame.append(Protocol.line("MO", String.valueOf(m.id()), m.type().key(), String.valueOf(mp.x()),
                        String.valueOf(mp.y()), String.valueOf(mp.z()), String.valueOf(Math.round(100 * m.healthFraction())),
                        m.type().isElite() ? "E" : "-"))
                        .append('\n');
            }
            ground.forEachNear(me, Protocol.VIEW_RADIUS, (gp, st) -> frame.append(Protocol.line("GI",
                    String.valueOf(gp.x()), String.valueOf(gp.y()), String.valueOf(gp.z()),
                    String.valueOf(st.itemId()), String.valueOf(st.count()))).append('\n'));
            ground.forEachCorpseNear(me, Protocol.VIEW_RADIUS, (cp, c) -> frame.append(Protocol.line("CO",
                    String.valueOf(cp.x()), String.valueOf(cp.y()), String.valueOf(cp.z()), c.monsterKey(),
                    c.hasLoot() ? "1" : "0")).append('\n'));
            String body = frame.toString();
            if (body.equals(p.lastFrameBody)) {
                // nic sie nie zmienilo: sam znacznik czasu zamiast calej klatki (oszczednosc transferu na telefonie)
                p.client.send("KEEP", String.valueOf(now));
                p.framesKept++;
            } else {
                p.client.sendRaw(Protocol.line("FRAME", String.valueOf(now)) + "\n" + body + "END");
                p.lastFrameBody = body;
            }
        }
    }

    void saveAll() {
        for (ServerPlayer p : players.values()) save(p);
    }
}
