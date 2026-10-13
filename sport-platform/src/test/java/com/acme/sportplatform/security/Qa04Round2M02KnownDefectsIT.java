package com.acme.sportplatform.security;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.core.read.ListAppender;
import java.lang.reflect.Proxy;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.test.util.AopTestUtils;
import org.springframework.test.util.ReflectionTestUtils;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** M02 candidate defects. Explicit opt-in only; each assertion describes the desired fix. */
@Tag("known-defect")
@EnabledIf("com.acme.sportplatform.security.Qa04M02Conditions#available")
class Qa04Round2M02KnownDefectsIT extends Qa04M02Fixture {
    @Autowired ApplicationContext context;

    /** Owner: M02 — canonicalize client addresses before constructing limiter keys.
     * MockMvc supplies equivalent textual IPv6 addresses; deployment exploitability depends on the connector. */
    @Test void equivalentIpv6AddressesMustShareTheLoginLimit() throws Exception {
        remoteIp="2001:db8:42::1";
        for(int i=0;i<5;i++) call("/login",Map.of("email","ipv6-"+i+"@qa04.example","password","wrong"),null)
                .andExpect(status().isUnauthorized());
        remoteIp="2001:0db8:0042:0000:0000:0000:0000:0001";
        call("/login",Map.of("email","ipv6-sixth@qa04.example","password","wrong"),null)
                .andExpect(status().isTooManyRequests());
    }

    /** Owner: M02 — never log reset credentials with the production mail implementation. */
    @Test void resetCredentialsMustNotAppearInDefaultApplicationLogs() throws Exception {
        Logger logger=(Logger)LoggerFactory.getLogger("com.acme.sportplatform.common.LoggingMailSender");
        ListAppender<ch.qos.logback.classic.spi.ILoggingEvent> appender=new ListAppender<>();
        appender.start(); logger.addAppender(appender);
        try {
            call("/password-reset/request",Map.of("email",ids.get("participantEmail")),null).andExpect(status().isAccepted());
            assertThat(appender.list.stream().map(e->e.getFormattedMessage()).toList())
                    .allSatisfy(message->assertThat(message).doesNotContain("reset-password?token="));
        } finally { logger.detachAppender(appender); appender.stop(); }
    }

    /** Owner: M02 — queue delivery or contain mail failures so known and absent users both receive 202. */
    @Test void mailDeliveryFailureMustNotEnumerateRegisteredUsers() throws Exception {
        call("/password-reset/request",Map.of("email","absent-"+ids.get("event")+"@qa04.example"),null)
                .andExpect(status().isAccepted());
        Object service=AopTestUtils.getUltimateTargetObject(context.getBean("passwordService"));
        Object original=ReflectionTestUtils.getField(service,"mail");
        Class<?> contract=Class.forName("com.acme.sportplatform.common.MailSender");
        Object failing=Proxy.newProxyInstance(contract.getClassLoader(),new Class<?>[]{contract},(proxy,method,args)->{
            throw new IllegalStateException("QA04 simulated mail delivery failure");
        });
        ReflectionTestUtils.setField(service,"mail",failing);
        try {
            call("/password-reset/request",Map.of("email",ids.get("participantEmail")),null)
                    .andExpect(status().isAccepted());
        } finally { ReflectionTestUtils.setField(service,"mail",original); }
    }
}
