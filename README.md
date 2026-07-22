# Image Exhibition

Image Exhibition 是一个图片展示、搜索、热榜和用户行为埋点后端服务。当前 `simple` 分支已经移除了报表、ETL、ClickHouse 和 Flink 链路，保留更轻量的在线业务能力：图片列表/详情、Elasticsearch 搜索、Redis 热榜、热门搜索词、MongoDB 索引管理和 Kafka 埋点事件转发。

## 项目要点

- 图片数据展示：从 MongoDB `imageurl` 集合读取图片记录，支持分页、排序、标签过滤和地区过滤。
- 图片详情接口：按图片 ID/标题获取图片详情，返回前端展示所需 DTO。
- 图片全文搜索：通过 Elasticsearch 提供关键词搜索，并支持国家/地区、时间等查询条件。
- 搜索热词：记录用户搜索关键词，并通过 Elasticsearch 聚合和 Redis 缓存返回近 7 天热门搜索词。
- 图片热度排行：基于前端浏览埋点直接更新 Redis ZSet，提供实时榜、日榜、周榜、月榜和历史榜。
- 用户行为埋点：接收前端批量事件，更新 Redis 热度数据，并将事件发送到 Kafka 供后续异步消费。
- 索引管理：提供 Elasticsearch 索引重建、删除、去重、统计、健康检查等管理接口。
- MongoDB 索引管理：提供 MongoDB 索引创建、删除和查看接口。
- 可观测性：集成 Spring Boot Actuator、Micrometer 和 Prometheus 指标暴露。
- 容器化依赖：通过 `docker-compose.yml` 启动 MongoDB、Redis、Kafka 和 Zookeeper。

## 系统架构

当前系统采用轻量在线服务架构，Spring Boot 后端负责对外提供 API，同时协调 MongoDB、Elasticsearch、Redis 和 Kafka。

```text
                        ┌────────────────────┐
                        │      Frontend      │
                        └─────────┬──────────┘
                                  │ HTTP API
                                  ▼
                        ┌────────────────────┐
                        │  Spring Boot API   │
                        │    port 8085       │
                        └───┬─────┬─────┬────┘
                            │     │     │
             image data     │     │     │ analytics events
                            │     │     ▼
                            │     │  ┌──────────────┐
                            │     │  │    Kafka     │
                            │     │  │ event stream │
                            │     │  └──────────────┘
                            │     │
                            │     │ rank/search cache
                            │     ▼
                            │  ┌──────────────┐
                            │  │    Redis     │
                            │  │ ZSet / cache │
                            │  └──────────────┘
                            │
          source of truth   │     full-text index
                            ▼     ▼
                     ┌──────────┐ ┌─────────────────┐
                     │ MongoDB  │ │ Elasticsearch   │
                     │ imageurl │ │ image/search log│
                     └──────────┘ └─────────────────┘
```

### 核心链路

图片浏览链路：

```text
Frontend trackImageView
  -> POST /api/analytics/track
  -> ImageHotRankService.recordView()
  -> Redis ZSet 写入实时榜、日榜、周榜、月榜
  -> Kafka image-view-events
```

图片搜索链路：

```text
Frontend search
  -> GET /api/images/search
  -> ImageSearchService 查询 Elasticsearch
  -> SearchLogService 记录搜索词到 Elasticsearch
  -> Redis 缓存热门搜索词
```

图片展示链路：

```text
Frontend image list/detail
  -> GET /api/images/list 或 GET /api/images/{id}
  -> DbImageUrlService
  -> MongoDB imageurl collection
```

索引同步链路：

```text
MongoDB imageurl changes
  -> MongoChangeStreamSyncService
  -> IndexManagementService
  -> Elasticsearch image index
```

## 技术栈

### 后端与基础框架

- Java 17
- Spring Boot 3.4.5
- Spring Web
- Spring Data MongoDB
- Spring Data Redis
- Spring Kafka
- Spring Boot Actuator
- Lombok

### 数据存储与中间件

- MongoDB：存储图片 URL、标题、标签、地区、描述等核心业务数据。
- Redis：存储图片实时/日/周/月热榜，缓存热门搜索词。
- Elasticsearch 8.14 Java Client：提供图片全文检索、搜索日志聚合、索引管理和去重统计。
- Kafka：承接前端行为埋点事件流。
- Zookeeper：当前 Compose 中 Kafka 依赖的协调服务。

### 工程与部署

- Maven：项目构建与依赖管理。
- Docker：后端服务镜像构建。
- Docker Compose：本地编排 MongoDB、Redis、Kafka 和 Zookeeper。
- Micrometer + Prometheus：应用指标采集。
- JUnit 5 + Mockito：单元测试相关依赖。
- Apache HttpClient 5：HTTP 客户端能力。

## 目录结构

```text
.
├── Dockerfile
├── docker-compose.yml
├── pom.xml
├── src
│   ├── main
│   │   ├── java/com/worker1/worker1
│   │   │   ├── config        # Elasticsearch、RestTemplate 等配置
│   │   │   ├── controller    # 图片、搜索、热榜、埋点、索引管理 API
│   │   │   ├── model         # API DTO 与搜索分页模型
│   │   │   ├── service       # 图片、搜索、热榜、索引同步等业务服务
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

- `title`：图片记录 ID/标题，也是当前 MongoDB 文档 ID。
- `imageUrl`：图片地址列表。
- `website`：来源站点。
- `labels`：标签列表，已建立索引。
- `country`：地区分类，已建立索引。
- `createdAt`：创建时间戳。
- `description`：描述文本，用于全文检索。
- `keywords`：标题或内容提取出的关键词。

项目还在 `labels + country` 上定义了 MongoDB 复合索引，用于标签和地区组合查询。

## 热榜设计

热榜基于 Redis ZSet 实现，浏览一次图片就对对应图片 ID 执行一次加分。

Redis key：

- 实时榜：`rank:realtime:{country}:{minuteTimestamp}`，查询时合并最近 5 个分钟桶。
- 日榜：`rank:daily:{country}:{yyyyMMdd}`。
- 周榜：`rank:weekly:{country}:{YYYY-Www}`，使用 ISO 周。
- 月榜：`rank:monthly:{country}:{yyyyMM}`。

每次浏览会同时更新具体地区和 `all` 维度。例如 `country=asia` 时会写：

```text
rank:weekly:asia:2026-W30
rank:weekly:all:2026-W30
rank:monthly:asia:202607
rank:monthly:all:202607
```

TTL 策略：

- 实时榜分钟桶保留 10 分钟。
- 日榜保留 7 天。
- 周榜保留 180 天。
- 月榜保留 540 天。

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

### 标签

- `GET /api/labels`：获取全部标签。
- `GET /api/labels/rankings`：获取 Redis 中的标签热度排序。

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
- `GET /api/images/rank/weekly`：当前周榜。
- `GET /api/images/rank/weekly/history?week=YYYY-Www`：历史周榜。
- `GET /api/images/rank/monthly`：当前月榜。
- `GET /api/images/rank/monthly/history?month=yyyyMM`：历史月榜。
- `GET /api/images/rank/all`：一次性获取实时、日、周、月榜。

常用参数：

- `top`：返回数量，默认 `10`。
- `country`：地区过滤，默认 `all`。

### 埋点

- `POST /api/analytics/track`：批量接收前端用户行为事件。

支持的事件类型包括：

- `image_view`：图片浏览事件，更新 Redis 热榜，并发送到 Kafka topic `image-view-events`。
- `search`：搜索事件，发送到 Kafka topic `search-events`。

### Elasticsearch 管理

- `POST /api/admin/elasticsearch/rebuild`：异步重建索引。
- `DELETE /api/admin/elasticsearch/index`：删除所有索引。
- `POST /api/admin/elasticsearch/deduplicate`：清理重复文档。
- `GET /api/admin/elasticsearch/stats`：查看索引统计信息。
- `GET /api/admin/elasticsearch/duplicates`：查找重复标题。
- `PUT /api/admin/elasticsearch/batch-size?size=200`：设置批量索引大小。
- `GET /api/admin/elasticsearch/health`：Elasticsearch 健康检查。

### MongoDB 索引管理

- `POST /api/index/create`：创建单字段或复合索引。
- `DELETE /api/index/delete`：删除指定索引。
- `GET /api/index/list`：查看 `imageurl` 集合索引。

## 本地运行

### 环境要求

- JDK 17+
- Maven 3.8+
- Docker 与 Docker Compose
- 本地或容器中的 MongoDB、Redis、Kafka、Elasticsearch

### 启动依赖服务

```bash
docker compose up -d
```

当前 Compose 会启动：

- `mongo`
- `redis`
- `zookeeper`
- `kafka`

Elasticsearch 当前没有放进 `docker-compose.yml`，需要本地单独启动，默认地址为 `http://localhost:9200`。

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

## 关键配置

配置文件位于 `src/main/resources/application.properties`。

当前默认配置：

- 后端端口：`server.port=8085`
- MongoDB：`mongodb://localhost:27017/imageurl?replicaSet=rs0`
- Redis：`localhost:6379`
- Elasticsearch：`localhost:9200`
- Kafka：`localhost:9092`
- Actuator 暴露：`management.endpoints.web.exposure.include=*`
- Prometheus 指标：`/actuator/prometheus`

## 已移除的复杂链路

`simple` 分支已经移除以下模块：

- `/api/reports/**` 报表接口。
- `/api/etl/**` ETL 触发接口。
- `ETLBatchService` 批处理任务。
- ClickHouse JDBC 配置和依赖。
- Flink 依赖和 Compose 服务。
- Compose 中的 ClickHouse、Flink JobManager、Flink TaskManager。

周榜/月榜现在由 `ImageHotRankService.recordView()` 在埋点进入时直接更新 Redis，不再依赖 ETL 汇总。

## 注意事项

- `ElasticsearchConfig` 当前硬编码连接 `localhost:9200`，容器化部署时需要按实际网络环境调整。
- 本地 MongoDB 默认 URI 带有 `replicaSet=rs0`，如果本地 MongoDB 未配置副本集，需要修改连接串或初始化副本集。
- `AnalyticsController` 会在收到任意事件时调用热榜记录逻辑，后续可以只对 `image_view` 事件更新热榜，避免非浏览事件误计数。
- 当前 `pom.xml` 中 `spring-boot-starter-actuator` 出现了重复依赖，可后续清理。
