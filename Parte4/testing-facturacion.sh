#!/bin/bash
# prueba del servicio de facturacion (us-40, us-41, us-42)
#
# inserta turnos en estado "Atendido" directamente en la base (la api no permite crear turnos en el
# pasado) y espera a que el servicio de facturacion, que corre cada 5 minutos, los facture.
#
# uso:   bash testing-facturacion.sh
# vars:  MAX_WAIT   segundos maximos de espera (por defecto 420)
#        DB_CMD     comando para ejecutar sql (por defecto docker exec en reservas-db)

DB_CMD=${DB_CMD:-"docker exec -i reservas-db mariadb -u reservas_app -padmin reservas"}
MAX_WAIT=${MAX_WAIT:-420}

sql() { $DB_CMD -N -B -e "$1"; }
FALLOS=0

check() {
  # check "descripcion" esperado obtenido
  if [ "$2" == "$3" ]; then
    echo "  [OK]    $1 (= $3)"
  else
    echo "  [FALLA] $1: esperado '$2', obtenido '$3'"
    FALLOS=$((FALLOS+1))
  fi
}

EMAIL="Cliente.Fact.$(date +%s)@Test.com"     # con mayusculas: el cliente se identifica por email normalizado
EMAIL_N=$(echo "$EMAIL" | tr 'A-Z' 'a-z')
DIA=$((RANDOM % 25 + 1))
HORA=$((RANDOM % 9 + 8))

echo "=== 1. insertar turnos de prueba para $EMAIL_N ==="
# dos turnos atendidos en septiembre 2026 (personal 1 cuesta 1500 y personal 2 cuesta 800)
# y uno en agosto 2026 (personal 1) -> deben generar dos facturas distintas
sql "INSERT INTO reservas_turnos (id_personal, email_solicitante, telefono_solicitante, fecha_turno, hora_turno, duracion_minutos, fecha_registro, estado) VALUES
 (1, '$EMAIL', '099111222', '2026-09-$(printf '%02d' $DIA)', '$(printf '%02d' $HORA):00:00', 30, NOW(), 'Atendido'),
 (2, '$EMAIL', '099111222', '2026-09-$(printf '%02d' $DIA)', '$(printf '%02d' $HORA):30:00', 30, NOW(), 'Atendido'),
 (1, '$EMAIL', '099111222', '2026-08-$(printf '%02d' $DIA)', '$(printf '%02d' $HORA):00:00', 30, NOW(), 'Atendido');"
# turnos de control: no deben ser tocados por la facturacion
sql "INSERT INTO reservas_turnos (id_personal, email_solicitante, telefono_solicitante, fecha_turno, hora_turno, duracion_minutos, fecha_registro, estado) VALUES
 (1, '$EMAIL', '099111222', '2027-03-$(printf '%02d' $DIA)', '$(printf '%02d' $HORA):00:00', 30, NOW(), 'Agendado'),
 (1, '$EMAIL', '099111222', '2027-04-$(printf '%02d' $DIA)', '$(printf '%02d' $HORA):00:00', 30, NOW(), 'Rechazado/Turno Ocupado');"

echo ""
echo "=== turnos del cliente antes de facturar ==="
$DB_CMD -e "SELECT id, id_personal, fecha_turno, hora_turno, estado FROM reservas_turnos WHERE email_solicitante = '$EMAIL' ORDER BY id;"

echo "=== 2. esperando a que el servicio de facturacion procese (maximo ${MAX_WAIT}s) ==="
INICIO=$(date +%s)
while true; do
  PENDIENTES=$(sql "SELECT COUNT(*) FROM reservas_turnos WHERE email_solicitante = '$EMAIL' AND estado = 'Atendido';")
  [ "$PENDIENTES" == "0" ] && break
  if [ $(( $(date +%s) - INICIO )) -ge "$MAX_WAIT" ]; then
    echo "  tiempo agotado: quedan $PENDIENTES turnos en estado Atendido. el contenedor 'facturacion' esta corriendo?"
    break
  fi
  echo "  quedan $PENDIENTES turnos Atendido, reintentando en 10s..."
  sleep 10
done

echo ""
echo "=== 3. resultado en la base de datos ==="
echo "--- turnos ---"
$DB_CMD -e "SELECT id, id_personal, fecha_turno, hora_turno, estado FROM reservas_turnos WHERE email_solicitante = '$EMAIL' ORDER BY id;"
echo "--- facturas ---"
$DB_CMD -e "SELECT f.id, c.email, f.anio, f.mes, f.total, f.estado FROM facturas f JOIN clientes c ON c.id = f.id_cliente WHERE c.email = '$EMAIL_N' ORDER BY f.anio, f.mes;"
echo "--- items ---"
$DB_CMD -e "SELECT i.id, i.id_factura, i.id_turno, i.monto, i.descripcion FROM items_factura i JOIN facturas f ON f.id = i.id_factura JOIN clientes c ON c.id = f.id_cliente WHERE c.email = '$EMAIL_N' ORDER BY i.id;"

echo ""
echo "=== 4. verificaciones ==="
check "turnos Facturado"                       3    "$(sql "SELECT COUNT(*) FROM reservas_turnos WHERE email_solicitante = '$EMAIL' AND estado = 'Facturado';")"
check "turnos Atendido restantes"              0    "$(sql "SELECT COUNT(*) FROM reservas_turnos WHERE email_solicitante = '$EMAIL' AND estado = 'Atendido';")"
check "turno Agendado no fue tocado"           1    "$(sql "SELECT COUNT(*) FROM reservas_turnos WHERE email_solicitante = '$EMAIL' AND estado = 'Agendado';")"
check "turno Rechazado no fue tocado"          1    "$(sql "SELECT COUNT(*) FROM reservas_turnos WHERE email_solicitante = '$EMAIL' AND estado = 'Rechazado/Turno Ocupado';")"
check "un unico cliente para el email"         1    "$(sql "SELECT COUNT(*) FROM clientes WHERE email = '$EMAIL_N';")"
check "dos facturas (agosto y septiembre)"     2    "$(sql "SELECT COUNT(*) FROM facturas f JOIN clientes c ON c.id = f.id_cliente WHERE c.email = '$EMAIL_N';")"
check "total factura septiembre (1500 + 800)"  2300.00 "$(sql "SELECT f.total FROM facturas f JOIN clientes c ON c.id = f.id_cliente WHERE c.email = '$EMAIL_N' AND f.anio = 2026 AND f.mes = 9;")"
check "total factura agosto"                   1500.00 "$(sql "SELECT f.total FROM facturas f JOIN clientes c ON c.id = f.id_cliente WHERE c.email = '$EMAIL_N' AND f.anio = 2026 AND f.mes = 8;")"
check "items de factura generados"             3    "$(sql "SELECT COUNT(*) FROM items_factura i JOIN facturas f ON f.id = i.id_factura JOIN clientes c ON c.id = f.id_cliente WHERE c.email = '$EMAIL_N';")"
check "total = suma de items (todas las facturas)" 0 "$(sql "SELECT COUNT(*) FROM facturas f WHERE f.total <> (SELECT COALESCE(SUM(monto),0) FROM items_factura WHERE id_factura = f.id);")"
check "ningun turno tiene mas de un item"      0    "$(sql "SELECT COUNT(*) FROM (SELECT id_turno FROM items_factura GROUP BY id_turno HAVING COUNT(*) > 1) t;")"

echo ""
if [ "$FALLOS" -eq 0 ]; then
  echo "RESULTADO: todas las verificaciones pasaron"
else
  echo "RESULTADO: $FALLOS verificacion(es) fallaron"
  exit 1
fi
