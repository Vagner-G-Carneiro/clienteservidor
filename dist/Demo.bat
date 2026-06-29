@echo off
rem Demonstração rápida: sobe 1 servidor + 2 clientes de uma vez (Windows).
rem Depois é so: no Servidor clicar "Iniciar"; em cada Cliente clicar "Conectar".
cd /d "%~dp0"
start "" javaw -jar clienteservidor.jar servidor
timeout /t 1 /nobreak >nul
start "" javaw -jar clienteservidor.jar cliente
start "" javaw -jar clienteservidor.jar cliente
