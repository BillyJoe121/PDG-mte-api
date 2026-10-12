package co.edu.icesi.pdg.mte.reports;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "report_export")
public class ReportExport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "report_type", nullable = false, length = 50)
    private String reportType;

    @Column(name = "cut_id")
    private Long cutId;

    @Column(name = "cycle_id")
    private Long cycleId;

    @Column(name = "key_result_id")
    private Long keyResultId;

    @Column(name = "exported_by", nullable = false, length = 100)
    private String exportedBy;

    @Column(name = "filters_applied", columnDefinition = "TEXT")
    private String filtersApplied;

    @Column(name = "file_format", nullable = false, length = 20)
    private String fileFormat;

    @Column(name = "file_size_bytes")
    private Long fileSizeBytes;

    @Column(name = "file_sha256", length = 64)
    private String fileSha256;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    public ReportExport() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getReportType() { return reportType; }
    public void setReportType(String reportType) { this.reportType = reportType; }
    public Long getCutId() { return cutId; }
    public void setCutId(Long cutId) { this.cutId = cutId; }
    public Long getCycleId() { return cycleId; }
    public void setCycleId(Long cycleId) { this.cycleId = cycleId; }
    public Long getKeyResultId() { return keyResultId; }
    public void setKeyResultId(Long keyResultId) { this.keyResultId = keyResultId; }
    public String getExportedBy() { return exportedBy; }
    public void setExportedBy(String exportedBy) { this.exportedBy = exportedBy; }
    public String getFiltersApplied() { return filtersApplied; }
    public void setFiltersApplied(String filtersApplied) { this.filtersApplied = filtersApplied; }
    public String getFileFormat() { return fileFormat; }
    public void setFileFormat(String fileFormat) { this.fileFormat = fileFormat; }
    public Long getFileSizeBytes() { return fileSizeBytes; }
    public void setFileSizeBytes(Long fileSizeBytes) { this.fileSizeBytes = fileSizeBytes; }
    public String getFileSha256() { return fileSha256; }
    public void setFileSha256(String fileSha256) { this.fileSha256 = fileSha256; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}
