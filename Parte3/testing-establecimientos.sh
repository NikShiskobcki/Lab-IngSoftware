#!/bin/bash

set -e

BASE_URL="${BASE_URL:-http://localhost:8080}"

if ! command -v jq > /dev/null 2>&1; then
  echo "falta instalar jq"
  exit 1
fi

echo "crear establecimiento"

RESPUESTA=$(curl -sS -X POST "$BASE_URL/establecimientos" \
  -H "Content-Type: application/json" \
  -d '{
    "nombreComercial": "TaPronto Centro",
    "direccion": "Avenida 18 de Julio 1234",
    "telefono": "29001234",
    "correoElectronico": "centro@tapronto.com",
    "horarioApertura": "08:00:00",
    "horarioCierre": "18:00:00"
  }')

echo "$RESPUESTA" | jq

ID=$(echo "$RESPUESTA" | jq -r '.id // empty')

if [ -z "$ID" ]; then
  echo "no se pudo obtener el id"
  exit 1
fi

echo
echo "listar establecimientos"
curl -sS "$BASE_URL/establecimientos" | jq

echo
echo "consultar establecimiento"
curl -sS "$BASE_URL/establecimientos/$ID" | jq

echo
echo "actualizar establecimiento"
curl -sS -X PUT "$BASE_URL/establecimientos/$ID" \
  -H "Content-Type: application/json" \
  -d '{
    "nombreComercial": "TaPronto Centro Actualizado",
    "direccion": "Avenida 18 de Julio 1500",
    "telefono": "29005678",
    "correoElectronico": "centro.actualizado@tapronto.com",
    "horarioApertura": "09:00:00",
    "horarioCierre": "19:00:00"
  }' | jq

echo
echo "probar datos invalidos"
curl -sS -w "\nHTTP %{http_code}\n" \
  -X POST "$BASE_URL/establecimientos" \
  -H "Content-Type: application/json" \
  -d '{
    "nombreComercial": "",
    "direccion": "",
    "telefono": "",
    "correoElectronico": "correo-invalido",
    "horarioApertura": "18:00:00",
    "horarioCierre": "08:00:00"
  }'

echo
echo "consultar id inexistente"
curl -sS -w "\nHTTP %{http_code}\n" \
  "$BASE_URL/establecimientos/999999"

echo
echo "eliminar establecimiento"
curl -sS -o /dev/null -w "HTTP %{http_code}\n" \
  -X DELETE "$BASE_URL/establecimientos/$ID"

echo
echo "verificar eliminacion"
curl -sS -w "\nHTTP %{http_code}\n" \
  "$BASE_URL/establecimientos/$ID"