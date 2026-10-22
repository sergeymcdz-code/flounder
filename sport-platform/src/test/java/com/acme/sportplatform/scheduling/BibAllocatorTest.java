package com.acme.sportplatform.scheduling;
import com.acme.sportplatform.scheduling.domain.BibAllocator;
import com.acme.sportplatform.scheduling.domain.BibAllocator.*;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class BibAllocatorTest {
    Athlete a=new Athlete(UUID.fromString("00000000-0000-0000-0000-000000000001"),"Иванов","Иван",LocalDate.of(2014,1,1),"Волна");
    Athlete b=new Athlete(UUID.fromString("00000000-0000-0000-0000-000000000002"),"Андреев","Иван",LocalDate.of(2014,1,1),"Якорь");
    @Test void deterministicClubAndNameOrders() {
        assertThat(BibAllocator.assign(List.of(b,a),Map.of(),Mode.BY_CLUB_THEN_NAME,10)).containsEntry(a.id(),10).containsEntry(b.id(),11);
        assertThat(BibAllocator.assign(List.of(a,b),Map.of(),Mode.BY_NAME,10)).containsEntry(b.id(),10).containsEntry(a.id(),11);
        assertThat(BibAllocator.assign(List.of(a,b),Map.of(),Mode.BY_NAME,10)).isEqualTo(BibAllocator.assign(List.of(b,a),Map.of(),Mode.BY_NAME,10));
    }
    @Test void keepsExistingAndSkipsReservedNumbersIncludingInactiveProfiles() {
        var old=Map.of(a.id(),77,UUID.randomUUID(),1);
        assertThat(BibAllocator.assign(List.of(a,b),old,Mode.BY_NAME,1)).containsEntry(a.id(),77).containsEntry(b.id(),2);
    }
    @Test void tiesUseBirthDateThenUuidAndRangeCannotOverflow() {
        var c=new Athlete(UUID.fromString("00000000-0000-0000-0000-000000000003"),a.lastName(),a.firstName(),a.birthDate(),a.club());
        assertThat(BibAllocator.assign(List.of(c,a),Map.of(),Mode.BY_NAME,1)).containsEntry(a.id(),1).containsEntry(c.id(),2);
        assertThatThrownBy(() -> BibAllocator.assign(List.of(a,b),Map.of(),Mode.BY_NAME,Integer.MAX_VALUE)).hasMessageContaining("исчерпан");
    }
}
