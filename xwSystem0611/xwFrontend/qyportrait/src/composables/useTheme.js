import { useHomeStore } from '@/stores/home'

export function useTheme() {
  const store = useHomeStore()

  function toggleTheme() {
    store.toggleTheme()
  }

  function initTheme() {
    store.initTheme()
  }

  return { theme: store.theme, toggleTheme, initTheme }
}
