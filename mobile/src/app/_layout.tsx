import {
  BeVietnamPro_400Regular,
  BeVietnamPro_500Medium,
  BeVietnamPro_600SemiBold,
  BeVietnamPro_700Bold,
  BeVietnamPro_800ExtraBold,
} from '@expo-google-fonts/be-vietnam-pro';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { useFonts } from 'expo-font';
import { Stack } from 'expo-router';
import * as SplashScreen from 'expo-splash-screen';
import { StatusBar } from 'expo-status-bar';
import { useEffect, useState } from 'react';
import { SafeAreaProvider } from 'react-native-safe-area-context';
import { AuthProvider, useAuth } from '@/lib/auth';
import { BookingProvider } from '@/lib/booking';
import { ProfileProvider } from '@/lib/profile';
import { colors } from '@/lib/theme';

SplashScreen.preventAutoHideAsync().catch(() => undefined);

export default function RootLayout() {
  const [queryClient] = useState(() => new QueryClient({ defaultOptions: { queries: { retry: 1, staleTime: 10_000 } } }));
  // Phông đóng gói cùng app, chờ tải xong mới vẽ để chữ không nhảy
  const [fontsLoaded] = useFonts({
    BeVietnamPro_400Regular,
    BeVietnamPro_500Medium,
    BeVietnamPro_600SemiBold,
    BeVietnamPro_700Bold,
    BeVietnamPro_800ExtraBold,
  });
  if (!fontsLoaded) return null;
  return (
    <SafeAreaProvider>
      <QueryClientProvider client={queryClient}>
        <AuthProvider>
          <StatusBar style="dark" />
          <ProfileProvider>
            <BookingProvider>
              <Routes />
            </BookingProvider>
          </ProfileProvider>
        </AuthProvider>
      </QueryClientProvider>
    </SafeAreaProvider>
  );
}

function Routes() {
  const { user, loading } = useAuth();
  useEffect(() => {
    if (!loading) SplashScreen.hideAsync().catch(() => undefined);
  }, [loading]);
  if (loading) return null;
  return (
    <Stack screenOptions={{ headerShown: false, contentStyle: { backgroundColor: colors.bg } }}>
      <Stack.Protected guard={!!user} redirectTo="/dat-lich">
        <Stack.Screen name="(app)" />
      </Stack.Protected>
      {/* Ai cũng xem được chuyên khoa, bác sĩ, giờ trống; khách mở app hoặc vào trang cần đăng nhập thì về đây */}
      <Stack.Screen name="dat-lich/index" />
      <Stack.Screen name="dat-lich/gio" />
      <Stack.Protected guard={!user}>
        <Stack.Screen name="(auth)/dang-nhap" />
        <Stack.Screen name="(auth)/tao-tai-khoan" />
        <Stack.Screen name="(auth)/quen-mat-khau" />
      </Stack.Protected>
    </Stack>
  );
}
