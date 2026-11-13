import { createNativeStackNavigator } from '@react-navigation/native-stack';
import type { QuickFocusRequest } from './focusChoices';
import { FocusScreen } from './FocusScreen';
import { ProtectionToolScreen } from './ProtectionToolScreen';
import { ProtectScreen } from './ProtectScreen';
import { SchedulesScreen } from './SchedulesScreen';

export type ProtectionRoutes = {
  Overview: undefined;
  Focus: { quickStart?: QuickFocusRequest } | undefined;
  Breaks: undefined;
  Blocks: undefined;
  Counter: undefined;
  Schedules: { packageName?: string; view?: 'groups' | 'slots' } | undefined;
};
const Stack = createNativeStackNavigator<ProtectionRoutes>();
export function ProtectionNavigator() {
  return (
    <Stack.Navigator screenOptions={{ headerShown: false, animation: 'none' }}>
      <Stack.Screen name="Overview">
        {({ navigation }) => (
          <ProtectScreen
            onFocus={() => navigation.navigate('Focus')}
            onTool={tool => navigation.navigate(tool)}
            onSchedules={view => navigation.navigate('Schedules', { view })}
          />
        )}
      </Stack.Screen>
      {(['Breaks', 'Blocks', 'Counter'] as const).map(tool => (
        <Stack.Screen key={tool} name={tool}>
          {({ navigation }) => (
            <ProtectionToolScreen
              tool={tool}
              onBack={() =>
                navigation.reset({ index: 0, routes: [{ name: 'Overview' }] })
              }
              onSchedules={packageName =>
                navigation.navigate('Schedules', { packageName })
              }
            />
          )}
        </Stack.Screen>
      ))}
      <Stack.Screen name="Focus">
        {({ navigation, route }) => (
          <FocusScreen
            quickStart={route.params?.quickStart}
            onBack={() =>
              navigation.reset({ index: 0, routes: [{ name: 'Overview' }] })
            }
            onSchedules={() => navigation.navigate('Schedules')}
          />
        )}
      </Stack.Screen>
      <Stack.Screen name="Schedules">
        {({ navigation, route }) => (
          <SchedulesScreen
            packageName={route.params?.packageName}
            initialView={route.params?.view}
            onBack={() =>
              navigation.reset({ index: 0, routes: [{ name: 'Overview' }] })
            }
          />
        )}
      </Stack.Screen>
    </Stack.Navigator>
  );
}
