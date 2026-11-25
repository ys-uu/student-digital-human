import { useUserStore } from '@/stores/user'

// 把指令绑定值统一规整为数组（支持字符串或数组写法）
function normalize(value) {
  if (!value) {
    return []
  }
  return Array.isArray(value) ? value : [value]
}

// 权限指令：v-permission="['homework:create']"，无权限直接从 DOM 移除元素
export const permission = {
  mounted(el, binding) {
    const userStore = useUserStore()
    const codes = normalize(binding.value)
    if (codes.length === 0) {
      return
    }
    const allowed = codes.some((code) => userStore.hasPermission(code))
    if (!allowed && el.parentNode) {
      el.parentNode.removeChild(el)
    }
  }
}

// 角色指令：v-role="['admin']"，角色不匹配则移除元素
export const role = {
  mounted(el, binding) {
    const userStore = useUserStore()
    const roles = normalize(binding.value)
    if (roles.length === 0) {
      return
    }
    if (!userStore.hasRole(roles) && el.parentNode) {
      el.parentNode.removeChild(el)
    }
  }
}
