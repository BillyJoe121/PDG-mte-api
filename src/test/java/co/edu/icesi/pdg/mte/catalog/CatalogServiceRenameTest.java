package co.edu.icesi.pdg.mte.catalog;

import co.edu.icesi.pdg.mte.api.dto.CatalogDtos;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CatalogServiceRenameTest extends CatalogServiceTestSupport {

    @Test
    void aSchoolCanBeRenamedToANameNobodyUses() {
        School school = new School();
        school.setId(1L);
        school.setName("Ingenieria");
        when(schoolRepository.findById(1L)).thenReturn(Optional.of(school));
        when(schoolRepository.findByNameIgnoreCase("Ciencias")).thenReturn(Optional.empty());
        when(schoolRepository.save(any(School.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.updateSchool(1L, new CatalogDtos.SchoolRequest(" Ciencias ", "Nueva"));

        assertThat(response.name()).isEqualTo("Ciencias");
        assertThat(response.description()).isEqualTo("Nueva");
    }
}
