# ADR-CAT-SERVER-001 - Servidor y seguridad de Catalogo

Decision del equipo para publicacion oficial, 2026-10-11. Acompana catalog-app.
Spring Boot inicia los tres modulos. JPA solo valida el esquema ya migrado;
usa catalog_app, no administrador. Variables de DB/password/JWKS sin secretos.

JWT se verifica con JWKS y exclusivamente RS256, issuer mis-security, audience
mis-api, UUID canonico, expiracion, kid y roles reconocidos. Sesiones stateless,
ADMIN para mutaciones, los cuatro roles para lectura; method security activo.
Health es publico y no interpreta bearer; requiere disponibilidad de tabla propia.
401/403 usan errores JSON con correlacion, sin filtrar detalles internos.

JSON rechaza propiedades desconocidas y coercion de numero/booleano a texto.
Correlacion validada de 1..128 caracteres o UUID generado, limpia MDC al terminar.
Limite 65536 bytes: POST y PUT se leen acotadamente aunque falte Content-Length;
se corrige la omision de PUT en la preparacion local previa. No se publica Docker
ni se reemplaza el despliegue activo en este PR. OpenAPI y despliegue siguen despues.

Pruebas criptograficas usan RSA temporal: roles/claims, firma ajena, none y HMAC.
Pruebas de filtro/JSON verifican limites POST/PUT, correlacion y coercion estricta.
No prueban aqui permisos HTTP integrados con el filtro real, disponibilidad JWKS
remota ni arranque contra PostgreSQL. Esos recorridos requieren integracion posterior.
