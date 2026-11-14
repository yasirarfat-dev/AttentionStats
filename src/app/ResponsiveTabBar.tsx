import { CommonActions } from '@react-navigation/native';
import {
  BottomTabBar,
  type BottomTabBarProps,
} from '@react-navigation/bottom-tabs';
import { Pressable, Text, View, useWindowDimensions } from 'react-native';
import { useUnistyles } from 'react-native-unistyles';

/** Keep full tab names at large text sizes without reducing system scaling. */
export function ResponsiveTabBar(props: BottomTabBarProps) {
  const { width, fontScale } = useWindowDimensions();
  const { theme } = useUnistyles();
  if ((width - props.insets.left - props.insets.right) / fontScale >= 300)
    return <BottomTabBar {...props} />;

  const { state, descriptors, navigation, insets } = props;
  return (
    <View
      style={{
        backgroundColor: theme.surface,
        borderTopColor: theme.border,
        borderTopWidth: 1,
        paddingLeft: insets.left,
        paddingRight: insets.right,
        paddingBottom: insets.bottom + 6,
        paddingTop: 6,
        flexDirection: 'row',
        flexWrap: 'wrap',
      }}
    >
      {state.routes.map((route, index) => {
        const focused = index === state.index;
        const descriptor = descriptors[route.key];
        if (!descriptor) return null;
        const { options } = descriptor;
        const color = focused ? theme.accent : theme.muted;
        return (
          <Pressable
            key={route.key}
            accessibilityRole="tab"
            accessibilityLabel={options.tabBarAccessibilityLabel ?? route.name}
            accessibilityState={{ selected: focused }}
            testID={options.tabBarButtonTestID}
            onPress={() => {
              const event = navigation.emit({
                type: 'tabPress',
                target: route.key,
                canPreventDefault: true,
                data: {
                  behavior: {
                    scrollToTop:
                      focused &&
                      options.tabBarRepeatedPressBehavior?.scrollToTop !==
                        false,
                    popToTop:
                      focused &&
                      options.tabBarRepeatedPressBehavior?.popToTop !== false,
                  },
                },
              });
              if (!focused && !event.defaultPrevented)
                navigation.dispatch({
                  ...CommonActions.navigate(route.name, route.params),
                  target: state.key,
                });
            }}
            onLongPress={() =>
              navigation.emit({ type: 'tabLongPress', target: route.key })
            }
            style={({ pressed }) => ({
              width: '50%',
              minHeight: 48,
              paddingHorizontal: 8,
              paddingVertical: 8,
              alignItems: 'center',
              justifyContent: 'center',
              gap: 4,
              opacity: pressed ? 0.65 : 1,
            })}
          >
            {options.tabBarIcon?.({ focused, color, size: 24 })}
            <Text style={{ color, fontSize: 12, fontWeight: '700' }}>
              {route.name}
            </Text>
          </Pressable>
        );
      })}
    </View>
  );
}
