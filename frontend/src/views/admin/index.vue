<template>
  <RoleConsole :role="role" />
</template>

<script setup>
import { computed } from 'vue'
import RoleConsole from '../../components/RoleConsole.vue'
import { useUserStore } from '../../stores/user'

const userStore = useUserStore()
const role = computed(() => (userStore.roleName === 'teacher' ? 'teacher' : 'admin'))
</script>
