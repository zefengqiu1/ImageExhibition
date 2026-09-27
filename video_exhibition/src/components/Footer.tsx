import styles from './Footer.module.css';

export default function Footer() {
  return (
    <footer className={styles.footer}>
      <h2>内容声明</h2>
      <p>内容皆为搬运，仅用作学习和交流，如侵权，请联系删除！</p>

      <div className="contact-info">
        <p><strong>邮箱地址：</strong>info@lsp66.com</p>
      </div>

      <div className="copyright">
        <p>lsp666 网站</p>
      </div>
    </footer>
  );
}
