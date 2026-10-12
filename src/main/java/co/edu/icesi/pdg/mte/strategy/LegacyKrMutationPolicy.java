package co.edu.icesi.pdg.mte.strategy;
import co.edu.icesi.pdg.mte.common.BusinessException;
import org.springframework.http.HttpStatus;

/** V1 project associations must not bypass the governed V2 lifecycle. */
public final class LegacyKrMutationPolicy {
    private LegacyKrMutationPolicy() {}
    public static void requireUngoverned(KeyResult kr){
        if(kr.getStrategicCycleId()!=null)throw new BusinessException(HttpStatus.CONFLICT,"El KR pertenece a un ciclo estratégico; usa las operaciones V2 con su versión y aprobación.");
    }
    public static void requireMutableLink(KeyResult kr){
        requireUngoverned(kr);
        if(kr.getStatus()==KeyResultStatus.CERRADO||!Boolean.TRUE.equals(kr.getActive()))throw new BusinessException(HttpStatus.CONFLICT,"El KR está cerrado o inactivo y no admite cambios de vínculos.");
    }
}
