import { Link, useLocation } from 'react-router-dom';
import AutoComplete from './AutoComplete';
import styles from './Header.module.css';

const REGION_ITEMS = [
  { to: '/list/movie', label: '电影' },
  { to: '/list/drama', label: '电视剧' },
  { to: '/list/variety', label: '综艺' },
];

export default function Header() {
  const location = useLocation();
  const activeRegion = REGION_ITEMS.find((item) => location.pathname === item.to);

  return (
    <header className={styles.header}>
      <nav className={styles.nav}>
        <div className={styles.dropdown}>
          <button type="button" className={styles.dropdownTrigger} aria-haspopup="menu">
            <span>{activeRegion?.label ?? '影视'}</span>
            <span className={styles.chevron} aria-hidden="true">⌄</span>
          </button>
          <div className={styles.dropdownMenu} role="menu" aria-label="地区筛选">
            {REGION_ITEMS.map((item) => (
              <Link
                key={item.to}
                to={item.to}
                className={`${styles.dropdownItem} ${location.pathname === item.to ? styles.activeItem : ''}`}
                role="menuitem"
              >
                {item.label}
              </Link>
            ))}
          </div>
        </div>

        <Link to="/analytics" className={styles.text}>
          数据分析
        </Link>
        <Link to="/admin/videos" className={styles.text}>
          影片管理
        </Link>
      </nav>
      <AutoComplete />
    </header>
  );
}
