FROM openjdk:8-jre-slim

LABEL maintainer="ops"
LABEL description="应急救援物资管理系统"

COPY target/springbootxnj6l.jar /app.jar

EXPOSE 8080

# 数据库连接通过环境变量注入:
#   DB_URL       — JDBC 连接串
#   DB_USERNAME  — 数据库用户名
#   DB_PASSWORD  — 数据库密码
ENTRYPOINT ["java", "-jar", "/app.jar"]
