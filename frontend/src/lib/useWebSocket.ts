'use client';

import { useCallback, useEffect, useRef, useState } from 'react';

import { useTimerSync } from '@/hooks/useTimerSync';
import type { WSMessage } from '@/types';

export type ConnectionState = 'connecting' | 'connected' | 'disconnected' | 'reconnecting';

interface UseWebSocketOptions {
  pin: string;
  token?: string;
  participantId?: string;
  onMessage?: (message: WSMessage) => void;
  onConnect?: () => void;
  onDisconnect?: () => void;
  onSyncFailure?: (error: string) => void;
  enabled?: boolean;
}

const MAX_RECONNECT_ATTEMPTS = 10;
const MAX_BACKOFF_MS = 30000;

function getBackoffDelay(attempt: number): number {
  return Math.min(1000 * Math.pow(2, attempt), MAX_BACKOFF_MS);
}

export function useWebSocket({
  pin,
  token,
  participantId,
  onMessage,
  onConnect,
  onDisconnect,
  onSyncFailure,
  enabled = true,
}: UseWebSocketOptions) {
  const [connectionState, setConnectionState] = useState<ConnectionState>('disconnected');
  const wsRef = useRef<WebSocket | null>(null);
  const reconnectAttemptRef = useRef(0);
  const reconnectTimeoutRef = useRef<NodeJS.Timeout | null>(null);
  const heartbeatIntervalRef = useRef<NodeJS.Timeout | null>(null);
  const onMessageRef = useRef(onMessage);
  const onConnectRef = useRef(onConnect);
  const onDisconnectRef = useRef(onDisconnect);

  // Keep refs up to date
  useEffect(() => {
    onMessageRef.current = onMessage;
  }, [onMessage]);
  useEffect(() => {
    onConnectRef.current = onConnect;
  }, [onConnect]);
  useEffect(() => {
    onDisconnectRef.current = onDisconnect;
  }, [onDisconnect]);

  const sendMessage = useCallback((type: string, payload: unknown) => {
    if (wsRef.current?.readyState === WebSocket.OPEN) {
      wsRef.current.send(JSON.stringify({ type, payload }));
    }
  }, []);

  // Timer sync integration
  const timerSync = useTimerSync({
    sendMessage,
    isConnected: connectionState === 'connected',
    onSyncFailure,
  });

  const cleanup = useCallback(() => {
    if (reconnectTimeoutRef.current) {
      clearTimeout(reconnectTimeoutRef.current);
      reconnectTimeoutRef.current = null;
    }
    if (heartbeatIntervalRef.current) {
      clearInterval(heartbeatIntervalRef.current);
      heartbeatIntervalRef.current = null;
    }
    if (wsRef.current) {
      wsRef.current.onclose = null;
      wsRef.current.onerror = null;
      wsRef.current.onmessage = null;
      wsRef.current.onopen = null;
      wsRef.current.close();
      wsRef.current = null;
    }
  }, []);

  const connect = useCallback(() => {
    if (!enabled || !pin) return;

    cleanup();
    setConnectionState('connecting');

    const wsProtocol = typeof window !== 'undefined' && window.location.protocol === 'https:' ? 'wss:' : 'ws:';
    const wsHost = process.env.NEXT_PUBLIC_WS_URL || 'localhost:8084';

    let url = `${wsProtocol}//${wsHost}/ws/${pin}`;
    const params = new URLSearchParams();
    if (token) params.set('token', token);
    if (participantId) params.set('participantId', participantId);
    const queryString = params.toString();
    if (queryString) url += `?${queryString}`;

    const ws = new WebSocket(url);
    wsRef.current = ws;

    ws.onopen = () => {
      setConnectionState('connected');
      reconnectAttemptRef.current = 0;
      onConnectRef.current?.();

      // Start heartbeat
      heartbeatIntervalRef.current = setInterval(() => {
        if (ws.readyState === WebSocket.OPEN) {
          ws.send(JSON.stringify({ type: 'heartbeat_ack', payload: { timestamp: Date.now() } }));
        }
      }, 15000);
    };

    ws.onmessage = (event) => {
      try {
        const message: WSMessage = JSON.parse(event.data);

        // Intercept clock sync responses for timer sync
        if (message.type === 'clock.sync_response') {
          const payload = message.payload as { serverTimestamp: number; clientTimestamp: number };
          timerSync.handleSyncResponse(payload);
        }

        // Still pass all messages to the consumer
        onMessageRef.current?.(message);
      } catch {
        // Ignore malformed messages
      }
    };

    ws.onclose = () => {
      if (heartbeatIntervalRef.current) {
        clearInterval(heartbeatIntervalRef.current);
        heartbeatIntervalRef.current = null;
      }

      onDisconnectRef.current?.();

      // Attempt reconnection
      if (reconnectAttemptRef.current < MAX_RECONNECT_ATTEMPTS && enabled) {
        setConnectionState('reconnecting');
        const delay = getBackoffDelay(reconnectAttemptRef.current);
        reconnectAttemptRef.current += 1;
        reconnectTimeoutRef.current = setTimeout(() => {
          connect();
        }, delay);
      } else {
        setConnectionState('disconnected');
      }
    };

    ws.onerror = () => {
      // onclose will fire after onerror
    };
  }, [pin, token, participantId, enabled, cleanup, timerSync]);

  const disconnect = useCallback(() => {
    reconnectAttemptRef.current = MAX_RECONNECT_ATTEMPTS; // Prevent reconnection
    cleanup();
    setConnectionState('disconnected');
  }, [cleanup]);

  useEffect(() => {
    if (enabled && pin) {
      connect();
    }
    return () => {
      cleanup();
    };
  }, [enabled, pin, connect, cleanup]);

  return {
    connectionState,
    sendMessage,
    disconnect,
    reconnect: connect,
    timerSync,
  };
}
