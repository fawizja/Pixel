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
 * zmienia stan gry. Uruchomienie: {@code ./gradlew :server:run --args="7171 saves"}.
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

    public PixelServer(int port, File saveDir) throws IOException {
        world = new ServerWorld(saveDir);
        socket = new ServerSocket();
        socket.setReuseAddress(true);
        socket.bind(new InetSocketAddress(port));
    }

    public int port() { return socket.getLocalPort(); }

    /** Czas swiata w ms od startu serwera (wspolny zegar dla wszystkich graczy). */
    long now() { return (System.nanoTime() - startNs) / 1_000_000L; }

    public void start() {
        acceptor = new Thread(this::acceptLoop, "pixel-accept");
        acceptor.setDaemon(true);
        acceptor.start();
        loop = new Thread(this::gameLoop, "pixel-world");
        loop.start();
        LOG.info("Pixel server listening on port " + port());
    }

    private void acceptLoop() {
        while (running) {
            try {
                Socket s = socket.accept();
                new ClientHandler(s, this).start();
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
                c.send("ERR", "Niezgodna wersja protokolu (serwer " + Protocol.VERSION + ").");
                return;
            }
            if (f.length < 5) {
                c.send("ERR", "Podaj imie, profesje i haslo.");
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
        PixelServer server = new PixelServer(port, saves);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                server.close();
            } catch (IOException ignored) {
                // zamykanie
            }
        }));
        server.start();
    }
}
