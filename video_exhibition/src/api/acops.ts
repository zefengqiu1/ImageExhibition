import { axios } from './axios';

export interface VideoData {
  id: string;
  title: string;
  description: string;
  imageUrl: string;
  category: string;
  type: string;
  region: string;
  language: string;
  year: string;
  quality: string;
  status: string;
  createdAt: number;
}

export interface VideoSearchResult {
  id: string;
  title: string;
  description: string;
  imageUrl: string;
  highlightTitle?: string;
}

export type AuditStatus = 'pending' | 'approved' | 'rejected';
export type PublishStatus = 'draft' | 'published' | 'offline';

export interface VideoManageData extends VideoData {
  auditStatus?: AuditStatus;
  publishStatus?: PublishStatus;
  videoUrl?: string;
  rejectReason?: string;
  reviewer?: string;
  reviewedAt?: number;
  updatedAt?: number;
}

interface PageResponse {
  imageUrlList: VideoData[];
  total: number;
}

export interface VideoAdminPageResponse {
  imageUrlList: VideoManageData[];
  total: number;
}

export interface VideoAdminFilters {
  page?: number;
  size?: number;
  sortBy?: string;
  sortDir?: 'asc' | 'desc';
  category?: string;
  type?: string;
  region?: string;
  language?: string;
  year?: string;
  quality?: string;
  status?: string;
  auditStatus?: AuditStatus | 'all';
  publishStatus?: PublishStatus | 'all';
}

export type ListTheme = 'movie' | 'drama' | 'variety';

export async function fetchThemePosts(
  theme: ListTheme,
  params: URLSearchParams,
): Promise<PageResponse> {
  const response = await axios.get<PageResponse>(`/api/videos/list/${theme}?` + params.toString());
  return response.data;
}

export async function getVideoPost(id: string): Promise<VideoData> {
  const response = await axios.get<VideoData>(`/api/videos/${id}`);
  return response.data;
}

function buildAdminParams(filters: VideoAdminFilters): URLSearchParams {
  const params = new URLSearchParams();

  Object.entries(filters).forEach(([key, value]) => {
    if (value === undefined || value === null || value === '') return;
    params.set(key, String(value));
  });

  return params;
}

export async function fetchAdminVideos(filters: VideoAdminFilters): Promise<VideoAdminPageResponse> {
  const response = await axios.get<VideoAdminPageResponse>('/api/admin/videos?' + buildAdminParams(filters).toString());
  return response.data;
}

export async function createAdminVideo(data: Partial<VideoManageData>): Promise<VideoManageData> {
  const response = await axios.post<VideoManageData>('/api/admin/videos', data);
  return response.data;
}

export async function updateAdminVideo(id: string, data: Partial<VideoManageData>): Promise<VideoManageData> {
  const response = await axios.put<VideoManageData>(`/api/admin/videos/${encodeURIComponent(id)}`, data);
  return response.data;
}

export async function deleteAdminVideo(id: string): Promise<void> {
  await axios.delete(`/api/admin/videos/${encodeURIComponent(id)}`);
}

export async function auditAdminVideo(
  id: string,
  request: { auditStatus: AuditStatus; rejectReason?: string; reviewer?: string },
): Promise<VideoManageData> {
  const response = await axios.patch<VideoManageData>(`/api/admin/videos/${encodeURIComponent(id)}/audit`, request);
  return response.data;
}

export async function publishAdminVideo(
  id: string,
  request: { publishStatus: PublishStatus },
): Promise<VideoManageData> {
  const response = await axios.patch<VideoManageData>(`/api/admin/videos/${encodeURIComponent(id)}/publish`, request);
  return response.data;
}

// 搜索相关
interface SearchResponse {
  total: number;
  list: VideoSearchResult[];
}

export async function searchVideos(
  keyword: string,
  page: number = 0,
  size: number = 10,
): Promise<SearchResponse> {
  try {
    const params = new URLSearchParams();
    params.append('keyword', keyword);
    params.append('page', page.toString());
    params.append('size', size.toString());

    const response = await axios.get<SearchResponse>('/api/videos/search', { params });
    const payload = response.data;
    return {
      total: Number.isFinite(payload?.total) ? payload.total : 0,
      list: Array.isArray(payload?.list) ? payload.list : [],
    };
  } catch (error) {
    console.error('搜索视频失败:', error);
    return {
      total: 0,
      list: [],
    };
  }
}

export async function getHotKeyList(): Promise<string[]> {
  try {
    const response = await axios.get<string[]>(`/api/videos/hotKeywords`);
    return response.data;
  } catch (error) {
    console.error('获取热门搜索失败:', error);
    return [];
  }
}
