#!/bin/bash
# ==============================================================================
# Script para consultar los turnos persistidos en MariaDB y el estado de tablas
# ==============================================================================

echo "=================================================================="
echo " 1. ESTABLECIMIENTOS REGISTRADOS                                  "
echo "=================================================================="
docker exec -i reservas-db mariadb -u reservas_app -padmin reservas -e "
SELECT id, nombre_comercial, horario_apertura, horario_cierre, telefono FROM establecimientos;
"

echo ""
echo "=================================================================="
echo " 2. PERSONAL DEL ESTABLECIMIENTO (PROFESIONALES)                  "
echo "=================================================================="
docker exec -i reservas-db mariadb -u reservas_app -padmin reservas -e "
SELECT id, id_establecimiento, nombre, especialidad, costo_consulta, estado FROM personal;
"

echo ""
echo "=================================================================="
echo " 3. RESERVAS DE TURNOS CONFIRMADAS (Últimas 15)                   "
echo "=================================================================="
docker exec -i reservas-db mariadb -u reservas_app -padmin reservas -e "
SELECT r.id, r.id_personal, p.nombre AS profesional, r.email_solicitante, r.fecha_turno, r.hora_turno, r.duracion_minutos, r.estado
FROM reservas_turnos r
JOIN personal p ON r.id_personal = p.id
ORDER BY r.id DESC
LIMIT 15;
"
