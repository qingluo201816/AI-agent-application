<template>
  <div class="home-page">
    <div class="ambient ambient-left"></div>
    <div class="ambient ambient-right"></div>
    <div class="ambient ambient-core"></div>

    <header class="hero">
      <div class="hero-badge">QINGLUO AI WRITING SYSTEM</div>
      <h1 class="hero-title">晴落 AI 写作智能体平台</h1>
      <p class="hero-subtitle">
        深蓝霓虹语境中的 AI 写作中枢，将创作辅助、灵感延展与任务执行整合为更聚焦的双入口体验，让每一次写作调度都更沉浸、更清晰。
      </p>
    </header>

    <main class="entry-grid">
      <article
        v-for="entry in entries"
        :key="entry.route"
        :class="['entry-card', entry.theme]"
        role="button"
        tabindex="0"
        @click="navigateTo(entry.route)"
        @keydown.enter.prevent="navigateTo(entry.route)"
        @keydown.space.prevent="navigateTo(entry.route)"
      >
        <div class="entry-orb">
          <div class="entry-glyph">
            <svg
              v-if="entry.glyph === 'novel'"
              viewBox="0 0 88 88"
              aria-hidden="true"
            >
              <path d="M24 24.5C24 20.358 27.358 17 31.5 17H56.5C60.642 17 64 20.358 64 24.5V60.5C64 64.642 60.642 68 56.5 68H31.5C27.358 68 24 64.642 24 60.5V24.5Z" />
              <path d="M32 29H56" />
              <path d="M32 40H48" />
              <path d="M32 50.5H43" />
              <path d="M49 54L61 42L65 46L53 58L47.5 59.5L49 54Z" />
              <path d="M54.5 19L56.172 23.328L60.5 25L56.172 26.672L54.5 31L52.828 26.672L48.5 25L52.828 23.328L54.5 19Z" />
              <path d="M24 28C21.791 28 20 29.791 20 32V59C20 61.209 21.791 63 24 63" />
            </svg>

            <svg v-else viewBox="0 0 88 88" aria-hidden="true">
              <circle cx="44" cy="44" r="18" />
              <circle cx="44" cy="44" r="8" />
              <path d="M44 14V24" />
              <path d="M44 64V74" />
              <path d="M14 44H24" />
              <path d="M64 44H74" />
              <path d="M23 23L30 30" />
              <path d="M58 58L65 65" />
              <path d="M65 23L58 30" />
              <path d="M30 58L23 65" />
              <circle cx="44" cy="14" r="3" />
              <circle cx="74" cy="44" r="3" />
              <circle cx="44" cy="74" r="3" />
              <circle cx="14" cy="44" r="3" />
              <path d="M40 44L43 47L49 39" />
            </svg>
          </div>
        </div>

        <h2 class="entry-title">{{ entry.title }}</h2>
        <p class="entry-description">{{ entry.description }}</p>

        <div class="entry-tags">
          <span v-for="tag in entry.tags" :key="tag" class="entry-tag">{{ tag }}</span>
        </div>

        <button type="button" class="entry-button">
          {{ entry.action }}
          <span class="entry-button-arrow">→</span>
        </button>
      </article>
    </main>

    <AppFooter />
  </div>
</template>

<script setup>
import { useHead } from '@vueuse/head'
import { useRouter } from 'vue-router'
import AppFooter from '../components/AppFooter.vue'

useHead({
  title: '晴落 AI 写作智能体平台',
  meta: [
    {
      name: 'description',
      content: '晴落 AI 写作智能体平台提供深蓝霓虹风格的小说写作助手与超级写作智能体入口，帮助完成创作辅助、任务执行与写作调度。'
    },
    {
      name: 'keywords',
      content: 'AI写作,小说写作助手,超级写作智能体,任务执行,创作辅助'
    }
  ]
})

const router = useRouter()

const entries = [
  {
    glyph: 'novel',
    title: 'AI小说写作助手',
    description: '聚焦灵感延展、剧情推进与长篇创作陪跑，帮助你快速进入沉浸式写作状态。',
    tags: ['灵感续写', '剧情推演', '世界观记忆'],
    action: '进入小说工作台',
    route: '/novel-workbench',
    theme: 'theme-novel'
  },
  {
    glyph: 'task',
    title: 'AI超级写作智能体',
    description: '偏向中控与执行协同，适合资料整理、任务编排、结果归档与文档导出。',
    tags: ['任务编排', '资料归档', 'PDF 导出'],
    action: '启动超级智能体',
    route: '/super-agent',
    theme: 'theme-task'
  }
]

const navigateTo = (path) => {
  router.push(path)
}
</script>

<style scoped>
.home-page {
  --bg-panel: rgba(8, 17, 34, 0.86);
  --line: rgba(123, 184, 255, 0.16);
  --text-main: #f4f9ff;
  --text-subtle: rgba(209, 227, 255, 0.72);
  min-height: 100vh;
  position: relative;
  overflow: hidden;
  background:
    radial-gradient(circle at 16% 18%, rgba(39, 114, 255, 0.22), transparent 28%),
    radial-gradient(circle at 82% 16%, rgba(56, 221, 255, 0.2), transparent 24%),
    radial-gradient(circle at 50% 0%, rgba(58, 145, 255, 0.12), transparent 34%),
    linear-gradient(180deg, #040813 0%, #050d1a 48%, #02050c 100%);
  color: var(--text-main);
}

.ambient {
  position: absolute;
  border-radius: 999px;
  filter: blur(18px);
  opacity: 0.5;
  pointer-events: none;
}

.ambient-left {
  top: 110px;
  left: -100px;
  width: 320px;
  height: 320px;
  background: rgba(55, 160, 255, 0.18);
}

.ambient-right {
  top: 320px;
  right: -120px;
  width: 360px;
  height: 360px;
  background: rgba(44, 243, 222, 0.14);
}

.ambient-core {
  top: 40px;
  left: 50%;
  transform: translateX(-50%);
  width: 520px;
  height: 220px;
  background: rgba(78, 162, 255, 0.12);
}

.hero {
  position: relative;
  z-index: 1;
  max-width: 980px;
  margin: 0 auto;
  padding: 96px 24px 40px;
  text-align: center;
}

.hero::before {
  content: '';
  position: absolute;
  left: 50%;
  top: 34px;
  width: min(72vw, 620px);
  height: 200px;
  transform: translateX(-50%);
  border-radius: 999px;
  background: radial-gradient(circle, rgba(77, 160, 255, 0.22), transparent 68%);
  filter: blur(12px);
  pointer-events: none;
}

.hero-badge {
  position: relative;
  display: inline-flex;
  align-items: center;
  padding: 8px 14px;
  border-radius: 999px;
  border: 1px solid rgba(122, 193, 255, 0.24);
  background: rgba(8, 18, 36, 0.76);
  color: #8fceff;
  font-size: 12px;
  letter-spacing: 0.22em;
  box-shadow:
    inset 0 0 0 1px rgba(255, 255, 255, 0.03),
    0 0 24px rgba(62, 161, 255, 0.08);
}

.hero-title {
  position: relative;
  margin: 22px 0 18px;
  font-size: clamp(2.9rem, 6vw, 5rem);
  line-height: 1.02;
  letter-spacing: 0.04em;
  background: linear-gradient(180deg, #f9fdff 0%, #d9efff 38%, #88e9ff 100%);
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
  text-shadow:
    0 0 12px rgba(122, 193, 255, 0.4),
    0 0 32px rgba(77, 196, 255, 0.24),
    0 18px 42px rgba(2, 18, 48, 0.55);
}

.hero-subtitle {
  position: relative;
  max-width: 760px;
  margin: 0 auto;
  color: var(--text-subtle);
  font-size: 1.08rem;
  line-height: 1.92;
}

.entry-grid {
  position: relative;
  z-index: 1;
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 30px;
  max-width: 1180px;
  margin: 0 auto;
  padding: 18px 24px 72px;
}

.entry-card {
  --card-accent: rgba(59, 146, 255, 0.5);
  --card-accent-soft: rgba(79, 214, 255, 0.3);
  --card-icon-stroke: #bfe7ff;
  position: relative;
  overflow: hidden;
  min-height: 470px;
  padding: 36px 34px 34px;
  border-radius: 32px;
  border: 1px solid var(--line);
  background: var(--bg-panel);
  box-shadow:
    0 28px 80px rgba(0, 10, 30, 0.42),
    inset 0 0 0 1px rgba(255, 255, 255, 0.03);
  cursor: pointer;
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
  transition: transform 0.28s ease, border-color 0.28s ease, box-shadow 0.28s ease;
}

.entry-card::before {
  content: '';
  position: absolute;
  inset: 0;
  background:
    radial-gradient(circle at 50% 18%, var(--card-accent-soft), transparent 28%),
    linear-gradient(180deg, rgba(55, 115, 235, 0.08), transparent 44%);
  pointer-events: none;
}

.entry-card::after {
  content: '';
  position: absolute;
  inset: 1px;
  border-radius: 31px;
  border: 1px solid rgba(151, 211, 255, 0.06);
  pointer-events: none;
}

.entry-card:hover {
  transform: translateY(-8px);
  border-color: rgba(124, 203, 255, 0.34);
  box-shadow:
    0 34px 90px rgba(0, 16, 46, 0.56),
    0 0 0 1px rgba(124, 203, 255, 0.08),
    0 0 46px rgba(81, 168, 255, 0.12);
}

.theme-task {
  --card-accent: rgba(42, 208, 255, 0.46);
  --card-accent-soft: rgba(41, 255, 214, 0.24);
  --card-icon-stroke: #d4fff4;
}

.entry-orb {
  position: relative;
  width: 210px;
  height: 210px;
  margin-bottom: 24px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.entry-orb::before {
  content: '';
  position: absolute;
  width: 188px;
  height: 188px;
  border-radius: 50%;
  background: radial-gradient(circle, rgba(114, 184, 255, 0.16), transparent 68%);
  filter: blur(8px);
}

.entry-orb::after {
  content: '';
  position: absolute;
  inset: 22px;
  border-radius: 38px;
  border: 1px solid rgba(124, 203, 255, 0.12);
  background:
    radial-gradient(circle at 50% 38%, rgba(119, 197, 255, 0.18), transparent 54%),
    rgba(5, 14, 30, 0.64);
  box-shadow:
    inset 0 0 0 1px rgba(255, 255, 255, 0.02),
    0 0 40px rgba(54, 129, 255, 0.12);
}

.theme-task .entry-orb::after {
  border-color: rgba(110, 235, 255, 0.14);
  background:
    radial-gradient(circle at 50% 38%, rgba(65, 255, 223, 0.14), transparent 54%),
    rgba(5, 17, 28, 0.66);
}

.entry-glyph {
  position: relative;
  z-index: 1;
  width: 138px;
  height: 138px;
  border-radius: 42px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: 1px solid rgba(111, 190, 255, 0.18);
  background:
    linear-gradient(180deg, rgba(12, 26, 52, 0.94), rgba(6, 16, 34, 0.92)),
    rgba(8, 17, 36, 0.82);
  box-shadow:
    inset 0 0 0 1px rgba(255, 255, 255, 0.03),
    0 26px 66px rgba(17, 69, 158, 0.22),
    0 0 36px rgba(78, 164, 255, 0.14);
}

.entry-glyph::before {
  content: '';
  position: absolute;
  inset: 12px;
  border-radius: 30px;
  border: 1px solid rgba(154, 217, 255, 0.12);
}

.entry-glyph::after {
  content: '';
  position: absolute;
  inset: -10px;
  border-radius: 52px;
  background: radial-gradient(circle, rgba(74, 166, 255, 0.14), transparent 70%);
  filter: blur(18px);
  z-index: -1;
}

.theme-task .entry-glyph {
  border-color: rgba(94, 227, 255, 0.18);
  box-shadow:
    inset 0 0 0 1px rgba(255, 255, 255, 0.03),
    0 26px 66px rgba(10, 92, 118, 0.22),
    0 0 36px rgba(49, 255, 214, 0.12);
}

.theme-task .entry-glyph::after {
  background: radial-gradient(circle, rgba(49, 255, 214, 0.14), transparent 70%);
}

.entry-glyph svg {
  width: 82px;
  height: 82px;
  fill: none;
  stroke: var(--card-icon-stroke);
  stroke-width: 2.2;
  stroke-linecap: round;
  stroke-linejoin: round;
  filter:
    drop-shadow(0 0 10px rgba(116, 194, 255, 0.28))
    drop-shadow(0 0 20px rgba(78, 164, 255, 0.14));
}

.theme-task .entry-glyph svg {
  filter:
    drop-shadow(0 0 10px rgba(70, 255, 220, 0.2))
    drop-shadow(0 0 20px rgba(70, 255, 220, 0.12));
}

.entry-title {
  position: relative;
  z-index: 1;
  margin: 0 0 14px;
  font-size: clamp(1.95rem, 4vw, 2.5rem);
  line-height: 1.16;
  text-shadow:
    0 0 14px rgba(104, 186, 255, 0.2),
    0 8px 28px rgba(2, 15, 44, 0.5);
}

.entry-description {
  position: relative;
  z-index: 1;
  max-width: 450px;
  margin: 0;
  color: var(--text-subtle);
  font-size: 1rem;
  line-height: 1.88;
}

.entry-tags {
  position: relative;
  z-index: 1;
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 10px;
  margin-top: auto;
  padding-top: 26px;
}

.entry-tag {
  padding: 8px 12px;
  border-radius: 999px;
  border: 1px solid rgba(123, 184, 255, 0.18);
  background: rgba(9, 18, 36, 0.76);
  color: #d5ebff;
  font-size: 13px;
  box-shadow: inset 0 0 0 1px rgba(255, 255, 255, 0.02);
}

.theme-task .entry-tag {
  border-color: rgba(110, 235, 255, 0.18);
}

.entry-button {
  position: relative;
  z-index: 1;
  margin-top: 28px;
  min-width: 190px;
  height: 54px;
  border: none;
  border-radius: 18px;
  background: linear-gradient(135deg, #1f88ff, #35c2ff);
  color: #fff;
  font-size: 15px;
  font-weight: 700;
  letter-spacing: 0.08em;
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  box-shadow:
    0 18px 36px rgba(31, 136, 255, 0.22),
    0 0 28px rgba(77, 166, 255, 0.14);
  transition: transform 0.2s ease, box-shadow 0.2s ease;
}

.entry-card:hover .entry-button {
  transform: translateY(-1px);
  box-shadow:
    0 22px 44px rgba(31, 136, 255, 0.28),
    0 0 34px rgba(77, 166, 255, 0.18);
}

.theme-task .entry-button {
  background: linear-gradient(135deg, #1698ff, #33f6da);
}

.entry-button-arrow {
  font-size: 18px;
  line-height: 1;
}

@media (max-width: 900px) {
  .entry-grid {
    grid-template-columns: 1fr;
  }

  .entry-card {
    min-height: unset;
  }
}

@media (max-width: 640px) {
  .hero {
    padding-top: 78px;
  }

  .hero-title {
    font-size: clamp(2.4rem, 11vw, 4rem);
  }

  .entry-grid {
    padding: 16px 16px 56px;
  }

  .entry-card {
    padding: 28px 20px 24px;
    border-radius: 26px;
  }

  .entry-card::after {
    border-radius: 25px;
  }

  .entry-orb {
    width: 180px;
    height: 180px;
    margin-bottom: 18px;
  }

  .entry-orb::before {
    width: 158px;
    height: 158px;
  }

  .entry-orb::after {
    inset: 18px;
    border-radius: 32px;
  }

  .entry-glyph {
    width: 120px;
    height: 120px;
    border-radius: 34px;
  }

  .entry-glyph svg {
    width: 70px;
    height: 70px;
  }

  .entry-title {
    font-size: 1.7rem;
  }

  .entry-button {
    width: 100%;
  }
}
</style>
