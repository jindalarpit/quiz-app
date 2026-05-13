'use client';

import { cn } from '@/lib/utils';

interface EnableAudioPromptProps {
  /** Whether the prompt should be visible */
  visible: boolean;
  /** Callback when user clicks to enable audio */
  onEnable: () => void;
  /** Additional CSS classes */
  className?: string;
}

/**
 * Prompt displayed when browser autoplay policy blocks audio.
 * Shows a non-blocking banner that the user can click to unlock audio.
 * Does not block quiz participation.
 */
export function EnableAudioPrompt({ visible, onEnable, className }: EnableAudioPromptProps) {
  if (!visible) return null;

  return (
    <div
      className={cn(
        'fixed bottom-4 left-1/2 z-50 -translate-x-1/2',
        'flex items-center gap-3 rounded-lg px-4 py-3 shadow-lg',
        'bg-indigo-600 text-white',
        'animate-in fade-in slide-in-from-bottom-4 duration-300',
        className
      )}
      role="alert"
      aria-live="polite"
    >
      <SpeakerOffIcon />
      <span className="text-sm font-medium">Audio is blocked by your browser.</span>
      <button
        type="button"
        onClick={onEnable}
        className={cn(
          'rounded-md bg-white px-3 py-1.5 text-sm font-semibold text-indigo-600',
          'hover:bg-indigo-50 transition-colors',
          'focus:outline-none focus:ring-2 focus:ring-white focus:ring-offset-2 focus:ring-offset-indigo-600'
        )}
        aria-label="Enable audio playback"
      >
        Enable Audio
      </button>
    </div>
  );
}

function SpeakerOffIcon() {
  return (
    <svg
      xmlns="http://www.w3.org/2000/svg"
      width="18"
      height="18"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="2"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
    >
      <polygon points="11 5 6 9 2 9 2 15 6 15 11 19 11 5" />
      <line x1="23" y1="9" x2="17" y2="15" />
      <line x1="17" y1="9" x2="23" y2="15" />
    </svg>
  );
}
