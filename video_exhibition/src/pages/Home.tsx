import { Link } from 'react-router-dom';
import styles from './Home.module.css';

export default function Home() {
  return (
    <div className={styles.container}>
      <title>Lsp666</title>
      <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no" />
      <link rel="icon" href="/favicon.ico" />
      <link rel="stylesheet" type="text/css" href="./icofont.min.css" />

      <div className={styles.warningbox}>
        <h1>警告 / WARNING</h1>
        <p>成人内容，未成年禁止入内</p>
        <p>The content is for adults only. Youngers,aged below 18, are prohibited.</p>
        <Link to="/list">
          <button className={styles.btn}>確認年齡 / Age Verification</button>
        </Link>
      </div>
    </div>
  );
}
