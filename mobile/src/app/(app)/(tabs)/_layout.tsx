import { Tabs } from 'expo-router';
import { TabBar } from '@/components/tab-bar';
import { colors } from '@/lib/theme';

export default function TabsLayout() {
  return (
    <Tabs tabBar={(props) => <TabBar {...props} />} screenOptions={{ headerShown: false, sceneStyle: { backgroundColor: colors.bg } }}>
      <Tabs.Screen name="index" />
      <Tabs.Screen name="lich-hen" />
      <Tabs.Screen name="ho-so" />
      <Tabs.Screen name="tai-khoan" />
    </Tabs>
  );
}
