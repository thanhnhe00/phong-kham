import { describe, expect, it } from 'vitest';
import { smsUrl } from './sms';

describe('mở màn soạn tin nhắn xác minh', () => {
  it('Android dùng ?body=, nội dung được mã hóa', () => {
    expect(smsUrl('0900000000', 'PKXM 123456', 'android')).toBe('sms:0900000000?body=PKXM%20123456');
  });
  it('iOS dùng &body=', () => {
    expect(smsUrl('0900000000', 'PKXM 123456', 'ios')).toBe('sms:0900000000&body=PKXM%20123456');
  });
});
