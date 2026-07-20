import { middlewareModule } from './middleware/index.js'
import { databaseModule } from './database/index.js'
import { hostModule } from './host/index.js'
import { networkModule } from './network/index.js'
import { networkSecurityModule } from './network-security/index.js'

export const jobModules = [middlewareModule, databaseModule, hostModule, networkModule, networkSecurityModule]

export function getJobModule(jobId) {
  return jobModules.find(({ id }) => id === jobId) || null
}
