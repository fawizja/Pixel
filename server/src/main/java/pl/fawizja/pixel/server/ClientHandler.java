package pl.fawizja.pixel.server;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.LinkedBlockingQueue;
import pl.fawizja.pixel.net.Protocol;

/**
 * Polaczenie jednego klienta. Watek odczytu przekazuje linie do petli serwera; wysylka idzie przez
 * kolejke i osobny watek, wiec wolny klient nie blokuje swiata. Ochrona: limit dlugosci linii,
 * limit wiadomosci na sekunde, czas na zalogowanie, limit zaleglosci wysylki.
 */
final class ClientHandler {
    /** Najdluzsza linia od klienta (czat ma max 200 znakow). */
    static final int MAX_CLIENT_LINE = 2048;
    /** Wiadomosci na sekunde (srednio) i maksymalny "zapas". */
    static final double RATE_PER_SECOND = 40;
    static final double RATE_BURST = 80;
    /** Czas na pierwsza wiadomosc (i uzgodnienie TLS). */
    static final int HELLO_TIMEOUT_MS = 15_000;
    /** Tyle niewyslanych linii = klient nie nadaza, rozlaczamy. */
    static final int MAX_PENDING = 400;

    private final Socket socket;
    private final BufferedWriter out;
    private final PixelServer server;
    private static final String POISON = new String("<koniec>");
    private final LinkedBlockingQueue<String> outbox = new LinkedBlockingQueue<>();
    private volatile boolean closed;
    private volatile Runnable onClose;
    ServerPlayer player; // ustawiany i czytany tylko w watku petli

    ClientHandler(Socket socket, PixelServer server) throws IOException {
        this.socket = socket;
        this.server = server;
        socket.setTcpNoDelay(true);
        socket.setSoTimeout(HELLO_TIMEOUT_MS);
        out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
    }

    void onClose(Runnable r) { onClose = r; }

    /** Adres klienta (limit nowych kont z jednego adresu). */
    java.net.InetAddress address() { return socket.getInetAddress(); }

    void start() {
        Thread t = new Thread(this::readLoop, "pixel-client-" + socket.getPort());
        t.setDaemon(true);
        t.start();
        Thread w = new Thread(this::writeLoop, "pixel-send-" + socket.getPort());
        w.setDaemon(true);
        w.start();
    }

    private void readLoop() {
        double tokens = RATE_BURST;
        long last = System.nanoTime();
        boolean first = true;
        try (Reader in = new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8)) {
            StringBuilder line = new StringBuilder();
            while (!closed) {
                String l = readLine(in, line);
                if (l == null) break;
                if (first) {
                    socket.setSoTimeout(0); // zalogowany gracz moze dlugo stac bez ruchu
                    first = false;
                }
                long now = System.nanoTime();
                tokens = Math.min(RATE_BURST, tokens + (now - last) / 1e9 * RATE_PER_SECOND);
                last = now;
                if (--tokens < 0) {
                    send("ERR", "Za dużo wiadomości - rozłączono.");
                    break;
                }
                String[] f = Protocol.parse(l);
                server.enqueue(this, f);
                if (f[0].equals("BYE")) break;
            }
        } catch (SocketTimeoutException e) {
            send("ERR", "Brak logowania - rozłączono.");
        } catch (IOException | RuntimeException e) {
            // klient zniknal albo przyslal smieci
        } finally {
            server.enqueue(this, new String[] {"BYE"});
        }
    }

    /** Linia bez znaku konca; null przy koncu strumienia albo zbyt dlugiej linii. */
    private static String readLine(Reader in, StringBuilder sb) throws IOException {
        sb.setLength(0);
        int c;
        while ((c = in.read()) != -1) {
            if (c == '\n') return sb.toString();
            if (c == '\r') continue;
            if (sb.length() >= MAX_CLIENT_LINE) return null;
            sb.append((char) c);
        }
        return null;
    }

    private void writeLoop() {
        try {
            while (true) {
                String s = outbox.take();
                if (s == POISON) break;
                out.write(s);
                out.write('\n');
                String more;
                boolean end = false;
                while ((more = outbox.poll()) != null) {
                    if (more == POISON) {
                        end = true;
                        break;
                    }
                    out.write(more);
                    out.write('\n');
                }
                out.flush();
                if (end) break;
            }
            out.flush();
        } catch (IOException | InterruptedException e) {
            // klient zniknal
        } finally {
            closeSocket();
        }
    }

    void send(String... fields) { sendRaw(Protocol.line(fields)); }

    /** Kolejkuje linie do wyslania (nie blokuje petli swiata). */
    void sendRaw(String lines) {
        if (closed) return;
        if (outbox.size() >= MAX_PENDING) {
            closed = true; // klient nie odbiera - nie trzymamy pamieci w nieskonczonosc
            outbox.clear();
            closeSocket();
            return;
        }
        outbox.add(lines);
    }

    boolean isClosed() { return closed; }

    /** Konczy polaczenie po wyslaniu tego, co juz jest w kolejce (np. komunikatu ERR). */
    void close() {
        if (closed) return;
        closed = true;
        outbox.add(POISON);
    }

    private void closeSocket() {
        try {
            socket.close();
        } catch (IOException ignored) {
            // zamykamy
        }
        Runnable r = onClose;
        onClose = null;
        if (r != null) r.run();
    }
}
