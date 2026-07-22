# Image Exhibition

Image Exhibition 是一个围绕图片数据展示、搜索、热榜和行为分析构建的 Spring Boot 后端项目。当前仓库主要包含后端服务 `lspbackend`，并通过 `docker-compose.yml` 编排 MongoDB、Redis、Kafka、Flink、前端服务以及图片抓取/下载相关服务。

## 项目要点

- 图片数据展示：从 MongoDB `imageurl` 集合读取图片记录，支持分页、排序、标签过滤和地区过滤。
- 图片详情接口：按图片 ID/标题获取图片详情，返回前端展示所需 DTO。
- 图片全文搜索：通过 Elasticsearch 提供关键词搜索，并支持国家/地区、时间等查询条件。
- 热门搜索词：记录用户搜索关键词，提供近 7 天热门搜索词接口。
- 图片热度排行：基于浏览埋点维护实时榜、日榜、周榜、月榜和历史榜单。
- 用户行为埋点：接收前端批量事件，将图片浏览、搜索等事件写入 Kafka，同时更新 Redis 热度数据。
- 索引管理：提供 Elasticsearch 索引重建、删除、去重、统计、健康检查等管理接口。
- 数据分析报表：通过 ClickHouse 查询运营概览、流量趋势、热门内容和关键词等分析数据；无数据时提供 mock 兜底。
- ETL 能力：包含从 MongoDB 抽取图片数据到数仓维表/明细层的批处理入口。
- 可观测性：集成 Spring Boot Actuator、Micrometer 和 Prometheus 指标暴露。
- 容器化部署：提供 Dockerfile 和 `docker-compose.yml`，用于本地或服务器部署完整链路。

## 技术栈

### 后端与基础框架

- Java 17
- Spring Boot 3.4.5
- Spring Web
- Spring Data MongoDB
- Spring Data Redis
- Spring JDBC
- Spring Kafka
- Spring Boot Actuator
- Lombok

### 数据存储与中间件

- MongoDB 5.0：存储图片 URL、标题、标签、地区、描述等核心业务数据。
- Redis 7：用于实时热度、榜单和搜索相关缓存/计数。
- Elasticsearch 8.14 Java Client：用于图片全文检索、索引管理和去重统计。
- Kafka 4.0：承接前端行为埋点事件流。
- ClickHouse：用于离线/准实时数据报表查询。
- Apache Flink 1.19.1：用于流式/批式数据处理任务环境。

### 工程与部署

- Maven：项目构建与依赖管理。
- Docker：后端服务镜像构建。
- Docker Compose：编排 MongoDB、Redis、Kafka、Flink、前后端和抓取下载服务。
- Micrometer + Prometheus：应用指标采集。
- JUnit 5 + Mockito：单元测试相关依赖。
- Apache HttpClient 5：HTTP 客户端能力。

## 目录结构

```text
.
├── Dockerfile
├── docker-compose.yml
├── dw_schema.sql
├── pom.xml
├── src
│   ├── main
│   │   ├── java/com/worker1/worker1
│   │   │   ├── config        # Elasticsearch、ClickHouse、RestTemplate 等配置
│   │   │   ├── controller    # 图片、搜索、热榜、埋点、ETL、报表和管理 API
│   │   │   ├── model         # API DTO 与搜索分页模型
│   │   │   ├── service       # 业务服务、索引服务、热榜服务、ETL 服务
│   │   │   └── store         # MongoDB Repository 与文档模型
│   │   └── resources
│   │       ├── application.properties
│   │       └── ipAddress.txt
│   └── test
│       └── java/com/worker1/worker1
└── target
```

## 核心数据模型

图片主数据模型为 `DbImageUrl`，对应 MongoDB collection：`imageurl`。

主要字段：

- `title`：图片记录 ID/标题。
- `imageUrl`：图片地址列表。
- `website`：来源站点。
- `labels`：标签列表，已建立索引。
- `country`：地区分类，已建立索引。
- `createdAt`：创建时间戳。
- `description`：描述文本，用于全文检索。
- `keywords`：标题或内容提取出的关键词。

项目还在 `labels + country` 上定义了 MongoDB 复合索引，用于标签和地区组合查询。

## 主要 API

### 图片列表与详情

- `GET /api/images/list`：分页获取全部图片。
- `GET /api/images/list/domestic`：分页获取 domestic 图片。
- `GET /api/images/list/asia`：分页获取 asia 图片。
- `GET /api/images/list/european`：分页获取 european 图片。
- `GET /api/images/{id}`：获取图片详情。
- `POST /api/images/`：创建图片记录。

常用查询参数：

- `page`：页码，默认 `0`。
- `size`：每页数量，默认 `10`。
- `sortBy`：排序字段，默认 `createdAt`。
- `sortDir`：排序方向，默认 `desc`。
- `label`：标签过滤。

### 搜索与热词

- `GET /api/images/search`：图片搜索。
- `GET /api/images/hotKeywords`：近 7 天热门搜索词。
- `GET /api/images/health`：图片搜索服务健康检查。

搜索参数：

- `keyword`：搜索关键词。
- `page`：页码。
- `size`：每页数量。
- `country`：地区过滤，默认 `all`。
- `time`：时间过滤。

### 热榜

- `GET /api/images/rank/realtime`：实时热榜。
- `GET /api/images/rank/daily`：日榜。
- `GET /api/images/rank/weekly`：周榜。
- `GET /api/images/rank/weekly/history?week=YYYY-WW`：历史周榜。
- `GET /api/images/rank/monthly`：月榜。
- `GET /api/images/rank/monthly/history?month=YYYY-MM`：历史月榜。
- `GET /api/images/rank/all`：一次性获取实时、日、周、月榜。

常用参数：

- `top`：返回数量，默认 `10`。
- `country`：地区过滤，默认 `all`。

### 埋点

- `POST /api/analytics/track`：批量接收前端用户行为事件。

支持的事件类型包括：

- `image_view`：图片浏览事件，发送到 Kafka topic `image-view-events`。
- `search`：搜索事件，发送到 Kafka topic `search-events`。

### Elasticsearch 管理

- `POST /api/admin/elasticsearch/rebuild`：异步重建索引。
- `DELETE /api/admin/elasticsearch/index`：删除所有索引。
- `POST /api/admin/elasticsearch/deduplicate`：清理重复文档。
- `GET /api/admin/elasticsearch/stats`：查看索引统计信息。
- `GET /api/admin/elasticsearch/duplicates`：查找重复标题。
- `PUT /api/admin/elasticsearch/batch-size?size=200`：设置批量索引大小。
- `GET /api/admin/elasticsearch/health`：Elasticsearch 健康检查。

### ETL 与报表

- `POST /api/etl/run-daily`：执行每日 ETL。
- `POST /api/etl/teststep1`：测试 ETL 抽取步骤。
- `GET /api/reports/overview/daily`：每日运营概览。
- `GET /api/reports/overview/compare`：昨日与前日对比。
- `GET /api/reports/trend/traffic`：流量趋势。
- `GET /api/reports/trend/hourly`：小时趋势。
- `GET /api/reports/top/images`：热门图片。
- `GET /api/reports/top/keywords`：热门关键词。

## 本地运行

### 环境要求

- JDK 17+
- Maven 3.8+
- Docker 与 Docker Compose
- 本地或容器中的 MongoDB、Redis、Kafka、Elasticsearch、ClickHouse

### 启动后端

```bash
./mvnw spring-boot:run
```

默认服务端口：

```text
http://localhost:8085
```

健康检查：

```bash
curl http://localhost:8085/api/images/health
```

### 打包

```bash
./mvnw clean package
```

生成的 jar 默认位于：

```text
target/worker1-0.0.1-SNAPSHOT.jar
```

### 构建 Docker 镜像

```bash
docker build -t image-exhibition-backend .
```

## Docker Compose 服务

`docker-compose.yml` 中包含以下服务：

- `mongodb`：MongoDB 数据库。
- `crawldownload`：URL 抓取/下载相关服务镜像。
- `imagedownload`：图片下载相关服务镜像。
- `lspbackend`：当前 Spring Boot 后端服务。
- `lspfront`：前端展示服务。
- `redis`：Redis 缓存与热榜计数。
- `kafka`：埋点事件消息队列。
- `jobmanager`：Flink JobManager。
- `taskmanager`：Flink TaskManager。

启动：

```bash
docker compose up -d
```

停止：

```bash
docker compose down
```

## 关键配置

配置文件位于 `src/main/resources/application.properties`。

当前默认配置：

- 后端端口：`server.port=8085`
- MongoDB：`mongodb://localhost:27017/imageurl?replicaSet=rs0`
- Redis：`localhost:6379`
- Elasticsearch：`localhost:9200`
- Kafka：`localhost:9092`
- Flink JobManager：`localhost:8081`
- ClickHouse JDBC：`jdbc:clickhouse://localhost:8123/dw_ods`
- Actuator 暴露：`management.endpoints.web.exposure.include=*`
- Prometheus 指标：`/actuator/prometheus`

部署到容器环境时，`docker-compose.yml` 会通过环境变量覆盖部分连接地址，例如 MongoDB、Redis 和 Kafka。

## 数据仓库脚本

`dw_schema.sql` 保存了 ClickHouse/数仓相关表结构脚本，用于支撑运营概览、流量趋势、热门图片、热门关键词等报表查询。

## 注意事项

- `docker-compose.yml` 中包含明文数据库用户名和密码，生产环境应改为环境变量或密钥管理。
- `ElasticsearchConfig` 当前硬编码连接 `localhost:9200`，容器化部署时需要按实际网络环境调整。
- 本地 MongoDB 默认 URI 带有 `replicaSet=rs0`，如果本地 MongoDB 未配置副本集，需要修改连接串或初始化副本集。
- 报表接口在 ClickHouse 无连接或无数据时会返回 mock 数据，便于前端联调。
- 当前 `pom.xml` 中 `spring-boot-starter-actuator` 出现了重复依赖，可后续清理。
