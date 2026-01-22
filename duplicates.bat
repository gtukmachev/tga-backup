@echo off

set JAR_FILE=target\tga-backup-1.0-SNAPSHOT-jar-with-dependencies.jar
java -jar %JAR_FILE% -m duplicates %*
