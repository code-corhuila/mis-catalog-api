# mis-catalog-api

> catalog bounded context: service API

Part of the **Medicine Inventory System** distributed system — team `medicine-inventory-system`, Grupo 1.
Governance and documentation live in [`mis-docs`](https://github.com/code-corhuila/mis-docs).

## Branching

Three permanent branches. **None of them accepts a direct commit** — you enter through a child
branch and leave through a Pull Request.

```
develop  <--PR--  feat/... fix/... chore/...
qa       <--PR--  qa/...
main     <--PR--  release/...  hotfix/...
```

Promotion happens **by re-application** (`git cherry-pick -x`), never by merging one permanent
branch into another: `merge develop -> qa` and `merge qa -> main` do not exist in this model.

`main` requires **1 approval from `ariel5253`**. On `develop` and `qa` the team sets its own review
rule.

Full policy: `00-governance/branching-policy.md` in `mis-docs`.

## Publicacion inicial: nucleo de HU-CAT-001 y HU-CAT-002

Este PR publica `catalog-core`: modelo, validacion y puertos, sin framework en
el codigo de dominio. El primer PR compilo solo ese modulo.
La publicacion inicial no incluia controladores ni servidor.

Con Java 17 y Maven: `mvn -B verify`. CI ejecuta el mismo comando en PR hacia
develop/qa, con concurrencia cancelable y timeout de 10 minutos. Siete pruebas
comprueban normalizacion, limites, UUID canonico, clave idempotente, paginacion
y sintaxis de If-Match. No demuestran persistencia, autenticacion o ETag HTTP.

El puerto de mantenimiento usa version fuerte; los adaptadores deben imponer
control de concurrencia, permisos ADMIN y conservar auditoria/snapshots.
Ver [ADR-CAT-CORE-001](docs/ADR-CAT-CORE-001.md).

## Publicacion de persistencia JPA/JDBC

El padre Maven ahora incluye `catalog-adapters` con entidades, repositorios y
`JpaMedications`. No se modifica el nucleo ni las migraciones ya publicadas.
`mvn -B verify` ejecuta siete pruebas del nucleo y siete del adaptador con mocks.
Estas ultimas no sustituyen pruebas reales de transaccion/concurrencia en PostgreSQL.
La decision de replay, campos inmutables y mantenimiento se publica junto al codigo
en [ADR-CAT-PERSISTENCE-001](docs/ADR-CAT-PERSISTENCE-001.md).

## Publicacion HTTP (servidor todavia pendiente)

`MedicationApi` y `Errors` publican rutas de medicamentos y traduccion de errores.
Ocho pruebas MockMvc se suman a las catorce anteriores: `mvn -B verify`.
El principal y la persistencia estan simulados: no se afirma autorizacion ni DB real.
JWT, method security, correlacion y JSON estricto se publicaran con `catalog-app`.
Todavia no hay servidor ejecutable ni puerto expuesto en este clon.
Contrato y limites: [ADR-CAT-HTTP-001](docs/ADR-CAT-HTTP-001.md).
