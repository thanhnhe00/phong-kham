import { API_URL, ApiError, fallbackMessage, getToken } from './api';
import type { ResultFile } from './types';

/** Bản web: tải tệp kèm mã đăng nhập rồi mở ở thẻ mới (PDF, ảnh trình duyệt tự hiển thị). */
export async function openResultFile(f: ResultFile): Promise<void> {
  // Mở thẻ ngay trong lúc người dùng bấm để trình duyệt không chặn cửa sổ bật lên
  const tab = window.open('', '_blank');
  let res: Response;
  try {
    res = await fetch(`${API_URL}/me/files/${f.id}`, { headers: { Authorization: `Bearer ${getToken() ?? ''}` } });
  } catch {
    tab?.close();
    throw new ApiError('Không tải được tệp kết quả. Kiểm tra mạng rồi thử lại.', 0);
  }
  if (!res.ok) {
    tab?.close();
    const body = (await res.json().catch(() => ({}))) as { message?: string };
    throw new ApiError(body.message ?? fallbackMessage(res.status), res.status);
  }
  const url = URL.createObjectURL(await res.blob());
  if (tab) tab.location.href = url;
  else window.location.href = url;
  setTimeout(() => URL.revokeObjectURL(url), 60_000);
}
