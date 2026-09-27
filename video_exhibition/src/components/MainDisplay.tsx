import { useCallback, useEffect, useState } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { fetchThemePosts, ListTheme, VideoData } from '../api/acops';
import VideoCard from './VideoCard';
import PageSection from './PageSection';
import styles from './MainDisplay.module.css';

const STATE_KEY = 'listPageState';
const STATE_EXPIRE_MS = 5 * 60 * 1000;

const getInitialState = (pathname: string, search: string) => {
  try {
    const saved = sessionStorage.getItem(STATE_KEY);
    if (!saved) return null;

    const state = JSON.parse(saved);
    const currentPath = pathname + search;
    if (state.path === currentPath && Date.now() - state.timestamp < STATE_EXPIRE_MS) {
      return state;
    }
  } catch (error) {
    console.error('恢复状态失败', error);
  }

  return null;
};

function MainDisplay() {
  const location = useLocation();
  const navigate = useNavigate();

  const savedState = getInitialState(location.pathname, location.search);
  const [data, setData] = useState<VideoData[]>([]);
  const [page, setPage] = useState(savedState?.page ?? 0);
  const [total, setTotal] = useState(1);
  const [isAsc, setIsAsc] = useState(false);

  const getApiType = useCallback((): ListTheme => {
    const path = location.pathname;
    if (path.includes('/drama')) return 'drama';
    if (path.includes('/variety')) return 'variety';
    return 'movie';
  }, [location.pathname]);

  const saveCurrentState = useCallback(() => {
    sessionStorage.setItem(
      STATE_KEY,
      JSON.stringify({
        path: location.pathname + location.search,
        page,
        scrollY: window.scrollY,
        timestamp: Date.now(),
      })
    );
  }, [location.pathname, location.search, page]);

  useEffect(() => {
    if (!savedState || data.length === 0) return;

    const timer = setTimeout(() => {
      window.scrollTo({ top: savedState.scrollY, behavior: 'auto' });
    }, 50);

    return () => clearTimeout(timer);
  }, [data, savedState]);

  useEffect(() => {
    let scrollTimer: NodeJS.Timeout;

    const handleScroll = () => {
      clearTimeout(scrollTimer);
      scrollTimer = setTimeout(saveCurrentState, 300);
    };

    window.addEventListener('scroll', handleScroll);
    return () => {
      window.removeEventListener('scroll', handleScroll);
      clearTimeout(scrollTimer);
    };
  }, [saveCurrentState]);

  useEffect(() => {
    saveCurrentState();
  }, [page, saveCurrentState]);

  const handlePageChange = useCallback((newPage: number) => {
    setPage(newPage);
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }, []);

  const toggleSortOrder = useCallback(() => {
    const nextAsc = !isAsc;
    setIsAsc(nextAsc);
    navigate(`${location.pathname}?sortDir=${nextAsc ? 'asc' : 'desc'}`);
  }, [isAsc, location.pathname, navigate]);

  useEffect(() => {
    const fetchData = async () => {
      const params = new URLSearchParams(location.search);
      params.set('sortDir', isAsc ? 'asc' : 'desc');
      params.set('page', String(page));

      try {
        const response = await fetchThemePosts(getApiType(), params);
        if (!response) return;

        setData(Array.isArray(response.imageUrlList) ? response.imageUrlList : []);
        setTotal(response.total);
      } catch (error) {
        console.error('Error fetching data:', error);
      }
    };

    fetchData();
  }, [getApiType, isAsc, location.search, page]);

  return (
    <div className={styles.maindisplay}>
      <div className={styles.filterbar}>
        <ul>
          <li className={styles.order} onClick={toggleSortOrder}>
            添加时间 {isAsc ? '↑' : '↓'}
          </li>
          <li className={styles.order}>评分高低</li>
          <li className={styles.order}>共有{total}个筛选结果</li>
        </ul>

        <div>
          <ul>
            <li className={styles.order} onClick={() => { window.location.href = '/rank/all'; }}>
              排行
            </li>
          </ul>
        </div>
      </div>

      <div className={styles.imageGrid}>
        {data.map((content) => (
          <div key={content.id} className={styles.imageItem}>
            <VideoCard result={content} />
          </div>
        ))}
      </div>

      <PageSection page={page} total={total} setPage={handlePageChange} />
    </div>
  );
}

export default MainDisplay;
