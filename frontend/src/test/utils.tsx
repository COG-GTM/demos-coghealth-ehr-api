import type { ReactNode } from 'react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { render } from '@testing-library/react';

export function createTestQueryClient() {
  return new QueryClient({
    defaultOptions: {
      queries: { retry: false, gcTime: 0 },
    },
  });
}

interface RenderRouteOptions {
  initialEntries: string[];
  path: string;
  element: ReactNode;
  children?: ReactNode;
}

export function renderRoute({ initialEntries, path, element, children }: RenderRouteOptions) {
  const queryClient = createTestQueryClient();
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={initialEntries}>
        <Routes>
          <Route path={path} element={element}>
            <Route index element={children} />
          </Route>
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>
  );
}
