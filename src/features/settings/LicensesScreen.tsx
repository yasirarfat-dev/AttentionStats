import { useState } from 'react';
import { Alert, Linking, Modal, Text } from 'react-native';
import { StyleSheet } from 'react-native-unistyles';
import { Card, Divider, Row, SectionTitle } from '../../ui/Kit';
import { Screen } from '../../ui/Screen';
import components from './licenses.json';
import texts from './licenseTexts.json';

export function LicensesScreen({ onBack }: { onBack: () => void }) {
  const [document, setDocument] = useState<string | null>(null);
  const groups = [
    ...new Set(components.map(component => component.license)),
  ].sort();
  return (
    <>
      <Screen
        title="Open-source licenses"
        back={{ label: 'Back to Settings', onPress: onBack }}
      >
        <Card padded={false}>
          <Row
            title="Third-party notices"
            icon="info"
            onPress={() => setDocument('NOTICE')}
          />
          <Divider />
          <Row
            title="Public Suffix List source"
            icon="code"
            onPress={() => setDocument('Public Suffix List source')}
          />
        </Card>
        {groups.map(license => (
          <Card key={license} padded={false}>
            <SectionTitle>{license}</SectionTitle>
            <Row
              title="Read license"
              icon="code"
              accessibilityLabel={`Read ${license} license`}
              onPress={() => setDocument(license)}
            />
            {components
              .filter(component => component.license === license)
              .map(component => (
                <CardRow
                  key={`${component.name}@${component.version}`}
                  component={component}
                />
              ))}
          </Card>
        ))}
      </Screen>
      <Modal
        visible={document !== null}
        onRequestClose={() => setDocument(null)}
        animationType="none"
      >
        <Screen
          key={document}
          title={
            document === 'NOTICE' ? 'Third-party notices' : (document ?? '')
          }
          back={{ label: 'Back to licenses', onPress: () => setDocument(null) }}
        >
          <Text textBreakStrategy="balanced" selectable style={styles.text}>
            {document ? (texts as Record<string, string>)[document] : ''}
          </Text>
        </Screen>
      </Modal>
    </>
  );
}

function CardRow({ component }: { component: (typeof components)[number] }) {
  return (
    <>
      <Divider />
      <Row
        title={component.name}
        detail={`${component.version} · ${component.license}`}
        accessibilityLabel={`${component.name}, ${component.version}, ${component.license}. Open project website`}
        onPress={() => {
          Linking.openURL(component.url).catch(() =>
            Alert.alert('Could not open website', 'Please try again.'),
          );
        }}
      />
    </>
  );
}

const styles = StyleSheet.create(theme => ({
  text: { color: theme.text, fontSize: 14, lineHeight: 21 },
}));
