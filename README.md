# Video Exhibition

Video Exhibition 是一个视频展示、搜索、热榜和用户行为埋点服务。后端基于 Spring Boot，对外提供视频列表、详情、搜索、管理、热榜和埋点 API；前端通过 Jenkins 构建后以 Nginx 容器部署。

当前 Jenkins 部署使用 `prod-lite` 配置，重点提供前端、后端、MongoDB、Prometheus 和 Grafana 的轻量线上运行环境。`prod-lite` 会关闭 Redis、Kafka 和 Elasticsearch 自动配置，因此线上部署后的首要验证目标是前端访问、后端健康检查、MongoDB 数据接口和监控指标。

## 项目要点

- 视频数据展示：从 MongoDB 读取视频记录，支持分页、排序和多条件过滤。
- 视频详情接口：按视频 ID 获取公开视频详情。
- 视频搜索：优先使用 Elasticsearch；未启用搜索服务时回退到 MongoDB 查询。
- 视频管理：支持后台分页查询、创建、更新、删除、审核和发布状态管理。
- 视频热榜：基于浏览埋点更新 Redis ZSet，提供实时榜、日榜、周榜、月榜和历史榜。
- 用户行为埋点：接收前端批量事件，将视频浏览事件写入热榜，并按事件类型转发到 Kafka。
- 可观测性：集成 Spring Boot Actuator、Micrometer 和 Prometheus 指标暴露。
- 容器化部署：通过 Jenkins 构建后端、前端镜像，并部署后端、前端、MongoDB、Prometheus 和 Grafana。

## 系统架构

```text
                        ┌────────────────────┐
                        │      Frontend      │
                        └─────────┬──────────┘
                                  │ HTTP API
                                  ▼
                        ┌────────────────────┐
                        │  Spring Boot API   │
                        │    port 8081       │
                        └───┬─────┬─────┬────┘
                            │     │     │ analytics events
              video data    │     │     ▼
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
                     │videoData │ │ video/search log│
                     └──────────┘ └─────────────────┘
```

### 核心链路

视频展示链路：

```text
Frontend video list/detail
  -> GET /api/videos/list/{category} 或 GET /api/videos/{id}
  -> VideoDataService
  -> MongoDB videoData collection
```

视频搜索链路：

```text
Frontend search
  -> GET /api/videos/search
  -> VideoSearchService 或 VideoDataService
  -> Elasticsearch 或 MongoDB
```

视频浏览埋点链路：

```text
Frontend track event
  -> POST /api/analytics/track
  -> VideoHotRankService.recordView()
  -> Redis ZSet 写入实时榜、日榜、周榜、月榜
  -> Kafka video-view-events
```

后台管理链路：

```text
Admin console
  -> /api/admin/videos
  -> VideoDataService
  -> MongoDB videoData collection
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

- MongoDB：存储视频标题、封面、分类、地区、语言、年份、清晰度、状态、审核状态、发布状态等数据。
- Redis：存储视频实时、日、周、月热榜。
- Elasticsearch 8.14 Java Client：提供视频全文检索和搜索日志能力。
- Kafka：承接前端行为埋点事件流。
- Zookeeper：当前 Compose 中 Kafka 依赖的协调服务。

### 工程与部署

- Maven：项目构建与依赖管理。
- Docker：后端与前端服务镜像构建。
- Docker Compose：本地编排 MongoDB、Redis、Kafka 和 Zookeeper。
- Micrometer + Prometheus：应用指标采集。
- JUnit 5 + Mockito：单元测试相关依赖。
- Apache HttpClient 5：HTTP 客户端能力。

## 目录结构

```text
.
├── Dockerfile
├── Jenkinsfile
├── docker-compose.yml
├── pom.xml
├── deploy
│   └── prometheus
│       └── prometheus.yml
├── src
│   ├── main
│   │   ├── java/com/worker1/worker1
│   │   │   ├── config        # Elasticsearch、RestTemplate 等配置
│   │   │   ├── controller    # 视频、热榜、埋点、管理 API
│   │   │   ├── model         # API DTO 与搜索分页模型
│   │   │   ├── service       # 视频、搜索、热榜等业务服务
│   │   │   └── store         # MongoDB Repository 与文档模型
│   │   └── resources
│   │       ├── application.properties
│   │       └── application-prod-lite.properties
│   └── test
│       └── java/com/worker1/worker1
└── video_exhibition       # 前端项目
```

## 核心数据模型

公开视频数据模型为 `VideoData`，管理端数据模型为 `VideoManageData`，MongoDB collection 为 `videoData`。

主要字段：

- `id`：视频 ID。
- `title`：标题。
- `description`：简介。
- 封面地址：用于列表和详情页展示视频封面。
- `videoUrl`：视频播放地址，管理端模型字段。
- `category`：分类，例如 `movie`、`drama`、`variety`。
- `type`：类型。
- `region`：地区。
- `language`：语言。
- `year`：年份。
- `quality`：清晰度。
- `status`：业务状态。
- `auditStatus`：审核状态，支持 `pending`、`approved`、`rejected`。
- `publishStatus`：发布状态，支持 `draft`、`published`、`offline`。
- `createdAt`、`updatedAt`、`reviewedAt`：时间字段。

`VideoManageData` 定义了分类过滤、创建时间和公开数据查询相关复合索引，用于支撑列表筛选和排序。

## 热榜设计

热榜基于 Redis ZSet 实现，浏览一次视频就对对应视频 ID 执行一次加分。

Redis key：

- 实时榜：`video:rank:realtime:{category}:{minuteTimestamp}`，查询时合并最近 5 个分钟桶。
- 日榜：`video:rank:daily:{category}:{yyyyMMdd}`。
- 周榜：`video:rank:weekly:{category}:{YYYY-Www}`，使用 ISO 周。
- 月榜：`video:rank:monthly:{category}:{yyyyMM}`。

每次浏览会同时更新具体分类和 `all` 维度。例如 `category=movie` 时会写：

```text
video:rank:weekly:movie:2026-W40
video:rank:weekly:all:2026-W40
video:rank:monthly:movie:202609
video:rank:monthly:all:202609
```

TTL 策略：

- 实时榜分钟桶保留 10 分钟。
- 日榜保留 7 天。
- 周榜保留 180 天。
- 月榜保留 540 天。

## 主要 API

### 视频列表与详情

- `GET /api/videos/list/{category}`：分页获取公开视频列表。
- `GET /api/videos/{id}`：获取公开视频详情。

常用查询参数：

- `page`：页码，默认 `0`。
- `size`：每页数量，默认 `10`。
- `sortBy`：排序字段，默认 `createdAt`。
- `sortDir`：排序方向，默认 `desc`。
- `type`：类型过滤。
- `region`：地区过滤。
- `language`：语言过滤。
- `year`：年份过滤。
- `quality`：清晰度过滤。
- `status`：状态过滤。

### 视频搜索

- `GET /api/videos/search`：搜索视频。
- `GET /api/videos/health`：视频搜索服务健康检查。

搜索参数：

- `keyword`：搜索关键词。
- `page`：页码，默认 `0`。
- `size`：每页数量，默认 `10`。

### 视频热榜

- `GET /api/videos/rank/realtime`：实时热榜。
- `GET /api/videos/rank/daily`：日榜。
- `GET /api/videos/rank/weekly`：当前周榜。
- `GET /api/videos/rank/weekly/history?week=YYYY-Www`：历史周榜。
- `GET /api/videos/rank/monthly`：当前月榜。
- `GET /api/videos/rank/monthly/history?month=yyyyMM`：历史月榜。
- `GET /api/videos/rank/all`：一次性获取实时、日、周、月榜。

常用参数：

- `top`：返回数量，默认 `10`。
- `category`：分类过滤，默认 `all`，支持 `movie`、`drama`、`variety`、`all`。

### 视频管理

- `GET /api/admin/videos`：分页查询管理端视频列表。
- `POST /api/admin/videos`：创建视频。
- `PUT /api/admin/videos/{id}`：更新视频。
- `DELETE /api/admin/videos/{id}`：删除视频。
- `PATCH /api/admin/videos/{id}/audit`：更新审核状态。
- `PATCH /api/admin/videos/{id}/publish`：更新发布状态。

管理端查询参数除公开视频列表参数外，还支持：

- `auditStatus`：审核状态过滤。
- `publishStatus`：发布状态过滤。

### 埋点

- `POST /api/analytics/track`：批量接收前端用户行为事件。

支持的事件类型：

- `video_view`：视频浏览事件，更新 Redis 热榜，并发送到 Kafka topic `video-view-events`。
- `search`：搜索事件，发送到 Kafka topic `search-events`。

### MongoDB 索引管理

- `POST /api/index/create`：创建单字段或复合索引。
- `DELETE /api/index/delete`：删除指定索引。
- `GET /api/index/list`：查看集合索引。

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
http://localhost:8081
```

健康检查：

```bash
curl http://localhost:8081/actuator/health
```

### 打包

```bash
./mvnw clean package
```

生成的 jar 默认位于 `target` 目录，实际文件名以 Maven 构建输出为准。

### 构建 Docker 镜像

```bash
docker build -t video-exhibition-backend .
```

## 关键配置

配置文件位于 `src/main/resources/application.properties`。

当前默认配置：

- 后端端口：`server.port=8081`
- Redis：`localhost:6379`
- Elasticsearch：`localhost:9200`
- Kafka：`localhost:9092`
- Actuator 暴露：`management.endpoints.web.exposure.include=*`
- Prometheus 指标：`/actuator/prometheus`

### Jenkins 线上配置

Jenkins 流水线使用 `SPRING_PROFILES_ACTIVE=prod-lite` 启动后端容器，对应配置文件为 `src/main/resources/application-prod-lite.properties`。

`prod-lite` 关键行为：

- 后端端口：`8081`。
- MongoDB 地址通过环境变量 `MONGO_URI` 注入。
- Actuator 只暴露 `health`、`info`、`prometheus`。
- Elasticsearch 健康检查关闭。
- Redis、Kafka、Elasticsearch 功能关闭：

```properties
feature.redis.enabled=false
feature.kafka.enabled=false
feature.elasticsearch.enabled=false
```

因此，使用 Jenkins 部署后的验证应优先覆盖基础服务和 MongoDB 数据接口。搜索、热榜、Kafka 埋点和 Elasticsearch 管理能力需要在启用对应依赖后再作为完整功能验收项。

## Jenkins 部署后验证

以下命令中的服务器地址以当前 `Jenkinsfile` 中的 `SERVER_HOST=192.210.161.144` 为例。如果服务器地址变更，请替换为实际地址。

### 1. 检查容器状态

在部署服务器上执行：

```bash
docker ps
docker logs --tail=200 backend
docker logs --tail=100 frontend
```

确认以下容器处于运行状态：

- `backend`
- `frontend`
- `mongo`
- `prometheus`
- `grafana`

重点检查 `backend` 日志中是否有 Spring Boot 启动成功、MongoDB 连接异常或端口占用错误。

### 2. 验证后端健康检查

```bash
curl -i http://192.210.161.144:8081/actuator/health
```

预期返回 `200`，响应体包含：

```json
{"status":"UP"}
```

### 3. 验证 Prometheus 指标

```bash
curl -i http://192.210.161.144:8081/actuator/prometheus
```

预期返回 `200`，并能看到 JVM、HTTP、Spring 等指标文本。

### 4. 验证前端访问

```bash
curl -I http://192.210.161.144
curl -I https://192.210.161.144
```

预期返回 `200`、`301` 或 `302`。如果浏览器中页面能正常加载，还需要打开开发者工具检查 API 请求是否指向正确的后端地址，并确认没有 CORS、404、500 或 Mixed Content 错误。

### 5. 验证基础业务接口

```bash
curl "http://192.210.161.144:8081/api/videos/list/all?page=0&size=10"
curl "http://192.210.161.144:8081/api/videos/list/movie?page=0&size=10"
curl "http://192.210.161.144:8081/api/videos/list/drama?page=0&size=10"
curl "http://192.210.161.144:8081/api/videos/search?keyword=test&page=0&size=10"
curl "http://192.210.161.144:8081/api/videos/health"
```

如果接口返回 `200` 且数据为空，通常表示服务链路可用但 MongoDB 中暂无对应数据。如果返回 `500`，优先查看后端日志：

```bash
docker logs --tail=300 backend
```

### 6. 验证监控服务

浏览器打开：

```text
http://192.210.161.144:9090
http://192.210.161.144:3000
```

Prometheus 中可查询：

```text
up
http_server_requests_seconds_count
jvm_memory_used_bytes
```

Grafana 默认账号通常为 `admin / admin`，首次登录后可能需要修改密码。

## 已移除的复杂链路

当前轻量部署不包含以下链路：

- `/api/reports/**` 报表接口。
- `/api/etl/**` ETL 触发接口。
- `ETLBatchService` 批处理任务。
- ClickHouse JDBC 配置和依赖。
- Flink 依赖和 Compose 服务。
- Compose 中的 ClickHouse、Flink JobManager、Flink TaskManager。

周榜和月榜现在由 `VideoHotRankService.recordView()` 在埋点进入时直接更新 Redis，不再依赖 ETL 汇总。

## 注意事项

- 本地 MongoDB 默认 URI 带有 `replicaSet=rs0`，如果本地 MongoDB 未配置副本集，需要修改连接串或初始化副本集。
- `prod-lite` 关闭了 Redis、Kafka 和 Elasticsearch，相关接口或能力需要在完整依赖启动后再验证。
- `AnalyticsController` 当前只对 `video_view` 事件调用热榜记录逻辑，其他事件仅按事件类型写入对应 Kafka topic。
- 当前 `pom.xml` 中 `spring-boot-starter-actuator` 出现了重复依赖，可后续清理。
