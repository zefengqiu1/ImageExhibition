export const formatNumber = (num: number | undefined | null) => {
  if (num === undefined || num === null || Number.isNaN(Number(num))) {
    return '0';
  }
  return new Intl.NumberFormat('zh-CN').format(Math.round(Number(num)));
};

export const formatPercent = (num: number | undefined | null) => {
  if (num === undefined || num === null || Number.isNaN(Number(num))) {
    return '0.0%';
  }
  return `${Number(num).toFixed(1)}%`;
};

export const formatDate = (
  timestamp: number,
  locale: string,
  options: Intl.DateTimeFormatOptions
) => new Date(timestamp).toLocaleDateString(locale, options);

export const getCountryLabel = (country: string) => {
  const labels: Record<string, string> = {
    domestic: '国产',
    asia: '亚洲',
    european: '欧美'
  };
  return labels[country] || country;
};

export const getCountryBadgeInfo = (country: string) => {
  switch (country) {
    case 'european':
      return { label: '歐美' };
    case 'asia':
      return { label: '亞洲' };
    case 'domestic':
      return { label: '國產' };
    default:
      return { label: country };
  }
};
