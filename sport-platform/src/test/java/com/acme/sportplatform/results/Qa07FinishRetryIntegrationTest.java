package com.acme.sportplatform.results;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/** Regression: an old finish retry must not skip the currently running heat. */
class Qa07FinishRetryIntegrationTest extends Qa02ResultsFixture {
    @Test
    void delayedFinishRetryMustPreserveTheCurrentlyRunningNextHeat() throws Exception {
        liveAction(first, "start");
        assertThat(liveAction(first, "finish").path("currentUnitId").asText()).isEqualTo(second.toString());
        var running = liveAction(second, "start");
        // A delayed retry from the first panel must not skip the now running second heat.
        assertThat(liveAction(first, "finish"))
                .as("M10: finish is idempotent even after the next heat has started").isEqualTo(running);
        assertThat(liveAction(first, "finish")).isEqualTo(running);
        assertThat(jdbc.queryForObject("select status from competition_units where id=?", String.class, second))
                .isEqualTo("IN_PROGRESS");
    }
}
