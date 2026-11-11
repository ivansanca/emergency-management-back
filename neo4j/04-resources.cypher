// Vehiculos y equipos de los parques de bomberos de Cantabria
// Requiere resources.csv en la carpeta import de Neo4j y las zonas ya cargadas
// Idempotente: si un vehiculo cambia de tipo o de zona, se sustituyen sus relaciones

LOAD CSV WITH HEADERS FROM 'file:///resources.csv' AS row
WITH row
WHERE coalesce(row.resource_type, '') <> ''
  AND coalesce(row.resource_code, '') <> ''
MATCH (z:Zone {ineCode: row.zone_code})
MERGE (rt:ResourceType {name: row.resource_type})
MERGE (r:Resource {id: row.resource_code})
SET r.name = row.resource_description + ' - ' + row.station_name,
    r.type = row.resource_type,
    r.available = true
WITH r, z, rt
OPTIONAL MATCH (r)-[old:HAS_TYPE|LOCATED_IN]->()
DELETE old
WITH DISTINCT r, z, rt
MERGE (r)-[:LOCATED_IN]->(z)
MERGE (r)-[:HAS_TYPE]->(rt);
