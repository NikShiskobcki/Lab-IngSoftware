#!/bin/bash
# Ejemplo de las API, validacion y facturacion

set -e

BASE_URL=${BASE_URL:-http://localhost:8080}
ARCHIVO=$(mktemp)
trap 'rm -f "$ARCHIVO"' EXIT

for comando in curl jq docker; do
  if ! command -v "$comando" >/dev/null 2>&1; then
    echo "falta instalar $comando"
    exit 1
  fi
done

# Mostrar la respuesta y comprobar el codigo HTTP
peticion() {
  local esperado=$1
  local metodo=$2
  local ruta=$3
  local codigo

  if [ "$#" -eq 4 ]; then
    codigo=$(curl -sS --connect-timeout 5 --max-time 20 \
      -o "$ARCHIVO" -w '%{http_code}' \
      -X "$metodo" "$BASE_URL$ruta" \
      -H 'Content-Type: application/json' -d "$4")
  else
    codigo=$(curl -sS --connect-timeout 5 --max-time 20 \
      -o "$ARCHIVO" -w '%{http_code}' \
      -X "$metodo" "$BASE_URL$ruta")
  fi

  RESPUESTA=$(cat "$ARCHIVO")
  jq . "$ARCHIVO" || cat "$ARCHIVO"
  echo "HTTP $codigo"
  echo

  if [ "$codigo" != "$esperado" ]; then
    echo "se esperaba HTTP $esperado; se detiene el ejemplo"
    exit 1
  fi
}

# Esperar al servicio sin imprimir cada consulta
esperar_estado() {
  local esperado=$1
  local limite=$2
  local inicio=$SECONDS
  local codigo estado

  echo "esperar estado $esperado"
  while (( SECONDS - inicio < limite )); do
    codigo=$(curl -sS --connect-timeout 5 --max-time 20 \
      -o "$ARCHIVO" -w '%{http_code}' \
      "$BASE_URL/reservas/$ID_RESERVA")

    if [ "$codigo" != 200 ]; then
      cat "$ARCHIVO"
      echo "HTTP $codigo"
      exit 1
    fi

    estado=$(jq -r '.estado' "$ARCHIVO")
    if [ "$estado" = "$esperado" ] ||
       { [ "$esperado" = Atendido ] && [ "$estado" = Facturado ]; }; then
      jq . "$ARCHIVO"
      echo "HTTP $codigo"
      echo
      return
    fi

    if [[ "$estado" == Rechazado* ]]; then
      jq . "$ARCHIVO"
      echo "HTTP $codigo"
      exit 1
    fi
    sleep 5
  done

  jq . "$ARCHIVO"
  echo "HTTP $codigo"
  echo "no llego a $esperado; revisar los logs"
  exit 1
}

echo "crear establecimiento"
peticion 201 POST /establecimientos '{
  "nombreComercial": "TaPronto Demo",
  "direccion": "Calle Demo 123",
  "telefono": "29001234",
  "correoElectronico": "demo@tapronto.com",
  "horarioApertura": "08:00",
  "horarioCierre": "18:00"
}'
ID_ESTABLECIMIENTO=$(echo "$RESPUESTA" | jq -er '.id | select(type == "number" and . > 0)')

echo "consultar establecimiento"
peticion 200 GET "/establecimientos/$ID_ESTABLECIMIENTO"

echo "crear personal"
peticion 201 POST /personal "{
  \"idEstablecimiento\": $ID_ESTABLECIMIENTO,
  \"nombre\": \"Maria Demo\",
  \"especialidad\": \"Consulta\",
  \"costoConsulta\": 1500,
  \"duracionEstandarMinutos\": 30,
  \"estado\": \"ACTIVO\"
}"
ID_PERSONAL=$(echo "$RESPUESTA" | jq -er '.id | select(type == "number" and . > 0)')

echo "consultar personal"
peticion 200 GET "/personal/$ID_PERSONAL"

FECHA=$(date -u -d tomorrow +%F)
EMAIL="demo.$(date +%s).$ID_PERSONAL@correo.com"

echo "crear reserva"
peticion 201 POST /reservas "{
  \"idEstablecimiento\": $ID_ESTABLECIMIENTO,
  \"idPersonal\": $ID_PERSONAL,
  \"emailCliente\": \"$EMAIL\",
  \"telefonoCliente\": \"099123456\",
  \"fecha\": \"$FECHA\",
  \"hora\": \"10:00\"
}"
ID_RESERVA=$(echo "$RESPUESTA" | jq -er '.datos.id | select(type == "number" and . > 0)')

esperar_estado Agendado 150

echo "consultar agenda"
peticion 200 GET "/personal/$ID_PERSONAL/agenda?fecha=$FECHA"

echo "simular que paso la fecha del turno de prueba"
docker exec -i reservas-db mariadb -u reservas_app -padmin reservas -e "
UPDATE reservas_turnos
SET fecha_turno = DATE_SUB(UTC_DATE(), INTERVAL 1 DAY)
WHERE id = $ID_RESERVA AND estado = 'Agendado';
"
echo

esperar_estado Atendido 150
esperar_estado Facturado 420

echo "ver reserva persistida"
docker exec -i reservas-db mariadb -u reservas_app -padmin \
  --vertical reservas -e "
SELECT r.id, p.nombre AS personal, r.fecha_turno,
       r.hora_turno, r.estado
FROM reservas_turnos r
JOIN personal p ON p.id = r.id_personal
WHERE r.id = $ID_RESERVA;
"

echo "ver cliente, factura e item persistidos"
docker exec -i reservas-db mariadb -u reservas_app -padmin \
  --vertical reservas -e "
SELECT c.email, f.id AS factura, f.anio, f.mes, f.total,
       i.id AS item, i.id_turno, i.monto
FROM items_factura i
JOIN facturas f ON f.id = i.id_factura
JOIN clientes c ON c.id = f.id_cliente
WHERE i.id_turno = $ID_RESERVA;
"

echo "ejemplo terminado; los datos quedan guardados"
