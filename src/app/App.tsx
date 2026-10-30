import {
  DarkTheme,
  DefaultTheme,
  NavigationContainer,
  type LinkingOptions,
} from '@react-navigation/native';
import { createBottomTabNavigator } from '@react-navigation/bottom-tabs';
import { StatusBar, useColorScheme, View } from 'react-native';
import { useEffect, useState } from 'react';
import {
  SafeAreaProvider,
  useSafeAreaInsets,
} from 'react-native-safe-area-context';
import NativeDiary from '../platform/specs/NativeDiary';
import { parseDiarySnapshot } from '../platform/diaryStatus';
import { TodayScreen } from '../features/today';
import { InsightsScreen } from '../features/insights';
import { ProtectScreen } from '../features/protection';
import { SettingsScreen } from '../features/settings';
import { WelcomeScreen } from '../features/onboarding';
import { NavIcon, type NavSymbol } from '../ui/NavIcon';
import { themes } from '../ui/theme';

type Tabs = {
  Today: undefined;
  Insights: undefined;
  Protect: undefined;
  Settings: undefined;
};
const Tab = createBottomTabNavigator<Tabs>();
const linking: LinkingOptions<Tabs> = {
  prefixes: ['attentionstats://'],
  config: {
    screens: {
      Today: 'today',
      Insights: 'insights',
      Protect: 'protect',
      Settings: { path: 'settings', alias: ['convenience', 'reels'] },
    },
  },
};
const icons: Record<keyof Tabs, NavSymbol> = {
  Today: 'today',
  Insights: 'patterns',
  Protect: 'protect',
  Settings: 'settings',
};

function MainTabs({ onWelcome }: { onWelcome: () => void }) {
  const insets = useSafeAreaInsets();
  const colors = useColorScheme() === 'dark' ? themes.dark : themes.light;
  return (
    <Tab.Navigator
      initialRouteName="Today"
      backBehavior="history"
      screenOptions={({ route }) => ({
        headerShown: false,
        tabBarActiveTintColor: colors.accent,
        tabBarInactiveTintColor: colors.muted,
        tabBarStyle: {
          backgroundColor: colors.surface,
          borderTopColor: colors.border,
          height: 66 + insets.bottom,
          paddingBottom: insets.bottom + 6,
          paddingTop: 8,
        },
        tabBarLabelStyle: { fontSize: 12, fontWeight: '700' },
        tabBarIcon: ({ color, size }) => (
          <NavIcon name={icons[route.name]} color={color} size={size} />
        ),
      })}
    >
      <Tab.Screen name="Today">
        {({ navigation }) => (
          <TodayScreen
            onSetup={onWelcome}
            onProtect={() => navigation.navigate('Protect')}
          />
        )}
      </Tab.Screen>
      <Tab.Screen name="Insights" component={InsightsScreen} />
      <Tab.Screen name="Protect" component={ProtectScreen} />
      <Tab.Screen name="Settings">
        {() => <SettingsScreen onWelcome={onWelcome} />}
      </Tab.Screen>
    </Tab.Navigator>
  );
}

export default function App() {
  const [welcome, setWelcome] = useState<boolean | null>(null);
  useEffect(() => {
    let active = true;
    if (!NativeDiary) {
      setWelcome(false);
      return;
    }
    NativeDiary.getSnapshot(1)
      .then(
        payload =>
          active && setWelcome(!parseDiarySnapshot(payload).onboardingSeen),
      )
      .catch(() => active && setWelcome(false));
    return () => {
      active = false;
    };
  }, []);
  const dark = useColorScheme() === 'dark';
  const colors = dark ? themes.dark : themes.light;
  const base = dark ? DarkTheme : DefaultTheme;
  return (
    <SafeAreaProvider>
      <StatusBar barStyle={dark ? 'light-content' : 'dark-content'} />
      <NavigationContainer
        linking={linking}
        theme={{
          ...base,
          colors: {
            ...base.colors,
            background: colors.canvas,
            card: colors.surface,
            text: colors.text,
            primary: colors.accent,
            border: colors.border,
          },
        }}
      >
        {welcome === null ? (
          <View style={{ flex: 1, backgroundColor: colors.canvas }} />
        ) : welcome ? (
          <WelcomeScreen onDone={() => setWelcome(false)} />
        ) : (
          <MainTabs onWelcome={() => setWelcome(true)} />
        )}
      </NavigationContainer>
    </SafeAreaProvider>
  );
}
