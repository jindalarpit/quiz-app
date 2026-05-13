/**
 * TimerSync — NTP-like clock synchronization between client and server.
 *
 * Performs 3 round-trip measurements during WebSocket handshake to calculate
 * the clock offset between client and server. Uses the median offset for
 * robustness against network jitter.
 *
 * Offset formula per sample: ((t2 - t1) + (t3 - t4)) / 2
 *   t1 = client send time
 *   t2 = server receive time (serverTimestamp in response)
 *   t3 = server send time (same as t2 in our implementation since server processes immediately)
 *   t4 = client receive time
 */

export interface SyncSample {
  t1: number; // client send timestamp
  t2: number; // server receive timestamp (serverTimestamp from response)
  t3: number; // server send timestamp (same as t2 in our protocol)
  t4: number; // client receive timestamp
}

export interface TimerSyncState {
  offset: number;
  isSynced: boolean;
  syncAttempts: number;
  lastSyncTime: number;
}

const DRIFT_CHECK_INTERVAL_MS = 30_000; // 30 seconds
const DRIFT_THRESHOLD_MS = 500;
const MAX_RESYNC_ATTEMPTS = 3;
const SYNC_SAMPLE_COUNT = 3;

export class TimerSync {
  private offset = 0;
  private isSynced = false;
  private syncAttempts = 0;
  private lastSyncTime = 0;
  private driftCheckTimer: ReturnType<typeof setInterval> | null = null;
  private consecutiveResyncAttempts = 0;
  private lastLocalTime = 0;
  private lastEstimatedServerTime = 0;
  private onResyncNeeded: (() => void) | null = null;
  private onSyncError: ((message: string) => void) | null = null;

  /**
   * Calibrate the clock offset from collected sync samples.
   * Uses the median of sample offsets for robustness.
   */
  calibrate(samples: SyncSample[]): void {
    if (samples.length === 0) return;

    const offsets = samples.map((sample) => {
      return ((sample.t2 - sample.t1) + (sample.t3 - sample.t4)) / 2;
    });

    // Sort and take median
    offsets.sort((a, b) => a - b);
    const medianIndex = Math.floor(offsets.length / 2);
    this.offset = offsets.length % 2 === 0
      ? (offsets[medianIndex - 1] + offsets[medianIndex]) / 2
      : offsets[medianIndex];

    this.isSynced = true;
    this.syncAttempts += 1;
    this.lastSyncTime = Date.now();
    this.consecutiveResyncAttempts = 0;

    // Initialize drift detection baseline
    this.lastLocalTime = Date.now();
    this.lastEstimatedServerTime = this.getServerTime();
  }

  /**
   * Returns the estimated current server time using the calibrated offset.
   */
  getServerTime(): number {
    return Date.now() + this.offset;
  }

  /**
   * Returns the current clock offset (server time - client time).
   */
  getOffset(): number {
    return this.offset;
  }

  /**
   * Calculate remaining time for a question timer.
   * @param startTimestamp - Server timestamp when the question started
   * @param durationMs - Total duration in milliseconds
   * @returns Remaining time in milliseconds (clamped to >= 0)
   */
  getRemainingTime(startTimestamp: number, durationMs: number): number {
    const currentServerTime = this.getServerTime();
    const elapsed = currentServerTime - startTimestamp;
    return Math.max(0, durationMs - elapsed);
  }

  /**
   * Whether the clock has been successfully synchronized.
   */
  getIsSynced(): boolean {
    return this.isSynced;
  }

  /**
   * Get the current sync state for external consumers.
   */
  getState(): TimerSyncState {
    return {
      offset: this.offset,
      isSynced: this.isSynced,
      syncAttempts: this.syncAttempts,
      lastSyncTime: this.lastSyncTime,
    };
  }

  /**
   * Set callback for when re-synchronization is needed (drift detected).
   */
  setOnResyncNeeded(callback: () => void): void {
    this.onResyncNeeded = callback;
  }

  /**
   * Set callback for when sync fails after max attempts.
   */
  setOnSyncError(callback: (message: string) => void): void {
    this.onSyncError = callback;
  }

  /**
   * Start periodic drift detection.
   * Checks every 30 seconds if the clock has drifted more than 500ms.
   */
  startDriftDetection(): void {
    this.stopDriftDetection();

    this.lastLocalTime = Date.now();
    this.lastEstimatedServerTime = this.getServerTime();

    this.driftCheckTimer = setInterval(() => {
      this.checkDrift();
    }, DRIFT_CHECK_INTERVAL_MS);
  }

  /**
   * Stop periodic drift detection.
   */
  stopDriftDetection(): void {
    if (this.driftCheckTimer !== null) {
      clearInterval(this.driftCheckTimer);
      this.driftCheckTimer = null;
    }
  }

  /**
   * Check if clock drift exceeds threshold.
   * Drift is detected by comparing how much local time has advanced
   * vs how much server time should have advanced.
   */
  private checkDrift(): void {
    if (!this.isSynced) return;

    const currentLocalTime = Date.now();
    const currentEstimatedServerTime = this.getServerTime();

    // How much local time has passed since last check
    const localElapsed = currentLocalTime - this.lastLocalTime;
    // How much server time has passed since last check
    const serverElapsed = currentEstimatedServerTime - this.lastEstimatedServerTime;

    // Drift is the difference between local and server elapsed times
    const drift = Math.abs(localElapsed - serverElapsed);

    // Update baseline for next check
    this.lastLocalTime = currentLocalTime;
    this.lastEstimatedServerTime = currentEstimatedServerTime;

    if (drift > DRIFT_THRESHOLD_MS) {
      this.handleDriftDetected();
    }
  }

  /**
   * Handle detected drift — trigger re-sync or error.
   */
  private handleDriftDetected(): void {
    this.consecutiveResyncAttempts += 1;

    if (this.consecutiveResyncAttempts > MAX_RESYNC_ATTEMPTS) {
      this.onSyncError?.('Unable to synchronize timing. Please check your connection.');
      return;
    }

    this.onResyncNeeded?.();
  }

  /**
   * Record a failed sync attempt during initial calibration.
   * Returns true if max attempts exceeded.
   */
  recordFailedAttempt(): boolean {
    this.syncAttempts += 1;
    return this.syncAttempts >= SYNC_SAMPLE_COUNT;
  }

  /**
   * Reset the sync state (useful for reconnection).
   */
  reset(): void {
    this.offset = 0;
    this.isSynced = false;
    this.syncAttempts = 0;
    this.lastSyncTime = 0;
    this.consecutiveResyncAttempts = 0;
    this.stopDriftDetection();
  }

  /**
   * Clean up resources.
   */
  destroy(): void {
    this.stopDriftDetection();
    this.onResyncNeeded = null;
    this.onSyncError = null;
  }
}

export { SYNC_SAMPLE_COUNT, DRIFT_CHECK_INTERVAL_MS, DRIFT_THRESHOLD_MS, MAX_RESYNC_ATTEMPTS };
