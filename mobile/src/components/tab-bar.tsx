import type { BottomTabBarProps } from 'expo-router/tabs';
import { Pressable, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { colors } from '@/lib/theme';
import { Icon, type IconName } from './icon';
import { T } from './text';

const ITEMS: Record<string, { icon: IconName; label: string }> = {
  index: { icon: 'home', label: 'Trang chủ' },
  'lich-hen': { icon: 'cal', label: 'Lịch hẹn' },
  'ho-so': { icon: 'doc', label: 'Hồ sơ sức khỏe' },
  'tai-khoan': { icon: 'user', label: 'Tài khoản' },
};

/** Thanh tab dưới cùng: mục đang mở có nền viên thuốc xanh nhạt */
export function TabBar({ state, navigateToTab }: BottomTabBarProps) {
  const insets = useSafeAreaInsets();
  return (
    <View accessibilityRole="tablist" style={{ flexDirection: 'row', backgroundColor: colors.card, paddingTop: 8, paddingBottom: Math.max(insets.bottom, 12), paddingHorizontal: 8 }}>
      {state.routes.map((route, i) => {
        const item = ITEMS[route.name];
        if (!item) return null;
        const on = state.index === i;
        return (
          <Pressable
            key={route.key}
            accessibilityRole="tab"
            accessibilityState={{ selected: on }}
            accessibilityLabel={item.label}
            onPress={() => {
              if (!on) navigateToTab(route.key);
            }}
            style={{ flex: 1, alignItems: 'center', gap: 4 }}
          >
            <View style={{ width: 56, height: 30, borderRadius: 15, backgroundColor: on ? colors.soft : 'transparent', alignItems: 'center', justifyContent: 'center' }}>
              <Icon name={item.icon} color={on ? colors.brandDark : colors.muted} />
            </View>
            <T size={12} weight={on ? 700 : 400} color={on ? colors.brandDark : colors.muted}>
              {item.label}
            </T>
          </Pressable>
        );
      })}
    </View>
  );
}
