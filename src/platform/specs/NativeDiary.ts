import type { TurboModule } from 'react-native';
import { TurboModuleRegistry } from 'react-native';

export interface Spec extends TurboModule {
  getSnapshot(days: number): Promise<string>;
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
}
export default TurboModuleRegistry.get<Spec>('NativeDiary');
