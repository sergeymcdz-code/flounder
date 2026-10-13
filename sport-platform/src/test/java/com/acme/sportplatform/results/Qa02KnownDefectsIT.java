package com.acme.sportplatform.results;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

/** Explicitly run -Dtest=Qa02KnownDefectsIT: expected RED until the report's owners repair them.
 * Surefire excludes *IT; ordinary Failsafe verify excludes the known-defect tag. */
@Tag("known-defect")
class Qa02KnownDefectsIT extends Qa02ResultsFixture {
    /** M07 section 3: estimatedStartAt; scheduledAt stays unchanged.
     * M07 must update the outdated schedule-recalculation wording in spec 5.7. */
    @Test
    void qa02_04_lateStartExposesDelayAndShiftsFollowingHeats() throws Exception {
        OffsetDateTime planned = OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(10).withNano(0);
        jdbc.update("update competition_units set scheduled_at=? where id=?", planned, first);
        jdbc.update("update competition_units set scheduled_at=? where id=?", planned.plusSeconds(90), second);
        liveAction(first, "start");
        var live = read("/live");
        assertAll(
                () -> assertThat(live.path("delaySec").isNumber()).as("5.7: live.delaySec").isTrue(),
                () -> {
                    var next = read("/live").at("/next/0");
                    assertThat(next.path("estimatedStartAt").isTextual()).as("M07: next.estimatedStartAt").isTrue();
                    assertThat(OffsetDateTime.parse(next.path("estimatedStartAt").asText()))
                            .isAfterOrEqualTo(OffsetDateTime.parse(next.path("scheduledAt").asText()));
                });
    }

    @Test
    void qa02_06_eventCardHasNullableRegulationUrl() throws Exception {
        var card = read("");
        assertThat(card.has("regulationUrl")).as("T06: null when there is no published file/link").isTrue();
        assertThat(card.path("regulationUrl").isNull()).isTrue();
    }

}
