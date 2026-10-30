import type { TurboModule } from 'react-native';
import { TurboModuleRegistry } from 'react-native';

export interface Spec extends TurboModule {
  getSnapshot(): Promise<string>;
  configure(
    app: string,
    enabled: boolean,
    noticeRevision: number,
    expectedRevision: string,
  ): Promise<string>;
  configureAll(
    enabled: boolean,
    noticeRevision: number,
    expectedRevision: string,
  ): Promise<string>;
  setPaused(paused: boolean, expectedRevision: string): Promise<string>;
  setOverlay(enabled: boolean, expectedRevision: string): Promise<string>;
  setCounterAppearance(
    showIcons: boolean,
    allAppsToday: boolean,
    expectedRevision: string,
  ): Promise<string>;
  deleteHistory(expectedRevision: string): Promise<string>;
  openAccessibilitySettings(): Promise<boolean>;
  openTarget(app: string): Promise<boolean>;
  setCalibration(enabled: boolean): Promise<boolean>;
  getAutoScrollSnapshot(): Promise<string>;
  configureAutoScroll(
    enabled: boolean,
    noticeRevision: number,
  ): Promise<string>;
  stopAutoScroll(): Promise<string>;
}

export default TurboModuleRegistry.get<Spec>('NativeReels');
