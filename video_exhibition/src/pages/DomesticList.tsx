import styles from './Home.module.css';
import ListPage from './ListPage';

export default function DomesticList() {
  return <ListPage containerClassName={styles.container} theme="movie" />;
}
