<template>
  <div class="user-page">
    <div class="page-header">
      <h2>用户管理</h2>
      <div class="search-bar">
        <el-input
          v-model="queryParams.keyword"
          placeholder="按账号/姓名搜索"
          clearable
          style="width: 220px"
          @keyup.enter="handleSearch"
        />
        <el-button type="primary" @click="handleSearch">查询</el-button>
      </div>
    </div>

    <el-table v-loading="loading" :data="userList" border>
      <el-table-column prop="id" label="id" width="70" />
      <el-table-column prop="userNo" label="账号" min-width="120" />
      <el-table-column prop="realName" label="姓名" min-width="100" />
      <el-table-column prop="phone" label="手机号" min-width="130" />
      <el-table-column prop="email" label="邮箱" min-width="160" show-overflow-tooltip />
      <el-table-column label="状态" min-width="80">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'info'">
            {{ row.status === 1 ? '正常' : '禁用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="角色" min-width="150">
        <template #default="{ row }">
          <el-select
            v-model="row.role"
            placeholder="未分配"
            style="width: 120px"
            @change="handleRoleChange(row)"
          >
            <el-option
              v-for="role in roleOptions"
              :key="role.id"
              :value="role.id"
              :label="role.roleName"
            />
          </el-select>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination
      class="pager"
      :total="total"
      :current-page="queryParams.page"
      :page-size="queryParams.size"
      :page-sizes="[10, 20, 50]"
      layout="total, sizes, prev, pager, next"
      @current-change="handlePageChange"
      @size-change="handleSizeChange"
    />
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { pageUsers, listRoles, assignUserRole } from '@/api/v1/rbac'

const loading = ref(false)
const userList = ref([])
const total = ref(0)
const roleOptions = ref([])
const queryParams = reactive({ keyword: '', page: 1, size: 10 })

async function fetchUsers() {
  loading.value = true
  try {
    const res = await pageUsers({
      keyword: queryParams.keyword,
      page: queryParams.page,
      size: queryParams.size
    })
    if (res.code === 200) {
      userList.value = res.data.list
      total.value = res.data.total
    }
  } finally {
    loading.value = false
  }
}

async function fetchRoles() {
  const res = await listRoles()
  if (res.code === 200) {
    roleOptions.value = res.data
  }
}

function handleSearch() {
  queryParams.page = 1
  fetchUsers()
}

function handlePageChange(page) {
  queryParams.page = page
  fetchUsers()
}

function handleSizeChange(size) {
  queryParams.size = size
  queryParams.page = 1
  fetchUsers()
}

async function handleRoleChange(row) {
  const res = await assignUserRole({ userId: row.id, roleId: row.role })
  if (res.code === 200) {
    ElMessage.success('角色已更新')
  } else {
    // 失败时重新拉取，回滚下拉显示
    fetchUsers()
  }
}

onMounted(() => {
  fetchRoles()
  fetchUsers()
})
</script>

<style scoped>
.user-page {
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
.search-bar {
  display: flex;
  gap: 12px;
}
.pager {
  margin-top: 16px;
  justify-content: flex-end;
}
</style>
