@echo off

rem Crude install script for jEdit on Windows NT and OS/2

rem To change the value of a set command, edit the text after the `='.
rem Don't change the enviroment variable name (DIRECTORY, JAVA).

rem The installation directory.
set DIRECTORY=C:\Program Files\jEdit

rem The Java virtual machine. The `.exe' prefix is not required.
rem On JDK 1.1/1.2 (aka Java 2) and JRE 1.2, this should be `java'.
rem If you are using JRE 1.1, set it to `jre'.
set JAVA=java

rem Don't change anything after this point unless you know what you
rem are doing.

echo -- jEdit installation script

echo -
echo The installation directory is [%DIRECTORY%]
echo The Java virtual machine is [%JAVA%]
echo -

rem Create the batch file for starting jEdit.
rem Because of DOS's poor scripting abilities, we can't write a
rem generic script that determines the install directory, so if
rem the user moves jEdit is a different directory, boomfuck!!!
echo %JAVA% -classpath "%DIRECTORY%\jedit.jar;%%CLASSPATH%%" -mx32m %%JEDIT%% org.gjt.sp.jedit.jEdit %%1 %%2 %%3 %%4 %%5 %%6 %%7 %%8 %%9 > jedit.bat

rem Copy the required files (jedit.jar, jedit.bat)
md "%DIRECTORY%"
copy jedit.jar "%DIRECTORY%"
copy jedit.bat "%DIRECTORY%"

rem Create directory where user can install plugins
md "%DIRECTORY%\jars"
copy jars\*.jar "%DIRECTORY%\jars"

echo -- Installation complete.
echo -- Run %DIRECTORY%\jedit.bat to start jEdit.
