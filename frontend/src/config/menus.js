// 按《大学生辅助学习 AI 数字人系统需求分析说明书》整理的功能菜单（界面占位）
const tint = {
  blue: 'linear-gradient(135deg, #8bb8ff, #5f8bff)',
  cyan: 'linear-gradient(135deg, #8bf1ff, #4fd6e8)',
  mint: 'linear-gradient(135deg, #9dffd9, #48d9a6)',
  violet: 'linear-gradient(135deg, #c9b2ff, #9b7bff)',
  amber: 'linear-gradient(135deg, #ffe0a3, #f5b955)',
  coral: 'linear-gradient(135deg, #ffc4ae, #ff8f6b)'
}

const student = {
  meta: {
    label: '学生',
    stats: [
      { label: '今日学习', value: '42 min', hint: '较昨日 +12 分钟' },
      { label: '待完成作业', value: '3 项', hint: '最近截止 明天 22:00' },
      { label: '连续打卡', value: '7 天', hint: '本周目标 5/7' },
      { label: '知识掌握度', value: '76%', hint: '较上周 +4%' }
    ]
  },
  groups: [
    {
      key: 'companion', title: '数字人陪伴', icon: 'chat', tag: 'FR-01',
      desc: '以拟人化数字人为统一入口，支持语音、文字、动作与情绪反馈。',
      features: [
        { name: '数字人对话', desc: '语音 / 文字 / 动作与数字人实时对话，学习统一入口。', icon: 'chat', prio: 'P0', tint: tint.blue },
        { name: '快捷提问', desc: '常用问题一键发起，结合学习状态智能推荐。', icon: 'spark', prio: 'P0', tint: tint.cyan },
        { name: '形象与情绪', desc: '数字人形象、表情与情绪反馈，陪伴式学习体验。', icon: 'users', prio: 'P1', tint: tint.violet }
      ]
    },
    {
      key: 'guide', title: '课程智能导学', icon: 'route', tag: 'FR-02',
      desc: '按专业、课程、章节自动生成学习路径，拆解重点难点考点。',
      features: [
        { name: '学习路径', desc: '依据课程与章节生成个性化学习路径与进度。', icon: 'route', prio: 'P0', tint: tint.cyan },
        { name: '知识点拆解', desc: '重点、难点、考点、易错点逐层拆解。', icon: 'target', prio: 'P0', tint: tint.mint },
        { name: '数字人讲解', desc: '数字人逐段讲解，支持暂停、重讲与举例。', icon: 'mic', prio: 'P0', tint: tint.violet }
      ]
    },
    {
      key: 'qa', title: 'AI 智能答疑', icon: 'help', tag: 'FR-03',
      desc: '自然语言与拍照提问，按“思路→步骤→提示→总结”引导式作答。',
      features: [
        { name: '文字提问', desc: '自然语言描述问题，引导式多轮追问。', icon: 'help', prio: 'P0', tint: tint.blue },
        { name: '拍照搜题', desc: '上传题目图片，OCR 识别后分步讲解。', icon: 'camera', prio: 'P0', tint: tint.coral },
        { name: '多轮追问', desc: '不直接给答案，逐步提示直至独立解出。', icon: 'chat', prio: 'P0', tint: tint.mint }
      ]
    },
    {
      key: 'homework', title: '作业与习题', icon: 'task', tag: 'FR-04',
      desc: '作业智能分析、错题本、同类题推荐与完成提醒。',
      features: [
        { name: '作业列表', desc: '作业任务、截止提醒与完成状态一览。', icon: 'task', prio: 'P0', tint: tint.amber },
        { name: '错题本', desc: '错题自动收录，按考点筛选与重做。', icon: 'book', prio: 'P0', tint: tint.coral },
        { name: '同类题推荐', desc: '基于错题推荐变式训练，举一反三。', icon: 'spark', prio: 'P0', tint: tint.mint },
        { name: '完成提醒', desc: '作业截止与学习计划到点提醒。', icon: 'bell', prio: 'P1', tint: tint.blue }
      ]
    },
    {
      key: 'preview', title: '预习与复习', icon: 'calendar', tag: 'FR-05',
      desc: '预习清单、复习计划、记忆曲线与抽背默写速记。',
      features: [
        { name: '预习清单', desc: '课前预习任务与导学材料自动生成。', icon: 'calendar', prio: 'P0', tint: tint.cyan },
        { name: '复习计划', desc: '按遗忘曲线安排复习节奏。', icon: 'clock', prio: 'P0', tint: tint.blue },
        { name: '记忆曲线', desc: '可视化记忆保持率与最佳复习时点。', icon: 'chart', prio: 'P0', tint: tint.violet },
        { name: '抽背默写', desc: '考点抽背、公式速记与默写批改。', icon: 'doc', prio: 'P0', tint: tint.mint }
      ]
    },
    {
      key: 'class', title: '课堂辅助', icon: 'mic', tag: 'FR-06',
      desc: '课件解析、录音转写与课堂实时追问。',
      features: [
        { name: '课件解析', desc: '拍照课件局部提问，即时解析。', icon: 'camera', prio: 'P1', tint: tint.coral },
        { name: '录音转写', desc: '课堂录音转文字，自动提炼重点笔记。', icon: 'mic', prio: 'P1', tint: tint.violet },
        { name: '实时追问', desc: '课堂中的“暂停、重讲、举例”指令追问。', icon: 'help', prio: 'P1', tint: tint.blue }
      ]
    },
    {
      key: 'exam', title: '备考冲刺', icon: 'spark', tag: 'FR-07',
      desc: '真题解析、考点串讲、模拟卷与应试指导。',
      features: [
        { name: '真题解析', desc: '历年真题分步讲解与答题技巧。', icon: 'doc', prio: 'P1', tint: tint.amber },
        { name: '考点串讲', desc: '高频考点串联，考前重点梳理。', icon: 'spark', prio: 'P1', tint: tint.coral },
        { name: '模拟卷', desc: '限时模拟与自动判分、错因分析。', icon: 'target', prio: 'P1', tint: tint.mint }
      ]
    },
    {
      key: 'report', title: '成长报告', icon: 'chart', tag: 'FR-08',
      desc: '学情统计、掌握度雷达图与周月诊断建议。',
      features: [
        { name: '学情指标', desc: '学习时长、专注度、完成率等指标。', icon: 'chart', prio: 'P0', tint: tint.blue },
        { name: '掌握度雷达图', desc: '按知识点维度呈现掌握情况。', icon: 'target', prio: 'P0', tint: tint.cyan },
        { name: '诊断建议', desc: '周 / 月诊断报告与提升建议。', icon: 'spark', prio: 'P0', tint: tint.mint }
      ]
    }
  ]
}

const teacher = {
  meta: {
    label: '教师',
    stats: [
      { label: '待批阅作业', value: '18 份', hint: '最近提交 10 分钟前' },
      { label: '班级人数', value: '46 人', hint: '计算机 2401 班' },
      { label: '本周作业', value: '2 次', hint: '1 次已截止' },
      { label: '预警学生', value: '3 人', hint: '连续两周未完成' }
    ]
  },
  groups: [
    {
      key: 'kb', title: '知识库维护', icon: 'book', tag: 'FR-10',
      desc: '课件与题库维护、版本管理，支撑 RAG 学科知识库。',
      features: [
        { name: '课件管理', desc: '上传、解析与更新课程课件资料。', icon: 'doc', prio: 'P2', tint: tint.violet },
        { name: '题库维护', desc: '题目录入、标签标注与难度分级。', icon: 'book', prio: 'P2', tint: tint.blue },
        { name: '版本管理', desc: '知识库版本对比、回滚与发布。', icon: 'clock', prio: 'P2', tint: tint.cyan }
      ]
    },
    {
      key: 'hw', title: '作业管理', icon: 'task', tag: 'FR-10',
      desc: '作业发布、批阅与成绩统计，减轻重复性工作。',
      features: [
        { name: '发布作业', desc: '选择班级与题库，设置截止时间发布。', icon: 'task', prio: 'P2', tint: tint.mint },
        { name: '作业批阅', desc: '逐份批阅、评分与评语，支持 AI 辅助。', icon: 'doc', prio: 'P2', tint: tint.amber },
        { name: '成绩统计', desc: '班级成绩分布、题目正确率统计。', icon: 'chart', prio: 'P2', tint: tint.coral }
      ]
    },
    {
      key: 'class', title: '班级学情', icon: 'chart', tag: 'FR-10',
      desc: '班级学情监控、异常预警与个体画像。',
      features: [
        { name: '班级看板', desc: '班级整体学习进度与掌握度概览。', icon: 'chart', prio: 'P2', tint: tint.blue },
        { name: '异常预警', desc: '学习停滞、作业缺交等风险自动预警。', icon: 'bell', prio: 'P2', tint: tint.coral },
        { name: '学生画像', desc: '个体学习行为、薄弱知识点画像。', icon: 'users', prio: 'P2', tint: tint.violet }
      ]
    }
  ]
}

const admin = {
  meta: {
    label: '管理员',
    stats: [
      { label: '课程总数', value: '12 门', hint: '本学期 8 门开课' },
      { label: '班级数量', value: '8 个', hint: '覆盖 3 个院系' },
      { label: '教师账号', value: '24 个', hint: '本月新增 2 个' },
      { label: '学生账号', value: '356 个', hint: '活跃 289 人' }
    ]
  },
  groups: [
    {
      key: 'course', title: '课程与班级', icon: 'book', tag: 'FR-10',
      desc: '课程、班级与人员配置，支撑教学组织管理。',
      features: [
        { name: '课程管理', desc: '课程创建、开课计划与知识库绑定。', icon: 'book', prio: 'P2', tint: tint.amber },
        { name: '班级管理', desc: '班级划分、成员导入与班主任设置。', icon: 'users', prio: 'P2', tint: tint.coral },
        { name: '人员配置', desc: '教师、助教与学生账号分配。', icon: 'users', prio: 'P2', tint: tint.cyan }
      ]
    },
    {
      key: 'avatar', title: '数字人配置', icon: 'settings', tag: 'FR-10',
      desc: '数字人形象、语音与教学风格配置（UC-14）。',
      features: [
        { name: '形象风格', desc: '数字人外观、服饰与场景选择。', icon: 'users', prio: 'P2', tint: tint.violet },
        { name: '语音音色', desc: '音色、语速与情感风格调节。', icon: 'mic', prio: 'P2', tint: tint.blue },
        { name: '教学风格', desc: '引导强度、提示粒度与语气风格。', icon: 'settings', prio: 'P2', tint: tint.mint }
      ]
    },
    {
      key: 'data', title: '数据统计', icon: 'chart', tag: 'FR-10',
      desc: '教学效果评估、使用统计与报表导出。',
      features: [
        { name: '教学效果评估', desc: '课程维度掌握度、及格率与提升幅度。', icon: 'target', prio: 'P2', tint: tint.cyan },
        { name: '使用统计', desc: '活跃用户、功能使用频次与时长。', icon: 'chart', prio: 'P2', tint: tint.blue },
        { name: '报表导出', desc: '按学期导出教学与学情报表。', icon: 'doc', prio: 'P2', tint: tint.amber }
      ]
    },
    {
      key: 'system', title: '权限与系统', icon: 'shield', tag: '—',
      desc: '角色权限与操作日志，保障系统安全合规。',
      features: [
        { name: '角色授权', desc: '角色与权限码绑定，支持按角色查看。', icon: 'shield', prio: 'P2', tint: tint.violet, path: '/admin/role' },
        { name: '用户管理', desc: '用户列表与角色分配。', icon: 'users', prio: 'P2', tint: tint.blue, path: '/admin/user' },
        { name: '数据大屏', desc: '班级学情实时监控大屏。', icon: 'chart', prio: 'P2', tint: tint.cyan, path: '/admin/dashboard' }
      ]
    }
  ]
}

export default { student, teacher, admin }
