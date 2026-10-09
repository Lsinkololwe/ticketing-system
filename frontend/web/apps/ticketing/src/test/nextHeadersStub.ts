/** Test stand-in for `next/headers` backed by an in-memory cookie jar. */
export const jar = new Map<string, string>();

const store = {
  get: (name: string) =>
    jar.has(name) ? { name, value: jar.get(name) as string } : undefined,
  set: (name: string, value: string) => void jar.set(name, value),
  delete: (name: string) => void jar.delete(name),
};

export async function cookies() {
  return store;
}
export async function headers() {
  return new Headers();
}
