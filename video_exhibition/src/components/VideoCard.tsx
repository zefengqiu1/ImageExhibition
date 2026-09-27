import { Calendar, Globe, ImageOff, Tag } from 'lucide-react';
import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import styles from './VideoCard.module.css';
import { formatDate } from '../utils/format';
import { VideoData } from '../api/acops';

interface VideoCardProps {
  result: VideoData;
}

const DATE_FORMAT = { year: 'numeric', month: 'short', day: 'numeric' } as const;

export default function VideoCard({ result }: VideoCardProps) {
  const navigate = useNavigate();
  const [imageError, setImageError] = useState(false);
  const createdDate = formatDate(result.createdAt, 'en-US', DATE_FORMAT);

  const handleCardClick = () => {
    navigate(`/video/${result.id}`);
  };

  return (
    <article className={styles.card} onClick={handleCardClick} style={{ cursor: 'pointer' }}>
      {result.imageUrl && !imageError ? (
        <img
          src={result.imageUrl}
          alt={result.title || '视频封面'}
          className={styles.image}
          onError={() => setImageError(true)}
          loading="lazy"
        />
      ) : (
        <div className={styles.placeholder}>
          <ImageOff size={48} />
        </div>
      )}

      <div className={styles.content}>
        <p className={styles.description}>{result.title || '未命名视频'}</p>
        {result.description && <p className={styles.description}>{result.description}</p>}

        <div className={styles.meta}>
          <span className={styles.metaItem}>
            <Calendar size={14} />
            {createdDate}
          </span>
          <span className={styles.metaItem}>
            <Globe size={14} />
            {result.category}
          </span>
        </div>

        <div className={styles.tags}>
          <span className={styles.tag}><Tag size={14} />{result.type}</span>
          <span className={styles.tag}>{result.region}</span>
          <span className={styles.tag}>{result.language}</span>
          <span className={styles.tag}>{result.year}</span>
          <span className={styles.tag}>{result.quality}</span>
          <span className={styles.tag}>{result.status}</span>
        </div>
      </div>
    </article>
  );
}
