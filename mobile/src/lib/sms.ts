/** Đường dẫn mở sẵn màn soạn tin nhắn: iOS nối nội dung bằng "&", Android và trình duyệt bằng "?" */
export function smsUrl(to: string, body: string, os: string): string {
  return `sms:${to}${os === 'ios' ? '&' : '?'}body=${encodeURIComponent(body)}`;
}
