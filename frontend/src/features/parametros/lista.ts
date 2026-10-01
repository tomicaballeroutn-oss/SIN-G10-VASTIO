/** Reemplaza el elemento con el mismo id o lo agrega al final. */
export function reemplazar<T extends { id: number }>(lista: T[] | undefined, item: T): T[] {
  const actual = lista ?? [];
  return actual.some((it) => it.id === item.id) ? actual.map((it) => (it.id === item.id ? item : it)) : [...actual, item];
}
