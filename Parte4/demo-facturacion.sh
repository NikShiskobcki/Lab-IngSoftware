#!/bin/bash
# ejemplo completo de la parte 4: reserva por api -> mqtt -> subscriber -> validador (cada 1 min)
# -> turno atendido -> facturacion (cada 5 min) -> datos persistidos en mariadb
#
# requisitos: docker compose up -d --build corriendo, curl y jq instalados
# vars: BASE_URL (por defecto http://localhost:8080), MAX_WAIT_VALIDACION y MAX_WAIT_FACTURACION en segundos

BASE_URL=${BASE_URL:-http://localhost:8080}
DB_CMD=${DB_CMD:-"docker exec -i reservas-db mariadb -u reservas_app -padmin reservas"}
MAX_WAIT_VALIDACION=${MAX_WAIT_VALIDACION:-180}
MAX_WAIT_FACTURACION=${MAX_WAIT_FACTURACION:-420}

# espera hasta que el turno tenga el estado indicado; devuelve 0 si lo alcanzo
esperar_estado() {
  local id=$1 esperado=$2 maximo=$3 inicio estado
  inicio=$(date +%s)
  while true; do
    estado=$(curl -sS "$BASE_URL/reservas/$id" | jq -r '.estado')
    echo "  turno $id en estado: $estado"
    [ "$estado" == "$esperado" ] && return 0
    [ $(( $(date +%s) - inicio )) -ge "$maximo" ] && return 1
    sleep 10
  done
}

echo "=== 1. crear establecimiento (api) ==="
ESTABLECIMIENTO=$(curl -sS -X POST "$BASE_URL/establecimientos" \
  -H "Content-Type: application/json" \
  -d '{
    "nombreComercial": "TaPronto Facturacion Demo",
    "direccion": "Avenida Principal 1234",
    "telefono": "29001234",
    "correoElectronico": "facturacion@tapronto.com",
    "horarioApertura": "08:00",
    "horarioCierre": "18:00"
  }')
echo "$ESTABLECIMIENTO" | jq
ID_ESTABLECIMIENTO=$(echo "$ESTABLECIMIENTO" | jq -r '.id')

echo
echo "=== 2. crear personal con costo de consulta 1200 (api) ==="
PERSONAL=$(curl -sS -X POST "$BASE_URL/personal" \
  -H "Content-Type: application/json" \
  -d "{
    \"idEstablecimiento\": $ID_ESTABLECIMIENTO,
    \"nombre\": \"Maria Facturacion\",
    \"especialidad\": \"Atencion general\",
    \"costoConsulta\": 1200,
    \"duracionEstandarMinutos\": 30,
    \"estado\": \"ACTIVO\"
  }")
echo "$PERSONAL" | jq
ID_PERSONAL=$(echo "$PERSONAL" | jq -r '.id')

EMAIL="demo.facturacion.$(date +%s)@correo.com"
FECHA="2026-12-$(printf '%02d' $((RANDOM % 25 + 1)))"

echo
echo "=== 3. reservar turno para $EMAIL el $FECHA 11:00 (api -> mqtt) ==="
curl -sS -X POST "$BASE_URL/reservas" \
  -H "Content-Type: application/json" \
  -d "{
    \"idPersonal\": $ID_PERSONAL,
    \"emailCliente\": \"$EMAIL\",
    \"telefonoCliente\": \"099123456\",
    \"fecha\": \"$FECHA\",
    \"hora\": \"11:00\"
  }" | jq

echo
echo "=== 4. esperar que el subscriber lo registre ==="
ID_TURNO=""
for i in $(seq 1 12); do
  ID_TURNO=$(curl -sS "$BASE_URL/reservas?idPersonal=$ID_PERSONAL" | jq -r --arg e "$EMAIL" '[.[] | select(.emailSolicitante == $e)][0].id // empty')
  [ -n "$ID_TURNO" ] && break
  sleep 5
done
if [ -z "$ID_TURNO" ]; then
  echo "el turno no aparecio en la api. estan corriendo mosquitto y turno-subscriber?"
  exit 1
fi
echo "turno registrado con id $ID_TURNO"

echo
echo "=== 5. esperar la validacion del turno-validador (corre cada 1 minuto) ==="
if ! esperar_estado "$ID_TURNO" "Agendado" "$MAX_WAIT_VALIDACION"; then
  echo "el turno no llego a Agendado a tiempo"
  exit 1
fi

echo
echo "=== 6. simular que el turno ya fue atendido ==="
# la transicion Agendado -> Atendido ocurre cuando pasa la fecha del turno; para la demo se fuerza en la base
$DB_CMD -e "UPDATE reservas_turnos SET estado = 'Atendido' WHERE id = $ID_TURNO AND estado = 'Agendado';"
curl -sS "$BASE_URL/reservas/$ID_TURNO" | jq

echo
echo "=== 7. esperar la facturacion (corre cada 5 minutos) ==="
if ! esperar_estado "$ID_TURNO" "Facturado" "$MAX_WAIT_FACTURACION"; then
  echo "el turno no llego a Facturado a tiempo. esta corriendo el contenedor 'facturacion'? (docker compose logs facturacion)"
  exit 1
fi

echo
echo "=== 8. datos persistidos en la base de datos ==="
bash ver-facturas-db.sh

echo
echo "=== 9. log del servicio de facturacion ==="
docker compose logs --tail 15 facturacion 2>/dev/null || true
