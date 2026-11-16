import { Directory, File, Paths } from 'expo-file-system';
import * as Sharing from 'expo-sharing';
import { API_URL, ApiError, getToken } from './api';
import type { ResultFile } from './types';

/**
 * Mở tệp kết quả xét nghiệm (PDF/ảnh): tải về bộ nhớ đệm kèm mã đăng nhập rồi xem ngay trong máy
 * (Quick Look trên iOS, ứng dụng xem tệp trên Android). Máy không có trình xem phù hợp thì mở bảng chia sẻ.
 * Backend chỉ trả tệp của kết quả đã được bác sĩ duyệt, thuộc hồ sơ tài khoản đang quản lý.
 */
export async function openResultFile(f: ResultFile): Promise<void> {
  const dir = new Directory(Paths.cache, 'ket-qua');
  dir.create({ idempotent: true, intermediates: true });
  // Tên tệp theo id để không đụng nhau giữa các kết quả trùng tên
  const target = new File(dir, `${f.id}-${f.fileName.replace(/[^\w.\-]+/g, '_')}`);
  let file: File;
  try {
    file = await File.downloadFileAsync(`${API_URL}/me/files/${f.id}`, target, {
      headers: { Authorization: `Bearer ${getToken() ?? ''}` },
      idempotent: true,
    });
  } catch {
    throw new ApiError('Không tải được tệp kết quả. Kiểm tra mạng rồi thử lại.', 0);
  }
  if (await file.canPreview({ mimeType: f.mimeType }).catch(() => false)) {
    await file.preview({ title: f.fileName, mimeType: f.mimeType });
  } else if (await Sharing.isAvailableAsync()) {
    await Sharing.shareAsync(file.uri, { mimeType: f.mimeType, dialogTitle: f.fileName });
  } else {
    throw new ApiError('Máy chưa có ứng dụng mở được loại tệp này.', 0);
  }
}
