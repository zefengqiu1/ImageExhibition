import { useEffect, useMemo, useRef, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { ArrowLeft, Calendar, Clock3, Globe, ImageOff, Play, Tag, Tv2 } from 'lucide-react';
import { getVideoPost, VideoData } from '../api/acops';
import { analytics } from '../utils/analytics';
import { formatDate } from '../utils/format';
import styles from './ImageDetail.module.css';

type ViewTracker = ReturnType<typeof analytics.trackVideoView>;

const DATE_FORMAT = { year: 'numeric', month: 'long', day: 'numeric' } as const;

export default function ImageDetail() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();

  const [data, setData] = useState<VideoData | null>(null);
  const [loading, setLoading] = useState(true);
  const [coverError, setCoverError] = useState(false);

  const viewTrackerRef = useRef<ViewTracker | null>(null);

  useEffect(() => {
    const fetchData = async () => {
      if (!id) return;

      try {
        setLoading(true);
        const response = await getVideoPost(decodeURIComponent(id));
        setData(response);

        viewTrackerRef.current = analytics.trackVideoView({
          videoId: response.id,
          videoTitle: response.title || response.description,
          category: response.category,
        });
      } catch (error) {
        console.error('Failed to fetch video details:', error);
      } finally {
        setLoading(false);
      }
    };

    fetchData();

    return () => {
      viewTrackerRef.current?.updateDuration();
    };
  }, [id]);

  useEffect(() => {
    const handleScroll = () => {
      const scrollHeight = document.documentElement.scrollHeight - window.innerHeight;
      const scrollDepth = scrollHeight > 0 ? (window.scrollY / scrollHeight) * 100 : 0;
      viewTrackerRef.current?.updateScrollDepth(Math.min(scrollDepth, 100));
    };

    window.addEventListener('scroll', handleScroll);
    return () => window.removeEventListener('scroll', handleScroll);
  }, []);

  const handleBack = () => navigate(-1);

  const tags = useMemo(
    () => [
      { label: '类型', value: data?.type },
      { label: '地区', value: data?.region },
      { label: '语言', value: data?.language },
      { label: '年份', value: data?.year },
      { label: '画质', value: data?.quality },
      { label: '状态', value: data?.status },
    ].filter((item) => item.value),
    [data]
  );

  if (loading) {
    return (
      <div className={styles.container}>
        <div className={styles.loading}>
          <div className={styles.spinner} />
          <p>加载中...</p>
        </div>
      </div>
    );
  }

  if (!data) {
    return (
      <div className={styles.container}>
        <div className={styles.error}>
          <p>未找到视频信息</p>
          <button onClick={handleBack} className={styles.backButton}>返回</button>
        </div>
      </div>
    );
  }

  return (
    <div className={styles.container}>
      <div className={styles.topBar}>
        <button onClick={handleBack} className={styles.backButton}>
          <ArrowLeft size={18} />
          返回
        </button>
        <div className={styles.topMeta}>
          <span className={styles.badge}><Tv2 size={14} />视频详情</span>
          <span className={styles.subtle}>{data.category}</span>
        </div>
      </div>

      <div className={styles.layout}>
        <main className={styles.main}>
          <section className={styles.playerCard}>
            <div className={styles.playerHeader}>
              <h1 className={styles.title}>{data.title || '未命名视频'}</h1>
              <span className={styles.videoState}>{data.status}</span>
            </div>

            <div className={styles.playerFrame}>
              {data.imageUrl && !coverError ? (
                <img
                  src={data.imageUrl}
                  alt={data.title || '视频封面'}
                  className={styles.cover}
                  onError={() => setCoverError(true)}
                />
              ) : (
                <div className={styles.coverFallback}>
                  <ImageOff size={56} />
                  <span>封面暂无</span>
                </div>
              )}

              <div className={styles.playOverlay}>
                <button type="button" className={styles.playButton}>
                  <Play size={18} />
                  播放
                </button>
              </div>
            </div>

            <div className={styles.actionRow}>
              <div className={styles.actionItem}>
                <Calendar size={16} />
                {formatDate(data.createdAt, 'zh-CN', DATE_FORMAT)}
              </div>
              <div className={styles.actionItem}>
                <Clock3 size={16} />
                {data.year}
              </div>
              <div className={styles.actionItem}>
                <Globe size={16} />
                {data.region}
              </div>
            </div>
          </section>

          <section className={styles.infoCard}>
            <h2 className={styles.sectionTitle}>内容信息</h2>
            <div className={styles.tagGrid}>
              {tags.map((tag) => (
                <span key={tag.label} className={styles.tagChip}>
                  <Tag size={14} />
                  <strong>{tag.label}:</strong>
                  <span>{tag.value}</span>
                </span>
              ))}
            </div>

            <div className={styles.summary}>
              <h3 className={styles.summaryTitle}>简介</h3>
              <p className={styles.summaryText}>{data.description}</p>
            </div>
          </section>
        </main>

        <aside className={styles.side}>
          <section className={styles.sideCard}>
            <h2 className={styles.sectionTitle}>基础信息</h2>
            <dl className={styles.metaList}>
              <div>
                <dt>分类</dt>
                <dd>{data.category}</dd>
              </div>
              <div>
                <dt>类型</dt>
                <dd>{data.type}</dd>
              </div>
              <div>
                <dt>地区</dt>
                <dd>{data.region}</dd>
              </div>
              <div>
                <dt>语言</dt>
                <dd>{data.language}</dd>
              </div>
              <div>
                <dt>年份</dt>
                <dd>{data.year}</dd>
              </div>
              <div>
                <dt>画质</dt>
                <dd>{data.quality}</dd>
              </div>
              <div>
                <dt>状态</dt>
                <dd>{data.status}</dd>
              </div>
            </dl>
          </section>
        </aside>
      </div>
    </div>
  );
}
