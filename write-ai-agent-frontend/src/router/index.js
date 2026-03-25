import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  {
    path: '/',
    name: 'Home',
    component: () => import('../views/Home.vue'),
    meta: {
      title: '晴落 AI 写作智能体平台',
      description: '小说写作工作台与任务执行归档工作台入口页。'
    }
  },
  {
    path: '/writing-master',
    redirect: '/novel-workbench'
  },
  {
    path: '/novel-workbench',
    name: 'NovelWorkbench',
    component: () => import('../views/WriteMaster.vue'),
    meta: {
      title: 'AI 小说写作工作台 - 晴落 AI 写作智能体平台',
      description: '提供状态回溯、灵感辅助、关键词续写三个独立创作入口。'
    }
  },
  {
    path: '/super-agent',
    name: 'SuperAgent',
    component: () => import('../views/SuperAgent.vue'),
    meta: {
      title: 'AI 超级写作智能体 - 晴落 AI 写作智能体平台',
      description: '用于小说任务执行、资料整理、文档归档与 PDF 导出。'
    }
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach((to, from, next) => {
  if (to.meta.title) {
    document.title = to.meta.title
  }
  next()
})

export default router
