@echo on

setlocal enabledelayedexpansion

set classpath=.

for %%J in (..\lib\java\*.jar) do set classpath=!classpath!;%%J

set classpath=%classpath%\;..\lib\servlet-api-2.5-20081211.jar

java -Djava.util.logging.config.file=logging.properties -cp %classpath% QueryTest

endlocal

