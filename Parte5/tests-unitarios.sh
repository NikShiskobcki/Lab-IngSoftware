#!/bin/bash
cd "$(dirname "$0")" || exit 1
resultado=0

for modulo in turno-validador turnos-api facturacion; do
  echo "unitarios de $modulo"
  mvn -f "$modulo/pom.xml" test \
    '-Dtest=*Test,!**/integracion/**,!**/funcionales/**' \
    -Djunit.jupiter.execution.parallel.enabled=false || resultado=1
done

exit "$resultado"
