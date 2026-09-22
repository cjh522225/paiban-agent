# 智能排班系统 - 前端

基于 Vue 3 + Vite + TypeScript + Element Plus + Tailwind CSS 构建的管理后台前端。

## 技术栈

- **Vue 3** — 组合式 API + `<script setup>`
- **TypeScript** — 类型安全
- **Vite** — 构建工具
- **Element Plus** — UI 组件库
- **Tailwind CSS** — 原子化 CSS
- **Pinia** — 状态管理
- **Vue Router** — 路由管理
- **Axios** — HTTP 请求

## 目录结构

```
src/
├── api/                # API 请求模块（auth、user、office、dormitory、schedule 等）
├── assets/             # 静态资源（logo 等）
├── components/         # 公共组件
├── layouts/            # 布局组件（MainLayout：侧边栏 + 顶栏 + 内容区）
├── router/             # 路由配置（/admin 管理员、/user 用户、/ 登录）
├── stores/             # Pinia 状态（userStore：用户信息、token）
├── utils/              # 工具函数（request.ts：Axios 封装，含 token 注入和 401 处理）
└── views/
    ├── Login.vue       # 登录页
    ├── admin/          # 管理员页面
    │   ├── AdminHome.vue              # 首页仪表盘
    │   ├── AdminDormitory.vue         # 宿舍排班管理
    │   ├── AdminDormitoryManage.vue   # 宿舍楼管理
    │   ├── AdminOffice.vue            # 办公室排班管理
    │   ├── AdminOfficeManage.vue      # 办公室管理
    │   ├── AdminAvailability.vue      # 空闲时间查看
    │   ├── AdminLeave.vue             # 请假审批
    │   ├── AdminMessage.vue           # 消息通知
    │   ├── AdminStatistics.vue        # 值班统计
    │   ├── AdminUser.vue              # 用户管理
    │   └── AdminSemester.vue          # 学期调整
    └── user/           # 用户页面
        ├── UserHome.vue               # 首页
        ├── DormitorySchedule.vue      # 宿舍值班表
        ├── OfficeSchedule.vue         # 办公室值班表
        ├── UserLeave.vue              # 请假申请
        ├── UserMessage.vue            # 消息通知
        └── UserAvailability.vue       # 空闲时间设置
```

## 开发

```bash
npm install        # 安装依赖
npm run dev        # 启动开发服务器（默认 http://localhost:5173）
npm run build      # 生产构建
npm run lint       # 代码检查
npm run type-check # TypeScript 类型检查
```

开发服务器默认代理 `/api` 到 `http://localhost:8080`。
