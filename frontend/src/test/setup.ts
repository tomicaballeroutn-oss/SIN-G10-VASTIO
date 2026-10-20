import '@testing-library/jest-dom/vitest';
import { cleanup } from '@testing-library/react';
import { afterEach } from 'vitest';
import { configure } from '@testing-library/dom';

// La primera navegación carga rutas lazy; en un arranque frío puede superar el segundo por defecto.
configure({ asyncUtilTimeout: 5000 });

afterEach(() => {
  cleanup();
});
