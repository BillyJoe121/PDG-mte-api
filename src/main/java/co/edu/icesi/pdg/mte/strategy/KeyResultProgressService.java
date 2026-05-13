package co.edu.icesi.pdg.mte.strategy;

import co.edu.icesi.pdg.mte.common.BusinessException;
import co.edu.icesi.pdg.mte.integration.ProjectKeyResultLinkRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class KeyResultProgressService {

    private final KeyResultRepository keyResultRepository;
    private final ProjectKeyResultLinkRepository linkRepository;

    public KeyResultProgressService(
            KeyResultRepository keyResultRepository,
            ProjectKeyResultLinkRepository linkRepository
    ) {
        this.keyResultRepository = keyResultRepository;
        this.linkRepository = linkRepository;
    }

    public KeyResult recalculateKeyResult(Long keyResultId) {
        KeyResult keyResult = keyResultRepository.findById(keyResultId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Key Result no encontrado."));
        keyResult.recalculateProgress(linkRepository.findByKeyResultIdAndActiveTrueOrderByIdAsc(keyResultId));
        return keyResultRepository.save(keyResult);
    }

    public void recalculateKeyResultsForProject(Long projectId) {
        linkRepository.findByProjectIdAndActiveTrueOrderByIdAsc(projectId)
                .forEach(link -> recalculateKeyResult(link.getKeyResult().getId()));
    }
}
