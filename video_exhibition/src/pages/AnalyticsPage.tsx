import { useCallback, useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  BarChart3,
  Clock,
  Eye,
  Globe,
  Image as ImageIcon,
  Search,
  TrendingUp,
  Users,
} from 'lucide-react';
import Footer from '../components/Footer';
import Header from '../components/Header';
import { formatNumber, formatPercent, getCountryLabel } from '../utils/format';
import styles from './AnalyticsPage.module.css';

interface OverviewData {
  report_date: string;
  total_pv: number | undefined;
  total_uv: number | undefined;
  avg_session_duration: number | undefined;
  bounce_rate: number | undefined;
  total_images: number | undefined;
  active_images: number | undefined;
  total_searches: number | undefined;
  unique_keywords: number | undefined;
  search_ctr: number | undefined;
  mobile_rate: number | undefined;
  desktop_rate: number | undefined;
  dataSource: string;
}

interface TrafficTrendData {
  date: string;
  pv: number | undefined;
  uv: number | undefined;
  bounceRate: number | undefined;
  mobileRate: number | undefined;
}

interface TopImage {
  imageId: string;
  title: string;
  country: string;
  pv: number | undefined;
  uv: number | undefined;
  avgDuration: number | undefined;
  bounceRate: number | undefined;
  avgScrollDepth: number | undefined;
  mobileRate: number | undefined;
}

interface TopKeyword {
  keyword: string;
  searchCount: number | undefined;
  clickCount: number | undefined;
  ctr: number | undefined;
  avgResultCount: number | undefined;
  avgClickPosition: number | undefined;
  uniqueUsers: number | undefined;
}

interface CountryDistribution {
  country: string;
  totalPv: number | undefined;
  totalUv: number | undefined;
  avgDuration: number | undefined;
  avgBounceRate: number | undefined;
  mobileRate: number | undefined;
}

const baseUrl = '/api/reports';
const quickDateButtons = [
  { label: '昨天', offset: 1 },
  { label: '7天前', offset: 7 },
  { label: '30天前', offset: 30 },
];

const getLocalDateString = (date: Date): string => {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
};

const getDefaultSelectedDate = () => {
  const yesterday = new Date();
  yesterday.setDate(yesterday.getDate() - 1);
  return getLocalDateString(yesterday);
};

export default function AnalyticsPage() {
  const navigate = useNavigate();
  const [overview, setOverview] = useState<OverviewData | null>(null);
  const [trafficTrend, setTrafficTrend] = useState<TrafficTrendData[]>([]);
  const [topImages, setTopImages] = useState<TopImage[]>([]);
  const [topKeywords, setTopKeywords] = useState<TopKeyword[]>([]);
  const [countryDist, setCountryDist] = useState<CountryDistribution[]>([]);
  const [loading, setLoading] = useState(true);
  const [selectedDays, setSelectedDays] = useState(30);
  const [etlRunning, setEtlRunning] = useState(false);
  const [etlMessage, setEtlMessage] = useState('');
  const [selectedDate, setSelectedDate] = useState(getDefaultSelectedDate);

  const fetchOverview = useCallback(async () => {
    const response = await fetch(`${baseUrl}/overview/daily?date=${selectedDate}`);
    setOverview(await response.json());
  }, [selectedDate]);

  const fetchTrafficTrend = useCallback(async () => {
    const response = await fetch(`${baseUrl}/trend/traffic?days=${selectedDays}`);
    setTrafficTrend(await response.json());
  }, [selectedDays]);

  const fetchTopImages = useCallback(async () => {
    const response = await fetch(`${baseUrl}/top/images?date=${selectedDate}&limit=10`);
    setTopImages(await response.json());
  }, [selectedDate]);

  const fetchTopKeywords = useCallback(async () => {
    const response = await fetch(`${baseUrl}/top/keywords?date=${selectedDate}&limit=20`);
    setTopKeywords(await response.json());
  }, [selectedDate]);

  const fetchCountryDistribution = useCallback(async () => {
    const response = await fetch(`${baseUrl}/distribution/country?date=${selectedDate}`);
    setCountryDist(await response.json());
  }, [selectedDate]);

  const fetchAllData = useCallback(async () => {
    setLoading(true);
    try {
      await Promise.all([
        fetchOverview(),
        fetchTrafficTrend(),
        fetchTopImages(),
        fetchTopKeywords(),
        fetchCountryDistribution(),
      ]);
    } catch (error) {
      console.error('Failed to fetch analytics data:', error);
    } finally {
      setLoading(false);
    }
  }, [fetchCountryDistribution, fetchOverview, fetchTopImages, fetchTopKeywords, fetchTrafficTrend]);

  useEffect(() => {
    fetchAllData();
  }, [fetchAllData]);

  const handleRunETL = async () => {
    if (etlRunning) return;

    setEtlRunning(true);
    setEtlMessage('正在执行 ETL 任务...');

    try {
      const response = await fetch('/api/etl/run-daily', { method: 'POST' });
      const result = await response.text();
      setEtlMessage(result);

      if (result.includes('成功')) {
        setTimeout(() => {
          fetchAllData();
          setEtlMessage('');
        }, 2000);
      }
    } catch (error) {
      setEtlMessage('ETL 执行失败: ' + (error as Error).message);
    } finally {
      setEtlRunning(false);
    }
  };

  const maxPv = useMemo(
    () => trafficTrend.reduce((max, item) => Math.max(max, Number(item.pv) || 0), 0),
    [trafficTrend]
  );

  const metricCards = [
    { label: '总浏览量 (PV)', value: formatNumber(overview?.total_pv || 0), icon: Eye, color: '#3b82f6' },
    { label: '访客数 (UV)', value: formatNumber(overview?.total_uv || 0), icon: Users, color: '#10b981' },
    { label: '平均停留时间', value: `${Math.round(overview?.avg_session_duration || 0)}s`, icon: Clock, color: '#f59e0b' },
    { label: '活跃图片', value: formatNumber(overview?.active_images || 0), icon: ImageIcon, color: '#8b5cf6' },
    { label: '搜索次数', value: formatNumber(overview?.total_searches || 0), icon: Search, color: '#ec4899' },
    { label: '跳出率', value: formatPercent(overview?.bounce_rate || 0), icon: TrendingUp, color: '#14b8a6' },
  ];

  if (loading) {
    return (
      <div className={styles.container}>
        <Header />
        <div className={styles.loading}>
          <div className={styles.spinner}></div>
          <p>加载数据中...</p>
        </div>
        <Footer />
      </div>
    );
  }

  return (
    <div className={styles.container}>
      <Header />

      <div className={styles.content}>
        <div className={styles.pageHeader}>
          <div className={styles.headerLeft}>
            <h1>
              <BarChart3 size={32} />
              数据分析报表
            </h1>
            <p className={styles.subtitle}>
              数据日期: {selectedDate}
              {overview?.dataSource === 'mock' && <span className={styles.mockBadge}>测试数据</span>}
              {etlMessage && (
                <span className={etlMessage.includes('成功') ? styles.successBadge : styles.infoBadge}>
                  {etlMessage}
                </span>
              )}
            </p>
          </div>
          <div className={styles.controlsWrapper}>
            <div className={styles.dateSelector}>
              <label htmlFor="dateInput">选择日期：</label>
              <input
                id="dateInput"
                type="date"
                value={selectedDate}
                onChange={(event) => setSelectedDate(event.target.value)}
                max={getLocalDateString(new Date())}
                className={styles.dateInput}
              />
              {quickDateButtons.map((button) => (
                <button
                  key={button.label}
                  onClick={() => {
                    const target = new Date();
                    target.setDate(target.getDate() - button.offset);
                    setSelectedDate(getLocalDateString(target));
                  }}
                  className={styles.todayButton}
                >
                  {button.label}
                </button>
              ))}
            </div>
            <button
              onClick={handleRunETL}
              disabled={etlRunning}
              className={styles.etlButton}
              title="手动触发 ETL 数据处理任务"
            >
              {etlRunning ? (
                <>
                  <div className={styles.miniSpinner}></div>
                  执行中...
                </>
              ) : (
                <>
                  <TrendingUp size={16} />
                  运行 ETL
                </>
              )}
            </button>
          </div>
        </div>

        <div className={styles.metricsGrid}>
          {metricCards.map((metric) => {
            const Icon = metric.icon;
            return (
              <div key={metric.label} className={styles.metricCard}>
                <div className={styles.metricIcon} style={{ background: metric.color }}>
                  <Icon size={24} />
                </div>
                <div className={styles.metricContent}>
                  <div className={styles.metricLabel}>{metric.label}</div>
                  <div className={styles.metricValue}>{metric.value}</div>
                </div>
              </div>
            );
          })}
        </div>

        <div className={styles.section}>
          <div className={styles.sectionHeader}>
            <h2>流量趋势</h2>
            <div className={styles.periodSelector}>
              <button className={selectedDays === 7 ? styles.active : ''} onClick={() => setSelectedDays(7)}>7天</button>
              <button className={selectedDays === 30 ? styles.active : ''} onClick={() => setSelectedDays(30)}>30天</button>
            </div>
          </div>
          <div className={styles.chartContainer}>
            <div className={styles.simpleChart}>
              {trafficTrend.map((item, index) => {
                const pvValue = Number(item.pv) || 0;
                const heightPercent = maxPv > 0 ? (pvValue / maxPv) * 100 : 0;

                return (
                  <div key={index} className={styles.chartBar}>
                    <div className={styles.chartBarFill} style={{ height: `${heightPercent}%` }}>
                      <span className={styles.chartBarValue}>{formatNumber(pvValue)}</span>
                    </div>
                    <div className={styles.chartBarLabel}>
                      {new Date(item.date).toLocaleDateString('zh-CN', { month: 'numeric', day: 'numeric' })}
                    </div>
                  </div>
                );
              })}
            </div>
          </div>
        </div>

        <div className={styles.section}>
          <div className={styles.sectionHeader}>
            <h2>热门图片 TOP 10</h2>
          </div>
          <div className={styles.tableContainer}>
            <table className={styles.table}>
              <thead>
                <tr>
                  <th>排名</th>
                  <th>标题</th>
                  <th>地区</th>
                  <th>浏览量</th>
                  <th>访客数</th>
                  <th>平均时长</th>
                  <th>跳出率</th>
                  <th>移动端占比</th>
                </tr>
              </thead>
              <tbody>
                {topImages.map((img, index) => (
                  <tr
                    key={img.imageId}
                    onClick={() => navigate(`/image/${encodeURIComponent(img.title)}`)}
                    className={styles.clickableRow}
                  >
                    <td><span className={styles.rank}>#{index + 1}</span></td>
                    <td className={styles.titleCell} title={img.title}>{img.title}</td>
                    <td><span className={styles.countryBadge}>{getCountryLabel(img.country)}</span></td>
                    <td>{formatNumber(img.pv)}</td>
                    <td>{formatNumber(img.uv)}</td>
                    <td>{Math.round(Number(img.avgDuration) || 0)}s</td>
                    <td>{formatPercent(img.bounceRate)}</td>
                    <td>{formatPercent(img.mobileRate)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>

        <div className={styles.section}>
          <div className={styles.sectionHeader}>
            <h2>热门搜索词 TOP 20</h2>
          </div>
          <div className={styles.keywordGrid}>
            {topKeywords.map((keyword, index) => (
              <div key={keyword.keyword} className={styles.keywordCard}>
                <div className={styles.keywordRank}>#{index + 1}</div>
                <div className={styles.keywordContent}>
                  <div className={styles.keywordName}>{keyword.keyword}</div>
                  <div className={styles.keywordStats}>
                    <span>搜索 {formatNumber(keyword.searchCount)}</span>
                    <span>点击率 {formatPercent(keyword.ctr)}</span>
                  </div>
                </div>
              </div>
            ))}
          </div>
        </div>

        <div className={styles.section}>
          <div className={styles.sectionHeader}>
            <h2>
              <Globe size={20} />
              地区分布
            </h2>
          </div>
          <div className={styles.countryGrid}>
            {countryDist.map((country) => (
              <div key={country.country} className={styles.countryCard}>
                <div className={styles.countryHeader}>
                  <h3>{getCountryLabel(country.country)}</h3>
                  <span className={styles.countryPv}>{formatNumber(country.totalPv)} PV</span>
                </div>
                <div className={styles.countryStats}>
                  <div className={styles.countryStat}>
                    <span className={styles.countryStatLabel}>访客数</span>
                    <span className={styles.countryStatValue}>{formatNumber(country.totalUv)}</span>
                  </div>
                  <div className={styles.countryStat}>
                    <span className={styles.countryStatLabel}>平均时长</span>
                    <span className={styles.countryStatValue}>{Math.round(Number(country.avgDuration) || 0)}s</span>
                  </div>
                  <div className={styles.countryStat}>
                    <span className={styles.countryStatLabel}>跳出率</span>
                    <span className={styles.countryStatValue}>{formatPercent(country.avgBounceRate)}</span>
                  </div>
                  <div className={styles.countryStat}>
                    <span className={styles.countryStatLabel}>移动端</span>
                    <span className={styles.countryStatValue}>{formatPercent(country.mobileRate)}</span>
                  </div>
                </div>
              </div>
            ))}
          </div>
        </div>

        <div className={styles.section}>
          <div className={styles.sectionHeader}>
            <h2>设备分布</h2>
          </div>
          <div className={styles.deviceDistribution}>
            <div className={styles.deviceBar}>
              <div className={styles.deviceBarMobile} style={{ width: `${overview?.mobile_rate || 0}%` }}>
                <span>移动端 {formatPercent(overview?.mobile_rate || 0)}</span>
              </div>
              <div className={styles.deviceBarDesktop} style={{ width: `${overview?.desktop_rate || 0}%` }}>
                <span>桌面端 {formatPercent(overview?.desktop_rate || 0)}</span>
              </div>
            </div>
          </div>
        </div>
      </div>

      <Footer />
    </div>
  );
}
