package doubletuck.meet.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

class JudgeFunctionTest {

    @ParameterizedTest
    @CsvSource({
            "President, PRESIDENT",
            "Vice-President, VICE_PRESIDENT",
            "Member, MEMBER",
            "D1, D1",
            "E7, E7",
            "SV, SV",
            "DS1, DS1",
            "' t1 ', T1",
            "R, R",
            "R1, R1",
            "R6, R6"
    })
    void testFind_ByName(String text, JudgeFunction expected) {
        assertEquals(expected, JudgeFunction.find(text));
    }

    @ParameterizedTest
    @CsvSource({
            "Vice President, VICE_PRESIDENT",
            "Difficulty Judge 2, D2",
            "time judge 1, T1",
            "Line Judge, L",
            "Reserve Judge, R",
            "Reserve Judge 3, R3",
            "Difficulty Supervisor, DS1"
    })
    void testFind_ByLongName(String text, JudgeFunction expected) {
        assertEquals(expected, JudgeFunction.find(text));
    }

    @Test
    void testFind_Unknown() {
        assertNull(JudgeFunction.find("RES 1"));
        assertNull(JudgeFunction.find(""));
        assertNull(JudgeFunction.find(null));
    }

    @Test
    void testLongName() {
        assertEquals("Vice President", JudgeFunction.VICE_PRESIDENT.getLongName());
        assertEquals("Reserve Judge", JudgeFunction.R.getLongName());
    }
}
