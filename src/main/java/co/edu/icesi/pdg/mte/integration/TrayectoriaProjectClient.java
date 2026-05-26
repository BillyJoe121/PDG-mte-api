package co.edu.icesi.pdg.mte.integration;

import co.edu.icesi.pdg.mte.common.BusinessException;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Component
public class TrayectoriaProjectClient {
    private final IntegrationProperties properties;
    private final WebClient.Builder webClientBuilder;

    public TrayectoriaProjectClient(
            IntegrationProperties properties,
            WebClient.Builder webClientBuilder
    ) {
        this.properties = properties;
        this.webClientBuilder = webClientBuilder;
    }

    public List<ExternalProjectPayload> fetchProjects(String bearerToken) {
        if ("mock".equalsIgnoreCase(properties.getMode())) {
            return mockProjects();
        }
        if (bearerToken == null || bearerToken.isBlank()) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "Se requiere token Bearer para sincronizar proyectos externos.");
        }
        JsonNode payload = webClientBuilder.build()
                .get()
                .uri(properties.getExternalBaseUrl() + properties.getProjectsPath())
                .header(HttpHeaders.AUTHORIZATION, bearerToken)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block();
        return parseProjectList(payload);
    }

    private List<ExternalProjectPayload> mockProjects() {
        return List.of(
                new ExternalProjectPayload(
                        9001L,
                        "Observatorio de indicadores TDI",
                        "Proyecto sincronizado de demostracion para validar seguimiento estrategico.",
                        "EN_CURSO",
                        "investigacion",
                        "Departamento de Computaci\u00f3n y Sistemas inteligentes.",
                        "2026-1",
                        "2026-2",
                        LocalDate.of(2026, 1, 15),
                        LocalDate.of(2026, 11, 30),
                        List.of("Ana Torres", "Luis Rojas"),
                        "{\"mock\":true,\"id_proyecto\":9001}"
                ),
                new ExternalProjectPayload(
                        9002L,
                        "Laboratorio de experiencias digitales",
                        "Proyecto externo simulado para probar sincronizacion sin depender del otro backend.",
                        "BORRADOR",
                        "extension",
                        "Departamento de Dise\u00f1o e Innovaci\u00f3n",
                        "2026-1",
                        "2026-1",
                        LocalDate.of(2026, 2, 1),
                        LocalDate.of(2026, 6, 20),
                        List.of("Marta Gomez"),
                        "{\"mock\":true,\"id_proyecto\":9002}"
                )
        );
    }

    private List<ExternalProjectPayload> parseProjectList(JsonNode payload) {
        if (payload == null) {
            return List.of();
        }
        JsonNode items = payload.isArray() ? payload : payload.path("content");
        if (!items.isArray()) {
            items = payload.path("data");
        }
        if (!items.isArray()) {
            items = payload.path("items");
        }
        if (!items.isArray()) {
            return List.of(parseProject(payload));
        }

        List<ExternalProjectPayload> projects = new ArrayList<>();
        for (JsonNode item : items) {
            projects.add(parseProject(item));
        }
        return projects;
    }

    private ExternalProjectPayload parseProject(JsonNode node) {
        return new ExternalProjectPayload(
                longValue(node, "id_proyecto", "idProyecto", "id", "externalProjectId"),
                textValue(node, "nombre_proyecto", "nombreProyecto", "name", "nombre"),
                textValue(node, "descripcion", "description"),
                textValue(node, "estado", "status"),
                textValue(node, "tipo", "type"),
                textValue(node, "departamento", "departmentName", "nombre_departamento"),
                textValue(node, "periodo_inicio", "startPeriod", "start_period"),
                textValue(node, "periodo_fin", "endPeriod", "end_period"),
                dateValue(node, "fecha_inicio", "startDate"),
                dateValue(node, "fecha_fin", "endDate"),
                tutors(node),
                node.toString()
        );
    }

    private Long longValue(JsonNode node, String... names) {
        for (String name : names) {
            JsonNode value = node.path(name);
            if (value.isNumber()) {
                return value.longValue();
            }
            if (value.isTextual()) {
                try {
                    return Long.parseLong(value.asText());
                } catch (NumberFormatException ignored) {
                    return null;
                }
            }
        }
        return null;
    }

    private String textValue(JsonNode node, String... names) {
        for (String name : names) {
            JsonNode value = node.path(name);
            if (!value.isMissingNode() && !value.isNull() && !value.asText().isBlank()) {
                return value.asText();
            }
        }
        return null;
    }

    private LocalDate dateValue(JsonNode node, String... names) {
        String value = textValue(node, names);
        return value == null ? null : LocalDate.parse(value);
    }

    private List<String> tutors(JsonNode node) {
        JsonNode participantes = node.path("participantes");
        if (!participantes.isArray()) {
            participantes = node.path("tutors");
        }
        List<String> tutors = new ArrayList<>();
        for (JsonNode participante : participantes) {
            String name = textValue(participante, "nombre", "name");
            if (name != null) {
                tutors.add(name);
            }
        }
        return tutors;
    }
}
