package co.edu.icesi.pdg.mte.audit;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "audit_export")
public class AuditExport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "exported_by", nullable = false, length = 100)
    private String exportedBy;

    @Column(name = "filters_applied", columnDefinition = "TEXT")
    private String filtersApplied;

    @Column(name = "date_from")
    private OffsetDateTime dateFrom;

    @Column(name = "date_to")
    private OffsetDateTime dateTo;

    @Column(name = "total_records_exported", nullable = false)
    private Integer totalRecordsExported;

    @Column(name = "file_format", nullable = false, length = 20)
    private String fileFormat = "CSV";

    @Column(name = "file_sha256", length = 64)
    private String fileSha256;

    @Column(name = "exported_at", nullable = false)
    private OffsetDateTime exportedAt = OffsetDateTime.now();

    public AuditExport() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getExportedBy() { return exportedBy; }
    public void setExportedBy(String exportedBy) { this.exportedBy = exportedBy; }
    public String getFiltersApplied() { return filtersApplied; }
    public void setFiltersApplied(String filtersApplied) { this.filtersApplied = filtersApplied; }
    public OffsetDateTime getDateFrom() { return dateFrom; }
    public void setDateFrom(OffsetDateTime dateFrom) { this.dateFrom = dateFrom; }
    public OffsetDateTime getDateTo() { return dateTo; }
    public void setDateTo(OffsetDateTime dateTo) { this.dateTo = dateTo; }
    public Integer getTotalRecordsExported() { return totalRecordsExported; }
    public void setTotalRecordsExported(Integer totalRecordsExported) { this.totalRecordsExported = totalRecordsExported; }
    public String getFileFormat() { return fileFormat; }
    public void setFileFormat(String fileFormat) { this.fileFormat = fileFormat; }
    public String getFileSha256() { return fileSha256; }
    public void setFileSha256(String fileSha256) { this.fileSha256 = fileSha256; }
    public OffsetDateTime getExportedAt() { return exportedAt; }
    public void setExportedAt(OffsetDateTime exportedAt) { this.exportedAt = exportedAt; }
}
