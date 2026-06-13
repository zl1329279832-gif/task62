# 部署指南 — 应急救援物资管理系统

## 1. 构建

项目通过 **Maven Profile** 区分 MySQL 与 SQL Server，打包产物名称始终为 `springbootxnj6l.jar`（或 `.war`），`context-path` 始终为 `/springbootxnj6l`。

```bash
# MySQL 版本（默认 profile）
cd springbootxnj6l
mvn clean package -Pmysql

# SQL Server 版本
mvn clean package -Psqlserver

# 跳过测试（仅打包）
mvn clean package -Pmysql -DskipTests
```

> **field-strategy 说明**：MySQL 使用 `field-strategy: 1`（NOT_NULL），SQL Server 使用 `field-strategy: 2`（NOT_EMPTY）。
> 该值已锁定在各自的 `application-mysql.yml` / `application-sqlserver.yml` 中，**无需手工修改**。

---

## 2. 环境变量

以下环境变量在运行时覆盖默认值（开发默认值仅供本地调试）：

| 变量名 | 说明 | MySQL 默认值 | SQL Server 默认值 |
|--------|------|-------------|-------------------|
| `DB_URL` | JDBC 连接串 | `jdbc:mysql://127.0.0.1:3306/springbootxnj6l?useUnicode=true&characterEncoding=utf-8&...` | `jdbc:sqlserver://127.0.0.1:1433;DatabaseName=springbootxnj6l` |
| `DB_USERNAME` | 数据库用户名 | `root` | `sa` |
| `DB_PASSWORD` | 数据库密码 | `123456` | `123456` |

---

## 3. Docker 部署

### 3.1 构建镜像

```bash
# MySQL 版本
mvn clean package -Pmysql -DskipTests
docker build -t springbootxnj6l:mysql ./springbootxnj6l

# SQL Server 版本
mvn clean package -Psqlserver -DskipTests
docker build -t springbootxnj6l:sqlserver ./springbootxnj6l
```

### 3.2 运行容器

**MySQL 现场：**

```bash
docker run -d \
  --name yjj-mysql \
  -p 8080:8080 \
  -e DB_URL="jdbc:mysql://db-host:3306/springbootxnj6l?useUnicode=true&characterEncoding=utf-8&serverTimezone=GMT%2B8" \
  -e DB_USERNAME="app_user" \
  -e DB_PASSWORD="<生产密码>" \
  springbootxnj6l:mysql
```

**SQL Server 现场：**

```bash
docker run -d \
  --name yjj-sqlserver \
  -p 8080:8080 \
  -e DB_URL="jdbc:sqlserver://db-host:1433;DatabaseName=springbootxnj6l" \
  -e DB_USERNAME="app_user" \
  -e DB_PASSWORD="<生产密码>" \
  springbootxnj6l:sqlserver
```

### 3.3 切换数据库类型

如果需要从 MySQL 切换到 SQL Server（或反过来），**重新构建镜像**即可：

```bash
# 从 MySQL 切到 SQL Server
mvn clean package -Psqlserver -DskipTests
docker build -t springbootxnj6l:sqlserver ./springbootxnj6l
docker stop yjj-mysql && docker rm yjj-mysql
docker run -d --name yjj-sqlserver -p 8080:8080 \
  -e DB_URL="jdbc:sqlserver://db-host:1433;DatabaseName=springbootxnj6l" \
  -e DB_USERNAME="sa" \
  -e DB_PASSWORD="<密码>" \
  springbootxnj6l:sqlserver

# 从 SQL Server 切到 MySQL
mvn clean package -Pmysql -DskipTests
docker build -t springbootxnj6l:mysql ./springbootxnj6l
docker stop yjj-sqlserver && docker rm yjj-sqlserver
docker run -d --name yjj-mysql -p 8080:8080 \
  -e DB_URL="jdbc:mysql://db-host:3306/springbootxnj6l?useUnicode=true&characterEncoding=utf-8&serverTimezone=GMT%2B8" \
  -e DB_USERNAME="root" \
  -e DB_PASSWORD="<密码>" \
  springbootxnj6l:mysql
```

---

## 4. 裸 Tomcat 部署（WAR）

### 4.1 打 WAR 包

使用项目自带的 `pom-war.xml`：

```bash
# MySQL 版本
mvn clean package -f pom-war.xml -Pmysql -DskipTests

# SQL Server 版本
mvn clean package -f pom-war.xml -Psqlserver -DskipTests
```

> 注意：`pom-war.xml` 需要同步添加与 `pom.xml` 相同的 `<profiles>` 配置才能使用 `-P` 参数。
> 如果尚未同步，可在运行时通过 JVM 参数切换 Spring profile：

```bash
# 在 Tomcat 的 CATALINA_OPTS 或 setenv.sh 中指定
export CATALINA_OPTS="-Dspring.profiles.active=sqlserver"
```

### 4.2 部署到 Tomcat

```bash
# 复制 WAR 到 webapps
cp springbootxnj6l/target/springbootxnj6l.war $CATALINA_HOME/webapps/springbootxnj6l.war

# 设置环境变量（在 $CATALINA_HOME/bin/setenv.sh 中）
export DB_URL="jdbc:mysql://db-host:3306/springbootxnj6l?useUnicode=true&characterEncoding=utf-8&serverTimezone=GMT%2B8"
export DB_USERNAME="app_user"
export DB_PASSWORD="<生产密码>"

# 启动 Tomcat
$CATALINA_HOME/bin/startup.sh
```

### 4.3 Tomcat 下切换数据库

```bash
# 停止 Tomcat
$CATALINA_HOME/bin/shutdown.sh

# 方式一：重新打包（推荐）
mvn clean package -f pom-war.xml -Psqlserver -DskipTests
cp springbootxnj6l/target/springbootxnj6l.war $CATALINA_HOME/webapps/

# 方式二：JVM 参数覆盖（不重新打包）
# 编辑 setenv.sh，将 spring.profiles.active 改为 sqlserver 并更新 DB_URL
export CATALINA_OPTS="-Dspring.profiles.active=sqlserver"
export DB_URL="jdbc:sqlserver://db-host:1433;DatabaseName=springbootxnj6l"
export DB_USERNAME="sa"

# 启动
$CATALINA_HOME/bin/startup.sh
```

---

## 5. 反向代理配置（Nginx 示例）

应用的 `context-path` 为 `/springbootxnj6l`，反代时需保持路径一致。

### 5.1 基本反代

```nginx
upstream yjj_backend {
    server 127.0.0.1:8080;
}

server {
    listen 80;
    server_name yjj.example.com;

    location /springbootxnj6l/ {
        proxy_pass http://yjj_backend;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
```

### 5.2 WuzichukuController 统计接口路径示例

`WuzichukuController` 映射在 `/wuzichuku`，反代后完整路径如下：

| 接口 | 反代后完整 URL |
|------|---------------|
| 分页列表 | `http://yjj.example.com/springbootxnj6l/wuzichuku/page` |
| 按值统计 | `http://yjj.example.com/springbootxnj6l/wuzichuku/value/{x列}/{y列}` |
| 按时间统计 | `http://yjj.example.com/springbootxnj6l/wuzichuku/value/{x列}/{y列}/{timeStatType}` |
| 分组统计 | `http://yjj.example.com/springbootxnj6l/wuzichuku/group/{columnName}` |
| 详情 | `http://yjj.example.com/springbootxnj6l/wuzichuku/info/{id}` |
| 前端详情（免登录） | `http://yjj.example.com/springbootxnj6l/wuzichuku/detail/{id}` |
| 前端列表（免登录） | `http://yjj.example.com/springbootxnj6l/wuzichuku/list` |

**curl 测试示例：**

```bash
# 统计接口（需登录，携带 token）
curl -H "Token: <your-token>" \
  "http://yjj.example.com/springbootxnj6l/wuzichuku/group/chukuleixing"

# 前端列表（免登录）
curl "http://yjj.example.com/springbootxnj6l/wuzichuku/list?page=1&limit=10"
```

---

## 6. CI 冒烟测试说明

CI 流水线对 `mysql` 和 `sqlserver` 两个 profile 各执行一次 `mvn clean verify`。

**测试方案：H2 内存数据库**

- surefire 通过 `-Dspring.profiles.active=test` 激活 `application-test.yml`，使用 H2 `MODE=MySQL` 内存库
- 运行 `SpringbootSchemaApplicationTests.contextLoads()` 验证 Spring 上下文正常启动
- `KucunServiceTest` 需要真实数据库，已在 surefire 配置中排除

**已知限制：**

1. H2 `MODE=MySQL` 仅模拟部分 MySQL 语法，不支持 SQL Server 方言（如 `GETDATE()`、`TOP N` 等）
2. 无法验证 MyBatis XML 中数据库专有 SQL 的正确性
3. 冒烟测试仅证明应用能启动、依赖注入正常；SQL 兼容性需通过真实数据库的集成测试验证
4. 如需完整集成测试，可在 CI 中引入 Testcontainers（需 Docker-in-Docker 支持）替代 H2
