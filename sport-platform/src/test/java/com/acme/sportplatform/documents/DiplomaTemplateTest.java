package com.acme.sportplatform.documents;
import com.acme.sportplatform.events.DiplomaSettings;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class DiplomaTemplateTest {
    @Test void substitutesLiterallyAndUsesRussianOrdinals() {
        var d=new DiplomaSettings(true,"ГРАМОТА","{lastName} {firstName}: {placeText}","",false,null);
        assertThat(d.substitute(d.bodyTemplate(),Map.of("lastName","Иванова","firstName","Мария $1","placeText",DiplomaSettings.placeText(2))))
                .isEqualTo("Иванова Мария $1: 2-е");
        assertThat(DiplomaSettings.placeText(1)).isEqualTo("1-е"); assertThat(DiplomaSettings.placeText(3)).isEqualTo("3-е");
    }
    @Test void rejectsUnknownPlaceholdersWithAllowedList() {
        var d=new DiplomaSettings(true,"{unknown}","", "",false,null);
        assertThatThrownBy(d::validate).hasMessageContaining("lastName","eventDates","city");
    }
}
