@echo off
title Multithreaded Download Manager

echo Compiling backend server...
javac backend\*.java

if errorlevel 1 (
    echo Compilation failed!
    pause
    exit /b
)

echo Starting server...
start cmd /k "java -cp backend DownloadServer"

timeout /t 3 > nul

echo Opening frontend web page...
start "" "Frondend\index.html"

echo Done.
pause
