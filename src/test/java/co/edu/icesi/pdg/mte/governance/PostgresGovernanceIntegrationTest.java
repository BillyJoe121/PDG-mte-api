package co.edu.icesi.pdg.mte.governance;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import java.util.UUID;
import java.util.Map;
import java.util.List;
import java.math.BigDecimal;
import co.edu.icesi.pdg.mte.strategy.KrVersionRepository;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

/** Opt-in real PostgreSQL, Flyway, Spring transactions, MVC/security and persisted audit.
 * A unique schema is created in a disposable local test database, never in a shared database.
 * This uses the existing mock authentication for test actors; it does not validate SSO.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named="MTE_LOCAL_PG_TEST_URL",matches="jdbc:postgresql://127\\.0\\.0\\.1:55432/mte_local_validation")
class PostgresGovernanceIntegrationTest {
    private static final String SCHEMA="bmad_it_"+UUID.randomUUID().toString().replace("-","");
    @DynamicPropertySource static void database(DynamicPropertyRegistry properties){
        properties.add("spring.datasource.url",()->System.getenv("MTE_LOCAL_PG_TEST_URL")+"?currentSchema="+SCHEMA);
        properties.add("spring.datasource.username",()->"mte_test");properties.add("spring.datasource.password",()->"");
        properties.add("spring.datasource.driver-class-name",()->"org.postgresql.Driver");
        properties.add("spring.flyway.enabled",()->true);properties.add("spring.flyway.schemas",()->SCHEMA);properties.add("spring.flyway.default-schema",()->SCHEMA);
        properties.add("spring.jpa.properties.hibernate.default_schema",()->SCHEMA);properties.add("spring.jpa.hibernate.ddl-auto",()->"validate");
        properties.add("mte.seed.enabled",()->false);properties.add("mte.auth.mode",()->"mock");properties.add("mte.auth.sso.enabled",()->false);
    }
    @Autowired JdbcTemplate jdbc;@Autowired Flyway flyway;@Autowired MockMvc mvc;@Autowired ObjectMapper mapper;
    @Autowired KrVersionRepository versions;
    record HttpResult(int status,JsonNode body) {}
    HttpResult rawCommand(String path,Object body,long version)throws Exception{
        var response=mvc.perform(post(path).header("If-Match",version).header("Idempotency-Key",UUID.randomUUID()).contentType("application/json").content(mapper.writeValueAsString(body))).andReturn().getResponse();return new HttpResult(response.getStatus(),mapper.readTree(response.getContentAsString()));
    }
    List<HttpResult> concurrent(String path,Object first,Object second,long version)throws Exception{
        var executor=Executors.newFixedThreadPool(2);var ready=new CountDownLatch(2);var start=new CountDownLatch(1);
        try{var a=executor.submit(()->{ready.countDown();if(!start.await(10,TimeUnit.SECONDS))throw new IllegalStateException("Start timeout");return rawCommand(path,first,version);});var b=executor.submit(()->{ready.countDown();if(!start.await(10,TimeUnit.SECONDS))throw new IllegalStateException("Start timeout");return rawCommand(path,second,version);});assertThat(ready.await(10,TimeUnit.SECONDS)).isTrue();start.countDown();return List.of(a.get(30,TimeUnit.SECONDS),b.get(30,TimeUnit.SECONDS));}finally{start.countDown();executor.shutdownNow();}
    }
    JsonNode command(String path,Object body,long version,int expected)throws Exception{
        var result=mvc.perform(post(path).header("If-Match",version).header("Idempotency-Key",UUID.randomUUID()).contentType("application/json").content(mapper.writeValueAsString(body))).andReturn();
        assertThat(result.getResponse().getStatus()).as(path+": "+result.getResponse().getContentAsString()).isEqualTo(expected);var bodyJson=mapper.readTree(result.getResponse().getContentAsString());if(expected>=400){assertThat(bodyJson.size()).isEqualTo(2);assertThat(bodyJson.path("traceId").asText()).isEqualTo(result.getResponse().getHeader("X-Trace-Id"));}return bodyJson;
    }
    @Test void allMigrationsAndReportReviewPersistWithRealAudit()throws Exception{
        assertThat(flyway.info().pending()).isEmpty();assertThat(flyway.info().current().getVersion().toString()).isEqualTo("35");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM flyway_schema_history WHERE success=TRUE AND type='SQL'",Long.class)).isEqualTo(36);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM institutional_policy",Long.class)).isZero();
        jdbc.update("INSERT INTO faculty(id,name) VALUES(10001,'Facultad de prueba')");jdbc.update("INSERT INTO school(id,name,faculty_id) VALUES(10001,'Escuela de prueba',10001)");jdbc.update("INSERT INTO department(id,name,school_id) VALUES(10010,'Departamento de prueba',10001)");
        jdbc.update("INSERT INTO professor(id,name,email,department_id,active) VALUES(10001,'Actor de prueba','demo.admin@icesi.edu.co',10010,TRUE)");
        jdbc.update("INSERT INTO user_role(professor_id,role_name,school_id) VALUES(10001,'DIRECTOR',10001)");jdbc.update("INSERT INTO user_role(professor_id,role_name) VALUES(10001,'RECTOR')");jdbc.update("INSERT INTO user_role(professor_id,role_name,faculty_id) VALUES(10001,'DECANO',10001)");jdbc.update("INSERT INTO user_role(professor_id,role_name,department_id) VALUES(10001,'JEFE',10010)");
        jdbc.update("INSERT INTO unit_of_measure(id,name,type) VALUES(10001,'Porcentaje de prueba','PORCENTAJE')");jdbc.update("INSERT INTO metric_definition(id,name,unit_of_measure_id,direction,source_description,frequency_days) VALUES(10001,'Métrica de prueba',10001,'MAINTAIN','Fuente',30)");jdbc.update("INSERT INTO strategic_cycle(id,name,start_date,end_date,cadence_days) VALUES(10001,'Ciclo de prueba','2026-01-01','2026-12-31',30)");
        command("/api/v2/policies/kr-links",new PoliciesV2Service.Write(0,0,"https://example.org/decision","Decisión de prueba",true),0,201);
        long kr=command("/api/v2/key-results",new StrategyV2Service.Create("KR","Descripción",10001L,new BigDecimal("40"),new BigDecimal("70"),10001L,10001L,"COMMITTED",List.of(),List.of()),0,201).path("data").path("id").asLong();assertThat(kr).isPositive();
        var activation=mvc.perform(patch("/api/v2/key-results/"+kr+"/status").header("If-Match",0).contentType("application/json").content("{\"newStatus\":\"ACTIVE\",\"reason\":\"Activación de prueba\"}")).andReturn();assertThat(activation.getResponse().getStatus()).as(activation.getResponse().getContentAsString()).isEqualTo(200);
        assertThat(mapper.readTree(versions.findByKeyResultIdOrderByVersionDesc(kr).get(0).getSnapshot()).isObject()).isTrue();
        long cut=command("/api/v2/monitoring-cuts",new MonitoringV2Service.CutWrite(10001L,10001L,"CORTE-REAL",java.time.LocalDate.of(2026,10,1)),0,201).path("data").path("id").asLong();command("/api/v2/monitoring-cuts/"+cut+"/validate",Map.of(),0,200);
        var branches=command("/api/v2/monitoring-cuts/"+cut+"/consolidate",new MonitoringV2Service.ConsolidateWrite("cut-command","Captura de prueba"),1,200).path("data");assertThat(branches.size()).isEqualTo(4);
        for(var branch:branches){long id=branch.path("id").asLong();boolean department="DEPARTMENT".equals(branch.path("branchType").asText());var entry=new MonitoringV2Service.EntryWrite(department?10010L:null,"Resultado de prueba",List.of("https://example.org/evidence"),List.of());var saved=command("/api/v2/monitoring-branches/"+id+(department?"/entries":"/review"),entry,0,201).path("data");if(department)command("/api/v2/monitoring-branches/"+id+"/department-results",new MonitoringV2Service.DepartmentDelivery(10010L,saved.path("entryId").asLong(),true),0,200);}
        long report=command("/api/v2/school-final-reports",new SchoolReportV2Service.Draft(cut,"Informe","Logros","Dificultades","Conclusiones","draft-command"),0,201).path("data").path("id").asLong();
        long auditBefore=jdbc.queryForObject("SELECT COUNT(*) FROM audit_log",Long.class);assertThat(command("/api/v2/school-final-reports/"+report+"/publish",new SchoolReportV2Service.Publish(1,"publish-command",true),0,409).path("error").asText()).isEqualTo("POLICY_UNSET");assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_log",Long.class)).isEqualTo(auditBefore);
        command("/api/v2/report-format-policy",new ReportFormatV2Service.Write("Formato de prueba",List.of("TITLE","CUT","BRANCH_RESULTS"),List.of("Rectoría"),"https://example.org/decision","Decisión de prueba",true),0,201);
        command("/api/v2/school-final-reports/"+report+"/publish",new SchoolReportV2Service.Publish(1,"publish-command",true),0,200);
        long beforeFailure=jdbc.queryForObject("SELECT COUNT(*) FROM audit_log",Long.class);jdbc.execute("ALTER TABLE audit_log ADD CONSTRAINT it_reject_review_audit CHECK (entity_type <> 'RECTOR_REVIEW')");
        command("/api/v2/school-final-reports/"+report+"/reviews",new SchoolReportV2Service.Review(1,"APPROVED","Soportes verificados",List.of("https://example.org/support"),"review-command"),1,409);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM rector_review",Long.class)).isZero();assertThat(jdbc.queryForObject("SELECT status FROM school_final_report WHERE id=?",String.class,report)).isEqualTo("PUBLISHED");assertThat(jdbc.queryForObject("SELECT transition_version FROM school_final_report WHERE id=?",Long.class,report)).isEqualTo(1);assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_log",Long.class)).isEqualTo(beforeFailure);jdbc.execute("ALTER TABLE audit_log DROP CONSTRAINT it_reject_review_audit");
        var race=concurrent("/api/v2/school-final-reports/"+report+"/reviews",new SchoolReportV2Service.Review(1,"APPROVED","Soportes verificados",List.of("https://example.org/support"),"review-command-a"),new SchoolReportV2Service.Review(1,"APPROVED","Soportes verificados",List.of("https://example.org/support"),"review-command-b"),1);assertThat(race.stream().map(HttpResult::status)).containsExactlyInAnyOrder(201,409);assertThat(race.stream().filter(result->result.status()==409).findFirst().orElseThrow().body().path("error").asText()).isEqualTo("VERSION_CONFLICT");
        long review=race.stream().filter(result->result.status()==201).findFirst().orElseThrow().body().path("data").path("review").path("id").asLong();assertThat(review).isPositive();assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM rector_review WHERE report_id=?",Long.class,report)).isEqualTo(1);
        var close=new SchoolReportV2Service.Finish(1,review,"finish-command",true);var finishRace=concurrent("/api/v2/school-final-reports/"+report+"/finish",close,close,2);assertThat(finishRace.stream().map(HttpResult::status)).containsExactly(200,200);var finished=finishRace.get(0).body().path("data");assertThat(finished.path("cutStatus").asText()).isEqualTo("FIN");assertThat(finished.path("finishedAt").asText()).isNotBlank();assertThat(finished.path("publicationFormat").path("recipients").get(0).asText()).isEqualTo("Rectoría");assertThat(jdbc.queryForObject("SELECT transition_version FROM school_final_report WHERE id=?",Long.class,report)).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_log WHERE entity_type='RECTOR_REVIEW' AND entity_id=?",Long.class,Long.toString(review))).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_log WHERE entity_type='MONITORING_CUT' AND entity_id=? AND after_snapshot LIKE '%FIN%'",Long.class,Long.toString(cut))).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT jsonb_typeof(evidence_urls) FROM rector_review WHERE id=?",String.class,review)).isEqualTo("array");assertThat(jdbc.queryForObject("SELECT jsonb_typeof(content_snapshot) FROM school_final_report WHERE id=?",String.class,report)).isEqualTo("object");assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM kr_progress_entry",Long.class)).isZero();
        targetApproval(kr);
        cycleClosure();
        java.nio.file.Files.writeString(java.nio.file.Path.of("target/local-postgres-runtime/validated-schema.txt"),SCHEMA,java.nio.charset.StandardCharsets.UTF_8);
    }
    HttpResult target(long kr,TargetApprovalV2Service.Write body,long version,String key)throws Exception{
        return target(kr,body,version,key,false);
    }
    HttpResult target(long kr,TargetApprovalV2Service.Write body,long version,String key,boolean direct)throws Exception{
        var builder=direct?patch("/api/v2/key-results/"+kr+"/target"):post("/api/v2/key-results/"+kr+"/target-requests");var response=mvc.perform(builder.header("If-Match",version).header("Idempotency-Key",key).contentType("application/json").content(mapper.writeValueAsString(body))).andReturn().getResponse();return new HttpResult(response.getStatus(),mapper.readTree(response.getContentAsString()));
    }
    HttpResult decision(long kr,long request,String status,String reason,String token)throws Exception{
        var response=mvc.perform(post("/api/v2/key-results/"+kr+"/target-requests/"+request+"/decision").header("Authorization","Bearer mock-token-"+token).header("If-Match",0).contentType("application/json").content(mapper.writeValueAsString(new TargetApprovalV2Service.Decision(status,reason)))).andReturn().getResponse();return new HttpResult(response.getStatus(),mapper.readTree(response.getContentAsString()));
    }
    void targetApproval(long kr)throws Exception{
        long checkIn=jdbc.queryForObject("INSERT INTO kr_measurement(key_result_id,measured_value,observed_at,registered_by) VALUES(?,49,CURRENT_TIMESTAMP,'Prueba') RETURNING id",Long.class,kr);
        jdbc.update("INSERT INTO kr_progress_entry(key_result_id,progress_percent,declared_by) VALUES(?,32,'Prueba')",kr);
        var change=new TargetApprovalV2Service.Write(10001L,BigDecimal.TEN,new BigDecimal("85"),"Cambio de prueba",checkIn);
        assertThat(mvc.perform(patch("/api/v2/key-results/"+kr+"/target").contentType("application/json").content(mapper.writeValueAsString(change))).andReturn().getResponse().getStatus()).isEqualTo(428);
        var absent=target(kr,change,1,"target-before-policy");assertThat(absent.status()).isEqualTo(409);assertThat(absent.body().path("error").asText()).isEqualTo("POLICY_UNSET");
        var matrix=new ApprovalPolicyV2Service.Write(List.of(new ApprovalPolicyV2Service.Rule("ACTIVE",10001L,true,List.of("RECTOR"),false)),"https://example.org/matrix","Decisión sintética de prueba",true);
        command("/api/v2/target-approval-policy",matrix,0,201);
        jdbc.update("INSERT INTO department(id,name,school_id) VALUES(10011,'Otro departamento de prueba',10001)");jdbc.update("INSERT INTO professor(id,name,email,department_id,active) VALUES(10002,'Rector de prueba','demo.user@icesi.edu.co',10011,TRUE)");jdbc.update("INSERT INTO user_role(professor_id,role_name) VALUES(10002,'RECTOR')");
        var required=target(kr,change,1,"target-direct-denied",true);assertThat(required.status()).isEqualTo(409);assertThat(required.body().path("error").asText()).isEqualTo("APPROVAL_REQUIRED");assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM kr_target_revision",Long.class)).isZero();
        var created=target(kr,change,1,"target-command");assertThat(created.status()).isEqualTo(201);long request=created.body().path("data").path("id").asLong();assertThat(created.body().path("data").path("status").asText()).isEqualTo("PENDING");
        assertThat(jdbc.queryForObject("SELECT version FROM key_result WHERE id=?",Long.class,kr)).isEqualTo(1);assertThat(jdbc.queryForObject("SELECT target_value FROM key_result WHERE id=?",BigDecimal.class,kr)).isEqualByComparingTo("70");
        assertThat(target(kr,change,1,"target-command").body().path("data").path("id").asLong()).isEqualTo(request);
        assertThat(target(kr,change,1,"target-command",true).status()).isEqualTo(409);
        assertThat(target(kr,new TargetApprovalV2Service.Write(10001L,BigDecimal.TEN,new BigDecimal("90"),"Otro",null),1,"target-command").status()).isEqualTo(409);
        var self=decision(kr,request,"APPROVED","Aprobar","admin");assertThat(self.status()).isEqualTo(409);assertThat(self.body().path("error").asText()).isEqualTo("SELF_APPROVAL_FORBIDDEN");
        // An actual PostgreSQL CHECK failure must roll back the KR change, version and decision together.
        jdbc.execute("ALTER TABLE audit_log ADD CONSTRAINT it_reject_target_audit CHECK (entity_type <> 'KR_TARGET_REVISION') NOT VALID");
        assertThat(decision(kr,request,"APPROVED","Aprobar","user").status()).isEqualTo(409);assertThat(jdbc.queryForObject("SELECT version FROM key_result WHERE id=?",Long.class,kr)).isEqualTo(1);assertThat(jdbc.queryForObject("SELECT approval_status FROM kr_target_revision WHERE id=?",String.class,request)).isEqualTo("PENDING");jdbc.execute("ALTER TABLE audit_log DROP CONSTRAINT it_reject_target_audit");
        var approved=decision(kr,request,"APPROVED","Aprobar","user");assertThat(approved.status()).isEqualTo(200);assertThat(approved.body().path("data").path("status").asText()).isEqualTo("APPROVED");assertThat(jdbc.queryForObject("SELECT version FROM key_result WHERE id=?",Long.class,kr)).isEqualTo(2);assertThat(jdbc.queryForObject("SELECT target_value FROM key_result WHERE id=?",BigDecimal.class,kr)).isEqualByComparingTo("85");
        assertThat(decision(kr,request,"APPROVED","Aprobar","user").status()).isEqualTo(200);assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM kr_version WHERE key_result_id=? AND version=2",Long.class,kr)).isEqualTo(1);
        var next=new TargetApprovalV2Service.Write(10001L,BigDecimal.TEN,new BigDecimal("90"),"Segunda solicitud",null);var second=target(kr,next,2,"target-second");long pending=second.body().path("data").path("id").asLong();assertThat(pending).isPositive();
        jdbc.update("UPDATE user_role SET ended_at=CURRENT_TIMESTAMP WHERE professor_id=10002 AND role_name='RECTOR'");assertThat(decision(kr,pending,"APPROVED","Aprobar","user").status()).isEqualTo(404);
        assertThat(mvc.perform(get("/api/v2/key-results/"+kr+"/target-requests").header("Authorization","Bearer mock-token-user")).andReturn().getResponse().getStatus()).isEqualTo(404);
        jdbc.update("INSERT INTO user_role(professor_id,role_name,department_id) VALUES(10002,'JEFE',10010)");assertThat(decision(kr,pending,"APPROVED","Aprobar","user").status()).isEqualTo(403);
        jdbc.update("INSERT INTO user_role(professor_id,role_name) VALUES(10002,'RECTOR')");command("/api/v2/target-approval-policy",matrix,1,201);var changed=decision(kr,pending,"APPROVED","Aprobar","user");assertThat(changed.status()).isEqualTo(409);assertThat(changed.body().path("error").asText()).isEqualTo("POLICY_CHANGED");
        assertThat(decision(kr,pending,"CANCELLED","Cancelar matriz obsoleta","admin").status()).isEqualTo(200);
        assertThat(jdbc.queryForObject("SELECT progress_percent FROM kr_progress_entry WHERE key_result_id=?",BigDecimal.class,kr)).isEqualByComparingTo("32");assertThat(jdbc.queryForObject("SELECT measured_value FROM kr_measurement WHERE id=?",BigDecimal.class,checkIn)).isEqualByComparingTo("49");
        var bypass=mvc.perform(patch("/api/v2/key-results/"+kr).header("If-Match",2).contentType("application/json").content(mapper.writeValueAsString(new StrategyV2Service.Revision(10001L,BigDecimal.TEN,new BigDecimal("95"),"Bypass")))).andReturn().getResponse();assertThat(bypass.getStatus()).isEqualTo(409);assertThat(mapper.readTree(bypass.getContentAsString()).path("error").asText()).isEqualTo("APPROVAL_REQUIRED");
        var automatic=new ApprovalPolicyV2Service.Write(List.of(new ApprovalPolicyV2Service.Rule("ACTIVE",10001L,false,List.of(),false)),"https://example.org/matrix-auto","Decisión sintética sin aprobación",true);command("/api/v2/target-approval-policy",automatic,2,201);
        var applied=target(kr,next,2,"target-auto",true);assertThat(applied.status()).isEqualTo(200);assertThat(applied.body().path("data").path("status").asText()).isEqualTo("APPROVED");assertThat(jdbc.queryForObject("SELECT version FROM key_result WHERE id=?",Long.class,kr)).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT progress_percent FROM kr_progress_entry WHERE key_result_id=?",BigDecimal.class,kr)).isEqualByComparingTo("32");assertThat(jdbc.queryForObject("SELECT measured_value FROM kr_measurement WHERE id=?",BigDecimal.class,checkIn)).isEqualByComparingTo("49");
        jdbc.update("UPDATE department SET active=FALSE WHERE id=10011");
    }
    long finalReport(long cycle,String code)throws Exception{
        return finalReport(cycle,code,10002L,10020L);
    }
    long finalReport(long cycle,String code,long school,long departmentId)throws Exception{
        long cut=command("/api/v2/monitoring-cuts",new MonitoringV2Service.CutWrite(school,cycle,code,java.time.LocalDate.of(2026,10,1)),0,201).path("data").path("id").asLong();command("/api/v2/monitoring-cuts/"+cut+"/validate",Map.of(),0,200);
        var branches=command("/api/v2/monitoring-cuts/"+cut+"/consolidate",new MonitoringV2Service.ConsolidateWrite(UUID.randomUUID().toString(),"Captura"),1,200).path("data");
        for(var branch:branches){long id=branch.path("id").asLong();boolean department="DEPARTMENT".equals(branch.path("branchType").asText());var saved=command("/api/v2/monitoring-branches/"+id+(department?"/entries":"/review"),new MonitoringV2Service.EntryWrite(department?departmentId:null,"Revisión",List.of("https://example.org/closure-evidence"),List.of()),0,201).path("data");if(department)command("/api/v2/monitoring-branches/"+id+"/department-results",new MonitoringV2Service.DepartmentDelivery(departmentId,saved.path("entryId").asLong(),true),0,200);}
        long report=command("/api/v2/school-final-reports",new SchoolReportV2Service.Draft(cut,"Cierre","Logros","Dificultades","Conclusiones",UUID.randomUUID().toString()),0,201).path("data").path("id").asLong();command("/api/v2/school-final-reports/"+report+"/publish",new SchoolReportV2Service.Publish(1,UUID.randomUUID().toString(),true),0,200);long review=command("/api/v2/school-final-reports/"+report+"/reviews",new SchoolReportV2Service.Review(1,"APPROVED","Revisión final",List.of("https://example.org/approved"),UUID.randomUUID().toString()),1,201).path("data").path("review").path("id").asLong();command("/api/v2/school-final-reports/"+report+"/finish",new SchoolReportV2Service.Finish(1,review,UUID.randomUUID().toString(),true),2,200);return report;
    }
    HttpResult krClose(String path,Object body,long version,String key,String token)throws Exception{
        var response=mvc.perform(patch(path).header("Authorization","Bearer mock-token-"+token).header("If-Match",version).header("Idempotency-Key",key).contentType("application/json").content(mapper.writeValueAsString(body))).andReturn().getResponse();return new HttpResult(response.getStatus(),mapper.readTree(response.getContentAsString()));
    }
    void cycleClosure()throws Exception{
        jdbc.update("INSERT INTO school(id,name,faculty_id) VALUES(10002,'Escuela de cierre',10001)");jdbc.update("INSERT INTO department(id,name,school_id) VALUES(10020,'Departamento de cierre',10002)");jdbc.update("INSERT INTO professor(id,name,email,department_id,active) VALUES(10003,'Responsable de cierre','demo.contributor@icesi.edu.co',10020,TRUE)");jdbc.update("INSERT INTO user_role(professor_id,role_name,school_id) VALUES(10001,'DIRECTOR',10002)");jdbc.update("INSERT INTO user_role(professor_id,role_name,department_id) VALUES(10001,'JEFE',10020)");jdbc.update("INSERT INTO strategic_cycle(id,name,start_date,end_date,cadence_days) VALUES(10002,'Ciclo para cierre','2026-01-01','2026-12-31',30)");
        var ids=new java.util.ArrayList<Long>();for(int i=0;i<2;i++){long id=command("/api/v2/key-results",new StrategyV2Service.Create("KR cierre "+i,"Definición",10001L,new BigDecimal("10"),new BigDecimal("80"),10003L,10002L,"COMMITTED",List.of(),List.of()),0,201).path("data").path("id").asLong();ids.add(id);mvc.perform(patch("/api/v2/key-results/"+id+"/status").header("If-Match",0).contentType("application/json").content(mapper.writeValueAsString(new StrategyV2Service.StatusWrite("ACTIVE","Inicio",null)))).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());}
        command("/api/v2/key-results/"+ids.get(0)+"/measurements",new TrackingV2Service.MeasurementWrite(new BigDecimal("40"),java.time.Instant.now(),"Medición final"),0,201);jdbc.update("INSERT INTO kr_progress_entry(key_result_id,progress_percent,declared_by) VALUES(?,0,'Prueba')",ids.get(1));
        long initiative=command("/api/v2/key-results/"+ids.get(0)+"/initiatives",new TrackingV2Service.InitiativeWrite(ids.get(0),10020L,"Iniciativa del ciclo","Descripción",10003L),0,201).path("data").path("id").asLong();
        var missing=new CycleClosureV2Service.Write(List.of(999999L),"Logros","Causas","Aprendizajes","Continuidad",true);assertThat(command("/api/v2/cycles/10002/close",missing,0,409).path("error").asText()).isEqualTo("FINAL_REVIEW_REQUIRED");long report=finalReport(10002,"CIERRE-1");
        command("/api/v2/key-results/"+ids.get(0)+"/measurements",new TrackingV2Service.MeasurementWrite(new BigDecimal("41"),java.time.Instant.now(),"Medición posterior"),0,201);var stale=new CycleClosureV2Service.Write(List.of(report),"Logros","Causas","Aprendizajes","Continuidad",true);assertThat(command("/api/v2/cycles/10002/close",stale,0,409).path("error").asText()).isEqualTo("FINAL_SNAPSHOT_STALE");
        long fresh=finalReport(10002,"CIERRE-2");String path="/api/v2/cycles/10002/close";
        String individual="/api/v2/key-results/"+ids.get(0)+"/status";var closeKr=new StrategyV2Service.StatusWrite("CLOSED","Cierre individual aprobado",true,fresh);String individualKey="kr-close-command";
        assertThat(krClose(individual,new StrategyV2Service.StatusWrite("CLOSED","Motivo",true,report),1,"stale","admin").body().path("error").asText()).isEqualTo("FINAL_REVIEW_REQUIRED");
        assertThat(krClose(individual,closeKr,1,"denied","contributor").status()).isEqualTo(403);
        jdbc.execute("ALTER TABLE audit_log ADD CONSTRAINT it_reject_individual CHECK (entity_type <> 'KEY_RESULT' OR action <> 'STATUS_CHANGE') NOT VALID");assertThat(krClose(individual,closeKr,1,individualKey,"admin").status()).isEqualTo(409);assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM key_result_closure",Long.class)).isZero();assertThat(jdbc.queryForObject("SELECT version FROM key_result WHERE id=?",Long.class,ids.get(0))).isEqualTo(1);jdbc.execute("ALTER TABLE audit_log DROP CONSTRAINT it_reject_individual");
        assertThat(mvc.perform(patch(individual).header("Idempotency-Key",individualKey).contentType("application/json").content(mapper.writeValueAsString(closeKr))).andReturn().getResponse().getStatus()).isEqualTo(428);
        assertThat(krClose(individual,new StrategyV2Service.StatusWrite("CLOSED","Motivo",false,fresh),1,individualKey,"admin").status()).isEqualTo(422);assertThat(krClose(individual,closeKr,0,individualKey,"admin").body().path("error").asText()).isEqualTo("VERSION_CONFLICT");
        assertThat(mvc.perform(patch("/api/v1/key-results/"+ids.get(0)+"/current-value").contentType("application/json").content("{\"currentValue\":42}")).andReturn().getResponse().getStatus()).isEqualTo(409);
        assertThat(mvc.perform(patch("/api/v1/key-results/"+ids.get(0)+"/status").contentType("application/json").content("{\"status\":\"CERRADO\"}")).andReturn().getResponse().getStatus()).isEqualTo(409);
        var individualClosed=krClose(individual,closeKr,1,individualKey,"admin");assertThat(individualClosed.status()).as(individualClosed.body().toString()).isEqualTo(200);assertThat(krClose(individual,closeKr,1,individualKey,"admin").status()).isEqualTo(200);assertThat(krClose(individual,closeKr,1,"different","admin").body().path("error").asText()).isEqualTo("KR_ALREADY_CLOSED");
        assertThat(command("/api/v2/initiatives/"+initiative+"/progress",new TrackingV2Service.ProgressWrite(BigDecimal.TEN,"ON_TRACK","KR cerrado",0L),0,409).path("error").asText()).isEqualTo("INACTIVE_RESOURCE");
        assertThat(command("/api/v2/key-results/"+ids.get(0)+"/measurements",new TrackingV2Service.MeasurementWrite(new BigDecimal("42"),java.time.Instant.now(),"KR cerrado"),0,409).path("error").asText()).isEqualTo("INACTIVE_RESOURCE");
        long inactiveKr=command("/api/v2/key-results",new StrategyV2Service.Create("KR inactivo","Histórico conservado",10001L,BigDecimal.TEN,new BigDecimal("80"),10003L,10002L,"COMMITTED",List.of(),List.of()),0,201).path("data").path("id").asLong();
        mvc.perform(patch("/api/v2/key-results/"+inactiveKr+"/active").header("If-Match",0).contentType("application/json").content(mapper.writeValueAsString(new StrategyV2Service.DeactivateWrite("Desactivación histórica",true)))).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());
        long historicalKr=command("/api/v2/key-results",new StrategyV2Service.Create("KR cierre legado","Cierre anterior sin captura",10001L,BigDecimal.TEN,new BigDecimal("80"),10003L,10002L,"COMMITTED",List.of(),List.of()),0,201).path("data").path("id").asLong();
        // Synthetic historical import, not a bypass exposed by the current API.
        jdbc.update("UPDATE key_result SET status='CERRADO',version=1 WHERE id=?",historicalKr);
        assertThat(command(path,new CycleClosureV2Service.Write(List.of(fresh),"Logros","Causas","Aprendizajes","Continuidad",true),0,409).path("error").asText()).isEqualTo("FINAL_REVIEW_REQUIRED");
        long latest=finalReport(10002,"CIERRE-3");
        jdbc.update("INSERT INTO school(id,name,faculty_id) VALUES(10003,'Escuela sólo histórica',10001)");jdbc.update("INSERT INTO department(id,name,school_id) VALUES(10030,'Departamento histórico',10003)");jdbc.update("INSERT INTO professor(id,name,email,department_id,active) VALUES(10004,'Responsable histórico','history.fixture@example.org',10030,TRUE)");jdbc.update("INSERT INTO user_role(professor_id,role_name,school_id) VALUES(10001,'DIRECTOR',10003)");jdbc.update("INSERT INTO user_role(professor_id,role_name,department_id) VALUES(10001,'JEFE',10030)");
        long onlyInactive=command("/api/v2/key-results",new StrategyV2Service.Create("KR único inactivo","Escuela sin KRs activos",10001L,BigDecimal.TEN,new BigDecimal("80"),10004L,10002L,"COMMITTED",List.of(),List.of()),0,201).path("data").path("id").asLong();mvc.perform(patch("/api/v2/key-results/"+onlyInactive+"/active").header("If-Match",0).contentType("application/json").content(mapper.writeValueAsString(new StrategyV2Service.DeactivateWrite("Conservar histórico",true)))).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());long historicalReport=finalReport(10002,"CIERRE-HISTORICO",10003L,10030L);


        long otherKr=command("/api/v2/key-results",new StrategyV2Service.Create("KR otra escuela","Definición",10001L,new BigDecimal("10"),new BigDecimal("80"),10001L,10002L,"COMMITTED",List.of(),List.of()),0,201).path("data").path("id").asLong();mvc.perform(patch("/api/v2/key-results/"+otherKr+"/status").header("If-Match",0).contentType("application/json").content(mapper.writeValueAsString(new StrategyV2Service.StatusWrite("ACTIVE","Inicio",null)))).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());
        assertThat(command(path,new CycleClosureV2Service.Write(List.of(fresh),"Logros","Causas","Aprendizajes","Continuidad",true),0,409).path("error").asText()).isEqualTo("FINAL_REVIEW_REQUIRED");long otherReport=finalReport(10002,"CIERRE-OTRA-ESCUELA",10001L,10010L);var body=new CycleClosureV2Service.Write(List.of(latest,fresh,otherReport,historicalReport),"Logros","Causas","Aprendizajes","Continuidad",true);
        assertThat(command(path,new CycleClosureV2Service.Write(List.of(latest,fresh,otherReport,historicalReport),"","Causas","Aprendizajes","Continuidad",true),0,422).path("error").asText()).isEqualTo("RETROSPECTIVE_REQUIRED");
        assertThat(mvc.perform(post(path).header("Authorization","Bearer mock-token-contributor").header("If-Match",0).header("Idempotency-Key","denied").contentType("application/json").content(mapper.writeValueAsString(body))).andReturn().getResponse().getStatus()).isEqualTo(403);
        jdbc.execute("ALTER TABLE audit_log ADD CONSTRAINT it_reject_cycle_audit CHECK (entity_type <> 'STRATEGIC_CYCLE' OR action <> 'STATUS_CHANGE') NOT VALID");command(path,body,0,409);assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM cycle_closure",Long.class)).isZero();assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM cycle_closure_snapshot",Long.class)).isZero();assertThat(jdbc.queryForObject("SELECT status FROM strategic_cycle WHERE id=10002",String.class)).isEqualTo("DRAFT");jdbc.execute("ALTER TABLE audit_log DROP CONSTRAINT it_reject_cycle_audit");
        var race=concurrent(path,body,body,0);assertThat(race.stream().map(HttpResult::status)).containsExactlyInAnyOrder(200,409);assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM cycle_closure WHERE cycle_id=10002",Long.class)).isEqualTo(1);assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM cycle_closure_snapshot",Long.class)).isEqualTo(6);assertThat(jdbc.queryForObject("SELECT final_manual_progress FROM cycle_closure_snapshot WHERE key_result_id=?",BigDecimal.class,ids.get(0))).isNull();assertThat(jdbc.queryForObject("SELECT final_manual_progress FROM cycle_closure_snapshot WHERE key_result_id=?",BigDecimal.class,ids.get(1))).isEqualByComparingTo("0");assertThat(jdbc.queryForObject("SELECT final_measured_value FROM cycle_closure_snapshot WHERE key_result_id=?",BigDecimal.class,ids.get(0))).isEqualByComparingTo("41");
        var mutation=mvc.perform(post("/api/v2/key-results/"+ids.get(0)+"/measurements").contentType("application/json").content(mapper.writeValueAsString(new TrackingV2Service.MeasurementWrite(new BigDecimal("42"),java.time.Instant.now(),"Después de cierre")))).andReturn().getResponse();assertThat(mutation.getStatus()).isEqualTo(409);assertThat(mapper.readTree(mutation.getContentAsString()).path("error").asText()).isEqualTo("CYCLE_CLOSED");
        assertThat(command("/api/v2/initiatives/"+initiative+"/progress",new TrackingV2Service.ProgressWrite(new BigDecimal("10"),"ON_TRACK","Después de cierre",0L),0,409).path("error").asText()).isEqualTo("CYCLE_CLOSED");
        String key=jdbc.queryForObject("SELECT command_key FROM cycle_closure WHERE cycle_id=10002",String.class);assertThat(mvc.perform(post(path).header("If-Match",0).header("Idempotency-Key",key).contentType("application/json").content(mapper.writeValueAsString(body))).andReturn().getResponse().getStatus()).isEqualTo(200);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_log WHERE entity_type='STRATEGIC_CYCLE' AND entity_id='10002' AND action='STATUS_CHANGE'",Long.class)).isEqualTo(1);assertThat(jdbc.queryForObject("SELECT definition_snapshot->>'description' FROM cycle_closure_snapshot WHERE key_result_id=?",String.class,ids.get(0))).isEqualTo("Definición");
        assertThat(krClose(individual,closeKr,1,individualKey,"admin").status()).isEqualTo(200);assertThat(jdbc.queryForObject("SELECT version FROM key_result WHERE id=?",Long.class,ids.get(0))).isEqualTo(2);assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_log WHERE entity_type='KEY_RESULT' AND entity_id=? AND action='STATUS_CHANGE'",Long.class,String.valueOf(ids.get(0)))).isEqualTo(2);
        assertThat(mvc.perform(get("/api/v2/key-results/"+ids.get(0)+"/closure")).andReturn().getResponse().getStatus()).isEqualTo(200);assertThat(mvc.perform(get("/api/v2/cycles/10002/closure")).andReturn().getResponse().getStatus()).isEqualTo(200);
        for(long archivedId:List.of(inactiveKr,historicalKr,onlyInactive)){assertThat(jdbc.queryForObject("SELECT version FROM key_result WHERE id=?",Long.class,archivedId)).isEqualTo(1);assertThat(jdbc.queryForObject("SELECT closure_origin FROM cycle_closure_snapshot WHERE key_result_id=?",String.class,archivedId)).isEqualTo("LIFECYCLE_RECONCILIATION");assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM kr_version WHERE key_result_id=? AND version>1",Long.class,archivedId)).isZero();}
        assertThat(jdbc.queryForObject("SELECT final_status FROM cycle_closure_snapshot WHERE key_result_id=?",String.class,inactiveKr)).isEqualTo("INACTIVE");assertThat(jdbc.queryForObject("SELECT final_status FROM cycle_closure_snapshot WHERE key_result_id=?",String.class,historicalKr)).isEqualTo("CLOSED");assertThat(jdbc.queryForObject("SELECT active FROM key_result WHERE id=?",Boolean.class,inactiveKr)).isFalse();assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM cycle_closure_snapshot WHERE source_report_id IS NULL OR source_review_id IS NULL",Long.class)).isZero();
        var historicalContent=mapper.readTree(jdbc.queryForObject("SELECT content_snapshot::text FROM school_final_report WHERE id=?",String.class,historicalReport));assertThat(historicalContent.path("cutReport").path("keyResults").size()).isZero();assertThat(historicalContent.path("cutReport").path("lifecycleInventory").size()).isEqualTo(1);
        var latestVersion=mapper.readTree(versions.findByKeyResultIdOrderByVersionDesc(ids.get(0)).get(0).getSnapshot());assertThat(latestVersion.path("after").path("status").asText()).isEqualTo("CLOSED");assertThat(latestVersion.path("after").path("baseValue").decimalValue()).isEqualByComparingTo("10");assertThat(latestVersion.path("before").path("status").asText()).isEqualTo("ACTIVE");
    }
}
