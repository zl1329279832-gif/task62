# MySQL / SQL Server 双环境配置说明

> 编写日期：2026-06-13
> 适用系统：应急救援物资管理系统（springbootxnj6l）
> 代码分支：claude-documentation

---

## 一、概述

本系统支持 **MySQL** 和 **SQL Server** 两种数据库环境运行。切换机制为 **Maven Profile + Spring Profile** 双层联动：

- **构建时**：Maven Profile 控制打入哪个 JDBC 驱动 JAR 包
- **运行时**：Spring Profile 激活对应的 YAML 配置文件，指定连接串和参数

两套环境的切换**不需要修改任何代码**，仅通过构建命令即可完成。

---

## 二、配置文件结构

```
springbootxnj6l/src/main/resources/
├── application.yml              ← 公共配置（与数据库无关）
├── application-mysql.yml        ← MySQL 数据源配置
├── application-sqlserver.yml    ← SQL Server 数据源配置
└── application-test.yml         ← CI 测试用 H2 内存库
```

| 文件 | 职责 | 激活条件 |
|------|------|----------|
| `application.yml` | Server 端口、MyBatis-Plus 通用配置、文件上传限制 | 始终加载 |
| `application-mysql.yml` | MySQL 驱动、连接串、field-strategy=1 | `mvn package -Pmysql` |
| `application-sqlserver.yml` | SQL Server 驱动、连接串、field-strategy=2 | `mvn package -Psqlserver` |
| `application-test.yml` | H2 内存库（MODE=MySQL） | CI 自动测试 |

---

## 三、切换方式：Maven 构建命令

### 3.1 构建 MySQL 版本（默认）

```bash
cd springbootxnj6l
mvn clean package -Pmysql
```

或直接（`mysql` 为默认激活 Profile）：

```bash
mvn clean package
```

### 3.2 构建 SQL Server 版本

```bash
cd springbootxnj6l
mvn clean package -Psqlserver
```

### 3.3 原理说明

`pom.xml` 底部定义了两个 `<profile>`：

```xml
<profiles>
    <!-- MySQL (默认) -->
    <profile>
        <id>mysql</id>
        <activation>
            <activeByDefault>true</activeByDefault>
        </activation>
        <properties>
            <activatedProfile>mysql</activatedProfile>
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

Maven 资源过滤会将 `application.yml` 中的占位符 `@activatedProfile@` 替换为实际 Profile 名称：

```yaml
# application.yml 第 16 行
spring:
    profiles:
        active: @activatedProfile@
```

构建后，JAR 包内 `application.yml` 的这一行变为：
- `-Pmysql` → `active: mysql` → 加载 `application-mysql.yml`
- `-Psqlserver` → `active: sqlserver` → 加载 `application-sqlserver.yml`

---

## 四、两套环境配置对照

### 4.1 application-mysql.yml（完整内容）

```yaml
# MySQL 数据源 — 由 Maven profile "mysql" 激活
# 打包命令: mvn clean package -Pmysql
#
# 环境变量说明（均有本地开发默认值，生产环境务必覆盖）:
#   DB_URL       — JDBC 连接串
#   DB_USERNAME  — 数据库用户名
#   DB_PASSWORD  — 数据库密码

spring:
    datasource:
        driverClassName: com.mysql.cj.jdbc.Driver
        url: ${DB_URL:jdbc:mysql://127.0.0.1:3306/springbootxnj6l?useUnicode=true&characterEncoding=utf-8&useJDBCCompliantTimezoneShift=true&useLegacyDatetimeCode=false&serverTimezone=GMT%2B8}
        username: ${DB_USERNAME:root}
        password: ${DB_PASSWORD:123456}

mybatis-plus:
  global-config:
    # 字段策略 0:"忽略判断",1:"非 NULL 判断",2:"非空判断"
    # MySQL 推荐 NOT_NULL(1)
    field-strategy: 1
```

### 4.2 application-sqlserver.yml（完整内容）

```yaml
# SQL Server 数据源 — 由 Maven profile "sqlserver" 激活
# 打包命令: mvn clean package -Psqlserver
#
# 环境变量说明（均有本地开发默认值，生产环境务必覆盖）:
#   DB_URL       — JDBC 连接串
#   DB_USERNAME  — 数据库用户名
#   DB_PASSWORD  — 数据库密码

spring:
    datasource:
        driverClassName: com.microsoft.sqlserver.jdbc.SQLServerDriver
        url: ${DB_URL:jdbc:sqlserver://127.0.0.1:1433;DatabaseName=springbootxnj6l}
        username: ${DB_USERNAME:sa}
        password: ${DB_PASSWORD:123456}

mybatis-plus:
  global-config:
    # 字段策略 0:"忽略判断",1:"非 NULL 判断",2:"非空判断"
    # SQL Server 推荐 NOT_EMPTY(2)，避免空串与 NULL 混淆
    field-strategy: 2
```

### 4.3 关键差异对照表

| 配置项 | MySQL | SQL Server | 差异原因 |
|--------|-------|------------|----------|
| `driverClassName` | `com.mysql.cj.jdbc.Driver` | `com.microsoft.sqlserver.jdbc.SQLServerDriver` | 不同厂商驱动 |
| JDBC URL 格式 | `jdbc:mysql://host:3306/db?params` | `jdbc:sqlserver://host:1433;DatabaseName=db` | 协议规范不同 |
| 默认端口 | 3306 | 1433 | 数据库默认端口 |
| 默认用户 | `root` | `sa` | 数据库超级用户命名 |
| `field-strategy` | `1`（NOT_NULL） | `2`（NOT_EMPTY） | 见下方说明 |
| Maven 驱动依赖 | `mysql:mysql-connector-java` | `com.microsoft.sqlserver:mssql-jdbc:6.2.0.jre8` | — |

---

## 五、field-strategy 差异说明

MyBatis-Plus 的 `field-strategy` 控制 INSERT/UPDATE 时是否忽略某些字段值：

| 值 | 策略名 | 行为 | 适用场景 |
|----|--------|------|----------|
| 0 | 忽略判断 | 所有字段都参与 SQL，包括 null 值 | 不推荐 |
| **1** | **NOT_NULL** | 值为 `null` 的字段不参与 SQL | MySQL（空串是合法值） |
| **2** | **NOT_EMPTY** | 值为 `null` 或空串 `""` 的字段不参与 SQL | SQL Server |

**为什么 SQL Server 用 2（NOT_EMPTY）？**

SQL Server 对空串 `""` 和 `NULL` 的处理与 MySQL 不同。在 MySQL 中 `'' != NULL`，空串是有意义的值。但 SQL Server 在某些排序规则（collation）下可能将空串视为特殊值，且 MyBatis-Plus 2.x 版本在 SQL Server 环境下对空串的处理存在已知兼容性问题。使用 `field-strategy: 2` 可以避免将无意义的空串写入数据库字段。

---

## 六、环境变量覆盖（生产部署）

两套配置文件都支持通过环境变量覆盖默认值，生产环境**务必**设置以下环境变量：

| 环境变量 | 说明 | MySQL 默认值 | SQL Server 默认值 |
|----------|------|-------------|-------------------|
| `DB_URL` | JDBC 连接串 | `jdbc:mysql://127.0.0.1:3306/springbootxnj6l?...` | `jdbc:sqlserver://127.0.0.1:1433;DatabaseName=springbootxnj6l` |
| `DB_USERNAME` | 数据库用户名 | `root` | `sa` |
| `DB_PASSWORD` | 数据库密码 | `123456` | `123456` |

### 6.1 Linux / Docker 环境设置示例

```bash
# MySQL 生产环境
export DB_URL="jdbc:mysql://db-prod.internal:3306/springbootxnj6l?useUnicode=true&characterEncoding=utf-8&serverTimezone=GMT%2B8"
export DB_USERNAME="app_user"
export DB_PASSWORD="<生产密码>"
java -jar springbootxnj6l-0.0.1-SNAPSHOT.jar

# SQL Server 生产环境
export DB_URL="jdbc:sqlserver://sqlsrv-prod.internal:1433;DatabaseName=springbootxnj6l"
export DB_USERNAME="app_user"
export DB_PASSWORD="<生产密码>"
java -jar springbootxnj6l-0.0.1-SNAPSHOT.jar
```

### 6.2 JVM 参数覆盖 Profile（不重新构建）

如果需要在不重新打包的情况下切换 Profile（例如 WAR 部署到 Tomcat）：

```bash
# 方式一：JVM 参数
java -Dspring.profiles.active=sqlserver -jar springbootxnj6l.jar

# 方式二：Tomcat CATALINA_OPTS
export CATALINA_OPTS="-Dspring.profiles.active=sqlserver"
```

> ⚠️ **注意**：仅覆盖 Spring Profile 不会改变 JAR 包中已打入的 JDBC 驱动依赖。如果用 `-Pmysql` 构建的包通过 JVM 参数强制切换到 `sqlserver` Profile，启动时会因缺少 `mssql-jdbc` 驱动而报 `ClassNotFoundException`。正确做法是用对应 Profile 重新构建，或手动将驱动 JAR 放入 classpath。

---

## 七、旧版切换方式（已废弃）

项目早期版本（可在 `target/classes/application.yml` 中看到残留）使用**手动注释/取消注释**的方式切换数据库：

```yaml
spring:
    datasource:
        # === 当前激活 MySQL ===
        driverClassName: com.mysql.cj.jdbc.Driver
        url: jdbc:mysql://127.0.0.1:3306/springbootxnj6l?...
        username: root
        password: 123456

        # === 注释掉的 SQL Server（需要时取消注释并注释上方） ===
#        driverClassName: com.microsoft.sqlserver.jdbc.SQLServerDriver
#        url: jdbc:sqlserver://127.0.0.1:1433;DatabaseName=springbootxnj6l
#        username: sa
#        password: 123456
```

**此方式的问题**：
1. 需要修改源码提交，不适合 CI/CD
2. `field-strategy` 无法区分，MySQL 和 SQL Server 共用同一个值
3. 密码硬编码在配置文件中，不支持环境变量覆盖
4. 两套驱动同时打包进 JAR，体积浪费

**当前方案已完全替代此旧方式**。如在 `target/` 目录中发现旧格式配置文件，系构建缓存残留，不影响实际运行。

---

## 八、SQL 兼容性已知问题

当前 Mapper XML 中的部分 SQL 使用了 MySQL 特有函数，**SQL Server 环境下会报错**：

| 文件 | 涉及 SQL | MySQL 函数 | SQL Server 等效 | 影响的 API |
|------|----------|-----------|-----------------|-----------|
| `WuzichukuDao.xml` | `selectTimeStatValue` | `DATE_FORMAT(col, '%Y-%m-%d')` | `FORMAT(col, 'yyyy-MM-dd')` 或 `CONVERT(VARCHAR(10), col, 120)` | `/wuzichuku/value/.../日\|月\|年` |
| `WuzirukuDao.xml` | `selectTimeStatValue` | `DATE_FORMAT(col, '%Y-%m-%d')` | 同上 | `/wuziruku/value/.../日\|月\|年` |
| `WuzixinxiDao.xml` | `selectTimeStatValue` | `DATE_FORMAT(col, '%Y-%m-%d')` | 同上 | `/wuzixinxi/value/.../日\|月\|年` |
| `KucunDao.xml` | `selectLowStockAlert` | `GROUP_CONCAT(col SEPARATOR ',')` | `STRING_AGG(col, ',')` (SQL Server 2017+) | `/wuzixinxi/lowStockAlert` |
| `WuzixinxiDao.xml` | `selectLowStockAlert` | `GROUP_CONCAT(col SEPARATOR ',')` | `STRING_AGG(col, ',')` | `/wuzixinxi/lowStockAlert` |

**现状**：代码中未做方言适配（如 MyBatis 的 `<if>` + `databaseId` 或 DatabaseIdProvider），SQL Server 环境下上述 5 个统计接口不可用。基础 CRUD 及库存联动逻辑不受影响。

---

## 九、CI 测试环境（application-test.yml）

CI 使用 H2 内存库运行冒烟测试，Maven Surefire 插件自动强制激活 test Profile：

```xml
<!-- pom.xml surefire 配置 -->
<argLine>-Dspring.profiles.active=test</argLine>
```

H2 配置：
- 驱动：`org.h2.Driver`
- URL：`jdbc:h2:mem:testdb;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=FALSE`
- `MODE=MySQL` 仅部分模拟 MySQL 语法，不覆盖 SQL Server 方言

> 已知限制：`KucunServiceTest.java` 因依赖真实数据库的 `SELECT ... FOR UPDATE` 悲观锁语义，已在 Surefire 中排除（`<exclude>**/KucunServiceTest.java</exclude>`）。

---

## 十、操作清单速查

| 场景 | 命令 |
|------|------|
| 本地开发（MySQL 默认） | `mvn spring-boot:run` |
| 本地开发（SQL Server） | `mvn spring-boot:run -Psqlserver` |
| 打包 MySQL 版本 | `mvn clean package -Pmysql` |
| 打包 SQL Server 版本 | `mvn clean package -Psqlserver` |
| 运行时覆盖数据库地址 | 设置环境变量 `DB_URL`、`DB_USERNAME`、`DB_PASSWORD` |
| 运行时强制切换 Profile | `-Dspring.profiles.active=sqlserver`（需确保驱动在 classpath） |
| CI 测试 | 自动使用 H2（`-Dspring.profiles.active=test`） |
