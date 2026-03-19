import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  {
    path: '/',
    name: 'Home',
    component: () => import('../views/Home.vue'),
    meta: {
      title: '首页 - 晴落AI智能体应用平台',
      description: '晴落AI智能体应用平台提供AI小说写作智能体和AI超级智能体服务，满足您的创作与问答需求'
    }
  },
  {
    path: '/writing-master',
    name: 'WritingMaster',
    component: () => import('../views/LoveMaster.vue'),
    meta: {
      title: 'AI小说写作智能体 - 晴落AI智能体应用平台',
      description: 'AI小说写作智能体是晴落AI智能体应用平台的专业创作顾问，帮助你完成构思、续写和润色'
    }
  },
  {
    path: '/love-master',
    redirect: '/writing-master'
  },
  {
    path: '/super-agent',
    name: 'SuperAgent',
    component: () => import('../views/SuperAgent.vue'),
    meta: {
      title: 'AI超级智能体 - 晴落AI智能体应用平台',
      description: 'AI超级智能体是晴落AI智能体应用平台的全能助手，能解答各类专业问题，提供精准建议和解决方案'
    }
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

// 全局导航守卫，设置文档标题
router.beforeEach((to, from, next) => {
  // 设置页面标题
  if (to.meta.title) {
    document.title = to.meta.title
  }
  next()
})

export default router
