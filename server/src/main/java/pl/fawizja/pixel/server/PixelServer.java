package pl.fawizja.pixel.server;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.logging.Logger;
import pl.fawizja.pixel.net.Protocol;

/**
 * Serwer gry: watek akceptujacy polaczenia i jedna petla swiata (TICK_MS), ktora jako jedyna
 * zmienia stan gry. Uruchomienie: {@code ./gradlew :server:run --args="7171 saves"}
 * (polaczenia szyfrowane TLS; {@code --no-tls} jako trzeci argument tylko do testow w sieci lokalnej).
 */
public final class PixelServer implements AutoCloseable {
    private static final Logger LOG = Logger.getLogger("pixel.server");
    public static final int TICK_MS = 50;
    /** Klatka stanu co drugi krok (10 razy na sekunde). */
    public static final int FRAME_EVERY_TICKS = 2;

    private final ServerSocket socket;
    private final ServerWorld world;
    private final ConcurrentLinkedQueue<Object[]> inbox = new ConcurrentLinkedQueue<>();
    private final long startNs = System.nanoTime();
    private volatile boolean running = true;
    private Thread loop;
    private Thread acceptor;

    private final String fingerprint;
    /** Limity polaczen: lacznie i z jednego adresu (np. jeden dom - kilku graczy). */
    static final int MAX_CONNECTIONS = 300;
    static final int MAX_PER_ADDRESS = 8;
    private final java.util.Map<java.net.InetAddress, java.util.concurrent.atomic.AtomicInteger> perAddress =
            new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.concurrent.atomic.AtomicInteger total = new java.util.concurrent.atomic.AtomicInteger();

    /** Serwer z TLS (domyslnie). */
    public PixelServer(int port, File saveDir) throws IOException {
        this(port, saveDir, true);
    }

    public PixelServer(int port, File saveDir, boolean tls) throws IOException {
        world = new ServerWorld(saveDir);
        if (tls) {
            TlsSetup t = TlsSetup.load(saveDir);
            socket = t.factory.createServerSocket();
            fingerprint = t.fingerprint;
        } else {
            socket = new ServerSocket();
            fingerprint = null;
        }
        socket.setReuseAddress(true);
        socket.bind(new InetSocketAddress(port));
    }

    public int port() { return socket.getLocalPort(); }

    /** Odcisk SHA-256 certyfikatu TLS albo null (bez szyfrowania). */
    public String fingerprint() { return fingerprint; }

    /** Czas swiata w ms od startu serwera (wspolny zegar dla wszystkich graczy). */
    long now() { return (System.nanoTime() - startNs) / 1_000_000L; }

    public void start() {
        acceptor = new Thread(this::acceptLoop, "pixel-accept");
        acceptor.setDaemon(true);
        acceptor.start();
        loop = new Thread(this::gameLoop, "pixel-world");
        loop.start();
        LOG.info("Pixel server listening on port " + port()
                + (fingerprint != null ? " (TLS, odcisk certyfikatu " + fingerprint + ")" : " (BEZ szyfrowania)"));
    }

    private void acceptLoop() {
        while (running) {
            try {
                Socket s = socket.accept();
                java.net.InetAddress addr = s.getInetAddress();
                java.util.concurrent.atomic.AtomicInteger n = perAddress.computeIfAbsent(addr,
                        k -> new java.util.concurrent.atomic.AtomicInteger());
                if (total.get() >= MAX_CONNECTIONS || n.get() >= MAX_PER_ADDRESS) {
                    LOG.warning("odrzucono połączenie z " + addr + " (limit)");
                    s.close();
                    continue;
                }
                ClientHandler c = new ClientHandler(s, this);
                n.incrementAndGet();
                total.incrementAndGet();
                c.onClose(() -> {
                    n.decrementAndGet();
                    total.decrementAndGet();
                });
                c.start();
            } catch (IOException e) {
                if (running) LOG.warning("accept: " + e.getMessage());
            }
        }
    }

    void enqueue(ClientHandler c, String[] fields) { inbox.add(new Object[] {c, fields}); }

    /** Testy: zadanie wykonane w watku petli swiata. */
    void enqueueTest(Runnable r) { inbox.add(new Object[] {r}); }

    private void gameLoop() {
        long tick = 0;
        long next = System.nanoTime();
        while (running) {
            long now = now();
            Object[] msg;
            while ((msg = inbox.poll()) != null) {
                if (msg.length == 1) ((Runnable) msg[0]).run();
                else handle((ClientHandler) msg[0], (String[]) msg[1], now);
            }
            try {
                world.tick(now);
                if (++tick % FRAME_EVERY_TICKS == 0) world.sendState(now);
            } catch (RuntimeException e) {
                LOG.severe("tick failed: " + e);
            }
            next += TICK_MS * 1_000_000L;
            long sleep = (next - System.nanoTime()) / 1_000_000L;
            if (sleep > 0) {
                try {
                    Thread.sleep(sleep);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            } else {
                next = System.nanoTime(); // przeciazenie: nie nadrabiamy krokow lawina
            }
        }
        world.saveAll();
    }

    private void handle(ClientHandler c, String[] f, long now) {
        if (f[0].equals("BYE")) {
            if (c.player != null) world.leave(c.player);
            c.player = null;
            c.close();
            return;
        }
        if (c.player == null) {
            if (!f[0].equals("HELLO") || f.length < 2) {
                c.send("ERR", "Najpierw HELLO.");
                return;
            }
            if (!String.valueOf(Protocol.VERSION).equals(f[1])) {
                c.send("ERR", "Niezgodna wersja protokołu (serwer " + Protocol.VERSION + ").");
                return;
            }
            if (f.length < 5) {
                c.send("ERR", "Podaj imię, profesję i hasło.");
                return;
            }
            c.player = world.join(c, f[2], f[3], f[4], now);
            return;
        }
        world.command(c.player, f, now);
    }

    // dostep dla testow
    ServerWorld world() { return world; }

    @Override
    public void close() throws IOException {
        running = false;
        socket.close();
        if (loop != null) {
            try {
                loop.join(5000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    public static void main(String[] args) throws Exception {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : Protocol.DEFAULT_PORT;
        File saves = new File(args.length > 1 ? args[1] : "server-saves");
        boolean tls = !(args.length > 2 && args[2].equals("--no-tls"));
        PixelServer server = new PixelServer(port, saves, tls);
        new Backups(saves).startSchedule(); // kopia przy starcie i co 6 h, 7 najnowszych
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                server.close();
            } catch (IOException ignored) {
                // zamykanie
            }
        }));
        server.start();
        server.startConsole(System.in);
    }

    /**
     * Konsola operatora (stdin, np. {@code docker attach} albo terminal): say tekst, kick imie, ban imie godziny,
     * unban imie, mute imie minuty, admin imie, who, invasion [miasto], status, save, motd [tekst|-]. Polecenia wykonuje watek swiata.
     */
    void startConsole(java.io.InputStream in) {
        Thread t = new Thread(() -> {
            try (java.io.BufferedReader r = new java.io.BufferedReader(new java.io.InputStreamReader(in,
                    java.nio.charset.StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) {
                    String[] c = consoleCommand(line);
                    if (c == null) continue;
                    inbox.add(new Object[] {(Runnable) () -> LOG.info("> " + world.moderate(c, "konsola", now()))});
                }
            } catch (IOException ignored) {
                // brak konsoli (usluga w tle) - nic do zrobienia
            }
        }, "pixel-console");
        t.setDaemon(true);
        t.start();
    }

    /** "ban Imie Nazwisko 24" -> [BAN, Imie Nazwisko, 24]; "say tekst" -> [SAY, tekst]. */
    static String[] consoleCommand(String line) {
        String t = line == null ? "" : line.trim();
        if (t.isEmpty()) return null;
        int sp = t.indexOf(' ');
        String cmd = (sp < 0 ? t : t.substring(0, sp)).toUpperCase(java.util.Locale.ROOT);
        String rest = sp < 0 ? "" : t.substring(sp + 1).trim();
        if (cmd.equals("SAY") || cmd.equals("MOTD")) return rest.isEmpty() ? new String[] {cmd} : new String[] {cmd, rest};
        if ((cmd.equals("BAN") || cmd.equals("MUTE")) && !rest.isEmpty()) {
            String[] nn = pl.fawizja.pixel.net.ChatCommands.nameAndNumber(rest);
            return nn.length == 2 ? new String[] {cmd, nn[0], nn[1]} : new String[] {cmd, nn[0]};
        }
        return rest.isEmpty() ? new String[] {cmd} : new String[] {cmd, rest};
    }
}
