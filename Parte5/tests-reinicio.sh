#!/bin/bash
cd "$(dirname "$0")" || exit 1

echo "recuperacion de facturacion tras reinicio"
mvn -f turnos-api/pom.xml test \
  -Dtest=RecuperacionFacturacionFunctionalTest \
  -Djunit.jupiter.execution.parallel.enabled=false
