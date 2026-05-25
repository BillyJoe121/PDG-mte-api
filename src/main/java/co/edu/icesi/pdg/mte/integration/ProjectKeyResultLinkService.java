package co.edu.icesi.pdg.mte.integration;

import co.edu.icesi.pdg.mte.api.dto.ProjectDtos;
import co.edu.icesi.pdg.mte.audit.AuditAction;
import co.edu.icesi.pdg.mte.audit.AuditService;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Transactional
public class ProjectKeyResultLinkService {

    private final ProjectKeyResultLinkRepository linkRepository;
    private final ProjectRepository projectRepository;
    private final KeyResultRepository keyResultRepository;
    private final KeyResultProgressService keyResultProgressService;
    private final AuditService auditService;

    public ProjectKeyResultLinkService(
            ProjectKeyResultLinkRepository linkRepository,
            ProjectRepository projectRepository,
            KeyResultRepository keyResultRepository,
            KeyResultProgressService keyResultProgressService,
            AuditService auditService
    ) {
        this.linkRepository = linkRepository;
        this.projectRepository = projectRepository;
        this.keyResultRepository = keyResultRepository;
        this.keyResultProgressService = keyResultProgressService;
        this.auditService = auditService;
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
        link.setContributionType(request.contributionType());
        ProjectKeyResultLink saved = linkRepository.save(link);
        keyResultProgressService.recalculateKeyResult(keyResult.getId());
        ProjectDtos.ProjectKeyResultLinkResponse response = toResponse(saved);
        auditService.record(AuditAction.LINK_CREATED, "PROJECT_KEY_RESULT_LINK", response.id(), "Proyecto vinculado a Key Result.", null, response);
        return response;
    }

    @Transactional(readOnly = true)
    public List<ProjectDtos.ProjectKeyResultLinkResponse> list(Long projectId, Long keyResultId) {
        if (projectId != null) {
            List<ProjectKeyResultLink> links = linkRepository.findByProjectIdAndActiveTrueOrderByIdAsc(projectId)
                    .stream()
                    .filter(link -> keyResultId == null || link.getKeyResult().getId().equals(keyResultId))
                    .toList();
            return toResponses(links);
        }
        if (keyResultId != null) {
            return toResponses(linkRepository.findByKeyResultIdAndActiveTrueOrderByIdAsc(keyResultId));
        }
        return toResponses(linkRepository.findByActiveTrueOrderByIdAsc());
    }

    public void unlink(Long id) {
        ProjectKeyResultLink link = linkRepository.findById(id)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Vinculo Proyecto-KR no encontrado."));
        ProjectDtos.ProjectKeyResultLinkResponse before = toResponse(link);
        link.setActive(false);
        linkRepository.save(link);
        keyResultProgressService.recalculateKeyResult(link.getKeyResult().getId());
        auditService.record(AuditAction.LINK_REMOVED, "PROJECT_KEY_RESULT_LINK", id, "Proyecto desvinculado de Key Result.", before, toResponse(link));
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
        return toResponse(link, totalWeightForKeyResult(link.getKeyResult().getId()));
    }

    private List<ProjectDtos.ProjectKeyResultLinkResponse> toResponses(List<ProjectKeyResultLink> links) {
        Map<Long, BigDecimal> totalWeightsByKeyResult = totalWeightsByKeyResult(links);
        return links.stream()
                .map(link -> toResponse(link, totalWeightsByKeyResult.getOrDefault(link.getKeyResult().getId(), BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP))))
                .toList();
    }

    private ProjectDtos.ProjectKeyResultLinkResponse toResponse(ProjectKeyResultLink link, BigDecimal totalWeight) {
        return new ProjectDtos.ProjectKeyResultLinkResponse(
                link.getId(),
                link.getProject() == null ? null : link.getProject().getId(),
                link.getProject() == null ? null : link.getProject().getName(),
                link.getKeyResult().getId(),
                link.getKeyResult().getDescription(),
                link.getContributionWeight(),
                link.getContributionType(),
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
                link.getContributionType(),
                appliedContribution.setScale(2, RoundingMode.HALF_UP),
                completed,
                periodOf(link.getProject())
        );
    }

    private BigDecimal totalWeightForKeyResult(Long keyResultId) {
        return totalWeightsByKeyResult(List.of(linkWithKeyResultId(keyResultId)))
                .getOrDefault(keyResultId, BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
    }

    private Map<Long, BigDecimal> totalWeightsByKeyResult(List<ProjectKeyResultLink> links) {
        List<Long> keyResultIds = links.stream()
                .map(ProjectKeyResultLink::getKeyResult)
                .filter(keyResult -> keyResult != null && keyResult.getId() != null)
                .map(KeyResult::getId)
                .distinct()
                .toList();
        if (keyResultIds.isEmpty()) {
            return Map.of();
        }
        return linkRepository.sumActiveContributionWeightsByKeyResultIds(keyResultIds)
                .stream()
                .collect(Collectors.toMap(
                        ProjectKeyResultLinkRepository.KeyResultWeightTotal::getKeyResultId,
                        total -> total.getTotalWeight().setScale(2, RoundingMode.HALF_UP),
                        (first, second) -> first,
                        LinkedHashMap::new
                ));
    }

    private ProjectKeyResultLink linkWithKeyResultId(Long keyResultId) {
        KeyResult keyResult = new KeyResult();
        keyResult.setId(keyResultId);
        ProjectKeyResultLink link = new ProjectKeyResultLink();
        link.setKeyResult(keyResult);
        return link;
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
