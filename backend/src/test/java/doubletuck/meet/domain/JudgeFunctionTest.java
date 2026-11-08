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
            "' t1 ', T1",
            "R1, R1",
            "R6, R6"
    })
    void testFind(String text, JudgeFunction expected) {
        assertEquals(expected, JudgeFunction.find(text));
    }

    @Test
    void testFind_Unknown() {
        assertNull(JudgeFunction.find("RES 1"));
        assertNull(JudgeFunction.find(""));
        assertNull(JudgeFunction.find(null));
    }
}
