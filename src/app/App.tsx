import {
  DarkTheme,
  DefaultTheme,
  NavigationContainer,
  type LinkingOptions,
  type NavigatorScreenParams,
} from '@react-navigation/native';
import { createBottomTabNavigator } from '@react-navigation/bottom-tabs';
import { AccessibilityInfo, AppState, StatusBar, View } from 'react-native';
import { clearHistoryCache, warmHistory } from '../features/diary';
import { useEffect, useState } from 'react';
import {
  SafeAreaProvider,
  useSafeAreaInsets,
} from 'react-native-safe-area-context';
import NativeDiary from '../platform/specs/NativeDiary';
import { parseDiarySnapshot } from '../platform/diaryStatus';
import { TodayScreen } from '../features/today';
import { InsightsScreen } from '../features/insights';
import {
  ProtectionNavigator,
  type ProtectionRoutes,
} from '../features/protection';
import { SettingsScreen } from '../features/settings';
import { WelcomeScreen } from '../features/onboarding';
import { NavIcon, type NavSymbol } from '../ui/NavIcon';
import { applyAppearance, useAppearance } from '../ui/theme';
import { loadAppearance } from '../platform/appearance';
import { luminance } from '../ui/customPalette';
import { useUnistyles } from 'react-native-unistyles';
import { ResponsiveTabBar } from './ResponsiveTabBar';

type Tabs = {
  Today: undefined;
  Insights: undefined;
  Protect: NavigatorScreenParams<ProtectionRoutes> | undefined;
  Settings: undefined;
};
const Tab = createBottomTabNavigator<Tabs>();
const linking: LinkingOptions<Tabs> = {
  prefixes: ['attentionstats://'],
  config: {
    screens: {
      Today: 'today',
      Insights: 'insights',
      Protect: {
        path: 'protect',
        screens: {
          Overview: '',
          Focus: 'focus',
          Schedules: 'schedules',
          Breaks: 'breaks',
          Blocks: 'blocks',
          Counter: 'counter',
        },
      },
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
  useEffect(() => {
    void warmHistory();
    const listener = AppState.addEventListener('change', state => {
      if (state === 'active') {
        clearHistoryCache();
        void warmHistory();
      }
    });
    return () => listener.remove();
  }, []);
  const insets = useSafeAreaInsets();
  const { theme: colors } = useUnistyles();
  const appearance = useAppearance();
  const [reduceMotion, setReduceMotion] = useState(true);
  useEffect(() => {
    AccessibilityInfo.isReduceMotionEnabled().then(setReduceMotion);
    const listener = AccessibilityInfo.addEventListener(
      'reduceMotionChanged',
      setReduceMotion,
    );
    return () => listener.remove();
  }, []);
  return (
    <Tab.Navigator
      tabBar={props => <ResponsiveTabBar {...props} />}
      initialRouteName="Today"
      backBehavior="history"
      screenOptions={({ route }) => ({
        headerShown: false,
        animation: appearance.motion && !reduceMotion ? 'fade' : 'none',
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
            onProtect={() =>
              navigation.navigate('Protect', { screen: 'Overview' })
            }
            onQuickAction={(screen, quickStart) =>
              screen === 'Focus'
                ? navigation.navigate('Protect', {
                    screen,
                    params: { quickStart },
                    initial: false,
                  })
                : screen === 'Schedules'
                  ? navigation.navigate('Protect', {
                      screen,
                      params: { view: 'groups' },
                      initial: false,
                    })
                  : navigation.navigate('Protect', { screen, initial: false })
            }
          />
        )}
      </Tab.Screen>
      <Tab.Screen name="Insights" component={InsightsScreen} />
      <Tab.Screen name="Protect" component={ProtectionNavigator} />
      <Tab.Screen name="Settings">
        {() => <SettingsScreen onWelcome={onWelcome} />}
      </Tab.Screen>
    </Tab.Navigator>
  );
}

export default function App() {
  const [welcome, setWelcome] = useState<boolean | null>(null);
  const [appearanceLoaded, setAppearanceLoaded] = useState(false);
  useEffect(() => {
    let active = true;
    loadAppearance()
      .then(value => {
        if (active) applyAppearance(value);
      })
      .catch(() => {})
      .finally(() => {
        if (active) setAppearanceLoaded(true);
      });
    return () => {
      active = false;
    };
  }, []);
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
  const { theme: colors } = useUnistyles();
  const dark = luminance(colors.canvas) < 0.179;
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
        {welcome === null || !appearanceLoaded ? (
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
