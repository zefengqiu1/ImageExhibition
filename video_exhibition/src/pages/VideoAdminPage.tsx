import { ChangeEvent, FormEvent, useCallback, useEffect, useMemo, useState } from 'react';
import {
  CheckCircle2,
  Edit3,
  Eye,
  FilePlus2,
  ImageOff,
  RefreshCcw,
  Search,
  Send,
  Trash2,
  XCircle,
} from 'lucide-react';
import {
  AuditStatus,
  PublishStatus,
  VideoAdminFilters,
  VideoManageData,
  auditAdminVideo,
  createAdminVideo,
  deleteAdminVideo,
  fetchAdminVideos,
  publishAdminVideo,
  updateAdminVideo,
} from '../api/acops';
import PageSection from '../components/PageSection';
import { formatDate } from '../utils/format';
import styles from './VideoAdminPage.module.css';

const PAGE_SIZE = 10;
const DATE_FORMAT = { year: 'numeric', month: '2-digit', day: '2-digit' } as const;

const emptyForm: Partial<VideoManageData> = {
  title: '',
  description: '',
  imageUrl: '',
  videoUrl: '',
  category: 'movie',
  type: '',
  region: '',
  language: '',
  year: '',
  quality: '',
  status: '',
  auditStatus: 'pending',
  publishStatus: 'draft',
};

const auditLabels: Record<AuditStatus, string> = {
  pending: '待审核',
  approved: '已通过',
  rejected: '已拒绝',
};

const publishLabels: Record<PublishStatus, string> = {
  draft: '草稿',
  published: '已发布',
  offline: '已下架',
};

const initialFilters: VideoAdminFilters = {
  category: 'all',
  type: '',
  region: '',
  language: '',
  year: '',
  quality: '',
  status: '',
  auditStatus: 'all',
  publishStatus: 'all',
};

function getStatusClass(status?: string) {
  if (!status) return styles.neutralBadge;
  return styles[`${status}Badge` as keyof typeof styles] || styles.neutralBadge;
}

function normalizeForm(data: Partial<VideoManageData>): Partial<VideoManageData> {
  const normalized = { ...data };
  Object.keys(normalized).forEach((key) => {
    const value = normalized[key as keyof VideoManageData];
    if (typeof value === 'string') {
      normalized[key as keyof VideoManageData] = value.trim() as never;
    }
  });
  return normalized;
}

export default function VideoAdminPage() {
  const [videos, setVideos] = useState<VideoManageData[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(0);
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');
  const [modalMode, setModalMode] = useState<'create' | 'edit' | null>(null);
  const [editingVideo, setEditingVideo] = useState<VideoManageData | null>(null);
  const [form, setForm] = useState<Partial<VideoManageData>>(emptyForm);
  const [filters, setFilters] = useState<VideoAdminFilters>({ ...initialFilters });
  const [appliedFilters, setAppliedFilters] = useState<VideoAdminFilters>({ ...initialFilters });

  const reviewer = useMemo(() => localStorage.getItem('videoReviewer') || 'admin', []);

  const loadVideos = useCallback(async (nextPage = page) => {
    try {
      setLoading(true);
      setError('');
      const response = await fetchAdminVideos({
        page: nextPage,
        size: PAGE_SIZE,
        sortBy: 'createdAt',
        sortDir: 'desc',
        ...appliedFilters,
      });
      setVideos(Array.isArray(response.imageUrlList) ? response.imageUrlList : []);
      setTotal(Number.isFinite(response.total) ? response.total : 0);
    } catch (requestError) {
      console.error('加载视频管理列表失败:', requestError);
      setError('加载失败，请确认后端服务已启动。');
    } finally {
      setLoading(false);
    }
  }, [appliedFilters, page]);

  useEffect(() => {
    loadVideos(page);
  }, [loadVideos, page]);

  const handleFilterChange = (event: ChangeEvent<HTMLInputElement | HTMLSelectElement>) => {
    const { name, value } = event.target;
    setFilters((current) => ({ ...current, [name]: value }));
  };

  const handleFilterSubmit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setAppliedFilters({ ...filters });
    setPage(0);
  };

  const resetFilters = () => {
    setFilters({ ...initialFilters });
    setAppliedFilters({ ...initialFilters });
    setPage(0);
  };

  const openCreate = () => {
    setEditingVideo(null);
    setForm({ ...emptyForm });
    setModalMode('create');
  };

  const openEdit = (video: VideoManageData) => {
    setEditingVideo(video);
    setForm({ ...emptyForm, ...video });
    setModalMode('edit');
  };

  const closeModal = () => {
    if (saving) return;
    setModalMode(null);
    setEditingVideo(null);
    setForm(emptyForm);
  };

  const handleFormChange = (event: ChangeEvent<HTMLInputElement | HTMLTextAreaElement | HTMLSelectElement>) => {
    const { name, value } = event.target;
    setForm((current) => ({ ...current, [name]: value }));
  };

  const handleSave = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const payload = normalizeForm(form);
    if (!payload.title) {
      setError('请填写视频标题。');
      return;
    }

    try {
      setSaving(true);
      setError('');
      if (modalMode === 'edit' && editingVideo?.id) {
        await updateAdminVideo(editingVideo.id, payload);
      } else {
        await createAdminVideo(payload);
      }
      closeModal();
      await loadVideos(page);
    } catch (requestError) {
      console.error('保存视频失败:', requestError);
      setError('保存失败，请稍后重试。');
    } finally {
      setSaving(false);
    }
  };

  const handleDelete = async (video: VideoManageData) => {
    if (!window.confirm(`确认删除「${video.title || video.id}」吗？`)) return;

    try {
      setError('');
      await deleteAdminVideo(video.id);
      await loadVideos(page);
    } catch (requestError) {
      console.error('删除视频失败:', requestError);
      setError('删除失败，请稍后重试。');
    }
  };

  const handleAudit = async (video: VideoManageData, auditStatus: AuditStatus) => {
    const rejectReason = auditStatus === 'rejected' ? window.prompt('请输入拒绝原因') || '' : '';
    if (auditStatus === 'rejected' && !rejectReason.trim()) return;

    try {
      setError('');
      await auditAdminVideo(video.id, { auditStatus, rejectReason, reviewer });
      await loadVideos(page);
    } catch (requestError) {
      console.error('审核视频失败:', requestError);
      setError('审核失败，请稍后重试。');
    }
  };

  const handlePublish = async (video: VideoManageData, publishStatus: PublishStatus) => {
    try {
      setError('');
      await publishAdminVideo(video.id, { publishStatus });
      await loadVideos(page);
    } catch (requestError) {
      console.error('更新发布状态失败:', requestError);
      setError('更新发布状态失败，请稍后重试。');
    }
  };

  return (
    <main className={styles.page}>
      <section className={styles.header}>
        <div>
          <p className={styles.eyebrow}>Admin Console</p>
          <h1>视频管理审核</h1>
        </div>
        <button type="button" className={styles.primaryButton} onClick={openCreate}>
          <FilePlus2 size={18} />
          新增视频
        </button>
      </section>

      <form className={styles.filters} onSubmit={handleFilterSubmit}>
        <label>
          分类
          <select name="category" value={filters.category} onChange={handleFilterChange}>
            <option value="all">全部</option>
            <option value="movie">电影</option>
            <option value="drama">电视剧</option>
            <option value="variety">综艺</option>
          </select>
        </label>
        <label>
          类型
          <input name="type" value={filters.type} onChange={handleFilterChange} placeholder="动作 / 喜剧" />
        </label>
        <label>
          地区
          <input name="region" value={filters.region} onChange={handleFilterChange} placeholder="大陆 / 欧美" />
        </label>
        <label>
          年份
          <input name="year" value={filters.year} onChange={handleFilterChange} placeholder="2026" />
        </label>
        <label>
          审核
          <select name="auditStatus" value={filters.auditStatus} onChange={handleFilterChange}>
            <option value="all">全部</option>
            <option value="pending">待审核</option>
            <option value="approved">已通过</option>
            <option value="rejected">已拒绝</option>
          </select>
        </label>
        <label>
          发布
          <select name="publishStatus" value={filters.publishStatus} onChange={handleFilterChange}>
            <option value="all">全部</option>
            <option value="draft">草稿</option>
            <option value="published">已发布</option>
            <option value="offline">已下架</option>
          </select>
        </label>
        <div className={styles.filterActions}>
          <button type="submit" className={styles.secondaryButton}>
            <Search size={16} />
            查询
          </button>
          <button type="button" className={styles.ghostButton} onClick={resetFilters}>
            <RefreshCcw size={16} />
            重置
          </button>
        </div>
      </form>

      {error && <div className={styles.error}>{error}</div>}

      <section className={styles.tableShell}>
        <div className={styles.tableHeader}>
          <strong>视频列表</strong>
          <span>共 {total} 条</span>
        </div>

        <div className={styles.tableScroll}>
          <table className={styles.table}>
            <thead>
              <tr>
                <th>视频</th>
                <th>分类</th>
                <th>内容属性</th>
                <th>审核</th>
                <th>发布</th>
                <th>更新时间</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              {loading ? (
                <tr>
                  <td colSpan={7} className={styles.emptyCell}>加载中...</td>
                </tr>
              ) : videos.length === 0 ? (
                <tr>
                  <td colSpan={7} className={styles.emptyCell}>暂无视频数据</td>
                </tr>
              ) : videos.map((video) => (
                <tr key={video.id}>
                  <td>
                    <div className={styles.videoCell}>
                      {video.imageUrl ? (
                        <img src={video.imageUrl} alt={video.title || '视频封面'} className={styles.cover} />
                      ) : (
                        <div className={styles.coverFallback}><ImageOff size={22} /></div>
                      )}
                      <div>
                        <strong>{video.title || '未命名视频'}</strong>
                        {video.description && <span>{video.description}</span>}
                        <span>{video.id}</span>
                      </div>
                    </div>
                  </td>
                  <td>{video.category || '-'}</td>
                  <td>
                    <div className={styles.metaStack}>
                      <span>{[video.type, video.region, video.language].filter(Boolean).join(' / ') || '-'}</span>
                      <span>{[video.year, video.quality, video.status].filter(Boolean).join(' / ') || '-'}</span>
                    </div>
                  </td>
                  <td>
                    <span className={`${styles.badge} ${getStatusClass(video.auditStatus)}`}>
                      {video.auditStatus ? auditLabels[video.auditStatus] : '未设置'}
                    </span>
                  </td>
                  <td>
                    <span className={`${styles.badge} ${getStatusClass(video.publishStatus)}`}>
                      {video.publishStatus ? publishLabels[video.publishStatus] : '未设置'}
                    </span>
                  </td>
                  <td>{formatDate(video.updatedAt || video.createdAt, 'zh-CN', DATE_FORMAT)}</td>
                  <td>
                    <div className={styles.rowActions}>
                      <button type="button" title="查看前台详情" onClick={() => window.open(`/video/${video.id}`, '_blank')}>
                        <Eye size={16} />
                      </button>
                      <button type="button" title="编辑" onClick={() => openEdit(video)}>
                        <Edit3 size={16} />
                      </button>
                      <button type="button" title="通过" onClick={() => handleAudit(video, 'approved')}>
                        <CheckCircle2 size={16} />
                      </button>
                      <button type="button" title="拒绝" onClick={() => handleAudit(video, 'rejected')}>
                        <XCircle size={16} />
                      </button>
                      <button type="button" title="发布" onClick={() => handlePublish(video, 'published')}>
                        <Send size={16} />
                      </button>
                      <button type="button" title="删除" className={styles.dangerIcon} onClick={() => handleDelete(video)}>
                        <Trash2 size={16} />
                      </button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </section>

      <PageSection page={page} total={total} setPage={setPage} />

      {modalMode && (
        <div className={styles.modalBackdrop} role="presentation">
          <form className={styles.modal} onSubmit={handleSave}>
            <div className={styles.modalHeader}>
              <h2>{modalMode === 'create' ? '新增视频' : '编辑视频'}</h2>
              <button type="button" className={styles.iconButton} onClick={closeModal}>×</button>
            </div>

            <div className={styles.formGrid}>
              <label className={styles.wideField}>
                视频标题
                <input name="title" value={form.title || ''} onChange={handleFormChange} />
              </label>
              <label className={styles.wideField}>
                简介
                <textarea name="description" value={form.description || ''} onChange={handleFormChange} rows={3} />
              </label>
              <label>
                分类
                <select name="category" value={form.category || 'movie'} onChange={handleFormChange}>
                  <option value="movie">电影</option>
                  <option value="drama">电视剧</option>
                  <option value="variety">综艺</option>
                </select>
              </label>
              <label>
                类型
                <input name="type" value={form.type || ''} onChange={handleFormChange} />
              </label>
              <label>
                地区
                <input name="region" value={form.region || ''} onChange={handleFormChange} />
              </label>
              <label>
                语言
                <input name="language" value={form.language || ''} onChange={handleFormChange} />
              </label>
              <label>
                年份
                <input name="year" value={form.year || ''} onChange={handleFormChange} />
              </label>
              <label>
                画质
                <input name="quality" value={form.quality || ''} onChange={handleFormChange} />
              </label>
              <label>
                状态
                <input name="status" value={form.status || ''} onChange={handleFormChange} />
              </label>
              <label>
                审核状态
                <select name="auditStatus" value={form.auditStatus || 'pending'} onChange={handleFormChange}>
                  <option value="pending">待审核</option>
                  <option value="approved">已通过</option>
                  <option value="rejected">已拒绝</option>
                </select>
              </label>
              <label>
                发布状态
                <select name="publishStatus" value={form.publishStatus || 'draft'} onChange={handleFormChange}>
                  <option value="draft">草稿</option>
                  <option value="published">已发布</option>
                  <option value="offline">已下架</option>
                </select>
              </label>
              <label className={styles.wideField}>
                封面地址
                <input name="imageUrl" value={form.imageUrl || ''} onChange={handleFormChange} />
              </label>
              <label className={styles.wideField}>
                视频地址
                <input name="videoUrl" value={form.videoUrl || ''} onChange={handleFormChange} />
              </label>
              <label className={styles.wideField}>
                拒绝原因
                <input name="rejectReason" value={form.rejectReason || ''} onChange={handleFormChange} />
              </label>
            </div>

            <div className={styles.modalActions}>
              <button type="button" className={styles.ghostButton} onClick={closeModal}>取消</button>
              <button type="submit" className={styles.primaryButton} disabled={saving}>
                {saving ? '保存中...' : '保存'}
              </button>
            </div>
          </form>
        </div>
      )}
    </main>
  );
}
