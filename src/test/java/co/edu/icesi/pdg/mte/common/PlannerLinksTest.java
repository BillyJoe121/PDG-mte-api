package co.edu.icesi.pdg.mte.common;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PlannerLinksTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "https://tasks.office.com/icesi.edu.co/Home/PlanViews/abc123",
            "https://planner.cloud.microsoft/webui/plan/abc123/view/board",
            "https://office.com/planner",
            "https://www.office.com/planner",
            "https://office365.com/planner",
            "https://icesi.sharepoint.com/sites/mte/Lists/plan",
            "https://teams.microsoft.com/l/entity/planner",
            "https://microsoft.com",
            "https://cloud.microsoft/planner",
            "https://tasks.office.com:443/plan"
    })
    void acceptsHttpsLinksOnMicrosoftDomainsAndSubdomains(String link) {
        assertThat(PlannerLinks.normalize(link)).isEqualTo(link);
    }

    @Test
    void trimsTheLinkAndComparesTheHostIgnoringCase() {
        assertThat(PlannerLinks.normalize("  HTTPS://Tasks.Office.COM/Plan?x=1  ")).isEqualTo("HTTPS://Tasks.Office.COM/Plan?x=1");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "   ", "\t"})
    void blankOrNullClearsTheLink(String value) {
        assertThat(PlannerLinks.normalize(value)).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "http://tasks.office.com/plan",
            "ftp://tasks.office.com/plan",
            "javascript:alert(1)",
            "tasks.office.com/plan",
            "//tasks.office.com/plan",
            "https://user@tasks.office.com/plan",
            "https://user:secret@tasks.office.com/plan",
            "https://tasks.office.com@evil.example/plan",
            "https://evil.example/plan",
            "https://tasks.office.com.evil.example/plan",
            "https://evilmicrosoft.com/plan",
            "https://notoffice.com/plan",
            "https://microsoft.com.evil.example",
            "https://tasks.office.com:8443/plan",
            "https://tasks.office.com:80/plan",
            "https:///plan",
            "https://tasks.office.com/pla n",
            "https://tasks.office.com\\@evil.example/plan"
    })
    void rejectsLinksThatAreNotHttpsMicrosoftLinks(String link) {
        assertBadRequest(link);
    }

    @Test
    void rejectsLinksLongerThanTheLimit() {
        String prefix = "https://tasks.office.com/";
        String atLimit = prefix + "a".repeat(PlannerLinks.MAX_LENGTH - prefix.length());
        String overLimit = atLimit + "a";

        assertThat(atLimit).hasSize(PlannerLinks.MAX_LENGTH);
        assertThat(PlannerLinks.normalize(atLimit)).isEqualTo(atLimit);
        assertBadRequest(overLimit);
    }

    private static void assertBadRequest(String link) {
        assertThatThrownBy(() -> PlannerLinks.normalize(link))
                .isInstanceOf(BusinessException.class)
                .hasMessageStartingWith("El enlace de Planner no es valido: ")
                .extracting("status").isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
