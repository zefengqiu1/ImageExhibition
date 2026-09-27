import { useState } from 'react';
import Footer from './Footer';
import Header from './Header';
import HotRankPanel from './HotRankPanel';
import styles from './RankPage.module.css';

interface TabConfig {
  id: 'all' | 'movie' | 'drama' | 'variety';
  label: string;
}

const TABS: TabConfig[] = [
  { id: 'all', label: '全站榜' },
  { id: 'movie', label: '电影榜' },
  { id: 'drama', label: '电视剧榜' },
  { id: 'variety', label: '综艺榜' },
];

type CountryKey = TabConfig['id'];

const TAB_TO_CATEGORY: Record<CountryKey, CountryKey> = {
  all: 'all',
  movie: 'movie',
  drama: 'drama',
  variety: 'variety',
}

export default function RankPage() {
  const [activeTab, setActiveTab] = useState<CountryKey>('all');

  return (
    <div className={styles.container}>
      <Header />
      <h1>排行榜</h1>
      <nav className={styles.navigation}>
        <ul>
          {TABS.map((tab) => (
            <li
              key={tab.id}
              className={activeTab === tab.id ? styles.active : ''}
              onClick={() => setActiveTab(tab.id)}
            >
              {tab.label}
            </li>
          ))}
        </ul>
      </nav>
      <section className={styles.section}>
        <HotRankPanel category={TAB_TO_CATEGORY[activeTab]} />
      </section>
      <Footer />
    </div>
  );
}
