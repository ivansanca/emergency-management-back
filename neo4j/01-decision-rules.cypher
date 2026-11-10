// Reglas de decision para incendios forestales (idempotente: MERGE)

MERGE (r:DecisionRule {id: 'RULE-FOREST-LOW'})
SET r.name = 'Incendio forestal - gravedad 1 a 3',
    r.description = 'Regla de demostración para incendios forestales de gravedad baja o moderada.',
    r.incidentType = 'FOREST_FIRE',
    r.minSeverity = 1, r.maxSeverity = 3,
    r.priority = 100, r.active = true
WITH r
UNWIND ['FIRE_CREW', 'WATER_TENDER'] AS typeName
MERGE (rt:ResourceType {name: typeName})
MERGE (r)-[:ALLOWS_RESOURCE_TYPE]->(rt);

MERGE (r:DecisionRule {id: 'RULE-FOREST-HIGH'})
SET r.name = 'Incendio forestal - gravedad 4 a 5',
    r.description = 'Regla de demostración para incendios forestales de gravedad alta.',
    r.incidentType = 'FOREST_FIRE',
    r.minSeverity = 4, r.maxSeverity = 5,
    r.priority = 100, r.active = true
WITH r
UNWIND ['FIRE_CREW', 'WATER_TENDER', 'COMMAND_UNIT'] AS typeName
MERGE (rt:ResourceType {name: typeName})
MERGE (r)-[:ALLOWS_RESOURCE_TYPE]->(rt);
