@echo off
rem Abre um CLIENTE com um duplo-clique (Windows). Pode abrir vários.
cd /d "%~dp0"
start "" javaw -jar clienteservidor.jar cliente
