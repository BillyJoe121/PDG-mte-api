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
            throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE,
                    "La sincronización con Trayectoria no está configurada en este ambiente.");
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
