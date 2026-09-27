import { useEffect, useMemo, useRef, useState } from 'react';
import { Eye, Search } from 'lucide-react';
import { useLocation, useNavigate } from 'react-router-dom';
import { VideoSearchResult, searchVideos } from '../api/acops';
import Footer from '../components/Footer';
import Header from '../components/Header';
import PageSection from '../components/PageSection';
import { analytics } from '../utils/analytics';
import styles from './SearchList.module.css';

const PAGE_SIZE = 10;
type SearchTracker = ReturnType<typeof analytics.trackSearch>;

export default function SearchList() {
  const location = useLocation();
  const navigate = useNavigate();

  const [searchResults, setSearchResults] = useState<VideoSearchResult[]>([]);
  const [loading, setLoading] = useState(false);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(0);
  const [currentQuery, setCurrentQuery] = useState('');

  const searchTrackerRef = useRef<SearchTracker | null>(null);
  const previousCriteriaRef = useRef<string>('');

  const keyword = useMemo(
    () => new URLSearchParams(location.search).get('query')?.trim() || '',
    [location.search]
  );

  useEffect(() => {
    analytics.trackPageView('search');
  }, []);

  useEffect(() => {
    const criteriaKey = keyword;
    const criteriaChanged = previousCriteriaRef.current !== criteriaKey;
    if (criteriaChanged) {
      previousCriteriaRef.current = criteriaKey;
      if (page !== 0) {
        setPage(0);
        return;
      }
    }

    const fetchSearch = async () => {
      setLoading(true);
      try {
        const response = await searchVideos(keyword, page, PAGE_SIZE);
        const list = Array.isArray(response?.list) ? response.list : [];
        const totalCount = Number.isFinite(response?.total) ? response.total : 0;

        setSearchResults(list);
        setTotal(totalCount);
        setCurrentQuery(keyword);

        if (page === 0) {
          searchTrackerRef.current = analytics.trackSearch({
            keyword,
            resultCount: totalCount,
            sortType: 'page',
          });
        }
      } catch (error) {
        console.error('搜索失败:', error);
        setSearchResults([]);
        setTotal(0);
      } finally {
        setLoading(false);
      }
    };

    fetchSearch();
  }, [keyword, page]);

  const handlePageChange = (nextPage: number) => {
    setPage(nextPage);
    window.scrollTo({ top: 0, behavior: 'smooth' });
  };

  return (
    <div className={styles.maindisplay}>
      <Header />

      <section className={styles.searchOverview}>
        <div className={styles.searchMeta}>
          <span className={styles.searchMetaLabel}>
            <Search size={14} />
            搜索词
          </span>
          <strong>{currentQuery || '未输入关键词'}</strong>
        </div>
        <div className={styles.searchStats}>
          <div className={styles.statCard}>
            <Eye size={15} />
            命中总数 {total}
          </div>
          <div className={styles.statCard}>
            当前第 {page + 1} 页
          </div>
          <div className={styles.statCard}>
            当前页展示 {searchResults.length}
          </div>
        </div>
      </section>

      <main className={styles.mainContent}>
        {loading ? (
          <div className={styles.loadingContainer}>
            <div className={styles.spinner}></div>
            <span className={styles.loadingText}>搜索中...</span>
          </div>
        ) : searchResults.length > 0 ? (
          <>
            <div className={styles.resultsContainer}>
              {searchResults.map((result, index) => (
                <div
                  key={`${result.id}-${index}`}
                  onClick={() => {
                    searchTrackerRef.current?.recordClick(result.id, page * PAGE_SIZE + index + 1);
                  navigate(`/video/${encodeURIComponent(result.id)}`);
                  }}
                >
                  <article className={styles.videoResultCard}>
                    {result.imageUrl ? (
                      <img src={result.imageUrl} alt={result.title || '视频封面'} />
                    ) : (
                      <div className={styles.videoResultPlaceholder}>暂无封面</div>
                    )}
                    <div className={styles.videoResultContent}>
                      <h3>{result.highlightTitle || result.title}</h3>
                      {result.description && <p>{result.description}</p>}
                    </div>
                  </article>
                </div>
              ))}
            </div>

            <PageSection page={page} total={total} setPage={handlePageChange} />
          </>
        ) : (
          <div className={styles.noResults}>
            <div className={styles.noResultsIcon}>
              <Search size={30} />
            </div>
            <h3 className={styles.noResultsTitle}>暂无搜索结果</h3>
            <p className={styles.noResultsDescription}>
              {currentQuery
                ? `没有找到与 "${currentQuery}" 相关的视频`
                : '请输入搜索关键词'}
            </p>
          </div>
        )}
      </main>
      <Footer />
    </div>
  );
}
