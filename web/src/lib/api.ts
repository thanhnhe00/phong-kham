// Gọi backend. Lỗi luôn có dạng {message, errors}; thông báo tiếng Việt hiển thị thẳng cho người dùng.
const BASE = '/api';
const TOKEN_KEY = 'pk.staff.token';

export class ApiError extends Error {
  status: number;
  fieldErrors?: Record<string, string>;
  constructor(message: string, status: number, fieldErrors?: Record<string, string>) {
    super(message);
    this.status = status;
    this.fieldErrors = fieldErrors;
  }
}

let token: string | null = readToken();
let onUnauthorized: (() => void) | null = null;

function readToken() {
  try {
    return sessionStorage.getItem(TOKEN_KEY);
  } catch {
    return null;
  }
}

export function getToken() {
  return token;
}

export function setToken(t: string | null) {
  token = t;
  try {
    if (t) sessionStorage.setItem(TOKEN_KEY, t);
    else sessionStorage.removeItem(TOKEN_KEY);
  } catch {
    /* trình duyệt chặn lưu trữ: chỉ giữ trong bộ nhớ */
  }
}

export function setUnauthorizedHandler(fn: (() => void) | null) {
  onUnauthorized = fn;
}

async function request<T>(method: string, path: string, body?: unknown): Promise<T> {
  const headers: Record<string, string> = {};
  if (token) headers.Authorization = `Bearer ${token}`;
  const isForm = body instanceof FormData;
  if (body !== undefined && !isForm) headers['Content-Type'] = 'application/json';
  let res: Response;
  try {
    res = await fetch(BASE + path, { method, headers, body: isForm ? body : body === undefined ? undefined : JSON.stringify(body) });
  } catch {
    throw new ApiError('Không kết nối được máy chủ. Kiểm tra mạng rồi thử lại.', 0);
  }
  if (res.status === 401 && token) onUnauthorized?.();
  if (!res.ok) {
    const data = await res.json().catch(() => null);
    throw new ApiError(data?.message ?? fallback(res.status), res.status, data?.errors);
  }
  if (res.status === 204) return undefined as T;
  const text = await res.text();
  return (text ? JSON.parse(text) : undefined) as T;
}

function fallback(status: number) {
  if (status === 401) return 'Phiên đăng nhập đã hết, vui lòng đăng nhập lại.';
  if (status === 403) return 'Bạn không có quyền thực hiện thao tác này.';
  if (status === 404) return 'Không tìm thấy dữ liệu.';
  return 'Có lỗi xảy ra, vui lòng thử lại.';
}

export const api = {
  get: <T>(path: string) => request<T>('GET', path),
  post: <T>(path: string, body: unknown = {}) => request<T>('POST', path, body),
  put: <T>(path: string, body: unknown) => request<T>('PUT', path, body),
  patch: <T>(path: string, body: unknown) => request<T>('PATCH', path, body),
  del: <T>(path: string) => request<T>('DELETE', path),
};

export function errorText(e: unknown) {
  if (e instanceof ApiError) {
    const fields = e.fieldErrors ? Object.values(e.fieldErrors) : [];
    return fields.length ? fields.join('. ') : e.message;
  }
  return 'Có lỗi xảy ra, vui lòng thử lại.';
}

/** Mở tệp kết quả (cần token nên tải về rồi mở trong tab mới) */
export async function openFile(fileId: number) {
  const tab = window.open('', '_blank');
  const res = await fetch(`${BASE}/files/${fileId}`, { headers: token ? { Authorization: `Bearer ${token}` } : {} });
  if (!res.ok) {
    tab?.close();
    throw new ApiError('Không mở được tệp.', res.status);
  }
  const url = URL.createObjectURL(await res.blob());
  if (tab) tab.location.href = url;
  else window.location.href = url;
}
