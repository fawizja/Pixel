package pl.fawizja.pixel.server;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import pl.fawizja.pixel.net.Protocol;

/** Polaczenie jednego klienta: watek odczytu przekazuje linie do petli serwera, zapis jest synchroniczny. */
final class ClientHandler {
    private final Socket socket;
    private final BufferedWriter out;
    private final PixelServer server;
    private volatile boolean closed;
    ServerPlayer player; // ustawiany i czytany tylko w watku petli

    ClientHandler(Socket socket, PixelServer server) throws IOException {
        this.socket = socket;
        this.server = server;
        socket.setTcpNoDelay(true);
        socket.setSoTimeout(0);
        out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
    }

    void start() {
        Thread t = new Thread(this::readLoop, "pixel-client-" + socket.getPort());
        t.setDaemon(true);
        t.start();
    }

    private void readLoop() {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while (!closed && (line = in.readLine()) != null) {
                if (line.length() > Protocol.MAX_LINE) break;
                String[] f = Protocol.parse(line);
                server.enqueue(this, f);
                if (f[0].equals("BYE")) break;
            }
        } catch (IOException e) {
            // klient zniknal
        } finally {
            server.enqueue(this, new String[] {"BYE"});
        }
    }

    void send(String... fields) { sendRaw(Protocol.line(fields)); }

    synchronized void sendRaw(String lines) {
        if (closed) return;
        try {
            out.write(lines);
            out.write('\n');
            out.flush();
        } catch (IOException e) {
            close();
        }
    }

    boolean isClosed() { return closed; }

    void close() {
        closed = true;
        try {
            socket.close();
        } catch (IOException ignored) {
            // zamykamy
        }
    }
}
