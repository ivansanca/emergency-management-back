// Restricciones de unicidad (cada una crea tambien un indice)

CREATE CONSTRAINT zone_ine_code IF NOT EXISTS
FOR (z:Zone) REQUIRE z.ineCode IS UNIQUE;

CREATE CONSTRAINT resource_id IF NOT EXISTS
FOR (r:Resource) REQUIRE r.id IS UNIQUE;

CREATE CONSTRAINT incident_id_unique IF NOT EXISTS
FOR (i:Incident) REQUIRE i.id IS UNIQUE;

CREATE CONSTRAINT resource_type_name_unique IF NOT EXISTS
FOR (rt:ResourceType) REQUIRE rt.name IS UNIQUE;

CREATE CONSTRAINT decision_rule_id_unique IF NOT EXISTS
FOR (r:DecisionRule) REQUIRE r.id IS UNIQUE;

CREATE CONSTRAINT detection_id_unique IF NOT EXISTS
FOR (d:Detection) REQUIRE d.id IS UNIQUE;

// Nodo de bloqueo que serializa el registro de incidentes (ver IncidentRepository)
CREATE CONSTRAINT incident_registration_lock_id IF NOT EXISTS
FOR (l:IncidentRegistrationLock) REQUIRE l.id IS UNIQUE;

// Indice espacial para buscar detecciones cercanas al agrupar
CREATE POINT INDEX detection_location IF NOT EXISTS
FOR (d:Detection) ON (d.location);
