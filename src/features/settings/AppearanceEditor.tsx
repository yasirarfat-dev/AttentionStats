import { useEffect, useState } from 'react';
import {
  Pressable,
  Text,
  TextInput,
  View,
  useWindowDimensions,
} from 'react-native';
import { StyleSheet, useUnistyles } from 'react-native-unistyles';
import {
  Body,
  Button,
  SectionTitle,
  Explanation,
  Divider,
  Segmented,
  Stepper,
  ToggleRow,
} from '../../ui/Kit';
import { appearancePreview, type Appearance } from '../../ui/theme';
import { ColorCanvas } from './ColorCanvas';

export function AppearanceEditor({
  value,
  onSave,
  onDragChange,
}: {
  value: Appearance;
  onSave: (value: Appearance) => Promise<unknown>;
  onDragChange: (dragging: boolean) => void;
}) {
  const { rt } = useUnistyles();
  const [draft, setDraft] = useState(value);
  const [hex, setHex] = useState(value.customColor || '#5676A6');
  const [scope, setScope] = useState<
    'foregroundColor' | 'backgroundColor' | 'customColor'
  >('customColor');
  const [advanced, setAdvanced] = useState(false);
  const [precision, setPrecision] = useState(false);
  const [busy, setBusy] = useState(false);
  useEffect(() => {
    setDraft(value);
    setHex(value[scope] || '#5676A6');
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
    setDraft({ ...draft, [scope]: color });
  };
  return (
    <>
      <Explanation title="Theme">
        Choose Light, Dark or follow the system. Appearance changes stay in this
        draft until Save Appearance.
      </Explanation>
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
      <View style={styles.colorGroup}>
        <Explanation title="Presets">
          Ready-made palettes. Choosing one resets the custom colors in this
          draft. Save Appearance applies your changes.
        </Explanation>
        <AppearanceChoices
          grid
          label="Presets"
          value={draft.accent}
          options={[
            { value: 'graphite', label: 'Graphite' },
            { value: 'blue', label: 'Blue' },
            { value: 'teal', label: 'Teal' },
            { value: 'violet', label: 'Violet' },
          ]}
          onChange={accent => {
            const next = {
              ...draft,
              accent,
              customColor: '',
              foregroundColor: '',
              backgroundColor: '',
            };
            setDraft(next);
            const colors = appearancePreview(next, dark);
            setHex(
              scope === 'backgroundColor'
                ? colors.canvas
                : scope === 'foregroundColor'
                  ? colors.text
                  : colors.accent,
            );
          }}
        />
      </View>
      <View style={styles.colorGroup}>
        <Explanation title="Custom Colors">
          Color changes text and numbers; Background changes page and card
          backgrounds; Accent changes buttons and highlights. Choose a scope,
          drag the hue strip and color canvas, or enter hex/RGB. Your selected
          background is preserved; text and accent tones adjust for readability.
          Status and deletion colors retain their meaning. Changes stay in the
          draft until Save Appearance.
        </Explanation>
        <AppearanceChoices
          label="Color scope"
          value={scope}
          options={[
            { value: 'foregroundColor', label: 'Color' },
            { value: 'backgroundColor', label: 'Background' },
            { value: 'customColor', label: 'Accent' },
          ]}
          onChange={next => {
            setScope(next);
            setHex(
              draft[next] ||
                (next === 'backgroundColor'
                  ? preview.canvas
                  : next === 'foregroundColor'
                    ? preview.text
                    : preview.accent),
            );
          }}
        />
        <ColorCanvas
          onDragChange={onDragChange}
          key={scope}
          color={valid ? hex : '#5676A6'}
          onChange={color => {
            setHex(color);
            setDraft({ ...draft, [scope]: color });
          }}
        />
        <Button
          label="Use preset for this color"
          variant="text"
          onPress={() => {
            setDraft({ ...draft, [scope]: '' });
            setHex('#5676A6');
          }}
        />
        <View
          style={[
            styles.preview,
            {
              backgroundColor: preview.surface,
              borderWidth: 1,
              borderColor: preview.border,
              borderRadius: draft.corners,
              padding: draft.spacing,
            },
          ]}
          accessible
          accessibilityLabel="Appearance preview. Video encounters 965. Recorded surface time 6 hours 51 minutes."
        >
          <Text
            style={{ color: preview.text, fontWeight: '600', fontSize: 14 }}
          >
            Video encounters
          </Text>
          <Text
            style={{
              color: preview.text,
              fontWeight: '700',
              fontSize: draft.numberSize,
              fontVariant: ['tabular-nums'],
            }}
          >
            965
          </Text>
          <Text style={{ color: preview.muted, fontSize: 14 }}>
            Recorded surface time · 6h 51m
          </Text>
          <Text
            style={{ color: preview.accent, fontSize: 16, fontWeight: '600' }}
          >
            Accent preview · View details
          </Text>
        </View>
        {
          <>
            <SectionTitle>Selected color</SectionTitle>
            <TextInput
              accessibilityLabel="Selected hex color"
              value={hex}
              autoCorrect={false}
              autoCapitalize="characters"
              maxLength={7}
              style={styles.input}
              onChangeText={text => {
                setHex(text);
                if (/^#[0-9a-f]{6}$/i.test(text))
                  setDraft({ ...draft, [scope]: text });
              }}
            />
            {!valid && (
              <Body>Enter a six-digit color, for example #5676A6.</Body>
            )}
            <Button
              label={precision ? 'Hide RGB controls' : 'Fine-tune RGB'}
              variant="text"
              onPress={() => setPrecision(!precision)}
            />
            {precision &&
              ['Red', 'Green', 'Blue'].map((label, i) => (
                <ColorChannel
                  key={label}
                  label={label}
                  value={
                    valid ? parseInt(hex.slice(1 + i * 2, 3 + i * 2), 16) : 0
                  }
                  color={['#B55D5D', '#44816A', '#5B78B3'][i]!}
                  onChange={next => updateChannel(i, next)}
                />
              ))}
          </>
        }
      </View>
      <Button
        label={advanced ? 'Hide layout controls' : 'Customize layout & numbers'}
        variant="text"
        onPress={() => setAdvanced(!advanced)}
      />
      {advanced && (
        <>
          <Divider />
          <Explanation title="Layout & Numbers">
            Adjust app-card corners, spacing and statistic size. System text
            scaling still applies. Native tools and launcher widgets retain
            their own layout controls.
          </Explanation>
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
        </>
      )}
      <ToggleRow
        title="Gentle animations"
        value={draft.motion}
        onChange={motion => setDraft({ ...draft, motion })}
      />
      <Button
        label={busy ? 'Saving appearance…' : 'Save appearance'}
        disabled={busy || !valid}
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
            foregroundColor: '',
            backgroundColor: '',
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

function AppearanceChoices<T extends string>({
  label,
  value,
  options,
  onChange,
  grid = false,
}: {
  label: string;
  value: T;
  options: { value: T; label: string }[];
  onChange: (value: T) => void;
  grid?: boolean;
}) {
  const { fontScale } = useWindowDimensions();
  const large = fontScale > 1.3;
  return (
    <View
      accessibilityRole="radiogroup"
      accessibilityLabel={label}
      style={styles.choices}
    >
      {options.map(option => (
        <Pressable
          key={option.value}
          accessibilityRole="radio"
          accessibilityLabel={option.label}
          accessibilityState={{ selected: option.value === value }}
          onPress={() => onChange(option.value)}
          style={[
            styles.choice,
            large
              ? { flexBasis: '100%' }
              : grid
                ? { flexBasis: '46%', flexGrow: 1 }
                : { flex: option.value === 'backgroundColor' ? 1.6 : 1 },
            option.value === value && styles.choiceSelected,
          ]}
        >
          <Text
            numberOfLines={1}
            adjustsFontSizeToFit={!large}
            minimumFontScale={0.9}
            style={styles.choiceLabel}
          >
            {option.label}
          </Text>
        </Pressable>
      ))}
    </View>
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
        onResponderTerminationRequest={() => false}
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
        <View
          pointerEvents="none"
          style={[styles.track, { backgroundColor: color }]}
        />
        <View
          pointerEvents="none"
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
  choices: { flexDirection: 'row', flexWrap: 'wrap', gap: 6 },
  choice: {
    minWidth: 0,
    minHeight: 48,
    paddingHorizontal: 4,
    paddingVertical: 12,
    borderWidth: 1,
    borderColor: theme.border,
    borderRadius: 12,
    alignItems: 'center',
    justifyContent: 'center',
  },
  choiceSelected: {
    backgroundColor: theme.accentSoft,
    borderColor: theme.accent,
  },
  choiceLabel: {
    color: theme.text,
    fontSize: 14,
    fontWeight: '600',
    textAlign: 'center',
    flexShrink: 1,
  },
  colorGroup: {
    gap: 12,
    padding: 12,
    borderRadius: 16,
    borderWidth: 1,
    borderColor: theme.border,
    backgroundColor: theme.surfaceAlt,
  },
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
