import { act, renderHook } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { useVoiceAssistant } from './useVoiceAssistant';

class FakeRecognition {
  continuous = false;
  interimResults = false;
  lang = 'en-US';
  onresult: ((ev: unknown) => void) | null = null;
  onerror: ((ev: unknown) => void) | null = null;
  onend: (() => void) | null = null;
  start = vi.fn(() => {
    this.onresult?.({
      results: [{ 0: { transcript: 'scan aisle four' }, isFinal: true }],
    });
    this.onend?.();
  });
  stop = vi.fn();
  abort = vi.fn();
}

describe('useVoiceAssistant', () => {
  beforeEach(() => {
    (window as unknown as { SpeechRecognition: typeof FakeRecognition }).SpeechRecognition =
      FakeRecognition;
    Object.defineProperty(window, 'speechSynthesis', {
      configurable: true,
      value: { cancel: vi.fn(), speak: vi.fn() },
    });
  });

  afterEach(() => {
    delete (window as unknown as { SpeechRecognition?: unknown }).SpeechRecognition;
  });

  it('starts listening and forwards final transcripts', async () => {
    const onTranscript = vi.fn();
    const onFinalTranscript = vi.fn();
    const { result } = renderHook(() =>
      useVoiceAssistant({ onTranscript, onFinalTranscript }),
    );

    expect(result.current.supported).toBe(true);

    await act(async () => {
      result.current.startListening();
    });

    expect(onTranscript).toHaveBeenCalledWith('scan aisle four');
    expect(onFinalTranscript).toHaveBeenCalledWith('scan aisle four');
  });

  it('speaks via speechSynthesis', () => {
    const speakSpy = vi.fn();
    const cancelSpy = vi.fn();
    Object.defineProperty(window, 'speechSynthesis', {
      configurable: true,
      value: { cancel: cancelSpy, speak: speakSpy },
    });
    // SpeechSynthesisUtterance is required by speak()
    (window as unknown as { SpeechSynthesisUtterance: typeof SpeechSynthesisUtterance })
      .SpeechSynthesisUtterance = function SpeechSynthesisUtterance(this: { text: string }, text?: string) {
        this.text = text ?? '';
      } as unknown as typeof SpeechSynthesisUtterance;

    const { result } = renderHook(() =>
      useVoiceAssistant({ onTranscript: () => undefined }),
    );
    act(() => {
      result.current.speak('**Operational Diagnosis:** ready');
    });
    expect(cancelSpy).toHaveBeenCalled();
    expect(speakSpy).toHaveBeenCalled();
  });
});
