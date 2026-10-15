package pl.fawizja.pixel.server;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.util.Base64;
import java.util.logging.Logger;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocketFactory;
import pl.fawizja.pixel.net.NetClient;

/**
 * Szyfrowanie polaczen (TLS). Przy pierwszym starcie serwer tworzy wlasny klucz i certyfikat
 * (keytool z JDK) w {@code <katalog zapisow>/tls/} - nic z tego nie trafia do repozytorium.
 * Klient przypina odcisk SHA-256 certyfikatu przy pierwszym polaczeniu (jak SSH).
 */
final class TlsSetup {
    private static final Logger LOG = Logger.getLogger("pixel.server");
    static final String ALIAS = "pixel";

    final SSLServerSocketFactory factory;
    final String fingerprint;

    private TlsSetup(SSLServerSocketFactory factory, String fingerprint) {
        this.factory = factory;
        this.fingerprint = fingerprint;
    }

    static TlsSetup load(File saveDir) throws IOException {
        File dir = new File(saveDir, "tls");
        File store = new File(dir, "server.p12");
        File passFile = new File(dir, "keystore.pass");
        if (!store.isFile()) generate(dir, store, passFile);
        char[] pass = new String(Files.readAllBytes(passFile.toPath()), StandardCharsets.UTF_8).trim().toCharArray();
        try (InputStream in = new FileInputStream(store)) {
            KeyStore ks = KeyStore.getInstance("PKCS12");
            ks.load(in, pass);
            KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            kmf.init(ks, pass);
            SSLContext ctx = SSLContext.getInstance("TLS");
            ctx.init(kmf.getKeyManagers(), null, new SecureRandom());
            Certificate cert = ks.getCertificate(ALIAS);
            return new TlsSetup(ctx.getServerSocketFactory(), NetClient.fingerprint(cert.getEncoded()));
        } catch (GeneralSecurityException e) {
            throw new IOException("TLS: " + e.getMessage(), e);
        }
    }

    private static void generate(File dir, File store, File passFile) throws IOException {
        Files.createDirectories(dir.toPath());
        byte[] raw = new byte[24];
        new SecureRandom().nextBytes(raw);
        // litera na poczatku: haslo zaczynajace sie od "-J" keytool bralby za opcje JVM (losowy blad)
        String pass = "p" + Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        Files.write(passFile.toPath(), pass.getBytes(StandardCharsets.UTF_8));
        passFile.setReadable(false, false);
        passFile.setReadable(true, true);
        String keytool = new File(new File(System.getProperty("java.home"), "bin"), "keytool").getPath();
        ProcessBuilder pb = new ProcessBuilder(keytool, "-genkeypair", "-alias", ALIAS, "-keyalg", "RSA",
                "-keysize", "2048", "-validity", "3650", "-dname", "CN=Pixel game server",
                "-storetype", "PKCS12", "-keystore", store.getPath(), "-storepass", pass, "-keypass", pass);
        pb.redirectErrorStream(true);
        Process p = pb.start();
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        try {
            if (p.waitFor() != 0) throw new IOException("keytool: " + out.trim());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("keytool przerwany");
        }
        LOG.info("Utworzono certyfikat TLS serwera: " + store);
    }
}
