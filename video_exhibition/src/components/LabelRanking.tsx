import styles from './LabelRanking.module.css';

interface LabelRankingProps {
  rankings: string[];
}

export default function LabelRanking({ rankings }: LabelRankingProps) {
  return (
    <div className={styles.container}>
      <h2>电影榜</h2>
      <ul>
        {rankings.slice(0, 10).map((label, index) => (
          <li key={`${label}-${index}`} className={styles.rankItem}>
            <span className={styles.rank}>#{index + 1}</span>
            <span className={styles.label}>{label}</span>
          </li>
        ))}
      </ul>
    </div>
  );
}
