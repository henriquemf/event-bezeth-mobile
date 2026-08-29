@echo off
rem  Gera o .apk assinado, pronto para mandar.
rem
rem  Da para dar clique duplo neste arquivo, ou chamar do terminal:
rem
rem      gerar-apk
rem      gerar-apk -Versao 1.1.0
rem      gerar-apk -Instalar
rem
rem  Existe por causa de UMA linha: o `-ExecutionPolicy Bypass`. O Windows
rem  recusa rodar .ps1 por clique duplo na configuracao padrao, e a mensagem
rem  que ele da ("nao pode ser carregado porque a execucao de scripts foi
rem  desabilitada") parece defeito do script. Aqui a permissao vale so para
rem  esta chamada; nada muda na maquina.
rem
rem  O `pause` no fim e para a janela nao sumir com o resultado junto quando
rem  se abre por clique duplo.

powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0empacotar.ps1" %*

echo.
pause
