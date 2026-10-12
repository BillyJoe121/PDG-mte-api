package co.edu.icesi.pdg.mte.governance;
import co.edu.icesi.pdg.mte.audit.AuditService;
import co.edu.icesi.pdg.mte.security.ExternalUserContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import java.math.BigDecimal;
import java.nio.file.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class StrategyV2ServiceTest {
    JdbcTemplate jdbc; StrategyV2Service service; TrackingV2Service tracking; PoliciesV2Service policies; TransactionTemplate tx;
    @BeforeEach void setup() throws Exception {
        var dataSource=new DriverManagerDataSource("jdbc:h2:mem:"+UUID.randomUUID()+";MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE;NON_KEYWORDS=VALUE","sa","");
        jdbc=new JdbcTemplate(dataSource); tx=new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        // Use the real schema files, excluding PostgreSQL-only procedural DDL/partial indexes in this H2 fixture.
        // PostgreSQL/Flyway execution remains a separate integration check.
        for(String file:List.of("V1__initial_schema.sql","V8__add_strategy_lifecycle.sql","V9__add_directory_user_access.sql","V10__expand_e0_governance_catalogs.sql","V12__expand_e1_strategic_links.sql","V13__expand_e2_initiatives_and_planner.sql","V14__expand_e3_measurements_and_closure.sql","V20__governance_screen_timestamps.sql","V21__v2_kr_command_idempotency.sql")) {
            String sql=Files.readString(Path.of("src/main/resources/db/migration",file)).replace("\uFEFF", "").replace("TIMESTAMPTZ", "TIMESTAMP WITH TIME ZONE").replaceAll("(?s)DO \\$\\$.*?END \\$\\$;","").replaceAll("(?m)--.*$","");
            for(String statement:sql.split(";")) if(!statement.isBlank() && !statement.matches("(?is).*CREATE\\s+(UNIQUE\\s+)?INDEX.*WHERE.*")) jdbc.execute(statement);
        }
        jdbc.execute(Files.readString(Path.of("src/main/resources/db/migration/V22__v2_initiative_execution_log.sql")).replace("TIMESTAMPTZ","TIMESTAMP WITH TIME ZONE"));
        var assignmentDdl=java.util.regex.Pattern.compile("(?s)CREATE TABLE IF NOT EXISTS assignment_history \\(.*?\\);").matcher(Files.readString(Path.of("src/main/resources/db/migration/V11__expand_e6_identity_and_roles.sql")));
        assertThat(assignmentDdl.find()).isTrue(); jdbc.execute(assignmentDdl.group().replace("TIMESTAMPTZ","TIMESTAMP WITH TIME ZONE"));
        var policyDdl=java.util.regex.Pattern.compile("(?s)CREATE TABLE IF NOT EXISTS institutional_policy \\(.*?\\);").matcher(Files.readString(Path.of("src/main/resources/db/migration/V11__expand_e6_identity_and_roles.sql")));
        assertThat(policyDdl.find()).isTrue(); jdbc.execute(policyDdl.group().replace("TIMESTAMPTZ","TIMESTAMP WITH TIME ZONE"));
        for(String file:List.of("V14_1__reconcile_institutional_policy_index.sql","V15__expand_e4_dashboards_and_consistency.sql","V23__institutional_policy_registry_lock.sql")) jdbc.execute(Files.readString(Path.of("src/main/resources/db/migration",file)));
        var roleDdl=java.util.regex.Pattern.compile("(?s)CREATE TABLE IF NOT EXISTS user_role \\(.*?\\);").matcher(Files.readString(Path.of("src/main/resources/db/migration/V11__expand_e6_identity_and_roles.sql")));
        assertThat(roleDdl.find()).isTrue(); jdbc.execute(roleDdl.group().replace("TIMESTAMPTZ","TIMESTAMP WITH TIME ZONE").replace("num_nonnulls(faculty_id, school_id, department_id)","(CASE WHEN faculty_id IS NULL THEN 0 ELSE 1 END + CASE WHEN school_id IS NULL THEN 0 ELSE 1 END + CASE WHEN department_id IS NULL THEN 0 ELSE 1 END)"));
        for(String file:List.of("V19__expand_e9_operations_and_closure.sql","V24__monitoring_cut_scope_and_snapshot.sql","V25__monitoring_situation_lifecycle.sql","V26__corrective_action_history.sql","V27__monitoring_department_results.sql")) jdbc.execute(Files.readString(Path.of("src/main/resources/db/migration",file)).replace("\uFEFF","").replace("TIMESTAMPTZ","TIMESTAMP WITH TIME ZONE"));
        var exportDdl=java.util.regex.Pattern.compile("(?s)CREATE TABLE IF NOT EXISTS report_export \\(.*?\\);").matcher(Files.readString(Path.of("src/main/resources/db/migration/V16__expand_e5_reports_and_presentation.sql")));assertThat(exportDdl.find()).isTrue();jdbc.execute(exportDdl.group());
        var reportDdl=java.util.regex.Pattern.compile("(?s)CREATE TABLE IF NOT EXISTS school_final_report \\(.*?\\);").matcher(Files.readString(Path.of("src/main/resources/db/migration/V16__expand_e5_reports_and_presentation.sql")));assertThat(reportDdl.find()).isTrue();jdbc.execute(reportDdl.group());jdbc.execute(Files.readString(Path.of("src/main/resources/db/migration/V28__school_report_draft_snapshots.sql")));
        jdbc.execute("INSERT INTO faculty(id,name) VALUES(1,'Facultad')");
        jdbc.execute("INSERT INTO school(id,name,faculty_id) VALUES(1,'Escuela',1)");
        jdbc.execute("INSERT INTO department(id,name,school_id) VALUES(10,'D1',1),(20,'D2',1)");
        jdbc.execute("INSERT INTO professor(id,name,email,department_id,active) VALUES(1,'Gestor','gestor@test',10,TRUE),(2,'Responsable','responsable@test',10,TRUE),(3,'Ajeno','ajeno@test',20,TRUE)");
        jdbc.execute("INSERT INTO unit_of_measure(id,name,type) VALUES(1,'Porcentaje','PORCENTAJE')");
        jdbc.execute("INSERT INTO metric_definition(id,name,unit_of_measure_id,direction,source_description,frequency_days) VALUES(1,'Retención',1,'MAINTAIN','Registro',30)");
        jdbc.execute("INSERT INTO strategic_cycle(id,name,start_date,end_date,cadence_days) VALUES(1,'Ciclo','2026-01-01','2026-12-31',30)");
        jdbc.execute("INSERT INTO goal(id,name,description,expected_value,start_date,end_date,unit_of_measure_id) VALUES(1,'Meta','M',80,'2026-01-01','2026-12-31',1)");
        var context=new ExternalUserContext(1L,"gestor","gestor@test",List.of("MANAGER"),List.of(),1L,"Gestor","D1");
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(context,null,List.of(new SimpleGrantedAuthority("ROLE_MANAGER"))));
        var audit=mock(AuditService.class); tracking=new TrackingV2Service(jdbc,audit); policies=new PoliciesV2Service(jdbc,tracking,audit,new ObjectMapper()); service=new StrategyV2Service(jdbc,tracking,audit,new ObjectMapper(),policies);
    }
    @AfterEach void cleanup() { SecurityContextHolder.clearContext(); }
    StrategyV2Service.Create request(Long responsible,List<Long> goals) { return new StrategyV2Service.Create("Retener estudiantes","Tasa anual",1L,new BigDecimal("40"),new BigDecimal("70"),responsible,1L,"COMMITTED",goals,List.of()); }
    long create() { return ((Number)tx.execute(s->service.create(request(2L,List.of(1L)),"create-1")).get("id")).longValue(); }
    @Test void configuredApprovalMatrixCannotBeBypassedThroughDraftDefinitionPatch() {
        long id=create();jdbc.update("INSERT INTO institutional_policy(policy_key,policy_code,version,\"value\",approved_by) VALUES('KR_TARGET_APPROVAL','KR_TARGET_APPROVAL',1,CAST(? AS JSONB),1)","{\"rules\":[{\"status\":\"DRAFT\",\"cycleId\":1,\"requiresApproval\":true,\"approverRoles\":[\"RECTOR\"],\"allowSelfApproval\":false}],\"decisionRef\":\"https://example.org/matrix\",\"reason\":\"Synthetic decision\",\"confirmed\":true}");
        assertThatThrownBy(()->tx.execute(s->service.revise(id,new StrategyV2Service.Revision(1L,BigDecimal.TEN,new BigDecimal("80"),"Change"),0))).hasMessage("APPROVAL_REQUIRED");
        assertThat(jdbc.queryForObject("SELECT version FROM key_result WHERE id=?",Long.class,id)).isZero();assertThat(jdbc.queryForObject("SELECT target_value FROM key_result WHERE id=?",BigDecimal.class,id)).isEqualByComparingTo("70");assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM kr_version WHERE key_result_id=?",Long.class,id)).isEqualTo(1);
    }
    @Test void createsDirectKrAndReplaysWithoutCreatingObjectiveOrDuplicatingLinks() {
        long id=create(); var replay=tx.execute(s->service.create(request(2L,List.of(1L)),"create-1"));
        assertThat(((Number)replay.get("id")).longValue()).isEqualTo(id);
        assertThat(replay.get("status")).isEqualTo("DRAFT");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM objective",Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM key_result",Long.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM kr_goal_link",Long.class)).isEqualTo(1);
        assertThat(service.get(id).get("currentValue")).isNull(); assertThat(service.get(id).get("progressPercentage")).isNull();
        assertThat(tracking.keyResults().get(0).departmentId()).isEqualTo(10);
    }
    @Test void rejectsOtherDepartmentAndChangedIdempotentPayload() {
        assertThatThrownBy(()->tx.execute(s->service.create(request(3L,List.of()),"bad"))).hasMessage("OUT_OF_SCOPE");
        create(); assertThatThrownBy(()->tx.execute(s->service.create(request(2L,List.of()),"create-1"))).hasMessage("IDEMPOTENCY_KEY_REUSED");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM key_result",Long.class)).isEqualTo(1);
    }
    @Test void definitionRevisionPreservesMeasurementAndManualProgressAndRejectsStaleVersion() {
        long id=create();
        tx.execute(s->tracking.measure(id,new TrackingV2Service.MeasurementWrite(new BigDecimal("65"),java.time.Instant.parse("2026-01-01T12:00:00Z"),"Fuente")));
        tx.execute(s->tracking.declareProgress(id,new TrackingV2Service.ProgressWrite(new BigDecimal("37"),"ON_TRACK","Manual",0L)));
        var revised=tx.execute(s->service.revise(id,new StrategyV2Service.Revision(1L,new BigDecimal("35"),new BigDecimal("75"),"Nueva base"),1L));
        assertThat(((Number)revised.get("version")).longValue()).isEqualTo(2);
        assertThat(tracking.measurements(id)).hasSize(1); assertThat(tracking.progress(id)).hasSize(1);
        assertThat(service.versions(id)).hasSize(2);
        assertThatThrownBy(()->tx.execute(s->service.revise(id,new StrategyV2Service.Revision(1L,BigDecimal.TEN,BigDecimal.TEN,"Cambio"),1L))).hasMessage("VERSION_CONFLICT");
        assertThat(service.versions(id)).hasSize(2);
    }
    @Test void searchPaginatesAndDoesNotDuplicateMultiLinkedKr() {
        create(); var page=service.search("Retener",1L,"DRAFT",2L,1L,null,0,1);
        assertThat(page.totalElements()).isEqualTo(1); assertThat(page.items()).hasSize(1);
        assertThat(service.search(null,null,null,null,null,null,1,1).items()).isEmpty();
        assertThatThrownBy(()->service.search(null,null,null,null,null,null,0,101)).hasMessage("INVALID_PAGINATION");
    }
    @Test void directTrackingSummaryReadsKrOutsideFirstHundredAndKeepsMeasurementSeparate() {
        long id=create();jdbc.update("UPDATE key_result SET name='ZZZ fuera de los 100' WHERE id=?",id);authority("ADMIN");
        for(int i=0;i<101;i++)jdbc.update("INSERT INTO key_result(name,description,metric,base_value,target_value,unit_of_measure_id,status,active,version) VALUES(?,'Fixture','Porcentaje',0,100,1,'BORRADOR',TRUE,0)","AAA "+i);
        jdbc.update("INSERT INTO kr_measurement(key_result_id,measured_value,observed_at,registered_by) VALUES(?,25,'2026-01-01T12:00:00Z','Gestor')",id);
        jdbc.update("INSERT INTO kr_progress_entry(key_result_id,progress_percent,status,declared_by) VALUES(?,37,'ON_TRACK','Gestor')",id);
        assertThat(tracking.keyResults().stream().map(TrackingV2Service.Kr::id)).doesNotContain(id);
        var summary=tracking.keyResultSummary(id);assertThat(summary.id()).isEqualTo(id);assertThat(summary.measuredValue()).isEqualTo(new BigDecimal("25.0000"));assertThat(summary.progressPercent()).isEqualTo(new BigDecimal("37.00"));assertThat(summary.departmentId()).isEqualTo(10);
        var context=new ExternalUserContext(3L,"ajeno","ajeno@test",List.of("MANAGER"),List.of(),1L,"Ajeno","D2");SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(context,null,List.of(new SimpleGrantedAuthority("ROLE_MANAGER"))));
        assertThatThrownBy(()->tracking.keyResultSummary(id)).hasMessage("OUT_OF_SCOPE");assertThat(jdbc.queryForObject("SELECT version FROM key_result WHERE id=?",Long.class,id)).isZero();
    }
    @Test void trackingPagesReachOlderMeasurementsAndDeclarationsWithoutChangingKr() {
        long id=create();
        long originalVersion=jdbc.queryForObject("SELECT version FROM key_result WHERE id=?",Long.class,id);
        jdbc.update("INSERT INTO kr_measurement(key_result_id,measured_value,observed_at,registered_by) VALUES(?,0,'2026-01-01T12:00:00Z','Histórico')",id);
        jdbc.update("INSERT INTO kr_progress_entry(key_result_id,progress_percent,status,declared_by,declared_at) VALUES(?,0,'ON_TRACK','Histórico','2026-01-01T12:00:00Z')",id);
        for(int i=1;i<=100;i++){
            jdbc.update("INSERT INTO kr_measurement(key_result_id,measured_value,observed_at,registered_by) VALUES(?,?,'2026-01-02T12:00:00Z','Gestor')",id,i);
            jdbc.update("INSERT INTO kr_progress_entry(key_result_id,progress_percent,status,declared_by,declared_at) VALUES(?,?,'ON_TRACK','Gestor','2026-01-02T12:00:00Z')",id,i);
        }
        var first=tx.execute(s->tracking.measurementPage(id,0,20));assertThat(first.totalElements()).isEqualTo(101);assertThat(first.totalPages()).isEqualTo(6);assertThat(first.items().get(0).get("measuredValue")).isEqualTo(new BigDecimal("100.0000"));
        assertThat(tracking.measurementPage(id,5,20).items().get(0).get("registeredBy")).isEqualTo("Histórico");assertThat(tracking.progressPage(id,5,20).items().get(0).get("declaredBy")).isEqualTo("Histórico");assertThat(tracking.progressPage(id,0,20).totalElements()).isEqualTo(101);
        assertThat(tracking.progressPage(id,6,20).items()).isEmpty();assertThatThrownBy(()->tracking.measurementPage(id,-1,20)).hasMessage("INVALID_PAGINATION");assertThatThrownBy(()->tracking.progressPage(id,0,101)).hasMessage("INVALID_PAGINATION");
        jdbc.update("UPDATE key_result SET status='CERRADO',active=FALSE WHERE id=?",id);assertThat(tracking.measurementPage(id,5,20).items()).hasSize(1);assertThat(jdbc.queryForObject("SELECT version FROM key_result WHERE id=?",Long.class,id)).isEqualTo(originalVersion);
        var context=new ExternalUserContext(3L,"ajeno","ajeno@test",List.of("MANAGER"),List.of(),1L,"Ajeno","D2");SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(context,null,List.of(new SimpleGrantedAuthority("ROLE_MANAGER"))));
        assertThatThrownBy(()->tracking.measurementPage(id,0,20)).hasMessage("OUT_OF_SCOPE");assertThatThrownBy(()->tracking.progressPage(id,0,20)).hasMessage("OUT_OF_SCOPE");
    }
    @Test void backdatedMeasurementCanBeRegisteredBeyondTheRecentHistoryWindow() {
        long id=create();
        for(int i=0;i<100;i++) jdbc.update("INSERT INTO kr_measurement(key_result_id,measured_value,observed_at,registered_by) VALUES(?,50,'2026-06-01T12:00:00Z','Gestor')",id);
        var saved=tx.execute(s->tracking.measure(id,new TrackingV2Service.MeasurementWrite(new BigDecimal("45"),java.time.Instant.parse("2026-01-01T12:00:00Z"),"Registro atrasado")));
        assertThat(saved.get("measuredValue")).isEqualTo(new BigDecimal("45.0000"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM kr_measurement WHERE key_result_id=?",Long.class,id)).isEqualTo(101);
        assertThat(tracking.measurements(id)).hasSize(100);
    }
    @Test void measurementEvidenceIsScopedValidatedAndDoesNotChangeProgress() {
        long id=create();
        var evidence=new EvidenceV2Service(jdbc,tracking,mock(AuditService.class));
        long measurement=((Number)tx.execute(s->tracking.measure(id,new TrackingV2Service.MeasurementWrite(BigDecimal.TEN,java.time.Instant.parse("2026-01-01T12:00:00Z"),"Fuente"))).get("id")).longValue();
        var result=tx.execute(s->evidence.create("measurement",measurement,new EvidenceV2Service.Write("Informe","DOCUMENT","https://example.org/informe","a".repeat(64))));
        assertThat(result.get("measurementId")).isEqualTo(measurement);
        assertThat(evidence.list("measurement",measurement)).hasSize(1);
        assertThat(tracking.progress(id)).isEmpty();
        assertThatThrownBy(()->tx.execute(s->evidence.create("measurement",measurement,new EvidenceV2Service.Write("X","URL","javascript:alert(1)",null)))).hasMessage("INVALID_EVIDENCE_URL");
        assertThatThrownBy(()->tx.execute(s->evidence.create("measurement",measurement,new EvidenceV2Service.Write("X","URL","https://example.org","bad")))).hasMessage("INVALID_EVIDENCE_HASH");
        var context=new ExternalUserContext(3L,"ajeno","ajeno@test",List.of("MANAGER"),List.of(),1L,"Ajeno","D2");
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(context,null,List.of(new SimpleGrantedAuthority("ROLE_MANAGER"))));
        assertThatThrownBy(()->evidence.list("measurement",measurement)).hasMessage("OUT_OF_SCOPE");
        assertThatThrownBy(()->tx.execute(s->evidence.create("measurement",measurement,new EvidenceV2Service.Write("X","URL","https://example.org",null)))).hasMessage("OUT_OF_SCOPE");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM evidence",Long.class)).isEqualTo(1);
    }
    @Test void evidencePagesIncludeOlderInactiveReferencesAndRespectBothOwnerScopes() {
        long kr=create();long id=((Number)tx.execute(s->tracking.createInitiative(kr,new TrackingV2Service.InitiativeWrite(kr,10L,"Referencias","Descripción",2L))).get("id")).longValue();
        jdbc.update("INSERT INTO evidence(initiative_id,title,evidence_type,external_ref,active,created_by,created_at) VALUES(?,'Histórica','DOCUMENT','https://example.org/old',FALSE,'Gestor','2026-01-01T12:00:00Z')",id);
        for(int i=0;i<100;i++)jdbc.update("INSERT INTO evidence(initiative_id,title,evidence_type,external_ref,active,created_by,created_at) VALUES(?,?,'URL','https://example.org',TRUE,'Gestor','2026-01-02T12:00:00Z')",id,"Referencia "+i);
        var evidence=new EvidenceV2Service(jdbc,tracking,mock(AuditService.class));
        var first=tx.execute(s->evidence.search("initiative",id,0,20,null,null));assertThat(first.totalElements()).isEqualTo(101);assertThat(first.totalPages()).isEqualTo(6);assertThat(first.items().get(0).get("title")).isEqualTo("Referencia 99");
        assertThat(evidence.list("initiative",id).stream().map(r->r.get("title"))).doesNotContain("Histórica");assertThat(evidence.search("initiative",id,5,20,null,null).items().get(0).get("title")).isEqualTo("Histórica");
        assertThat(evidence.search("initiative",id,0,20,"DOCUMENT",false).totalElements()).isEqualTo(1);assertThat(evidence.search("initiative",id,0,20,"URL",false).items()).isEmpty();
        long measurement=((Number)tx.execute(s->tracking.measure(kr,new TrackingV2Service.MeasurementWrite(BigDecimal.TEN,java.time.Instant.parse("2026-01-01T12:00:00Z"),"Fuente"))).get("id")).longValue();
        tx.execute(s->evidence.create("measurement",measurement,new EvidenceV2Service.Write("Medición","URL","https://example.org/measure",null)));assertThat(evidence.search("measurement",measurement,0,20,null,null).totalElements()).isEqualTo(1);
        assertThatThrownBy(()->evidence.search("initiative",id,0,101,null,null)).hasMessage("INVALID_PAGINATION");assertThatThrownBy(()->evidence.search("initiative",id,0,20,"INVALID",null)).hasMessage("INVALID_EVIDENCE");
        tx.execute(s->tracking.deactivate(id,new TrackingV2Service.DeactivateWrite("Finalizada",true,0L)));assertThat(evidence.search("initiative",id,5,20,null,null).items()).hasSize(1);
        var context=new ExternalUserContext(3L,"ajeno","ajeno@test",List.of("MANAGER"),List.of(),1L,"Ajeno","D2");SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(context,null,List.of(new SimpleGrantedAuthority("ROLE_MANAGER"))));
        assertThatThrownBy(()->evidence.search("initiative",id,0,20,null,null)).hasMessage("OUT_OF_SCOPE");assertThatThrownBy(()->evidence.search("measurement",measurement,0,20,null,null)).hasMessage("OUT_OF_SCOPE");assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM kr_progress_entry",Long.class)).isZero();
    }
    @Test void initiativeEvidenceRemainsReadableAfterDeactivationButCannotBeAdded() {
        long id=create(); var evidence=new EvidenceV2Service(jdbc,tracking,mock(AuditService.class));
        long initiative=((Number)tx.execute(s->tracking.createInitiative(id,new TrackingV2Service.InitiativeWrite(id,10L,"Trabajo","Descripción",2L))).get("id")).longValue();
        tx.execute(s->evidence.create("initiative",initiative,new EvidenceV2Service.Write("Repositorio","REPOSITORY","https://example.org/repo",null)));
        tx.execute(s->tracking.deactivate(initiative,new TrackingV2Service.DeactivateWrite("Cancelada",true,0L)));
        assertThat(evidence.list("initiative",initiative)).hasSize(1);
        assertThatThrownBy(()->tx.execute(s->evidence.create("initiative",initiative,new EvidenceV2Service.Write("X","URL","https://example.org",null)))).hasMessage("INACTIVE_RESOURCE");
    }
    @Test void executionHistoryCanReadBeyondRecentHundredWithStableOrderAndScope() {
        long kr=create();long id=((Number)tx.execute(s->tracking.createInitiative(kr,new TrackingV2Service.InitiativeWrite(kr,10L,"Bitácora","Descripción",2L))).get("id")).longValue();
        jdbc.update("INSERT INTO initiative_execution_log(initiative_id,actor_id,execution_date,summary) VALUES(?,1,'2026-01-01','Registro histórico')",id);
        for(int i=0;i<100;i++)jdbc.update("INSERT INTO initiative_execution_log(initiative_id,actor_id,execution_date,summary) VALUES(?,1,'2026-01-02',?)",id,"Trabajo "+i);
        var execution=new ExecutionV2Service(jdbc,tracking,mock(AuditService.class));
        var first=tx.execute(s->execution.search(id,0,20));assertThat(first.totalElements()).isEqualTo(101);assertThat(first.totalPages()).isEqualTo(6);assertThat(first.items().get(0).get("summary")).isEqualTo("Trabajo 99");
        assertThat(execution.list(id).stream().map(r->r.get("summary"))).doesNotContain("Registro histórico");
        var last=tx.execute(s->execution.search(id,5,20));assertThat(last.items()).hasSize(1);assertThat(last.items().get(0).get("summary")).isEqualTo("Registro histórico");
        assertThatThrownBy(()->execution.search(id,0,101)).hasMessage("INVALID_PAGINATION");assertThatThrownBy(()->execution.search(id,-1,20)).hasMessage("INVALID_PAGINATION");
        tx.execute(s->tracking.deactivate(id,new TrackingV2Service.DeactivateWrite("Finalizada",true,0L)));assertThat(execution.search(id,5,20).items()).hasSize(1);
        var context=new ExternalUserContext(3L,"ajeno","ajeno@test",List.of("MANAGER"),List.of(),1L,"Ajeno","D2");
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(context,null,List.of(new SimpleGrantedAuthority("ROLE_MANAGER"))));
        assertThatThrownBy(()->execution.search(id,0,20)).hasMessage("OUT_OF_SCOPE");assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM initiative_progress_entry",Long.class)).isZero();
    }
    @Test void executionLogUsesInitiativeDomainAndPreservesIndependentPercentages() {
        long id=create(); var execution=new ExecutionV2Service(jdbc,tracking,mock(AuditService.class));
        long initiative=((Number)tx.execute(s->tracking.createInitiative(id,new TrackingV2Service.InitiativeWrite(id,10L,"Trabajo","Descripción",2L))).get("id")).longValue();
        var result=tx.execute(s->execution.create(initiative,new ExecutionV2Service.Write(java.time.LocalDate.of(2026,1,2),"Taller realizado","Acta disponible como evidencia")));
        assertThat(result.get("executionDate")).isEqualTo("2026-01-02");
        assertThat(execution.list(initiative)).hasSize(1); assertThat(tracking.initiativeProgress(initiative)).isEmpty(); assertThat(tracking.progress(id)).isEmpty();
        assertThatThrownBy(()->tx.execute(s->execution.create(initiative,new ExecutionV2Service.Write(java.time.LocalDate.now().plusDays(2),"Futuro",null)))).hasMessage("INVALID_EXECUTION_DATE");
        tx.execute(s->tracking.deactivate(initiative,new TrackingV2Service.DeactivateWrite("Cancelada",true,0L)));
        assertThat(execution.list(initiative)).hasSize(1);
        assertThatThrownBy(()->tx.execute(s->execution.create(initiative,new ExecutionV2Service.Write(java.time.LocalDate.of(2026,1,2),"Nuevo",null)))).hasMessage("INACTIVE_RESOURCE");
    }
    @Test void linksRetainIntervalsAndVersionsAndRejectDuplicateOrStaleChanges() {
        long id=create(); long link=jdbc.queryForObject("SELECT id FROM kr_goal_link WHERE key_result_id=?",Long.class,id);
        assertThatThrownBy(()->tx.execute(s->service.addLink(id,"goals",new StrategyV2Service.LinkWrite(1L,"Duplicado"),0L))).hasMessage("DUPLICATE_LINK");
        tx.execute(s->service.removeLink(id,"goals",link,"Cambio de enfoque",0L));
        assertThat(service.get(id).get("linkedGoalIds")).isEqualTo(List.of());
        tx.execute(s->service.addLink(id,"goals",new StrategyV2Service.LinkWrite(1L,"Restablecer"),1L));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM kr_goal_link WHERE key_result_id=?",Long.class,id)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM kr_goal_link WHERE key_result_id=? AND ended_at IS NULL",Long.class,id)).isEqualTo(1);
        assertThat(service.versions(id)).hasSize(3);
        assertThatThrownBy(()->tx.execute(s->service.reassign(id,new StrategyV2Service.OwnerWrite(1L,"Cambio"),0L))).hasMessage("VERSION_CONFLICT");
    }
    @Test void reassignmentPreservesPreviousOwnerAndRejectsCrossDepartment() {
        long id=create();
        assertThatThrownBy(()->tx.execute(s->service.reassign(id,new StrategyV2Service.OwnerWrite(3L,"Cambio"),0L))).hasMessage("OUT_OF_SCOPE");
        var result=tx.execute(s->service.reassign(id,new StrategyV2Service.OwnerWrite(1L,"Nueva responsabilidad"),0L));
        assertThat(result.get("responsibleId")).isEqualTo(1L);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM kr_assignment WHERE key_result_id=?",Long.class,id)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM kr_assignment WHERE key_result_id=? AND ended_at IS NULL",Long.class,id)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM assignment_history WHERE entity_id=?",Long.class,id)).isEqualTo(2);
        assertThat(tracking.progress(id)).isEmpty(); assertThat(service.versions(id)).hasSize(2);
    }
    @Test void initiativeReassignmentPreservesIntervalsAndRejectsStaleVersionOrContributor() {
        long id=create(); long initiative=((Number)tx.execute(s->tracking.createInitiative(id,new TrackingV2Service.InitiativeWrite(id,10L,"Trabajo","Descripción",2L))).get("id")).longValue();
        assertThatThrownBy(()->tx.execute(s->tracking.reassignInitiative(initiative,new TrackingV2Service.OwnerWrite(3L,"Cambio"),0L))).hasMessage("OUT_OF_SCOPE");
        tx.execute(s->tracking.reassignInitiative(initiative,new TrackingV2Service.OwnerWrite(1L,"Nueva responsabilidad"),0L));
        assertThat(tracking.initiativeAssignments(initiative)).hasSize(2);
        assertThat(tracking.initiativeProgress(initiative)).isEmpty();
        assertThatThrownBy(()->tx.execute(s->tracking.reassignInitiative(initiative,new TrackingV2Service.OwnerWrite(2L,"Cambio"),0L))).hasMessage("VERSION_CONFLICT");
        var context=new ExternalUserContext(1L,"gestor","gestor@test",List.of("CONTRIBUTOR"),List.of(),1L,"Gestor","D1");
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(context,null,List.of(new SimpleGrantedAuthority("ROLE_CONTRIBUTOR"))));
        assertThatThrownBy(()->tx.execute(s->tracking.reassignInitiative(initiative,new TrackingV2Service.OwnerWrite(2L,"Cambio"),1L))).hasMessage("FORBIDDEN");
    }
    @Test void classificationSnapshotsChangeWithoutAlteringMeasurementsOrManualProgress() {
        long id=create();
        tx.execute(s->tracking.declareProgress(id,new TrackingV2Service.ProgressWrite(new BigDecimal("37"),"ON_TRACK","Manual",0L)));
        jdbc.update("UPDATE key_result SET status='ACTIVO' WHERE id=?",id);
        assertThatThrownBy(()->tx.execute(s->service.classify(id,new StrategyV2Service.TypeWrite("LEARNING",""),1L))).hasMessage("REASON_REQUIRED");
        var result=tx.execute(s->service.classify(id,new StrategyV2Service.TypeWrite("LEARNING","Revisar aprendizaje"),1L));
        assertThat(result.get("type")).isEqualTo("LEARNING"); assertThat(result.get("progressPercentage")).isEqualTo(new BigDecimal("37.00"));
        assertThat(service.versions(id)).hasSize(2);
        long link=jdbc.queryForObject("SELECT id FROM kr_goal_link WHERE key_result_id=?",Long.class,id);
        assertThatThrownBy(()->tx.execute(s->service.removeLink(id,"goals",link,"Cambio",2L))).hasMessage("POLICY_UNSET");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM kr_goal_link WHERE key_result_id=? AND ended_at IS NULL",Long.class,id)).isEqualTo(1);
    }
    @Test void krDeactivationRequiresConfirmationAndKeepsEvidenceAndRelationsReadable() {
        long id=create();
        long measurement=((Number)tx.execute(s->tracking.measure(id,new TrackingV2Service.MeasurementWrite(BigDecimal.TEN,java.time.Instant.parse("2026-01-01T12:00:00Z"),"Fuente"))).get("id")).longValue();
        var evidence=new EvidenceV2Service(jdbc,tracking,mock(AuditService.class));
        tx.execute(s->evidence.create("measurement",measurement,new EvidenceV2Service.Write("Fuente","URL","https://example.org",null)));
        assertThatThrownBy(()->tx.execute(s->service.deactivate(id,new StrategyV2Service.DeactivateWrite("Fin de vigencia",false),0L))).hasMessage("CONFIRMATION_REQUIRED");
        assertThat(service.get(id).get("active")).isEqualTo(true);
        var result=tx.execute(s->service.deactivate(id,new StrategyV2Service.DeactivateWrite("Fin de vigencia",true),0L));
        assertThat(result.get("status")).isEqualTo("INACTIVE"); assertThat(evidence.list("measurement",measurement)).hasSize(1);
        assertThat(service.get(id).get("linkedGoalIds")).isEqualTo(List.of(1L)); assertThat(tracking.measurements(id)).hasSize(1); assertThat(service.versions(id)).hasSize(2);
        assertThatThrownBy(()->tx.execute(s->tracking.measure(id,new TrackingV2Service.MeasurementWrite(BigDecimal.TEN,java.time.Instant.parse("2026-01-01T12:00:00Z"),"Otra")))).hasMessage("INACTIVE_RESOURCE");
    }
    void authority(String role) {
        var context=SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(context,null,List.of(new SimpleGrantedAuthority("ROLE_"+role))));
    }
    void registerPolicy(int goals,int bets,int version) {
        authority("ADMIN"); tx.execute(s->policies.record(new PoliciesV2Service.Write(goals,bets,"https://example.org/decision","Decisión aprobada",true),version)); authority("MANAGER");
    }
    @Test void activationRequiresExplicitPolicyAndPreservesItsSnapshot() {
        long id=create(); assertThat(service.activationCheck(id).ready()).isFalse();
        assertThatThrownBy(()->tx.execute(s->service.transition(id,new StrategyV2Service.StatusWrite("ACTIVE","Activar",null),0L))).hasMessage("POLICY_UNSET");
        assertThat(service.get(id).get("status")).isEqualTo("DRAFT");
        registerPolicy(1,0,0); assertThat(service.activationCheck(id).ready()).isTrue();
        var result=tx.execute(s->service.transition(id,new StrategyV2Service.StatusWrite("ACTIVE","Inicio de ciclo",null),0L));
        assertThat(result.get("status")).isEqualTo("ACTIVE"); assertThat(tracking.progress(id)).isEmpty();
        var snapshot=(Map<?,?>)service.versions(id).get(0).get("snapshot"); assertThat(((Map<?,?>)snapshot.get("policy")).get("version")).isEqualTo(1);
        long link=jdbc.queryForObject("SELECT id FROM kr_goal_link WHERE key_result_id=?",Long.class,id);
        assertThatThrownBy(()->tx.execute(s->service.removeLink(id,"goals",link,"Quitar",1L))).hasMessage("POLICY_MINIMUM_NOT_MET");
        registerPolicy(0,0,1); tx.execute(s->service.removeLink(id,"goals",link,"Nueva decisión",1L));
        assertThat(service.get(id).get("linkedGoalIds")).isEqualTo(List.of()); assertThat(policies.history()).hasSize(2);
    }
    @Test void policyPublicationRequiresAdminConfirmationAndCurrentVersion() {
        var request=new PoliciesV2Service.Write(1,1,"https://example.org/decision","Decisión aprobada",true);
        assertThatThrownBy(()->tx.execute(s->policies.record(request,0))).hasMessage("FORBIDDEN");
        authority("ADMIN"); assertThatThrownBy(()->tx.execute(s->policies.record(new PoliciesV2Service.Write(1,1,"https://example.org","Decisión",false),0))).hasMessage("POLICY_CONFIRMATION_REQUIRED");
        tx.execute(s->policies.record(request,0));
        assertThatThrownBy(()->tx.execute(s->policies.record(request,0))).hasMessage("VERSION_CONFLICT");
        assertThat(policies.history()).hasSize(1);
    }
    @Test void unmetLinkMinimumsKeepKrInDraftAndInvalidPolicyDoesNotInventDefaults() {
        long id=create(); registerPolicy(1,1,0);
        assertThat(service.activationCheck(id).ready()).isFalse();
        assertThatThrownBy(()->tx.execute(s->service.activate(id,new StrategyV2Service.ActivationWrite("Activar"),0L))).hasMessage("KR_NOT_READY");
        assertThat(service.versions(id)).hasSize(1);
        jdbc.update("UPDATE institutional_policy SET value=CAST(? AS JSONB) WHERE policy_key=?","{}",PoliciesV2Service.KEY);
        assertThatThrownBy(()->service.activationCheck(id)).hasMessage("INVALID_POLICY");
        assertThat(service.get(id).get("status")).isEqualTo("DRAFT");
    }
    @Test void inactiveOwnerBlocksActivationAndFinalClosureCannotBypassReviewOrPermissions() {
        long id=create(); registerPolicy(1,0,0); jdbc.update("UPDATE professor SET active=FALSE WHERE id=2");
        assertThat(service.activationCheck(id).ready()).isFalse();
        assertThatThrownBy(()->tx.execute(s->service.transition(id,new StrategyV2Service.StatusWrite("ACTIVE","Activar",null),0L))).hasMessage("KR_DEFINITION_INCOMPLETE");
        assertThatThrownBy(()->tx.execute(s->service.transition(id,new StrategyV2Service.StatusWrite("CLOSED","Cierre",null),0L))).hasMessage("FINAL_REVIEW_REQUIRED");
        authority("CONTRIBUTOR");
        assertThatThrownBy(()->tx.execute(s->service.transition(id,new StrategyV2Service.StatusWrite("CLOSED","Cierre",null),0L))).hasMessage("FORBIDDEN");
        assertThat(service.get(id).get("status")).isEqualTo("DRAFT"); assertThat(service.versions(id)).hasSize(1);
    }
    MonitoringV2Service monitoring() { return new MonitoringV2Service(jdbc,tracking,policies,new ObjectMapper(),mock(AuditService.class)); }
    long monitoredCut(MonitoringV2Service monitoring) {
        long kr=create(); jdbc.update("UPDATE key_result SET status='ACTIVO' WHERE id=?",kr); registerPolicy(1,0,0);
        jdbc.update("INSERT INTO user_role(professor_id,role_name,school_id) VALUES(1,'DIRECTOR',1)");
        return ((Number)tx.execute(s->monitoring.create(new MonitoringV2Service.CutWrite(1L,1L,"CORTE-1",java.time.LocalDate.of(2026,10,1)))).get("id")).longValue();
    }
    @Test void consolidationRequiresValidationAndCreatesExactlyFourParallelBranchesWithReplay() {
        var monitoring=monitoring(); long id=monitoredCut(monitoring);
        var command=new MonitoringV2Service.ConsolidateWrite("cut-command","Revisión del corte");
        assertThatThrownBy(()->tx.execute(s->monitoring.consolidate(id,0,command))).hasMessage("CUT_NOT_VALIDATED"); assertThat(monitoring.branches(id)).isEmpty();
        var validated=tx.execute(s->monitoring.validate(id,0)); var branches=tx.execute(s->monitoring.consolidate(id,1,command));
        assertThat(branches).hasSize(4); assertThat(branches.stream().map(b->b.get("branchType"))).containsExactlyInAnyOrder("RECTOR","DECANO","DIRECTOR","DEPARTMENT");
        assertThat(branches.stream().map(b->b.get("asOf"))).containsOnly(validated.get("asOf"));
        var replay=tx.execute(s->monitoring.consolidate(id,1,command)); assertThat(replay).hasSize(4);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM monitoring_branch WHERE cut_id=?",Long.class,id)).isEqualTo(4);
    }
    @Test void branchReviewPersistsSituationsWithoutChangingOtherBranchesOrKrProgress() {
        var monitoring=monitoring(); long id=monitoredCut(monitoring); tx.execute(s->monitoring.validate(id,0)); var branches=tx.execute(s->monitoring.consolidate(id,1,new MonitoringV2Service.ConsolidateWrite("cut-command",null)));
        long director=((Number)branches.stream().filter(b->b.get("branchType").equals("DIRECTOR")).findFirst().orElseThrow().get("id")).longValue();
        tx.execute(s->monitoring.record(director,new MonitoringV2Service.EntryWrite(null,"Hallazgo",List.of("https://example.org/acta"),List.of(new MonitoringV2Service.SituationWrite("Riesgo","Descripción","HIGH","DECANO"))),true));
        assertThat(monitoring.entries(director)).hasSize(1); assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM situation WHERE origin_type='MONITORING_ENTRY'",Long.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM monitoring_branch WHERE cut_id=? AND status='OPEN'",Long.class,id)).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM kr_progress_entry",Long.class)).isZero(); assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM rector_review",Long.class)).isZero();
        assertThatThrownBy(()->tx.execute(s->monitoring.record(director,new MonitoringV2Service.EntryWrite(null,"Repetición",List.of(),List.of()),true))).hasMessage("BRANCH_NOT_OPEN");
    }
    @Test void generalManagerIsNotAnInstitutionalDirectorAndDepartmentScopeIsEnforced() {
        var monitoring=monitoring();
        assertThatThrownBy(()->tx.execute(s->monitoring.create(new MonitoringV2Service.CutWrite(1L,1L,"Ajeno",java.time.LocalDate.of(2026,10,1))))).hasMessage("FORBIDDEN");
        long id=monitoredCut(monitoring); tx.execute(s->monitoring.validate(id,0)); var branches=tx.execute(s->monitoring.consolidate(id,1,new MonitoringV2Service.ConsolidateWrite("cut-command",null)));
        jdbc.update("INSERT INTO user_role(professor_id,role_name,department_id) VALUES(2,'JEFE',10)");
        var context=new ExternalUserContext(2L,"responsable","responsable@test",List.of("CONTRIBUTOR"),List.of(),1L,"Responsable","D1");
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(context,null,List.of(new SimpleGrantedAuthority("ROLE_CONTRIBUTOR"))));
        long department=((Number)branches.stream().filter(b->b.get("branchType").equals("DEPARTMENT")).findFirst().orElseThrow().get("id")).longValue();
        assertThatThrownBy(()->tx.execute(s->monitoring.record(department,new MonitoringV2Service.EntryWrite(20L,"Ajeno",List.of(),List.of()),false))).hasMessage("OUT_OF_SCOPE");
        tx.execute(s->monitoring.record(department,new MonitoringV2Service.EntryWrite(10L,"Revisión departamental",List.of(),List.of()),false));
        assertThat(monitoring.entries(department)).hasSize(1); assertThat(monitoring.branches(id).stream().filter(b->b.get("status").equals("OPEN"))).hasSize(4);
    }
    @Test void newPolicyRequiresRevalidationAndPreservesBothCapturedVersions() {
        var monitoring=monitoring(); long id=monitoredCut(monitoring); tx.execute(s->monitoring.validate(id,0)); registerPolicy(1,0,1);
        var command=new MonitoringV2Service.ConsolidateWrite("cut-command",null);
        assertThatThrownBy(()->tx.execute(s->monitoring.consolidate(id,1,command))).hasMessage("CUT_VALIDATION_STALE");
        assertThat(monitoring.branches(id)).isEmpty(); tx.execute(s->monitoring.validate(id,1)); tx.execute(s->monitoring.consolidate(id,2,command));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM monitoring_cut_snapshot WHERE cut_id=?",Long.class,id)).isEqualTo(2);
    }
    long directorSituation(MonitoringV2Service monitoring) {
        long cut=monitoredCut(monitoring);tx.execute(s->monitoring.validate(cut,0));
        var branches=tx.execute(s->monitoring.consolidate(cut,1,new MonitoringV2Service.ConsolidateWrite("situations-command",null)));
        long branch=((Number)branches.stream().filter(b->b.get("branchType").equals("DIRECTOR")).findFirst().orElseThrow().get("id")).longValue();
        tx.execute(s->monitoring.record(branch,new MonitoringV2Service.EntryWrite(null,"Hallazgo original",List.of(),List.of(new MonitoringV2Service.SituationWrite("Riesgo","Descripción original","HIGH","DECANO"))),true));
        return jdbc.queryForObject("SELECT id FROM monitoring_situation",Long.class);
    }
    @Test void resolvingSituationRequiresEvidenceAndKeepsFindingHistoryAndProgress() {
        var monitoring=monitoring();long id=directorSituation(monitoring);
        assertThatThrownBy(()->tx.execute(s->monitoring.transitionSituation(id,0,new MonitoringV2Service.SituationTransition("RESOLVED","Solución",null)))).hasMessage("RESOLUTION_EVIDENCE_REQUIRED");
        assertThat(monitoring.situation(id).get("version")).isEqualTo(0L);
        tx.execute(s->monitoring.transitionSituation(id,0,new MonitoringV2Service.SituationTransition("IN_PROGRESS","Atención iniciada",null)));
        assertThatThrownBy(()->tx.execute(s->monitoring.transitionSituation(id,0,new MonitoringV2Service.SituationTransition("RESOLVED","Solución","https://example.org/acta")))).hasMessage("VERSION_CONFLICT");
        var resolved=tx.execute(s->monitoring.transitionSituation(id,1,new MonitoringV2Service.SituationTransition("RESOLVED","Solución","https://example.org/acta")));
        assertThat(resolved.get("description")).isEqualTo("Descripción original");assertThat(resolved.get("status")).isEqualTo("RESOLVED");
        assertThat((List<?>)resolved.get("history")).hasSize(2);assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM evidence WHERE situation_id IS NOT NULL",Long.class)).isEqualTo(1);
        tx.execute(s->monitoring.transitionSituation(id,2,new MonitoringV2Service.SituationTransition("CLOSED","Verificación final","https://example.org/cierre")));
        assertThatThrownBy(()->tx.execute(s->monitoring.transitionSituation(id,3,new MonitoringV2Service.SituationTransition("IN_PROGRESS","Reabrir",null)))).hasMessage("INVALID_TRANSITION");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM kr_progress_entry",Long.class)).isZero();assertThat(jdbc.queryForObject("SELECT status_result FROM monitoring_entry",String.class)).isEqualTo("WITH_SITUATIONS");
    }
    @Test void institutionalDestinationCanAttendSituationButGeneralAdminAndOtherDepartmentCannot() {
        var monitoring=monitoring();long id=directorSituation(monitoring);
        jdbc.update("UPDATE user_role SET ended_at=CURRENT_TIMESTAMP WHERE professor_id=1"); authority("ADMIN");
        assertThat(monitoring.situation(id).get("canManage")).isEqualTo(false);
        assertThatThrownBy(()->tx.execute(s->monitoring.transitionSituation(id,0,new MonitoringV2Service.SituationTransition("IN_PROGRESS","Atender",null)))).hasMessage("FORBIDDEN");
        jdbc.update("INSERT INTO user_role(professor_id,role_name,faculty_id) VALUES(1,'DECANO',1)");
        tx.execute(s->monitoring.transitionSituation(id,0,new MonitoringV2Service.SituationTransition("IN_PROGRESS","Atención por destino",null)));
        var context=new ExternalUserContext(3L,"ajeno","ajeno@test",List.of("CONTRIBUTOR"),List.of(),1L,"Ajeno","D2");
        jdbc.update("INSERT INTO user_role(professor_id,role_name,department_id) VALUES(3,'JEFE',20)");
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(context,null,List.of(new SimpleGrantedAuthority("ROLE_CONTRIBUTOR"))));
        assertThatThrownBy(()->monitoring.situation(id)).hasMessage("OUT_OF_SCOPE");
        assertThatThrownBy(()->tx.execute(s->monitoring.transitionSituation(id,1,new MonitoringV2Service.SituationTransition("RESOLVED","Ajeno","https://example.org/acta")))).hasMessage("OUT_OF_SCOPE");
    }
    @Test void departmentalCorrectiveRequiresLocalOwnerEvidenceAndCompletionBeforeResolution() {
        var monitoring=monitoring();long cut=monitoredCut(monitoring);tx.execute(s->monitoring.validate(cut,0));
        var branches=tx.execute(s->monitoring.consolidate(cut,1,new MonitoringV2Service.ConsolidateWrite("department-action",null)));
        long branch=((Number)branches.stream().filter(b->b.get("branchType").equals("DEPARTMENT")).findFirst().orElseThrow().get("id")).longValue();
        jdbc.update("INSERT INTO user_role(professor_id,role_name,department_id) VALUES(1,'JEFE',10)");
        tx.execute(s->monitoring.record(branch,new MonitoringV2Service.EntryWrite(10L,"Hallazgo",List.of(),List.of(new MonitoringV2Service.SituationWrite("Riesgo local","Descripción","HIGH",null))),false));
        long id=jdbc.queryForObject("SELECT id FROM monitoring_situation",Long.class);var due=java.time.LocalDate.now(java.time.ZoneId.of("America/Bogota")).plusDays(7);
        assertThatThrownBy(()->tx.execute(s->monitoring.createCorrective(id,new MonitoringV2Service.CorrectiveWrite(3L,due,"Acción")))).hasMessage("OUT_OF_SCOPE");
        var action=tx.execute(s->monitoring.createCorrective(id,new MonitoringV2Service.CorrectiveWrite(2L,due,"Revisar causa")));long actionId=((Number)action.get("id")).longValue();
        assertThatThrownBy(()->tx.execute(s->monitoring.transitionSituation(id,0,new MonitoringV2Service.SituationTransition("RESOLVED","Corregido","https://example.org/acta")))).hasMessage("CORRECTIVE_ACTIONS_PENDING");
        assertThatThrownBy(()->tx.execute(s->monitoring.transitionCorrective(id,actionId,0,new MonitoringV2Service.CorrectiveTransition("COMPLETED","Terminado",null)))).hasMessage("RESOLUTION_EVIDENCE_REQUIRED");
        tx.execute(s->monitoring.transitionCorrective(id,actionId,0,new MonitoringV2Service.CorrectiveTransition("IN_PROGRESS","Iniciado",null)));
        assertThatThrownBy(()->tx.execute(s->monitoring.transitionCorrective(id,actionId,0,new MonitoringV2Service.CorrectiveTransition("COMPLETED","Terminado","https://example.org/correctivo")))).hasMessage("VERSION_CONFLICT");
        var done=tx.execute(s->monitoring.transitionCorrective(id,actionId,1,new MonitoringV2Service.CorrectiveTransition("COMPLETED","Terminado","https://example.org/correctivo")));
        assertThat((List<?>)done.get("history")).hasSize(2);tx.execute(s->monitoring.transitionSituation(id,0,new MonitoringV2Service.SituationTransition("RESOLVED","Verificado","https://example.org/resolucion")));
        assertThat(monitoring.correctiveActions(id)).hasSize(1);assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM kr_progress_entry",Long.class)).isZero();
    }
    @Test void correctiveRejectsNonDepartmentalOriginAndAuditorRemainsReadOnly() {
        var monitoring=monitoring();long id=directorSituation(monitoring);
        assertThatThrownBy(()->tx.execute(s->monitoring.createCorrective(id,new MonitoringV2Service.CorrectiveWrite(2L,java.time.LocalDate.now().plusDays(7),"Plan")))).hasMessage("CORRECTIVE_ORIGIN_REQUIRED");
        jdbc.update("UPDATE user_role SET ended_at=CURRENT_TIMESTAMP WHERE professor_id=1");jdbc.update("INSERT INTO user_role(professor_id,role_name) VALUES(1,'AUDITOR')");
        assertThat(monitoring.situation(id).get("canManage")).isEqualTo(false);
        assertThatThrownBy(()->tx.execute(s->monitoring.transitionSituation(id,0,new MonitoringV2Service.SituationTransition("IN_PROGRESS","Auditor",null)))).hasMessage("FORBIDDEN");
        assertThat(monitoring.entries(jdbc.queryForObject("SELECT branch_id FROM monitoring_entry",Long.class))).hasSize(1);
    }
    @Test void cutReportPreservesCapturedValuesAndSeparatesInitiativeDeclarations() {
        var monitoring=monitoring();long cut=monitoredCut(monitoring);long kr=jdbc.queryForObject("SELECT id FROM key_result",Long.class);
        long initiative=((Number)tx.execute(s->tracking.createInitiative(kr,new TrackingV2Service.InitiativeWrite(kr,10L,"Trabajo","Descripción",2L))).get("id")).longValue();
        jdbc.update("INSERT INTO initiative_progress_entry(initiative_id,progress_percent,status,comment,actor_id) VALUES(?,30,'IN_PROGRESS','Declaración',2)",initiative);
        tx.execute(s->monitoring.validate(cut,0));
        jdbc.update("INSERT INTO kr_progress_entry(key_result_id,progress_percent,status,comment,declared_by) VALUES(?,90,'ON_TRACK','Posterior','Gestor')",kr);
        jdbc.update("UPDATE initiative SET title='Nombre posterior' WHERE id=?",initiative);
        var report=tx.execute(s->monitoring.report(cut,null));
        var krs=(List<Map<String,Object>>)report.get("keyResults");var initiatives=(List<Map<String,Object>>)report.get("initiatives");
        assertThat(krs).hasSize(1);assertThat(krs.get(0).get("manualProgress")).isNull();assertThat(krs.get(0).get("responsible")).isEqualTo("Responsable");
        assertThat(initiatives).hasSize(1);assertThat(new BigDecimal(initiatives.get(0).get("manualProgress").toString())).isEqualByComparingTo("30");assertThat(initiatives.get(0).get("title")).isEqualTo("Trabajo");
        assertThat(report.get("tasksCaptured")).isEqualTo(true);assertThat((List<?>)report.get("tasks")).isEmpty();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM kr_progress_entry",Long.class)).isEqualTo(1);
    }
    @Test void reportRechecksDepartmentScopeAndDoesNotExposeOtherDepartmentRows() {
        var monitoring=monitoring();long cut=monitoredCut(monitoring);
        long other=((Number)tx.execute(s->service.create(request(2L,List.of(1L)),"second-report-kr")).get("id")).longValue();
        jdbc.update("UPDATE kr_assignment SET professor_id=3 WHERE key_result_id=?",other);jdbc.update("UPDATE key_result SET status='ACTIVO' WHERE id=?",other);tx.execute(s->monitoring.validate(cut,0));
        assertThat((List<?>)monitoring.report(cut,null).get("keyResults")).hasSize(2);
        jdbc.update("UPDATE user_role SET ended_at=CURRENT_TIMESTAMP WHERE professor_id=1");jdbc.update("INSERT INTO user_role(professor_id,role_name,department_id) VALUES(1,'JEFE',10)");
        var scoped=monitoring.report(cut,null);assertThat((List<?>)scoped.get("keyResults")).hasSize(1);assertThat((List<?>)scoped.get("departments")).hasSize(1);
        assertThatThrownBy(()->monitoring.report(cut,20L)).hasMessage("OUT_OF_SCOPE");
        jdbc.update("UPDATE user_role SET ended_at=CURRENT_TIMESTAMP WHERE professor_id=1");
        assertThatThrownBy(()->monitoring.report(cut,null)).hasMessage("OUT_OF_SCOPE");
    }
    @Test void csvExportRecordsItsHashAndNeutralizesSpreadsheetLabelsWithoutInventingProgress() {
        var monitoring=monitoring();long cut=monitoredCut(monitoring);jdbc.update("UPDATE key_result SET name=' =SUM(1,2)' WHERE id=(SELECT MIN(id) FROM key_result)");tx.execute(s->monitoring.validate(cut,0));
        byte[] bytes=tx.execute(s->monitoring.exportCsv(cut,10L));String csv=new String(bytes,java.nio.charset.StandardCharsets.UTF_8);
        assertThat(csv).startsWith("as_of,capture_version,section,capture_state,");assertThat(csv).contains("\"' =SUM(1,2)\"");assertThat(csv).contains("\"initiatives\",\"EMPTY\"");assertThat(csv).contains("\"tasks\",\"EMPTY\"");
        assertThat(jdbc.queryForObject("SELECT file_size_bytes FROM report_export",Long.class)).isEqualTo(bytes.length);assertThat(jdbc.queryForObject("SELECT file_sha256 FROM report_export",String.class)).hasSize(64);
        assertThatThrownBy(()->tx.execute(s->monitoring.exportCsv(cut,99L))).hasMessage("OUT_OF_SCOPE");assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM report_export",Long.class)).isEqualTo(1);assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM kr_progress_entry",Long.class)).isZero();
    }
    @Test void departmentBranchWaitsForEveryCapturedParticipantAndRejectsStaleOrForeignEntries() {
        var monitoring=monitoring();long cut=monitoredCut(monitoring);tx.execute(s->monitoring.validate(cut,0));var branches=tx.execute(s->monitoring.consolidate(cut,1,new MonitoringV2Service.ConsolidateWrite("deliveries",null)));
        long branch=((Number)branches.stream().filter(b->b.get("branchType").equals("DEPARTMENT")).findFirst().orElseThrow().get("id")).longValue();assertThat(monitoring.departmentResults(branch)).hasSize(2);
        jdbc.update("INSERT INTO user_role(professor_id,role_name,department_id) VALUES(1,'JEFE',10)");
        long entry=((Number)tx.execute(s->monitoring.record(branch,new MonitoringV2Service.EntryWrite(10L,"Resultado D1",List.of(),List.of()),false)).get("entryId")).longValue();
        assertThatThrownBy(()->tx.execute(s->monitoring.submitDepartment(branch,0,new MonitoringV2Service.DepartmentDelivery(20L,entry,true)))).hasMessage("OUT_OF_SCOPE");
        assertThatThrownBy(()->tx.execute(s->monitoring.submitDepartment(branch,0,new MonitoringV2Service.DepartmentDelivery(10L,entry,false)))).hasMessage("CONFIRMATION_REQUIRED");
        tx.execute(s->monitoring.submitDepartment(branch,0,new MonitoringV2Service.DepartmentDelivery(10L,entry,true)));
        assertThat(jdbc.queryForObject("SELECT status FROM monitoring_branch WHERE id=?",String.class,branch)).isEqualTo("OPEN");
        assertThatThrownBy(()->tx.execute(s->monitoring.submitDepartment(branch,0,new MonitoringV2Service.DepartmentDelivery(10L,entry,true)))).hasMessage("VERSION_CONFLICT");
        long newer=((Number)tx.execute(s->monitoring.record(branch,new MonitoringV2Service.EntryWrite(10L,"Actualización D1",List.of(),List.of()),false)).get("entryId")).longValue();
        assertThatThrownBy(()->tx.execute(s->monitoring.submitDepartment(branch,1,new MonitoringV2Service.DepartmentDelivery(10L,entry,true)))).hasMessage("DEPARTMENT_ENTRY_CHANGED");
        tx.execute(s->monitoring.submitDepartment(branch,1,new MonitoringV2Service.DepartmentDelivery(10L,newer,true)));
        jdbc.update("INSERT INTO user_role(professor_id,role_name,department_id) VALUES(1,'JEFE',20)");
        long entry2=((Number)tx.execute(s->monitoring.record(branch,new MonitoringV2Service.EntryWrite(20L,"Resultado D2",List.of(),List.of()),false)).get("entryId")).longValue();
        tx.execute(s->monitoring.submitDepartment(branch,0,new MonitoringV2Service.DepartmentDelivery(20L,entry2,true)));
        assertThat(jdbc.queryForObject("SELECT status FROM monitoring_branch WHERE id=?",String.class,branch)).isEqualTo("SUBMITTED");assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM monitoring_department_delivery",Long.class)).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM monitoring_branch WHERE cut_id=? AND status='OPEN'",Long.class,cut)).isEqualTo(3);assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM kr_progress_entry",Long.class)).isZero();
    }
    @Test void departmentParticipantsRemainFrozenAndReadScopeUsesCurrentRoles() {
        var monitoring=monitoring();long cut=monitoredCut(monitoring);tx.execute(s->monitoring.validate(cut,0));jdbc.update("INSERT INTO department(id,name,school_id) VALUES(30,'Nuevo',1)");
        var branches=tx.execute(s->monitoring.consolidate(cut,1,new MonitoringV2Service.ConsolidateWrite("frozen-departments",null)));long branch=((Number)branches.stream().filter(b->b.get("branchType").equals("DEPARTMENT")).findFirst().orElseThrow().get("id")).longValue();
        assertThat(monitoring.departmentResults(branch)).hasSize(2);jdbc.update("INSERT INTO user_role(professor_id,role_name,department_id) VALUES(1,'JEFE',30)");
        assertThatThrownBy(()->tx.execute(s->monitoring.record(branch,new MonitoringV2Service.EntryWrite(30L,"Nuevo departamento",List.of(),List.of()),false))).hasMessage("OUT_OF_SCOPE");
        jdbc.update("UPDATE user_role SET ended_at=CURRENT_TIMESTAMP WHERE professor_id=1");jdbc.update("INSERT INTO user_role(professor_id,role_name,department_id) VALUES(1,'JEFE',10)");
        var scoped=monitoring.departmentResults(branch);assertThat(scoped).hasSize(1);assertThat(scoped.get(0).get("departmentId")).isEqualTo(10L);assertThat(scoped.get(0).get("latestEntryId")).isNull();
        tx.execute(s->monitoring.record(branch,new MonitoringV2Service.EntryWrite(10L,"Resultado local",List.of(),List.of()),false));assertThat(monitoring.departmentResults(branch).get(0).get("latestObservation")).isEqualTo("Resultado local");
    }
    @Test void departmentDeliveryHistoryReachesOldEvidenceAndUsesCurrentReadScope() {
        var monitoring=monitoring();long cut=monitoredCut(monitoring);tx.execute(s->monitoring.validate(cut,0));
        var branches=tx.execute(s->monitoring.consolidate(cut,1,new MonitoringV2Service.ConsolidateWrite("delivery-history",null)));
        long branch=((Number)branches.stream().filter(b->b.get("branchType").equals("DEPARTMENT")).findFirst().orElseThrow().get("id")).longValue();
        jdbc.update("INSERT INTO user_role(professor_id,role_name,department_id) VALUES(1,'JEFE',10)");
        long entry=((Number)tx.execute(s->monitoring.record(branch,new MonitoringV2Service.EntryWrite(10L,"Observación original",List.of("https://example.org/original"),List.of()),false)).get("entryId")).longValue();
        tx.execute(s->monitoring.submitDepartment(branch,0,new MonitoringV2Service.DepartmentDelivery(10L,entry,true)));
        long newer=((Number)tx.execute(s->monitoring.record(branch,new MonitoringV2Service.EntryWrite(10L,"Observación nueva",List.of(),List.of()),false)).get("entryId")).longValue();
        tx.execute(s->monitoring.submitDepartment(branch,1,new MonitoringV2Service.DepartmentDelivery(10L,newer,true)));
        long result=jdbc.queryForObject("SELECT id FROM monitoring_department_result WHERE branch_id=? AND department_id=10",Long.class,branch);
        for(int version=3;version<=101;version++)jdbc.update("INSERT INTO monitoring_department_delivery(result_id,version,entry_id,actor_id) VALUES(?,?,?,1)",result,version,newer);
        jdbc.update("UPDATE monitoring_department_result SET version=101 WHERE id=?",result);
        jdbc.update("INSERT INTO user_role(professor_id,role_name,department_id) VALUES(1,'JEFE',20)");
        long entry2=((Number)tx.execute(s->monitoring.record(branch,new MonitoringV2Service.EntryWrite(20L,"D2",List.of(),List.of()),false)).get("entryId")).longValue();tx.execute(s->monitoring.submitDepartment(branch,0,new MonitoringV2Service.DepartmentDelivery(20L,entry2,true)));
        var first=tx.execute(s->monitoring.departmentDeliveryPage(branch,10,0,20));assertThat(first.totalElements()).isEqualTo(101);assertThat(first.totalPages()).isEqualTo(6);assertThat(first.items().get(0).get("version")).isEqualTo(101L);assertThat(first.items().get(0).get("current")).isEqualTo(true);
        var old=monitoring.departmentDeliveryPage(branch,10,5,20).items().get(0);assertThat(old.get("observation")).isEqualTo("Observación original");assertThat(old.get("current")).isEqualTo(false);assertThat(old.get("evidenceUrls")).isEqualTo(List.of("https://example.org/original"));assertThat(old.get("deliveredBy")).isEqualTo("Gestor");
        assertThat(monitoring.departmentDeliveryPage(branch,10,6,20).items()).isEmpty();assertThat(monitoring.departmentDeliveryPage(branch,20,0,20).totalElements()).isEqualTo(1);assertThatThrownBy(()->monitoring.departmentDeliveryPage(branch,30,0,20)).hasMessage("OUT_OF_SCOPE");assertThatThrownBy(()->monitoring.departmentDeliveryPage(branch,10,0,101)).hasMessage("INVALID_PAGINATION");
        assertThat(jdbc.queryForObject("SELECT status FROM monitoring_branch WHERE id=?",String.class,branch)).isEqualTo("SUBMITTED");assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM kr_progress_entry",Long.class)).isZero();
        jdbc.update("UPDATE user_role SET ended_at=CURRENT_TIMESTAMP WHERE professor_id=1");jdbc.update("INSERT INTO user_role(professor_id,role_name,department_id) VALUES(1,'JEFE',10)");
        assertThat(monitoring.departmentDeliveryPage(branch,10,0,20).totalElements()).isEqualTo(101);assertThatThrownBy(()->monitoring.departmentDeliveryPage(branch,20,0,20)).hasMessage("OUT_OF_SCOPE");
    }
    SchoolReportV2Service schoolReports(MonitoringV2Service monitoring){try{jdbc.execute(Files.readString(Path.of("src/main/resources/db/migration/V29__report_format_policy_lock.sql")));jdbc.execute(Files.readString(Path.of("src/main/resources/db/migration/V30__school_report_publication.sql")));jdbc.execute(Files.readString(Path.of("src/main/resources/db/migration/V31__report_rector_review_and_finish.sql")));}catch(Exception e){throw new IllegalStateException(e);}return new SchoolReportV2Service(jdbc,tracking,monitoring,new ObjectMapper(),mock(AuditService.class),new ReportFormatV2Service(jdbc,tracking,new ObjectMapper(),mock(AuditService.class)));}
    @Test void rectorReviewPreservesEvidenceAndFinRequiresTheLatestApprovedVersion(){
        var monitoring=monitoring();var reports=schoolReports(monitoring);long cut=completeReportCut(monitoring);authority("ADMIN");
        var formats=new ReportFormatV2Service(jdbc,tracking,new ObjectMapper(),mock(AuditService.class));tx.execute(s->formats.record(new ReportFormatV2Service.Write("Formato",List.of("TITLE","CUT"),List.of("Rectoría"),"https://example.org/decision","Decisión",true),0));
        long first=((Number)tx.execute(s->reports.create(new SchoolReportV2Service.Draft(cut,"Primero","Logros","Dificultades","Conclusiones","review-first"),0)).get("id")).longValue();
        var observed=new SchoolReportV2Service.Review(1,"OBSERVED","Faltan soportes adicionales",List.of("https://example.org/observed"),"review-observed");
        assertThatThrownBy(()->tx.execute(s->reports.review(first,0,observed))).hasMessage("INVALID_TRANSITION");tx.execute(s->reports.publish(first,0,new SchoolReportV2Service.Publish(1,"publish-first",true)));
        assertThatThrownBy(()->tx.execute(s->reports.review(first,1,new SchoolReportV2Service.Review(1,"APPROVED","Comentario",List.of(),"invalid")))).hasMessage("EVIDENCE_REQUIRED");
        assertThatThrownBy(()->tx.execute(s->reports.review(first,0,observed))).hasMessage("VERSION_CONFLICT");var observation=tx.execute(s->reports.review(first,1,observed));assertThat(observation.report().get("status")).isEqualTo("OBSERVED");assertThat(observation.review().get("evidenceUrls")).isEqualTo(List.of("https://example.org/observed"));
        assertThatThrownBy(()->tx.execute(s->reports.finish(first,2,new SchoolReportV2Service.Finish(1,((Number)observation.review().get("id")).longValue(),"premature",true)))).hasMessage("APPROVED_REVIEW_REQUIRED");assertThat(jdbc.queryForObject("SELECT status FROM monitoring_cut WHERE id=?",String.class,cut)).isEqualTo("CONSOLIDATED");
        assertThat(tx.execute(s->reports.review(first,1,observed)).review().get("id")).isEqualTo(observation.review().get("id"));assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM rector_review",Long.class)).isEqualTo(1);
        assertThatThrownBy(()->tx.execute(s->reports.review(first,2,new SchoolReportV2Service.Review(1,"OBSERVED","Otro comentario",observed.evidenceUrls(),observed.idempotencyKey())))).hasMessage("IDEMPOTENCY_CONFLICT");assertThatThrownBy(()->tx.execute(s->reports.review(first,2,new SchoolReportV2Service.Review(1,"APPROVED","Comentario",List.of("javascript:alert(1)"),"unsafe")))).hasMessage("INVALID_EVIDENCE_URL");
        var approved=new SchoolReportV2Service.Review(1,"APPROVED","Soportes revisados",List.of("https://example.org/approved"),"review-approved");var approval=tx.execute(s->reports.review(first,2,approved));long approvalId=((Number)approval.review().get("id")).longValue();assertThat(approval.report().get("canFinish")).isEqualTo(true);
        for(int sequence=3;sequence<=102;sequence++)jdbc.update("INSERT INTO rector_review(monitoring_cut_id,report_id,report_version,review_version,status,reviewed_by,actor_id,command_key,observations,evidence_urls) SELECT monitoring_cut_id,report_id,report_version,?,status,reviewed_by,actor_id,?,observations,evidence_urls FROM rector_review WHERE id=?",sequence,"fixture-"+sequence,approvalId);
        var old=reports.reviews(first,5,20);assertThat(old.totalElements()).isEqualTo(102);assertThat(old.items()).hasSize(2);assertThat(old.items().get(1).get("comment")).isEqualTo(observed.comment());assertThat(old.items().get(1).get("evidenceUrls")).isEqualTo(observed.evidenceUrls());assertThat(reports.reviews(first,6,20).items()).isEmpty();assertThatThrownBy(()->reports.reviews(first,-1,20)).hasMessage("INVALID_PAGINATION");
        long second=((Number)tx.execute(s->reports.create(new SchoolReportV2Service.Draft(cut,"Segundo","Logros","Dificultades","Conclusiones","review-second"),1)).get("id")).longValue();
        assertThat(reports.get(first).get("canFinish")).isEqualTo(false);assertThatThrownBy(()->tx.execute(s->reports.finish(first,3,new SchoolReportV2Service.Finish(1,approvalId,"stale-report",true)))).hasMessage("APPROVED_REVIEW_REQUIRED");
        tx.execute(s->reports.publish(second,0,new SchoolReportV2Service.Publish(1,"publish-second",true)));
        jdbc.update("UPDATE user_role SET ended_at=CURRENT_TIMESTAMP WHERE professor_id=1 AND role_name='RECTOR'");assertThatThrownBy(()->tx.execute(s->reports.review(second,1,new SchoolReportV2Service.Review(2,"APPROVED","Soportes revisados",List.of("https://example.org/approved"),"second-approved")))).hasMessage("FORBIDDEN");assertThat(reports.get(second).get("canReview")).isEqualTo(false);
        jdbc.update("INSERT INTO user_role(professor_id,role_name) VALUES(1,'RECTOR')");var finalApproval=tx.execute(s->reports.review(second,1,new SchoolReportV2Service.Review(2,"APPROVED","Soportes revisados",List.of("https://example.org/approved"),"second-approved")));long finalId=((Number)finalApproval.review().get("id")).longValue();
        assertThatThrownBy(()->tx.execute(s->reports.finish(second,2,new SchoolReportV2Service.Finish(2,approvalId,"wrong-review",true)))).hasMessage("APPROVED_REVIEW_REQUIRED");var close=new SchoolReportV2Service.Finish(2,finalId,"finish-second",true);var finished=tx.execute(s->reports.finish(second,2,close));assertThat(finished.get("finishedAt")).isNotNull();assertThat(finished.get("canReview")).isEqualTo(false);assertThat(finished.get("canFinish")).isEqualTo(false);assertThat(jdbc.queryForObject("SELECT status FROM monitoring_cut WHERE id=?",String.class,cut)).isEqualTo("FIN");assertThat(tx.execute(s->reports.finish(second,2,close)).get("finalReviewId")).isEqualTo(finalId);
        assertThatThrownBy(()->tx.execute(s->reports.create(new SchoolReportV2Service.Draft(cut,"Tercero","Logros","Dificultades","Conclusiones","after-fin"),2))).hasMessage("BRANCH_INCOMPLETE");assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM cycle_closure",Long.class)).isZero();assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM kr_progress_entry",Long.class)).isZero();
        jdbc.update("UPDATE user_role SET ended_at=CURRENT_TIMESTAMP WHERE professor_id=1");jdbc.update("INSERT INTO user_role(professor_id,role_name,department_id) VALUES(1,'JEFE',20)");authority("MANAGER");assertThatThrownBy(()->reports.reviews(first,0,20)).hasMessage("OUT_OF_SCOPE");assertThatThrownBy(()->tx.execute(s->reports.finish(second,2,close))).hasMessage("OUT_OF_SCOPE");assertThat(jdbc.queryForObject("SELECT status FROM monitoring_cut WHERE id=?",String.class,cut)).isEqualTo("FIN");
    }
    @Test void schoolReportPublicationRequiresCurrentFormatAndFreezesItsDecision(){
        var monitoring=monitoring();var reports=schoolReports(monitoring);long cut=completeReportCut(monitoring);
        long id=((Number)tx.execute(s->reports.create(new SchoolReportV2Service.Draft(cut,"Informe","Logros","Dificultades","Conclusiones","publication-draft"),0)).get("id")).longValue();
        var command=new SchoolReportV2Service.Publish(1,"publication-1",true);
        assertThatThrownBy(()->tx.execute(s->reports.publish(id,0,command))).hasMessage("POLICY_UNSET");assertThat(reports.get(id).get("status")).isEqualTo("DRAFT");
        authority("ADMIN");var formats=new ReportFormatV2Service(jdbc,tracking,new ObjectMapper(),mock(AuditService.class));
        var policy=new ReportFormatV2Service.Write("Plantilla de prueba",List.of("TITLE","CUT","BRANCH_RESULTS","CORRECTIVES"),List.of("Rectoría"),"https://example.org/decision","Decisión de prueba",true);tx.execute(s->formats.record(policy,0));
        assertThatThrownBy(()->tx.execute(s->reports.publish(id,9,command))).hasMessage("VERSION_CONFLICT");assertThatThrownBy(()->tx.execute(s->reports.publish(id,0,new SchoolReportV2Service.Publish(2,"publication-1",true)))).hasMessage("VERSION_CONFLICT");
        var published=tx.execute(s->reports.publish(id,0,command));assertThat(published.get("status")).isEqualTo("PUBLISHED");assertThat(published.get("transitionVersion")).isEqualTo(1L);assertThat(published.get("publishedAt")).isNotNull();assertThat(((Map<?,?>)published.get("publicationFormat")).get("version")).isEqualTo(1);
        tx.execute(s->formats.record(new ReportFormatV2Service.Write("Nueva plantilla",policy.requiredSections(),List.of("Nuevo destinatario"),policy.decisionRef(),policy.reason(),true),1));
        assertThat(((Map<?,?>)reports.get(id).get("publicationFormat")).get("templateName")).isEqualTo("Plantilla de prueba");assertThat(tx.execute(s->reports.publish(id,0,command)).get("status")).isEqualTo("PUBLISHED");
        assertThatThrownBy(()->tx.execute(s->reports.publish(id,1,new SchoolReportV2Service.Publish(1,"other",true)))).hasMessage("IDEMPOTENCY_CONFLICT");
        long newId=((Number)tx.execute(s->reports.create(new SchoolReportV2Service.Draft(cut,"Nuevo","Logros","Dificultades","Conclusiones","publication-draft-2"),1)).get("id")).longValue();
        jdbc.update("UPDATE user_role SET ended_at=CURRENT_TIMESTAMP WHERE professor_id=1 AND role_name='DIRECTOR'");
        assertThatThrownBy(()->tx.execute(s->reports.publish(newId,0,new SchoolReportV2Service.Publish(2,"new",true)))).hasMessage("FORBIDDEN");assertThat(reports.get(newId).get("canPublish")).isEqualTo(false);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM rector_review",Long.class)).isZero();assertThat(jdbc.queryForObject("SELECT status FROM monitoring_cut WHERE id=?",String.class,cut)).isEqualTo("CONSOLIDATED");
        jdbc.update("UPDATE institutional_policy SET \"value\"=CAST(? AS JSONB) WHERE policy_key=? AND version=2","{\"templateName\":\"Incompleta\"}",ReportFormatV2Service.KEY);assertThat(((Map<?,?>)reports.get(id).get("publicationFormat")).get("templateName")).isEqualTo("Plantilla de prueba");assertThat(reports.get(id).get("formatPolicyApproved")).isEqualTo(true);
    }
    @Test void monitoringEntryPagesReachOldFindingsAndRespectDepartmentRoles(){
        var monitoring=monitoring();long cut=monitoredCut(monitoring);tx.execute(s->monitoring.validate(cut,0));var branches=tx.execute(s->monitoring.consolidate(cut,1,new MonitoringV2Service.ConsolidateWrite("entry-pages",null)));
        long branch=((Number)branches.stream().filter(b->b.get("branchType").equals("DEPARTMENT")).findFirst().orElseThrow().get("id")).longValue();jdbc.update("INSERT INTO user_role(professor_id,role_name,department_id) VALUES(1,'JEFE',10)");
        long original=((Number)tx.execute(s->monitoring.record(branch,new MonitoringV2Service.EntryWrite(10L,"Observación antigua",List.of("https://example.org/evidence"),List.of(new MonitoringV2Service.SituationWrite("Hallazgo antiguo","Descripción","HIGH",null))),false)).get("entryId")).longValue();
        jdbc.update("UPDATE monitoring_entry SET created_at='2026-01-01T12:00:00Z' WHERE id=?",original);
        for(int i=0;i<100;i++)jdbc.update("INSERT INTO monitoring_entry(branch_id,department_id,actor_email,actor_name,actor_role,observation,created_at) VALUES(?,10,'gestor@test','Gestor','JEFE','Reciente','2026-01-02T12:00:00Z')",branch);
        jdbc.update("INSERT INTO monitoring_entry(branch_id,department_id,actor_email,actor_name,actor_role,observation) VALUES(?,20,'ajeno@test','Ajeno','JEFE','Ajena')",branch);
        assertThat(tx.execute(s->monitoring.entryPage(branch,0,20)).totalElements()).isEqualTo(102);
        jdbc.update("UPDATE user_role SET ended_at=CURRENT_TIMESTAMP WHERE professor_id=1");jdbc.update("INSERT INTO user_role(professor_id,role_name,department_id) VALUES(1,'JEFE',10)");
        var first=monitoring.entryPage(branch,0,20);assertThat(first.totalElements()).isEqualTo(101);assertThat(first.totalPages()).isEqualTo(6);assertThat(first.items()).allMatch(row->row.get("departmentId").equals(10L));
        var old=monitoring.entryPage(branch,5,20).items().get(0);assertThat(old.get("id")).isEqualTo(original);assertThat(old.get("evidenceUrls")).isEqualTo(List.of("https://example.org/evidence"));assertThat((List<?>)old.get("situations")).hasSize(1);assertThat(monitoring.entryPage(branch,6,20).items()).isEmpty();
        jdbc.update("UPDATE monitoring_branch SET status='SUBMITTED' WHERE id=?",branch);assertThat(monitoring.entryPage(branch,5,20).items()).hasSize(1);assertThatThrownBy(()->monitoring.entryPage(branch,0,101)).hasMessage("INVALID_PAGINATION");assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM kr_progress_entry",Long.class)).isZero();
        jdbc.update("UPDATE user_role SET ended_at=CURRENT_TIMESTAMP WHERE professor_id=1");assertThatThrownBy(()->monitoring.entryPage(branch,0,20)).hasMessage("OUT_OF_SCOPE");
    }
    long completeReportCut(MonitoringV2Service monitoring){
        long cut=monitoredCut(monitoring);tx.execute(s->monitoring.validate(cut,0));var branches=tx.execute(s->monitoring.consolidate(cut,1,new MonitoringV2Service.ConsolidateWrite("report-ready",null)));
        jdbc.update("INSERT INTO user_role(professor_id,role_name,faculty_id) VALUES(1,'DECANO',1)");jdbc.update("INSERT INTO user_role(professor_id,role_name) VALUES(1,'RECTOR')");jdbc.update("INSERT INTO user_role(professor_id,role_name,department_id) VALUES(1,'JEFE',10),(1,'JEFE',20)");
        for(var branch:branches){long id=((Number)branch.get("id")).longValue();if(!"DEPARTMENT".equals(branch.get("branchType")))tx.execute(s->monitoring.record(id,new MonitoringV2Service.EntryWrite(null,"Resultado de rama",List.of(),List.of()),true));else for(long dept:List.of(10L,20L)){long entry=((Number)tx.execute(s->monitoring.record(id,new MonitoringV2Service.EntryWrite(dept,"Resultado departamento",List.of(),List.of()),false)).get("entryId")).longValue();tx.execute(s->monitoring.submitDepartment(id,0,new MonitoringV2Service.DepartmentDelivery(dept,entry,true)));}}
        return cut;
    }
    @Test void schoolReportDraftRequiresFourResultsAndRetainsImmutableVersionsWithReplay(){
        var monitoring=monitoring();var reports=schoolReports(monitoring);long incomplete=monitoredCut(monitoring);var request=new SchoolReportV2Service.Draft(incomplete,"Informe","Logros","Dificultades","Conclusiones","draft-1");
        assertThatThrownBy(()->tx.execute(s->reports.create(request,0))).hasMessage("BRANCH_INCOMPLETE");assertThat(reports.list(incomplete)).isEmpty();
        tx.execute(s->monitoring.validate(incomplete,0));var branches=tx.execute(s->monitoring.consolidate(incomplete,1,new MonitoringV2Service.ConsolidateWrite("report-case",null)));
        // Complete the real branch transitions, without creating a rector final-review record.
        jdbc.update("INSERT INTO user_role(professor_id,role_name,faculty_id) VALUES(1,'DECANO',1)");jdbc.update("INSERT INTO user_role(professor_id,role_name) VALUES(1,'RECTOR')");jdbc.update("INSERT INTO user_role(professor_id,role_name,department_id) VALUES(1,'JEFE',10),(1,'JEFE',20)");
        for(var b:branches){long id=((Number)b.get("id")).longValue();if(!"DEPARTMENT".equals(b.get("branchType")))tx.execute(s->monitoring.record(id,new MonitoringV2Service.EntryWrite(null,"Resultado completo",List.of(),List.of()),true));else for(long dept:List.of(10L,20L)){long entry=((Number)tx.execute(s->monitoring.record(id,new MonitoringV2Service.EntryWrite(dept,"Resultado departamento",List.of(),List.of()),false)).get("entryId")).longValue();tx.execute(s->monitoring.submitDepartment(id,0,new MonitoringV2Service.DepartmentDelivery(dept,entry,true)));}}
        var first=tx.execute(s->reports.create(request,0));long id=((Number)first.get("id")).longValue();assertThat(first.get("status")).isEqualTo("DRAFT");assertThat(first.get("formatPolicyApproved")).isEqualTo(false);
        var replay=tx.execute(s->reports.create(request,0));assertThat(replay.get("id")).isEqualTo(first.get("id"));assertThat(reports.list(incomplete)).hasSize(1);
        tx.execute(s->reports.create(new SchoolReportV2Service.Draft(incomplete,"Informe 2","Otros logros","Dificultades","Nuevas conclusiones","draft-2"),1));assertThat(reports.list(incomplete)).hasSize(2);assertThat(reports.get(id).get("conclusions")).isEqualTo("Conclusiones");
        assertThatThrownBy(()->tx.execute(s->reports.create(new SchoolReportV2Service.Draft(incomplete,"Alterado","Logros","Dificultades","Conclusiones","draft-1"),2))).hasMessage("IDEMPOTENCY_CONFLICT");assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM rector_review",Long.class)).isZero();assertThat(jdbc.queryForObject("SELECT status FROM monitoring_cut WHERE id=?",String.class,incomplete)).isEqualTo("CONSOLIDATED");
    }
    @Test void schoolWideDraftCannotBeReadByDepartmentOnlyRoleOrCreatedByGeneralAdmin(){
        var monitoring=monitoring();var reports=schoolReports(monitoring);long cut=completeReportCut(monitoring);var request=new SchoolReportV2Service.Draft(cut,"Informe","Logros","Dificultades","Conclusiones","draft-scope");long id=((Number)tx.execute(s->reports.create(request,0)).get("id")).longValue();
        jdbc.update("UPDATE user_role SET ended_at=CURRENT_TIMESTAMP WHERE professor_id=1");jdbc.update("INSERT INTO user_role(professor_id,role_name,department_id) VALUES(1,'JEFE',10)");assertThatThrownBy(()->reports.get(id)).hasMessage("OUT_OF_SCOPE");assertThatThrownBy(()->reports.list(cut)).hasMessage("OUT_OF_SCOPE");
        authority("ADMIN");assertThat(reports.get(id).get("id")).isEqualTo(id);assertThatThrownBy(()->tx.execute(s->reports.create(request,1))).hasMessage("FORBIDDEN");
    }
    @Test void situationPagesAndOldCorrectiveWritesKeepScopeAndOriginalEvidence(){
        var monitoring=monitoring();long cut=completeReportCut(monitoring);long branch=jdbc.queryForObject("SELECT id FROM monitoring_branch WHERE cut_id=? AND branch_type='DEPARTMENT'",Long.class,cut);jdbc.update("UPDATE monitoring_branch SET status='OPEN' WHERE id=?",branch);
        long entry=((Number)tx.execute(s->monitoring.record(branch,new MonitoringV2Service.EntryWrite(10L,"Original",List.of(),List.of(new MonitoringV2Service.SituationWrite("Hallazgo","Detalle","HIGH",null))),false)).get("entryId")).longValue();long finding=jdbc.queryForObject("SELECT id FROM monitoring_situation WHERE entry_id=?",Long.class,entry);
        var attention=tx.execute(s->monitoring.transitionSituation(finding,0,new MonitoringV2Service.SituationTransition("IN_PROGRESS","Atención original","https://example.org/original")));long canonical=((Number)attention.get("situationId")).longValue();
        long action=((Number)tx.execute(s->monitoring.createCorrective(finding,new MonitoringV2Service.CorrectiveWrite(2L,java.time.LocalDate.now(java.time.ZoneId.of("America/Bogota")).plusDays(2),"Correctivo original"))).get("id")).longValue();
        for(int i=0;i<100;i++){jdbc.update("INSERT INTO situation_history(situation_id,previous_status,new_status,comment,changed_by) VALUES(?,'OPEN','IN_PROGRESS','Antecedente','1')",canonical);jdbc.update("INSERT INTO corrective_action(situation_id,department_id,assigned_to,due_at,decision_description,created_by) VALUES(?,10,'2','2026-12-01','Posterior','1')",canonical);}
        var page=tx.execute(s->monitoring.situationHistoryPage(finding,5,20));assertThat(page.totalElements()).isEqualTo(101);assertThat(page.items()).hasSize(1);assertThat(page.items().get(0).get("comment")).isEqualTo("Atención original");assertThat(page.items().get(0).get("evidenceUrl")).isEqualTo("https://example.org/original");
        assertThat(monitoring.correctiveActions(finding).stream().noneMatch(row->((Number)row.get("id")).longValue()==action)).isTrue();assertThat(monitoring.correctiveAction(finding,action).get("decisionDescription")).isEqualTo("Correctivo original");
        var changed=tx.execute(s->monitoring.transitionCorrective(finding,action,0,new MonitoringV2Service.CorrectiveTransition("CANCELLED","Se sustituye por otra decisión",null)));assertThat(changed.get("status")).isEqualTo("CANCELLED");assertThat(((Number)changed.get("version")).longValue()).isEqualTo(1);
        assertThatThrownBy(()->monitoring.situationHistoryPage(finding,-1,20)).hasMessage("INVALID_PAGINATION");assertThatThrownBy(()->monitoring.situationHistoryPage(finding,0,101)).hasMessage("INVALID_PAGINATION");assertThat(monitoring.situationHistoryPage(finding,6,20).items()).isEmpty();
        jdbc.update("UPDATE user_role SET ended_at=CURRENT_TIMESTAMP WHERE professor_id=1");jdbc.update("INSERT INTO user_role(professor_id,role_name,department_id) VALUES(1,'JEFE',20)");assertThatThrownBy(()->monitoring.situationHistoryPage(finding,0,20)).hasMessage("OUT_OF_SCOPE");assertThatThrownBy(()->monitoring.correctiveAction(finding,action)).hasMessage("OUT_OF_SCOPE");
        assertThat(jdbc.queryForObject("SELECT observation FROM monitoring_entry WHERE id=?",String.class,entry)).isEqualTo("Original");
    }
    @Test void reportFormatRequiresExplicitDecisionAndPreservesVersionedHistory() throws Exception {
        jdbc.execute(Files.readString(Path.of("src/main/resources/db/migration/V29__report_format_policy_lock.sql")));
        var formats=new ReportFormatV2Service(jdbc,tracking,new ObjectMapper(),mock(AuditService.class));
        assertThat(formats.current()).isNull();assertThat(formats.page(0,20).totalElements()).isZero();
        var write=new ReportFormatV2Service.Write("Plantilla de prueba",List.of("TITLE","CUT","BRANCH_RESULTS"),List.of("Rectoría","Dirección"),"https://example.org/decision","Decisión de prueba",true);
        assertThatThrownBy(()->tx.execute(s->formats.record(write,0))).hasMessage("FORBIDDEN");authority("ADMIN");
        assertThatThrownBy(()->tx.execute(s->formats.record(new ReportFormatV2Service.Write(write.templateName(),write.requiredSections(),write.recipients(),write.decisionRef(),write.reason(),false),0))).hasMessage("POLICY_CONFIRMATION_REQUIRED");
        assertThatThrownBy(()->tx.execute(s->formats.record(new ReportFormatV2Service.Write(write.templateName(),List.of("UNKNOWN"),write.recipients(),write.decisionRef(),write.reason(),true),0))).hasMessage("INVALID_REPORT_FORMAT");
        assertThatThrownBy(()->tx.execute(s->formats.record(new ReportFormatV2Service.Write(write.templateName(),write.requiredSections(),List.of(" Rectoría ","Rectoría"),write.decisionRef(),write.reason(),true),0))).hasMessage("INVALID_REPORT_RECIPIENTS");
        var first=tx.execute(s->formats.record(write,0));assertThat(first.version()).isEqualTo(1);assertThat(first.templateName()).isEqualTo("Plantilla de prueba");assertThat(first.recipients()).containsExactly("Rectoría","Dirección");
        for(int version=2;version<=101;version++)jdbc.update("INSERT INTO institutional_policy(policy_key,policy_code,version,\"value\",approved_by) SELECT policy_key,policy_code,?,\"value\",approved_by FROM institutional_policy WHERE id=?",version,first.id());
        var last=tx.execute(s->formats.page(5,20));assertThat(last.totalElements()).isEqualTo(101);assertThat(last.latestVersion()).isEqualTo(101);assertThat(last.items()).hasSize(1);assertThat(last.items().get(0).version()).isEqualTo(1);
        assertThatThrownBy(()->tx.execute(s->formats.record(write,1))).hasMessage("VERSION_CONFLICT");assertThat(formats.page(6,20).items()).isEmpty();assertThatThrownBy(()->formats.page(-1,20)).hasMessage("INVALID_PAGINATION");assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM school_final_report",Long.class)).isZero();assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM rector_review",Long.class)).isZero();
    }
    @Test void reportFormatRejectsCorruptConfigurationRatherThanPresumingApproval() throws Exception {
        jdbc.execute(Files.readString(Path.of("src/main/resources/db/migration/V29__report_format_policy_lock.sql")));authority("ADMIN");
        jdbc.update("INSERT INTO institutional_policy(policy_key,policy_code,version,\"value\",approved_by) VALUES(?,?,1,CAST(? AS JSONB),1)",ReportFormatV2Service.KEY,ReportFormatV2Service.KEY,"{\"templateName\":\"Incompleta\"}");
        var formats=new ReportFormatV2Service(jdbc,tracking,new ObjectMapper(),mock(AuditService.class));assertThatThrownBy(formats::current).hasMessage("INVALID_POLICY");assertThatThrownBy(()->formats.page(0,20)).hasMessage("INVALID_POLICY");
    }
    @Test void correctivePagesKeepOldEvidenceAndRejectOtherFindingsAndDepartments(){
        var monitoring=monitoring();long cut=completeReportCut(monitoring);long branch=jdbc.queryForObject("SELECT id FROM monitoring_branch WHERE cut_id=? AND branch_type='DEPARTMENT'",Long.class,cut);jdbc.update("UPDATE monitoring_branch SET status='OPEN' WHERE id=?",branch);
        long entry=((Number)tx.execute(s->monitoring.record(branch,new MonitoringV2Service.EntryWrite(10L,"Original",List.of(),List.of(new MonitoringV2Service.SituationWrite("Hallazgo","Detalle","HIGH",null))),false)).get("entryId")).longValue();long finding=jdbc.queryForObject("SELECT id FROM monitoring_situation WHERE entry_id=?",Long.class,entry);long canonical=((Number)monitoring.situation(finding).get("situationId")).longValue();
        long action=((Number)tx.execute(s->monitoring.createCorrective(finding,new MonitoringV2Service.CorrectiveWrite(2L,java.time.LocalDate.now(java.time.ZoneId.of("America/Bogota")).plusDays(2),"Correctivo original"))).get("id")).longValue();
        tx.execute(s->monitoring.transitionCorrective(finding,action,0,new MonitoringV2Service.CorrectiveTransition("IN_PROGRESS","Atención original","https://example.org/original")));
        for(int i=0;i<100;i++){jdbc.update("INSERT INTO corrective_action(situation_id,department_id,assigned_to,due_at,decision_description,created_by) VALUES(?,10,'2','2026-12-01','Posterior','1')",canonical);jdbc.update("INSERT INTO corrective_action_history(corrective_action_id,previous_status,new_status,comment,actor_id) VALUES(?,'PENDING','IN_PROGRESS','Antecedente',1)",action);}
        var last=tx.execute(s->monitoring.correctivePage(finding,5,20));assertThat(last.totalElements()).isEqualTo(101);assertThat(last.totalPages()).isEqualTo(6);assertThat(last.items()).hasSize(1);assertThat(last.items().get(0).get("id")).isEqualTo(action);assertThat(last.items().get(0)).doesNotContainKey("history");
        var history=tx.execute(s->monitoring.correctiveHistoryPage(finding,action,5,20));assertThat(history.totalElements()).isEqualTo(101);assertThat(history.items()).hasSize(1);assertThat(history.items().get(0).get("comment")).isEqualTo("Atención original");assertThat(history.items().get(0).get("evidenceUrl")).isEqualTo("https://example.org/original");
        assertThat(monitoring.correctivePage(finding,6,20).items()).isEmpty();assertThat(monitoring.correctiveHistoryPage(finding,action,6,20).items()).isEmpty();assertThatThrownBy(()->monitoring.correctivePage(finding,-1,20)).hasMessage("INVALID_PAGINATION");assertThatThrownBy(()->monitoring.correctiveHistoryPage(finding,action,0,101)).hasMessage("INVALID_PAGINATION");
        long foreignEntry=((Number)tx.execute(s->monitoring.record(branch,new MonitoringV2Service.EntryWrite(20L,"Ajeno",List.of(),List.of(new MonitoringV2Service.SituationWrite("Otro","Detalle","HIGH",null))),false)).get("entryId")).longValue();long foreignFinding=jdbc.queryForObject("SELECT id FROM monitoring_situation WHERE entry_id=?",Long.class,foreignEntry);assertThatThrownBy(()->monitoring.correctiveHistoryPage(foreignFinding,action,0,20)).hasMessage("OUT_OF_SCOPE");
        tx.execute(s->monitoring.transitionCorrective(finding,action,1,new MonitoringV2Service.CorrectiveTransition("COMPLETED","Verificación final","https://example.org/final")));assertThat(monitoring.correctiveHistoryPage(finding,action,0,20).totalElements()).isEqualTo(102);
        jdbc.update("UPDATE user_role SET ended_at=CURRENT_TIMESTAMP WHERE professor_id=1");jdbc.update("INSERT INTO user_role(professor_id,role_name,department_id) VALUES(1,'JEFE',20)");assertThatThrownBy(()->monitoring.correctivePage(finding,0,20)).hasMessage("OUT_OF_SCOPE");assertThatThrownBy(()->monitoring.correctiveHistoryPage(finding,action,0,20)).hasMessage("OUT_OF_SCOPE");assertThat(jdbc.queryForObject("SELECT status FROM corrective_action WHERE id=?",String.class,action)).isEqualTo("COMPLETED");
    }
    @Test void schoolReportPagesKeepLatestVersionAndEnforceScope(){
        var monitoring=monitoring();var reports=schoolReports(monitoring);long cut=completeReportCut(monitoring);
        long id=((Number)tx.execute(s->reports.create(new SchoolReportV2Service.Draft(cut,"Original","Logros","Dificultades","Conclusiones","pages-original"),0)).get("id")).longValue();
        for(int version=2;version<=101;version++)jdbc.update("INSERT INTO school_final_report(cut_id,cycle_id,title,executive_summary,achievements_summary,challenges_summary,conclusions,author_id,report_version,creation_key,content_snapshot) SELECT cut_id,cycle_id,'Version posterior',executive_summary,achievements_summary,challenges_summary,conclusions,author_id,?, ?,content_snapshot FROM school_final_report WHERE id=?",version,"page-"+version,id);
        var last=tx.execute(s->reports.reportPage(cut,5,20));assertThat(last.totalElements()).isEqualTo(101);assertThat(last.totalPages()).isEqualTo(6);assertThat(last.latestVersion()).isEqualTo(101);assertThat(last.items()).hasSize(1);assertThat(last.items().get(0).get("id")).isEqualTo(id);
        assertThat(tx.execute(s->reports.reportPage(cut,6,20)).items()).isEmpty();assertThatThrownBy(()->reports.reportPage(cut,-1,20)).hasMessage("INVALID_PAGINATION");assertThatThrownBy(()->reports.reportPage(cut,0,101)).hasMessage("INVALID_PAGINATION");
        jdbc.update("UPDATE user_role SET ended_at=CURRENT_TIMESTAMP WHERE professor_id=1");jdbc.update("INSERT INTO user_role(professor_id,role_name,department_id) VALUES(1,'JEFE',10)");assertThatThrownBy(()->reports.reportPage(cut,0,20)).hasMessage("OUT_OF_SCOPE");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM school_final_report WHERE cut_id=?",Long.class,cut)).isEqualTo(101);
    }
    @Test void schoolDraftCapturesLargeBranchAndFindingHistoriesWithoutTruncation(){
        var monitoring=monitoring();long cut=completeReportCut(monitoring);long branch=jdbc.queryForObject("SELECT id FROM monitoring_branch WHERE cut_id=? AND branch_type='DEPARTMENT'",Long.class,cut);
        // Extend the completed fixture with a finding and historical records; no production branch is reopened.
        jdbc.update("UPDATE monitoring_branch SET status='OPEN' WHERE id=?",branch);
        long entry=((Number)tx.execute(s->monitoring.record(branch,new MonitoringV2Service.EntryWrite(10L,"Detalle completo",List.of("https://example.org/original"),List.of(new MonitoringV2Service.SituationWrite("Hallazgo","Detalle","HIGH",null))),false)).get("entryId")).longValue();
        long finding=jdbc.queryForObject("SELECT id FROM monitoring_situation WHERE entry_id=?",Long.class,entry);long canonical=((Number)monitoring.situation(finding).get("situationId")).longValue();
        long action=((Number)tx.execute(s->monitoring.createCorrective(finding,new MonitoringV2Service.CorrectiveWrite(2L,java.time.LocalDate.now(java.time.ZoneId.of("America/Bogota")).plusDays(3),"Correctivo original"))).get("id")).longValue();
        for(int i=0;i<101;i++) {
            jdbc.update("INSERT INTO monitoring_entry(branch_id,department_id,actor_email,actor_name,actor_role,observation) VALUES(?,10,'gestor@test','Gestor','JEFE','Antecedente')",branch);
            jdbc.update("INSERT INTO situation_history(situation_id,previous_status,new_status,comment,changed_by) VALUES(?,'OPEN','IN_PROGRESS','Antecedente','1')",canonical);
            jdbc.update("INSERT INTO corrective_action_history(corrective_action_id,previous_status,new_status,comment,actor_id) VALUES(?,'PENDING','IN_PROGRESS','Antecedente',1)",action);
        }
        for(int i=0;i<100;i++)jdbc.update("INSERT INTO corrective_action(situation_id,department_id,assigned_to,due_at,decision_description,created_by) VALUES(?,10,'2','2026-12-01','Antecedente','1')",canonical);
        long result=jdbc.queryForObject("SELECT id FROM monitoring_department_result WHERE branch_id=? AND department_id=10",Long.class,branch);
        for(int version=2;version<=101;version++)jdbc.update("INSERT INTO monitoring_department_delivery(result_id,version,entry_id,actor_id) VALUES(?,?,?,1)",result,version,entry);
        jdbc.update("UPDATE monitoring_department_result SET version=101,entry_id=? WHERE id=?",entry,result);
        jdbc.update("UPDATE monitoring_branch SET status='SUBMITTED' WHERE id=?",branch);
        var reports=schoolReports(monitoring);var saved=tx.execute(s->reports.create(new SchoolReportV2Service.Draft(cut,"Informe amplio","Logros","Dificultades","Conclusiones","large-history"),0));long id=((Number)saved.get("id")).longValue();
        var content=(Map<?,?>)saved.get("content");var branchRows=(List<?>)content.get("branchResults");var captured=(Map<?,?>)branchRows.stream().map(row->(Map<?,?>)row).filter(row->((Map<?,?>)row.get("branch")).get("branchType").equals("DEPARTMENT")).findFirst().orElseThrow();assertThat((List<?>)captured.get("entries")).hasSize(104);assertThat((List<?>)captured.get("departmentDeliveryHistories")).hasSize(2);
        var detail=(Map<?,?>)((List<?>)content.get("situations")).get(0);assertThat((List<?>)detail.get("history")).hasSize(101);var actions=(List<?>)detail.get("correctiveActions");assertThat(actions).hasSize(101);var original=(Map<?,?>)actions.stream().map(row->(Map<?,?>)row).filter(row->row.get("decisionDescription").equals("Correctivo original")).findFirst().orElseThrow();assertThat((List<?>)original.get("history")).hasSize(101);
        var deliveries=(Map<?,?>)((List<?>)captured.get("departmentDeliveryHistories")).stream().map(row->(Map<?,?>)row).filter(row->((Number)row.get("departmentId")).longValue()==10).findFirst().orElseThrow();assertThat((List<?>)deliveries.get("deliveries")).hasSize(101);
        jdbc.update("UPDATE monitoring_entry SET observation='Cambio posterior' WHERE id=?",entry);assertThat(reports.get(id).get("content").toString()).contains("Detalle completo").doesNotContain("Cambio posterior");assertThat(saved.get("status")).isEqualTo("DRAFT");assertThat(saved.get("formatPolicyApproved")).isEqualTo(false);assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM rector_review",Long.class)).isZero();
    }
    @Test void consistencyReportsIndependentGapsWhenLinkPolicyIsMissingWithoutMutatingData(){
        long id=create();authority("ADMIN");jdbc.update("UPDATE key_result SET metric_definition_id=NULL,base_value=101 WHERE id=?",id);jdbc.update("DELETE FROM kr_assignment WHERE key_result_id=?",id);
        var consistency=new ConsistencyV2Service(jdbc,tracking,policies);var result=tx.execute(s->consistency.check(1L,null));
        assertThat(result.get("auditStatus")).isEqualTo("POLICY_UNSET");var findings=(List<Map<String,Object>>)result.get("findings");assertThat(findings.stream().map(f->f.get("ruleCode"))).contains("KR_METRIC","KR_VALUES","KR_OWNER","KR_DEPARTMENT").doesNotContain("KR_GOAL_MINIMUM","KR_BET_MINIMUM");
        assertThat(((List<?>)result.get("blockedRules")).stream().map(Object::toString).toList()).contains("KR_GOAL_MINIMUM","KR_BET_MINIMUM");assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM kr_version",Long.class)).isEqualTo(1);assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM kr_progress_entry",Long.class)).isZero();assertThat(jdbc.queryForObject("SELECT status FROM key_result WHERE id=?",String.class,id)).isEqualTo("BORRADOR");
    }
    @Test void consistencyUsesRecordedMinimumsAndFiltersOtherDepartments(){
        long id=create();long other=((Number)tx.execute(s->service.create(request(2L,List.of(1L)),"consistency-other")).get("id")).longValue();jdbc.update("UPDATE kr_assignment SET professor_id=3 WHERE key_result_id=?",other);registerPolicy(1,1,0);
        long initiative=((Number)tx.execute(s->tracking.createInitiative(id,new TrackingV2Service.InitiativeWrite(id,10L,"Trabajo","Descripción",2L))).get("id")).longValue();jdbc.update("UPDATE professor SET active=FALSE WHERE id=2");
        var consistency=new ConsistencyV2Service(jdbc,tracking,policies);var result=tx.execute(s->consistency.check(1L,10L));var findings=(List<Map<String,Object>>)result.get("findings");assertThat(findings.stream().map(f->f.get("entityId"))).doesNotContain(other);assertThat(findings.stream().map(f->f.get("ruleCode"))).contains("KR_BET_MINIMUM","KR_OWNER","INITIATIVE_OWNER");assertThat(result.get("policyVersion")).isEqualTo(1);assertThat(((Map<?,?>)result.get("checked")).get("keyResults")).isEqualTo(1);
        assertThatThrownBy(()->consistency.check(1L,20L)).hasMessage("OUT_OF_SCOPE");assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM initiative_progress_entry WHERE initiative_id=?",Long.class,initiative)).isZero();
    }
    @Test void malformedPolicyLeavesStructuralConsistencyAvailable(){
        long id=create();registerPolicy(1,0,0);jdbc.update("UPDATE institutional_policy SET value=CAST(? AS JSONB) WHERE policy_key=?","{}",PoliciesV2Service.KEY);jdbc.update("UPDATE key_result SET target_value=101 WHERE id=?",id);
        var result=tx.execute(s->new ConsistencyV2Service(jdbc,tracking,policies).check(1L,null));assertThat(result.get("auditStatus")).isEqualTo("INVALID_POLICY");assertThat((List<?>)result.get("findings")).isNotEmpty();assertThat(result.get("policyVersion")).isNull();
    }
    @Test void initiativePagesAndManualHistoryReachOldRowsWithinDepartmentScope(){
        long kr=create();long id=((Number)tx.execute(s->tracking.createInitiative(kr,new TrackingV2Service.InitiativeWrite(kr,10L,"Histórica 50%_!","Descripción",2L))).get("id")).longValue();
        jdbc.update("UPDATE initiative SET created_at='2026-01-01T12:00:00Z' WHERE id=?",id);
        for(int i=0;i<100;i++)jdbc.update("INSERT INTO initiative(key_result_id,department_id,title,description,created_at) VALUES(?,10,?,'Fixture','2026-01-02T12:00:00Z')",kr,"Trabajo "+i);
        jdbc.update("INSERT INTO initiative_progress_entry(initiative_id,progress_percent,status,comment,actor_id,occurred_at) VALUES(?,0,'ON_TRACK','Histórica',1,'2026-01-01T12:00:00Z')",id);
        for(int i=1;i<=100;i++)jdbc.update("INSERT INTO initiative_progress_entry(initiative_id,progress_percent,status,comment,actor_id,occurred_at) VALUES(?,?,'ON_TRACK','Reciente',1,'2026-01-02T12:00:00Z')",id,i);
        authority("ADMIN");long other=((Number)tx.execute(s->service.create(request(3L,List.of(1L)),"other-initiative-dept")).get("id")).longValue();tx.execute(s->tracking.createInitiative(other,new TrackingV2Service.InitiativeWrite(other,20L,"Ajena","Descripción",3L)));authority("MANAGER");
        var first=tx.execute(s->tracking.initiativePage(null,null,0,20));assertThat(first.totalElements()).isEqualTo(101);assertThat(first.totalPages()).isEqualTo(6);assertThat(first.items().get(0).get("title")).isEqualTo("Trabajo 99");assertThat(first.items().get(0).get("keyResultName")).isEqualTo("Retener estudiantes");
        assertThat(tracking.initiativePage(null,null,5,20).items().get(0).get("id")).isEqualTo(id);assertThat(tracking.initiativePage("50%_!",null,0,20).totalElements()).isEqualTo(1);
        assertThat(tracking.initiativeProgressPage(id,5,20).items().get(0).get("comment")).isEqualTo("Histórica");assertThat(tracking.initiativeProgressPage(id,0,20).totalElements()).isEqualTo(101);
        assertThatThrownBy(()->tracking.initiativePage(null,"INVALID",0,20)).hasMessage("INVALID_STATUS");assertThatThrownBy(()->tracking.initiativeProgressPage(id,0,101)).hasMessage("INVALID_PAGINATION");
        tx.execute(s->tracking.deactivate(id,new TrackingV2Service.DeactivateWrite("Finalizada",true,0L)));assertThat(tracking.initiativePage(null,"INACTIVE",0,20).totalElements()).isEqualTo(1);assertThat(tracking.initiativeProgressPage(id,5,20).items()).hasSize(1);assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM kr_progress_entry",Long.class)).isZero();
        var context=new ExternalUserContext(3L,"ajeno","ajeno@test",List.of("MANAGER"),List.of(),1L,"Ajeno","D2");SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(context,null,List.of(new SimpleGrantedAuthority("ROLE_MANAGER"))));
        assertThat(tracking.initiativePage(null,null,0,20).totalElements()).isEqualTo(1);assertThatThrownBy(()->tracking.initiativeProgressPage(id,0,20)).hasMessage("OUT_OF_SCOPE");
    }
    @Test void plannerReferencesArePagedScopedAndExcludeConnectionSecrets(){
        long kr=create();long id=((Number)tx.execute(s->tracking.createInitiative(kr,new TrackingV2Service.InitiativeWrite(kr,10L,"Referencias","Descripción",2L))).get("id")).longValue();
        jdbc.update("INSERT INTO planner_connection(id,professor_id,provider,external_tenant_id,granted_scopes,consent_status,secret_ref,revoked_at) VALUES(1,2,'MICROSOFT_GRAPH','private-tenant','Tasks.Read','REVOKED','private-secret',CURRENT_TIMESTAMP)");
        for(String task:List.of("task-1","task-2","task-3"))jdbc.update("INSERT INTO planner_task_ref(connection_id,initiative_id,provider,external_plan_id,external_task_id,linked_by,title_snapshot,status_snapshot) VALUES(1,?,'MICROSOFT_GRAPH','plan',?,1,?,'completed')",id,task,task);
        var references=new PlannerReferencesV2Service(jdbc,tracking);var first=tx.execute(s->references.list(id,0,2));
        assertThat(first.totalElements()).isEqualTo(3);assertThat(first.totalPages()).isEqualTo(2);assertThat(first.items()).hasSize(2);
        assertThat(first.items().get(0).get("externalTaskId")).isEqualTo("task-3");assertThat(first.items().get(0).get("connectionState")).isEqualTo("REVOKED");assertThat(first.toString()).doesNotContain("private-tenant","private-secret","Tasks.Read");
        assertThat(references.list(id,1,2).items()).hasSize(1);assertThat(references.list(id,2,2).items()).isEmpty();
        assertThatThrownBy(()->references.list(id,-1,20)).hasMessage("INVALID_PAGINATION");assertThatThrownBy(()->references.list(id,0,101)).hasMessage("INVALID_PAGINATION");
        var context=new ExternalUserContext(3L,"ajeno","ajeno@test",List.of("MANAGER"),List.of(),1L,"Ajeno","D2");
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(context,null,List.of(new SimpleGrantedAuthority("ROLE_MANAGER"))));
        assertThatThrownBy(()->references.list(id,0,20)).hasMessage("OUT_OF_SCOPE");assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM initiative_progress_entry",Long.class)).isZero();
    }
    @Test void initiativeAssignmentsReachOldIntervalsAndRemainScopedAfterDeactivation(){
        long kr=create();long id=((Number)tx.execute(s->tracking.createInitiative(kr,new TrackingV2Service.InitiativeWrite(kr,10L,"Historial","Descripción",2L))).get("id")).longValue();
        for(int i=0;i<100;i++)jdbc.update("INSERT INTO initiative_assignment(initiative_id,professor_id,actor_id,started_at,ended_at) VALUES(?,1,1,'2026-01-01T12:00:00Z','2026-01-02T12:00:00Z')",id);
        var first=tx.execute(s->tracking.initiativeAssignmentPage(id,0,20));assertThat(first.totalElements()).isEqualTo(101);assertThat(first.totalPages()).isEqualTo(6);assertThat(first.items().get(0).get("endedAt")).isNull();
        var last=tracking.initiativeAssignmentPage(id,5,20);assertThat(last.items()).hasSize(1);assertThat(last.items().get(0).get("name")).isEqualTo("Gestor");assertThat(last.items().get(0).get("actorName")).isEqualTo("Gestor");
        assertThat(tracking.initiativeAssignmentPage(id,6,20).items()).isEmpty();assertThatThrownBy(()->tracking.initiativeAssignmentPage(id,-1,20)).hasMessage("INVALID_PAGINATION");
        tx.execute(s->tracking.deactivate(id,new TrackingV2Service.DeactivateWrite("Finalizada",true,0L)));assertThat(tracking.initiativeAssignmentPage(id,5,20).items()).hasSize(1);assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM initiative_progress_entry",Long.class)).isZero();
        var context=new ExternalUserContext(3L,"ajeno","ajeno@test",List.of("MANAGER"),List.of(),1L,"Ajeno","D2");SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(context,null,List.of(new SimpleGrantedAuthority("ROLE_MANAGER"))));
        assertThatThrownBy(()->tracking.initiativeAssignmentPage(id,0,20)).hasMessage("OUT_OF_SCOPE");
    }
    @Test void krVersionPagesReachInitialSnapshotWithoutChangingLiveDefinition(){
        long id=create();
        for(int i=1;i<=100;i++)jdbc.update("INSERT INTO kr_version(key_result_id,version,snapshot,reason,actor_id) VALUES(?,?,CAST(? AS JSONB),?,1)",id,i,"{\"before\":{\"baseValue\":0},\"after\":{\"baseValue\":1}}","Revisión "+i);
        var first=tx.execute(s->service.versionPage(id,0,20));assertThat(first.totalElements()).isEqualTo(101);assertThat(first.totalPages()).isEqualTo(6);assertThat(first.items().get(0).get("version")).isEqualTo(100L);
        var snapshot=(Map<?,?>)first.items().get(0).get("snapshot");assertThat(((Map<?,?>)snapshot.get("before")).containsKey("baseValue")).isTrue();assertThat(first.items().get(0).get("actorName")).isEqualTo("Gestor");
        var last=service.versionPage(id,5,20);assertThat(last.items()).hasSize(1);assertThat(last.items().get(0).get("version")).isEqualTo(0L);assertThat(service.versionPage(id,6,20).items()).isEmpty();
        assertThatThrownBy(()->service.versionPage(id,0,101)).hasMessage("INVALID_PAGINATION");assertThatThrownBy(()->service.versionPage(id,-1,20)).hasMessage("INVALID_PAGINATION");assertThat(service.get(id).get("version")).isEqualTo(0L);assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM kr_progress_entry",Long.class)).isZero();
        jdbc.update("UPDATE key_result SET active=FALSE WHERE id=?",id);assertThat(service.versionPage(id,5,20).items()).hasSize(1);
        var context=new ExternalUserContext(3L,"ajeno","ajeno@test",List.of("MANAGER"),List.of(),1L,"Ajeno","D2");SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(context,null,List.of(new SimpleGrantedAuthority("ROLE_MANAGER"))));
        assertThatThrownBy(()->service.versionPage(id,0,20)).hasMessage("OUT_OF_SCOPE");
    }
    @Test void krRelationPagesReachAllOldIntervalsWithoutChangingCurrentLinks(){
        long id=create();
        jdbc.update("INSERT INTO world(id,name) VALUES(1,'Institucional')");
        jdbc.update("INSERT INTO strategic_bet(id,name,description,start_date,end_date,world_id) VALUES(1,'Apuesta','Fixture','2026-01-01','2026-12-31',1)");
        jdbc.update("INSERT INTO kr_bet_link(key_result_id,strategic_bet_id,actor_id) VALUES(?,1,1)",id);
        for(int i=0;i<100;i++) {
            jdbc.update("INSERT INTO kr_assignment(key_result_id,professor_id,actor_id,started_at,ended_at) VALUES(?,1,1,'2026-01-01T12:00:00Z','2026-01-02T12:00:00Z')",id);
            jdbc.update("INSERT INTO kr_goal_link(key_result_id,goal_id,actor_id,started_at,ended_at) VALUES(?,1,1,'2026-01-01T12:00:00Z','2026-01-02T12:00:00Z')",id);
            jdbc.update("INSERT INTO kr_bet_link(key_result_id,strategic_bet_id,actor_id,started_at,ended_at) VALUES(?,1,1,'2026-01-01T12:00:00Z','2026-01-02T12:00:00Z')",id);
        }
        for(String kind:List.of("goals","bets","assignments")) {
            var first=tx.execute(s->service.relationPage(id,kind,0,20));assertThat(first.totalElements()).isEqualTo(101);assertThat(first.totalPages()).isEqualTo(6);assertThat(first.items().get(0).get("endedAt")).isNull();
            assertThat(service.relationPage(id,kind,5,20).items()).hasSize(1);assertThat(service.relationPage(id,kind,6,20).items()).isEmpty();
        }
        assertThatThrownBy(()->service.relationPage(id,"unknown",0,20)).hasMessage("INVALID_REQUEST");assertThatThrownBy(()->service.relationPage(id,"goals",0,101)).hasMessage("INVALID_PAGINATION");assertThat(service.get(id).get("version")).isEqualTo(0L);assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM kr_progress_entry",Long.class)).isZero();
        jdbc.update("UPDATE key_result SET active=FALSE WHERE id=?",id);assertThat(service.relationPage(id,"goals",5,20).items()).hasSize(1);
        var context=new ExternalUserContext(3L,"ajeno","ajeno@test",List.of("MANAGER"),List.of(),1L,"Ajeno","D2");SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(context,null,List.of(new SimpleGrantedAuthority("ROLE_MANAGER"))));
        assertThatThrownBy(()->service.relationPage(id,"assignments",0,20)).hasMessage("OUT_OF_SCOPE");
    }
    @Test void initiativeReferenceUsesItsOwnScopeAndDoesNotChangeProgress(){
        long kr=create();long id=((Number)tx.execute(s->tracking.createInitiative(kr,new TrackingV2Service.InitiativeWrite(kr,10L,"Referenciada","Descripción",2L))).get("id")).longValue();
        assertThat(tracking.getInitiative(id).get("title")).isEqualTo("Referenciada");
        var context=new ExternalUserContext(3L,"ajeno","ajeno@test",List.of("MANAGER"),List.of(),1L,"Ajeno","D2");
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(context,null,List.of(new SimpleGrantedAuthority("ROLE_MANAGER"))));
        assertThatThrownBy(()->tracking.getInitiative(id)).hasMessage("OUT_OF_SCOPE");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM initiative_progress_entry",Long.class)).isZero();
    }
    @Test void consistencyFlagsRevokedPlannerReferenceWithoutInventingFreshnessOrFetchingSecrets(){
        long kr=create();long initiative=((Number)tx.execute(s->tracking.createInitiative(kr,new TrackingV2Service.InitiativeWrite(kr,10L,"Trabajo","Descripción",2L))).get("id")).longValue();
        jdbc.update("INSERT INTO planner_connection(id,professor_id,provider,external_tenant_id,granted_scopes,consent_status,secret_ref,revoked_at) VALUES(1,2,'MICROSOFT_GRAPH','fixture','Tasks.Read','REVOKED','fixture-only',CURRENT_TIMESTAMP)");
        jdbc.update("INSERT INTO planner_task_ref(connection_id,initiative_id,provider,external_plan_id,external_task_id,linked_by,title_snapshot,status_snapshot) VALUES(1,?,'MICROSOFT_GRAPH','plan','task',1,'Referencia','completed')",initiative);
        var result=tx.execute(s->new ConsistencyV2Service(jdbc,tracking,policies).check(1L,null));var findings=(List<Map<String,Object>>)result.get("findings");assertThat(findings.stream().map(f->f.get("ruleCode"))).contains("PLANNER_CONNECTION").doesNotContain("PLANNER_STALE");assertThat(result.toString()).doesNotContain("fixture-only","external_tenant_id","granted_scopes");assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM kr_progress_entry",Long.class)).isZero();
    }
}
