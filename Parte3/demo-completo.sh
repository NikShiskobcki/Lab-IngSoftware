#!/bin/bash

echo "crear establecimiento"

ESTABLECIMIENTO=$(curl -sS -X POST http://localhost:8080/establecimientos \
  -H "Content-Type: application/json" \
  -d '{
    "nombreComercial": "TaPronto Demo",
    "direccion": "Avenida Principal 1234",
    "telefono": "29001234",
    "correoElectronico": "demo@tapronto.com",
    "horarioApertura": "08:00",
    "horarioCierre": "18:00"
  }')

echo "$ESTABLECIMIENTO" | jq
ID_ESTABLECIMIENTO=$(echo "$ESTABLECIMIENTO" | jq -r '.id')

echo
echo "crear personal"

PERSONAL=$(curl -sS -X POST http://localhost:8080/personal \
  -H "Content-Type: application/json" \
  -d "{
    \"idEstablecimiento\": $ID_ESTABLECIMIENTO,
    \"nombre\": \"Maria Demo\",
    \"especialidad\": \"Atencion general\",
    \"costoConsulta\": 1200,
    \"duracionEstandarMinutos\": 30,
    \"estado\": \"ACTIVO\"
  }")

echo "$PERSONAL" | jq
ID_PERSONAL=$(echo "$PERSONAL" | jq -r '.id')

echo
echo "crear reserva"

curl -sS -X POST http://localhost:8080/reservas \
  -H "Content-Type: application/json" \
  -d "{
    \"idPersonal\": $ID_PERSONAL,
    \"emailCliente\": \"cliente.demo@correo.com\",
    \"telefonoCliente\": \"099123456\",
    \"fecha\": \"2026-10-15\",
    \"hora\": \"11:00\"
  }" | jq

echo
echo "esperar procesamiento de la reserva"
sleep 3

echo
echo "consultar reserva mediante la API"
curl -sS "http://localhost:8080/reservas?idPersonal=$ID_PERSONAL" | jq

echo
echo "visualizar datos persistidos en la base de datos"
bash ver-turnos-db.sh

echo
read -p "Presione Enter para cerrar..."