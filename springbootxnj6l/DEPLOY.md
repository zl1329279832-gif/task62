# 部署手册 — 应急物资管理系统

> 本系统同时支持 MySQL 和 SQL Server 两种数据库。通过 Maven profile 切换，打包时一次选定，运行时不再变更。

---

## 1. 环境变量

| 变量名 | 说明 | 示例（MySQL） | 示例（SQL Server） |
|--------|------|---------------|-------------------|
| `DB_URL` | JDBC 连接串 | `jdbc:mysql://db-host:3306/springbootxnj6l?useUnicode=true&characterEncoding=utf-8&serverTimezone=GMT%2B8` | `jdbc:sqlserver://db-host:1433;DatabaseName=springbootxnj6l` |
| `DB_USERNAME` | 数据库用户名 | `root` | `sa` |
| `DB_PASSWORD` | 数据库密码 | `your_password` | `your_password` |

> **不设环境变量时的默认值**：各 profile 在 `pom.xml` 中定义了本地开发默认值（MySQL: `root/123456`，SQL Server: `sa/123456`），直接 `mvn compile -P mysql` 即可在本地跑通。

---

## 2. 打包命令

```bash
# MySQL 版本
mvn clean package -P mysql

# SQL Server 版本
mvn clean package -P sqlserver

# 覆盖环境变量（CI / 生产打包）
mvn clean package -P mysql \
  -DDB_URL="jdbc:mysql://10.0.0.5:3306/springbootxnj6l?useUnicode=true&characterEncoding=utf-8&serverTimezone=GMT%2B8" \
  -DDB_USERNAME=app_user \
  -DDB_PASSWORD=prod_password

# 打 war 包（裸 Tomcat 部署用）
mvn clean package -P mysql -f pom-war.xml
mvn clean package -P sqlserver -f pom-war.xml
```

产物路径：`target/springbootxnj6l.jar`（或 `.war`），产物名字与 context-path `/springbootxnj6l` 保持一致。

---

## 3. Docker 部署

### 3.1 Dockerfile

```dockerfile
FROM openjdk:8-jre-alpine

LABEL maintainer="emergency-supply@your-org.cn"

# 时区设置（可选）
RUN apk add --no-cache tzdata \
    && cp /usr/share/zoneinfo/Asia/Shanghai /etc/localtime \
    && echo "Asia/Shanghai" > /etc/timezone \
    && apk del tzdata

WORKDIR /app

# 复制打包产物（根据 profile 选择对应的 jar）
COPY target/springbootxnj6l.jar app.jar

# 环境变量默认值（可被 docker run -e 覆盖）
ENV DB_URL="" \
    DB_USERNAME="" \
    DB_PASSWORD="" \
    JAVA_OPTS="-Xms256m -Xmx512m"

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
```

### 3.2 docker run

```bash
# -------- MySQL 版本 --------
docker build -t emergency-supply:mysql .

docker run -d \
  --name emergency-mysql \
  -p 8080:8080 \
  -e DB_URL="jdbc:mysql://10.0.0.5:3306/springbootxnj6l?useUnicode=true&characterEncoding=utf-8&serverTimezone=GMT%2B8" \
  -e DB_USERNAME=app_user \
  -e DB_PASSWORD=prod_password \
  emergency-supply:mysql

# -------- SQL Server 版本 --------
docker build -t emergency-supply:sqlserver .

docker run -d \
  --name emergency-sqlserver \
  -p 8080:8080 \
  -e DB_URL="jdbc:sqlserver://10.0.0.6:1433;DatabaseName=springbootxnj6l" \
  -e DB_USERNAME=sa \
  -e DB_PASSWORD=prod_password \
  emergency-supply:sqlserver
```

### 3.3 docker-compose

**MySQL 全套（应用 + 数据库）：**

```yaml
# docker-compose-mysql.yml
version: '3.8'

services:
  db:
    image: mysql:5.7
    environment:
      MYSQL_ROOT_PASSWORD: root_password
      MYSQL_DATABASE: springbootxnj6l
      MYSQL_USER: app_user
      MYSQL_PASSWORD: app_password
    ports:
      - "3306:3306"
    volumes:
      - mysql_data:/var/lib/mysql
    healthcheck:
      test: ["CMD", "mysqladmin", "ping", "--silent"]
      interval: 10s
      timeout: 5s
      retries: 5

  app:
    build: .
    ports:
      - "8080:8080"
    environment:
      DB_URL: "jdbc:mysql://db:3306/springbootxnj6l?useUnicode=true&characterEncoding=utf-8&serverTimezone=GMT%2B8"
      DB_USERNAME: app_user
      DB_PASSWORD: app_password
    depends_on:
      db:
        condition: service_healthy

volumes:
  mysql_data:
```

**SQL Server 全套（应用 + 数据库）：**

```yaml
# docker-compose-sqlserver.yml
version: '3.8'

services:
  db:
    image: mcr.microsoft.com/mssql/server:2019-latest
    environment:
      ACCEPT_EULA: Y
      SA_PASSWORD: "YourStrong@Passw0rd"
    ports:
      - "1433:1433"
    volumes:
      - mssql_data:/var/opt/mssql
    healthcheck:
      test: /opt/mssql-tools/bin/sqlcmd -S localhost -U sa -P "$$SA_PASSWORD" -Q "SELECT 1"
      interval: 10s
      timeout: 5s
      retries: 10

  app:
    build: .
    ports:
      - "8080:8080"
    environment:
      DB_URL: "jdbc:sqlserver://db:1433;DatabaseName=springbootxnj6l"
      DB_USERNAME: sa
      DB_PASSWORD: "YourStrong@Passw0rd"
    depends_on:
      db:
        condition: service_healthy

volumes:
  mssql_data:
```

```bash
# 启动
docker-compose -f docker-compose-mysql.yml up -d
# 或
docker-compose -f docker-compose-sqlserver.yml up -d
```

---

## 4. 裸 Tomcat 部署

### 4.1 打 war 包

```bash
# MySQL 版 war
mvn clean package -P mysql -f pom-war.xml

# SQL Server 版 war
mvn clean package -P sqlserver -f pom-war.xml
```

产物：`target/springbootxnj6l.war`

### 4.2 部署步骤

```bash
# 1. 拷贝 war 到 Tomcat webapps
cp target/springbootxnj6l.war $CATALINA_HOME/webapps/

# 2. 传入数据库环境变量
#    方式一：在 $CATALINA_HOME/bin/setenv.sh 中设置
cat > $CATALINA_HOME/bin/setenv.sh << 'EOF'
export DB_URL="jdbc:mysql://db-host:3306/springbootxnj6l?useUnicode=true&characterEncoding=utf-8&serverTimezone=GMT%2B8"
export DB_USERNAME=app_user
export DB_PASSWORD=prod_password
export CATALINA_OPTS="-Xms256m -Xmx512m"
EOF
chmod +x $CATALINA_HOME/bin/setenv.sh

# 3. 启动 Tomcat
$CATALINA_HOME/bin/startup.sh
```

> **注意**：war 包部署后，context-path 由 war 文件名决定（即 `/springbootxnj6l`），与 `application.yml` 中的 `server.servlet.context-path` 一致。如果想改部署路径，需同时修改 war 文件名和 `application.yml`。

### 4.3 Tomcat 版本要求

- Spring Boot 2.2.2 内嵌 Tomcat 9.0.x
- 外部 Tomcat 建议使用 **9.0.x**（支持 Servlet 4.0，兼容 Java 8）
- 不建议使用 Tomcat 10+（Jakarta EE 命名空间变更，与 Spring Boot 2.x 不兼容）

---

## 5. MySQL 与 SQL Server 切换

### 5.1 打包时切换（推荐）

```bash
# 切换到 MySQL
mvn clean package -P mysql

# 切换到 SQL Server
mvn clean package -P sqlserver
```

### 5.2 运行时覆盖（不重新打包）

jar 包已经打好了，但想临时改数据库连接：

```bash
java -jar target/springbootxnj6l.jar \
  --spring.datasource.url="jdbc:sqlserver://new-host:1433;DatabaseName=springbootxnj6l" \
  --spring.datasource.driverClassName=com.microsoft.sqlserver.jdbc.SQLServerDriver \
  --spring.datasource.username=sa \
  --spring.datasource.password=new_password
```

> **警告**：运行时切换时 `driverClassName` 和 `url` 必须同时改，否则会连接失败。

### 5.3 field-strategy 说明

`application.yml` 中 `mybatis-plus.global-config.field-strategy: 1` 表示 **非 NULL 判断**策略——UPDATE 操作只更新非 null 字段。此配置对 MySQL 和 SQL Server 均相同，**无需按数据库修改**。

---

## 6. WuzichukuController 统计接口 — 反代路径示例

### 6.1 接口清单

| 接口 | 路径 | 说明 |
|------|------|------|
| 按值统计 | `GET /springbootxnj6l/wuzichuku/value/{xColumnName}/{yColumnName}` | 按 X 列分组，SUM Y 列 |
| 按值统计（时间） | `GET /springbootxnj6l/wuzichuku/value/{xColumnName}/{yColumnName}/{timeStatType}` | timeStatType: 日/月/年 |
| 分组统计 | `GET /springbootxnj6l/wuzichuku/group/{columnName}` | 按列分组，COUNT(1) |

### 6.2 Nginx 反代配置

```nginx
server {
    listen 80;
    server_name emergency.your-org.cn;

    # 前端静态文件
    location / {
        root /var/www/emergency-admin;
        index index.html;
        try_files $uri $uri/ /index.html;
    }

    # 后端 API 代理
    location /springbootxnj6l/ {
        proxy_pass http://127.0.0.1:8080/springbootxnj6l/;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;

        # 文件上传大小限制（与 application.yml 中 300MB 保持一致）
        client_max_body_size 300m;
    }
}
```

### 6.3 反代后的统计接口完整 URL

```
# 按物资分类统计出库数量
https://emergency.your-org.cn/springbootxnj6l/wuzichuku/value/wuzifenlei/wuzishuliang

# 按日期统计出库总量（按日）
https://emergency.your-org.cn/springbootxnj6l/wuzichuku/value/chukuriqi/wuzishuliang/日

# 按日期统计出库总量（按月）
https://emergency.your-org.cn/springbootxnj6l/wuzichuku/value/chukuriqi/wuzishuliang/月

# 按出库类型分组统计
https://emergency.your-org.cn/springbootxnj6l/wuzichuku/group/chukuleixing
```

### 6.4 已知限制

统计接口中的时间聚合使用了 MySQL 专有函数 `DATE_FORMAT()`，低库存预警使用了 `GROUP_CONCAT()`。这些 SQL 在 SQL Server profile 下**会运行时报错**。如需支持 SQL Server，需修改对应 mapper XML 中的 SQL 方言——这超出了构建/配置层面的范围。

涉及的 mapper 文件：
- `WuzichukuDao.xml` — `selectTimeStatValue`
- `WuzirukuDao.xml` — `selectTimeStatValue`
- `WuzixinxiDao.xml` — `selectTimeStatValue`, `selectLowStockAlert`
- `CommonDao.xml` — `selectTimeStatValue`, `remindCount`（`str_to_date`）
- `KucunDao.xml` — `selectLowStockAlert`（`GROUP_CONCAT`）

---

## 7. CI 流水线

项目配置了 GitHub Actions（`.github/workflows/ci.yml`），每次 push / PR 自动执行：

| Job | Profile | 数据库 | 测试 |
|-----|---------|--------|------|
| `build-mysql` | `-P mysql` | MySQL 5.7 service container | contextLoads 冒烟测试 |
| `build-sqlserver` | `-P sqlserver` | SQL Server 2019 service container | contextLoads 冒烟测试 |

### CI 限制

1. **仅冒烟测试**：CI 中只跑 `SpringbootSchemaApplicationTests.contextLoads()` 验证 Spring 容器能正常启动。`KucunServiceTest`（10 场景集成测试）因依赖预建表结构，不在 CI 中执行。
2. **未使用 H2 内存数据库**：H2 的 MySQL 兼容模式不支持 `DATE_FORMAT()`、`GROUP_CONCAT()`、`str_to_date()` 等函数；SQL Server 兼容模式差距更大。因此 CI 使用真实数据库的 service container。
3. **未使用 Testcontainers**：当前 Spring Boot 2.2 + Java 8 环境下 Testcontainers SQL Server 模块支持有限；MySQL 可用但需 Docker-in-Docker，不如 service container 简洁。
4. **统计接口测试缺失**：由于 mapper XML 中的 MySQL 专有函数，SQL Server profile 下统计接口无法通过冒烟测试中的请求触发测试。
