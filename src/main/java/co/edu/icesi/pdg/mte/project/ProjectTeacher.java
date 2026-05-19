package co.edu.icesi.pdg.mte.project;

import co.edu.icesi.pdg.mte.people.Professor;
import co.edu.icesi.pdg.mte.people.Role;
import jakarta.persistence.*;

import java.time.LocalDate;

@Entity
@Table(name = "project_teacher")
public class ProjectTeacher {

    @EmbeddedId
    private ProjectTeacherId id = new ProjectTeacherId();

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @MapsId("projectId")
    @JoinColumn(name = "project_id")
    private Project project;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @MapsId("teacherId")
    @JoinColumn(name = "teacher_id")
    private Professor teacher;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @MapsId("roleId")
    @JoinColumn(name = "role_id")
    private Role role;

    @Column(nullable = false)
    private LocalDate joinedAt;

    private LocalDate leftAt;

    public ProjectTeacherId getId() {
        return id;
    }

    public void setId(ProjectTeacherId id) {
        this.id = id;
    }

    public Project getProject() {
        return project;
    }

    public void setProject(Project project) {
        this.project = project;
    }

    public Professor getTeacher() {
        return teacher;
    }

    public void setTeacher(Professor teacher) {
        this.teacher = teacher;
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }

    public LocalDate getJoinedAt() {
        return joinedAt;
    }

    public void setJoinedAt(LocalDate joinedAt) {
        this.joinedAt = joinedAt;
    }

    public LocalDate getLeftAt() {
        return leftAt;
    }

    public void setLeftAt(LocalDate leftAt) {
        this.leftAt = leftAt;
    }
}
