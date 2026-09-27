import { useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import styles from './HotRankPanel.module.css';

interface ContentItem {
  videoId: string;
  rank: number;
  imageUrl: string;
  title: string;
  category?: string;
  description?: string;
  viewCount: number;
}

interface RankResponse {
  success: boolean;
  type?: string;
  title?: string;
  data?: ContentItem[];
  total?: number;
  message?: string;
}

type TabKey = 'realtime' | 'daily' | 'weekly' | 'monthly';
type CategoryKey = 'all' | 'movie' | 'drama' | 'variety';

const TABS: Array<{ key: TabKey; label: string }> = [
  { key: 'realtime', label: '实时榜（近5分钟）' },
  { key: 'daily', label: '日榜' },
  { key: 'weekly', label: '周榜' },
  { key: 'monthly', label: '月榜' },
];

const RANK_API_BY_TAB: Record<TabKey, string> = {
  realtime: '/api/videos/rank/realtime',
  daily: '/api/videos/rank/daily',
  weekly: '/api/videos/rank/weekly',
  monthly: '/api/videos/rank/monthly',
};

interface HotRankPanelProps {
  category: CategoryKey;
}

function HotRankPanel({ category }: HotRankPanelProps) {
  const [rankItems, setRankItems] = useState<ContentItem[]>([]);
  const [loading, setLoading] = useState(false);
  const [activeTab, setActiveTab] = useState<TabKey>('realtime');
  const latestRequestIdRef = useRef(0);
  const navigate = useNavigate();

  useEffect(() => {
    const controller = new AbortController();
    const requestId = latestRequestIdRef.current + 1;
    latestRequestIdRef.current = requestId;

    const fetchRanks = async () => {
      try {
        setLoading(true);
        setRankItems([]);
        const endpoint = RANK_API_BY_TAB[activeTab];
        const response = await fetch(
          `${endpoint}?top=10&category=${encodeURIComponent(category)}&t=${Date.now()}`,
          { signal: controller.signal }
        );
        const data: RankResponse = await response.json();
        if (requestId !== latestRequestIdRef.current) return;

        const items = Array.isArray(data.data) ? data.data : [];
        setRankItems(items);
      } catch (error) {
        if ((error as Error).name !== 'AbortError' && requestId === latestRequestIdRef.current) {
          console.error('Failed to fetch rank data:', error);
          setRankItems([]);
        }
      } finally {
        if (requestId === latestRequestIdRef.current) {
          setLoading(false);
        }
      }
    };

    fetchRanks();

    return () => controller.abort();
  }, [activeTab, category]);

  return (
    <div className={styles.hotRankPanel}>
      <div className={styles.tabs}>
        {TABS.map((tab) => (
          <button
            key={tab.key}
            className={`${styles.tabButton} ${activeTab === tab.key ? styles.tabButtonActive : ''}`}
            onClick={() => setActiveTab(tab.key)}
          >
            {tab.label}
          </button>
        ))}
      </div>
      {activeTab === 'realtime' && (
        <p className={styles.realtimeHint}>统计口径：过去 5 分钟浏览次数</p>
      )}

      <div className={styles.rankList}>
        {loading && <div className={styles.realtimeHint}>加载中...</div>}
        {rankItems.map((item) => (
          <div
            key={item.videoId}
            className={styles.rankItem}
            onClick={() => navigate(`/video/${encodeURIComponent(item.videoId)}`)}
          >
            <span className={styles.rankNumber}>{item.rank}</span>
            <img src={item.imageUrl} alt={item.title} className={styles.rankImage} />
            <div className={styles.info}>
              <h4 className={styles.infoTitle}>{item.title}</h4>
              <p className={styles.infoView}>浏览 {item.viewCount} 次</p>
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}

export default HotRankPanel;
