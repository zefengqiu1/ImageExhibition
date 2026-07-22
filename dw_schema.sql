-- ============================================
-- 1️⃣ 创建数据库
-- ============================================
CREATE DATABASE IF NOT EXISTS dw_ods;
CREATE DATABASE IF NOT EXISTS dw_dwd;
CREATE DATABASE IF NOT EXISTS dw_dws;
CREATE DATABASE IF NOT EXISTS dw_ads;
CREATE DATABASE IF NOT EXISTS dw_dim;

-- ============================================
-- 2️⃣ ODS 层 (原始数据)
-- ============================================
CREATE TABLE IF NOT EXISTS dw_ods.ods_page_view_log
(
    event_id String,
    user_id String,
    session_id String,
    device_type String,
    browser String,
    os String,
    screen_resolution String,
    page_type String,
    page_url String,
    referrer String,
    event_timestamp DateTime,
    etl_date DateTime,
    etl_timestamp DateTime DEFAULT now()
    ) ENGINE = MergeTree()
    PARTITION BY toYYYYMM(etl_date)
    ORDER BY (etl_date, event_timestamp, session_id)
    TTL etl_date + INTERVAL 90 DAY;

CREATE TABLE IF NOT EXISTS dw_ods.ods_image_view_log
(
    event_id String,
    user_id String,
    session_id String,
    device_type String,
    browser String,
    os String,
    image_id String,
    image_title String,
    country String,
    labels Array(String),
    view_duration UInt32,
    scroll_depth Float32,
    event_timestamp DateTime,
    etl_date DateTime,
    etl_timestamp DateTime DEFAULT now()
    ) ENGINE = MergeTree()
    PARTITION BY toYYYYMM(etl_date)
    ORDER BY (etl_date, event_timestamp, image_id)
    TTL etl_date + INTERVAL 90 DAY;

CREATE TABLE IF NOT EXISTS dw_ods.ods_search_log
(
    event_id String,
    user_id String,
    session_id String,
    device_type String,
    keyword String,
    result_count UInt32,
    clicked_results Array(String),
    click_positions Array(UInt16),
    sort_type String,
    filter_country String,
    filter_labels Array(String),
    event_timestamp DateTime,
    etl_date DateTime,
    etl_timestamp DateTime DEFAULT now()
    ) ENGINE = MergeTree()
    PARTITION BY toYYYYMM(etl_date)
    ORDER BY (etl_date, event_timestamp, keyword)
    TTL etl_date + INTERVAL 90 DAY;

CREATE TABLE IF NOT EXISTS dw_ods.ods_mongo_image_snapshot
(
    image_id String,
    title String,
    image_urls Array(String),
    website String,
    labels Array(String),
    country String,
    description String,
    created_at DateTime,
    snapshot_date DateTime,
    etl_timestamp DateTime DEFAULT now()
    ) ENGINE = ReplacingMergeTree(etl_timestamp)
    PARTITION BY toYYYYMM(snapshot_date)
    ORDER BY (snapshot_date, image_id);

CREATE TABLE IF NOT EXISTS dw_ods.ods_redis_rank
(
    rank_type String,
    image_id String,
    view_count UInt32,
    rank_position UInt32,
    snapshot_datetime DateTime,
    etl_datetime DateTime DEFAULT now()
    ) ENGINE = MergeTree()
    ORDER BY (rank_type, image_id);

-- ============================================
-- 3️⃣ DIM 层 (维度表)
-- ============================================
CREATE TABLE IF NOT EXISTS dw_dim.dim_date
(
    date_key UInt32,
    full_date DateTime,
    year UInt16,
    quarter UInt8,
    month UInt8,
    week UInt8,
    day_of_month UInt8,
    day_of_week UInt8,
    day_name String,
    is_weekend UInt8,
    is_holiday UInt8,
    holiday_name String
) ENGINE = MergeTree()
    ORDER BY date_key;

CREATE TABLE IF NOT EXISTS dw_dim.dim_time
(
    time_key UInt32,
    hour UInt8,
    minute UInt8,
    second UInt8,
    time_period String,
    is_work_hour UInt8
) ENGINE = MergeTree()
    ORDER BY time_key;

DROP TABLE IF EXISTS dw_dim.dim_image;

CREATE TABLE dw_dim.dim_image
(
    image_key       UInt64,
    image_id        String,
    title           String,
    country         String,
    website         String,
    labels          String,
    label_count     UInt16,
    image_count     UInt16,
    created_date    DateTime64(3),
    age_days        UInt32,
    effective_date  DateTime64(3),
    expiration_date DateTime64(3),
    is_current      UInt8,
    etl_timestamp   DateTime64(3) DEFAULT now64()
) ENGINE = MergeTree
ORDER BY image_key;

CREATE TABLE IF NOT EXISTS dw_dim.dim_user
(
    user_key UInt64,
    user_id String,
    user_type String,
    first_visit_date DateTime,
    total_sessions UInt32,
    total_views UInt32,
    preferred_country String,
    preferred_labels Array(String),
    avg_session_duration UInt32,
    effective_date DateTime,
    is_current UInt8,
    etl_timestamp DateTime DEFAULT now()
    ) ENGINE = ReplacingMergeTree(etl_timestamp)
    ORDER BY (user_id, effective_date);

CREATE TABLE IF NOT EXISTS dw_dim.dim_device
(
    device_key UInt32,
    device_type String,
    browser String,
    browser_version String,
    os String,
    os_version String,
    screen_resolution String,
    is_mobile UInt8
) ENGINE = MergeTree()
    ORDER BY device_key;

-- ============================================
-- 4️⃣ DWD 层 (明细数据)
-- ============================================
CREATE TABLE IF NOT EXISTS dw_dwd.dwd_image_view_detail
(
    view_id String,
    date_key UInt32,
    time_key UInt32,
    user_key UInt64,
    image_key UInt64,
    session_id String,
    device_key UInt32,
    user_id String,
    device_type String,
    browser String,
    os String,
    image_id String,
    image_title String,
    country String,
    labels Array(String),
    view_duration UInt32,
    scroll_depth Float32,
    is_bounce UInt8,
    view_date DateTime,
    view_hour UInt8,
    view_timestamp DateTime,
    etl_date DateTime,
    etl_timestamp DateTime DEFAULT now()
    ) ENGINE = MergeTree()
    PARTITION BY toYYYYMM(view_date)
    ORDER BY (view_date, view_timestamp, image_id)
    TTL view_date + INTERVAL 180 DAY;

CREATE TABLE IF NOT EXISTS dw_dwd.dwd_search_detail
(
    search_id String,
    date_key UInt32,
    time_key UInt32,
    user_key UInt64,
    session_id String,
    device_key UInt32,
    user_id String,
    device_type String,
    keyword String,
    keyword_category String,
    result_count UInt32,
    clicked_count UInt16,
    avg_click_position Float32,
    has_result UInt8,
    sort_type String,
    search_datetime DateTime,
    etl_datetime DateTime DEFAULT now()
    ) ENGINE = MergeTree()
    PARTITION BY toYYYYMM(search_datetime)
    ORDER BY (search_datetime, keyword)
    TTL search_datetime + INTERVAL 180 DAY;

-- ============================================
-- 5️⃣ DWS 层 (汇总数据)
-- ============================================
-- 1. 实时图片统计表（窗口聚合结果）
CREATE TABLE IF NOT EXISTS dw_dws.dws_image_realtime_stats
(
    window_start DateTime COMMENT '窗口开始时间',
    window_end DateTime COMMENT '窗口结束时间',
    image_id String COMMENT '图片ID',
    pv UInt64 COMMENT '浏览量',
    uv UInt64 COMMENT '独立访客数',
    avg_duration Float64 COMMENT '平均浏览时长(秒)',

    -- 扩展字段（可选）
    image_title String DEFAULT '' COMMENT '图片标题',
    country String DEFAULT '' COMMENT '地区',
    window_duration_minutes UInt16 DEFAULT 5 COMMENT '窗口时长(分钟)',

    -- 时间戳
    update_time DateTime DEFAULT now() COMMENT '更新时间'
    )
    ENGINE = MergeTree()
    PARTITION BY toYYYYMMDD(window_start)
    ORDER BY (window_start, image_id)
    TTL window_start + INTERVAL 7 DAY  -- 保留7天数据
    SETTINGS index_granularity = 8192
    COMMENT '实时图片浏览统计（5分钟窗口）';

CREATE TABLE IF NOT EXISTS dw_dws.dws_image_daily_stats
(
    stat_date DateTime,
    image_id String,
    image_title String,
    country String,
    pv UInt32,
    uv UInt32,
    avg_view_duration Float32,
    bounce_rate Float32,
    avg_scroll_depth Float32,
    mobile_rate Float32,
    top_regions Array(String),
    daily_rank UInt32,
    weekly_rank UInt32,
    monthly_rank UInt32,
    etl_timestamp DateTime DEFAULT now()
    ) ENGINE = ReplacingMergeTree(etl_timestamp)
    PARTITION BY toYYYYMM(stat_date)
    ORDER BY (stat_date, image_id);

CREATE TABLE IF NOT EXISTS dw_dws.dws_search_keyword_daily
(
    stat_date DateTime,
    keyword String,
    keyword_category String,
    search_count UInt32,
    click_count UInt32,
    ctr Float32,
    avg_result_count Float32,
    avg_click_position Float32,
    unique_users UInt32,
    etl_timestamp DateTime DEFAULT now()
    ) ENGINE = ReplacingMergeTree(etl_timestamp)
    PARTITION BY toYYYYMM(stat_date)
    ORDER BY (stat_date, keyword);

CREATE TABLE IF NOT EXISTS dw_dws.dws_user_daily_behavior
(
    stat_date DateTime,
    user_id String,
    session_count UInt16,
    page_view_count UInt32,
    image_view_count UInt32,
    search_count UInt32,
    total_duration UInt32,
    avg_session_duration Float32,
    preferred_country String,
    preferred_labels Array(String),
    device_types Array(String),
    etl_timestamp DateTime DEFAULT now()
    ) ENGINE = ReplacingMergeTree(etl_timestamp)
    PARTITION BY toYYYYMM(stat_date)
    ORDER BY (stat_date, user_id);

-- ============================================
-- 6️⃣ ADS 层 (应用数据)
-- ============================================
CREATE TABLE IF NOT EXISTS dw_ads.ads_operation_overview
(
    report_date DateTime,
    total_pv UInt64,
    total_uv UInt64,
    new_users UInt32,
    returning_users UInt32,
    avg_session_duration Float32,
    bounce_rate Float32,
    total_images UInt32,
    new_images_today UInt32,
    active_images UInt32,
    total_searches UInt32,
    unique_keywords UInt32,
    avg_search_result Float32,
    search_ctr Float32,
    top_countries String,
    top_regions String,
    mobile_rate Float32,
    desktop_rate Float32,
    created_at DateTime DEFAULT now()
    ) ENGINE = ReplacingMergeTree(created_at)
    ORDER BY report_date;

CREATE TABLE IF NOT EXISTS dw_ads.ads_content_analysis
(
    report_date DateTime,
    country String,
    total_images UInt32,
    new_images UInt32,
    avg_view_duration Float32,
    avg_bounce_rate Float32,
    high_quality_images UInt32,
    hot_labels String,
    trending_images String,
    preferred_by_regions String,
    created_at DateTime DEFAULT now()
    ) ENGINE = ReplacingMergeTree(created_at)
    PARTITION BY toYYYYMM(report_date)
    ORDER BY (report_date, country);

CREATE TABLE IF NOT EXISTS dw_ads.ads_user_retention
(
    cohort_date DateTime,
    retention_day UInt16,
    initial_users UInt32,
    retained_users UInt32,
    retention_rate Float32,
    created_at DateTime DEFAULT now()
    ) ENGINE = ReplacingMergeTree(created_at)
    ORDER BY (cohort_date, retention_day);

-- ============================================
-- 7️⃣ 初始化日期维度（2024-2026）
-- ============================================
INSERT INTO dw_dim.dim_date
SELECT
    toUInt32(toYYYYMMDD(date_value)) AS date_key,
    date_value AS full_date,
    toYear(date_value) AS year,
    toQuarter(date_value) AS quarter,
    toMonth(date_value) AS month,
    toISOWeek(date_value) AS week,
    toDayOfMonth(date_value) AS day_of_month,
    toDayOfWeek(date_value) AS day_of_week,
    dateName('weekday', date_value) AS day_name,
    if(toDayOfWeek(date_value) IN (6, 7), 1, 0) AS is_weekend,
    0 AS is_holiday,
    '' AS holiday_name
FROM (
    SELECT toDateTime('2024-01-01') + toIntervalDay(number) AS date_value
    FROM numbers(1096)
    )
WHERE date_value <= toDateTime('2026-12-31');

-- ============================================
-- 8️⃣ 初始化时间维度（24小时 × 60分 × 60秒）
-- ============================================
INSERT INTO dw_dim.dim_time
SELECT
    number AS time_key,
    toUInt8(number / 3600) AS hour,
    toUInt8((number % 3600) / 60) AS minute,
    toUInt8(number % 60) AS second,
    CASE
        WHEN number / 3600 >= 0 AND number / 3600 < 6 THEN '深夜'
        WHEN number / 3600 >= 6 AND number / 3600 < 9 THEN '早晨'
        WHEN number / 3600 >= 9 AND number / 3600 < 12 THEN '上午'
        WHEN number / 3600 >= 12 AND number / 3600 < 14 THEN '中午'
        WHEN number / 3600 >= 14 AND number / 3600 < 18 THEN '下午'
        WHEN number / 3600 >= 18 AND number / 3600 < 22 THEN '晚上'
        ELSE '深夜'
END AS time_period,
    if(number / 3600 >= 9 AND number / 3600 < 18, 1, 0) AS is_work_hour
FROM numbers(86400);
