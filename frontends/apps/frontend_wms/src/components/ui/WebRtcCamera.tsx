/**
 * Back-compat shim — use {@link CameraCapture} for new call sites.
 */
export { CameraCapture as WebRtcCamera, CameraCapture } from '@/components/ui/CameraCapture';
export type { CameraCaptureProps, CameraFacing } from '@/components/ui/CameraCapture';
