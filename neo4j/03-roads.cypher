// Carreteras entre municipios vecinos (km y minutos calculados con OSRM)
// Requiere roads.csv en la carpeta import de Neo4j y las zonas ya cargadas
// El estado (active) solo se fija al crear la carretera: volver a ejecutarlo no reabre las cortadas

LOAD CSV WITH HEADERS FROM 'file:///roads.csv' AS row
MATCH (a:Zone {ineCode: row.from_ine})
MATCH (b:Zone {ineCode: row.to_ine})
MERGE (a)-[c:CONNECTS_TO]->(b)
ON CREATE SET c.active = true
SET c.distanceKm = toFloat(row.distance_km),
    c.travelTimeMinutes = toFloat(row.travel_time_min);
