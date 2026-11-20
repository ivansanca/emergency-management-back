# emergency-management

Servicio de gestión de emergencias para Cantabria. Recibe incidentes (por REST o
por Kafka), los registra en un grafo Neo4j y recomienda qué recursos enviar
según unas reglas de decisión y las rutas por carretera entre municipios. El
sistema recomienda; la asignación y el cierre los decide un operador.

## Tecnologías

- Java 21 y Spring Boot 4
- Neo4j: municipios, carreteras, recursos, reglas e incidentes
- Kafka: entrada de eventos y notificaciones de salida
- Testcontainers para los tests de integración

## Requisitos

- JDK 21
- Docker con Docker Compose (para Kafka)
- Neo4j en `bolt://localhost:7687`, sin autenticación. No está incluido en el
  `docker-compose.yml`: hay que tenerlo instalado y arrancado aparte.

## Puesta en marcha

### 1. Levantar Kafka

```bash
docker compose up -d
```

| Servicio         | URL / puerto                                |
|------------------|---------------------------------------------|
| Kafka            | `localhost:9092`                            |
| Redpanda Console | http://localhost:8081 (para ver los topics) |

### 2. Cargar los datos en Neo4j

Copia los CSV de [`neo4j/data`](neo4j/data) a la carpeta `import` de Neo4j y
ejecuta los scripts de [`neo4j`](neo4j) en orden, desde Neo4j Browser o con
`cypher-shell -f`. Son idempotentes, así que se pueden volver a lanzar.

| Script                     | Contenido                                           |
|----------------------------|-----------------------------------------------------|
| `00-constraints.cypher`    | Restricciones de unicidad                           |
| `01-decision-rules.cypher` | Reglas de decisión y tipos de recurso que requieren |
| `02-zones.cypher`          | Los 102 municipios (código INE y coordenadas)       |
| `03-roads.cypher`          | Carreteras entre municipios vecinos (km y minutos)  |
| `04-resources.cypher`      | Vehículos de los parques de bomberos                |

### 3. Arrancar la aplicación

```bash
./mvnw spring-boot:run
```

La aplicación escucha en http://localhost:8080 y crea los topics de Kafka al
arrancar. Para comprobar que está levantada: `GET /actuator/health`.

## API

| Método | Ruta                                         | Descripción                                             |
|--------|----------------------------------------------|---------------------------------------------------------|
| POST   | `/api/incidents`                             | Registra un incidente (lo publica en Kafka, responde 202) |
| GET    | `/api/incidents/{id}/recommendations`        | Recursos recomendados para el incidente                 |
| POST   | `/api/incidents/{id}/assignments`            | Asigna un recurso (`{"resourceId": "..."}`)             |
| POST   | `/api/incidents/{id}/close`                  | Cierra el incidente y libera sus recursos               |
| POST   | `/api/resources`                             | Da de alta un recurso                                   |

Los errores se devuelven en formato Problem Details (RFC 9457).

Ejemplo de incidente (la ubicación se indica con `zoneCode`, con coordenadas o
con ambos):

```bash
curl -X POST http://localhost:8080/api/incidents \
  -H "Content-Type: application/json" \
  -d '{"type": "FOREST_FIRE", "severity": 1, "zoneCode": "39075"}'
```

En [`http/ciclo-vida-incidente.http`](http/ciclo-vida-incidente.http) hay el
ciclo completo (recomendación, asignación y cierre) listo para lanzar desde el
IDE.

## Topics de Kafka

| Topic                     | Uso                                                        |
|---------------------------|------------------------------------------------------------|
| `emergency-events`        | Entrada de incidentes, de cualquier origen                 |
| `emergency-events-dlt`    | Eventos rechazados (inválidos o que han agotado reintentos) |
| `incident-created`        | Notificación: incidente registrado                         |
| `incident-recommendation` | Notificación: recomendación calculada                      |
| `resource-assigned`       | Notificación: recurso asignado                             |
| `incident-closed`         | Notificación: incidente cerrado                            |

Las notificaciones usan la clave del incidente, así que las de un mismo
incidente llegan en orden.

## Tests

```bash
./mvnw verify
```

Ejecuta los tests unitarios y los de integración, que levantan Neo4j y Kafka con
Testcontainers (requiere Docker). El informe de cobertura de JaCoCo queda en
`target/site/jacoco/index.html`.
