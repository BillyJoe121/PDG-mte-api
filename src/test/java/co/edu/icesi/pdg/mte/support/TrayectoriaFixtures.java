package co.edu.icesi.pdg.mte.support;

import co.edu.icesi.pdg.mte.integration.ExternalProjectPayload;

import java.time.LocalDate;
import java.util.List;

/** Projects as the Trayectoria client returns them, for tests that exercise the sync flow. */
public final class TrayectoriaFixtures {
    private TrayectoriaFixtures() {
    }

    public static List<ExternalProjectPayload> projects() {
        return List.of(
                new ExternalProjectPayload(
                        9001L,
                        "Observatorio de indicadores TDI",
                        "Proyecto externo de prueba para validar el seguimiento estratégico.",
                        "EN_CURSO",
                        "investigacion",
                        "Departamento de Computación y Sistemas inteligentes.",
                        "2026-1",
                        "2026-2",
                        LocalDate.of(2026, 1, 15),
                        LocalDate.of(2026, 11, 30),
                        List.of("Ana Torres", "Luis Rojas"),
                        "{\"id_proyecto\":9001}"
                ),
                new ExternalProjectPayload(
                        9002L,
                        "Laboratorio de experiencias digitales",
                        "Proyecto externo de prueba para la sincronización.",
                        "BORRADOR",
                        "extension",
                        "Departamento de Diseño e Innovación",
                        "2026-1",
                        "2026-1",
                        LocalDate.of(2026, 1, 15),
                        LocalDate.of(2026, 6, 30),
                        List.of(),
                        "{\"id_proyecto\":9002}"
                )
        );
    }
}
