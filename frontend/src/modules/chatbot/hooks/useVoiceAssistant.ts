import { useCallback, useEffect, useRef, useState } from 'react';

type SpeechRecognitionLike = {
  continuous: boolean;
  interimResults: boolean;
  lang: string;
  start: () => void;
  stop: () => void;
  abort: () => void;
  onresult: ((ev: { results: ArrayLike<{ 0: { transcript: string }; isFinal: boolean }> }) => void) | null;
  onerror: ((ev: { error?: string }) => void) | null;
  onend: (() => void) | null;
};

type SpeechRecognitionCtor = new () => SpeechRecognitionLike;

function getSpeechRecognitionCtor(): SpeechRecognitionCtor | null {
  if (typeof window === 'undefined') return null;
  const w = window as Window & {
    SpeechRecognition?: SpeechRecognitionCtor;
    webkitSpeechRecognition?: SpeechRecognitionCtor;
  };
  return w.SpeechRecognition ?? w.webkitSpeechRecognition ?? null;
}

export type UseVoiceAssistantOptions = {
  onTranscript: (text: string) => void;
  onFinalTranscript?: (text: string) => void;
  lang?: string;
};

/**
 * Hands-free PTT (Web Speech API) + TTS for rugged scanner support flows.
 */
export function useVoiceAssistant(options: UseVoiceAssistantOptions) {
  const { onTranscript, onFinalTranscript, lang = 'en-US' } = options;
  const [listening, setListening] = useState(false);
  const [supported, setSupported] = useState(false);
  const recognitionRef = useRef<SpeechRecognitionLike | null>(null);
  const pressTimerRef = useRef<number | null>(null);

  useEffect(() => {
    setSupported(!!getSpeechRecognitionCtor() && typeof window !== 'undefined' && 'speechSynthesis' in window);
  }, []);

  const stopListening = useCallback(() => {
    const rec = recognitionRef.current;
    if (rec) {
      try {
        rec.stop();
      } catch {
        // already stopped
      }
    }
    setListening(false);
  }, []);

  const startListening = useCallback(() => {
    const Ctor = getSpeechRecognitionCtor();
    if (!Ctor) return;
    try {
      recognitionRef.current?.abort();
    } catch {
      // ignore
    }
    const rec = new Ctor();
    rec.continuous = false;
    rec.interimResults = true;
    rec.lang = lang;
    rec.onresult = (ev) => {
      let interim = '';
      let finalText = '';
      for (let i = 0; i < ev.results.length; i++) {
        const piece = ev.results[i]?.[0]?.transcript ?? '';
        if (ev.results[i]?.isFinal) finalText += piece;
        else interim += piece;
      }
      const text = (finalText || interim).trim();
      if (text) onTranscript(text);
      if (finalText.trim()) onFinalTranscript?.(finalText.trim());
    };
    rec.onerror = () => setListening(false);
    rec.onend = () => setListening(false);
    recognitionRef.current = rec;
    rec.start();
    setListening(true);
  }, [lang, onFinalTranscript, onTranscript]);

  const toggleListening = useCallback(() => {
    if (listening) stopListening();
    else startListening();
  }, [listening, startListening, stopListening]);

  const speak = useCallback((text: string) => {
    if (typeof window === 'undefined' || !('speechSynthesis' in window) || !text.trim()) return;
    try {
      window.speechSynthesis.cancel();
      const utter = new SpeechSynthesisUtterance(text.replace(/\*\*/g, '').slice(0, 600));
      utter.lang = lang;
      window.speechSynthesis.speak(utter);
    } catch {
      // TTS optional on locked-down scanners
    }
  }, [lang]);

  /** Long-press / hardware side-button PTT helpers. */
  const bindPushToTalk = useCallback(
    (el: HTMLElement | null) => {
      if (!el) return () => undefined;
      const onDown = (e: Event) => {
        e.preventDefault();
        if (pressTimerRef.current != null) window.clearTimeout(pressTimerRef.current);
        pressTimerRef.current = window.setTimeout(() => startListening(), 280);
      };
      const onUp = () => {
        if (pressTimerRef.current != null) {
          window.clearTimeout(pressTimerRef.current);
          pressTimerRef.current = null;
        }
        stopListening();
      };
      el.addEventListener('pointerdown', onDown);
      el.addEventListener('pointerup', onUp);
      el.addEventListener('pointerleave', onUp);
      el.addEventListener('pointercancel', onUp);
      return () => {
        el.removeEventListener('pointerdown', onDown);
        el.removeEventListener('pointerup', onUp);
        el.removeEventListener('pointerleave', onUp);
        el.removeEventListener('pointercancel', onUp);
      };
    },
    [startListening, stopListening],
  );

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      // Common rugged-scanner side-button mappings (F9 / Scan).
      if (e.key === 'F9' || e.code === 'F9') {
        e.preventDefault();
        if (e.type === 'keydown' && !listening) startListening();
        if (e.type === 'keyup' && listening) stopListening();
      }
    };
    window.addEventListener('keydown', onKey);
    window.addEventListener('keyup', onKey);
    return () => {
      window.removeEventListener('keydown', onKey);
      window.removeEventListener('keyup', onKey);
      stopListening();
      if (pressTimerRef.current != null) window.clearTimeout(pressTimerRef.current);
    };
  }, [listening, startListening, stopListening]);

  return {
    supported,
    listening,
    startListening,
    stopListening,
    toggleListening,
    speak,
    bindPushToTalk,
  };
}
