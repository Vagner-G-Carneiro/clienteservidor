#!/usr/bin/env bash
# Demonstração rápida: sobe 1 servidor + 2 clientes de uma vez (Linux/Mac).
# Depois é só: no Servidor clicar "Iniciar"; em cada Cliente clicar "Conectar".
cd "$(dirname "$0")"
java -jar clienteservidor.jar servidor &
sleep 1
java -jar clienteservidor.jar cliente &
java -jar clienteservidor.jar cliente &
