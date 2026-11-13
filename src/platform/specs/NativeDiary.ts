import type { TurboModule } from 'react-native';
import { TurboModuleRegistry } from 'react-native';

export interface Spec extends TurboModule {
  getSnapshot(days: number): Promise<string>;
  getHistoryPage(endDay: string): Promise<string>;
  configure(
    action: string,
    enabled: boolean,
    count: number,
    minutes: number,
    expectedRevision: string,
  ): Promise<string>;
  openUsageSettings(): Promise<boolean>;
  openAppSettings(): Promise<boolean>;
  getAppIcons(packages: string[]): Promise<string>;
  openBatterySettings(): Promise<boolean>;
  pinWidget(): Promise<boolean>;
  openAppSchedules(): Promise<boolean>;
  openAppSchedule(packageName: string): Promise<boolean>;
  getAppSchedules(): Promise<string>;
  saveAppSchedules(document: string, expectedRevision: string): Promise<string>;
  startFocus(
    packages: string[],
    minutes: number,
    expectedRevision: string,
  ): Promise<string>;
  stopFocus(sessionId: string, expectedRevision: string): Promise<string>;
  getProtectableApps(): Promise<string>;
  openBackup(): Promise<boolean>;
  getAppearance(): Promise<string>;
  setAppearance(mode: string, accent: string, motion: boolean): Promise<string>;
}
export default TurboModuleRegistry.get<Spec>('NativeDiary');
