#!/bin/bash

set -e

BASE_URL="${BASE_URL:-http://localhost:8080}"
FECHA="${FECHA:-$(date -d "+7 days" +%F)}"
EMAIL="agenda.$(date +%s)@correo.com"

if ! command -v jq > /dev/null 2>&1; then
  echo "falta instalar jq"
  exit 1
fi

echo "crear establecimiento"

RESPUESTA_ESTABLECIMIENTO=$(curl -sS -X POST "$BASE_URL/establecimientos" \
  -H "Content-Type: application/json" \
  -d '{
    "nombreComercial": "TaPronto Personal",
    "direccion": "Calle Personal 123",
    "telefono": "29001111",
    "correoElectronico": "personal@tapronto.com",
    "horarioApertura": "08:00:00",
    "horarioCierre": "18:00:00"
  }')

ID_ESTABLECIMIENTO=$(echo "$RESPUESTA_ESTABLECIMIENTO" | jq -r '.id // empty')

if [ -z "$ID_ESTABLECIMIENTO" ]; then
  echo "no se pudo crear el establecimiento"
  exit 1
fi

echo "$RESPUESTA_ESTABLECIMIENTO" | jq

echo
echo "crear personal"

RESPUESTA_PERSONAL=$(curl -sS -X POST "$BASE_URL/personal" \
  -H "Content-Type: application/json" \
  -d "{
    \"idEstablecimiento\": $ID_ESTABLECIMIENTO,
    \"nombre\": \"Ana Rodriguez\",
    \"especialidad\": \"Medicina general\",
    \"costoConsulta\": 1500,
    \"duracionEstandarMinutos\": 30,
    \"estado\": \"ACTIVO\"
  }")

ID_PERSONAL=$(echo "$RESPUESTA_PERSONAL" | jq -r '.id // empty')

if [ -z "$ID_PERSONAL" ]; then
  echo "no se pudo crear el personal"
  exit 1
fi

echo "$RESPUESTA_PERSONAL" | jq

echo
echo "listar personal"
curl -sS "$BASE_URL/personal" | jq

echo
echo "consultar personal"
curl -sS "$BASE_URL/personal/$ID_PERSONAL" | jq

echo
echo "consultar agenda vacia"
curl -sS "$BASE_URL/personal/$ID_PERSONAL/agenda?fecha=$FECHA" | jq

echo
echo "consultar disponibilidad"
curl -sS "$BASE_URL/personal/$ID_PERSONAL/disponibilidad?fecha=$FECHA" | jq

echo
echo "crear reserva para las 10:00"
curl -sS -X POST "$BASE_URL/reservas" \
  -H "Content-Type: application/json" \
  -d "{
    \"idPersonal\": $ID_PERSONAL,
    \"emailCliente\": \"$EMAIL\",
    \"telefonoCliente\": \"099123456\",
    \"fecha\": \"$FECHA\",
    \"hora\": \"10:00\"
  }" | jq

ID_RESERVA=""

for INTENTO in $(seq 1 10); do
  ID_RESERVA=$(curl -sS "$BASE_URL/reservas?idPersonal=$ID_PERSONAL" |
    jq -r --arg EMAIL "$EMAIL" \
      'map(select(.emailSolicitante == $EMAIL)) | .[0].id // empty')

  if [ -n "$ID_RESERVA" ]; then
    break
  fi

  sleep 1
done

echo
echo "consultar agenda con reserva"
curl -sS "$BASE_URL/personal/$ID_PERSONAL/agenda?fecha=$FECHA" | jq

echo
echo "consultar disponibilidad con reserva"
curl -sS "$BASE_URL/personal/$ID_PERSONAL/disponibilidad?fecha=$FECHA" | jq

echo
echo "actualizar personal"
curl -sS -X PUT "$BASE_URL/personal/$ID_PERSONAL" \
  -H "Content-Type: application/json" \
  -d "{
    \"idEstablecimiento\": $ID_ESTABLECIMIENTO,
    \"nombre\": \"Ana Rodriguez\",
    \"especialidad\": \"Medicina familiar\",
    \"costoConsulta\": 1800,
    \"duracionEstandarMinutos\": 30,
    \"estado\": \"INACTIVO\"
  }" | jq

echo
echo "consultar disponibilidad inactiva"
curl -sS "$BASE_URL/personal/$ID_PERSONAL/disponibilidad?fecha=$FECHA" | jq

echo
echo "probar datos invalidos"
curl -sS -w "\nHTTP %{http_code}\n" \
  -X POST "$BASE_URL/personal" \
  -H "Content-Type: application/json" \
  -d "{
    \"idEstablecimiento\": $ID_ESTABLECIMIENTO,
    \"nombre\": \"\",
    \"especialidad\": \"\",
    \"costoConsulta\": 0,
    \"duracionEstandarMinutos\": 0,
    \"estado\": \"ACTIVO\"
  }"

echo
echo "probar establecimiento inexistente"
curl -sS -w "\nHTTP %{http_code}\n" \
  -X POST "$BASE_URL/personal" \
  -H "Content-Type: application/json" \
  -d '{
    "idEstablecimiento": 999999,
    "nombre": "Personal de prueba",
    "especialidad": "Medicina general",
    "costoConsulta": 1500,
    "duracionEstandarMinutos": 30,
    "estado": "ACTIVO"
  }'

echo
echo "consultar id inexistente"
curl -sS -w "\nHTTP %{http_code}\n" \
  "$BASE_URL/personal/999999"

echo
echo "eliminar datos de prueba"

if [ -n "$ID_RESERVA" ]; then
  curl -sS -X DELETE "$BASE_URL/reservas/$ID_RESERVA" > /dev/null
fi

curl -sS -X DELETE "$BASE_URL/personal/$ID_PERSONAL" > /dev/null
curl -sS -X DELETE "$BASE_URL/establecimientos/$ID_ESTABLECIMIENTO" > /dev/null

echo "datos eliminados"