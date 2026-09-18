@echo off
setlocal enabledelayedexpansion
echo === Layer 4 仿真进度监控 ===
echo 监控目录: D:\Luan\2025-09\MATSim\guangzhoubaselineOutput
echo.
:loop
echo [%date% %time%] 检查中...
echo --- ITERS 目录 ---
dir "D:\Luan\2025-09\MATSim\guangzhoubaselineOutput\ITERS" /B 2>nul | findstr /R "it\." | sort
echo --- modestats.csv 最新 3 行 ---
powershell -NoProfile -Command "Get-Content 'D:\Luan\2025-09\MATSim\guangzhoubaselineOutput\modestats.csv' -ErrorAction SilentlyContinue | Select-Object -Last 3" 2>nul
echo --- Java 进程 ---
tasklist /FI "IMAGENAME eq java.exe" 2>nul | findstr /R "java.exe"
echo.
timeout /T 300 /NOBREAK >nul
goto loop