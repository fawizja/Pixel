package pl.fawizja.pixel.net;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import pl.fawizja.pixel.world.Position;

/**
 * Klient sieciowy (bez libGDX - dziala na Androidzie, desktopie i w testach). Watek odbiorczy
 * sklada klatki stanu; gra czyta najnowsza klatke i kolejke komunikatow bez blokowania.
 */
public final class NetClient implements AutoCloseable {

    /** Klatka stanu: czas serwera i widoczne byty. */
    public record Frame(long serverTime, List<RemoteEntity> players, List<RemoteEntity> monsters) {}

    private final Socket socket;
    private final BufferedWriter out;
    private final Thread reader;
    private volatile int playerId = -1;
    private volatile String error;
    private volatile String selfSave;
    private volatile Frame frame;
    private volatile boolean closed;
    private final ConcurrentLinkedQueue<String[]> messages = new ConcurrentLinkedQueue<>();

    public NetClient(String host, int port, int timeoutMs) throws IOException {
        socket = new Socket();
        socket.connect(new InetSocketAddress(host, port), timeoutMs);
        socket.setTcpNoDelay(true);
        out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
        BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        reader = new Thread(() -> readLoop(in), "pixel-net-reader");
        reader.setDaemon(true);
        reader.start();
    }

    private void readLoop(BufferedReader in) {
        long frameTime = 0;
        List<RemoteEntity> players = new ArrayList<>();
        List<RemoteEntity> monsters = new ArrayList<>();
        try {
            String line;
            while ((line = in.readLine()) != null) {
                String[] f = Protocol.parse(line);
                switch (f[0]) {
                    case "WELCOME": playerId = Integer.parseInt(f[1]); break;
                    case "ERR": error = f[1]; break;
                    case "SELF": selfSave = f[1]; break;
                    case "FRAME":
                        frameTime = Long.parseLong(f[1]);
                        players = new ArrayList<>();
                        monsters = new ArrayList<>();
                        break;
                    case "PL":
                        players.add(new RemoteEntity("PL", Integer.parseInt(f[1]), f[3], f[2], pos(f, 4), Integer.parseInt(f[7])));
                        break;
                    case "MO":
                        monsters.add(new RemoteEntity("MO", Integer.parseInt(f[1]), f[2], f[2], pos(f, 3), Integer.parseInt(f[6])));
                        break;
                    case "END":
                        frame = new Frame(frameTime, Collections.unmodifiableList(players), Collections.unmodifiableList(monsters));
                        break;
                    default: messages.add(f); // LOG, CHAT
                }
            }
        } catch (IOException | RuntimeException e) {
            if (!closed) error = "Polaczenie przerwane: " + e.getMessage();
        } finally {
            closed = true;
        }
    }

    private static Position pos(String[] f, int i) {
        return new Position(Integer.parseInt(f[i]), Integer.parseInt(f[i + 1]), Integer.parseInt(f[i + 2]));
    }

    public synchronized void send(String... fields) {
        if (closed) return;
        try {
            out.write(Protocol.line(fields));
            out.write('\n');
            out.flush();
        } catch (IOException e) {
            error = "Nie mozna wyslac: " + e.getMessage();
            closed = true;
        }
    }

    public void hello(String name, String vocation) { send("HELLO", String.valueOf(Protocol.VERSION), name, vocation); }

    public int playerId() { return playerId; }
    public String error() { return error; }
    public String selfSave() { return selfSave; }
    public Frame frame() { return frame; }
    public boolean isClosed() { return closed; }
    /** Nastepna wiadomosc LOG/CHAT albo null. */
    public String[] poll() { return messages.poll(); }

    @Override
    public void close() {
        if (!closed) send("BYE");
        closed = true;
        try {
            socket.close();
        } catch (IOException ignored) {
            // zamykamy
        }
    }
}
