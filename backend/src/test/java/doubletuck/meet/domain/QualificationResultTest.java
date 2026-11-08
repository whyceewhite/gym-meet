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
            "' NR ', NR"
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
}
