#!/bin/bash
cd "$(dirname "$0")" || exit 1
resultado=0

for modulo in turno-validador turnos-api facturacion; do
  echo "integracion de $modulo"
  mvn -f "$modulo/pom.xml" test \
    '-Dtest=**/integracion/*Test.java' \
    -Djunit.jupiter.execution.parallel.enabled=false || resultado=1
done

exit "$resultado"
