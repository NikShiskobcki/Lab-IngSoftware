#!/bin/bash
# script para probar las validaciones del validador asincrono (us-35, us-36, us-37, us-38)

echo "=== 1. crear turno con fecha en el pasado (us-37) ==="
docker exec -i reservas-db mariadb -u reservas_app -padmin reservas -e "
INSERT INTO reservas_turnos (id_personal, email_solicitante, telefono_solicitante, fecha_turno, hora_turno, duracion_minutos, fecha_registro, estado)
VALUES (1, 'pasado@test.com', '099000111', '2020-01-01', '10:00:00', 30, NOW(), 'SOLICITADO');
"

echo "=== 2. crear turno con personal inexistente (us-36) ==="
docker exec -i reservas-db mariadb -u reservas_app -padmin reservas -e "
INSERT INTO reservas_turnos (id_personal, email_solicitante, telefono_solicitante, fecha_turno, hora_turno, duracion_minutos, fecha_registro, estado)
VALUES (9999, 'invalido@test.com', '099000222', '2026-11-15', '10:00:00', 30, NOW(), 'SOLICITADO');
"

echo "=== 3. crear turno valido futuro (us-38) ==="
docker exec -i reservas-db mariadb -u reservas_app -padmin reservas -e "
INSERT INTO reservas_turnos (id_personal, email_solicitante, telefono_solicitante, fecha_turno, hora_turno, duracion_minutos, fecha_registro, estado)
VALUES (1, 'valido1@test.com', '099000333', '2026-11-20', '15:00:00', 30, NOW(), 'SOLICITADO');
"

echo "=== 4. crear segundo turno para el mismo horario y profesional (us-38 ocupado) ==="
docker exec -i reservas-db mariadb -u reservas_app -padmin reservas -e "
INSERT INTO reservas_turnos (id_personal, email_solicitante, telefono_solicitante, fecha_turno, hora_turno, duracion_minutos, fecha_registro, estado)
VALUES (1, 'valido2@test.com', '099000444', '2026-11-20', '15:00:00', 30, NOW(), 'SOLICITADO');
"

echo ""
echo "turnos insertados en estado SOLICITADO. esperando a que turno-validador ejecute su ciclo..."
echo "puedes ver los logs en tiempo real con: docker logs -f turno-validador"
echo ""
echo "consultando estado actual en la base de datos:"
docker exec -i reservas-db mariadb -u reservas_app -padmin reservas -e "
SELECT id, id_personal, email_solicitante, fecha_turno, hora_turno, estado 
FROM reservas_turnos 
ORDER BY id DESC 
LIMIT 10;
"
