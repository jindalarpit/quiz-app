'use client';

import { useCallback } from 'react';

import { cn } from '@/lib/utils';

interface AutoModeControlsProps {
  /** Whether auto mode is currently enabled */
  enabled: boolean;
  /** Current leaderboard delay in seconds */
  leaderboardDelay: number;
  /** Callback when auto mode is toggled */
  onToggle: (enabled: boolean) => void;
  /** Callback when leaderboard delay changes */
  onDelayChange: (seconds: number) => void;
  /** When true, renders a compact version for floating in-game display */
  compact?: boolean;
}

/**
 * Controls for enabling/disabling Auto Mode and configuring the leaderboard delay.
 * Supports a full lobby display and a compact floating in-game display.
 */
export function AutoModeControls({
  enabled,
  leaderboardDelay,
  onToggle,
  onDelayChange,
  compact = false,
}: AutoModeControlsProps) {
  const handleToggle = useCallback(() => {
    onToggle(!enabled);
  }, [enabled, onToggle]);

  const handleDelayChange = useCallback(
    (e: React.ChangeEvent<HTMLInputElement>) => {
      const value = parseInt(e.target.value, 10);
      if (!isNaN(value)) {
        onDelayChange(value);
      }
    },
    [onDelayChange]
  );

  if (compact) {
    return (
      <div
        className={cn(
          'inline-flex items-center gap-2 rounded-full px-3 py-1.5',
          'bg-white/90 shadow-md backdrop-blur-sm dark:bg-slate-800/90',
          'border border-slate-200 dark:border-slate-700'
        )}
        role="group"
        aria-label="Auto mode controls"
      >
        <StatusIndicator enabled={enabled} compact />
        <ToggleSwitch
          enabled={enabled}
          onToggle={handleToggle}
          compact
        />
        {enabled && (
          <span className="text-xs text-slate-500 dark:text-slate-400">
            {leaderboardDelay}s
          </span>
        )}
      </div>
    );
  }

  return (
    <div
      className={cn(
        'rounded-lg border border-slate-200 p-4',
        'bg-white dark:border-slate-700 dark:bg-slate-800'
      )}
      role="group"
      aria-label="Auto mode controls"
    >
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-3">
          <ToggleSwitch enabled={enabled} onToggle={handleToggle} />
          <div>
            <label
              htmlFor="auto-mode-toggle"
              className="block text-sm font-medium text-slate-900 dark:text-white"
            >
              Auto Mode
            </label>
            <p className="text-xs text-slate-500 dark:text-slate-400">
              Automatically advance through questions
            </p>
          </div>
        </div>
        <StatusIndicator enabled={enabled} />
      </div>

      {enabled && (
        <div className="mt-4 border-t border-slate-100 pt-4 dark:border-slate-700">
          <label
            htmlFor="leaderboard-delay"
            className="block text-sm font-medium text-slate-700 dark:text-slate-300"
          >
            Leaderboard display time
          </label>
          <div className="mt-1 flex items-center gap-2">
            <input
              id="leaderboard-delay"
              type="number"
              min={1}
              max={30}
              value={leaderboardDelay}
              onChange={handleDelayChange}
              className={cn(
                'w-20 rounded-md border border-slate-300 px-3 py-1.5 text-sm',
                'text-slate-900 dark:border-slate-600 dark:bg-slate-700 dark:text-white',
                'focus:border-indigo-500 focus:outline-none focus:ring-1 focus:ring-indigo-500'
              )}
              aria-describedby="delay-description"
            />
            <span
              id="delay-description"
              className="text-sm text-slate-500 dark:text-slate-400"
            >
              seconds (1–30)
            </span>
          </div>
        </div>
      )}
    </div>
  );
}

interface ToggleSwitchProps {
  enabled: boolean;
  onToggle: () => void;
  compact?: boolean;
}

function ToggleSwitch({ enabled, onToggle, compact = false }: ToggleSwitchProps) {
  return (
    <button
      id="auto-mode-toggle"
      type="button"
      role="switch"
      aria-checked={enabled}
      aria-label="Toggle auto mode"
      onClick={onToggle}
      className={cn(
        'relative inline-flex shrink-0 cursor-pointer rounded-full border-2 border-transparent',
        'transition-colors duration-200 ease-in-out',
        'focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:ring-offset-2',
        enabled ? 'bg-indigo-600' : 'bg-slate-300 dark:bg-slate-600',
        compact ? 'h-5 w-9' : 'h-6 w-11'
      )}
    >
      <span
        aria-hidden="true"
        className={cn(
          'pointer-events-none inline-block transform rounded-full bg-white shadow ring-0',
          'transition duration-200 ease-in-out',
          compact ? 'h-4 w-4' : 'h-5 w-5',
          enabled
            ? compact ? 'translate-x-4' : 'translate-x-5'
            : 'translate-x-0'
        )}
      />
    </button>
  );
}

interface StatusIndicatorProps {
  enabled: boolean;
  compact?: boolean;
}

function StatusIndicator({ enabled, compact = false }: StatusIndicatorProps) {
  return (
    <span
      className={cn(
        'inline-flex items-center gap-1 rounded-full font-medium',
        compact ? 'px-1.5 py-0.5 text-[10px]' : 'px-2.5 py-0.5 text-xs',
        enabled
          ? 'bg-green-100 text-green-700 dark:bg-green-900/30 dark:text-green-400'
          : 'bg-slate-100 text-slate-600 dark:bg-slate-700 dark:text-slate-400'
      )}
      aria-live="polite"
    >
      <span
        className={cn(
          'inline-block rounded-full',
          compact ? 'h-1.5 w-1.5' : 'h-2 w-2',
          enabled ? 'bg-green-500' : 'bg-slate-400'
        )}
        aria-hidden="true"
      />
      {compact ? (enabled ? 'ON' : 'OFF') : (enabled ? 'Auto Mode: ON' : 'Auto Mode: OFF')}
    </span>
  );
}
