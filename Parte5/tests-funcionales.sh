#!/bin/bash
cd "$(dirname "$0")" || exit 1

echo "funcionales de la API"
mvn -f turnos-api/pom.xml test \
  '-Dtest=**/funcionales/*Test.java,!**/RecuperacionFacturacionFunctionalTest.java' \
  -Djunit.jupiter.execution.parallel.enabled=false
