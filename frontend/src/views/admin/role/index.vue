<template>
  <div class="role-page">
    <div class="page-header">
      <h2>角色管理</h2>
      <el-button v-permission="['system:role:save']" type="primary" @click="handleAdd">新增角色</el-button>
    </div>

    <el-table v-loading="loading" :data="roleList" border>
      <el-table-column prop="roleName" label="角色名称" min-width="120" />
      <el-table-column prop="roleCode" label="角色编码" min-width="120" />
      <el-table-column label="数据范围" min-width="110">
        <template #default="{ row }">{{ scopeMap[row.dataScope] }}</template>
      </el-table-column>
      <el-table-column label="状态" min-width="80">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'info'">
            {{ row.status === 1 ? '启用' : '停用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="remark" label="备注" min-width="160" show-overflow-tooltip />
      <el-table-column label="操作" width="240" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="handleAssign(row)">分配权限</el-button>
          <el-button link type="primary" @click="handleEdit(row)">编辑</el-button>
          <el-button link type="danger" @click="handleDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 新增/编辑角色 -->
    <el-dialog v-model="formDialogVisible" :title="form.id ? '编辑角色' : '新增角色'" width="460px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="角色编码" prop="roleCode">
          <el-input v-model="form.roleCode" placeholder="如 student" />
        </el-form-item>
        <el-form-item label="角色名称" prop="roleName">
          <el-input v-model="form.roleName" placeholder="如 学生" />
        </el-form-item>
        <el-form-item label="数据范围" prop="dataScope">
          <el-select v-model="form.dataScope" style="width: 100%">
            <el-option :value="1" label="全部数据" />
            <el-option :value="2" label="本班/本部门" />
            <el-option :value="3" label="本人" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-radio-group v-model="form.status">
            <el-radio :value="1">启用</el-radio>
            <el-radio :value="0">停用</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitForm">确定</el-button>
      </template>
    </el-dialog>

    <!-- 分配权限 -->
    <el-dialog v-model="permDialogVisible" :title="`分配权限 - ${currentRole.roleName || ''}`" width="520px">
      <div v-loading="permLoading" class="tree-wrap">
        <el-tree
          ref="treeRef"
          :data="permTree"
          :props="treeProps"
          show-checkbox
          node-key="id"
          default-expand-all
        />
      </div>
      <template #footer>
        <el-button @click="permDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="permSaving" @click="submitPermissions">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, nextTick, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  listRoles, createRole, updateRole, deleteRole,
  getPermissionTree, getRolePermissions, assignRolePermissions
} from '@/api/v1/rbac'

const scopeMap = { 1: '全部数据', 2: '本班/本部门', 3: '本人' }
const treeProps = { label: 'permName', children: 'children' }

const roleList = ref([])
const loading = ref(false)

async function fetchRoles() {
  loading.value = true
  try {
    const res = await listRoles()
    if (res.code === 200) {
      roleList.value = res.data
    }
  } finally {
    loading.value = false
  }
}

// 新增/编辑角色
const formDialogVisible = ref(false)
const formRef = ref()
const defaultForm = () => ({ id: null, roleCode: '', roleName: '', dataScope: 1, status: 1, remark: '' })
const form = reactive(defaultForm())
const rules = {
  roleCode: [{ required: true, message: '请输入角色编码', trigger: 'blur' }],
  roleName: [{ required: true, message: '请输入角色名称', trigger: 'blur' }],
  dataScope: [{ required: true, message: '请选择数据范围', trigger: 'change' }],
  status: [{ required: true, message: '请选择状态', trigger: 'change' }]
}

function handleAdd() {
  Object.assign(form, defaultForm())
  formDialogVisible.value = true
}

function handleEdit(row) {
  Object.assign(form, {
    id: row.id,
    roleCode: row.roleCode,
    roleName: row.roleName,
    dataScope: row.dataScope,
    status: row.status,
    remark: row.remark
  })
  formDialogVisible.value = true
}

async function submitForm() {
  await formRef.value.validate()
  const res = form.id
    ? await updateRole(form.id, { ...form })
    : await createRole({ ...form })
  if (res.code === 200) {
    ElMessage.success('保存成功')
    formDialogVisible.value = false
    fetchRoles()
  }
}

function handleDelete(row) {
  ElMessageBox.confirm(`确定删除角色「${row.roleName}」吗？`, '删除确认', {
    type: 'warning'
  })
    .then(async () => {
      const res = await deleteRole(row.id)
      if (res.code === 200) {
        ElMessage.success('删除成功')
        fetchRoles()
      }
    })
    .catch(() => {})
}

// 权限分配
const permDialogVisible = ref(false)
const permLoading = ref(false)
const permSaving = ref(false)
const permTree = ref([])
const treeRef = ref()
const currentRole = ref({})

// 递归收集所有含子节点的父节点 id
function collectParentIds(nodes, set) {
  for (const node of nodes) {
    if (node.children && node.children.length) {
      set.add(node.id)
      collectParentIds(node.children, set)
    }
  }
}

async function handleAssign(row) {
  currentRole.value = row
  permDialogVisible.value = true
  permLoading.value = true
  try {
    const [treeRes, checkedRes] = await Promise.all([
      getPermissionTree(),
      getRolePermissions(row.id)
    ])
    permTree.value = treeRes.code === 200 ? treeRes.data : []
    const parentIds = new Set()
    collectParentIds(permTree.value, parentIds)
    const checked = checkedRes.code === 200 ? checkedRes.data : []
    await nextTick()
    // 只回显叶子（操作）节点，菜单勾选态由 el-tree 自动推导，避免父子联动错误
    const leafIds = checked.filter((id) => !parentIds.has(id))
    treeRef.value.setCheckedKeys(leafIds)
  } finally {
    permLoading.value = false
  }
}

async function submitPermissions() {
  const checked = treeRef.value.getCheckedKeys()
  const halfChecked = treeRef.value.getHalfCheckedKeys()
  // 完全勾选 + 半选菜单并集，保证菜单型权限也正确入库
  const permissionIds = [...new Set([...checked, ...halfChecked])]
  permSaving.value = true
  try {
    const res = await assignRolePermissions(currentRole.value.id, permissionIds)
    if (res.code === 200) {
      ElMessage.success('授权成功')
      permDialogVisible.value = false
    }
  } finally {
    permSaving.value = false
  }
}

onMounted(fetchRoles)
</script>

<style scoped>
.role-page {
  padding: 24px;
}
.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}
.page-header h2 {
  margin: 0;
}
.tree-wrap {
  max-height: 420px;
  overflow-y: auto;
}
</style>
