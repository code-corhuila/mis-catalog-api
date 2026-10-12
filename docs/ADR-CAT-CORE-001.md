# ADR-CAT-CORE-001 - Publicacion incremental del nucleo

## Estado

Decision del equipo para la publicacion oficial, 2026-10-11. Este primer PR
publica el nucleo existente con pruebas y decision juntas, no un servicio completo.

## Decision y contrato

Separar modelo/validacion/puertos Java de HTTP, JPA y JWT. `Medications` define
crear/consultar/listar y mantenimiento versionado; los adaptadores futuros
implementaran transacciones, snapshots y permisos. No hay lectura SQL cruzada.
Las claves de creacion se conservan sin trim, UUID deben ser canonicos y los
campos de medicamento se normalizan con limites. Paginacion: page>=1, limit 1..100.
La ausencia de If-Match se expresa como 428 y una sintaxis no fuerte como 400;
la comprobacion de version obsoleta pertenece al adaptador (412).

## Consecuencias y alcance

Este modulo puede compilarse/probarse sin motor ni credenciales. El POM agrega
solo `catalog-core`; se incorporaran otros modulos con su codigo, sin placeholders
que rompan la compilacion. No se promete idempotencia persistente con estos tests:
se prueba validacion del puerto. Tampoco se prueba autorizacion ni bloqueo de fila.
La implementacion local completa sigue funcionando desde su carpeta original.
