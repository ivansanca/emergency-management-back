// Zonas: los 102 municipios de Cantabria (identificados por codigo INE)
// Requiere zones.csv en la carpeta import de Neo4j

LOAD CSV WITH HEADERS FROM 'file:///zones.csv' AS row
MERGE (z:Zone {ineCode: row.ine_code})
SET z.name = row.name,
    z.latitude = toFloat(row.latitude),
    z.longitude = toFloat(row.longitude);
