interface BluetoothDevice extends EventTarget {
  readonly id: string;
  readonly name?: string;
  readonly gatt?: BluetoothRemoteGATTServer;
  addEventListener(type: 'gattserverdisconnected', listener: EventListener): void;
}

interface BluetoothRemoteGATTServer {
  readonly connected: boolean;
  readonly device: BluetoothDevice;
  connect(): Promise<BluetoothRemoteGATTServer>;
  disconnect(): void;
  getPrimaryService(service: string): Promise<BluetoothRemoteGATTService>;
}

interface BluetoothRemoteGATTService {
  getCharacteristic(characteristic: string): Promise<BluetoothRemoteGATTCharacteristic>;
}

interface BluetoothRemoteGATTCharacteristic extends EventTarget {
  readonly value?: DataView;
  startNotifications(): Promise<BluetoothRemoteGATTCharacteristic>;
  addEventListener(
    type: 'characteristicvaluechanged',
    listener: (event: Event) => void
  ): void;
}

interface BluetoothRequestDeviceFilter {
  services?: string[];
}

interface RequestDeviceOptions {
  filters?: BluetoothRequestDeviceFilter[];
  optionalServices?: string[];
}

interface Bluetooth extends EventTarget {
  requestDevice(options?: RequestDeviceOptions): Promise<BluetoothDevice>;
}

interface Navigator {
  bluetooth?: Bluetooth;
}
