package com.acme.sportplatform.results;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/** Explicitly run -Dtest=Qa02KnownDefectsIT: expected RED until the report's owners repair them.
 * Surefire excludes *IT; ordinary Failsafe verify excludes the known-defect tag. */
@Tag("known-defect")
class Qa02KnownDefectsIT extends Qa02ResultsFixture {
    @Test
    void qa02_06_eventCardHasNullableRegulationUrl() throws Exception {
        var card = read("");
        assertThat(card.has("regulationUrl")).as("T06: null when there is no published file/link").isTrue();
        assertThat(card.path("regulationUrl").isNull()).isTrue();
    }

}
