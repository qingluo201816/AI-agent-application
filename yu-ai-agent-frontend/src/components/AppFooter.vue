<template>
  <footer class="app-footer">
    <div class="footer-content">
      <p>© {{ currentYear }} 晴落AI智能体</p>
    </div>
  </footer>
</template>

<script setup>
import { computed } from 'vue'

const currentYear = computed(() => new Date().getFullYear())
</script>

<style scoped>
.app-footer {
  background-color: #fff;
  color: #888;
  border-top: 1px solid #eee;
  width: 100%;
  margin-top: auto;
  position: relative;
  z-index: 2;
}

.footer-content {
  max-width: 1200px;
  margin: 0 auto;
  padding: 16px 20px;
  text-align: center;
  font-size: 0.9rem;
}
</style>
