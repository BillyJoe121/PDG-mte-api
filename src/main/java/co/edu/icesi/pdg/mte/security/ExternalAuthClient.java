package co.edu.icesi.pdg.mte.security;

import co.edu.icesi.pdg.mte.common.BusinessException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@Component
public class ExternalAuthClient {

    private final RestClient restClient;
    private final AuthProperties properties;

    public ExternalAuthClient(RestClient.Builder builder, AuthProperties properties) {
        this.properties = properties;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.introspectionTimeout());
        requestFactory.setReadTimeout(properties.introspectionTimeout());
        this.restClient = builder
                .baseUrl(properties.externalBaseUrl())
                .requestFactory(requestFactory)
                .build();
    }

    public ExternalUserContext introspect(String bearerToken) {
        try {
            Map<?, ?> response = restClient.get()
                    .uri(properties.introspectionPath())
                    .header(HttpHeaders.AUTHORIZATION, bearerToken)
                    .retrieve()
                    .body(Map.class);

            if (response == null) {
                throw new BusinessException(HttpStatus.UNAUTHORIZED, "No fue posible validar el token externo.");
            }

            Map<?, ?> professor = response.get("profesor") instanceof Map<?, ?> map ? map : Map.of();
            return new ExternalUserContext(
                    asLong(response.get("id_usuario")),
                    asString(response.get("username")),
                    asString(response.get("email")),
                    asStringList(response.get("roles")),
                    asStringList(response.get("permisos")),
                    asLong(professor.get("id_profesor")),
                    joinName(professor),
                    asString(professor.get("departamento"))
            );
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "Token externo invalido o servicio de autenticacion no disponible.");
        }
    }

    private Long asLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            return Long.parseLong(text);
        }
        return null;
    }

    private String asString(Object value) {
        return value == null ? null : value.toString();
    }

    private List<String> asStringList(Object value) {
        if (value instanceof List<?> list) {
            return list.stream().map(Object::toString).toList();
        }
        return List.of();
    }

    private String joinName(Map<?, ?> professor) {
        String name = asString(professor.get("nombre"));
        String lastName = asString(professor.get("apellido"));
        if (name == null) {
            return lastName;
        }
        if (lastName == null) {
            return name;
        }
        return name + " " + lastName;
    }
}
