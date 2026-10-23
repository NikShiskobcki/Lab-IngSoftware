#!/bin/bash
# Pruebas de integracion entre modulos (flujo API -> validador -> facturacion, a traves de la BD)
# Estrategia bottom-up, ultimo nivel (US-54). Casos CP-231 a CP-239.
#
# Requisitos: Docker Desktop, curl, jq. El sistema debe estar levantado:
#   docker compose up -d --build
# Para que los servicios revisen mas seguido (opcional, acorta la espera):
#   docker compose -f docker-compose.yml -f docker-compose.integracion.yml up -d --build
#
# Uso:  bash testing-integracion.sh | tee resultados-integracion.txt

BASE_URL="${BASE_URL:-http://localhost:8080}"
DB_CONTENEDOR="${DB_CONTENEDOR:-reservas-db}"
ESPERA_VALIDADOR="${ESPERA_VALIDADOR:-150}"      # segundos (el validador revisa cada 1 minuto)
ESPERA_FACTURACION="${ESPERA_FACTURACION:-420}"  # segundos (facturacion revisa cada 5 minutos)

ARCHIVO=$(mktemp)
trap 'rm -f "$ARCHIVO"' EXIT

for comando in curl jq docker; do
  command -v "$comando" >/dev/null 2>&1 || { echo "falta instalar $comando"; exit 1; }
done

# fechas (GNU date, BSD/macOS date o python)
fecha_relativa() {  # $1 = dias (ej: 3 o -1)
  local n=$1
  date -u -d "$n days" +%F 2>/dev/null \
    || date -u -v"$( [ "$n" -ge 0 ] && echo "+$n" || echo "$n" )d" +%F 2>/dev/null \
    || python3 -c "import datetime; print((datetime.datetime.utcnow().date()+datetime.timedelta(days=$n)).isoformat())"
}
FECHA_FUTURA=$(fecha_relativa 3)
FECHA_PASADA=$(fecha_relativa -1)
SUFIJO=$(date +%s)

APROBADOS=0
FALLADOS=0
BUGS=0
RESUMEN=""

# ---------- utilidades ----------
sql() {
  docker exec -i "$DB_CONTENEDOR" mariadb -u reservas_app -padmin -N -B reservas -e "$1" 2>/dev/null
}

# peticion METODO RUTA [BODY] -> deja HTTP y el cuerpo en $ARCHIVO
peticion() {
  local metodo=$1 ruta=$2 cuerpo=$3
  if [ -n "$cuerpo" ]; then
    HTTP=$(curl -sS --connect-timeout 5 --max-time 20 -o "$ARCHIVO" -w '%{http_code}' \
      -X "$metodo" "$BASE_URL$ruta" -H 'Content-Type: application/json' -d "$cuerpo")
  else
    HTTP=$(curl -sS --connect-timeout 5 --max-time 20 -o "$ARCHIVO" -w '%{http_code}' \
      -X "$metodo" "$BASE_URL$ruta")
  fi
}

# espera hasta que la reserva deje de estar SOLICITADO (o hasta el limite); devuelve el estado final
esperar_cambio() {  # $1 = id, $2 = limite en segundos
  local id=$1 limite=$2 inicio=$SECONDS estado=""
  while (( SECONDS - inicio < limite )); do
    estado=$(sql "SELECT estado FROM reservas_turnos WHERE id = $id")
    [ -n "$estado" ] && [ "$estado" != "SOLICITADO" ] && { echo "$estado"; return; }
    sleep 3
  done
  echo "${estado:-SIN_FILA}"
}

# espera hasta que la reserva llegue a un estado concreto
esperar_estado() {  # $1 = id, $2 = estado esperado, $3 = limite
  local id=$1 esperado=$2 limite=$3 inicio=$SECONDS estado=""
  while (( SECONDS - inicio < limite )); do
    estado=$(sql "SELECT estado FROM reservas_turnos WHERE id = $id")
    [ "$estado" = "$esperado" ] && { echo "$estado"; return; }
    sleep 3
  done
  echo "${estado:-SIN_FILA}"
}

verificar() {  # $1 = CP, $2 = descripcion, $3 = esperado, $4 = obtenido
  if [ "$3" = "$4" ]; then
    echo "  [OK]    $1 | $2 | $4"
    APROBADOS=$((APROBADOS + 1))
    RESUMEN="$RESUMEN$1 passed\n"
  else
    echo "  [FALLA] $1 | $2 | esperado: '$3' | obtenido: '$4'"
    FALLADOS=$((FALLADOS + 1))
    RESUMEN="$RESUMEN$1 FALLA\n"
  fi
}

# igual que verificar, pero una falla es un bug ya conocido (no corta ni confunde el resultado)
verificar_bug_conocido() {  # $1 = CP, $2 = descripcion, $3 = esperado, $4 = obtenido, $5 = incidencia
  if [ "$3" = "$4" ]; then
    echo "  [OK]    $1 | $2 | $4"
    APROBADOS=$((APROBADOS + 1))
    RESUMEN="$RESUMEN$1 passed\n"
  else
    echo "  [BUG]   $1 | $2 | esperado: '$3' | obtenido: '$4' | incidencia $5"
    BUGS=$((BUGS + 1))
    RESUMEN="$RESUMEN$1 bug detectado ($5)\n"
  fi
}

reservar() {  # $1 = idEstablecimiento, $2 = idPersonal, $3 = email, $4 = fecha, $5 = hora
  peticion POST /reservas "{
    \"idEstablecimiento\": $1,
    \"idPersonal\": $2,
    \"emailCliente\": \"$3\",
    \"telefonoCliente\": \"099123456\",
    \"fecha\": \"$4\",
    \"hora\": \"$5\"
  }"
}

echo "=============================================================="
echo " PRUEBAS DE INTEGRACION ENTRE MODULOS (API -> validador -> facturacion)"
echo "=============================================================="
echo "Servidor: $BASE_URL | Fecha futura: $FECHA_FUTURA | Fecha pasada: $FECHA_PASADA"
echo

peticion GET /health
[ "$HTTP" = "200" ] || { echo "la API no responde (HTTP $HTTP); levantar el sistema con docker compose up -d --build"; exit 1; }
sql "SELECT 1" >/dev/null || { echo "no se puede consultar la BD en el contenedor $DB_CONTENEDOR"; exit 1; }

# ---------- datos base (por la API) ----------
echo ">>> Datos base"
peticion POST /establecimientos '{
  "nombreComercial": "Integracion '"$SUFIJO"'",
  "direccion": "Calle Integracion 1",
  "telefono": "29001234",
  "correoElectronico": "integracion@tapronto.com",
  "horarioApertura": "08:00",
  "horarioCierre": "18:00"
}'
ID_EST=$(jq -er '.id' "$ARCHIVO") || { echo "no se pudo crear el establecimiento (HTTP $HTTP)"; exit 1; }

peticion POST /personal '{
  "idEstablecimiento": '"$ID_EST"',
  "nombre": "Profesional Activo",
  "especialidad": "Consulta",
  "costoConsulta": 1500,
  "duracionEstandarMinutos": 30,
  "estado": "ACTIVO"
}'
ID_ACTIVO=$(jq -er '.id' "$ARCHIVO") || { echo "no se pudo crear el personal activo (HTTP $HTTP)"; exit 1; }

peticion POST /personal '{
  "idEstablecimiento": '"$ID_EST"',
  "nombre": "Profesional Inactivo",
  "especialidad": "Consulta",
  "costoConsulta": 1500,
  "duracionEstandarMinutos": 30,
  "estado": "INACTIVO"
}'
ID_INACTIVO=$(jq -er '.id' "$ARCHIVO") || { echo "no se pudo crear el personal inactivo (HTTP $HTTP)"; exit 1; }
echo "Establecimiento $ID_EST | Profesional activo $ID_ACTIVO | Profesional inactivo $ID_INACTIVO"
echo

EMAIL_A="integracion.a.$SUFIJO@correo.com"
EMAIL_B="integracion.b.$SUFIJO@correo.com"
EMAIL_C="integracion.c.$SUFIJO@correo.com"

# ---------- solicitudes (todas juntas: el validador las procesa en su ciclo) ----------
echo ">>> Envio de solicitudes por la API"
reservar "$ID_EST" "$ID_ACTIVO" "$EMAIL_A" "$FECHA_FUTURA" "10:00"
[ "$HTTP" = "201" ] && ID_R1=$(jq -er '.datos.id' "$ARCHIVO")
echo "  R1 (cliente A, 10:00)            HTTP $HTTP id=$ID_R1"

reservar "$ID_EST" "$ID_ACTIVO" "$EMAIL_A" "$FECHA_FUTURA" "12:00"
[ "$HTTP" = "201" ] && ID_R2=$(jq -er '.datos.id' "$ARCHIVO")
echo "  R2 (cliente A, 12:00)            HTTP $HTTP id=$ID_R2"

reservar "$ID_EST" "$ID_ACTIVO" "$EMAIL_B" "$FECHA_FUTURA" "14:00"
[ "$HTTP" = "201" ] && ID_R3=$(jq -er '.datos.id' "$ARCHIVO")
echo "  R3 (cliente B, 14:00)            HTTP $HTTP id=$ID_R3"

reservar "$ID_EST" "$ID_ACTIVO" "$EMAIL_C" "$FECHA_FUTURA" "14:00"
[ "$HTTP" = "201" ] && ID_R4=$(jq -er '.datos.id' "$ARCHIVO")
echo "  R4 (cliente C, 14:00, mismo horario que R3) HTTP $HTTP id=$ID_R4"

reservar "$ID_EST" "$ID_INACTIVO" "$EMAIL_C" "$FECHA_FUTURA" "09:00"
[ "$HTTP" = "201" ] && ID_R5=$(jq -er '.datos.id' "$ARCHIVO")
echo "  R5 (profesional inactivo)        HTTP $HTTP id=$ID_R5"

reservar "$ID_EST" 999999 "$EMAIL_C" "$FECHA_FUTURA" "09:00"
HTTP_INEXISTENTE=$HTTP
echo "  R6 (profesional inexistente)     HTTP $HTTP"
echo

for v in ID_R1 ID_R2 ID_R3 ID_R4 ID_R5; do
  [ -n "${!v}" ] || { echo "no se pudo crear $v; se detiene la prueba"; exit 1; }
done

# ---------- CP-231: API -> BD ----------
echo ">>> CP-231 | La API guarda la solicitud en la BD con los datos enviados"
verificar "CP-231" "email guardado de R1" "$EMAIL_A" "$(sql "SELECT email_solicitante FROM reservas_turnos WHERE id = $ID_R1")"
verificar "CP-231" "profesional guardado de R1" "$ID_ACTIVO" "$(sql "SELECT id_personal FROM reservas_turnos WHERE id = $ID_R1")"
echo

# ---------- CP-232: validador agenda una solicitud valida ----------
echo ">>> CP-232 | El validador lee la solicitud de la BD y la agenda (espera hasta ${ESPERA_VALIDADOR}s)"
verificar "CP-232" "estado de R1 tras la validacion" "Agendado" "$(esperar_cambio "$ID_R1" "$ESPERA_VALIDADOR")"
verificar "CP-232" "estado de R2 tras la validacion" "Agendado" "$(esperar_cambio "$ID_R2" "$ESPERA_VALIDADOR")"
echo

# ---------- CP-233: doble reserva ----------
echo ">>> CP-233 | Dos solicitudes para el mismo horario: se agenda la primera y se rechaza la otra"
verificar "CP-233" "estado de R3 (primera en llegar)" "Agendado" "$(esperar_cambio "$ID_R3" "$ESPERA_VALIDADOR")"
verificar "CP-233" "estado de R4 (mismo horario)" "Rechazado/Turno Ocupado" "$(esperar_cambio "$ID_R4" "$ESPERA_VALIDADOR")"
verificar "CP-233" "turnos agendados en ese horario" "1" "$(sql "SELECT COUNT(*) FROM reservas_turnos WHERE id_personal = $ID_ACTIVO AND fecha_turno = '$FECHA_FUTURA' AND hora_turno = '14:00:00' AND estado = 'Agendado'")"
echo

# ---------- CP-234: profesional inactivo ----------
echo ">>> CP-234 | Solicitud de un profesional inactivo"
verificar "CP-234" "estado de R5" "Rechazado/Solicitud No Valida" "$(esperar_cambio "$ID_R5" "$ESPERA_VALIDADOR")"
echo

# ---------- CP-235: profesional inexistente ----------
echo ">>> CP-235 | Solicitud de un profesional inexistente (la BD rechaza por clave foranea)"
verificar "CP-235" "codigo HTTP de la API" "409" "$HTTP_INEXISTENTE"
verificar "CP-235" "filas guardadas para ese profesional" "0" "$(sql "SELECT COUNT(*) FROM reservas_turnos WHERE id_personal = 999999")"
echo

# ---------- CP-236: lo que escribio el validador se ve por la API ----------
echo ">>> CP-236 | La agenda de la API muestra el estado que dejo el validador"
peticion GET "/personal/$ID_ACTIVO/agenda?fecha=$FECHA_FUTURA"
verificar "CP-236" "estado de R1 en la agenda" "Agendado" "$(jq -r ".turnos[] | select(.idReserva == $ID_R1) | .estado" "$ARCHIVO")"
verificar "CP-236" "estado de R4 en la agenda" "Rechazado/Turno Ocupado" "$(jq -r ".turnos[] | select(.idReserva == $ID_R4) | .estado" "$ARCHIVO")"
echo

# ---------- CP-237: disponibilidad (INC-02) ----------
echo ">>> CP-237 | La disponibilidad no ofrece un horario ya agendado (INC-02)"
peticion GET "/personal/$ID_ACTIVO/disponibilidad?fecha=$FECHA_FUTURA"
OFRECE_10=$(jq -r '[.horariosDisponibles[] | tostring | select(test("^10:00") or . == "[10,0]")] | length' "$ARCHIVO")
verificar_bug_conocido "CP-237" "horario 10:00 (R1 agendada) ofrecido como libre" "0" "$OFRECE_10" "INC-02"
echo

# ---------- CP-238 y 68: atencion y facturacion ----------
echo ">>> Se simula que paso la fecha de R1 y R2 (cambio de fecha en la BD, como en demo-completo.sh)"
sql "UPDATE reservas_turnos SET fecha_turno = '$FECHA_PASADA' WHERE id IN ($ID_R1, $ID_R2) AND estado = 'Agendado'"
echo

echo ">>> CP-238 | Turno agendado cuya fecha paso: validador -> Atendido -> facturacion -> Facturado"
echo "    (espera hasta ${ESPERA_VALIDADOR}s para Atendido y ${ESPERA_FACTURACION}s para Facturado)"
ESTADO_R1=$(esperar_estado "$ID_R1" Atendido "$ESPERA_VALIDADOR")
[ "$ESTADO_R1" = "Facturado" ] && ESTADO_R1=Atendido   # si facturacion fue muy rapida, tambien cuenta
verificar "CP-238" "R1 llego a Atendido" "Atendido" "$ESTADO_R1"
verificar "CP-238" "R1 llego a Facturado" "Facturado" "$(esperar_estado "$ID_R1" Facturado "$ESPERA_FACTURACION")"
verificar "CP-238" "R2 llego a Facturado" "Facturado" "$(esperar_estado "$ID_R2" Facturado "$ESPERA_FACTURACION")"
echo

echo ">>> CP-239 | Dos turnos del mismo cliente y mes: un cliente, una factura, dos items, total sumado"
MES=$((10#$(echo "$FECHA_PASADA" | cut -d- -f2)))
verificar "CP-239" "clientes con el email del cliente A" "1" "$(sql "SELECT COUNT(*) FROM clientes WHERE email = '$EMAIL_A'")"
verificar "CP-239" "facturas del cliente A en el mes del turno" "1" "$(sql "SELECT COUNT(*) FROM facturas f JOIN clientes c ON c.id = f.id_cliente WHERE c.email = '$EMAIL_A' AND f.anio = ${FECHA_PASADA%%-*} AND f.mes = $MES")"
verificar "CP-239" "items de esa factura" "2" "$(sql "SELECT COUNT(*) FROM items_factura i JOIN facturas f ON f.id = i.id_factura JOIN clientes c ON c.id = f.id_cliente WHERE c.email = '$EMAIL_A' AND i.id_turno IN ($ID_R1, $ID_R2)")"
verificar "CP-239" "total de la factura" "3000.00" "$(sql "SELECT f.total FROM facturas f JOIN clientes c ON c.id = f.id_cliente WHERE c.email = '$EMAIL_A' AND f.anio = ${FECHA_PASADA%%-*} AND f.mes = $MES")"
echo

# ---------- CP-240: inmutabilidad por la API ----------
echo ">>> CP-240 | Una reserva Atendido o Facturado no se puede modificar ni eliminar por la API"
peticion PUT "/reservas/$ID_R1" "{
  \"idEstablecimiento\": $ID_EST,
  \"idPersonal\": $ID_ACTIVO,
  \"emailCliente\": \"$EMAIL_A\",
  \"telefonoCliente\": \"099123456\",
  \"fecha\": \"$FECHA_FUTURA\",
  \"hora\": \"16:00\"
}"
verificar "CP-240" "codigo HTTP del PUT" "400" "$HTTP"
peticion DELETE "/reservas/$ID_R1"
verificar "CP-240" "codigo HTTP del DELETE" "400" "$HTTP"
verificar "CP-240" "R1 sigue en la BD, Facturado" "Facturado" "$(sql "SELECT estado FROM reservas_turnos WHERE id = $ID_R1")"
echo

# ---------- resumen ----------
echo "=============================================================="
echo " RESUMEN"
echo "=============================================================="
printf "%b" "$RESUMEN" | sort -u
echo
echo "Verificaciones OK: $APROBADOS | Fallas: $FALLADOS | Bugs conocidos: $BUGS"
[ "$FALLADOS" -eq 0 ]