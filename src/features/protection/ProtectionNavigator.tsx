import { createNativeStackNavigator } from '@react-navigation/native-stack';
import { ProtectScreen } from './ProtectScreen';
import { SchedulesScreen } from './SchedulesScreen';

export type ProtectionRoutes = {
  Overview: undefined;
  Schedules: { packageName?: string } | undefined;
};
const Stack = createNativeStackNavigator<ProtectionRoutes>();
export function ProtectionNavigator() {
  return (
    <Stack.Navigator screenOptions={{ headerShown: false, animation: 'none' }}>
      <Stack.Screen name="Overview">
        {({ navigation }) => (
          <ProtectScreen
            onSchedules={packageName =>
              navigation.navigate('Schedules', { packageName })
            }
          />
        )}
      </Stack.Screen>
      <Stack.Screen name="Schedules">
        {({ navigation, route }) => (
          <SchedulesScreen
            packageName={route.params?.packageName}
            onBack={() =>
              navigation.reset({ index: 0, routes: [{ name: 'Overview' }] })
            }
          />
        )}
      </Stack.Screen>
    </Stack.Navigator>
  );
}
