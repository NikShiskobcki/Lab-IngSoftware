#!/bin/bash
for m in turno-validador turnos-api facturacion turno-subscriber; do
  echo "=== $m ==="
  (cd $m && mvn -q clean test -Dmaven.test.failure.ignore=true | tail -n 15)
done