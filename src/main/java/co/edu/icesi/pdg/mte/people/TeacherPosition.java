package co.edu.icesi.pdg.mte.people;

import jakarta.persistence.*;

@Entity
@Table(name = "teacher_position")
public class TeacherPosition {

    @EmbeddedId
    private TeacherPositionId id = new TeacherPositionId();

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @MapsId("positionId")
    @JoinColumn(name = "position_id")
    private Position position;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @MapsId("teacherId")
    @JoinColumn(name = "teacher_id")
    private Professor teacher;

    private Boolean isActive;

    public TeacherPositionId getId() {
        return id;
    }

    public void setId(TeacherPositionId id) {
        this.id = id;
    }

    public Position getPosition() {
        return position;
    }

    public void setPosition(Position position) {
        this.position = position;
    }

    public Professor getTeacher() {
        return teacher;
    }

    public void setTeacher(Professor teacher) {
        this.teacher = teacher;
    }

    public Boolean getIsActive() {
        return isActive;
    }

    public void setIsActive(Boolean active) {
        isActive = active;
    }
}
