package co.edu.icesi.pdg.mte.people;

import co.edu.icesi.pdg.mte.catalog.Department;
import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "professor", uniqueConstraints = @UniqueConstraint(columnNames = "email"))
public class Professor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String email;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private Department department;

    @Column(nullable = false, length = 32)
    private String accessRole = "CONTRIBUTOR";

    @Column(nullable = false)
    private boolean active = true;

    private Instant lastAccessAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public Department getDepartment() {
        return department;
    }

    public void setDepartment(Department department) {
        this.department = department;
    }

    public String getAccessRole() {
        return accessRole;
    }

    public void setAccessRole(String accessRole) {
        this.accessRole = accessRole;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public Instant getLastAccessAt() {
        return lastAccessAt;
    }

    public void setLastAccessAt(Instant lastAccessAt) {
        this.lastAccessAt = lastAccessAt;
    }
}
