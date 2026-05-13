'use client';

import { cn } from '@/lib/utils';

interface MuteToggleProps {
  /** Whether audio is currently muted */
  isMuted: boolean;
  /** Callback when the toggle is clicked */
  onToggle: () => void;
  /** Additional CSS classes */
  className?: string;
}

/**
 * Accessible mute/unmute toggle button with speaker icon.
 * Displays a speaker icon when unmuted and a muted speaker icon when muted.
 */
export function MuteToggle({ isMuted, onToggle, className }: MuteToggleProps) {
  return (
    <button
      type="button"
      onClick={onToggle}
      className={cn(
        'inline-flex items-center justify-center rounded-full p-2',
        'bg-slate-100 hover:bg-slate-200 dark:bg-slate-800 dark:hover:bg-slate-700',
        'text-slate-700 dark:text-slate-300',
        'transition-colors duration-150',
        'focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:ring-offset-2',
        'min-h-[44px] min-w-[44px]',
        className
      )}
      aria-label={isMuted ? 'Unmute audio' : 'Mute audio'}
      aria-pressed={isMuted}
      title={isMuted ? 'Unmute audio' : 'Mute audio'}
    >
      {isMuted ? <SpeakerMutedIcon /> : <SpeakerIcon />}
    </button>
  );
}

function SpeakerIcon() {
  return (
    <svg
      xmlns="http://www.w3.org/2000/svg"
      width="20"
      height="20"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="2"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
    >
      <polygon points="11 5 6 9 2 9 2 15 6 15 11 19 11 5" />
      <path d="M15.54 8.46a5 5 0 0 1 0 7.07" />
      <path d="M19.07 4.93a10 10 0 0 1 0 14.14" />
    </svg>
  );
}

function SpeakerMutedIcon() {
  return (
    <svg
      xmlns="http://www.w3.org/2000/svg"
      width="20"
      height="20"
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
