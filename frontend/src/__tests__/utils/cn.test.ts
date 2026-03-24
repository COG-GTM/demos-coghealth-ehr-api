import { describe, it, expect } from 'vitest';
import { cn } from '../../utils/cn';

describe('cn', () => {
  it('merges class strings correctly', () => {
    expect(cn('foo', 'bar')).toBe('foo bar');
  });

  it('handles conditional classes (falsy values are dropped)', () => {
    const condition = false;
    expect(cn('foo', condition && 'bar', 'baz')).toBe('foo baz');
    expect(cn('foo', undefined, null, 'bar')).toBe('foo bar');
  });

  it('resolves TailwindCSS conflicts via twMerge', () => {
    expect(cn('p-2', 'p-4')).toBe('p-4');
    expect(cn('text-red-500', 'text-blue-500')).toBe('text-blue-500');
  });

  it('handles empty input', () => {
    expect(cn()).toBe('');
  });
});
