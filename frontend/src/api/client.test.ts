import type { AxiosAdapter, AxiosRequestConfig, AxiosResponse } from 'axios';
import { AxiosError } from 'axios';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { apiClient } from './client';

const originalAdapter = apiClient.defaults.adapter;

function respondWith(status: number): AxiosAdapter {
  return (config: AxiosRequestConfig) => {
    const response = {
      data: status === 200 ? { ok: true } : { error: 'failed' },
      status,
      statusText: String(status),
      headers: {},
      config,
    } as AxiosResponse;

    if (status >= 400) {
      return Promise.reject(
        new AxiosError('Request failed', String(status), undefined, null, response)
      );
    }
    return Promise.resolve(response);
  };
}

function sentHeaders(adapter: ReturnType<typeof vi.fn>): Record<string, unknown> {
  return adapter.mock.calls[0][0].headers;
}

describe('apiClient interceptors', () => {
  beforeEach(() => {
    localStorage.clear();
    vi.stubGlobal('location', { href: '' } as Location);
  });

  afterEach(() => {
    apiClient.defaults.adapter = originalAdapter;
    vi.unstubAllGlobals();
  });

  it('attaches a Bearer header when a token is stored', async () => {
    const adapter = vi.fn(respondWith(200));
    apiClient.defaults.adapter = adapter;
    localStorage.setItem('auth_token', 'token-123');

    await apiClient.get('/patients');

    expect(sentHeaders(adapter).Authorization).toBe('Bearer token-123');
  });

  it('sends no Authorization header when no token is stored', async () => {
    const adapter = vi.fn(respondWith(200));
    apiClient.defaults.adapter = adapter;

    await apiClient.get('/patients');

    expect(sentHeaders(adapter).Authorization).toBeUndefined();
  });

  it('clears the token and redirects to /login on 401, and still rejects', async () => {
    apiClient.defaults.adapter = respondWith(401);
    localStorage.setItem('auth_token', 'token-123');

    await expect(apiClient.get('/patients')).rejects.toBeInstanceOf(AxiosError);

    expect(localStorage.getItem('auth_token')).toBeNull();
    expect(window.location.href).toBe('/login');
  });

  it('keeps the token and does not redirect on non-401 errors', async () => {
    apiClient.defaults.adapter = respondWith(500);
    localStorage.setItem('auth_token', 'token-123');

    await expect(apiClient.get('/patients')).rejects.toMatchObject({ code: '500' });

    expect(localStorage.getItem('auth_token')).toBe('token-123');
    expect(window.location.href).toBe('');
  });
});
