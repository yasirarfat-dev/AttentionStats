import {
  DarkTheme,
  DefaultTheme,
  NavigationContainer,
  type NavigatorScreenParams,
  type LinkingOptions,
} from '@react-navigation/native';
import { createNativeStackNavigator } from '@react-navigation/native-stack';
import { createBottomTabNavigator } from '@react-navigation/bottom-tabs';
import { StatusBar, useColorScheme } from 'react-native';
import {
  SafeAreaProvider,
  useSafeAreaInsets,
} from 'react-native-safe-area-context';
import { TodayScreen } from '../features/today';
import { HistoryScreen, DiaryScreen } from '../features/history';
import {
  PatternsScreen,
  PatternExplanationScreen,
  CategoriesScreen,
} from '../features/patterns';
import {
  ProtectScreen,
  FocusScreen,
  RulesScreen,
} from '../features/protection';
import {
  SettingsScreen,
  AboutScreen,
  type SettingsDestination,
} from '../features/settings';
import { SourcesScreen } from '../features/sources';
import { StorageScreen } from '../features/storage';
import { BackupScreen } from '../features/backup';
import {
  AppearanceScreen,
  CounterAppearanceScreen,
} from '../features/appearance';
import { ConvenienceScreen } from '../features/convenience';
import { WelcomeScreen } from '../features/onboarding';
import { PrivacyScreen } from '../features/privacy';
import { ReelsScreen } from '../features/reels';
import { NavIcon, type NavSymbol } from '../ui/NavIcon';
import { themes } from '../ui/theme';

type Routes = {
  Today: undefined;
  History: undefined;
  Patterns: undefined;
  Protect: undefined;
  Settings: undefined;
  Reels: undefined;
  Diary: undefined;
  PatternExplanation: undefined;
  Categories: undefined;
  Focus: undefined;
  Rules: undefined;
  Sources: undefined;
  Storage: undefined;
  Backup: undefined;
  Appearance: undefined;
  CounterAppearance: undefined;
  Convenience: undefined;
  About: undefined;
  Welcome: undefined;
  Privacy: undefined;
};
type MainTab =
  'TodayTab' | 'HistoryTab' | 'PatternsTab' | 'ProtectTab' | 'SettingsTab';
type TabRoutes = Record<MainTab, NavigatorScreenParams<Routes> | undefined>;
const Stack = createNativeStackNavigator<Routes>();
const Tabs = createBottomTabNavigator<TabRoutes>();
const linking: LinkingOptions<TabRoutes> = {
  prefixes: ['attentionstats://'],
  config: {
    screens: { TodayTab: { screens: { Today: 'today', Reels: 'reels' } } },
  },
};
const tabs: {
  route: MainTab;
  start: keyof Routes;
  label: string;
  icon: NavSymbol;
}[] = [
  { route: 'TodayTab', start: 'Today', label: 'Today', icon: 'today' },
  { route: 'HistoryTab', start: 'History', label: 'History', icon: 'history' },
  {
    route: 'PatternsTab',
    start: 'Patterns',
    label: 'Patterns',
    icon: 'patterns',
  },
  { route: 'ProtectTab', start: 'Protect', label: 'Protect', icon: 'protect' },
  {
    route: 'SettingsTab',
    start: 'Settings',
    label: 'Settings',
    icon: 'settings',
  },
];

function FeatureStack({
  initial,
  onTab,
}: {
  initial: keyof Routes;
  onTab: (tab: MainTab) => void;
}) {
  return (
    <Stack.Navigator
      initialRouteName={initial}
      screenOptions={{ headerShadowVisible: false, animation: 'none' }}
    >
      <Stack.Screen name="Today" options={{ title: 'Attention Stats' }}>
        {({ navigation }) => (
          <TodayScreen
            onReels={() => navigation.navigate('Reels')}
            onProtect={() => onTab('ProtectTab')}
            onHealth={() => navigation.navigate('Sources')}
            onPrivacy={() => navigation.navigate('Privacy')}
          />
        )}
      </Stack.Screen>
      <Stack.Screen name="History" options={{ title: 'History' }}>
        {({ navigation }) => (
          <HistoryScreen
            onReels={() => navigation.navigate('Reels')}
            onDiary={() => navigation.navigate('Diary')}
            onPrivacy={() => navigation.navigate('Privacy')}
          />
        )}
      </Stack.Screen>
      <Stack.Screen name="Patterns" options={{ title: 'Patterns' }}>
        {({ navigation }) => (
          <PatternsScreen
            onExplanation={() => navigation.navigate('PatternExplanation')}
            onCategories={() => navigation.navigate('Categories')}
            onPrivacy={() => navigation.navigate('Privacy')}
          />
        )}
      </Stack.Screen>
      <Stack.Screen name="Protect" options={{ title: 'Protect' }}>
        {({ navigation }) => (
          <ProtectScreen
            onFocus={() => navigation.navigate('Focus')}
            onRules={() => navigation.navigate('Rules')}
            onPrivacy={() => navigation.navigate('Privacy')}
          />
        )}
      </Stack.Screen>
      <Stack.Screen name="Settings" options={{ title: 'Settings' }}>
        {({ navigation }) => (
          <SettingsScreen
            onOpen={(route: SettingsDestination) => navigation.navigate(route)}
            onPrivacy={() => navigation.navigate('Privacy')}
          />
        )}
      </Stack.Screen>
      <Stack.Screen name="Reels" options={{ title: 'Reels & short videos' }}>
        {({ navigation }) => (
          <ReelsScreen onPrivacy={() => navigation.navigate('Privacy')} />
        )}
      </Stack.Screen>
      <Stack.Screen name="Diary" options={{ title: 'Activity diary' }}>
        {({ navigation }) => (
          <DiaryScreen
            onReels={() => navigation.navigate('Reels')}
            onHealth={() => navigation.navigate('Sources')}
          />
        )}
      </Stack.Screen>
      <Stack.Screen
        name="PatternExplanation"
        component={PatternExplanationScreen}
        options={{ title: 'How patterns work' }}
      />
      <Stack.Screen
        name="Categories"
        component={CategoriesScreen}
        options={{ title: 'App categories' }}
      />
      <Stack.Screen
        name="Focus"
        component={FocusScreen}
        options={{ title: 'Focus sessions' }}
      />
      <Stack.Screen
        name="Rules"
        component={RulesScreen}
        options={{ title: 'Budgets & schedules' }}
      />
      <Stack.Screen name="Sources" options={{ title: 'Sources & permissions' }}>
        {({ navigation }) => (
          <SourcesScreen onReels={() => navigation.navigate('Reels')} />
        )}
      </Stack.Screen>
      <Stack.Screen name="Storage" options={{ title: 'Local storage' }}>
        {({ navigation }) => (
          <StorageScreen onReels={() => navigation.navigate('Reels')} />
        )}
      </Stack.Screen>
      <Stack.Screen
        name="Backup"
        component={BackupScreen}
        options={{ title: 'Backup & recovery' }}
      />
      <Stack.Screen
        name="Appearance"
        component={AppearanceScreen}
        options={{ title: 'App appearance' }}
      />
      <Stack.Screen
        name="CounterAppearance"
        options={{ title: 'Floating counter' }}
      >
        {({ navigation }) => (
          <CounterAppearanceScreen
            onReels={() => navigation.navigate('Reels')}
          />
        )}
      </Stack.Screen>
      <Stack.Screen
        name="Convenience"
        component={ConvenienceScreen}
        options={{ title: 'Convenience features' }}
      />
      <Stack.Screen name="About" options={{ title: 'About Attention Stats' }}>
        {({ navigation }) => (
          <AboutScreen
            onPrivacy={() => navigation.navigate('Privacy')}
            onWelcome={() => navigation.navigate('Welcome')}
          />
        )}
      </Stack.Screen>
      <Stack.Screen name="Welcome" options={{ title: 'Welcome' }}>
        {({ navigation }) => (
          <WelcomeScreen
            onPrivacy={() => navigation.navigate('Privacy')}
            onReels={() => navigation.navigate('Reels')}
          />
        )}
      </Stack.Screen>
      <Stack.Screen
        name="Privacy"
        component={PrivacyScreen}
        options={{ title: 'About your data' }}
      />
    </Stack.Navigator>
  );
}

function MainTabs() {
  const insets = useSafeAreaInsets();
  const colors = useColorScheme() === 'dark' ? themes.dark : themes.light;
  return (
    <Tabs.Navigator
      initialRouteName="TodayTab"
      backBehavior="history"
      screenOptions={{
        headerShown: false,
        animation: 'none',
        tabBarActiveTintColor: colors.accent,
        tabBarInactiveTintColor: colors.muted,
        tabBarStyle: {
          backgroundColor: colors.surface,
          borderTopColor: colors.border,
          height: 72 + insets.bottom,
          paddingBottom: insets.bottom,
          paddingTop: 8,
        },
        tabBarLabelStyle: { fontSize: 11, fontWeight: '600' },
      }}
    >
      {tabs.map(tab => (
        <Tabs.Screen
          key={tab.route}
          name={tab.route}
          options={{
            title: tab.label,
            tabBarAccessibilityLabel: tab.label,
            tabBarIcon: ({ color, size }) => (
              <NavIcon name={tab.icon} color={color} size={size} />
            ),
          }}
        >
          {({ navigation }) => (
            <FeatureStack
              initial={tab.start}
              onTab={name => navigation.navigate(name)}
            />
          )}
        </Tabs.Screen>
      ))}
    </Tabs.Navigator>
  );
}

export default function App() {
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
            card: colors.canvas,
            text: colors.text,
            primary: colors.accent,
            border: colors.border,
          },
        }}
      >
        <MainTabs />
      </NavigationContainer>
    </SafeAreaProvider>
  );
}
