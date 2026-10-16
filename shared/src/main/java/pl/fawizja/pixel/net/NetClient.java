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
    public record Frame(long serverTime, Position self, String facing, int targetId, int playerTargetId, String skull,
                        List<RemoteEntity> players, List<RemoteEntity> monsters, List<GroundStack> ground,
                        List<CorpseMark> corpses) {}

    /** Cialo potwora widoczne w klatce (wyglad i czy jest w nim lup). */
    public record CorpseMark(Position position, String monsterKey, boolean hasLoot) {}

    /** Stos przedmiotow na ziemi widoczny w klatce. */
    public record GroundStack(Position position, int itemId, int count) {}

    private final Socket socket;
    private final BufferedWriter out;
    private final Thread reader;
    private volatile int playerId = -1;
    private volatile String error;
    private volatile String selfSave;
    private volatile Frame frame;
    private volatile boolean closed;
    private final ConcurrentLinkedQueue<String[]> messages = new ConcurrentLinkedQueue<>();

    /**
     * Decyzja o zaufaniu certyfikatowi serwera po odcisku SHA-256 (przypinanie jak w SSH:
     * pierwszy odcisk zapamietany, kolejne musza sie zgadzac).
     */
    public interface Pinning {
        boolean accept(String fingerprint);
    }

    private volatile String serverFingerprint;

    /** Polaczenie bez szyfrowania (testy, siec lokalna z serwerem uruchomionym z --no-tls). */
    public NetClient(String host, int port, int timeoutMs) throws IOException {
        this(host, port, timeoutMs, null);
    }

    /** Polaczenie TLS z przypietym certyfikatem, gdy pinning != null. */
    public NetClient(String host, int port, int timeoutMs, Pinning pinning) throws IOException {
        Socket raw = new Socket();
        raw.connect(new InetSocketAddress(host, port), timeoutMs);
        raw.setTcpNoDelay(true);
        if (pinning != null) {
            raw.setSoTimeout(timeoutMs);
            javax.net.ssl.SSLSocket ssl;
            try {
                javax.net.ssl.SSLContext ctx = javax.net.ssl.SSLContext.getInstance("TLS");
                ctx.init(null, new javax.net.ssl.TrustManager[] {pinningTrust(pinning)}, new java.security.SecureRandom());
                ssl = (javax.net.ssl.SSLSocket) ctx.getSocketFactory().createSocket(raw, host, port, true);
                ssl.startHandshake();
            } catch (java.security.GeneralSecurityException e) {
                raw.close();
                throw new IOException("TLS: " + e.getMessage(), e);
            } catch (IOException e) {
                raw.close();
                throw e;
            }
            ssl.setSoTimeout(0);
            socket = ssl;
        } else {
            socket = raw;
        }
        out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
        BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        reader = new Thread(() -> readLoop(in), "pixel-net-reader");
        reader.setDaemon(true);
        reader.start();
    }

    private javax.net.ssl.X509TrustManager pinningTrust(Pinning pinning) {
        return new javax.net.ssl.X509TrustManager() {
            @Override
            public void checkClientTrusted(java.security.cert.X509Certificate[] chain, String authType) {
                throw new UnsupportedOperationException();
            }

            @Override
            public void checkServerTrusted(java.security.cert.X509Certificate[] chain, String authType)
                    throws java.security.cert.CertificateException {
                if (chain == null || chain.length == 0) throw new java.security.cert.CertificateException("brak certyfikatu");
                String fp = fingerprint(chain[0].getEncoded());
                serverFingerprint = fp;
                if (!pinning.accept(fp)) throw new java.security.cert.CertificateException("odcisk certyfikatu się nie zgadza");
            }

            @Override
            public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                return new java.security.cert.X509Certificate[0];
            }
        };
    }

    /** Odcisk SHA-256 certyfikatu w postaci AB:CD:... */
    public static String fingerprint(byte[] encoded) {
        try {
            byte[] d = java.security.MessageDigest.getInstance("SHA-256").digest(encoded);
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < d.length; i++) {
                if (i > 0) sb.append(':');
                sb.append(String.format("%02X", d[i] & 0xff));
            }
            return sb.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Odcisk certyfikatu serwera z ostatniego polaczenia TLS albo null. */
    public String serverFingerprint() { return serverFingerprint; }

    private void readLoop(BufferedReader in) {
        long frameTime = 0;
        Position self = null;
        String facing = null;
        int targetId = -1;
        int playerTargetId = -1;
        String skull = "NONE";
        List<RemoteEntity> players = new ArrayList<>();
        List<RemoteEntity> monsters = new ArrayList<>();
        List<GroundStack> ground = new ArrayList<>();
        List<CorpseMark> corpses = new ArrayList<>();
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
                        ground = new ArrayList<>();
                        corpses = new ArrayList<>();
                        break;
                    case "ME":
                        self = pos(f, 1);
                        facing = f.length > 4 ? f[4] : null;
                        skull = f.length > 5 ? f[5] : "NONE";
                        break;
                    case "TG": targetId = f[1].equals("-") ? -1 : Integer.parseInt(f[1]); break;
                    case "TP": playerTargetId = f[1].equals("-") ? -1 : Integer.parseInt(f[1]); break;
                    case "PL":
                        players.add(new RemoteEntity("PL", Integer.parseInt(f[1]), f[3], f[2], pos(f, 4), Integer.parseInt(f[7]),
                                f.length > 8 ? f[8] : "NONE", f.length > 9 && !f[9].equals("-") ? f[9] : null,
                                f.length > 10 && f[10].equals("M")));
                        break;
                    case "MO":
                        // f[7] (od 0.54.0): "E" = odmiana elitarna
                        monsters.add(new RemoteEntity("MO", Integer.parseInt(f[1]), f[2], f[2], pos(f, 3), Integer.parseInt(f[6]),
                                f.length > 7 ? f[7] : "-", null));
                        break;
                    case "GI":
                        ground.add(new GroundStack(pos(f, 1), Integer.parseInt(f[4]), Integer.parseInt(f[5])));
                        break;
                    case "CO":
                        corpses.add(new CorpseMark(pos(f, 1), f[4], "1".equals(f[5])));
                        break;
                    case "KEEP": {
                        Frame last = frame;
                        if (last != null) {
                            frame = new Frame(Long.parseLong(f[1]), last.self(), last.facing(), last.targetId(),
                                    last.playerTargetId(), last.skull(), last.players(), last.monsters(), last.ground(),
                                    last.corpses());
                        }
                        break;
                    }
                    case "END":
                        frame = new Frame(frameTime, self, facing, targetId, playerTargetId, skull,
                                Collections.unmodifiableList(players), Collections.unmodifiableList(monsters),
                                Collections.unmodifiableList(ground), Collections.unmodifiableList(corpses));
                        break;
                    default: messages.add(f); // LOG, CHAT, EV
                }
            }
        } catch (IOException | RuntimeException e) {
            if (!closed) error = "Połączenie przerwane: " + e.getMessage();
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
            error = "Nie można wysłać: " + e.getMessage();
            closed = true;
        }
    }

    /** Logowanie (wersja 2: z haslem). Uwaga: polaczenie nie jest jeszcze szyfrowane. */
    public void hello(String name, String vocation, String password) {
        send("HELLO", String.valueOf(Protocol.VERSION), name, vocation, password);
    }

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
