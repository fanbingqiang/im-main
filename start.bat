@echo off
chcp 65001 >nul
title ti_live_app 本地一键启动
REM ============================================================
REM  ti_live_app 微服务本地一键启动脚本 (Windows)
REM
REM  使用前提:
REM    1. JDK 17 已安装并加入 PATH（或修改下面 JAVA_HOME）
REM    2. 已执行过 mvn clean package -DskipTests 生成 jar
REM    3. Nacos(8848)、MySQL(3306)、Redis(6379) 已启动
REM  说明:
REM    每个服务独立窗口启动，关闭窗口即停止该服务
REM    端口: gateway=9000 api=8080 user=8081 im=8085 live=8086 vod=8087 id=8090
REM ============================================================

set BASE_DIR=%~dp0

REM ---------- 基础设施地址（按需修改） ----------
set NACOS_ADDR=localhost
set REDIS_HOST=localhost
set MYSQL_HOST=localhost
set MYSQL_PASSWORD=123456
set GATEWAY_SECRET=tlive-dev-secret

REM ---------- JDK17 + hessian2 序列化所需 JVM 参数 ----------
REM 不加会报: Unable to make field private int java.math.BigInteger.signum accessible
set JVM_OPENS=--add-opens java.base/java.lang=ALL-UNNAMED --add-opens java.base/java.math=ALL-UNNAMED

REM ---------- JVM 内存（按机器配置调整） ----------
set JVM_MEM=-Xms256m -Xmx512m

echo 正在启动 ti_live_app 全部 7 个微服务 ...
echo   Nacos=%NACOS_ADDR%  Redis=%REDIS_HOST%  MySQL=%MYSQL_HOST%  MYSQL_PASSWORD=%MYSQL_PASSWORD%
echo.

start "id-generate-provider(8090)" java %JVM_MEM% %JVM_OPENS% -jar "%BASE_DIR%id-generate\id-generate-provider\target\id-generate-provider-1.0.jar"
start "user-provider(8081)"       java %JVM_MEM% %JVM_OPENS% -jar "%BASE_DIR%ti-live-user\ti-live-user-provider\target\ti-live-user-provider-1.0.jar"
start "im-provider(8085)"         java %JVM_MEM% %JVM_OPENS% -jar "%BASE_DIR%ti-live-im\ti-live-im-provider\target\ti-live-im-provider-1.0.jar"
start "live-provider(8086)"       java %JVM_MEM% %JVM_OPENS% -jar "%BASE_DIR%ti-live-live\ti-live-live-provider\target\ti-live-live-provider-1.0.jar"
start "vod-provider(8087)"        java %JVM_MEM% %JVM_OPENS% -jar "%BASE_DIR%ti-live-vod\ti-live-vod-provider\target\ti-live-vod-provider-1.0.jar"
start "api(8080)"                 java %JVM_MEM% %JVM_OPENS% -jar "%BASE_DIR%ti-live-api\target\ti-live-api-1.0.jar"
start "gateway(9000)"             java %JVM_MEM% %JVM_OPENS% -jar "%BASE_DIR%ti-live-gateway\target\ti-live-gateway-1.0.jar"

echo.
echo 已发起全部服务启动，请观察各窗口日志。
echo 健康检查: http://localhost:9000/actuator/health
pause
