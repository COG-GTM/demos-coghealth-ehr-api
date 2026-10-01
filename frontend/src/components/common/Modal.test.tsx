import { describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen } from '@testing-library/react';
import { Modal } from './Modal';

describe('Modal', () => {
  it('renders nothing when closed', () => {
    const { container } = render(
      <Modal isOpen={false} onClose={() => {}} title="Patient">
        <p>body</p>
      </Modal>
    );

    expect(container).toBeEmptyDOMElement();
    expect(document.body.style.overflow).toBe('');
  });

  it('renders title and children when open', () => {
    render(
      <Modal isOpen onClose={() => {}} title="Patient" footer={<button>Save</button>}>
        <p>body</p>
      </Modal>
    );

    expect(screen.getByRole('heading', { name: 'Patient' })).toBeTruthy();
    expect(screen.getByText('body')).toBeTruthy();
    expect(screen.getByRole('button', { name: 'Save' })).toBeTruthy();
  });

  it('locks body scroll while open and restores it on close', () => {
    const { rerender } = render(
      <Modal isOpen onClose={() => {}}>
        <p>body</p>
      </Modal>
    );
    expect(document.body.style.overflow).toBe('hidden');

    rerender(
      <Modal isOpen={false} onClose={() => {}}>
        <p>body</p>
      </Modal>
    );
    expect(document.body.style.overflow).toBe('');
  });

  it('restores body scroll on unmount', () => {
    const { unmount } = render(
      <Modal isOpen onClose={() => {}}>
        <p>body</p>
      </Modal>
    );
    expect(document.body.style.overflow).toBe('hidden');

    unmount();
    expect(document.body.style.overflow).toBe('');
  });

  it('calls onClose on Escape', () => {
    const onClose = vi.fn();
    render(
      <Modal isOpen onClose={onClose}>
        <p>body</p>
      </Modal>
    );

    fireEvent.keyDown(document, { key: 'Escape' });
    expect(onClose).toHaveBeenCalledTimes(1);
  });

  it('ignores other keys and Escape while closed', () => {
    const onClose = vi.fn();
    const { rerender } = render(
      <Modal isOpen onClose={onClose}>
        <p>body</p>
      </Modal>
    );

    fireEvent.keyDown(document, { key: 'Enter' });
    expect(onClose).not.toHaveBeenCalled();

    rerender(
      <Modal isOpen={false} onClose={onClose}>
        <p>body</p>
      </Modal>
    );
    fireEvent.keyDown(document, { key: 'Escape' });
    expect(onClose).not.toHaveBeenCalled();
  });

  it('calls onClose on overlay click', () => {
    const onClose = vi.fn();
    const { container } = render(
      <Modal isOpen onClose={onClose}>
        <p>body</p>
      </Modal>
    );

    const overlay = container.querySelector('.modal-overlay');
    expect(overlay).not.toBeNull();
    fireEvent.click(overlay!);
    expect(onClose).toHaveBeenCalledTimes(1);
  });

  it('calls onClose from the header close button', () => {
    const onClose = vi.fn();
    render(
      <Modal isOpen onClose={onClose} title="Patient">
        <p>body</p>
      </Modal>
    );

    fireEvent.click(screen.getAllByRole('button')[0]);
    expect(onClose).toHaveBeenCalledTimes(1);
  });
});
