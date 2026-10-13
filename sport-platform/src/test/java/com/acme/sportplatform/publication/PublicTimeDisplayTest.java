package com.acme.sportplatform.publication;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import com.acme.sportplatform.publication.application.PublicPublicationService;

class PublicTimeDisplayTest {
    @Test
    void formatsTimesInMinutesSecondsAndHundredthsWithoutLocaleDependence() {
        assertThat(PublicPublicationService.display(32150L,"VALID")).isEqualTo("00:32.15");
        assertThat(PublicPublicationService.display(62345L,"VALID")).isEqualTo("01:02.34");
        assertThat(PublicPublicationService.display(3661230L,"VALID")).isEqualTo("61:01.23");
        assertThat(PublicPublicationService.display(null,"DID_NOT_START")).isEqualTo("DNS");
        assertThat(PublicPublicationService.display(32150L,"DISQUALIFIED")).isEqualTo("DSQ");
        assertThat(PublicPublicationService.display(null,null)).isNull();
        assertThat(PublicPublicationService.publicStatus("CORRECTED")).isEqualTo("VALID");
    }
}
