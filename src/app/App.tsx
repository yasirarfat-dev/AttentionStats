import {
  DarkTheme,
  DefaultTheme,
  NavigationContainer,
} from '@react-navigation/native';
import { createNativeStackNavigator } from '@react-navigation/native-stack';
import { StatusBar, useColorScheme } from 'react-native';
import { SafeAreaProvider } from 'react-native-safe-area-context';
import { WelcomeScreen } from '../features/onboarding';
import { PrivacyScreen } from '../features/privacy';
import { ReelsScreen } from '../features/reels';
import { themes } from '../ui/theme';

type Routes = { Welcome: undefined; Privacy: undefined; Reels: undefined };
const Stack = createNativeStackNavigator<Routes>();

export default function App() {
  const dark = useColorScheme() === 'dark';
  const colors = dark ? themes.dark : themes.light;
  const base = dark ? DarkTheme : DefaultTheme;
  return (
    <SafeAreaProvider>
      <StatusBar barStyle={dark ? 'light-content' : 'dark-content'} />
      <NavigationContainer
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
        <Stack.Navigator
          initialRouteName="Reels"
          screenOptions={{ headerShadowVisible: false, animation: 'none' }}
        >
          <Stack.Screen name="Welcome" options={{ title: 'Attention Stats' }}>
            {({ navigation }) => (
              <WelcomeScreen
                onPrivacy={() => navigation.navigate('Privacy')}
                onReels={() => navigation.navigate('Reels')}
              />
            )}
          </Stack.Screen>
          <Stack.Screen name="Reels" options={{ title: 'Attention Stats' }}>
            {({ navigation }) => (
              <ReelsScreen onPrivacy={() => navigation.navigate('Privacy')} />
            )}
          </Stack.Screen>
          <Stack.Screen
            name="Privacy"
            component={PrivacyScreen}
            options={{ title: 'About your data' }}
          />
        </Stack.Navigator>
      </NavigationContainer>
    </SafeAreaProvider>
  );
}
