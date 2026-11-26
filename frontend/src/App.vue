<template>
  <router-view />
</template>

<script setup>
import { watch } from 'vue'
import { useUserStore } from '@/stores/user'
import { usePrivateSocket } from '@/composables/usePrivateSocket'

const userStore = useUserStore()
const { connect, disconnect } = usePrivateSocket()

watch(
  () => userStore.token,
  (token) => {
    if (token) connect()
    else disconnect()
  },
  { immediate: true }
)
</script>
