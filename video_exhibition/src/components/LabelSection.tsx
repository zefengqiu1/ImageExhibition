import { useMemo } from 'react';
import { Link, useLocation } from 'react-router-dom';
import styles from './LabelSection.module.css';

type ThemeKey = 'movie' | 'drama' | 'variety';

type FilterOption = {
  label: string;
  value: string;
};

type FilterSection = {
  key: string;
  title: string;
  options: FilterOption[];
};

const THEME_TABS: Array<{ key: ThemeKey; label: string }> = [
  { key: 'movie', label: '电影' },
  { key: 'drama', label: '电视剧' },
  { key: 'variety', label: '综艺' },
];

const SECTION_CONFIGS: Record<ThemeKey, FilterSection[]> = {
  movie: [
    {
      key: 'type',
      title: '全部类型',
      options: [
        { label: '喜剧', value: 'comedy' },
        { label: '爱情', value: 'romance' },
        { label: '动作', value: 'action' },
        { label: '犯罪', value: 'crime' },
        { label: '科幻', value: 'sci-fi' },
        { label: '奇幻', value: 'fantasy' },
        { label: '冒险', value: 'adventure' },
        { label: '灾难', value: 'disaster' },
        { label: '恐怖', value: 'horror' },
        { label: '惊悚', value: 'thriller' },
        { label: '剧情', value: 'drama' },
        { label: '战争', value: 'war' },
        { label: '歌舞', value: 'musical' },
        { label: '经典', value: 'classic' },
        { label: '悬疑', value: 'mystery' },
        { label: '动画', value: 'animation' },
        { label: '同性', value: 'lgbtq' },
        { label: '网络电影', value: 'web-movie' },
      ],
    },
    {
      key: 'region',
      title: '全部地区',
      options: [
        { label: '大陆', value: 'mainland' },
        { label: '香港', value: 'hongkong' },
        { label: '台湾', value: 'taiwan' },
        { label: '日本', value: 'japan' },
        { label: '韩国', value: 'korea' },
        { label: '欧美', value: 'west' },
        { label: '英国', value: 'uk' },
        { label: '泰国', value: 'thailand' },
        { label: '其它', value: 'other' },
      ],
    },
    {
      key: 'language',
      title: '全部语言',
      options: [
        { label: '国语', value: 'zh-cn' },
        { label: '粤语', value: 'yue' },
        { label: '英语', value: 'en' },
        { label: '韩语', value: 'ko' },
        { label: '日语', value: 'ja' },
        { label: '西班牙语', value: 'es' },
        { label: '法语', value: 'fr' },
        { label: '德语', value: 'de' },
        { label: '意大利语', value: 'it' },
        { label: '泰国语', value: 'th' },
        { label: '其它', value: 'other' },
      ],
    },
    {
      key: 'year',
      title: '全部年份',
      options: [
        { label: '今年', value: 'this-year' },
        { label: '去年', value: 'last-year' },
        { label: '更早', value: 'older' },
        { label: '90年代', value: '1990s' },
        { label: '80年代', value: '1980s' },
        { label: '怀旧', value: 'nostalgia' },
      ],
    },
    {
      key: 'quality',
      title: '全部画质',
      options: [
        { label: '4K', value: '4k' },
        { label: '1080P', value: '1080p' },
        { label: '900P', value: '900p' },
        { label: '720P', value: '720p' },
      ],
    },
  ],
  drama: [
    {
      key: 'type',
      title: '全部类型',
      options: [
        { label: '偶像', value: 'idol' },
        { label: '爱情', value: 'romance' },
        { label: '言情', value: 'drama-romance' },
        { label: '古装', value: 'costume' },
        { label: '历史', value: 'history' },
        { label: '玄幻', value: 'xuanhuan' },
        { label: '谍战', value: 'spy' },
        { label: '历险', value: 'adventure' },
        { label: '都市', value: 'urban' },
        { label: '科幻', value: 'sci-fi' },
        { label: '军旅', value: 'military' },
        { label: '喜剧', value: 'comedy' },
        { label: '武侠', value: 'wuxia' },
        { label: '江湖', value: 'martial' },
        { label: '罪案', value: 'crime' },
        { label: '青春', value: 'youth' },
        { label: '家庭', value: 'family' },
        { label: '战争', value: 'war' },
        { label: '悬疑', value: 'mystery' },
        { label: '穿越', value: 'time-travel' },
        { label: '宫廷', value: 'palace' },
        { label: '神话', value: 'myth' },
        { label: '商战', value: 'business' },
        { label: '警匪', value: 'police' },
        { label: '动作', value: 'action' },
        { label: '惊悚', value: 'thriller' },
        { label: '剧情', value: 'drama' },
        { label: '同性', value: 'lgbtq' },
        { label: '奇幻', value: 'fantasy' },
      ],
    },
    {
      key: 'region',
      title: '全部地区',
      options: [
        { label: '大陆', value: 'mainland' },
        { label: '香港', value: 'hongkong' },
        { label: '台湾', value: 'taiwan' },
        { label: '日本', value: 'japan' },
        { label: '韩国', value: 'korea' },
        { label: '欧美', value: 'west' },
        { label: '英国', value: 'uk' },
        { label: '泰国', value: 'thailand' },
        { label: '其它', value: 'other' },
      ],
    },
    {
      key: 'language',
      title: '全部语言',
      options: [
        { label: '国语', value: 'zh-cn' },
        { label: '粤语', value: 'yue' },
        { label: '英语', value: 'en' },
        { label: '韩语', value: 'ko' },
        { label: '日语', value: 'ja' },
        { label: '其它', value: 'other' },
      ],
    },
    {
      key: 'year',
      title: '全部年份',
      options: [
        { label: '今年', value: 'this-year' },
        { label: '去年', value: 'last-year' },
        { label: '更早', value: 'older' },
        { label: '90年代', value: '1990s' },
        { label: '80年代', value: '1980s' },
      ],
    },
    {
      key: 'status',
      title: '全部状态',
      options: [
        { label: '全集', value: 'completed' },
        { label: '连载中', value: 'ongoing' },
      ],
    },
  ],
  variety: [
    {
      key: 'type',
      title: '全部类型',
      options: [
        { label: '真人秀', value: 'reality' },
        { label: '选秀', value: 'talent' },
        { label: '网综', value: 'web-variety' },
        { label: '脱口秀', value: 'talk-show' },
        { label: '搞笑', value: 'comedy' },
        { label: '竞技', value: 'competition' },
        { label: '情感', value: 'emotion' },
        { label: '访谈', value: 'interview' },
        { label: '演唱会', value: 'concert' },
        { label: '晚会', value: 'gala' },
        { label: '其它', value: 'other' },
      ],
    },
    {
      key: 'region',
      title: '全部地区',
      options: [
        { label: '大陆', value: 'mainland' },
        { label: '香港', value: 'hongkong' },
        { label: '台湾', value: 'taiwan' },
        { label: '日本', value: 'japan' },
        { label: '韩国', value: 'korea' },
        { label: '欧美', value: 'west' },
        { label: '其它', value: 'other' },
      ],
    },
    {
      key: 'language',
      title: '全部语言',
      options: [
        { label: '国语', value: 'zh-cn' },
        { label: '粤语', value: 'yue' },
        { label: '英语', value: 'en' },
        { label: '韩语', value: 'ko' },
        { label: '日语', value: 'ja' },
        { label: '其它', value: 'other' },
      ],
    },
    {
      key: 'year',
      title: '全部年份',
      options: [
        { label: '今年', value: 'this-year' },
        { label: '去年', value: 'last-year' },
        { label: '更早', value: 'older' },
        { label: '90年代', value: '1990s' },
        { label: '80年代', value: '1980s' },
      ],
    },
    {
      key: 'quality',
      title: '全部画质',
      options: [
        { label: '4K', value: '4k' },
        { label: '1080P', value: '1080p' },
        { label: '900P', value: '900p' },
        { label: '720P', value: '720p' },
      ],
    },
  ],
};

function buildSearchParams(search: string, key: string, value: string) {
  const nextParams = new URLSearchParams(search);
  nextParams.set(key, value);
  return nextParams.toString();
}

function getThemeFromPath(pathname: string): ThemeKey {
  if (pathname.includes('/drama')) return 'drama';
  if (pathname.includes('/variety')) return 'variety';
  return 'movie';
}

export default function LabelSection({ theme: explicitTheme }: { theme?: ThemeKey } = {}) {
  const location = useLocation();
  const theme = explicitTheme ?? getThemeFromPath(location.pathname);
  const activeSections = SECTION_CONFIGS[theme];

  const activeValues = useMemo(() => {
    const params = new URLSearchParams(location.search);
    return activeSections.reduce<Record<string, string>>((accumulator, section) => {
      accumulator[section.key] = params.get(section.key) || 'all';
      return accumulator;
    }, {});
  }, [activeSections, location.search]);

  return (
    <div className={styles.labelsection}>
      <div className={styles.panel}>
        <div className={styles.themeTabs}>
          {THEME_TABS.map((tab) => {
            const isActive = tab.key === theme;
            return (
              <Link
                key={tab.key}
                to={`/list/${tab.key}`}
                className={`${styles.themeTab} ${isActive ? styles.themeTabActive : ''}`}
              >
                {tab.label}
              </Link>
            );
          })}
        </div>

        {activeSections.map((section) => {
          const activeValue = activeValues[section.key];
          return (
            <div key={section.key} className={styles.sectionRow}>
              <Link
                to={`?${buildSearchParams(location.search, section.key, 'all')}`}
                className={styles.groupLink}
              >
                <span className={styles.groupTag}>{section.title}</span>
              </Link>
              <div className={styles.optionWrap}>
                {section.options.map((option) => {
                  const isActive = activeValue === option.value;
                  return (
                    <Link
                      key={option.value}
                      to={`?${buildSearchParams(location.search, section.key, option.value)}`}
                      className={`${styles.optionItem} ${isActive ? styles.active : ''}`}
                    >
                      {option.label}
                    </Link>
                  );
                })}
              </div>
            </div>
          );
        })}
      </div>
    </div>
  );
}
