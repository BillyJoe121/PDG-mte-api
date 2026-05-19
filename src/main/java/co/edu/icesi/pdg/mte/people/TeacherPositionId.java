package co.edu.icesi.pdg.mte.people;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class TeacherPositionId implements Serializable {

    @Column(name = "position_id")
    private Long positionId;

    @Column(name = "teacher_id")
    private Long teacherId;

    public TeacherPositionId() {
    }

    public TeacherPositionId(Long positionId, Long teacherId) {
        this.positionId = positionId;
        this.teacherId = teacherId;
    }

    public Long getPositionId() {
        return positionId;
    }

    public void setPositionId(Long positionId) {
        this.positionId = positionId;
    }

    public Long getTeacherId() {
        return teacherId;
    }

    public void setTeacherId(Long teacherId) {
        this.teacherId = teacherId;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof TeacherPositionId that)) {
            return false;
        }
        return Objects.equals(positionId, that.positionId) && Objects.equals(teacherId, that.teacherId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(positionId, teacherId);
    }
}
