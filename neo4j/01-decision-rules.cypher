// Reglas de decision (idempotente: cada ejecucion deja exactamente estas reglas y necesidades)
// FOREST_FIRE: gravedad = Indice de Gravedad Potencial (IGP 0-3) del plan INFOCANT (Decreto 192/2023)
// STRUCTURE_FIRE: gravedad = nivel de emergencia 1-4 del manual de procedimientos del SEMCA
// Las cantidades son una propuesta del TFM: ninguna de las dos fuentes las fija
// Necesidades con el mismo "group" se cubren con cualquiera de sus tipos (p. ej. ENGINES)

MERGE (rt:ResourceType {name: 'AERIAL_UNIT'})
SET rt.aerial = true;

MATCH (r:DecisionRule)
WHERE r.id IN ['RULE-FOREST-LOW', 'RULE-FOREST-HIGH']
DETACH DELETE r;

UNWIND [
  {id: 'RULE-FOREST-IGP0', type: 'FOREST_FIRE', severity: 0,
   name: 'Incendio forestal - IGP 0',
   description: 'Sin amenaza para personas ajenas al dispositivo; daño principalmente forestal.',
   needs: [{types: ['FOREST_ENGINE', 'RURAL_ENGINE'], group: 'ENGINES', quantity: 2},
           {types: ['WATER_TENDER'], quantity: 1}]},
  {id: 'RULE-FOREST-IGP1', type: 'FOREST_FIRE', severity: 1,
   name: 'Incendio forestal - IGP 1',
   description: 'Requiere proteger personas o bienes aislados: viviendas aisladas, infraestructuras o redes de suministro.',
   needs: [{types: ['FOREST_ENGINE', 'RURAL_ENGINE'], group: 'ENGINES', quantity: 3},
           {types: ['WATER_TENDER'], quantity: 1},
           {types: ['COMMAND_UNIT'], quantity: 1}]},
  {id: 'RULE-FOREST-IGP2', type: 'FOREST_FIRE', severity: 2,
   name: 'Incendio forestal - IGP 2',
   description: 'Amenaza seria a núcleos de población o infraestructuras de especial importancia.',
   needs: [{types: ['FOREST_ENGINE', 'RURAL_ENGINE'], group: 'ENGINES', quantity: 4},
           {types: ['WATER_TENDER'], quantity: 2},
           {types: ['COMMAND_UNIT'], quantity: 1},
           {types: ['URBAN_ENGINE'], quantity: 1},
           {types: ['AERIAL_UNIT'], quantity: 1}]},
  {id: 'RULE-FOREST-IGP3', type: 'FOREST_FIRE', severity: 3,
   name: 'Incendio forestal - IGP 3',
   description: 'Circunstancias extremas que impiden al dispositivo controlar el incendio.',
   needs: [{types: ['FOREST_ENGINE', 'RURAL_ENGINE'], group: 'ENGINES', quantity: 6},
           {types: ['WATER_TENDER'], quantity: 3},
           {types: ['COMMAND_UNIT'], quantity: 2},
           {types: ['URBAN_ENGINE'], quantity: 2},
           {types: ['AERIAL_UNIT'], quantity: 1}]},
  {id: 'RULE-STRUCTURE-L1', type: 'STRUCTURE_FIRE', severity: 1,
   name: 'Incendio estructural - nivel 1',
   description: 'No supone riesgo inminente para personas, pero requiere la intervención de bomberos.',
   needs: [{types: ['URBAN_ENGINE'], quantity: 1}]},
  {id: 'RULE-STRUCTURE-L2', type: 'STRUCTURE_FIRE', severity: 2,
   name: 'Incendio estructural - nivel 2',
   description: 'Riesgo inminente para personas. Mínimo para incendios en viviendas, lonjas o bajos comerciales.',
   needs: [{types: ['URBAN_ENGINE'], quantity: 2},
           {types: ['AERIAL_LADDER'], quantity: 1}]},
  {id: 'RULE-STRUCTURE-L3', type: 'STRUCTURE_FIRE', severity: 3,
   name: 'Incendio estructural - nivel 3',
   description: 'Riesgo inminente para personas con posible necesidad de refuerzos: viviendas de 22:00 a 08:00, industrias, pública concurrencia, hospitales, centros educativos, residencias, hoteles, garajes y túneles.',
   needs: [{types: ['URBAN_ENGINE'], quantity: 3},
           {types: ['AERIAL_LADDER'], quantity: 1},
           {types: ['WATER_TENDER'], quantity: 1},
           {types: ['COMMAND_UNIT'], quantity: 1},
           {types: ['RESCUE_UNIT'], quantity: 1}]},
  {id: 'RULE-STRUCTURE-L4', type: 'STRUCTURE_FIRE', severity: 4,
   name: 'Incendio estructural - nivel 4',
   description: 'Múltiples víctimas o posible activación de un plan de protección civil, incluidos establecimientos con Plan de Emergencia Exterior.',
   needs: [{types: ['URBAN_ENGINE'], quantity: 4},
           {types: ['AERIAL_LADDER'], quantity: 2},
           {types: ['WATER_TENDER'], quantity: 2},
           {types: ['COMMAND_UNIT'], quantity: 2},
           {types: ['RESCUE_UNIT'], quantity: 2},
           {types: ['MEDICAL_UNIT'], quantity: 1},
           {types: ['LOGISTICS_UNIT'], quantity: 1}]}
] AS d
MERGE (r:DecisionRule {id: d.id})
SET r.name = d.name,
    r.description = d.description,
    r.incidentType = d.type,
    r.minSeverity = d.severity,
    r.maxSeverity = d.severity,
    r.priority = 100,
    r.active = true
WITH r, d
OPTIONAL MATCH (r)-[old:ALLOWS_RESOURCE_TYPE]->()
DELETE old
WITH DISTINCT r, d
UNWIND d.needs AS need
UNWIND need.types AS typeName
MERGE (rt:ResourceType {name: typeName})
CREATE (r)-[:ALLOWS_RESOURCE_TYPE {
    quantity: need.quantity,
    group: coalesce(need.group, typeName)
}]->(rt);
