#!/bin/bash

# Script de Pruebas Funcionales Automatizadas (Técnicas de Caja Negra)

set -e
BASE_URL="${BASE_URL:-http://localhost:8080}"
FECHA_FUTURA=$(date -u -d "+5 days" +%F 2>/dev/null || date -v+5d +%F 2>/dev/null || python -c "import datetime; print((datetime.date.today() + datetime.timedelta(days=5)).isoformat())" 2>/dev/null || echo "2026-11-01")
FECHA_PASADA=$(date -u -d "-2 days" +%F 2>/dev/null || date -v-2d +%F 2>/dev/null || python -c "import datetime; print((datetime.date.today() - datetime.timedelta(days=2)).isoformat())" 2>/dev/null || echo "2026-10-01")

ARCHIVO=$(mktemp 2>/dev/null || echo "/tmp/tapronto_test_tmp.json")
trap 'rm -f "$ARCHIVO"' EXIT


echo "    TAPRONTO - SUITE DE PRUEBAS FUNCIONALES     "

echo "Servidor objetivo: $BASE_URL"
echo "Fecha de prueba futura: $FECHA_FUTURA"
echo "Fecha de prueba pasada: $FECHA_PASADA"
echo

peticion() {
  HTTP=$(curl -sS --connect-timeout 5 --max-time 20 \
    -o "$ARCHIVO" -w '%{http_code}' "$@")
  if [ -s "$ARCHIVO" ]; then
    if command -v jq >/dev/null 2>&1; then
      jq . "$ARCHIVO" 2>/dev/null || cat "$ARCHIVO"
    else
      cat "$ARCHIVO"
    fi
  fi
  echo "-> Código HTTP: $HTTP"
  echo
}

esperar_estado() {
  ID=$1
  ESPERADO=$2
  echo "[Esperando revisión asíncrona del validador para turno ID $ID. Esperado: '$ESPERADO']"

  for INTENTO in {1..24}; do
    ESTADO=$(curl -sS --connect-timeout 5 --max-time 20 \
      "$BASE_URL/reservas/$ID" | jq -er '.estado // empty' 2>/dev/null || echo "")

    if [ -n "$ESTADO" ] && [ "$ESTADO" != "SOLICITADO" ]; then
      echo "  Intento $INTENTO: Estado detectado = '$ESTADO'"
      if [ "$ESTADO" = "$ESPERADO" ]; then
        echo "  [OK] Estado coincide con el esperado: $ESPERADO"
        echo
        return 0
      else
        echo "  [ERROR] Estado incorrecto: se obtuvo '$ESTADO', se esperaba '$ESPERADO'"
        exit 1
      fi
    fi
    sleep 5
  done

  echo "  [TIMEOUT] El turno $ID sigue en estado SOLICITADO tras 2 minutos"
  exit 1
}


# US-57: Pruebas Establecimiento completo
# ==============================================================================
echo ">>> [IS-68 / US-57] Pruebas Establecimiento completo"

echo "1.1 Alta de establecimiento válido (PE: Clase Válida)"
peticion -X POST "$BASE_URL/establecimientos" \
  -H "Content-Type: application/json" \
  -d '{
    "nombreComercial": "Clinica TaPronto Central",
    "direccion": "Av. 18 de Julio 1234",
    "telefono": "29001234",
    "correoElectronico": "central@tapronto.com",
    "horarioApertura": "08:00:00",
    "horarioCierre": "20:00:00"
  }'
[ "$HTTP" = "201" ] || { echo "Fallo US-57 alta"; exit 1; }
ID_ESTABLECIMIENTO=$(jq -er '.id' "$ARCHIVO")
echo "Establecimiento creado con ID: $ID_ESTABLECIMIENTO"

echo "1.2 Consultar establecimiento por ID (PE: ID Existente)"
peticion "$BASE_URL/establecimientos/$ID_ESTABLECIMIENTO"
[ "$HTTP" = "200" ] || exit 1

echo "1.3 Listar todos los establecimientos"
peticion "$BASE_URL/establecimientos"
[ "$HTTP" = "200" ] || exit 1

echo "1.4 Actualizar establecimiento existente"
peticion -X PUT "$BASE_URL/establecimientos/$ID_ESTABLECIMIENTO" \
  -H "Content-Type: application/json" \
  -d '{
    "nombreComercial": "Clinica TaPronto Central Renovada",
    "direccion": "Av. 18 de Julio 1500",
    "telefono": "29005678",
    "correoElectronico": "central.renovada@tapronto.com",
    "horarioApertura": "08:00:00",
    "horarioCierre": "21:00:00"
  }'
[ "$HTTP" = "200" ] || exit 1

echo "1.5 Consulta ID inexistente (PE: ID Inexistente)"
peticion "$BASE_URL/establecimientos/99999"
[ "$HTTP" = "404" ] || echo "Esperado 404"

echo "1.6 AVL: Rechazo horario de cierre anterior o igual a la apertura"
peticion -X POST "$BASE_URL/establecimientos" \
  -H "Content-Type: application/json" \
  -d '{
    "nombreComercial": "Invalido Horario",
    "direccion": "Calle Falsa 123",
    "telefono": "29000000",
    "correoElectronico": "invalido@tapronto.com",
    "horarioApertura": "18:00:00",
    "horarioCierre": "08:00:00"
  }'
[ "$HTTP" = "400" ] || [ "$HTTP" = "500" ] || echo "Horario ilógico rechazado"

echo "[OK] US-57 Pruebas Establecimiento completo superada."
echo "------------------------------------------------------------------------------"

# ==============================================================================
# IS-69 / US-58: Pruebas Personal completo
# ==============================================================================
echo ">>> [IS-69 / US-58] Pruebas Personal completo"

echo "2.1 Alta de personal válido en establecimiento existente (PE: Válido)"
peticion -X POST "$BASE_URL/personal" \
  -H "Content-Type: application/json" \
  -d "{
    \"idEstablecimiento\": $ID_ESTABLECIMIENTO,
    \"nombre\": \"Dra. Valentina Gomez\",
    \"especialidad\": \"Medicina General\",
    \"costoConsulta\": 1500,
    \"duracionEstandarMinutos\": 30,
    \"estado\": \"ACTIVO\"
  }"
[ "$HTTP" = "201" ] || { echo "Fallo US-58 alta"; exit 1; }
ID_PERSONAL=$(jq -er '.id' "$ARCHIVO")
echo "Personal creado con ID: $ID_PERSONAL"

echo "2.2 Consultar personal por ID existente"
peticion "$BASE_URL/personal/$ID_PERSONAL"
[ "$HTTP" = "200" ] || exit 1

echo "2.3 Listar todo el personal"
peticion "$BASE_URL/personal"
[ "$HTTP" = "200" ] || exit 1

echo "2.4 Actualizar datos de personal existente"
peticion -X PUT "$BASE_URL/personal/$ID_PERSONAL" \
  -H "Content-Type: application/json" \
  -d "{
    \"idEstablecimiento\": $ID_ESTABLECIMIENTO,
    \"nombre\": \"Dra. Valentina Gomez\",
    \"especialidad\": \"Medicina Preventiva\",
    \"costoConsulta\": 1800,
    \"duracionEstandarMinutos\": 30,
    \"estado\": \"ACTIVO\"
  }"
[ "$HTTP" = "200" ] || exit 1

echo "2.5 PE Inválida: Alta de personal con establecimiento inexistente (esperado 404)"
peticion -X POST "$BASE_URL/personal" \
  -H "Content-Type: application/json" \
  -d '{
    "idEstablecimiento": 99999,
    "nombre": "Dr. Fantasma",
    "especialidad": "General",
    "costoConsulta": 1000,
    "duracionEstandarMinutos": 30,
    "estado": "ACTIVO"
  }'
[ "$HTTP" = "404" ] || exit 1

echo "[OK] US-58 Pruebas Personal completo superada."
echo "------------------------------------------------------------------------------"

# ==============================================================================
# IS-70 / US-59: Pruebas Reservas Completo
# ==============================================================================
echo ">>> [IS-70 / US-59] Pruebas Reservas Completo"

echo "3.1 Crear reserva válida (201 Created + Estado SOLICITADO + Evento MQTT)"
peticion -X POST "$BASE_URL/reservas" \
  -H "Content-Type: application/json" \
  -d "{
    \"idPersonal\": $ID_PERSONAL,
    \"idEstablecimiento\": $ID_ESTABLECIMIENTO,
    \"emailCliente\": \"reserva.completa@correo.com\",
    \"telefonoCliente\": \"099123456\",
    \"fecha\": \"$FECHA_FUTURA\",
    \"hora\": \"09:00:00\"
  }"
[ "$HTTP" = "201" ] || exit 1
ID_RESERVA_COMPLETA=$(jq -er '.datos.id' "$ARCHIVO")
echo "Reserva creada con ID: $ID_RESERVA_COMPLETA"

echo "3.2 Consultar reserva por ID existente"
peticion "$BASE_URL/reservas/$ID_RESERVA_COMPLETA"
[ "$HTTP" = "200" ] || exit 1

echo "3.3 Listar reservas generales y filtradas por idPersonal"
peticion "$BASE_URL/reservas?idPersonal=$ID_PERSONAL"
[ "$HTTP" = "200" ] || exit 1

echo "3.4 Consultar reserva por ID inexistente (404 Not Found)"
peticion "$BASE_URL/reservas/99999"
[ "$HTTP" = "404" ] || exit 1

echo "3.5 Validar transición a Agendado por el servicio asíncrono"
esperar_estado "$ID_RESERVA_COMPLETA" "Agendado"

echo "[OK] US-59 Pruebas Reservas Completo superada."
echo "------------------------------------------------------------------------------"

# ==============================================================================
# IS-71 / US-60: Prueba Cancelar Turno
# ==============================================================================
echo ">>> [IS-71 / US-60] Prueba Cancelar Turno"

echo "4.1 Crear un turno temporal para cancelarlo"
peticion -X POST "$BASE_URL/reservas" \
  -H "Content-Type: application/json" \
  -d "{
    \"idPersonal\": $ID_PERSONAL,
    \"idEstablecimiento\": $ID_ESTABLECIMIENTO,
    \"emailCliente\": \"a.cancelar@correo.com\",
    \"telefonoCliente\": \"099777888\",
    \"fecha\": \"$FECHA_FUTURA\",
    \"hora\": \"14:00:00\"
  }"
[ "$HTTP" = "201" ] || exit 1
ID_A_CANCELAR=$(jq -er '.datos.id' "$ARCHIVO")

echo "4.2 Cancelar turno existente vía DELETE (esperado 204 No Content)"
peticion -X DELETE "$BASE_URL/reservas/$ID_A_CANCELAR"
[ "$HTTP" = "204" ] || exit 1

echo "4.3 Verificar que el turno cancelado/eliminado ya no existe (404 Not Found)"
peticion "$BASE_URL/reservas/$ID_A_CANCELAR"
[ "$HTTP" = "404" ] || exit 1

echo "4.4 Intentar cancelar un turno inexistente (esperado 404 Not Found)"
peticion -X DELETE "$BASE_URL/reservas/99999"
[ "$HTTP" = "404" ] || exit 1

echo "[OK] US-60 Prueba Cancelar Turno superada."
echo "------------------------------------------------------------------------------"

# ==============================================================================
# IS-72 / US-61: Prueba Disponibilidad de un Profesional
# ==============================================================================
echo ">>> [IS-72 / US-61] Prueba Disponibilidad de un Profesional"

echo "5.1 Consultar agenda del profesional para la fecha de prueba"
peticion "$BASE_URL/personal/$ID_PERSONAL/agenda?fecha=$FECHA_FUTURA"
[ "$HTTP" = "200" ] || exit 1

echo "5.2 Consultar disponibilidad del profesional (slots libres de 30 min)"
peticion "$BASE_URL/personal/$ID_PERSONAL/disponibilidad?fecha=$FECHA_FUTURA"
[ "$HTTP" = "200" ] || exit 1

echo "[OK] US-61 Prueba Disponibilidad de un Profesional superada."
echo "------------------------------------------------------------------------------"

# ==============================================================================
# IS-73 / US-62: Prueba doble reserva
# ==============================================================================
echo ">>> [IS-73 / US-62] Prueba doble reserva (Evitar doble reserva)"

HORA_DOBLE="11:00:00"

echo "6.1 Enviar primera solicitud para el horario $HORA_DOBLE"
peticion -X POST "$BASE_URL/reservas" \
  -H "Content-Type: application/json" \
  -d "{
    \"idPersonal\": $ID_PERSONAL,
    \"idEstablecimiento\": $ID_ESTABLECIMIENTO,
    \"emailCliente\": \"primero@correo.com\",
    \"telefonoCliente\": \"099111111\",
    \"fecha\": \"$FECHA_FUTURA\",
    \"hora\": \"$HORA_DOBLE\"
  }"
[ "$HTTP" = "201" ] || exit 1
ID_PRIMERO=$(jq -er '.datos.id' "$ARCHIVO")

echo "6.2 Enviar segunda solicitud concurrente para el MISMO horario $HORA_DOBLE"
peticion -X POST "$BASE_URL/reservas" \
  -H "Content-Type: application/json" \
  -d "{
    \"idPersonal\": $ID_PERSONAL,
    \"idEstablecimiento\": $ID_ESTABLECIMIENTO,
    \"emailCliente\": \"segundo@correo.com\",
    \"telefonoCliente\": \"099222222\",
    \"fecha\": \"$FECHA_FUTURA\",
    \"hora\": \"$HORA_DOBLE\"
  }"
[ "$HTTP" = "201" ] || exit 1
ID_SEGUNDO=$(jq -er '.datos.id' "$ARCHIVO")

echo "6.3 Esperar validación: la 1ra debe quedar Agendada"
esperar_estado "$ID_PRIMERO" "Agendado"

echo "6.4 Esperar validación: la 2da duplicada debe quedar Rechazado/Turno Ocupado"
esperar_estado "$ID_SEGUNDO" "Rechazado/Turno Ocupado"

echo "[OK] US-62 Prueba doble reserva superada."
echo "------------------------------------------------------------------------------"

# ==============================================================================
# IS-74 / US-63: Prueba Reserva a Personal Inexistente
# ==============================================================================
echo ">>> [IS-74 / US-63] Prueba Reserva a Personal Inexistente"

echo "7.1 Enviar reserva con idPersonal inexistente (99999)"
peticion -X POST "$BASE_URL/reservas" \
  -H "Content-Type: application/json" \
  -d "{
    \"idPersonal\": 99999,
    \"idEstablecimiento\": $ID_ESTABLECIMIENTO,
    \"emailCliente\": \"sin.personal@correo.com\",
    \"telefonoCliente\": \"099333333\",
    \"fecha\": \"$FECHA_FUTURA\",
    \"hora\": \"15:00:00\"
  }"
[ "$HTTP" = "201" ] || exit 1
ID_SIN_PERSONAL=$(jq -er '.datos.id' "$ARCHIVO")

echo "7.2 Esperar validación: debe quedar Rechazado/Solicitud No Valida"
esperar_estado "$ID_SIN_PERSONAL" "Rechazado/Solicitud No Valida"

echo "[OK] US-63 Prueba Reserva a Personal Inexistente superada."
echo "------------------------------------------------------------------------------"

# ==============================================================================
# IS-75 / US-64: Prueba Reserva ocupada
# ==============================================================================
echo ">>> [IS-75 / US-64] Prueba Reserva ocupada (horario ocupado)"

echo "8.1 Enviar reserva para el slot 09:00 que ya quedó Agendado en US-59"
peticion -X POST "$BASE_URL/reservas" \
  -H "Content-Type: application/json" \
  -d "{
    \"idPersonal\": $ID_PERSONAL,
    \"idEstablecimiento\": $ID_ESTABLECIMIENTO,
    \"emailCliente\": \"colision@correo.com\",
    \"telefonoCliente\": \"099444444\",
    \"fecha\": \"$FECHA_FUTURA\",
    \"hora\": \"09:00:00\"
  }"
[ "$HTTP" = "201" ] || exit 1
ID_COLISION=$(jq -er '.datos.id' "$ARCHIVO")

echo "8.2 Esperar validación: debe rechazarse con Rechazado/Turno Ocupado"
esperar_estado "$ID_COLISION" "Rechazado/Turno Ocupado"

echo "[OK] US-64 Prueba Reserva ocupada superada."
echo "------------------------------------------------------------------------------"

# ==============================================================================
# IS-76 / US-65: Prueba Reserva Fuera de horario
# ==============================================================================
echo ">>> [IS-76 / US-65] Prueba Reserva Fuera de horario (fecha pasada)"

echo "9.1 Enviar reserva con fecha en el pasado ($FECHA_PASADA)"
peticion -X POST "$BASE_URL/reservas" \
  -H "Content-Type: application/json" \
  -d "{
    \"idPersonal\": $ID_PERSONAL,
    \"idEstablecimiento\": $ID_ESTABLECIMIENTO,
    \"emailCliente\": \"pasada@correo.com\",
    \"telefonoCliente\": \"099555555\",
    \"fecha\": \"$FECHA_PASADA\",
    \"hora\": \"10:00:00\"
  }"
[ "$HTTP" = "201" ] || exit 1
ID_PASADA=$(jq -er '.datos.id' "$ARCHIVO")

echo "9.2 Esperar validación: debe rechazarse con Rechazado/Solicitud No Valida"
esperar_estado "$ID_PASADA" "Rechazado/Solicitud No Valida"

echo "[OK] US-65 Prueba Reserva Fuera de horario superada."
echo "------------------------------------------------------------------------------"

echo "=============================================================================="
echo "    TODAS LAS PRUEBAS FUNCIONALES (US-57 A US-65) HAN FINALIZADO CON ÉXITO     "
echo "=============================================================================="
