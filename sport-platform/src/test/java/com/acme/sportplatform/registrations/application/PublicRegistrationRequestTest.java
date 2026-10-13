package com.acme.sportplatform.registrations.application;

import com.acme.sportplatform.identity.ProfileData;
import com.acme.sportplatform.registrations.api.PublicRegistrationRequest;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.util.UUID;
import java.util.stream.IntStream;
import static org.assertj.core.api.Assertions.assertThat;

class PublicRegistrationRequestTest {
    @Test
    void qa03_02RejectsMoreThanThreeDisciplinesAndAcceptsOneToThree() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            for (int count = 1; count <= 3; count++) assertThat(validator.validate(request(count))).isEmpty();
            assertThat(validator.validate(request(4))).anySatisfy(violation -> {
                assertThat(violation.getPropertyPath().toString()).isEqualTo("items");
                assertThat(violation.getMessage()).isEqualTo("Выберите не больше трёх видов программы");
            });
        }
    }

    private PublicRegistrationRequest request(int count) {
        return new PublicRegistrationRequest(new ProfileData("Демов", "Тимофей", null,
                LocalDate.of(2014, 4, 18), "M", null), IntStream.range(0, count)
                .mapToObj(i -> new PublicRegistrationRequest.Item(UUID.randomUUID(), 32150L)).toList());
    }
}
