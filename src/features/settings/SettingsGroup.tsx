import { useState, type ReactNode } from 'react';
import { Text, View } from 'react-native';
import { StyleSheet } from 'react-native-unistyles';
import {
  Body,
  Button,
  Card,
  Divider,
  InfoButton,
  SectionTitle,
} from '../../ui/Kit';

export type SettingExplanation = { term: string; detail: string };

/** Definitions open beside their heading so the result is visible without searching below a long card. */
export function SettingsGroup({
  title,
  entries,
  children,
  padded = false,
}: {
  title: string;
  entries: SettingExplanation[];
  children: ReactNode;
  padded?: boolean;
}) {
  const [expanded, setExpanded] = useState(false);
  return (
    <>
      <SectionTitle
        action={
          <InfoButton
            label={title}
            expanded={expanded}
            onPress={() => setExpanded(!expanded)}
          />
        }
      >
        {title}
      </SectionTitle>
      {expanded && (
        <Card tone="soft">
          {entries.map((entry, index) => (
            <View key={entry.term} style={styles.entry}>
              {index > 0 && <Divider />}
              <Text accessibilityRole="header" style={styles.term}>
                {entry.term}
              </Text>
              <Body muted>{entry.detail}</Body>
            </View>
          ))}
          <Button
            label="Hide Explanations"
            variant="text"
            onPress={() => setExpanded(false)}
          />
        </Card>
      )}
      <Card padded={padded}>{children}</Card>
    </>
  );
}
const styles = StyleSheet.create(theme => ({
  entry: { gap: 6 },
  term: { color: theme.text, fontSize: 15, fontWeight: '700' },
}));
