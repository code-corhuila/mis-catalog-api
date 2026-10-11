# ADR-CAT-HTTP-001 - Adaptador HTTP de Catalogo

## Estado y alcance

Decision del equipo para publicacion oficial, 2026-10-11. Controladores y errores
acompanados de pruebas HTTP aisladas. No publica todavia servidor ni filtros JWT.

## Contrato y decision

POST `/api/v1/medications` usa Idempotency-Key: creacion 201 con Location, replay
200; conflicto de huella 409. GET de coleccion usa page=1/limit=20 por defecto,
limite 1..100; GET por UUID devuelve recurso original de nueve campos y ETag fuerte.
PUT por UUID normaliza los seis campos de creacion y exige If-Match: 428 si falta,
400 si sintaxis invalida, 412 si obsoleto, 422 si cambia unidad/codigo ocupado.
POST `/{id}/deactivate` acepta cuerpo ausente o {}, nunca reactivacion/borrado.
Ambas mutaciones devuelven recurso y ETag vigente; no alteran stock ni snapshots.

Los controladores declaran ADMIN para escribir y los cuatro roles para leer.
Estas anotaciones requieren method security y filtro JWT del servidor posterior;
no son una proteccion activa en los tests standalone con principal sintetico.
Errores usan error/message/traceId y details opcionales. Excepciones de DB se
traducen a 503 sin exponer SQL ni credenciales. El filtro futuro proveera traceId
y encabezado X-Correlation-Id; aqui los tests suministran el atributo directamente.

## Verificacion y limites

MockMvc usa persistencia simulada y despacha HTTP real dentro del proceso de test:
creacion/replay, ETag, PUT, 428, desactivacion, JSON roto, paginacion y fallo de DB.
No valida firma JWT, autorizacion, coercion JSON estricta ni transacciones reales.
Esas comprobaciones y OpenAPI se incorporaran con configuracion/servidor posteriores.
`/health` consulta solamente la tabla propia; no se publica ningun puerto en este PR.
