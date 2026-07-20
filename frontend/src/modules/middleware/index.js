import { createJobModule } from '../../shared/jobs/createJobModule.js'
import ModuleEntry from './ModuleEntry.vue'
import CommandsPage from './pages/CommandsPage.vue'

const defaultApiBaseUrl = import.meta.env?.VITE_MIDDLEWARE_API_BASE_URL || '/api'

const definition = {
  id: 'middleware',
  name: '中间件岗位',
  shortName: '中间件',
  englishName: 'Middleware Operations',
  description: '集中维护中间件软件、运维命令、知识与诊断能力。',
  modulePath: '/modules/middleware',
  apiConfigKey: 'VITE_MIDDLEWARE_API_BASE_URL',
  defaultApiBaseUrl,
  entryComponent: ModuleEntry,
  features: [{
    id: 'commands',
    name: '常用命令',
    description: '查询、查看和复制中间件运维命令。',
    icon: '令',
    component: CommandsPage,
    getProps: ({ context, job }) => ({
      auth: context.auth,
      isSysAdmin: context.isSysAdmin,
      managedCategory: context.managedCategory,
      softwareTypes: context.softwareTypes,
      moduleRequest: job.request,
      notify: context.notify,
      confirm: context.confirm
    })
  }]
}

export function createMiddlewareModule(options) {
  return createJobModule(definition, options)
}

export const middlewareModule = createMiddlewareModule()
