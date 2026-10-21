@echo off
setlocal

set "SRC=Z:\00000101"
set "DEST=C:\Users\406SRVBRB\Desktop\101"

if not exist "%DEST%" mkdir "%DEST%"

for /d %%D in ("%SRC%\*") do (
    mkdir "%DEST%\%%~nxD\"
    xcopy "%%D\*.dcm" "%DEST%\%%~nxD\" /Y
)

endlocal
