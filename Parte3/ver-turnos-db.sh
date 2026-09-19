#!/bin/bash
# script para consultar las reservas y datos en mariadb

echo "=== reservas registradas en base de datos ==="
docker exec -i reservas-db mariadb -u reservas_app -padmin reservas -e "
SELECT r.id, r.id_personal, p.nombre AS profesional, r.email_solicitante, r.fecha_turno, r.hora_turno, r.estado
FROM reservas_turnos r
JOIN personal p ON r.id_personal = p.id
ORDER BY r.id DESC;
"
