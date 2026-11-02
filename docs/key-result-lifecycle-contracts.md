# Contratos: ciclo de vida de los Key Results

Complementa [frontend-api-contracts.md](frontend-api-contracts.md). Todas las rutas cuelgan de `/api/v1`. ADMIN y MANAGER pueden escribir; VISITOR solo lee. Los campos que pueden ser `null` (`plannerUrl`, `archivedAt` y los valores `previous*` del historial) se serializan siempre, con valor `null`, para que el frontend no tenga que distinguir entre ausente y vacio.

## 1. Enlace a Planner

`plannerUrl` es un campo opcional (`string | null`) de `KeyResultRequest`/`KeyResultResponse` y de `ProjectRequest`/`ProjectUpdateRequest`/`ProjectResponse` (iniciativas). El sistema solo lo guarda y lo devuelve: nunca abre el enlace ni lee su contenido.

- `null` o texto en blanco limpia el enlace; en cualquier otro caso se guarda recortado.
- Debe ser `https`, sin usuario ni contrasena, puerto 443 (o sin puerto) y un maximo de 2048 caracteres.
- El dominio debe pertenecer a Microsoft: `tasks.office.com`, `planner.cloud.microsoft`, `office.com`, `office365.com`, `sharepoint.com`, `microsoft.com` o `cloud.microsoft` (o un subdominio de ellos).
- Si no cumple: `400` con `El enlace de Planner no es valido: <motivo>.`

## 2. Archivar y restaurar un KR

El borrado fisico desaparece: `DELETE /key-results/{id}` ya no existe.

| Metodo | Endpoint | Query | Body | Respuesta | Acceso |
|---|---|---|---|---|---|
| PATCH | `/key-results/{id}/active` | - | `{ "active": boolean }` | `KeyResultResponse` | ADMIN, MANAGER |
| GET | `/key-results` | `includeArchived?` (default `false`), `goalId?`, `strategicBetId?`, `periodId?` | - | `KeyResultResponse[]` | autenticado |

- `active: false` archiva, `active: true` restaura. La operacion es idempotente.
- `KeyResultResponse` agrega `archived: boolean` y `archivedAt: string | null` (instante ISO-8601; `null` mientras no este archivado).
- Archivar falla con `409` si el KR esta cerrado o tiene vinculos activos con iniciativas.
- Un KR archivado no admite cambios: `PUT`, `PATCH .../progress` y nuevos vinculos con iniciativas responden `409` (`El KR esta archivado y no admite cambios ni nuevos vinculos.`). Restaurarlo lo habilita de nuevo.
- `GET /key-results/{id}` sigue devolviendo el KR archivado, con `archived: true`.
- Un KR archivado se excluye de la jerarquia estrategica, los resumenes de ejecucion, el dashboard, los reportes, la presentacion y las verificaciones de consistencia. `GET /key-results` solo lo incluye con `includeArchived=true`.
- Archivar y restaurar quedan en auditoria como `STATUS_CHANGE`.

## 3. Historial de avance

| Metodo | Endpoint | Respuesta | Acceso |
|---|---|---|---|
| GET | `/key-results/{id}/progress-records` | `KeyResultProgressRecordResponse[]` | autenticado (cualquier rol) |

```json
KeyResultProgressRecordResponse: {
  "id": 12,
  "keyResultId": 3,
  "previousValue": 25.00,
  "currentValue": 60.00,
  "previousPercentage": 25.00,
  "progressPercentage": 60.00,
  "authorName": "Ana Perez",
  "authorEmail": "ana.perez@icesi.edu.co",
  "recordedAt": "2026-03-02T10:00:00Z"
}
```

- Orden: del mas reciente al mas antiguo (`recordedAt` y luego `id`, descendente). Sin registros devuelve `[]`.
- Cada `PATCH /key-results/{id}/progress` exitoso guarda un registro en la misma transaccion. Las peticiones rechazadas (validacion, KR cerrado o archivado) no guardan nada, ni tampoco archivar, restaurar o cambiar el estado.
- `previousValue` y `previousPercentage` son `null` cuando el KR no tenia avance registrado antes. No hay datos retroactivos: solo existen registros desde que se desplego esta version.
- `authorName` es el nombre del profesor del usuario autenticado (o su username si no tiene nombre) y `authorEmail` su correo.
- El historial de un KR archivado sigue siendo consultable. Un KR inexistente responde `404`.

Migraciones: `V12` (enlace a Planner), `V13` (archivado) y `V14` (tabla `key_result_progress_record`).
