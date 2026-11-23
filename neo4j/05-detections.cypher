// Primera deteccion de los incidentes registrados antes de agrupar detecciones.
// Sin ella, los focos nuevos no podrian unirse a esos incidentes.
// Se puede ejecutar varias veces: solo actua sobre incidentes sin detecciones.

MATCH (i:Incident)
WHERE NOT EXISTS { (:Detection)-[:DETECTION_OF]->(i) }
CREATE (d:Detection {
    id: i.id,
    latitude: i.latitude,
    longitude: i.longitude,
    location: point({latitude: i.latitude, longitude: i.longitude}),
    observedAt: i.occurredAt,
    receivedAt: i.receivedAt,
    source: i.source
})
CREATE (d)-[:DETECTION_OF]->(i)
SET i.detectionCount = coalesce(i.detectionCount, 1),
    i.lastDetectedAt = coalesce(i.lastDetectedAt, i.occurredAt);
