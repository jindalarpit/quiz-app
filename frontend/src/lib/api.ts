const BASE_URL = process.env.NEXT_PUBLIC_API_URL || '';

interface ApiError {
  message: string;
  status: number;
}

class ApiClient {
  private baseUrl: string;

  constructor(baseUrl: string) {
    this.baseUrl = baseUrl;
  }

  private getAccessToken(): string | null {
    if (typeof window === 'undefined') return null;
    const stored = localStorage.getItem('auth-storage');
    if (!stored) return null;
    try {
      const parsed = JSON.parse(stored);
      return parsed?.state?.tokens?.accessToken || null;
    } catch {
      return null;
    }
  }

  private getRefreshToken(): string | null {
    if (typeof window === 'undefined') return null;
    const stored = localStorage.getItem('auth-storage');
    if (!stored) return null;
    try {
      const parsed = JSON.parse(stored);
      return parsed?.state?.tokens?.refreshToken || null;
    } catch {
      return null;
    }
  }

  private getUserId(): string | null {
    if (typeof window === 'undefined') return null;
    const stored = localStorage.getItem('auth-storage');
    if (!stored) return null;
    try {
      const parsed = JSON.parse(stored);
      return parsed?.state?.user?.id || null;
    } catch {
      return null;
    }
  }

  private setTokens(accessToken: string, refreshToken: string): void {
    if (typeof window === 'undefined') return;
    const stored = localStorage.getItem('auth-storage');
    if (!stored) return;
    try {
      const parsed = JSON.parse(stored);
      if (parsed?.state) {
        parsed.state.tokens = { accessToken, refreshToken };
        localStorage.setItem('auth-storage', JSON.stringify(parsed));
      }
    } catch {
      // ignore
    }
  }

  private clearTokens(): void {
    if (typeof window === 'undefined') return;
    const stored = localStorage.getItem('auth-storage');
    if (!stored) return;
    try {
      const parsed = JSON.parse(stored);
      if (parsed?.state) {
        parsed.state.tokens = null;
        parsed.state.user = null;
        localStorage.setItem('auth-storage', JSON.stringify(parsed));
      }
    } catch {
      // ignore
    }
  }

  private async refreshAccessToken(): Promise<boolean> {
    const refreshToken = this.getRefreshToken();
    if (!refreshToken) return false;

    try {
      const response = await fetch(`${this.baseUrl}/api/auth/refresh`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ refreshToken }),
      });

      if (!response.ok) {
        this.clearTokens();
        return false;
      }

      const data = await response.json();
      this.setTokens(data.accessToken, data.refreshToken);
      return true;
    } catch {
      this.clearTokens();
      return false;
    }
  }

  async request<T>(
    endpoint: string,
    options: RequestInit = {}
  ): Promise<T> {
    const url = `${this.baseUrl}${endpoint}`;
    const token = this.getAccessToken();

    const headers: Record<string, string> = {
      'Content-Type': 'application/json',
      ...(options.headers as Record<string, string>),
    };

    if (token) {
      headers['Authorization'] = `Bearer ${token}`;
    }

    // Add X-User-Id header for services that need it (quiz, session, analytics)
    const userId = this.getUserId();
    if (userId) {
      headers['X-User-Id'] = userId;
    }

    let response = await fetch(url, { ...options, headers });

    // Auto-refresh on 401
    if (response.status === 401 && token) {
      const refreshed = await this.refreshAccessToken();
      if (refreshed) {
        const newToken = this.getAccessToken();
        if (newToken) {
          headers['Authorization'] = `Bearer ${newToken}`;
        }
        response = await fetch(url, { ...options, headers });
      } else {
        // Redirect to login
        if (typeof window !== 'undefined') {
          window.location.href = '/login';
        }
        throw { message: 'Session expired', status: 401 } as ApiError;
      }
    }

    if (!response.ok) {
      const errorBody = await response.json().catch(() => ({}));
      throw {
        message: errorBody.message || `Request failed with status ${response.status}`,
        status: response.status,
      } as ApiError;
    }

    // Handle 204 No Content or empty body
    if (response.status === 204) {
      return undefined as T;
    }

    const text = await response.text();
    if (!text) {
      return undefined as T;
    }

    return JSON.parse(text);
  }

  get<T>(endpoint: string): Promise<T> {
    return this.request<T>(endpoint, { method: 'GET' });
  }

  post<T>(endpoint: string, body?: unknown): Promise<T> {
    return this.request<T>(endpoint, {
      method: 'POST',
      body: body ? JSON.stringify(body) : undefined,
    });
  }

  put<T>(endpoint: string, body?: unknown): Promise<T> {
    return this.request<T>(endpoint, {
      method: 'PUT',
      body: body ? JSON.stringify(body) : undefined,
    });
  }

  delete<T>(endpoint: string): Promise<T> {
    return this.request<T>(endpoint, { method: 'DELETE' });
  }
}

export const api = new ApiClient(BASE_URL);
export type { ApiError };
