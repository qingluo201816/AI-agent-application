import {
  chatWithNovelInspirationAssist,
  chatWithNovelKeywordContinuation,
  chatWithNovelStateMemory
} from '../api'

export const novelAbilities = [
  {
    key: 'state_memory',
    index: '01',
    title: '状态回溯',
    subtitle: 'State Memory',
    tagline: '找回人物、设定与剧情推进脉络',
    description: '回溯角色设定、关系变动、时间线和关键状态，帮助你在长篇创作里保持连贯。',
    longDescription: '适合在忘记人物关系、剧情推进位置或想确认本轮哪些信息值得沉淀为长期状态时使用。',
    placeholder: '例如：帮我回溯女主在第二卷结尾前的身份变化，并标出这一轮值得沉淀的新状态。',
    emptyTitle: '选择历史会话，或新建一段状态回溯',
    emptyDescription: '左侧会按入口展示真实持久化会话。选中任意会话后，右侧会立刻加载历史消息并继续对话。',
    createConnection: chatWithNovelStateMemory
  },
  {
    key: 'inspiration_assist',
    index: '02',
    title: '灵感辅助',
    subtitle: 'Inspiration Assist',
    tagline: '默认入口，优先解决卡文与推进问题',
    description: '围绕当前卡点给出可执行的推进思路，让你更快落到下一场戏和下一章。',
    longDescription: '适合卡文、冲突不够、章节落点不明确时使用。系统会优先输出可以直接动笔的方案，而不是空泛分析。',
    placeholder: '例如：我卡在第三卷中段，男女主已经决裂，但下一章不知道怎么继续拉高冲突。',
    emptyTitle: '新建一段灵感辅助会话',
    emptyDescription: '第一次完成 AI 回复后，系统会自动用首轮问答生成简短标题，后续也支持手动重命名。',
    createConnection: chatWithNovelInspirationAssist
  },
  {
    key: 'keyword_continuation',
    index: '03',
    title: '关键词续写',
    subtitle: 'Keyword Continuation',
    tagline: '根据关键词直接续写正文',
    description: '输入关键词、桥段目标或半段正文，让系统按当前叙事风格自然续写。',
    longDescription: '适合你已经知道这一段要写什么，但不想从零起笔的时候使用。系统会尽量输出可直接落地的正文续写。',
    placeholder: '例如：关键词：雨夜、旧站台、误会未解开、男主带伤出现。续写成正文。',
    emptyTitle: '新建一段关键词续写会话',
    emptyDescription: '你可以先在左侧新建会话，再用同一个 chatId 持续续写，历史记录会按入口自动归类。',
    createConnection: chatWithNovelKeywordContinuation
  }
]

export const novelAbilityMap = Object.fromEntries(
  novelAbilities.map((ability) => [ability.key, ability])
)
