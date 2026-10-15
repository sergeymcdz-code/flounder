package com.acme.sportplatform.results;

import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@TestPropertySource(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
class SwimResultsBatchIntegrationTest extends Qa02ResultsFixture {
    @Autowired EntityManagerFactory emf;

    @Test
    void protocolQueryCountDoesNotGrowWithHeatsOrEntries() throws Exception {
        entry(first, 1, 2014);
        entry(second, 1, 2012);
        long small = protocolStatements();
        for (int number = 3; number <= 8; number++) {
            var unit = heat(discipline, number, start.plusSeconds(number * 90L));
            for (int lane = 1; lane <= 8; lane++) entry(unit, lane, 2014);
        }
        long large = protocolStatements();
        assertThat(large).isEqualTo(small).isLessThanOrEqualTo(10);
    }

    private long protocolStatements() throws Exception {
        em.flush();
        em.clear();
        var stats = emf.unwrap(SessionFactory.class).getStatistics();
        stats.clear();
        mvc.perform(get("/api/v1/event-disciplines/{id}/results", discipline)
                .with(authentication(auth("ORGANIZER")))).andExpect(status().isOk());
        return stats.getPrepareStatementCount();
    }
}
