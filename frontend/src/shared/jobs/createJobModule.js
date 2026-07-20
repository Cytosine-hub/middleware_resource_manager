import { createModuleApi } from '../api/createModuleApi.js'

export function createJobModule(definition, options = {}) {
  const apiBaseUrl = options.apiBaseUrl || definition.defaultApiBaseUrl || '/api'
  const features = definition.features || []

  return {
    ...definition,
    features,
    apiBaseUrl,
    request: createModuleApi(apiBaseUrl),
    resolveFeature(featureId) {
      return features.find(({ id }) => id === featureId) || null
    },
    createModule(moduleOptions) {
      return createJobModule(definition, moduleOptions)
    }
  }
}
