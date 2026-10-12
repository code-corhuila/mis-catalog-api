# ADR-CAT-PERSISTENCE-001 - Publicar persistencia de Catalogo

## Estado y alcance

Decision del equipo para publicacion oficial, 2026-10-11. Acompana al adaptador
existente en este PR. No publica HTTP, servidor ni autenticacion y no cierra la HU.

## Decision

JPA mapea exclusivamente `catalog.medication` y `catalog.creation_request`.
Crear medicamento y snapshot idempotente comparte una transaccion. Un bloqueo
advisory transaccional por actor/operacion/clave serializa reintentos; la huella
SHA-256 compara campos normalizados. Replay devuelve el JSON original (no lee
el medicamento actual); otra huella con la misma clave se rechaza con 409.
El codigo unico se resuelve con la restriccion PostgreSQL y se traduce a 422.

Mantenimiento bloquea la fila con PESSIMISTIC_WRITE y compara version (412).
La unidad no cambia (422); estados iguales no escriben ni avanzan auditoria.
JDBC actualiza solo columnas mutables/version/auditoria, sin ensuciar la entidad
JPA gestionada ni intentar UPDATE de columnas inmutables. No hay reactivacion.
Desactivar no borra datos ni altera stock. La paginacion usa REPEATABLE_READ.

## Consecuencias y verificacion

Requiere los cuatro changesets de `mis-catalog-db`. La API futura usara
`catalog_app`, no credenciales administrativas. No se escriben esquemas ajenos.
Siete pruebas del adaptador usan mocks: verifican replay, conflicto, version,
unidad, no-op y columnas actualizadas. No prueban transacciones reales, carreras
entre replicas ni bloqueos efectivos; eso requiere PostgreSQL y el servidor.
El workflow de CI compila ambos modulos y ejecuta esas pruebas y las del nucleo.
