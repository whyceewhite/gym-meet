package doubletuck.meet.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

class QualificationResultTest {

    @ParameterizedTest
    @CsvSource({
            "Q, Q",
            "r1, R1",
            "R4, R4",
            "' NR ', NR",
            "Qualified, Q",
            "fourth reserve, R4",
            "NOC Reserve, NR"
    })
    void testFind(String text, QualificationResult expected) {
        assertEquals(expected, QualificationResult.find(text));
    }

    @Test
    void testFind_Unknown() {
        assertNull(QualificationResult.find("R5"));
        assertNull(QualificationResult.find(""));
        assertNull(QualificationResult.find(null));
    }

    @Test
    void testLongName() {
        assertEquals("NOC Reserve", QualificationResult.NR.getLongName());
    }
}
