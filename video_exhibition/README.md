# LSP Frontend 项目说明

## 1. 项目概览

本项目是一个基于 **React + TypeScript + Create React App** 构建的前端站点，主要用于图片内容展示、分类浏览、关键词搜索、榜单展示以及数据分析报表查看。

从用户访问路径来看，项目的核心流程如下：

1. 用户先进入年龄确认页。
2. 进入图片列表页后，可按地区、标签、时间排序浏览内容。
3. 通过顶部搜索栏可输入关键词，或直接点击热门搜索词进入检索结果页。
4. 点击图片卡片进入详情页，查看完整图片集合与来源信息。
5. 通过排行榜页查看实时榜、日榜、周榜、月榜。
6. 通过数据分析页查看 PV、UV、搜索、图片表现、国家分布等业务指标。

项目目前是一个典型的前后端分离前端工程：

- 前端本地开发默认运行在 `http://localhost:3000`
- 主业务后端默认请求 `http://localhost:8085`
- 弹幕数据看板默认请求 `http://localhost:8080`
- 生产环境通过 Nginx 统一托管静态资源并反向代理 `/api`

---

## 2. 技术栈总览

### 2.1 前端框架

- **React 19**
  用于构建页面与组件。
- **TypeScript**
  用于提供类型约束、接口定义和更稳健的前端开发体验。
- **Create React App / react-scripts 5**
  当前项目的构建、开发服务器、测试入口均基于 CRA。
- **React Router DOM 7**
  用于页面级路由管理。

### 2.2 数据请求与通信

- **Axios**
  用于封装主要业务 API 请求，集中管理后端地址。
- **Fetch API**
  在部分功能中直接使用原生 `fetch`，例如数据分析报表、排行榜、埋点上报、弹幕看板等。

### 2.3 UI 与样式

- **CSS Modules**
  大部分页面和组件都采用 `*.module.css` 方式隔离样式作用域。
- **全局 CSS**
  项目中同时存在 `src/index.css`、`src/App.css`、`globals.css` 等全局样式文件。
- **lucide-react**
  用于提供搜索、浏览量、时间、图表等图标。

### 2.4 数据埋点与行为分析

- **uuid**
  用于生成匿名 `userId` 和 `sessionId`。
- **自定义 analytics 模块**
  用于记录图片浏览、搜索行为、停留时长、滚动深度等事件，并以批量方式上报。

### 2.5 测试与工程化

- **Jest + Testing Library**
  来自 CRA 默认测试体系，`src/setupTests.ts` 中引入了 `@testing-library/jest-dom`。
- **Webpack 5 / webpack-cli**
  作为工程依赖存在，实际构建入口仍由 `react-scripts` 驱动。

### 2.6 部署与运维

- **Nginx**
  用于托管前端 `build` 产物、处理 SPA 路由回退、代理后端接口。
- **Docker**
  仓库中提供 `Dockerfile`，用于打包基于 Nginx 的部署镜像。
- **HTTPS 证书文件**
  仓库内存在 `lsp66.com.cert` 和 `lsp66.com.key`，当前 Docker / Nginx 配置会直接引用它们。

### 2.7 当前仓库里值得注意的“历史/预留配置”

项目主体是 **CRA**，但仓库中还保留了以下配置文件：

- `tailwind.config.ts`
- `eslint.config.mjs`（内容偏向 Next.js 风格）

这些文件目前不是项目主流程的核心配置来源，更多像是历史遗留或未来扩展预留。实际开发与运行仍应以 `react-scripts`、`package.json`、`tsconfig.json`、现有 CSS Modules 为准。

---

## 3. 功能实现总览

下面按照“用户访问顺序 + 功能模块顺序”梳理项目已经实现的能力。

### 3.1 年龄确认首页

入口页面：`/`

对应文件：

- `src/pages/Home.tsx`

已实现功能：

- 展示成人内容访问警告。
- 中英文双语提示。
- 点击确认按钮后进入主列表页 `/list`。

这个页面主要承担访问入口与内容分流作用，逻辑较轻，但对产品入口非常关键。

### 3.2 主导航与全局搜索

核心文件：

- `src/components/Header.tsx`
- `src/components/AutoComplete.tsx`
- `src/components/HotKeySearchDroplist.tsx`

已实现功能：

- 顶部导航支持快速切换：
  - 国产
  - 亚洲
  - 欧美
  - 数据分析
- 顶部搜索框支持输入关键词后直接跳转 `/search?query=关键词`
- 搜索框获得焦点且输入为空时，会自动展示热门搜索下拉列表
- 支持点击热门搜索词快速填充关键词
- 支持按回车触发搜索

实现特点：

- 热门搜索词通过 `getHotKeyList()` 从 `/api/images/hotKeywords` 获取。
- 搜索入口非常轻，真正的搜索请求放在搜索结果页统一执行。

### 3.3 图片列表页

核心路由：

- `/list`
- `/list/domestic`
- `/list/asia`
- `/list/european`

核心文件：

- `src/pages/List.tsx`
- `src/pages/ListPage.tsx`
- `src/components/MainDisplay.tsx`
- `src/components/LabelSection.tsx`
- `src/components/ImageCard.tsx`
- `src/components/PageSection.tsx`

已实现功能：

- 支持按地区浏览内容：
  - 全部
  - 国产
  - 亚洲
  - 欧美
- 支持按标签筛选内容
- 支持按时间升序 / 降序排序
- 支持分页浏览
- 支持从列表页点击进入详情页
- 支持从详情页返回后恢复列表页码与滚动位置

实现细节：

- `LabelSection` 会从 `/api/labels` 拉取全部标签。
- `MainDisplay` 根据当前路径自动选择不同 API：
  - `/api/images/list`
  - `/api/images/list/domestic`
  - `/api/images/list/asia`
  - `/api/images/list/european`
- 使用 `sessionStorage` 保存：
  - 当前路径
  - 当前页码
  - 当前滚动位置
  - 状态时间戳
- 页面状态有效期为 **5 分钟**，超时后不恢复。
- 图片卡片会展示：
  - 首图
  - 标题
  - 创建时间
  - 地区标识
  - 匹配关键词标签
  - 当日新增 `NEW` 标记

### 3.4 搜索结果页

核心路由：

- `/search?query=xxx`

核心文件：

- `src/pages/SearchList.tsx`
- `src/api/acops.ts`
- `src/components/ImageCard.tsx`
- `src/components/PageSection.tsx`

已实现功能：

- 根据 URL 中的 `query` 参数执行搜索
- 支持地区筛选：
  - 全部地区
  - 国产
  - 亚洲
  - 欧美
- 支持时间排序：
  - 默认
  - 最新发布
  - 最早发布
- 显示命中总数、当前页、当前页结果数量
- 支持分页切换
- 搜索无结果时展示空状态

实现细节：

- 搜索接口为 `/api/images/search`
- 查询参数包括：
  - `keyword`
  - `page`
  - `size`
  - `country`
  - `time`
- 搜索页会在筛选条件变化时自动重置页码。
- 点击某个搜索结果时，会记录该结果在搜索结果中的点击位置，用于后续搜索效果分析。

### 3.5 图片详情页

核心路由：

- `/image/:id`

核心文件：

- `src/components/ImageDetail.tsx`

已实现功能：

- 根据图片 ID / 标题获取单条内容详情
- 展示标题、发布时间、地区信息
- 支持展示图片集合
- 图片加载失败时展示占位状态
- 支持返回上一页
- 支持跳转到来源网站

实现细节：

- 详情接口为 `/api/images/:id`
- 页面会对 `id` 做 `decodeURIComponent`
- 如果存在来源地址，会拼接到 `https://t66y.com${website}`
- 图片采用 `loading="lazy"` 延迟加载

### 3.6 排行榜页

核心路由：

- `/rank/all`

核心文件：

- `src/components/RankPage.tsx`
- `src/components/HotRankPanel.tsx`

已实现功能：

- 支持榜单维度切换：
  - 全站榜
  - 国内榜
  - 亚洲榜
  - 欧美榜
- 支持时间范围切换：
  - 实时榜（近 5 分钟）
  - 日榜
  - 周榜
  - 月榜
- 榜单项支持点击进入详情页

实现细节：

- 榜单接口：
  - `/api/images/rank/realtime`
  - `/api/images/rank/daily`
  - `/api/images/rank/weekly`
  - `/api/images/rank/monthly`
- 请求参数：
  - `top=10`
  - `country=...`
- 实时榜页面会显示统计口径提示。
- 使用 `AbortController` 和请求 ID 避免频繁切换 tab 时旧请求覆盖新请求。

### 3.7 数据分析报表页

核心路由：

- `/analytics`

核心文件：

- `src/pages/AnalyticsPage.tsx`
- `src/utils/format.ts`

已实现功能：

- 按日期查看日度总览数据
- 快速切换日期：
  - 昨天
  - 7 天前
  - 30 天前
- 查看核心指标卡片：
  - 总浏览量 PV
  - 访客数 UV
  - 平均停留时间
  - 活跃图片数
  - 搜索次数
  - 跳出率
- 查看趋势数据、热门图片、热门关键词、国家分布等报表数据
- 支持手动触发 ETL 任务
- 支持对测试数据源进行标记

实现细节：

- 报表接口基地址写死为 `http://localhost:8085/api/reports`
- ETL 手动执行接口为 `http://localhost:8085/api/etl/run-daily`
- 页面初始化后会并行拉取多个报表接口
- 日期默认选中“昨天”
- 页面内对数值、百分比、国家名称做了统一格式化

### 3.8 用户行为埋点

核心文件：

- `src/utils/analytics.ts`

已实现功能：

- 生成匿名用户 ID
- 生成会话 ID
- 识别设备类型
- 识别浏览器
- 识别操作系统
- 记录屏幕分辨率
- 记录图片浏览事件
- 记录搜索事件
- 记录搜索结果点击位置
- 记录详情页停留时长与滚动深度
- 批量上报埋点事件

实现细节：

- `userId` 存储在 `localStorage`
- `sessionId` 存储在 `sessionStorage`
- 事件上报地址为 `http://localhost:8085/api/analytics/track`
- 默认每 **5 秒** 批量发送一次
- 队列达到 **20 条** 时会立即发送
- 页面关闭前会尝试 `keepalive` 上报

当前状态说明：

- `trackPageView()` 方法目前已被禁用，页面浏览埋点逻辑保留但未实际发送。
- 图片详情页和搜索页的行为埋点仍然在工作流中被调用。

### 3.9 实时弹幕数据看板（代码已存在，当前主路由未启用）

核心文件：

- `src/components/DanmakuDashboard.tsx`
- `src/components/Danmakudashboard.css`

当前能力：

- 拉取房间热词
- 拉取用户排行榜
- 拉取房间 PV / UV / 平均 PV
- 本地模拟弹幕滚动动画
- 支持房间切换
- 每 5 秒自动刷新统计数据

接口依赖：

- `http://localhost:8080/api/danmaku/hotwords/:roomId`
- `http://localhost:8080/api/danmaku/userrank/:roomId`
- `http://localhost:8080/api/danmaku/room/:roomId/stats`

说明：

- 组件已经在代码中存在，但当前 `App.tsx` 没有为它配置对外访问路由。
- 因此它更像一个预研中的实时数据面板，而不是当前线上主功能。

---

## 4. 路由清单

当前前端已定义的主路由如下：

| 路由 | 页面说明 |
| --- | --- |
| `/` | 年龄确认页 |
| `/list` | 全部图片列表 |
| `/list/domestic` | 国产列表 |
| `/list/asia` | 亚洲列表 |
| `/list/european` | 欧美列表 |
| `/search` | 搜索结果页 |
| `/rank/all` | 排行榜页 |
| `/image/:id` | 图片详情页 |
| `/analytics` | 数据分析报表页 |

---

## 5. 目录结构说明

```text
lsp_front/
├── public/                     # 静态资源
├── src/
│   ├── api/                    # Axios 封装与业务 API
│   ├── components/             # 通用组件、榜单、详情、分页、搜索等
│   ├── pages/                  # 页面级组件
│   ├── utils/                  # 格式化、埋点等工具
│   ├── App.tsx                 # 路由入口
│   ├── index.tsx               # React 挂载入口
│   └── index.css               # 全局样式
├── Dockerfile                  # Nginx 镜像部署文件
├── nginx.conf                  # 主 Nginx 配置
├── default.conf                # 默认 HTTP 配置
├── tsconfig.json               # TypeScript 配置
├── package.json                # 项目依赖与脚本
└── README.md                   # 项目说明文档
```

---

## 6. 核心接口说明

### 6.1 图片与标签相关接口

| 接口 | 说明 |
| --- | --- |
| `/api/images/list` | 全站分页列表 |
| `/api/images/list/domestic` | 国产分页列表 |
| `/api/images/list/asia` | 亚洲分页列表 |
| `/api/images/list/european` | 欧美分页列表 |
| `/api/images/:id` | 图片详情 |
| `/api/labels` | 所有标签 |
| `/api/images/search` | 搜索图片 |
| `/api/images/hotKeywords` | 热门搜索词 |

### 6.2 排行榜接口

| 接口 | 说明 |
| --- | --- |
| `/api/images/rank/realtime` | 实时榜 |
| `/api/images/rank/daily` | 日榜 |
| `/api/images/rank/weekly` | 周榜 |
| `/api/images/rank/monthly` | 月榜 |

### 6.3 分析与 ETL 接口

| 接口 | 说明 |
| --- | --- |
| `/api/reports/overview/daily` | 日报总览 |
| `/api/reports/trend/traffic` | 流量趋势 |
| `/api/reports/top/images` | 热门图片 |
| `/api/reports/top/keywords` | 热门关键词 |
| `/api/reports/distribution/country` | 国家分布 |
| `/api/etl/run-daily` | 手动执行 ETL |

### 6.4 埋点接口

| 接口 | 说明 |
| --- | --- |
| `/api/analytics/track` | 用户行为埋点上报 |

### 6.5 弹幕数据接口

| 接口 | 说明 |
| --- | --- |
| `/api/danmaku/hotwords/:roomId` | 房间热词 |
| `/api/danmaku/userrank/:roomId` | 用户排行榜 |
| `/api/danmaku/room/:roomId/stats` | 房间统计 |

---

## 7. 开发环境准备

这一部分尽量写成“拿到项目后即可执行”的步骤。

### 7.1 系统要求

建议环境：

- Node.js `18.x` 或 `20.x`
- npm `9+`
- Git

说明：

- 项目当前使用的是 `package-lock.json`，推荐直接使用 `npm`。
- 虽然 `package.json` 中的 `typescript` 版本声明较旧，但实际能否升级需要结合整个项目测试后再决定。当前建议先按现状安装运行。

### 7.2 获取代码

```bash
git clone <your-repository-url>
cd lsp_front
```

### 7.3 安装依赖

```bash
npm install
```

如果你想严格按照锁文件安装，也可以使用：

```bash
npm ci
```

### 7.4 后端服务准备

本项目不是纯静态站点，前端运行依赖后端接口。至少需要准备以下服务：

1. 主业务接口服务，默认端口 `8085`
2. 如果要联调弹幕看板，还需要弹幕服务，默认端口 `8080`

本地开发默认写死的接口地址如下：

- 主业务 API：`http://localhost:8085`
- 报表 API：`http://localhost:8085/api/reports`
- ETL API：`http://localhost:8085/api/etl`
- 埋点 API：`http://localhost:8085/api/analytics`
- 弹幕 API：`http://localhost:8080`

这意味着：

- 如果你的后端不是运行在这两个端口，需要先修改前端源码中的接口地址。
- 当前仓库中**没有完善的 `.env` 环境变量方案**，接口地址主要是直接写在代码里的。

### 7.5 本地接口地址位置

如果你需要改成本地其他地址，重点检查这些文件：

- `src/api/axios.ts`
- `src/pages/AnalyticsPage.tsx`
- `src/components/HotRankPanel.tsx`
- `src/utils/analytics.ts`
- `src/components/DanmakuDashboard.tsx`

---

## 8. 本地开发运行步骤

### 8.1 启动前端开发服务器

```bash
npm start
```

默认会启动在：

```text
http://localhost:3000
```

### 8.2 打开页面验证

建议按以下顺序验证：

1. 访问 `/`
2. 点击年龄确认进入 `/list`
3. 切换国产 / 亚洲 / 欧美
4. 点击顶部搜索，测试热门搜索词是否可用
5. 执行一次关键词搜索
6. 点击卡片进入详情页
7. 进入 `/rank/all` 查看榜单是否加载
8. 进入 `/analytics` 查看报表是否返回数据

### 8.3 本地开发常见问题

#### 问题 1：页面打开了，但列表没有数据

通常原因：

- `8085` 后端没有启动
- 后端接口地址不是 `localhost:8085`
- Nginx / 后端未正确放通跨域

#### 问题 2：数据分析页一直 loading

通常原因：

- `/api/reports/*` 接口未准备好
- 选中日期没有对应报表数据
- ETL 尚未生成当日数据

#### 问题 3：弹幕看板请求失败

通常原因：

- `8080` 服务未启动
- 当前组件未挂接正式路由
- 对应房间数据接口不存在

#### 问题 4：构建时出现 browserslist 警告

当前项目执行 `npm run build` 时会提示 `caniuse-lite` 数据较旧，这通常不影响构建成功，但建议后续统一维护浏览器兼容数据库。

---

## 9. 项目构建步骤

### 9.1 生成生产构建产物

```bash
npm run build
```

构建完成后会生成：

```text
build/
```

该目录就是前端静态产物，可直接交给 Nginx 托管。

### 9.2 当前构建状态

当前仓库执行 `npm run build` 可以成功构建，但有 ESLint 警告：

- `src/App.tsx` 中存在已导入但未使用的 `HotRankPanel`
- `src/App.tsx` 中存在已导入但未使用的 `DanmakuDashboard`

这不影响产物生成，但建议后续清理无用 import。

---

## 10. 项目部署方式

本项目当前最适合的部署方式是：

1. 本地或 CI 中执行 `npm run build`
2. 将 `build/` 目录交给 Nginx 托管
3. 通过 Nginx 代理 `/api` 到后端服务

下面分两种方式说明。

### 10.1 方式一：直接使用 Nginx 部署

#### 第一步：构建前端

```bash
npm install
npm run build
```

#### 第二步：将构建产物上传到服务器

把 `build/` 目录上传到你的 Nginx 静态目录，例如：

```text
/var/www/lsp_front/build
```

#### 第三步：配置 Nginx

你可以参考仓库里的：

- `nginx.conf`
- `default.conf`

当前 `nginx.conf` 的关键逻辑包括：

- `location /` 使用 `try_files $uri $uri/ /index.html`
  用于支持 React Router 的前端路由刷新不 404
- `location /api/` 代理到后端 `8085`
- `listen 443 ssl`
  表示支持 HTTPS

如果你使用自己的服务器，请重点修改：

- `server_name`
- `ssl_certificate`
- `ssl_certificate_key`
- `proxy_pass`
- 静态资源目录 `root`

#### 第四步：重载 Nginx

```bash
sudo nginx -t
sudo nginx -s reload
```

### 10.2 方式二：使用 Docker 部署

仓库中已提供 `Dockerfile`，但它有一个前提：

- 你必须先在本地生成 `build/` 目录

因为当前 `Dockerfile` 是直接把已有的 `build` 目录复制进镜像，而不是多阶段构建。

#### 第一步：本地先构建

```bash
npm install
npm run build
```

#### 第二步：构建镜像

```bash
docker build -t lsp-front:latest .
```

#### 第三步：运行容器

```bash
docker run -d \
  --name lsp-front \
  -p 80:80 \
  -p 443:443 \
  lsp-front:latest
```

#### 当前 Dockerfile 的行为说明

它会做以下事情：

- 使用 `nginx:latest` 作为基础镜像
- 拷贝 `nginx.conf`
- 拷贝 `default.conf`
- 拷贝证书文件
- 拷贝 `build/` 到容器内 `/etc/nginx/build`
- 启动 Nginx 前台进程

#### 部署时需要特别注意

- 如果你的证书文件名、路径、域名不同，需要同步修改 `Dockerfile` 和 `nginx.conf`
- 如果后端地址不是 `192.210.161.144:8085`，必须修改 `proxy_pass`
- 如果你不希望证书直接放在仓库里，建议改为运行时挂载

---

## 11. 推荐部署流程

如果是给团队使用，推荐按下面顺序执行：

1. 拉取前端代码
2. 执行 `npm ci`
3. 执行 `npm run build`
4. 检查 `build/` 产物是否生成成功
5. 按实际环境修改 Nginx 中的域名、证书、后端地址
6. 选择“直接 Nginx 部署”或“Docker + Nginx 部署”
7. 发布后验证以下功能：
   - 列表页
   - 搜索页
   - 详情页
   - 榜单页
   - 分析页
   - `/api` 代理是否正常

---

## 12. 开发与维护建议

### 12.1 建议补充环境变量方案

当前接口地址大量写死在源码中，建议后续统一改造成环境变量，例如：

- `REACT_APP_API_BASE_URL`
- `REACT_APP_REPORT_API_BASE_URL`
- `REACT_APP_ANALYTICS_API_BASE_URL`
- `REACT_APP_DANMAKU_API_BASE_URL`

这样可以更方便地区分：

- 本地开发环境
- 测试环境
- 生产环境

### 12.2 建议统一请求层

目前项目中既有 `axios`，也有大量 `fetch`。后续建议统一一层 API SDK，减少：

- 重复的错误处理
- 分散的 base URL
- 类型定义重复

### 12.3 建议清理当前遗留配置

建议排查并统一以下内容：

- `tailwind.config.ts` 是否还需要
- `eslint.config.mjs` 是否仍适用 CRA 项目
- 未使用的组件 import
- 未对外开放路由的实验性模块

### 12.4 建议补充文档与脚本

后续可以继续增加：

- `.env.example`
- `docker-compose.yml`
- 自动化部署脚本
- API 对接说明
- 页面截图 / 功能演示图

---

## 13. 常用命令速查

```bash
# 安装依赖
npm install

# 启动本地开发环境
npm start

# 运行测试
npm test

# 生成生产构建
npm run build

# 构建 Docker 镜像
docker build -t lsp-front:latest .

# 启动 Docker 容器
docker run -d --name lsp-front -p 80:80 -p 443:443 lsp-front:latest
```

---

## 14. 总结

这个项目已经具备一个完整内容站前端的核心形态：

- 有入口页
- 有列表浏览
- 有分类与标签筛选
- 有搜索能力
- 有详情展示
- 有热门榜单
- 有业务报表
- 有埋点能力
- 有 Nginx / Docker 部署基础

如果后续继续完善，优先建议投入在以下几个方向：

1. 把接口地址配置化
2. 统一请求层与错误处理
3. 清理历史配置与无用代码
4. 为弹幕看板等实验模块补齐正式路由和说明
5. 建立更标准的 CI/CD 与部署流程

如果你愿意，我下一步还可以继续帮你做两件事里的任意一个：

1. 把这个 README 再升级成“适合开源项目展示”的版本，加入徽章、架构图说明、目录锚点和更漂亮的模块排版
2. 顺手把项目里的接口地址改造成 `.env` 配置，并把 README 同步更新成真正可复制的多环境开发文档
