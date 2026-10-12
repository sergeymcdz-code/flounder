package com.acme.sportplatform.common;

import static org.junit.jupiter.api.Assertions.*;

import com.acme.sportplatform.common.exception.BusinessException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class TimeParserTest {
    @ParameterizedTest
    @CsvSource({
        "32.15,32150",
        "0:32.15,32150",
        "00:32.15,32150",
        "1:02:30.50,3750500",
        "01:02.003,62003",
        "100:00.00,6000000"
    })
    void parsesWithoutFloatingPointLoss(String text, long expected) {
        assertEquals(expected, TimeParser.parse(text));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(
            strings = {
                " ",
                "0",
                "-1",
                "1:60.00",
                "1:60:00.00",
                "1:2:3:4",
                "NaN",
                "00:32,15",
                "32.1234",
                "999999999999999999999999:00"
            })
    void rejectsInvalidTimes(String text) {
        assertThrows(BusinessException.class, () -> TimeParser.parse(text));
    }

    @Test
    void formatsCentisecondsAndTotalMinutes() {
        assertEquals("00:32.15", TimeParser.format(32150));
        assertEquals("62:30.50", TimeParser.format(3750500L));
        assertNull(TimeParser.format(null));
        assertEquals(32150L, TimeParser.parse(TimeParser.format(32150L)));
    }
}
