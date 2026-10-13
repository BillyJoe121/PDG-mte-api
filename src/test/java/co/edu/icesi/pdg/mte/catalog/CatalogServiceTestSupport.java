package co.edu.icesi.pdg.mte.catalog;

import co.edu.icesi.pdg.mte.audit.AuditService;
import co.edu.icesi.pdg.mte.project.ProjectRepository;
import co.edu.icesi.pdg.mte.strategy.InstitutionalGoalRepository;
import co.edu.icesi.pdg.mte.strategy.KeyResultRepository;
import org.mockito.InjectMocks;
import org.mockito.Mock;

abstract class CatalogServiceTestSupport {

    @Mock
    protected MeasurementUnitRepository unitRepository;

    @Mock
    protected AcademicPeriodRepository periodRepository;

    @Mock
    protected SchoolRepository schoolRepository;

    @Mock
    protected DepartmentRepository departmentRepository;

    @Mock
    protected InstitutionalGoalRepository goalRepository;

    @Mock
    protected KeyResultRepository keyResultRepository;

    @Mock
    protected ProjectRepository projectRepository;

    @Mock
    protected AuditService auditService;

    @InjectMocks
    protected CatalogService service;
}
