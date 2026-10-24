package com.acme.sportplatform.common.web;

import java.sql.SQLException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class GlobalExceptionHandlerTest {
    @RestController
    static class Endpoint {
        @PostMapping(value = "/body", consumes = "application/json")
        public void body(@RequestBody java.util.Map<String, Object> body) {}
        @GetMapping("/duplicate")
        public void duplicate() { throw new DataIntegrityViolationException("SQLState 23505 constraint uq_secret",
                new SQLException("insert into secret values ('private')")); }
        @GetMapping("/database")
        public void database() { throw new DataAccessResourceFailureException("select private from secret"); }
    }
    final MockMvc mvc = standaloneSetup(new Endpoint()).setControllerAdvice(new GlobalExceptionHandler()).build();

    @Test void malformedJsonIs400WithoutParserDetails() throws Exception {
        mvc.perform(post("/body").contentType(MediaType.APPLICATION_JSON).content("{broken"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("invalid_request_body"))
                .andExpect(jsonPath("$.message").value("Некорректный JSON в теле запроса"));
    }
    @Test void unsupportedMethodIs405WithAllow() throws Exception {
        mvc.perform(put("/body")).andExpect(status().isMethodNotAllowed())
                .andExpect(header().string("Allow", "POST"))
                .andExpect(jsonPath("$.message").value("Метод запроса не поддерживается"));
    }
    @Test void unsupportedMediaIs415() throws Exception {
        mvc.perform(post("/body").contentType(MediaType.TEXT_PLAIN).content("private"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.message").value("Тип содержимого запроса не поддерживается"));
    }
    @Test void databaseMessagesNeverReachResponseOrApiLogs() throws Exception {
        var logger = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(GlobalExceptionHandler.class);
        var appender = new ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>();
        appender.start(); logger.addAppender(appender);
        try {
            var duplicate = mvc.perform(get("/duplicate")).andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("data_conflict")).andReturn();
            var failure = mvc.perform(get("/database")).andExpect(status().isInternalServerError()).andReturn();
            assertThat(duplicate.getResponse().getContentAsString() + failure.getResponse().getContentAsString())
                    .doesNotContain("SQLState", "23505", "uq_secret", "private", "insert into", "select");
            assertThat(appender.list).hasSize(2).allSatisfy(event -> {
                assertThat(event.getLevel()).isEqualTo(ch.qos.logback.classic.Level.WARN);
                assertThat(event.getThrowableProxy()).isNull();
                assertThat(event.getFormattedMessage()).doesNotContain("SQLState", "uq_secret", "private", "insert into", "select");
            });
        } finally { logger.detachAppender(appender); }
    }
}
