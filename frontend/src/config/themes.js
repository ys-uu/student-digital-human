export const THEMES = [
  { key: 'academic', name: '清新学术', desc: '白底 + 学院蓝', colors: ['#2f6fed', '#22b8c3'] },
  { key: 'paper', name: '暖阳纸张', desc: '米白 + 暖橙', colors: ['#c2703a', '#e0a45c'] },
  { key: 'minimal', name: '极简商务', desc: '纯白 + 靛蓝', colors: ['#4f46e5', '#111111'] },
  { key: 'soft', name: '薄雾渐变', desc: '雾蓝 + 紫青', colors: ['#8b5cf6', '#06b6d4'] }
]

const KEY = 'app_theme'

export function currentTheme() {
  return localStorage.getItem(KEY) || 'soft'
}

export function applyTheme(key) {
  document.body.classList.remove(...THEMES.map((t) => 'theme-' + t.key))
  document.body.classList.add('theme-' + key)
  localStorage.setItem(KEY, key)
}
