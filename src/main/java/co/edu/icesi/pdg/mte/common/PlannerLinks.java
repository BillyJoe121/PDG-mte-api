package co.edu.icesi.pdg.mte.common;

import org.springframework.http.HttpStatus;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Locale;

/**
 * Validation shared by every record that stores a Planner link (key results and initiatives).
 * The link is only stored and shown back; the system never follows it nor reads its content.
 */
public final class PlannerLinks {
    public static final int MAX_LENGTH = 2048;

    private static final List<String> ALLOWED_DOMAINS = List.of(
            "tasks.office.com",
            "planner.cloud.microsoft",
            "office.com",
            "office365.com",
            "sharepoint.com",
            "microsoft.com",
            "cloud.microsoft"
    );

    private PlannerLinks() {
    }

    /**
     * Returns the link trimmed, or {@code null} when the value is null or blank (which clears the link).
     *
     * @throws BusinessException with status 400 when the value is not an https link to a Microsoft domain
     */
    public static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String link = value.trim();
        if (link.length() > MAX_LENGTH) {
            throw invalid("supera los " + MAX_LENGTH + " caracteres");
        }
        URI uri;
        try {
            uri = new URI(link);
        } catch (URISyntaxException exception) {
            throw invalid("no es una URL bien formada");
        }
        if (!"https".equalsIgnoreCase(uri.getScheme())) {
            throw invalid("debe usar https");
        }
        if (uri.getRawUserInfo() != null) {
            throw invalid("no puede incluir usuario ni contrasena");
        }
        if (uri.getHost() == null) {
            throw invalid("no tiene un dominio valido");
        }
        if (uri.getPort() != -1 && uri.getPort() != 443) {
            throw invalid("solo admite el puerto 443");
        }
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        if (ALLOWED_DOMAINS.stream().noneMatch(domain -> host.equals(domain) || host.endsWith("." + domain))) {
            throw invalid("el dominio debe pertenecer a Microsoft (" + String.join(", ", ALLOWED_DOMAINS) + ")");
        }
        return link;
    }

    private static BusinessException invalid(String reason) {
        return new BusinessException(HttpStatus.BAD_REQUEST, "El enlace de Planner no es valido: " + reason + ".");
    }
}
