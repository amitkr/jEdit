@echo off
set CLASSPATH=C:\Program Files\jEdit\jedit.jar;%CLASSPATH%
java %JOPEN_J% jOpen -portfile=C:\Windows\Temp\jedit-server %JOPEN% %1 %2 %3 %4 %5 %6 %7 %8 %9
