package co.edu.icesi.pdg.mte.integration;

import co.edu.icesi.pdg.mte.api.dto.ProjectDtos;
import co.edu.icesi.pdg.mte.common.BusinessException;
import co.edu.icesi.pdg.mte.project.Project;
import co.edu.icesi.pdg.mte.project.ProjectRepository;
import co.edu.icesi.pdg.mte.project.ProjectStatus;
import co.edu.icesi.pdg.mte.strategy.KeyResult;
import co.edu.icesi.pdg.mte.strategy.KeyResultProgressService;
import co.edu.icesi.pdg.mte.strategy.KeyResultRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Service
@Transactional
public class ProjectKeyResultLinkService {

    private final ProjectKeyResultLinkRepository linkRepository;
    private final ProjectRepository projectRepository;
    private final KeyResultRepository keyResultRepository;
    private final KeyResultProgressService keyResultProgressService;

    public ProjectKeyResultLinkService(
            ProjectKeyResultLinkRepository linkRepository,
            ProjectRepository projectRepository,
            KeyResultRepository keyResultRepository,
            KeyResultProgressService keyResultProgressService
    ) {
        this.linkRepository = linkRepository;
        this.projectRepository = projectRepository;
        this.keyResultRepository = keyResultRepository;
        this.keyResultProgressService = keyResultProgressService;
    }

    public ProjectDtos.ProjectKeyResultLinkResponse link(ProjectDtos.ProjectKeyResultLinkRequest request) {
        Project project = findProject(request.projectId());
        KeyResult keyResult = findKeyResult(request.keyResultId());
        if (linkRepository.existsByProjectIdAndKeyResultIdAndActiveTrue(project.getId(), keyResult.getId())) {
            throw new BusinessException(HttpStatus.CONFLICT, "El proyecto ya esta vinculado a este Key Result.");
        }

        ProjectKeyResultLink link = new ProjectKeyResultLink();
        link.setProject(project);
        link.setKeyResult(keyResult);
        link.setContributionWeight(request.contributionWeight());
        ProjectKeyResultLink saved = linkRepository.save(link);
        keyResultProgressService.recalculateKeyResult(keyResult.getId());
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<ProjectDtos.ProjectKeyResultLinkResponse> list(Long projectId, Long keyResultId) {
        if (projectId != null) {
            return linkRepository.findByProjectIdAndActiveTrueOrderByIdAsc(projectId)
                    .stream()
                    .filter(link -> keyResultId == null || link.getKeyResult().getId().equals(keyResultId))
                    .map(this::toResponse)
                    .toList();
        }
        if (keyResultId != null) {
            return linkRepository.findByKeyResultIdAndActiveTrueOrderByIdAsc(keyResultId)
                    .stream()
                    .map(this::toResponse)
                    .toList();
        }
        return linkRepository.findAll()
                .stream()
                .filter(ProjectKeyResultLink::isActive)
                .map(this::toResponse)
                .toList();
    }

    public void unlink(Long id) {
        ProjectKeyResultLink link = linkRepository.findById(id)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Vinculo Proyecto-KR no encontrado."));
        link.setActive(false);
        linkRepository.save(link);
        keyResultProgressService.recalculateKeyResult(link.getKeyResult().getId());
    }

    @Transactional(readOnly = true)
    public ProjectDtos.ImpactChainResponse impactChain(Long projectId) {
        Project project = findProject(projectId);
        List<ProjectDtos.ImpactChainItemResponse> impacts = linkRepository.findByProjectIdAndActiveTrueOrderByIdAsc(projectId)
                .stream()
                .map(this::toImpactItem)
                .toList();
        return new ProjectDtos.ImpactChainResponse(
                project.getId(),
                project.getName(),
                project.getGlobalProgress(),
                project.getStatus(),
                impacts
        );
    }

    private ProjectDtos.ProjectKeyResultLinkResponse toResponse(ProjectKeyResultLink link) {
        BigDecimal totalWeight = totalWeightForKeyResult(link.getKeyResult().getId());
        return new ProjectDtos.ProjectKeyResultLinkResponse(
                link.getId(),
                link.getProject() == null ? null : link.getProject().getId(),
                link.getProject() == null ? null : link.getProject().getName(),
                link.getKeyResult().getId(),
                link.getKeyResult().getDescription(),
                link.getContributionWeight(),
                totalWeight,
                totalWeight.compareTo(BigDecimal.valueOf(100)) > 0,
                link.isActive(),
                link.getCreatedAt()
        );
    }

    private ProjectDtos.ImpactChainItemResponse toImpactItem(ProjectKeyResultLink link) {
        KeyResult keyResult = link.getKeyResult();
        boolean completed = link.getProject() != null && link.getProject().getStatus() == ProjectStatus.FINALIZADO;
        BigDecimal appliedContribution = completed ? link.getContributionWeight() : BigDecimal.ZERO;
        return new ProjectDtos.ImpactChainItemResponse(
                link.getId(),
                keyResult.getId(),
                keyResult.getDescription(),
                keyResult.getObjective().getId(),
                keyResult.getObjective().getName(),
                link.getContributionWeight(),
                appliedContribution.setScale(2, RoundingMode.HALF_UP),
                completed,
                periodOf(link.getProject())
        );
    }

    private BigDecimal totalWeightForKeyResult(Long keyResultId) {
        return linkRepository.findByKeyResultIdAndActiveTrueOrderByIdAsc(keyResultId)
                .stream()
                .map(ProjectKeyResultLink::getContributionWeight)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private String periodOf(Project project) {
        if (project == null) {
            return null;
        }
        return project.getEndPeriod() == null || project.getEndPeriod().isBlank()
                ? project.getStartPeriod()
                : project.getEndPeriod();
    }

    private Project findProject(Long id) {
        return projectRepository.findById(id)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Proyecto no encontrado."));
    }

    private KeyResult findKeyResult(Long id) {
        return keyResultRepository.findById(id)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Key Result no encontrado."));
    }
}
