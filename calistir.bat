@echo off
chcp 65001 >nul
cd /d "%~dp0"
javac -encoding UTF-8 src\HavaDurumu.java -d out 2>nul
java -cp out HavaDurumu
pause