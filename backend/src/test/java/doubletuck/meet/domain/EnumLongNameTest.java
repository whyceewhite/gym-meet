package doubletuck.meet.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@code find} lookups by long name for the enums without a dedicated test class.
 */
class EnumLongNameTest {

    @Test
    void testApparatus() {
        assertEquals(Apparatus.SR, Apparatus.find("SR"));
        assertEquals(Apparatus.SR, Apparatus.find("still rings"));
        assertEquals("Horizontal Bar", Apparatus.HB.getLongName());
        assertNull(Apparatus.find("Rings"));
    }

    @Test
    void testRound() {
        assertEquals(Round.EVENT, Round.find("EVENT"));
        assertEquals(Round.EVENT, Round.find("Apparatus Final"));
        assertEquals(Round.AA, Round.find("all-around final"));
        assertEquals("Qualification", Round.QUAL.getLongName());
    }

    @Test
    void testEventLevel() {
        assertEquals(EventLevel.WORLD_CHAMPIONSHIPS, EventLevel.find("WORLD_CHAMPIONSHIPS"));
        assertEquals(EventLevel.WORLD_CHAMPIONSHIPS, EventLevel.find("World Championships"));
        assertEquals("International Tournament", EventLevel.INTERNATIONAL_TOURNAMENTS.getLongName());
    }

    @Test
    void testScoreStatus() {
        assertEquals(ScoreStatus.DNS, ScoreStatus.find("DNS"));
        assertEquals(ScoreStatus.DNS, ScoreStatus.find("Did Not Start"));
        assertEquals("Invalid Vault", ScoreStatus.INVALID_VAULT.getLongName());
        assertNull(ScoreStatus.find("DNF"));
    }
}
