@echo off
rem Sobe o SERVIDOR com um duplo-clique (Windows). Precisa de Java 21+ instalado.
cd /d "%~dp0"
start "" javaw -jar clienteservidor.jar servidor
