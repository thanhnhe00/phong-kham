import * as SecureStore from 'expo-secure-store';
import { Platform } from 'react-native';

/**
 * Lưu giá trị nhỏ an toàn: Keychain (iOS) / Keystore (Android) qua expo-secure-store.
 * Bản web (chỉ để xem thử và kiểm thử tự động) không có SecureStore nên dùng localStorage.
 */
export const storage = {
  async get(key: string): Promise<string | null> {
    try {
      if (Platform.OS === 'web') return localStorage.getItem(key);
      return await SecureStore.getItemAsync(key);
    } catch {
      return null;
    }
  },
  async set(key: string, value: string | null) {
    try {
      if (Platform.OS === 'web') {
        if (value === null) localStorage.removeItem(key);
        else localStorage.setItem(key, value);
      } else if (value === null) await SecureStore.deleteItemAsync(key);
      else await SecureStore.setItemAsync(key, value);
    } catch {
      /* không lưu được thì lần sau đăng nhập lại, không làm hỏng app */
    }
  },
};

export const KEYS = { token: 'pk.token', patient: 'pk.patient' } as const;
