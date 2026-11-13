package doubletuck.meet.domain;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

class PersonNameTest {

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "BLAKELY           | Blakely",
            "HAMAMES-MOALLIC   | Hamames-Moallic",
            "McDONALD          | McDonald",
            "Mc CLENAGHAN      | Mc Clenaghan",
            "MC CLENAGHAN      | Mc Clenaghan",
            "van POL           | van Pol",
            "LIDE de LEON      | Lide de Leon",
            "O'CONNELL         | O'Connell",
            "ÖZTÜRK            | Öztürk",
            "McCauley          | McCauley",
            "VanGrinsven       | VanGrinsven",
            "Louna Marie       | Louna Marie",
            "J J               | J J",
    })
    void testNormalize(String printed, String stored) {
        assertEquals(stored, PersonName.normalize(printed));
    }

    @ParameterizedTest
    @CsvSource({ "BLAKELY", "Blakely", "McDONALD" })
    void testNormalize_IsStable(String printed) {
        String once = PersonName.normalize(printed);
        assertEquals(once, PersonName.normalize(once));
    }

    @org.junit.jupiter.api.Test
    void testNormalize_Null() {
        assertNull(PersonName.normalize(null));
    }
}
