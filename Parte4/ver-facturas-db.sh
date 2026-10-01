#!/bin/bash
# script para consultar clientes, facturas e items de factura en mariadb

DB_CMD=${DB_CMD:-"docker exec -i reservas-db mariadb -u reservas_app -padmin reservas"}

echo "=== clientes ==="
$DB_CMD -e "SELECT id, email, telefono, fecha_alta FROM clientes ORDER BY id;"

echo "=== facturas (una por cliente y mes) ==="
$DB_CMD -e "
SELECT f.id, c.email, f.anio, f.mes, f.total, f.estado,
       (SELECT COUNT(*) FROM items_factura i WHERE i.id_factura = f.id) AS items
FROM facturas f
JOIN clientes c ON c.id = f.id_cliente
ORDER BY c.email, f.anio, f.mes;"

echo "=== items de factura ==="
$DB_CMD -e "
SELECT i.id, i.id_factura, c.email, i.id_turno, r.estado AS estado_turno, i.monto, i.descripcion
FROM items_factura i
JOIN facturas f ON f.id = i.id_factura
JOIN clientes c ON c.id = f.id_cliente
JOIN reservas_turnos r ON r.id = i.id_turno
ORDER BY i.id;"

echo "=== turnos por estado ==="
$DB_CMD -e "SELECT estado, COUNT(*) AS cantidad FROM reservas_turnos GROUP BY estado ORDER BY estado;"
