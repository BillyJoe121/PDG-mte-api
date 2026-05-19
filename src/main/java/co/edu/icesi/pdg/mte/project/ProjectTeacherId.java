package co.edu.icesi.pdg.mte.project;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class ProjectTeacherId implements Serializable {

    @Column(name = "project_id")
    private Long projectId;

    @Column(name = "teacher_id")
    private Long teacherId;

    @Column(name = "role_id")
    private Long roleId;

    public ProjectTeacherId() {
    }

    public ProjectTeacherId(Long projectId, Long teacherId, Long roleId) {
        this.projectId = projectId;
        this.teacherId = teacherId;
        this.roleId = roleId;
    }

    public Long getProjectId() {
        return projectId;
    }

    public void setProjectId(Long projectId) {
        this.projectId = projectId;
    }

    public Long getTeacherId() {
        return teacherId;
    }

    public void setTeacherId(Long teacherId) {
        this.teacherId = teacherId;
    }

    public Long getRoleId() {
        return roleId;
    }

    public void setRoleId(Long roleId) {
        this.roleId = roleId;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof ProjectTeacherId that)) {
            return false;
        }
        return Objects.equals(projectId, that.projectId)
                && Objects.equals(teacherId, that.teacherId)
                && Objects.equals(roleId, that.roleId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(projectId, teacherId, roleId);
    }
}
