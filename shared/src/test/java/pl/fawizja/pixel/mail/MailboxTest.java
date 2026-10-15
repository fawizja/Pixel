package pl.fawizja.pixel.mail;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class MailboxTest {

    @Test
    void parcelsWaitPerRecipientSurviveARestartAndHaveALimit() {
        Mailbox m = new Mailbox();
        m.add(new Mailbox.Parcel("Stary Wilk", "Ola", 10, 5));
        m.add(new Mailbox.Parcel("stary wilk", "Ula", 11, 1));
        assertEquals(2, m.waiting("STARY WILK").size());
        Mailbox back = Mailbox.decode(m.encode() + "P zepsute\n");
        assertEquals(m.waiting("Stary Wilk"), back.waiting("Stary Wilk"));
        assertEquals(2, back.take("Stary Wilk").size());
        assertTrue(back.take("Stary Wilk").isEmpty(), "taken once");
        for (int i = 0; i < Mailbox.MAX_WAITING; i++) m.add(new Mailbox.Parcel("Ola", "Ula", 10, 1));
        assertFalse(m.canAccept("ola"));
        m.forget("Ola");
        assertTrue(m.canAccept("Ola"));
    }
}
