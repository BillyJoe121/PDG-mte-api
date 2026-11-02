package co.edu.icesi.pdg.mte.strategy;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface KeyResultProgressRecordRepository extends JpaRepository<KeyResultProgressRecord, Long> {
    List<KeyResultProgressRecord> findByKeyResultIdOrderByRecordedAtDescIdDesc(Long keyResultId);
}
