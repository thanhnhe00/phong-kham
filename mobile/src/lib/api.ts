import Constants from 'expo-constants';
import { Platform } from 'react-native';

/**
 * Địa chỉ API, theo thứ tự ưu tiên:
 * 1. EXPO_PUBLIC_API_URL (bản build trỏ về máy chủ thật)
 * 2. Bản web: '/api' cùng địa chỉ trang, Metro (metro.config.js) hoặc máy chủ thử nghiệm chuyển tiếp sang backend
 * 3. expo.extra.apiUrl trong app.json (IP LAN của máy chạy backend, cho điện thoại thật)
 */
export const API_URL: string =
  process.env.EXPO_PUBLIC_API_URL ??
  (Platform.OS === 'web' ? '/api' : undefined) ??
  (Constants.expoConfig?.extra?.apiUrl as string | undefined) ??
  'http://10.0.2.2:8080/api';

/** Lỗi từ backend: luôn có câu tiếng Việt hiển thị được, kèm lỗi từng trường khi dữ liệu nhập sai. */
export class ApiError extends Error {
  constructor(
    message: string,
    readonly status: number,
    readonly fieldErrors: Record<string, string> = {},
  ) {
    super(message);
  }
}

let token: string | null = null;
let onUnauthorized: (() => void) | null = null;
export const setToken = (t: string | null) => (token = t);
export const getToken = () => token;
export const setUnauthorizedHandler = (fn: (() => void) | null) => (onUnauthorized = fn);

type Body = Record<string, unknown> | unknown[] | undefined;

export async function request<T>(method: string, path: string, body?: Body): Promise<T> {
  let res: Response;
  try {
    res = await fetch(API_URL + path, {
      method,
      headers: {
        Accept: 'application/json',
        ...(body !== undefined ? { 'Content-Type': 'application/json' } : {}),
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
      },
      body: body !== undefined ? JSON.stringify(body) : undefined,
    });
  } catch {
    throw new ApiError('Không kết nối được phòng khám. Kiểm tra mạng rồi thử lại.', 0);
  }
  const text = await res.text();
  const data = text ? safeJson(text) : null;
  if (!res.ok) {
    // 401 ở API cần đăng nhập nghĩa là phiên đã hết hạn hoặc tài khoản bị khóa: đăng xuất trên máy
    if (res.status === 401 && token && !path.startsWith('/auth/')) onUnauthorized?.();
    const d = (data ?? {}) as { message?: string; errors?: Record<string, string> };
    throw new ApiError(d.message ?? fallbackMessage(res.status), res.status, d.errors ?? {});
  }
  return data as T;
}

function safeJson(text: string): unknown {
  try {
    return JSON.parse(text);
  } catch {
    return null;
  }
}

export function fallbackMessage(status: number): string {
  if (status === 401) return 'Phiên đăng nhập đã hết hạn, vui lòng đăng nhập lại.';
  if (status === 403) return 'Bạn không có quyền thực hiện thao tác này.';
  if (status === 404) return 'Không tìm thấy dữ liệu.';
  if (status === 429) return 'Bạn thao tác quá nhiều lần, vui lòng thử lại sau ít phút.';
  if (status >= 500) return 'Phòng khám đang gặp sự cố, vui lòng thử lại sau.';
  return 'Có lỗi xảy ra, vui lòng thử lại.';
}

export const api = {
  get: <T>(path: string) => request<T>('GET', path),
  post: <T>(path: string, body?: Body) => request<T>('POST', path, body ?? {}),
  put: <T>(path: string, body?: Body) => request<T>('PUT', path, body ?? {}),
  del: <T>(path: string) => request<T>('DELETE', path),
};

/** Câu thông báo lỗi cho người dùng từ một lỗi bất kỳ. */
export function errorText(err: unknown): string {
  if (err instanceof ApiError) return err.message;
  return 'Có lỗi xảy ra, vui lòng thử lại.';
}
