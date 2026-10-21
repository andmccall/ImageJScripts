@echo off
setlocal

set "SRC=C:\path\to\parent"
set "DEST=C:\path\to\destination"

if not exist "%DEST%" mkdir "%DEST%"

for /d %%D in ("%SRC%\*") do (
    for %%F in ("%%D\*.dcm") do (
        copy "%%F" "%DEST%\%%~nxD_%%~nxF"
    )
)

endlocal
