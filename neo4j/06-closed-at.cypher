// Fecha de cierre (closedAt) de los incidentes cerrados antes de que se guardara.
// Es una aproximacion: la ultima liberacion de un recurso, que se hace al cerrar,
// o, si no tuvo recursos asignados, su ultima deteccion.
// Solo toca los cerrados sin closedAt, asi que se puede volver a ejecutar.

MATCH (i:Incident)
WHERE i.status = 'CLOSED' AND i.closedAt IS NULL
OPTIONAL MATCH (:Resource)-[a:ASSIGNED_TO]->(i)
WITH i, max(a.releasedAt) AS lastRelease
SET i.closedAt = coalesce(lastRelease, i.lastDetectedAt, i.occurredAt);
