import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Calendar, Tag, Globe, ImageOff } from 'lucide-react';
import styles from './ImageCard.module.css';
import { formatDate, getCountryBadgeInfo } from '../utils/format';

export interface ImageData {
  id: string;
  title: string;
  highlightTitle?: string;
  website: string;
  imageUrls: string[];
  country: string;
  createdAt: number;
  score?: number;
  matchedKeywords: string[];
}

interface ImageCardProps {
  result: ImageData;
  onBeforeNavigate?: () => void;
}

const CountryBadge = ({ country }: { country: string }) => {
  const { label } = getCountryBadgeInfo(country);

  return (
    <span className={styles.countryBadge}>
      <Globe className="w-3 h-3 mr-1" />
      {label}
    </span>
  );
};

const CardLabel = ({ labelText }: { labelText: string }) => (
  <div className={styles.cardLabel}>
    <span>{labelText}</span>
  </div>
);

const DATE_FORMAT = { year: 'numeric', month: 'short', day: 'numeric' } as const;

export default function ImageCard({ result, onBeforeNavigate }: ImageCardProps) {
  const [imageError, setImageError] = useState(false);
  const navigate = useNavigate();

  const today = formatDate(Date.now(), 'en-US', DATE_FORMAT);
  const createdDate = formatDate(result.createdAt, 'en-US', DATE_FORMAT);

  const handleClick = () => {
    onBeforeNavigate?.();
    navigate(`/image/${encodeURIComponent(result.title)}`);
  };

  return (
    <div className={styles.card} onClick={handleClick}>
      {today === createdDate && <CardLabel labelText="NEW" />}

      {result.imageUrls?.length > 0 ? (
        imageError ? (
          <div className={styles.imagePlaceholder}>
            <ImageOff size={48} color="#ccc" />
            <span className={styles.imageErrorText}>图片加载失败</span>
          </div>
        ) : (
          <img
            src={result.imageUrls[0]}
            alt={result.title}
            className={styles.cardImage}
            onError={() => setImageError(true)}
          />
        )
      ) : (
        <div className={styles.imagePlaceholder}>
          <ImageOff />
        </div>
      )}

      <div className={styles.cardContent}>
        <h3 className={styles.cardTitle}>{result.highlightTitle || result.title}</h3>

        <div className={styles.dateInfo}>
          <Calendar />
          <span>{createdDate}</span>
        </div>

        <CountryBadge country={result.country} />

        {result.matchedKeywords?.length > 0 && (
          <div className={styles.cardTags}>
            {result.matchedKeywords.slice(0, 3).map((keyword, index) => (
              <span key={`${keyword}-${index}`} className={styles.tag}>
                <Tag />
                {keyword}
              </span>
            ))}
            {result.matchedKeywords.length > 3 && <span>+{result.matchedKeywords.length - 3} 更多</span>}
          </div>
        )}
      </div>
    </div>
  );
}
