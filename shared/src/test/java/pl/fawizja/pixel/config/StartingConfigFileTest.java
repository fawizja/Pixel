package pl.fawizja.pixel.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.InputStream;
import java.util.Properties;
import org.junit.jupiter.api.Test;

/** Zalozenia serwera z briefu: kazda profesja startuje na poziomie 300 z 10 000 000 gp w banku. */
class StartingConfigFileTest {
    @Test
    void shippedConfigStartsAtLevel300WithTenMillionInBank() throws Exception {
        Properties p = new Properties();
        try (InputStream in = StartingConfig.class.getResourceAsStream("/data/starting.properties")) {
            p.load(in);
        }
        StartingConfig c = StartingConfig.fromProperties(p);
        assertEquals(300, c.startLevel());
        assertEquals(300, c.offlineStartLevel());
        assertEquals(10_000_000L, c.startBankBalance());
    }
}
