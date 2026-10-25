package com.acme.sportplatform.common.web;

import java.sql.SQLException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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
                new SQLException("insert into secret values ('private')", "23505")); }
        @GetMapping("/database")
        public void database() { throw new DataAccessResourceFailureException("select private from secret",
                new SQLException("constraint uq_secret", "08006")); }
        @GetMapping("/sql")
        public void sql() throws SQLException { throw new SQLException("select private from secret", "42000"); }
        @GetMapping("/wrapped-sql")
        public void wrappedSql() { throw new IllegalStateException("wrapper private",
                new SQLException("select private from secret", "08006")); }
        @GetMapping("/wrapped-data-access")
        public void wrappedDataAccess() { throw new IllegalStateException("wrapper private",
                new DataAccessResourceFailureException("select private from secret")); }
        @GetMapping("/unexpected")
        public void unexpected() { throw new IllegalStateException("boom"); }
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
        var previousLevel = logger.getLevel();
        logger.setLevel(ch.qos.logback.classic.Level.INFO);
        appender.start(); logger.addAppender(appender);
        try {
            var duplicate = mvc.perform(get("/duplicate")).andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("data_conflict")).andReturn();
            var failure = mvc.perform(get("/database")).andExpect(status().isInternalServerError()).andReturn();
            assertThat(duplicate.getResponse().getContentAsString() + failure.getResponse().getContentAsString())
                    .doesNotContain("SQLState", "23505", "uq_secret", "private", "insert into", "select");
            assertThat(appender.list).hasSize(2);
            assertThat(appender.list.get(0).getLevel()).isEqualTo(ch.qos.logback.classic.Level.WARN);
            assertThat(appender.list.get(0).getFormattedMessage()).contains("DataIntegrityViolationException", "SQLState 23505");
            assertThat(appender.list.get(1).getLevel()).isEqualTo(ch.qos.logback.classic.Level.ERROR);
            assertThat(appender.list.get(1).getFormattedMessage()).contains("DataAccessResourceFailureException", "SQLState 08006");
            assertThat(appender.list).allSatisfy(event -> {
                assertThat(event.getThrowableProxy()).isNull();
                assertThat(event.getFormattedMessage()).doesNotContain("uq_secret", "private", "insert into", "select");
            });
        } finally { logger.detachAppender(appender); logger.setLevel(previousLevel); }
    }

    @Test void unexpectedFailureLogsFullStackAtErrorWithoutExposingItsMessage() throws Exception {
        var logger = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(GlobalExceptionHandler.class);
        var previousLevel = logger.getLevel();
        var appender = new ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>();
        logger.setLevel(ch.qos.logback.classic.Level.INFO);
        appender.start(); logger.addAppender(appender);
        try {
            var result = mvc.perform(get("/unexpected")).andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.code").value("internal_error"))
                    .andExpect(jsonPath("$.message").value("Непредвиденная внутренняя ошибка сервера")).andReturn();
            assertThat(result.getResponse().getContentAsString()).doesNotContain("boom", "IllegalStateException");
            assertThat(appender.list).hasSize(1);
            var event = appender.list.getFirst();
            assertThat(event.getLevel()).isEqualTo(ch.qos.logback.classic.Level.ERROR);
            assertThat(event.getThrowableProxy()).isNotNull();
            assertThat(event.getThrowableProxy().getStackTraceElementProxyArray()).isNotEmpty();
            assertThat(ch.qos.logback.classic.spi.ThrowableProxyUtil.asString(event.getThrowableProxy()))
                    .contains("IllegalStateException: boom", "Endpoint.unexpected(");
        } finally { logger.detachAppender(appender); logger.setLevel(previousLevel); }
    }

    @ParameterizedTest
    @CsvSource({"database, DataAccessResourceFailureException, 08006, 500, ERROR", "sql, SQLException, 42000, 500, ERROR",
            "wrapped-sql, SQLException, 08006, 500, ERROR", "wrapped-data-access, DataAccessResourceFailureException, unknown, 500, ERROR",
            "duplicate, DataIntegrityViolationException, 23505, 409, WARN"})
    void databaseStackIsAvailableOnlyAtDebug(String path, String exceptionClass, String sqlState, int statusCode, String level) throws Exception {
        var logger = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(GlobalExceptionHandler.class);
        var previousLevel = logger.getLevel();
        var appender = new ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>();
        logger.setLevel(ch.qos.logback.classic.Level.DEBUG);
        appender.start(); logger.addAppender(appender);
        try {
            var result = mvc.perform(get("/" + path)).andExpect(status().is(statusCode))
                    .andExpect(jsonPath("$.code").value(statusCode == 409 ? "data_conflict" : "internal_error")).andReturn();
            assertThat(result.getResponse().getContentAsString()).doesNotContain("SQLState", "uq_secret", "private", "select", sqlState);
            assertThat(appender.list).hasSize(2);
            var error = appender.list.get(0);
            assertThat(error.getLevel()).isEqualTo(ch.qos.logback.classic.Level.toLevel(level));
            assertThat(error.getThrowableProxy()).isNull();
            assertThat(error.getFormattedMessage()).contains(exceptionClass, "SQLState " + sqlState)
                    .doesNotContain("uq_secret", "private", "select");
            var debug = appender.list.get(1);
            assertThat(debug.getLevel()).isEqualTo(ch.qos.logback.classic.Level.DEBUG);
            assertThat(debug.getThrowableProxy()).isNotNull();
            assertThat(ch.qos.logback.classic.spi.ThrowableProxyUtil.asString(debug.getThrowableProxy()))
                    .contains(path.equals("duplicate") ? "insert into secret values" : "select private from secret");
        } finally { logger.detachAppender(appender); logger.setLevel(previousLevel); }
    }

}
