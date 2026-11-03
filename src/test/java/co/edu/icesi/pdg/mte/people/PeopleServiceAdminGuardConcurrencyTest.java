package co.edu.icesi.pdg.mte.people;

import co.edu.icesi.pdg.mte.api.dto.PeopleDtos;
import co.edu.icesi.pdg.mte.catalog.Department;
import co.edu.icesi.pdg.mte.catalog.DepartmentRepository;
import co.edu.icesi.pdg.mte.common.BusinessException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.LongConsumer;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Two requests that each remove a different administrator while exactly two are active must not
 * leave the directory without an active administrator. Runs against the real service and database.
 */
@SpringBootTest
@AutoConfigureMockMvc
class PeopleServiceAdminGuardConcurrencyTest {
    private static final int ROUNDS = 12;
    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    @Autowired private PeopleService service;
    @Autowired private ProfessorRepository professorRepository;
    @Autowired private DepartmentRepository departmentRepository;

    private final List<Long> pausedAdmins = new ArrayList<>();
    private final List<Long> createdAdmins = new ArrayList<>();
    private ExecutorService executor;

    @BeforeEach
    void leaveNoOtherActiveAdmin() {
        executor = Executors.newFixedThreadPool(2);
        for (Professor admin : activeAdmins()) {
            admin.setActive(false);
            professorRepository.save(admin);
            pausedAdmins.add(admin.getId());
        }
    }

    @AfterEach
    void restoreDirectory() {
        executor.shutdownNow();
        createdAdmins.forEach(id -> professorRepository.findById(id).ifPresent(professorRepository::delete));
        pausedAdmins.forEach(id -> professorRepository.findById(id).ifPresent(admin -> {
            admin.setActive(true);
            professorRepository.save(admin);
        }));
    }

    @Test
    void concurrentRemovalsOfDifferentAdminsKeepOneActiveAdmin() throws Exception {
        List<LongConsumer> removals = List.of(
                id -> service.updateDirectoryUserActive(id, false),
                id -> service.updateDirectoryUser(id, demotion(id)),
                service::deleteDirectoryUser
        );
        for (int round = 0; round < ROUNDS; round++) {
            Long first = createAdmin();
            Long second = createAdmin();
            LongConsumer firstRemoval = removals.get(round % 3);
            LongConsumer secondRemoval = removals.get((round + 1) % 3);

            List<Throwable> outcomes = race(
                    () -> firstRemoval.accept(first),
                    () -> secondRemoval.accept(second));

            List<Throwable> failures = outcomes.stream().filter(outcome -> outcome != null).toList();
            assertThat(failures).as("round %d: exactly one removal is refused", round).hasSize(1);
            assertThat(failures.get(0))
                    .isInstanceOfSatisfying(BusinessException.class,
                            exception -> assertThat(exception.status()).isEqualTo(HttpStatus.CONFLICT))
                    .hasMessage("Debe quedar al menos un administrador activo.");
            assertThat(activeAdmins()).as("round %d: one active admin remains", round).hasSize(1);

            createdAdmins.forEach(id -> professorRepository.findById(id).ifPresent(professorRepository::delete));
            createdAdmins.clear();
        }
    }

    /** Starts both actions together and returns, per action, null on success or what it threw. */
    private List<Throwable> race(Runnable left, Runnable right) throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Throwable>> futures = new ArrayList<>();
        for (Runnable action : List.of(left, right)) {
            futures.add(executor.submit((Callable<Throwable>) () -> {
                ready.countDown();
                start.await();
                try {
                    action.run();
                    return null;
                } catch (RuntimeException exception) {
                    return exception;
                }
            }));
        }
        assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
        start.countDown();
        List<Throwable> outcomes = new ArrayList<>();
        for (Future<Throwable> future : futures) {
            outcomes.add(future.get(30, TimeUnit.SECONDS));
        }
        return outcomes;
    }

    private List<Professor> activeAdmins() {
        return professorRepository.findAll().stream()
                .filter(professor -> professor.isActive() && "ADMIN".equals(professor.getAccessRole()))
                .toList();
    }

    private Long createAdmin() {
        int sequence = SEQUENCE.incrementAndGet();
        Professor admin = new Professor();
        admin.setName("Admin Concurrente " + sequence);
        admin.setEmail("admin.concurrente." + sequence + "@icesi.edu.co");
        admin.setDepartment(department());
        admin.setAccessRole("ADMIN");
        admin.setActive(true);
        Long id = professorRepository.save(admin).getId();
        createdAdmins.add(id);
        return id;
    }

    private PeopleDtos.DirectoryUserRequest demotion(Long id) {
        Professor admin = professorRepository.findById(id).orElseThrow();
        return new PeopleDtos.DirectoryUserRequest(admin.getName(), admin.getEmail(), department().getId(), "MANAGER", true);
    }

    private Department department() {
        return departmentRepository.findAll().get(0);
    }
}
