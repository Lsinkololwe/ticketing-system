const isDevelopment = process.env.NODE_ENV === 'development';

export const logger = {
  log: (...args: unknown[]): void => {
    if (isDevelopment) {
      // eslint-disable-next-line no-console
      console.log(...args);
    }
  },
  warn: (...args: unknown[]): void => {
    if (isDevelopment) {
      // eslint-disable-next-line no-console
      console.warn(...args);
    }
  },
  error: (...args: unknown[]): void => {
    // Errors are always logged to help with diagnostics
    // eslint-disable-next-line no-console
    console.error(...args);
  },
};


