import request from '@/utils/request'

// 角色列表
export function listRoles() {
  return request.get('/api/v1/rbac/roles')
}

// 角色详情
export function getRole(id) {
  return request.get(`/api/v1/rbac/roles/${id}`)
}

// 新增角色
export function createRole(data) {
  return request.post('/api/v1/rbac/roles', data)
}

// 编辑角色
export function updateRole(id, data) {
  return request.put(`/api/v1/rbac/roles/${id}`, data)
}

// 删除角色
export function deleteRole(id) {
  return request.delete(`/api/v1/rbac/roles/${id}`)
}

// 权限树
export function getPermissionTree() {
  return request.get('/api/v1/rbac/permissions/tree')
}

// 角色已绑定权限 id（回显）
export function getRolePermissions(id) {
  return request.get(`/api/v1/rbac/roles/${id}/permissions`)
}

// 保存角色授权
export function assignRolePermissions(id, permissionIds) {
  return request.put(`/api/v1/rbac/roles/${id}/permissions`, { permissionIds })
}

// 用户分页列表
export function pageUsers(params) {
  return request.get('/api/v1/rbac/users', { params })
}

// 用户换角色
export function assignUserRole(data) {
  return request.put('/api/v1/rbac/users/role', data)
}
