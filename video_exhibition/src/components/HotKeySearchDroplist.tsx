import { useEffect, useState } from 'react';
import { getHotKeyList } from '../api/acops';
import styles from './HotKeySearchDroplist.module.css';

interface HotKeySearchDroplistProps {
  onKeywordSelect?: (keyword: string) => void;
  maxItems?: number;
}

const getRankClassName = (index: number) => {
  if (index === 0) return styles.top1;
  if (index === 1) return styles.top2;
  if (index === 2) return styles.top3;
  return styles.normal;
};

export default function HotKeySearchDroplist({
  onKeywordSelect,
  maxItems = 10,
}: HotKeySearchDroplistProps) {
  const [hotKeys, setHotKeys] = useState<string[]>([]);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    let mounted = true;

    const fetchHotKeys = async () => {
      setLoading(true);
      try {
        const data = await getHotKeyList();
        if (mounted) {
          setHotKeys(data.slice(0, maxItems));
        }
      } catch (error) {
        console.error('Failed to fetch hot keys', error);
        if (mounted) {
          setHotKeys([]);
        }
      } finally {
        if (mounted) {
          setLoading(false);
        }
      }
    };

    fetchHotKeys();

    return () => {
      mounted = false;
    };
  }, [maxItems]);

  if (loading) {
    return (
      <div className={styles.dropdown}>
        <div className={styles.loading}>加载中...</div>
      </div>
    );
  }

  if (!hotKeys.length) {
    return null;
  }

  return (
    <div className={styles.dropdown}>
      <h3 className={styles.title}>热门搜索：</h3>
      <ul className={styles.list}>
        {hotKeys.map((key, index) => (
          <li
            key={`${key}-${index}`}
            className={styles.item}
            onClick={() => onKeywordSelect?.(key)}
          >
            <span className={`${styles.rank} ${getRankClassName(index)}`}>
              {index + 1}
            </span>
            <span className={styles.keyword}>{key}</span>
          </li>
        ))}
      </ul>
    </div>
  );
}
