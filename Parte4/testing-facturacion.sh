#!/bin/bash
# pruebas de agrupacion de facturas

set -e
BASE_URL="http://localhost:8080"
FECHA=$(date -u -d "+2 days" +%F)
EMAIL="facturacion.$(date +%s).$$@correo.com"
ARCHIVO=$(mktemp)
trap 'rm -f "$ARCHIVO"' EXIT

# mostrar la respuesta y el codigo HTTP
peticion() {
  HTTP=$(curl -sS --connect-timeout 5 --max-time 20 \
    -o "$ARCHIVO" -w '%{http_code}' "$@")
  jq . "$ARCHIVO"
  echo "HTTP $HTTP"
  echo
}

# esperar la revision que se ejecuta cada minuto
esperar() {
  ID=$1
  ESPERADO=$2

  for INTENTO in {1..84}; do
    ESTADO=$(curl -sS --connect-timeout 5 --max-time 20 \
      "$BASE_URL/reservas/$ID" | jq -er '.estado')

    if [ "$ESTADO" = "$ESPERADO" ]; then
      peticion "$BASE_URL/reservas/$ID"
      return
    fi
    sleep 5
  done

  echo "la reserva $ID no llego al estado $ESPERADO"
  exit 1
}

# enviar una solicitud con los datos de la prueba
reservar() {
  peticion -X POST "$BASE_URL/reservas" \
    -H "Content-Type: application/json" \
    -d "{
      \"idPersonal\": $ID_PERSONAL,
      \"idEstablecimiento\": $1,
      \"emailCliente\": \"$2\",
      \"telefonoCliente\": \"099123456\",
      \"fecha\": \"$3\",
      \"hora\": \"$4\"
    }"
  [ "$HTTP" = "201" ] || exit 1
}

echo "crear establecimiento"
peticion -X POST "$BASE_URL/establecimientos" \
  -H "Content-Type: application/json" \
  -d '{
    "nombreComercial": "TaPronto Facturacion",
    "direccion": "Calle Pruebas 123",
    "telefono": "29001234",
    "correoElectronico": "pruebas@tapronto.com",
    "horarioApertura": "08:00",
    "horarioCierre": "18:00"
  }'
[ "$HTTP" = "201" ] || exit 1
ID_ESTABLECIMIENTO=$(jq -er '.id' "$ARCHIVO")

echo "crear personal"
peticion -X POST "$BASE_URL/personal" \
  -H "Content-Type: application/json" \
  -d "{
    \"idEstablecimiento\": $ID_ESTABLECIMIENTO,
    \"nombre\": \"Maria Facturacion\",
    \"especialidad\": \"Consulta\",
    \"costoConsulta\": 1500,
    \"duracionEstandarMinutos\": 30,
    \"estado\": \"ACTIVO\"
  }"
[ "$HTTP" = "201" ] || exit 1
ID_PERSONAL=$(jq -er '.id' "$ARCHIVO")

echo "crear primer turno del cliente"
reservar "$ID_ESTABLECIMIENTO" "$EMAIL" "$FECHA" "10:00"
ID_PRIMERO=$(jq -er '.datos.id' "$ARCHIVO")

echo "crear segundo turno del mismo cliente"
reservar "$ID_ESTABLECIMIENTO" "$EMAIL" "$FECHA" "11:00"
ID_SEGUNDO=$(jq -er '.datos.id' "$ARCHIVO")

echo "crear tercer turno del mismo cliente"
reservar "$ID_ESTABLECIMIENTO" "$EMAIL" "$FECHA" "12:00"
ID_TERCERO=$(jq -er '.datos.id' "$ARCHIVO")

echo "esperar que se agenden los tres turnos"
esperar "$ID_PRIMERO" "Agendado"
esperar "$ID_SEGUNDO" "Agendado"
esperar "$ID_TERCERO" "Agendado"

echo "simular dos turnos del mismo mes y otro del mes anterior"
# Cambiar solo la fecha para simular que los turnos ya sucedieron.
docker exec -i reservas-db mariadb -u reservas_app -padmin reservas -e "
SET @fecha = DATE_SUB(UTC_DATE(), INTERVAL 1 DAY);
UPDATE reservas_turnos
SET fecha_turno = @fecha
WHERE id IN ($ID_PRIMERO, $ID_SEGUNDO) AND estado = 'Agendado';
UPDATE reservas_turnos
SET fecha_turno = DATE_SUB(DATE_FORMAT(@fecha, '%Y-%m-01'), INTERVAL 1 MONTH)
WHERE id = $ID_TERCERO AND estado = 'Agendado';
"

echo "esperar validacion y facturacion; puede demorar hasta seis minutos"
esperar "$ID_PRIMERO" "Facturado"
esperar "$ID_SEGUNDO" "Facturado"
esperar "$ID_TERCERO" "Facturado"

echo "ver facturas e items persistidos"
docker exec -i reservas-db mariadb -u reservas_app -padmin reservas -e "
SELECT c.email, f.id AS factura, f.anio, f.mes, f.total,
       COUNT(i.id) AS cantidad_items, SUM(i.monto) AS suma_items
FROM clientes c
JOIN facturas f ON f.id_cliente = c.id
JOIN items_factura i ON i.id_factura = f.id
WHERE c.email = '$EMAIL'
GROUP BY c.email, f.id, f.anio, f.mes, f.total
ORDER BY f.anio DESC, f.mes DESC\\G
SELECT id_turno, id_factura, monto
FROM items_factura
WHERE id_turno IN ($ID_PRIMERO, $ID_SEGUNDO, $ID_TERCERO)
ORDER BY id_turno\\G
"

echo "verificar agrupacion y total del mismo mes"
RESULTADO=$(docker exec reservas-db mariadb -u reservas_app -padmin reservas -N -B -e "
SELECT CONCAT(COUNT(DISTINCT f.id), '|', COUNT(i.id), '|', MAX(f.total))
FROM facturas f
JOIN items_factura i ON i.id_factura = f.id
WHERE i.id_turno IN ($ID_PRIMERO, $ID_SEGUNDO);
")
if [ "$RESULTADO" != "1|2|3000.00" ]; then
  echo "no coincide: $RESULTADO; se esperaba 1|2|3000.00"
  exit 1
fi
echo "correcto: una factura, dos items y total 3000"

echo "verificar que el otro mes tenga su propia factura"
RESULTADO=$(docker exec reservas-db mariadb -u reservas_app -padmin reservas -N -B -e "
SELECT CONCAT(COUNT(DISTINCT f.id), '|', COUNT(DISTINCT c.id), '|', COUNT(i.id), '|', SUM(i.monto))
FROM clientes c
JOIN facturas f ON f.id_cliente = c.id
JOIN items_factura i ON i.id_factura = f.id
WHERE c.email = '$EMAIL';
")
if [ "$RESULTADO" != "2|1|3|4500.00" ]; then
  echo "no coincide: $RESULTADO; se esperaba 2|1|3|4500.00"
  exit 1
fi
echo "correcto: dos facturas, un cliente y tres items por un total de 4500"
echo "pruebas terminadas; los datos quedan guardados"
