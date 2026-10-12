package co.edu.icesi.pdg.mte.strategy;
import co.edu.icesi.pdg.mte.integration.ProjectKeyResultLinkRepository;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.Optional;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
class KeyResultProgressServiceTest {
    @Test void projectRefreshPreservesGovernedAndClosedKr(){
        var repository=mock(KeyResultRepository.class);var links=mock(ProjectKeyResultLinkRepository.class);var service=new KeyResultProgressService(repository,links);
        var governed=new KeyResult();governed.setId(1L);governed.setStrategicCycleId(7L);governed.setProgressPercentage(new BigDecimal("32"));when(repository.findById(1L)).thenReturn(Optional.of(governed));
        var closed=new KeyResult();closed.setId(2L);closed.setStatus(KeyResultStatus.CERRADO);closed.setProgressPercentage(new BigDecimal("44"));when(repository.findById(2L)).thenReturn(Optional.of(closed));
        service.recalculateKeyResult(1L);service.recalculateKeyResult(2L);assertThat(governed.getProgressPercentage()).isEqualByComparingTo("32");assertThat(closed.getProgressPercentage()).isEqualByComparingTo("44");verify(repository,never()).save(any());verifyNoInteractions(links);
    }
}
