package co.edu.icesi.pdg.mte.strategy;

import co.edu.icesi.pdg.mte.common.BusinessException;
import org.springframework.http.HttpStatus;

/** Rules shared by every module that must not operate on an archived Key Result. */
public final class KeyResultArchivePolicy {
    public static final String ARCHIVED_MESSAGE = "El KR esta archivado y no admite cambios ni nuevos vinculos.";

    private KeyResultArchivePolicy() {
    }

    public static void requireNotArchived(KeyResult keyResult) {
        if (keyResult.isArchived()) {
            throw new BusinessException(HttpStatus.CONFLICT, ARCHIVED_MESSAGE);
        }
    }
}
