package pl.fawizja.pixel.friends;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;
import pl.fawizja.pixel.TestData;
import pl.fawizja.pixel.character.CharacterCodec;
import pl.fawizja.pixel.character.PlayerCharacter;
import pl.fawizja.pixel.vocation.Vocation;

class FriendListTest {

    @Test
    void addRemoveAndLimits() {
        FriendList f = new FriendList();
        assertEquals(FriendList.Result.SELF, f.add("Ola", "ola"));
        assertEquals(FriendList.Result.OK, f.add("Ola", "Ula"));
        assertEquals(FriendList.Result.ALREADY, f.add("Ola", "ULA"));
        assertEquals(FriendList.Result.UNKNOWN, f.add("Ola", "  "));
        for (int i = 1; i < FriendList.MAX; i++) assertEquals(FriendList.Result.OK, f.add("Ola", "Gracz" + i));
        assertEquals(FriendList.Result.FULL, f.add("Ola", "Jeszcze"));
        assertEquals(FriendList.Result.OK, f.remove("ula"));
        assertEquals(FriendList.Result.NOT_FRIEND, f.remove("ula"));
        assertFalse(f.contains("Ula"));
    }

    @Test
    void theListKeepsItsOrderInTheSave() throws Exception {
        PlayerCharacter pc = TestData.newCharacter(Vocation.DRUID);
        for (String n : List.of("Stary Wilk", "Ula", "Kuba", "A", "B", "C", "D", "E", "F", "G", "H", "Ż=ółw\\x"))
            pc.friends().add(pc.name(), n);
        CharacterCodec codec = new CharacterCodec(TestData.ITEMS);
        PlayerCharacter back = codec.decode(codec.encode(pc));
        assertEquals(pc.friends().names(), back.friends().names());
    }

    @Test
    void viewRoundTrip() {
        FriendsView v = new FriendsView(List.of(new FriendsView.Row("Ula", true, "Druid", 40),
                new FriendsView.Row("Stary Wilk", false, "-", 0)));
        String[] enc = v.encode();
        String[] msg = new String[enc.length + 1];
        msg[0] = "FRIENDS";
        System.arraycopy(enc, 0, msg, 1, enc.length);
        assertEquals(v, FriendsView.parse(msg));
        assertEquals(1, v.online());
        assertNull(FriendsView.parse(new String[] {"RANK"}));
    }
}
