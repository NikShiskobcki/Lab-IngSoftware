#!/bin/bash
# Corre los tests de codigos HTTP y muestra una tabla con el codigo esperado y el recibido.
# Uso (desde la carpeta Parte5):  bash tests-http.sh
cd "$(dirname "$0")" || exit 1
LOG=resultados-http.txt

echo "Ejecutando tests HTTP de turnos-api (puede tardar en la primera ejecucion)..."
mvn -f turnos-api/pom.xml test \
  '-Dtest=com.laboratorio.turnos.api.http.*Test' \
  -Djunit.jupiter.execution.parallel.enabled=false > "$LOG" 2>&1
rc=$?

echo
printf '%-12s %-7s %-34s %-9s %-9s %s\n' "CASO" "METODO" "URI" "ESPERADO" "RECIBIDO" "RESULTADO"
printf '%-12s %-7s %-34s %-9s %-9s %s\n' "------------" "-------" "----------------------------------" "---------" "---------" "---------"
tr -d '\r' < "$LOG" | grep '^HTTP_LOG|' | sort -t'|' -k2,2 -u | awk -F'|' '
  { r = ($5 == $6) ? "OK" : "DIFERENTE"; if (r == "OK") ok++; else bad++;
    printf "%-12s %-7s %-34s %-9s %-9s %s\n", $2, $3, $4, $5, $6, r }
  END { printf "\nCodigos coincidentes: %d | distintos de lo esperado: %d\n", ok, bad }'

echo
tr -d '\r' < "$LOG" | grep -E 'Tests run:.*Fail|BUILD (SUCCESS|FAILURE)|COMPILATION ERROR' | tail -4
echo "Salida completa de Maven en: $LOG"
exit "$rc"
