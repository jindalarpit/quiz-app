'use client';

import { useCallback, useEffect, useRef, useState } from 'react';

import { TimerSync, SYNC_SAMPLE_COUNT } from '@/lib/timer-sync';
import type { SyncSample } from '@/lib/timer-sync';

export interface TimerSyncStatus {
  isSynced: boolean;
  offset: number;
  error: string | null;
  isSyncing: boolean;
}

interface UseTimerSyncOptions {
  /** Function to send a WebSocket message */
  sendMessage: (type: string, payload: unknown) => void;
  /** Whether the WebSocket is connected */
  isConnected: boolean;
  /** Called when sync fails and participant should be blocked */
  onSyncFailure?: (error: string) => void;
}

/**
 * React hook wrapping TimerSync for use in components.
 * Manages the sync lifecycle: initial calibration, drift detection, and re-sync.
 */
export function useTimerSync({ sendMessage, isConnected, onSyncFailure }: UseTimerSyncOptions) {
  const timerSyncRef = useRef<TimerSync>(new TimerSync());
  const samplesRef = useRef<SyncSample[]>([]);
  const pendingSyncRef = useRef<{ t1: number } | null>(null);
  const syncTimeoutRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const [status, setStatus] = useState<TimerSyncStatus>({
    isSynced: false,
    offset: 0,
    error: null,
    isSyncing: false,
  });

  const updateStatus = useCallback(() => {
    const state = timerSyncRef.current.getState();
    setStatus((prev) => ({
      ...prev,
      isSynced: state.isSynced,
      offset: state.offset,
    }));
  }, []);

  /**
   * Send a single clock sync request.
   */
  const sendSyncRequest = useCallback(() => {
    const t1 = Date.now();
    pendingSyncRef.current = { t1 };
    sendMessage('clock.sync_request', { clientTimestamp: t1 });

    // Timeout for individual sync request (5 seconds)
    if (syncTimeoutRef.current) {
      clearTimeout(syncTimeoutRef.current);
    }
    syncTimeoutRef.current = setTimeout(() => {
      // If we haven't received a response, count as failed
      if (pendingSyncRef.current) {
        pendingSyncRef.current = null;
        const maxExceeded = timerSyncRef.current.recordFailedAttempt();
        if (maxExceeded) {
          const errorMsg = 'Unable to synchronize timing. Please check your connection.';
          setStatus((prev) => ({ ...prev, error: errorMsg, isSyncing: false }));
          onSyncFailure?.(errorMsg);
        }
      }
    }, 5000);
  }, [sendMessage, onSyncFailure]);

  /**
   * Start the full sync process (3 samples).
   */
  const startSync = useCallback(() => {
    samplesRef.current = [];
    setStatus((prev) => ({ ...prev, isSyncing: true, error: null }));
    sendSyncRequest();
  }, [sendSyncRequest]);

  /**
   * Handle a clock.sync_response message from the server.
   */
  const handleSyncResponse = useCallback(
    (payload: { serverTimestamp: number; clientTimestamp: number }) => {
      if (!pendingSyncRef.current) return;

      if (syncTimeoutRef.current) {
        clearTimeout(syncTimeoutRef.current);
        syncTimeoutRef.current = null;
      }

      const t4 = Date.now();
      const sample: SyncSample = {
        t1: pendingSyncRef.current.t1,
        t2: payload.serverTimestamp,
        t3: payload.serverTimestamp, // Server processes immediately, t2 ≈ t3
        t4,
      };

      pendingSyncRef.current = null;
      samplesRef.current.push(sample);

      if (samplesRef.current.length < SYNC_SAMPLE_COUNT) {
        // Send next sync request after a small delay to avoid burst
        setTimeout(() => {
          sendSyncRequest();
        }, 50);
      } else {
        // All samples collected — calibrate
        timerSyncRef.current.calibrate(samplesRef.current);
        timerSyncRef.current.startDriftDetection();
        updateStatus();
        setStatus((prev) => ({ ...prev, isSyncing: false }));
      }
    },
    [sendSyncRequest, updateStatus]
  );

  /**
   * Get the estimated current server time.
   */
  const getServerTime = useCallback((): number => {
    return timerSyncRef.current.getServerTime();
  }, []);

  /**
   * Get remaining time for a question timer.
   */
  const getRemainingTime = useCallback((startTimestamp: number, durationMs: number): number => {
    return timerSyncRef.current.getRemainingTime(startTimestamp, durationMs);
  }, []);

  /**
   * Get the current offset.
   */
  const getOffset = useCallback((): number => {
    return timerSyncRef.current.getOffset();
  }, []);

  // Set up re-sync and error callbacks
  useEffect(() => {
    const timerSync = timerSyncRef.current;

    timerSync.setOnResyncNeeded(() => {
      // Trigger re-sync
      startSync();
    });

    timerSync.setOnSyncError((message: string) => {
      setStatus((prev) => ({ ...prev, error: message, isSyncing: false }));
      onSyncFailure?.(message);
    });

    return () => {
      timerSync.destroy();
    };
  }, [startSync, onSyncFailure]);

  // Auto-start sync when connected
  useEffect(() => {
    if (isConnected) {
      // Small delay to ensure WebSocket is fully ready
      const timeout = setTimeout(() => {
        startSync();
      }, 100);
      return () => clearTimeout(timeout);
    } else {
      // Reset on disconnect
      timerSyncRef.current.reset();
      setStatus({ isSynced: false, offset: 0, error: null, isSyncing: false });
    }
  }, [isConnected, startSync]);

  // Cleanup on unmount
  useEffect(() => {
    return () => {
      if (syncTimeoutRef.current) {
        clearTimeout(syncTimeoutRef.current);
      }
    };
  }, []);

  return {
    status,
    handleSyncResponse,
    getServerTime,
    getRemainingTime,
    getOffset,
    startSync,
  };
}
