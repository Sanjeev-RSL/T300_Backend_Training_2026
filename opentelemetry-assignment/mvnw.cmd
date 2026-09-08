@REM ----------------------------------------------------------------------------
@REM Licensed to the Apache Software Foundation (ASF)
@REM Maven Wrapper startup batch script
@REM ----------------------------------------------------------------------------
@IF "%__MVNW_ARG0_NAME__%"=="" (SET "BASE_DIR=%~dp0")

@SET WRAPPER_JAR="%BASE_DIR%.mvn\wrapper\maven-wrapper.jar"
@SET WRAPPER_LAUNCHER=org.apache.maven.wrapper.MavenWrapperMain

@SET DOWNLOAD_URL="https://repo1.maven.org/maven2/org/apache/maven/wrapper/maven-wrapper/3.2.0/maven-wrapper-3.2.0.jar"

@FOR /F "usebackq tokens=1,2 delims==" %%A IN ("%BASE_DIR%.mvn\wrapper\maven-wrapper.properties") DO (
    @IF "%%A"=="wrapperUrl" SET DOWNLOAD_URL=%%B
)

@IF NOT EXIST %WRAPPER_JAR% (
    echo Downloading Maven Wrapper...
    powershell -Command "&{[Net.ServicePointManager]::SecurityProtocol=[Net.SecurityProtocolType]::Tls12; Invoke-WebRequest -Uri %DOWNLOAD_URL% -OutFile %WRAPPER_JAR%}"
)

@"%JAVA_HOME%\bin\java.exe" -jar %WRAPPER_JAR% %WRAPPER_LAUNCHER% %*