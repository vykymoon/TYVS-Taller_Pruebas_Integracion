# Registro de Defectos — Taller de Pruebas de Integración y Sistema

Este documento recopila los **defectos reales detectados durante el desarrollo y ejecución de las pruebas** del proyecto **Registraduría**, encontrados por mí durante la implementación de las suites de pruebas de sistema y de bases de datos reales.

> Nota: el proyecto base clonado ya traía corregidos los defectos de dominio documentados en `defectos_template.md` / el ejemplo del profesor (edad negativa, persona fallecida, duplicados, mocks, HTTP 500 por género inválido) — verificado revisando `Registry.java` y confirmando que `RegistryIT` y `RegistryWithMockTest` ya cubren esos casos en verde desde la primera ejecución. Los defectos documentados a continuación son distintos: los encontré yo mismo al extender la suite de pruebas de sistema (HTTP) y al configurar Testcontainers.

---

## Defecto 01 — Campo numérico faltante no se rechaza (queda en 0 silenciosamente)

- **Capa afectada:** Delivery / validación de entrada (`PersonDTO`, `RegistryController`)
- **Caso de prueba:** Envío de un JSON de registro **sin el campo `age`**.
- **Entrada:**
```json
{ "name": "Incompleto", "id": 105, "gender": "MALE", "alive": true }
```
- **Resultado esperado:** `HTTP 400 Bad Request` — un registro sin edad es una entrada inválida o inconsistente, según el punto 5 de la rúbrica del taller (entrada inválida → 400/422).
- **Resultado obtenido (antes de la corrección):** `HTTP 200 OK` con cuerpo `"UNDERAGE"`. El campo `age`, al estar declarado como `int` primitivo en `PersonDTO`, no puede representar la ausencia del dato: Jackson lo deserializa como `0` en lugar de rechazar la petición, y `0` cae dentro de la regla de negocio como "menor de edad".
- **Causa probable:** Uso de tipos primitivos (`int`) en el DTO de entrada en vez de tipos envolventes (`Integer`). Un `int` nunca puede ser `null`, así que cualquier anotación `@NotNull` sobre ese campo es inalcanzable: para cuando la validación se ejecuta, el valor ya es `0`, no `null`.
- **Tipo de prueba que lo evidenció:** Sistema (HTTP, `TestRestTemplate`) — se detectó al diseñar el caso "body incompleto" exigido por la rúbrica, antes de tener un test automatizado que lo cubriera.
- **Estado:** **Resuelto.**
    - Se cambiaron `id` y `age` de `int` a `Integer` en `PersonDTO`.
    - Se agregaron anotaciones `@NotNull`, `@NotBlank` y `@Positive` (paquete `javax.validation.constraints`, acorde a Spring Boot 2.7.18).
    - Se agregó `@Valid` en la firma de `RegistryController.register(...)`.
    - Se agregó un `@ExceptionHandler(MethodArgumentNotValidException.class)` en `RegistryExceptionHandler` que traduce el fallo de validación a `400 Bad Request`.
    - Verificado por `RegistryControllerIT.shouldReturnBadRequestWhenBodyIsIncomplete()`.
- **Prioridad:** Alta — sin esta corrección, cualquier cliente que omitiera un campo numérico obtenía una respuesta exitosa con un resultado de negocio incorrecto, en vez de un error claro indicando el dato faltante.

---

## Defecto 02 — Pruebas de Testcontainers se saltan silenciosamente por incompatibilidad de versión de API con Docker

- **Capa afectada:** Infraestructura de pruebas (configuración de entorno, no código de producción)
- **Caso de prueba:** Ejecución de `RegistryRepositoryPostgresIT` (5 pruebas contra PostgreSQL real vía Testcontainers) con Docker Desktop 4.90 corriendo y con el motor ("Engine running") activo.
- **Resultado esperado:** Las 5 pruebas se ejecutan contra un contenedor real de `postgres:16-alpine`.
- **Resultado obtenido:** Las 5 pruebas se reportaban como `Skipped` (no fallidas) en cada ejecución de `mvn clean verify`, con el siguiente error en el log:
```
ERROR --- o.t.d.DockerClientProviderStrategy : Could not find a valid Docker environment.
```
a pesar de que `docker version` y el estado de Docker Desktop confirmaban que el motor estaba activo.
- **Causa probable:** La librería `docker-java` (dependencia transitiva de Testcontainers 1.19.8) intenta negociar por defecto una versión de la API de Docker anterior a la que expone el Docker Engine incluido en Docker Desktop 4.90, y la negociación falla de forma silenciosa — la extensión `@EnabledIf("hayDocker")` interpreta esa falla como "Docker no disponible" y salta la clase completa en lugar de fallar con un error explícito.
- **Tipo de prueba que lo evidenció:** Bases de datos reales (Testcontainers) — el defecto no está en el código de producción ni en las pruebas en sí, sino en la configuración del cliente Docker usado por la librería de pruebas.
- **Estado:** **Resuelto.** Se creó el archivo `src/test/resources/docker-java.properties` fijando explícitamente la versión de la API:
```properties
api.version=1.44
```
Tras agregarlo, las 5 pruebas de `RegistryRepositoryPostgresIT` se ejecutan y pasan sin saltarse (`Tests run: 18, Failures: 0, Errors: 0, Skipped: 0` en el resumen combinado de `mvn clean verify`).
- **Prioridad:** Alta para el desarrollo local — sin esta corrección, un desarrollador con una versión reciente de Docker Desktop puede creer que "no tiene pruebas de Testcontainers" cuando en realidad nunca se ejecutan, y el build reporta éxito (`BUILD SUCCESS`) de forma engañosa al no distinguir "saltado por diseño" de "saltado por una incompatibilidad de configuración".

---

## Tabla resumen

| ID | Caso de Prueba | Capa | Resultado Esperado | Resultado Obtenido | Tipo | Estado | Prioridad |
|----|----------------|------|--------------------|--------------------|------|--------|-----------|
| 01 | Body sin campo `age` | Delivery / DTO | `HTTP 400` | `HTTP 200` con `"UNDERAGE"` | Sistema (HTTP) | Resuelto | Alta |
| 02 | Suite Testcontainers con Docker activo | Infraestructura de pruebas | 5 pruebas ejecutadas contra PostgreSQL real | 5 pruebas `Skipped` silenciosamente | Testcontainers | Resuelto | Alta |

---

## Convenciones de Estado

| Estado | Significado |
|---|---|
| **Abierto** | El defecto fue detectado pero no corregido. |
| **En progreso** | El defecto se encuentra en análisis o corrección. |
| **Resuelto** | El defecto fue corregido y validado mediante pruebas. |

---

## Observaciones

- El Defecto 01 muestra por qué la elección de tipos en un DTO (`int` vs `Integer`) no es un detalle menor: afecta directamente qué puede y qué no puede detectar una anotación de validación como `@NotNull`.
- El Defecto 02 no es un defecto de la aplicación bajo prueba, sino de la infraestructura de pruebas misma — se documenta porque ilustra un riesgo real: un build "verde" con pruebas saltadas puede ocultar que una técnica completa (bases de datos reales) nunca se está ejecutando.
- Ambos defectos fueron encontrados de forma incremental, mientras se completaban los criterios de la rúbrica del taller (validación de entrada HTTP y ejecución real de Testcontainers), no mediante un plan de pruebas exploratorias independiente.