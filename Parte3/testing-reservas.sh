#!/bin/bash

set -e

BASE_URL="${BASE_URL:-http://localhost:8080}"
FECHA="${FECHA:-$(date -d "+7 days" +%F)}"
EMAIL="reserva.$(date +%s)@correo.com"
EMAIL_INACTIVO="inactivo.$(date +%s)@correo.com"
EMAIL_FUERA="fuera.$(date +%s)@correo.com"

if ! command -v jq > /dev/null 2>&1; then
  echo "falta instalar jq"
  exit 1
fi

echo "crear establecimiento"

RESPUESTA_ESTABLECIMIENTO=$(curl -sS -X POST "$BASE_URL/establecimientos" \
  -H "Content-Type: application/json" \
  -d '{
    "nombreComercial": "TaPronto Reservas",
    "direccion": "Calle Reservas 123",
    "telefono": "29003333",
    "correoElectronico": "reservas@tapronto.com",
    "horarioApertura": "08:00:00",
    "horarioCierre": "18:00:00"
  }')

ID_ESTABLECIMIENTO=$(echo "$RESPUESTA_ESTABLECIMIENTO" | jq -r '.id // empty')

if [ -z "$ID_ESTABLECIMIENTO" ]; then
  echo "no se pudo crear el establecimiento"
  exit 1
fi

echo "crear personal activo"

RESPUESTA_PERSONAL=$(curl -sS -X POST "$BASE_URL/personal" \
  -H "Content-Type: application/json" \
  -d "{
    \"idEstablecimiento\": $ID_ESTABLECIMIENTO,
    \"nombre\": \"Personal de reservas\",
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

echo "crear personal inactivo"

RESPUESTA_INACTIVO=$(curl -sS -X POST "$BASE_URL/personal" \
  -H "Content-Type: application/json" \
  -d "{
    \"idEstablecimiento\": $ID_ESTABLECIMIENTO,
    \"nombre\": \"Personal inactivo\",
    \"especialidad\": \"Odontologia\",
    \"costoConsulta\": 1800,
    \"duracionEstandarMinutos\": 30,
    \"estado\": \"INACTIVO\"
  }")

ID_INACTIVO=$(echo "$RESPUESTA_INACTIVO" | jq -r '.id // empty')

echo
echo "crear reserva"
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

if [ -z "$ID_RESERVA" ]; then
  echo "la reserva no fue persistida"
  exit 1
fi

echo
echo "listar reservas"
curl -sS "$BASE_URL/reservas" | jq

echo
echo "consultar reserva"
curl -sS "$BASE_URL/reservas/$ID_RESERVA" | jq

echo
echo "filtrar reservas por personal"
curl -sS "$BASE_URL/reservas?idPersonal=$ID_PERSONAL" | jq

echo
echo "probar datos invalidos"
curl -sS -w "\nHTTP %{http_code}\n" \
  -X POST "$BASE_URL/reservas" \
  -H "Content-Type: application/json" \
  -d "{
    \"idPersonal\": $ID_PERSONAL,
    \"emailCliente\": \"correo-invalido\",
    \"telefonoCliente\": \"\",
    \"fecha\": \"$FECHA\",
    \"hora\": \"10:00\"
  }"

echo
echo "enviar reserva para personal inactivo"
curl -sS -X POST "$BASE_URL/reservas" \
  -H "Content-Type: application/json" \
  -d "{
    \"idPersonal\": $ID_INACTIVO,
    \"emailCliente\": \"$EMAIL_INACTIVO\",
    \"telefonoCliente\": \"099123456\",
    \"fecha\": \"$FECHA\",
    \"hora\": \"10:00\"
  }" | jq

sleep 2

echo
echo "verificar rechazo de personal inactivo"
curl -sS "$BASE_URL/reservas?idPersonal=$ID_INACTIVO" |
  jq --arg EMAIL "$EMAIL_INACTIVO" \
    'map(select(.emailSolicitante == $EMAIL))'

echo
echo "enviar reserva fuera de horario"
curl -sS -X POST "$BASE_URL/reservas" \
  -H "Content-Type: application/json" \
  -d "{
    \"idPersonal\": $ID_PERSONAL,
    \"emailCliente\": \"$EMAIL_FUERA\",
    \"telefonoCliente\": \"099123456\",
    \"fecha\": \"$FECHA\",
    \"hora\": \"03:00\"
  }" | jq

sleep 2

echo
echo "verificar rechazo por horario"
curl -sS "$BASE_URL/reservas?idPersonal=$ID_PERSONAL" |
  jq --arg EMAIL "$EMAIL_FUERA" \
    'map(select(.emailSolicitante == $EMAIL))'

echo
echo "consultar id inexistente"
curl -sS -w "\nHTTP %{http_code}\n" \
  "$BASE_URL/reservas/999999"

echo
echo "eliminar reserva"
curl -sS -o /dev/null -w "HTTP %{http_code}\n" \
  -X DELETE "$BASE_URL/reservas/$ID_RESERVA"

echo
echo "verificar eliminacion"
curl -sS -w "\nHTTP %{http_code}\n" \
  "$BASE_URL/reservas/$ID_RESERVA"

echo
echo "eliminar datos de prueba"
curl -sS -X DELETE "$BASE_URL/personal/$ID_PERSONAL" > /dev/null
curl -sS -X DELETE "$BASE_URL/personal/$ID_INACTIVO" > /dev/null
curl -sS -X DELETE "$BASE_URL/establecimientos/$ID_ESTABLECIMIENTO" > /dev/null

echo "datos eliminados"