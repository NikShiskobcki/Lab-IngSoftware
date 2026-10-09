#!/bin/bash
# pruebas de validacion de reservas

set -e
BASE_URL="http://localhost:8080"
FECHA=$(date -u -d "+2 days" +%F)
FECHA_PASADA=$(date -u -d "yesterday" +%F)
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

  for INTENTO in {1..24}; do
    ESTADO=$(curl -sS --connect-timeout 5 --max-time 20 \
      "$BASE_URL/reservas/$ID" | jq -er '.estado')

    if [ "$ESTADO" != "SOLICITADO" ]; then
      peticion "$BASE_URL/reservas/$ID"
      if [ "$ESTADO" != "$ESPERADO" ]; then
        echo "se esperaba: $ESPERADO"
        exit 1
      fi
      return
    fi
    sleep 5
  done

  echo "la reserva $ID sigue pendiente de validacion"
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
    "nombreComercial": "TaPronto Pruebas",
    "direccion": "Calle Pruebas 123",
    "telefono": "29001234",
    "correoElectronico": "pruebas@tapronto.com",
    "horarioApertura": "08:00",
    "horarioCierre": "18:00"
  }'
[ "$HTTP" = "201" ] || exit 1
ID_ESTABLECIMIENTO=$(jq -er '.id' "$ARCHIVO")

echo "crear otro establecimiento"
peticion -X POST "$BASE_URL/establecimientos" \
  -H "Content-Type: application/json" \
  -d '{
    "nombreComercial": "TaPronto Otro",
    "direccion": "Otra Calle 456",
    "telefono": "29005678",
    "correoElectronico": "otro@tapronto.com",
    "horarioApertura": "08:00",
    "horarioCierre": "18:00"
  }'
[ "$HTTP" = "201" ] || exit 1
ID_OTRO=$(jq -er '.id' "$ARCHIVO")

echo "crear personal"
peticion -X POST "$BASE_URL/personal" \
  -H "Content-Type: application/json" \
  -d "{
    \"idEstablecimiento\": $ID_ESTABLECIMIENTO,
    \"nombre\": \"Maria Pruebas\",
    \"especialidad\": \"Consulta\",
    \"costoConsulta\": 1500,
    \"duracionEstandarMinutos\": 30,
    \"estado\": \"ACTIVO\"
  }"
[ "$HTTP" = "201" ] || exit 1
ID_PERSONAL=$(jq -er '.id' "$ARCHIVO")

echo "crear reserva valida"
reservar "$ID_ESTABLECIMIENTO" "valida@correo.com" "$FECHA" "10:00"
ID_VALIDA=$(jq -er '.datos.id' "$ARCHIVO")
echo "esperar estado Agendado"
esperar "$ID_VALIDA" "Agendado"

echo "probar establecimiento inexistente"
reservar 2147483647 "inexistente@correo.com" "$FECHA" "11:00"
ID_INEXISTENTE=$(jq -er '.datos.id' "$ARCHIVO")

echo "probar personal de otro establecimiento"
reservar "$ID_OTRO" "otro@correo.com" "$FECHA" "11:30"
ID_OTRO_TURNO=$(jq -er '.datos.id' "$ARCHIVO")

echo "probar fecha pasada"
reservar "$ID_ESTABLECIMIENTO" "pasada@correo.com" "$FECHA_PASADA" "12:00"
ID_PASADA=$(jq -er '.datos.id' "$ARCHIVO")

echo "probar horario ocupado"
reservar "$ID_ESTABLECIMIENTO" "ocupado@correo.com" "$FECHA" "10:00"
ID_OCUPADA=$(jq -er '.datos.id' "$ARCHIVO")

echo "probar datos invalidos"
peticion -X POST "$BASE_URL/reservas" \
  -H "Content-Type: application/json" \
  -d "{
    \"idPersonal\": $ID_PERSONAL,
    \"idEstablecimiento\": $ID_ESTABLECIMIENTO,
    \"emailCliente\": \"correo-invalido\",
    \"telefonoCliente\": \"\",
    \"fecha\": \"$FECHA\",
    \"hora\": \"13:00\"
  }"
[ "$HTTP" = "400" ] || exit 1

echo "verificar establecimiento inexistente"
esperar "$ID_INEXISTENTE" "Rechazado/Solicitud No Valida"

echo "verificar personal de otro establecimiento"
esperar "$ID_OTRO_TURNO" "Rechazado/Solicitud No Valida"

echo "verificar fecha pasada"
esperar "$ID_PASADA" "Rechazado/Solicitud No Valida"

echo "verificar horario ocupado"
esperar "$ID_OCUPADA" "Rechazado/Turno Ocupado"

echo "ver datos persistidos"
docker exec -i reservas-db mariadb -u reservas_app -padmin reservas -e "
SELECT id, fecha_turno, hora_turno, estado
FROM reservas_turnos
WHERE id_personal = $ID_PERSONAL
ORDER BY id\\G
"

echo "pruebas terminadas; los datos quedan guardados"
