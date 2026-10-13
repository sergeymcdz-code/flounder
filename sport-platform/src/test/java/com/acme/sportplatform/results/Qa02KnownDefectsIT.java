package com.acme.sportplatform.results;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Explicitly run -Dtest=Qa02KnownDefectsIT: expected RED until the report's owners repair them.
 * Surefire excludes *IT; ordinary Failsafe verify excludes the known-defect tag. */
@Tag("known-defect")
class Qa02KnownDefectsIT extends Qa02ResultsFixture {
    /** T06 / spec 5.7; implementation requires M07 and its migration. */
    @Test
    void qa02_04_lateStartExposesDelayAndShiftsFollowingHeats() throws Exception {
        OffsetDateTime planned = OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(10).withNano(0);
        jdbc.update("update competition_units set scheduled_at=? where id=?", planned, first);
        jdbc.update("update competition_units set scheduled_at=? where id=?", planned.plusSeconds(90), second);
        liveAction(first, "start");
        var live = read("/live");
        var following = jdbc.queryForObject("select scheduled_at from competition_units where id=?", OffsetDateTime.class, second);
        assertAll(
                () -> assertThat(live.path("delaySec").isNumber()).as("5.7: live.delaySec").isTrue(),
                () -> assertThat(following).as("5.7: subsequent heat shifts with actual start").isAfter(planned.plusMinutes(9)));
    }

    @Test
    void qa02_06_eventCardHasNullableRegulationUrl() throws Exception {
        var card = read("");
        assertThat(card.has("regulationUrl")).as("T06: null when there is no published file/link").isTrue();
        assertThat(card.path("regulationUrl").isNull()).isTrue();
    }

    private void publishResults() throws Exception {
        UUID id = entry(first, 4, 2014);
        liveAction(first, "start");
        save(first, "JUDGE", List.of(timed(id, "46.20")), 200);
        mvc.perform(post("/api/v1/competition-units/{id}/publication", first)
                .with(authentication(auth("JUDGE"))).contentType(MediaType.APPLICATION_JSON)
                .content("{\"published\":true}")).andExpect(status().isNoContent());
        clearCaches();
    }

    /** Existing QA05-07, owned by M10. No assertion about the publication target status. */
    @Test
    void qa02_07_qa05_07_publicationMakesValidatedResultsPublic() throws Exception {
        publishResults();
        assertThat(read("/results").at("/disciplines/0/overall")).hasSize(1);
    }

    /** Existing QA05-09, owned by M10. No assertion that the heat remains PUBLISHED. */
    @Test
    void qa02_08_qa05_09_finishAfterPublicationSucceeds() throws Exception {
        publishResults();
        mvc.perform(post("/api/v1/competition-units/{id}/finish", first)
                .with(authentication(auth("JUDGE")))).andExpect(status().isOk());
    }

}
