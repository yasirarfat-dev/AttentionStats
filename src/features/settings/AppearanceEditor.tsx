import { useEffect, useState } from 'react';
import { Text, TextInput, View } from 'react-native';
import { StyleSheet, useUnistyles } from 'react-native-unistyles';
import {
  Body,
  Button,
  SectionTitle,
  Segmented,
  Stepper,
  ToggleRow,
} from '../../ui/Kit';
import { appearancePreview, type Appearance } from '../../ui/theme';

export function AppearanceEditor({
  value,
  onSave,
}: {
  value: Appearance;
  onSave: (value: Appearance) => Promise<unknown>;
}) {
  const { rt } = useUnistyles();
  const [draft, setDraft] = useState(value);
  const [hex, setHex] = useState(value.customColor || '#5676A6');
  const [advanced, setAdvanced] = useState(false);
  const [busy, setBusy] = useState(false);
  useEffect(() => {
    setDraft(value);
    setHex(value.customColor || '#5676A6');
  }, [value]);
  const valid = /^#[0-9a-f]{6}$/i.test(hex);
  const dark =
    draft.mode === 'dark' ||
    (draft.mode === 'system' && rt.themeName === 'dark');
  const preview = appearancePreview(draft, dark);
  const updateChannel = (index: number, next: number) => {
    const base = valid ? hex : '#5676A6';
    const color =
      base.slice(0, 1 + index * 2) +
      next.toString(16).padStart(2, '0') +
      base.slice(3 + index * 2);
    setHex(color);
    setDraft({ ...draft, customColor: color });
  };
  return (
    <>
      <Segmented
        label="Theme"
        value={draft.mode}
        options={[
          { value: 'light', label: 'Light' },
          { value: 'dark', label: 'Dark' },
          { value: 'system', label: 'System' },
        ]}
        onChange={mode => setDraft({ ...draft, mode })}
      />
      <Segmented
        label="Accent starting point"
        value={draft.accent}
        options={[
          { value: 'graphite', label: 'Graphite' },
          { value: 'blue', label: 'Blue' },
          { value: 'teal', label: 'Teal' },
          { value: 'violet', label: 'Violet' },
        ]}
        onChange={accent => setDraft({ ...draft, accent, customColor: '' })}
      />
      <ToggleRow
        title="Mix a custom accent"
        value={draft.customColor !== ''}
        onChange={enabled =>
          setDraft({
            ...draft,
            customColor: enabled ? (valid ? hex : '#5676A6') : '',
          })
        }
      />
      <View
        style={[
          styles.preview,
          {
            backgroundColor: preview.accentSoft,
            borderRadius: draft.corners,
            padding: draft.spacing,
          },
        ]}
        accessible
        accessibilityLabel="Appearance preview. Video encounters 965. Recorded surface time 6 hours 51 minutes."
      >
        <Text
          style={{ color: preview.accent, fontWeight: '600', fontSize: 14 }}
        >
          Video encounters
        </Text>
        <Text
          style={{
            color: preview.accent,
            fontWeight: '700',
            fontSize: draft.numberSize,
            fontVariant: ['tabular-nums'],
          }}
        >
          965
        </Text>
        <Text style={{ color: preview.accent, fontSize: 14 }}>
          Recorded surface time · 6h 51m
        </Text>
      </View>
      {draft.customColor !== '' && (
        <>
          <SectionTitle>Your accent color</SectionTitle>
          <TextInput
            accessibilityLabel="Custom accent hex color"
            value={hex}
            autoCorrect={false}
            autoCapitalize="characters"
            maxLength={7}
            style={styles.input}
            onChangeText={text => {
              setHex(text);
              if (/^#[0-9a-f]{6}$/i.test(text))
                setDraft({ ...draft, customColor: text });
            }}
          />
          {!valid && <Body>Enter a six-digit color, for example #5676A6.</Body>}
          {['Red', 'Green', 'Blue'].map((label, i) => (
            <ColorChannel
              key={label}
              label={label}
              value={valid ? parseInt(hex.slice(1 + i * 2, 3 + i * 2), 16) : 0}
              color={['#B55D5D', '#44816A', '#5B78B3'][i]!}
              onChange={next => updateChannel(i, next)}
            />
          ))}
          <Body muted>
            Your chosen color adapts for readable text and buttons in each
            theme. Status and deletion colors keep their meaning.
          </Body>
        </>
      )}
      <Button
        label={advanced ? 'Hide layout controls' : 'Customize layout & numbers'}
        variant="text"
        onPress={() => setAdvanced(!advanced)}
      />
      {advanced && (
        <>
          <SectionTitle>Card corners</SectionTitle>
          <Stepper
            label="Card corners"
            value={draft.corners}
            min={0}
            max={32}
            step={2}
            suffix="dp"
            onChange={corners => setDraft({ ...draft, corners })}
          />
          <SectionTitle>Card spacing</SectionTitle>
          <Stepper
            label="Card spacing"
            value={draft.spacing}
            min={12}
            max={24}
            step={2}
            suffix="dp"
            onChange={spacing => setDraft({ ...draft, spacing })}
          />
          <SectionTitle>Statistic size</SectionTitle>
          <Stepper
            label="Statistic size"
            value={draft.numberSize}
            min={24}
            max={40}
            step={2}
            suffix="sp · follows system text size"
            onChange={numberSize => setDraft({ ...draft, numberSize })}
          />
          <Body muted>
            Layout controls apply to app cards and statistic numbers. Native
            tools and launcher widgets have their own layouts.
          </Body>
        </>
      )}
      <ToggleRow
        title="Gentle animations"
        value={draft.motion}
        onChange={motion => setDraft({ ...draft, motion })}
      />
      <Button
        label={busy ? 'Saving appearance…' : 'Save appearance'}
        disabled={busy || (draft.customColor !== '' && !valid)}
        onPress={() => {
          setBusy(true);
          void onSave(draft).finally(() => setBusy(false));
        }}
      />
      <Button
        label="Reset appearance draft"
        variant="text"
        disabled={busy}
        onPress={() => {
          setDraft({
            mode: 'system',
            accent: 'graphite',
            motion: true,
            customColor: '',
            corners: 24,
            spacing: 20,
            numberSize: 30,
          });
          setHex('#5676A6');
        }}
      />
    </>
  );
}

function ColorChannel({
  label,
  value,
  color,
  onChange,
}: {
  label: string;
  value: number;
  color: string;
  onChange: (value: number) => void;
}) {
  const [width, setWidth] = useState(1);
  return (
    <View style={styles.channel}>
      <Text style={styles.channelLabel}>
        {label} · {value}
      </Text>
      <View
        accessibilityRole="adjustable"
        accessibilityLabel={label}
        accessibilityValue={{ min: 0, max: 255, now: value }}
        accessibilityActions={[{ name: 'increment' }, { name: 'decrement' }]}
        onAccessibilityAction={event =>
          onChange(
            Math.min(
              255,
              Math.max(
                0,
                value + (event.nativeEvent.actionName === 'increment' ? 1 : -1),
              ),
            ),
          )
        }
        onLayout={event => setWidth(event.nativeEvent.layout.width)}
        onStartShouldSetResponder={() => true}
        onResponderGrant={event =>
          onChange(
            Math.round(
              Math.max(0, Math.min(1, event.nativeEvent.locationX / width)) *
                255,
            ),
          )
        }
        onResponderMove={event =>
          onChange(
            Math.round(
              Math.max(0, Math.min(1, event.nativeEvent.locationX / width)) *
                255,
            ),
          )
        }
        style={styles.channelTouch}
      >
        <View style={[styles.track, { backgroundColor: color }]} />
        <View
          style={[
            styles.thumb,
            { left: `${(value / 255) * 90}%`, borderColor: color },
          ]}
        />
      </View>
    </View>
  );
}
const styles = StyleSheet.create(theme => ({
  preview: { gap: 6, minHeight: 125 },
  input: {
    color: theme.text,
    backgroundColor: theme.surfaceAlt,
    borderRadius: 12,
    padding: 14,
    fontSize: 18,
    minHeight: 52,
    borderWidth: 1,
    borderColor: theme.border,
  },
  channel: { gap: 4 },
  channelLabel: { color: theme.muted, fontSize: 14 },
  channelTouch: {
    minHeight: 48,
    justifyContent: 'center',
    paddingHorizontal: 12,
  },
  track: { height: 4, borderRadius: 2 },
  thumb: {
    position: 'absolute',
    width: 24,
    height: 24,
    borderRadius: 12,
    borderWidth: 3,
    backgroundColor: theme.surface,
  },
}));
