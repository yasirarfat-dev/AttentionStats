import { useState } from 'react';
import { Pressable, Switch, Text, TextInput, View } from 'react-native';
import { StyleSheet, useUnistyles } from 'react-native-unistyles';
import { Body, Card, Divider, SectionTitle, CheckRow } from '../../ui/Kit';
import { Icon } from '../../ui/Icon';
import { AppIcon } from '../../ui/Charts';
import { useAppIcons } from '../diary';
import type { AppGroup, ProtectableApp } from '../../platform/protectionStatus';
import { scheduleStyles } from './schedulePresentation';

/** One app list: groups expand in place, and shared membership reflects one selection set. */
export function AppSelection({
  groups,
  apps,
  selected,
  onChange,
  disabled = false,
}: {
  groups: AppGroup[];
  apps: ProtectableApp[];
  selected: string[];
  onChange: (packages: string[]) => void;
  disabled?: boolean;
}) {
  const { theme } = useUnistyles();
  const [query, setQuery] = useState('');
  const [expanded, setExpanded] = useState<string[]>([]);
  const matching = (name: string) =>
    name.toLocaleLowerCase().includes(query.trim().toLocaleLowerCase());
  const nameOf = (pkg: string) =>
    apps.find(app => app.packageName === pkg)?.name ?? pkg;
  const others = apps
    .filter(app => matching(app.name))
    .sort((a, b) => a.name.localeCompare(b.name));
  const visibleGroups = groups.filter(
    group =>
      matching(group.name) || group.packages.some(pkg => matching(nameOf(pkg))),
  );
  const icons = useAppIcons([
    ...others.map(app => app.packageName),
    ...visibleGroups.flatMap(group => group.packages),
  ]);
  const appIcon = (pkg: string) => (
    <AppIcon
      uri={icons[pkg]}
      mark={nameOf(pkg).slice(0, 1)}
      color={theme.accentFill}
      size={36}
    />
  );
  const choose = (packages: string[], enabled: boolean) => {
    const next = new Set(selected);
    packages.forEach(pkg => (enabled ? next.add(pkg) : next.delete(pkg)));
    if (next.size <= 100) onChange([...next]);
  };
  return (
    <>
      <SectionTitle>All Apps</SectionTitle>
      <TextInput
        accessibilityLabel="Search Focus Apps"
        placeholder="Search Apps or Groups"
        placeholderTextColor={theme.muted}
        value={query}
        onChangeText={value => {
          setQuery(value);
        }}
        style={scheduleStyles.input}
        editable={!disabled}
      />
      <Body muted>{selected.length} selected</Body>
      <Card
        title="Apps & Groups"
        info="Group and individual choices stay in sync. Expand a group to inspect its members; its switch selects all members. Apps remain alphabetical and may appear in more than one group. Up to 100 apps can be selected."
        padded={false}
      >
        {visibleGroups.map((group, index) => {
          const selectedCount = group.packages.filter(pkg =>
            selected.includes(pkg),
          ).length;
          const checked = selectedCount === group.packages.length;
          const open =
            expanded.includes(group.id) ||
            (query.trim().length > 0 &&
              group.packages.some(pkg => matching(nameOf(pkg))));
          const atLimit =
            !checked && new Set([...selected, ...group.packages]).size > 100;
          return (
            <View key={group.id}>
              {index > 0 && <Divider />}
              <View style={styles.group}>
                <Pressable
                  accessibilityRole="button"
                  accessibilityLabel={`${open ? 'Collapse' : 'Expand'} ${group.name}`}
                  accessibilityState={{ expanded: open }}
                  style={styles.groupHeading}
                  onPress={() =>
                    setExpanded(
                      expanded.includes(group.id)
                        ? expanded.filter(id => id !== group.id)
                        : [...expanded, group.id],
                    )
                  }
                >
                  <View
                    style={{ transform: [{ rotate: open ? '90deg' : '0deg' }] }}
                  >
                    <Icon name="chevron" color={theme.muted} size={18} />
                  </View>
                  <View style={styles.groupText}>
                    <Text style={styles.groupName}>{group.name}</Text>
                    <Text style={styles.groupCount}>
                      {selectedCount}/{group.packages.length} Selected
                      {atLimit ? ' · Selection Limit' : ''}
                    </Text>
                  </View>
                </Pressable>
                <Switch
                  accessibilityLabel={`Select All in ${group.name}`}
                  accessibilityHint={`${selectedCount} of ${group.packages.length} selected`}
                  value={checked}
                  disabled={disabled || atLimit}
                  onValueChange={enabled => choose(group.packages, enabled)}
                  trackColor={{ true: theme.accentFill, false: theme.track }}
                  thumbColor={checked ? theme.onAccent : theme.surface}
                />
              </View>
              {open && (
                <View style={styles.members}>
                  {group.packages
                    .filter(
                      pkg =>
                        query.trim().length === 0 ||
                        matching(group.name) ||
                        matching(nameOf(pkg)),
                    )
                    .sort((a, b) => nameOf(a).localeCompare(nameOf(b)))
                    .map(pkg => (
                      <CheckRow
                        key={pkg}
                        title={nameOf(pkg)}
                        leading={appIcon(pkg)}
                        value={selected.includes(pkg)}
                        disabled={
                          disabled ||
                          (!selected.includes(pkg) && selected.length >= 100)
                        }
                        onChange={enabled => choose([pkg], enabled)}
                      />
                    ))}
                </View>
              )}
            </View>
          );
        })}
        {others.map((app, index) => (
          <View key={app.packageName}>
            {(index > 0 || visibleGroups.length > 0) && <Divider />}
            <CheckRow
              title={app.name}
              leading={appIcon(app.packageName)}
              value={selected.includes(app.packageName)}
              disabled={
                disabled ||
                (!selected.includes(app.packageName) && selected.length >= 100)
              }
              onChange={enabled => choose([app.packageName], enabled)}
            />
          </View>
        ))}
        {others.length === 0 && visibleGroups.length === 0 && (
          <View style={styles.empty}>
            <Body muted>No Matching Apps or Groups</Body>
          </View>
        )}
      </Card>
    </>
  );
}
const styles = StyleSheet.create(theme => ({
  group: {
    flexDirection: 'row',
    alignItems: 'center',
    minHeight: 68,
    paddingRight: 12,
  },
  groupHeading: {
    flex: 1,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 10,
    padding: 12,
    minHeight: 64,
  },
  groupText: { flex: 1, gap: 3 },
  groupName: { color: theme.text, fontSize: 16, fontWeight: '700' },
  groupCount: { color: theme.muted, fontSize: 12 },
  members: {
    paddingLeft: 28,
    backgroundColor: theme.surfaceAlt,
    borderRadius: 12,
    margin: 4,
  },
  empty: { padding: 12 },
}));
