# MySQL / SQL Server 双环境配置说明

> **文档用途**：说明本项目如何同时支持 MySQL 和 SQL Server 两种数据库环境，以及如何切换。
> **生成日期**：2026-06-13
> **对应代码版本**：springbootxnj6l（Spring Boot 2.2.2 + MyBatis-Plus 2.3）

---

## 一、整体架构

本项目采用 **Maven Profile + Spring Profile** 双层机制实现数据库环境切换：

```
                        pom.xml
                     Maven profiles
                    /              \
              -Pmysql          -Psqlserver
                 |                  |
     sets activatedProfile=mysql  sets activatedProfile=sqlserver
                 |                  |
                 v                  v
        application.yml 中 spring.profiles.active = @activatedProfile@
                 |                  |
                 v                  v
      application-mysql.yml   application-sqlserver.yml
      (MySQL 数据源配置)      (SQL Server 数据源配置)
```

**核心思路**：
1. Maven 在打包时根据 `-P` 参数选择 profile，将 `activatedProfile` 属性值注入 `application.yml` 的 `spring.profiles.active`
2. Spring Boot 启动时根据 `spring.profiles.active` 加载对应的 `application-{profile}.yml` 数据源配置
3. 与数据库无关的公共配置放在 `application.yml` 中，所有环境共享

---

## 二、配置文件详解

### 2.1 application.yml — 公共配置（与数据库无关）

**文件位置**：`src/main/resources/application.yml`

```yaml
server:
    tomcat:
        uri-encoding: UTF-8
    port: 8080
    servlet:
        context-path: /springbootxnj6l

spring:
    profiles:
        active: @activatedProfile@      # <-- Maven 构建时注入
    servlet:
      multipart:
        max-file-size: 300MB
        max-request-size: 300MB

mybatis-plus:
  mapper-locations: classpath*:mapper/*.xml
  typeAliasesPackage: com.entity
  global-config:
    id-type: 1                          # 用户输入ID（非自增）
    db-column-underline: true           # 驼峰转下划线
    refresh-mapper: true
    logic-delete-value: -1              # 逻辑删除标记
    logic-not-delete-value: 0
    sql-injector: com.baomidou.mybatisplus.mapper.LogicSqlInjector
  configuration:
    map-underscore-to-camel-case: true
    cache-enabled: false
    call-setters-on-nulls: true
    jdbc-type-for-null: 'null'
```

**关键注释说明：**

| 配置项 | 值 | 说明 |
|--------|-----|------|
| `spring.profiles.active` | `@activatedProfile@` | Maven resource filtering 占位符，构建时替换为 `mysql` 或 `sqlserver` |
| `id-type` | `1` | 主键策略为"用户输入ID"，代码中通过 `new Date().getTime() + random` 生成 |
| `db-column-underline` | `true` | 开启驼峰转下划线（但本项目数据库列名实际与 Java 字段名一致，均为小写无下划线） |
| `logic-delete-value` | `-1` | 逻辑删除：删除操作实际执行 `UPDATE SET ... = -1` |
| `jdbc-type-for-null` | `'null'` | MyBatis 处理 null 值时使用 `JdbcType.NULL`，避免 Oracle 等数据库的 `Other` 类型问题 |

> **注意**：`application.yml` 中 **不包含** `spring.datasource` 配置块。数据源配置完全由 profile 文件提供。`field-strategy`（字段更新策略）也由 profile 文件单独指定。

### 2.2 application-mysql.yml — MySQL 环境

**文件位置**：`src/main/resources/application-mysql.yml`

```yaml
spring:
    datasource:
        driverClassName: com.mysql.cj.jdbc.Driver
        url: ${DB_URL:jdbc:mysql://127.0.0.1:3306/springbootxnj6l?useUnicode=true&characterEncoding=utf-8&useJDBCCompliantTimezoneShift=true&useLegacyDatetimeCode=false&serverTimezone=GMT%2B8}
        username: ${DB_USERNAME:root}
        password: ${DB_PASSWORD:123456}

mybatis-plus:
  global-config:
    field-strategy: 1    # NOT_NULL — update 时忽略 null 字段
```

**配置项说明：**

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| `driverClassName` | `com.mysql.cj.jdbc.Driver` | MySQL 8.x JDBC 驱动（向下兼容 MySQL 5.x） |
| `url` | `jdbc:mysql://127.0.0.1:3306/springbootxnj6l?...` | 本地开发默认连接。生产环境通过 `DB_URL` 环境变量覆盖 |
| `username` | `root` | 通过 `DB_USERNAME` 环境变量覆盖 |
| `password` | `123456` | 通过 `DB_PASSWORD` 环境变量覆盖 |
| `field-strategy` | `1`（NOT_NULL） | MyBatis-Plus 更新策略：字段为 null 时不纳入 UPDATE 语句 |

**JDBC URL 参数说明：**

| 参数 | 值 | 作用 |
|------|-----|------|
| `useUnicode` | `true` | 启用 Unicode |
| `characterEncoding` | `utf-8` | 字符编码 |
| `useJDBCCompliantTimezoneShift` | `true` | JDBC 兼容时区转换 |
| `useLegacyDatetimeCode` | `false` | 使用新版日期时间处理 |
| `serverTimezone` | `GMT+8` | 服务器时区（中国标准时间） |

### 2.3 application-sqlserver.yml — SQL Server 环境

**文件位置**：`src/main/resources/application-sqlserver.yml`

```yaml
spring:
    datasource:
        driverClassName: com.microsoft.sqlserver.jdbc.SQLServerDriver
        url: ${DB_URL:jdbc:sqlserver://127.0.0.1:1433;DatabaseName=springbootxnj6l}
        username: ${DB_USERNAME:sa}
        password: ${DB_PASSWORD:123456}

mybatis-plus:
  global-config:
    field-strategy: 2    # NOT_EMPTY — update 时忽略 null 和空字符串
```

**配置项说明：**

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| `driverClassName` | `com.microsoft.sqlserver.jdbc.SQLServerDriver` | SQL Server JDBC 驱动（`mssql-jdbc 6.2.0.jre8`） |
| `url` | `jdbc:sqlserver://127.0.0.1:1433;DatabaseName=springbootxnj6l` | 本地开发默认连接 |
| `username` | `sa` | SQL Server 默认管理员账户 |
| `password` | `123456` | 通过 `DB_PASSWORD` 环境变量覆盖 |
| `field-strategy` | `2`（NOT_EMPTY） | 更新策略：字段为 null **或空字符串** 时不纳入 UPDATE。选择 NOT_EMPTY 是为了避免 SQL Server 中空字符串与 NULL 的语义混淆 |

### 2.4 application-test.yml — CI 冒烟测试（H2 内存库）

**文件位置**：`src/main/resources/application-test.yml`

```yaml
spring:
    datasource:
        driverClassName: org.h2.Driver
        url: jdbc:h2:mem:testdb;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=FALSE
        username: sa
        password:                    # 空密码

mybatis-plus:
  global-config:
    field-strategy: 1
```

**用途**：仅用于 CI 构建时验证 Spring 上下文能否正常启动，**不替代真实数据库测试**。

**限制**：
- H2 的 `MODE=MySQL` 仅模拟部分 MySQL 语法，不覆盖 SQL Server 方言
- MyBatis XML 中的数据库专有函数（如 MySQL 的 `DATE_FORMAT()`、`GROUP_CONCAT()`）在 H2 中可能失败
- `KucunServiceTest`（需要真实数据库的集成测试）在 CI 中被 surefire 排除

---

## 三、Maven Profile 机制详解

### 3.1 pom.xml 中的 profiles 定义

```xml
<profiles>
    <!-- MySQL (默认) -->
    <profile>
        <id>mysql</id>
        <activation>
            <activeByDefault>true</activeByDefault>     <!-- 不指定 -P 时默认使用 -->
        </activation>
        <properties>
            <activatedProfile>mysql</activatedProfile>   <!-- 注入到 application.yml -->
        </properties>
        <dependencies>
            <dependency>
                <groupId>mysql</groupId>
                <artifactId>mysql-connector-java</artifactId>
                <scope>runtime</scope>
            </dependency>
        </dependencies>
    </profile>

    <!-- SQL Server -->
    <profile>
        <id>sqlserver</id>
        <properties>
            <activatedProfile>sqlserver</activatedProfile>
        </properties>
        <dependencies>
            <dependency>
                <groupId>com.microsoft.sqlserver</groupId>
                <artifactId>mssql-jdbc</artifactId>
                <version>6.2.0.jre8</version>
                <scope>runtime</scope>
            </dependency>
        </dependencies>
    </profile>
</profiles>
```

**工作原理：**

1. 每个 profile 定义一个 `<activatedProfile>` 属性
2. `application.yml` 中的 `spring.profiles.active: @activatedProfile@` 在 Maven 构建时被替换
3. 每个 profile 引入对应的 JDBC 驱动 jar（`mysql-connector-java` 或 `mssql-jdbc`）
4. MySQL profile 设置了 `<activeByDefault>true</activeByDefault>`，不指定 `-P` 时默认走 MySQL

### 3.2 field-strategy 差异原因

| Profile | field-strategy | 值 | 选择原因 |
|---------|---------------|-----|---------|
| MySQL | NOT_NULL | 1 | MySQL 中空字符串 `""` 与 `NULL` 有明确区分，更新时只跳过 null 即可 |
| SQL Server | NOT_EMPTY | 2 | SQL Server 中空字符串与 NULL 语义容易混淆（尤其是 `char`/`varchar` 类型），跳过 null 和空字符串更安全 |

**影响范围**：`field-strategy` 控制 MyBatis-Plus 的 `updateById()` 方法生成 UPDATE SQL 时的字段过滤策略：
- `NOT_NULL(1)`：字段值为 null 时不纳入 SET 子句
- `NOT_EMPTY(2)`：字段值为 null 或空字符串 `""` 时不纳入 SET 子句

---

## 四、切换方式速查

### 4.1 本地开发切换

```bash
# MySQL（默认，无需指定 -P）
cd springbootxnj6l
mvn spring-boot:run

# 或显式指定
mvn spring-boot:run -Pmysql

# SQL Server
mvn spring-boot:run -Psqlserver
```

### 4.2 打包部署切换

```bash
# MySQL 环境打包
cd springbootxnj6l
mvn clean package -Pmysql
# 产物: target/springbootxnj6l.jar

# SQL Server 环境打包
mvn clean package -Psqlserver
# 产物: target/springbootxnj6l.jar
```

### 4.3 生产环境覆盖数据库连接

通过环境变量覆盖默认值，避免在配置文件中硬编码生产密码：

```bash
# MySQL 生产环境
export DB_URL="jdbc:mysql://prod-db.example.com:3306/emergency_materials?useUnicode=true&characterEncoding=utf-8&serverTimezone=GMT%2B8"
export DB_USERNAME="app_user"
export DB_PASSWORD="strong_password_here"
java -jar springbootxnj6l.jar

# SQL Server 生产环境
export DB_URL="jdbc:sqlserver://prod-db.example.com:1433;DatabaseName=emergency_materials"
export DB_USERNAME="app_user"
export DB_PASSWORD="strong_password_here"
java -jar springbootxnj6l.jar
```

### 4.4 Docker 部署切换

```dockerfile
# Dockerfile 示例 — 通过构建参数选择 profile
ARG DB_PROFILE=mysql
COPY springbootxnj6l/target/springbootxnj6l.jar /app/app.jar
CMD ["java", "-jar", "/app/app.jar"]
```

```bash
# MySQL 容器
docker run -e DB_URL="jdbc:mysql://mysql-host:3306/db" \
           -e DB_USERNAME="root" \
           -e DB_PASSWORD="pass" \
           app:latest

# SQL Server 容器
docker run -e DB_URL="jdbc:sqlserver://sqlserver-host:1433;DatabaseName=db" \
           -e DB_USERNAME="sa" \
           -e DB_PASSWORD="pass" \
           app:latest
```

---

## 五、CI 构建矩阵

项目使用 GitHub Actions 进行双数据库 profile 并行构建：

```yaml
# .github/workflows/ci.yml
jobs:
  build:
    strategy:
      matrix:
        db-profile: [ mysql, sqlserver ]     # 两个 profile 并行构建
    steps:
      - name: Build & smoke-test
        run: mvn clean verify -P${{ matrix.db-profile }} -B
```

**CI 流程**：
1. 分别用 `-Pmysql` 和 `-Psqlserver` 各构建一次
2. 运行 `maven-surefire-plugin`，自动激活 `test` profile（H2 内存库）
3. 排除 `KucunServiceTest.java`（需要真实数据库的集成测试）
4. 验证产物 JAR 文件正确生成
5. 上传构建产物（保留 7 天）

---

## 六、SQL 兼容性注意事项

由于项目同时支持 MySQL 和 SQL Server，以下 SQL 语法差异需要注意：

### 6.1 当前代码中使用的 MySQL 专有语法

| 位置 | SQL 语法 | MySQL | SQL Server 替代 |
|------|---------|-------|----------------|
| `KucunDao.xml` — `selectLowStockAlert` | `GROUP_CONCAT(wuzimingcheng SEPARATOR ',')` | 支持 | 需改用 `STRING_AGG(wuzimingcheng, ',')` 或 `FOR XML PATH` |
| `WuzichukuDao.xml` — `selectTimeStatValue` | `DATE_FORMAT(col, '%Y-%m-%d')` | 支持 | 需改用 `FORMAT(col, 'yyyy-MM-dd')` 或 `CONVERT` |
| `WuzixinxiDao.xml` — `selectTimeStatValue` | `DATE_FORMAT(col, '%Y-%m-%d')` | 支持 | 同上 |
| `KucunDao.xml` — `selectForUpdate` | `SELECT ... FOR UPDATE` | 支持 | SQL Server 需改用 `WITH (UPDLOCK, ROWLOCK)` |

### 6.2 实际影响评估

> **当前状态**：项目代码中的 Mapper XML **直接使用了 MySQL 专有语法**（`GROUP_CONCAT`、`DATE_FORMAT`、`FOR UPDATE`），在 SQL Server 环境下运行会报错。

**这意味着**：
- MySQL profile 打包的 JAR 可以正常运行
- SQL Server profile 打包的 JAR **编译通过但运行时会因 SQL 语法不兼容而报错**
- CI 的 H2 冒烟测试（`MODE=MySQL`）无法发现这些兼容性问题
- 如需真正支持 SQL Server，需要：
  - 为 SQL Server 编写独立的 Mapper XML（使用 `<if>` 或 `<choose>` 按数据库方言分支）
  - 或使用 MyBatis 的 `DatabaseIdProvider` 机制按数据库类型自动选择 SQL

---

## 七、配置切换总结表

| 维度 | MySQL | SQL Server | CI 测试 (H2) |
|------|-------|-----------|--------------|
| **Maven profile** | `-Pmysql`（默认） | `-Psqlserver` | surefire 自动激活 test |
| **Spring profile** | `mysql` | `sqlserver` | `test` |
| **配置文件** | `application-mysql.yml` | `application-sqlserver.yml` | `application-test.yml` |
| **JDBC 驱动** | `mysql-connector-java` | `mssql-jdbc 6.2.0.jre8` | H2 (test scope) |
| **默认端口** | 3306 | 1433 | -- |
| **默认库名** | `springbootxnj6l` | `springbootxnj6l` | `testdb` (内存) |
| **默认用户** | `root` | `sa` | `sa` |
| **field-strategy** | `1` (NOT_NULL) | `2` (NOT_EMPTY) | `1` (NOT_NULL) |
| **SQL 兼容性** | 完全兼容 | `GROUP_CONCAT`/`DATE_FORMAT`/`FOR UPDATE` 不兼容 | 部分 MySQL 语法兼容 |
| **环境变量** | `DB_URL` / `DB_USERNAME` / `DB_PASSWORD` | 同左 | 无需设置 |
