import { v4 as uuidv4 } from 'uuid';
import { axios } from '../api/axios';

interface UserInfo {
  userId: string;
  sessionId: string;
  deviceType: 'mobile' | 'desktop' | 'tablet';
  browser: string;
  os: string;
  screenResolution: string;
}

interface PageViewEvent {
  eventType: 'page_view';
  pageType: 'home' | 'list' | 'detail' | 'search';
  pageUrl: string;
  referrer: string;
  timestamp: number;
}

interface VideoViewEvent {
  eventType: 'video_view';
  videoId: string;
  videoTitle: string;
  category: string;
  viewDuration?: number;
  scrollDepth?: number;
  timestamp: number;
}

interface SearchEvent {
  eventType: 'search';
  keyword: string;
  resultCount: number;
  clickedResults?: string[];
  clickPositions?: number[];
  sortType: string;
  filters: {
    category?: string;
  };
  timestamp: number;
}

type AnalyticsEvent = PageViewEvent | VideoViewEvent | SearchEvent;
type PageType = PageViewEvent['pageType'];

class Analytics {
  private endpoint = '/api/analytics/track';
  private userInfo: UserInfo;
  private eventQueue: AnalyticsEvent[] = [];
  private flushInterval = 5000; // 5秒批量发送
  private maxQueueSize = 20;

  private toDurationSeconds(startTimestampMs: number) {
    const elapsedMs = Date.now() - startTimestampMs;
    return Math.max(0, Math.floor(elapsedMs / 1000));
  }

  constructor() {
    this.userInfo = this.initUserInfo();
    this.startAutoFlush();
    this.setupBeforeUnload();
  }

  private initUserInfo(): UserInfo {
    // 获取或创建 userId (使用 localStorage 持久化)
    let userId = localStorage.getItem('analytics_user_id');
    if (!userId) {
      userId = uuidv4();
      localStorage.setItem('analytics_user_id', userId);
    }

    // 获取或创建 sessionId (使用 sessionStorage)
    let sessionId = sessionStorage.getItem('analytics_session_id');
    if (!sessionId) {
      sessionId = uuidv4();
      sessionStorage.setItem('analytics_session_id', sessionId);
    }

    return {
      userId,
      sessionId,
      deviceType: this.getDeviceType(),
      browser: this.getBrowser(),
      os: this.getOS(),
      screenResolution: `${window.screen.width}x${window.screen.height}`
    };
  }

  private getDeviceType(): 'mobile' | 'desktop' | 'tablet' {
    const ua = navigator.userAgent;
    if (/(tablet|ipad|playbook|silk)|(android(?!.*mobi))/i.test(ua)) {
      return 'tablet';
    }
    if (/Mobile|Android|iP(hone|od)|IEMobile|BlackBerry|Kindle|Silk-Accelerated|(hpw|web)OS|Opera M(obi|ini)/.test(ua)) {
      return 'mobile';
    }
    return 'desktop';
  }

  private getBrowser(): string {
    const ua = navigator.userAgent;
    if (ua.indexOf('Chrome') > -1) return 'Chrome';
    if (ua.indexOf('Firefox') > -1) return 'Firefox';
    if (ua.indexOf('Safari') > -1) return 'Safari';
    if (ua.indexOf('Edge') > -1) return 'Edge';
    return 'Other';
  }

  private getOS(): string {
    const ua = navigator.userAgent;
    if (ua.indexOf('Win') > -1) return 'Windows';
    if (ua.indexOf('Mac') > -1) return 'MacOS';
    if (ua.indexOf('Linux') > -1) return 'Linux';
    if (ua.indexOf('Android') > -1) return 'Android';
    if (ua.indexOf('iOS') > -1) return 'iOS';
    return 'Other';
  }

  /**
   * 页面浏览埋点
   */
  trackPageView(_pageType: PageType, _additionalData?: Partial<PageViewEvent>) {
    // Page view reporting has been disabled.
  }

  /**
   * 视频浏览埋点
   */
  trackVideoView(videoData: {
    videoId: string;
    videoTitle: string;
    category: string;
  }) {
    const event: VideoViewEvent = {
      eventType: 'video_view',
      ...videoData,
      timestamp: Date.now()
    };
    this.addEvent(event);

    // 返回一个函数用于更新浏览时长和滚动深度
    const startTime = Date.now();
    return {
      updateDuration: () => {
        event.viewDuration = this.toDurationSeconds(startTime);
        console.log('Updated view duration:', event.viewDuration);
      },
      updateScrollDepth: (depth: number) => {
        event.scrollDepth = depth;
        console.log('Updated scroll depth:', event.scrollDepth);
      }
    };
  }

  /**
   * 搜索埋点
   */
  trackSearch(searchData: {
    keyword: string;
    resultCount: number;
    sortType: string;
    filters?: SearchEvent['filters'];
  }) {
    const event: SearchEvent = {
      eventType: 'search',
      ...searchData,
      filters: searchData.filters || {},
      timestamp: Date.now()
    };
    this.addEvent(event);

    // 返回方法用于记录点击
    return {
      recordClick: (videoId: string, position: number) => {
        if (!event.clickedResults) event.clickedResults = [];
        if (!event.clickPositions) event.clickPositions = [];
        event.clickedResults.push(videoId);
        event.clickPositions.push(position);
      }
    };
  }

  private addEvent(event: AnalyticsEvent) {
    this.eventQueue.push(event);

    this.flush();

    // 队列满了立即发送
    if (this.eventQueue.length >= this.maxQueueSize) {
      this.flush();
    }
  }

  private async flush() {
    if (this.eventQueue.length === 0) return;

    const events = [...this.eventQueue];
    this.eventQueue = [];

    try {
      await axios.post(this.endpoint, {
        userInfo: this.userInfo,
        events
      });
    } catch (error) {
      console.error('Analytics tracking failed:', error);
      // 失败了重新加入队列
      this.eventQueue.unshift(...events);
    }
  }

  private startAutoFlush() {
    setInterval(() => {
      this.flush();
    }, this.flushInterval);
  }

  private setupBeforeUnload() {
    window.addEventListener('beforeunload', () => {
      this.flush();
    });
  }
}

// 创建单例
export const analytics = new Analytics();
