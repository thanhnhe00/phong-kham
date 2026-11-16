import { useEffect, useState } from 'react';
import { api, errorText } from './api';

export const PHONE_RE = /^0\d{9}$/;
export const cleanPhone = (p: string) => p.replace(/\s/g, '');

export interface SmsTicket {
  phone: string;
  code: string;
  /** Số của phòng khám; null khi phòng khám chưa cấu hình số nhận tin */
  smsTo: string | null;
  smsBody: string;
  expiresAt: string;
}

const POLL_MS = 3000;

/**
 * Xác minh số điện thoại bằng tin nhắn NGƯỜI DÙNG gửi đi: xin mã, người dùng nhắn "PKXM <mã>" tới số phòng khám
 * từ chính SIM của mình, app hỏi lại mỗi 3 giây tới khi phòng khám nhận được. Backend cho xin lại sau 60 giây.
 */
export function usePhoneCode(purpose: 'REGISTER' | 'RESET_PASSWORD') {
  const [ticket, setTicket] = useState<SmsTicket | null>(null);
  const [verified, setVerified] = useState(false);
  const [pollError, setPollError] = useState<string | null>(null);
  const [wait, setWait] = useState(0);

  useEffect(() => {
    if (wait <= 0) return;
    const t = setTimeout(() => setWait(wait - 1), 1000);
    return () => clearTimeout(t);
  }, [wait]);

  useEffect(() => {
    if (!ticket || verified || pollError) return;
    let stop = false;
    const t = setInterval(async () => {
      try {
        const res = await api.post<{ status: string }>('/auth/phone-codes/check', { phone: ticket.phone, purpose, code: ticket.code });
        if (!stop && res.status === 'VERIFIED') setVerified(true);
      } catch (e) {
        // mã hết hạn hoặc sai quá nhiều lần: dừng hỏi, người dùng xin mã mới
        if (!stop) setPollError(errorText(e));
      }
    }, POLL_MS);
    return () => {
      stop = true;
      clearInterval(t);
    };
  }, [ticket, verified, pollError, purpose]);

  const send = async (phone: string) => {
    const res = await api.post<Omit<SmsTicket, 'phone'>>('/auth/phone-codes', { phone, purpose });
    setTicket({ ...res, phone });
    setVerified(false);
    setPollError(null);
    setWait(60);
  };
  return { send, ticket, verified, pollError, wait };
}
