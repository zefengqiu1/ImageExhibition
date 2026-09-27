import { useState } from 'react';
import styles from './AutoComplete.module.css';
import HotKeySearchDroplist from './HotKeySearchDroplist';

export default function AutoComplete() {
  const [query, setQuery] = useState('');
  const [showHotKeys, setShowHotKeys] = useState(false);

  const trimmedQuery = query.trim();

  const handleSearch = () => {
    if (!trimmedQuery) return;
    window.location.href = `/search?query=${encodeURIComponent(trimmedQuery)}`;
  };

  return (
    <div className={styles.container}>
      <div className={styles.inputWrapper}>
        <input
          type="text"
          value={query}
          onChange={(event) => setQuery(event.target.value)}
          onFocus={() => setShowHotKeys(true)}
          onBlur={() => setTimeout(() => setShowHotKeys(false), 200)}
          onKeyDown={(event) => event.key === 'Enter' && handleSearch()}
          placeholder="搜索影视内容..."
        />

        {showHotKeys && !trimmedQuery && (
          <HotKeySearchDroplist onKeywordSelect={setQuery} />
        )}
      </div>

      <button onClick={handleSearch} disabled={!trimmedQuery}>
        搜索
      </button>
    </div>
  );
}
