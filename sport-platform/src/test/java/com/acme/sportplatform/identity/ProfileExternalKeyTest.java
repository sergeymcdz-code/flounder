package com.acme.sportplatform.identity;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

class ProfileExternalKeyTest {
    @Test
    void buildsCanonicalKey() {
        assertEquals(
                "елкина|анна мария|2015-06-01|f",
                ProfileExternalKey.build(
                        " Ёлкина ", "Анна  Мария", LocalDate.of(2015, 6, 1), "FEMALE"));
        assertEquals(
                "елкина|анна мария|2015-06-01|f",
                ProfileExternalKey.normalize(" ЁЛКИНА | Анна  Мария | 2015-06-01 | FEMALE "));
        assertEquals("m", ProfileExternalKey.gender(" MALE "));
        assertEquals("x", ProfileExternalKey.gender("OTHER"));
    }
}
