import { FormEvent, useEffect, useMemo, useState } from 'react';
import styles from './PageSection.module.css';

interface PageSectionProps {
  page: number;
  total: number;
  setPage: (page: number) => void;
}

const PAGE_SIZE = 10;

function buildVisiblePages(current: number, totalPages: number): Array<number | 'ellipsis'> {
  if (totalPages <= 7) {
    return Array.from({ length: totalPages }, (_, index) => index + 1);
  }

  const pages = new Set<number>([1, totalPages, current - 1, current, current + 1]);
  const normalized = Array.from(pages)
    .filter((value) => value >= 1 && value <= totalPages)
    .sort((a, b) => a - b);

  const items: Array<number | 'ellipsis'> = [];
  for (let i = 0; i < normalized.length; i += 1) {
    const value = normalized[i];
    const prev = normalized[i - 1];

    if (i > 0 && value - prev > 1) {
      items.push('ellipsis');
    }
    items.push(value);
  }

  return items;
}

export default function PageSection({ page, total, setPage }: PageSectionProps) {
  const totalPages = Math.max(1, Math.ceil(total / PAGE_SIZE));
  const displayPage = page + 1;
  const [jumpPage, setJumpPage] = useState(String(displayPage));

  useEffect(() => {
    setJumpPage(String(displayPage));
  }, [displayPage]);

  const items = useMemo(() => buildVisiblePages(displayPage, totalPages), [displayPage, totalPages]);

  const changePage = (nextPage: number) => {
    if (nextPage < 0 || nextPage >= totalPages || nextPage === page) return;
    setPage(nextPage);
  };

  const handleJump = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const parsed = Number(jumpPage);
    if (!Number.isFinite(parsed)) return;

    const target = Math.min(totalPages, Math.max(1, Math.floor(parsed)));
    changePage(target - 1);
  };

  if (totalPages <= 1) {
    return null;
  }

  return (
    <div className={styles.page}>
      <ul className={styles.pageList}>
        <li>
          <button
            type="button"
            onClick={() => changePage(0)}
            disabled={page === 0}
            className={styles.actionButton}
          >
            首页
          </button>
        </li>
        <li>
          <button
            type="button"
            onClick={() => changePage(page - 1)}
            disabled={page === 0}
            className={styles.actionButton}
          >
            上一页
          </button>
        </li>

        {items.map((item, index) => {
          if (item === 'ellipsis') {
            return <li key={`ellipsis-${index}`} className={styles.ellipsis}>...</li>;
          }

          return (
            <li key={`page-${item}`}>
              <button
                type="button"
                className={item === displayPage ? `${styles.pageButton} ${styles.active}` : styles.pageButton}
                onClick={() => changePage(item - 1)}
                aria-current={item === displayPage ? 'page' : undefined}
              >
                {item}
              </button>
            </li>
          );
        })}

        <li>
          <button
            type="button"
            onClick={() => changePage(page + 1)}
            disabled={page + 1 >= totalPages}
            className={styles.actionButton}
          >
            下一页
          </button>
        </li>
        <li>
          <button
            type="button"
            onClick={() => changePage(totalPages - 1)}
            disabled={page + 1 >= totalPages}
            className={styles.actionButton}
          >
            末页
          </button>
        </li>
      </ul>

      <form className={styles.jumpForm} onSubmit={handleJump}>
        <span className={styles.pageMeta}>第 {displayPage} / {totalPages} 页</span>
        <input
          type="number"
          min={1}
          max={totalPages}
          value={jumpPage}
          onChange={(event) => setJumpPage(event.target.value)}
          className={styles.jumpInput}
          aria-label="跳转页码"
        />
        <button type="submit" className={styles.jumpButton}>跳转</button>
      </form>
    </div>
  );
}
