# ADR-CAT-DEPLOYMENT-001 - Contrato y despliegue oficial

Decision del equipo para publicacion, 2026-10-11. Docker y OpenAPI acompanados
de una prueba HTTP con servidor, PostgreSQL y JWKS reales. No acredita QA/main.

La imagen multietapa ejecuta Maven verify con Java 17 y usa JRE como UID 10001.
Compose usa el motor compartido, espera migraciones y Seguridad saludable, sin
puertos por defecto. compose.dev es opt-in y expone solo loopback 58082, no main.
El despliegue requiere valores de infraestructura; no se incluyen passwords/JWT.
Docker excluye .git, targets, env y secrets; no empaquetar .env reales.
El contrato OpenAPI describe creacion/replay, consultas y mantenimiento; ETag
se obtiene de GET por UUID, no del snapshot historico de la creacion.

La prueba recibe URLs y la ruta privada del env de Seguridad, nunca imprime
passwords ni tokens. Agrega un medicamento sintetico con codigo aleatorio,
lo edita/desactiva y conserva ese fixture: no borra datos ni toca filas previas.
Verifica 401, los cuatro roles reales, escrituras 403 pese al header ADMIN falso,
idempotencia, JSON estricto, ETag/412/428, unidad inmutable y snapshot conservado.
No prueba aqui carreras concurrentes, reintentos tras reinicio, rollback DB
ni el recorrido gateway/saga. La instancia temporal tiene nombre propio y
puerto dinamico; nunca reemplaza el servicio activo ni su alias de red catalog.
