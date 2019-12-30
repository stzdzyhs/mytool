@echo off
set DIR=%~dp0
java -cp "$DIR/target/mytool.jar" tool.SyncDir %1 %2 %3 %4 %5 %6 %7
