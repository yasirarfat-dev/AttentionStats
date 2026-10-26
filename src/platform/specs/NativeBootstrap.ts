import type { TurboModule } from 'react-native';
import { TurboModuleRegistry } from 'react-native';

export type BootstrapStatus = {
  contractVersion: number;
  sourceState: string;
};

export interface Spec extends TurboModule {
  getBootstrapStatus(): Promise<BootstrapStatus>;
}

export default TurboModuleRegistry.get<Spec>('NativeBootstrap');
