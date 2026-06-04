/**
 * 管理后台状态和逻辑 composable
 * 从 App.vue 提取，包含文件管理、类型管理、标准管理、用户管理等
 */
import { reactive, ref, computed } from 'vue'
import { request } from '../api'

export function useAdmin(auth, notify, confirm) {
  // ── 管理后台状态 ──
  const adminSection = ref('files')
  const showPassword = ref(false)
  const showImport = ref(false)
  const importing = ref(false)
  const importResult = ref(null)
  const showImportResultDialog = ref(false)
  const editing = ref(false)
  const uploading = ref(false)
  const uploadProgress = ref(0)
  const deleteTarget = ref(null)
  const deletingRelease = ref(false)

  // 类型管理
  const showTypeDialog = ref(false)
  const showCategoryDialog = ref(false)
  const softwareCategories = ref([])
  const softwareTypes = ref([])

  // 标准管理
  const showStandardDialog = ref(false)
  const showParameterDialog = ref(false)
  const showParamImportDialog = ref(false)
  const paramImporting = ref(false)
  const paramImportResult = ref(null)
  const paramImportFile = ref(null)
  const allParameterStandards = ref([])
  const standardDocuments = ref([])
  const standardParameters = ref([])
  const selectedStandard = ref(null)

  // 审核管理
  const selectedReview = ref(null)
  const selectedReviewDiff = ref('')
  const reviewComment = ref('')
  const allReviews = ref([])
  const showRevisionModal = ref(false)
  const revisionList = ref([])
  const revisionDocTitle = ref('')

  // 用户管理
  const showUserDialog = ref(false)
  const showRoleDialog = ref(false)
  const userFormTarget = ref(null)
  const userList = ref([])
  const allRoles = ref([])

  // 系统设置
  const systemSettings = reactive({ 'knowledge-enabled': 'true', 'diagnostics-enabled': 'true' })

  // 筛选器
  const adminFilters = reactive({ keyword: '', platform: '', published: '', page: 0, size: 10 })
  const typeFilters = reactive({ category: '', name: '', page: 0, size: 10 })
  const standardFilters = reactive({ keyword: '', category: '', software: '', status: '', page: 0, size: 10 })
  const parameterFilters = reactive({ page: 0, size: 10 })
  const maintenanceDocumentFilters = reactive({ keyword: '', documentType: '', status: '', page: 0, size: 10 })
  const reviewFilters = reactive({ status: '' })
  const reviewPage = reactive({ page: 0, size: 10 })

  // 分页
  const adminPage = reactive(emptyPage(10))
  const typePage = reactive(emptyPage(10))
  const standardPage = reactive(emptyPage(10))
  const parameterPage = reactive(emptyPage(10))
  const maintenanceDocumentPage = reactive(emptyPage(10))
  const reviewListPage = reactive(emptyPage(10))

  // 表单
  const releaseForm = reactive(defaultReleaseForm())
  const importForm = reactive(defaultImportForm())
  const passwordForm = reactive({ currentPassword: '', newPassword: '', confirmPassword: '' })
  const categoryForm = reactive({ name: '' })
  const typeForm = reactive(defaultTypeForm())
  const standardForm = reactive(defaultStandardForm())
  const parameterForm = reactive(defaultParameterForm())
  const userForm = reactive({ username: '', displayName: '', password: '', role: '开发经理' })

  // ── 辅助函数 ──
  function emptyPage(size) { return { content: [], page: 0, size, totalElements: 0, totalPages: 0, first: true, last: true } }
  function defaultReleaseForm() { return { id: null, category: '', softwareTypeId: '', version: '', platform: '', releasedAt: '', published: false, standardDocumentId: null, standardPackage: false, parameterStandardId: null, description: '', file: null, originalFileName: '' } }
  function defaultImportForm() { return { sourceDirectory: '', category: '', softwareTypeId: '', platform: '', recursive: true, published: false, description: '' } }
  function defaultTypeForm() { return { id: null, category: '', name: '', description: '', active: true } }
  function defaultStandardForm() { return { id: null, category: '', softwareTypeId: '', softwareVersion: '', code: '', summary: '', documentType: 'STANDARD' } }
  function defaultParameterForm() { return { id: null, code: '', name: '', value: '', category: '', description: '', active: true, deploymentStandard: false } }
  function applyPage(target, source) { Object.assign(target, source) }

  // ── 加载函数 ──
  async function loadAdmin() {
    const pub = adminFilters.published !== '' ? `&published=${adminFilters.published}` : ''
    const query = `keyword=${encodeURIComponent(adminFilters.keyword)}&platform=${encodeURIComponent(adminFilters.platform)}&page=${adminFilters.page}&size=${adminFilters.size}${pub}`
    applyPage(adminPage, await request(`/api/admin/releases?${query}`))
  }

  async function loadSoftwareTypes() {
    softwareTypes.value = await request('/api/admin/software-types')
    if (typeFilters.page >= typePage.value.totalPages) typeFilters.page = Math.max(typePage.value.totalPages - 1, 0)
  }

  async function loadSoftwareCategories() { softwareCategories.value = await request('/api/admin/software-type-categories') }
  async function loadSoftwareMetadata() { await loadSoftwareCategories(); await loadSoftwareTypes() }

  async function loadStandardDocuments() {
    const data = await request('/api/admin/standard-documents?size=1000')
    const list = Array.isArray(data) ? data : (data?.content ?? [])
    standardDocuments.value = list
    if (selectedStandard.value) selectedStandard.value = standardDocuments.value.find(d => d.id === selectedStandard.value.id) || null
    await loadStandardParameters(selectedStandard.value?.id)
  }

  async function loadAllParameterStandards() {
    try {
      const data = await request('/api/admin/parameter-standards')
      const list = Array.isArray(data) ? data : (data?.content ?? [])
      allParameterStandards.value = list.filter(d => d.status === 'PUBLISHED')
    } catch { allParameterStandards.value = [] }
  }

  async function loadStandardParameters(targetId) {
    if (!targetId) { standardParameters.value = []; return }
    standardParameters.value = await request(`/api/admin/standard-parameters?standardDocumentId=${encodeURIComponent(targetId)}`)
  }

  async function loadSystemSettings() {
    try { const data = await request('/api/admin/settings'); if (data) Object.assign(systemSettings, data) } catch {}
  }

  async function saveSystemSettings() {
    try { await request('/api/admin/settings', { method: 'PUT', body: systemSettings }); notify('设置已保存', 'success') } catch { notify('保存失败', 'error') }
  }

  async function loadUsers() { try { userList.value = await request('/api/admin/users') } catch { userList.value = [] } }
  async function loadRoles() { try { allRoles.value = await request('/api/admin/users/roles') } catch { allRoles.value = [] } }

  // ── 密码 ──
  async function changePassword() {
    if (passwordForm.newPassword !== passwordForm.confirmPassword) { notify('两次密码不一致', 'error'); return }
    if (passwordForm.newPassword.length < 8) { notify('密码至少8位', 'error'); return }
    try {
      await request('/api/admin/account/password', { method: 'PUT', body: { currentPassword: passwordForm.currentPassword, newPassword: passwordForm.newPassword } })
      notify('密码已修改', 'success'); showPassword.value = false; Object.assign(passwordForm, { currentPassword: '', newPassword: '', confirmPassword: '' })
    } catch (e) { notify(e.message || '修改失败', 'error') }
  }

  // ── 资源 CRUD ──
  function startCreate() { editing.value = true; Object.assign(releaseForm, defaultReleaseForm()) }
  function startEdit(release) { editing.value = true; Object.assign(releaseForm, { id: release.id, category: release.softwareTypeCategory || '', softwareTypeId: release.softwareTypeId || '', version: release.version || '', platform: release.platform || '', releasedAt: release.releasedAt || '', published: release.published, standardDocumentId: release.standardDocumentId || null, standardPackage: release.standardPackage || false, parameterStandardId: release.parameterStandardId || null, description: release.description || '', file: null, originalFileName: release.originalFileName || '' }) }
  function cancelEdit() { editing.value = false; Object.assign(releaseForm, defaultReleaseForm()) }
  function handleReleaseFileChange(e) { releaseForm.file = e.target.files[0] || null }

  async function saveRelease() {
    uploading.value = true; uploadProgress.value = 0
    try {
      const fd = new FormData()
      if (releaseForm.file) fd.append('file', releaseForm.file)
      fd.append('softwareTypeId', releaseForm.softwareTypeId)
      fd.append('version', releaseForm.version)
      if (releaseForm.platform) fd.append('platform', releaseForm.platform)
      if (releaseForm.releasedAt) fd.append('releasedAt', releaseForm.releasedAt)
      fd.append('published', releaseForm.published)
      if (releaseForm.standardDocumentId) fd.append('standardDocumentId', releaseForm.standardDocumentId)
      fd.append('standardPackage', releaseForm.standardPackage)
      if (releaseForm.parameterStandardId) fd.append('parameterStandardId', releaseForm.parameterStandardId)
      if (releaseForm.description) fd.append('description', releaseForm.description)
      const url = releaseForm.id ? `/api/admin/releases/${releaseForm.id}` : '/api/admin/releases'
      const method = releaseForm.id ? 'PUT' : 'POST'
      await request(url, { method, body: fd })
      notify(releaseForm.id ? '已更新' : '已创建', 'success')
      cancelEdit(); await loadAdmin()
    } catch (e) { notify(e.message || '保存失败', 'error') }
    finally { uploading.value = false }
  }

  async function togglePublish(release) {
    try {
      await request(`/api/admin/releases/${release.id}/publish`, { method: 'PUT', body: { published: !release.published } })
      notify(release.published ? '已下架' : '已发布', 'success'); await loadAdmin()
    } catch (e) { notify(e.message || '操作失败', 'error') }
  }

  function openDeleteReleaseDialog(r) { deleteTarget.value = r }
  function closeDeleteReleaseDialog() { deleteTarget.value = null }
  async function confirmDeleteRelease() {
    if (!deleteTarget.value) return; deletingRelease.value = true
    try { await request(`/api/admin/releases/${deleteTarget.value.id}`, { method: 'DELETE' }); notify('已删除', 'success'); closeDeleteReleaseDialog(); await loadAdmin() }
    catch (e) { notify(e.message || '删除失败', 'error') }
    finally { deletingRelease.value = false }
  }

  // ── 批量导入 ──
  function openImportPage() { showImport.value = true; Object.assign(importForm, defaultImportForm()) }
  function closeImportPage() { showImport.value = false }
  async function submitImport() {
    importing.value = true
    try {
      const result = await request('/api/admin/releases/import', { method: 'POST', body: importForm })
      importResult.value = result; showImportResultDialog.value = true; closeImportPage(); await loadAdmin()
    } catch (e) { notify(e.message || '导入失败', 'error') }
    finally { importing.value = false }
  }

  // ── 类型管理 ──
  function openCreateCategoryDialog() { categoryForm.name = ''; showCategoryDialog.value = true }
  function closeCategoryDialog() { showCategoryDialog.value = false }
  async function saveCategory() {
    if (!categoryForm.name.trim()) { notify('分类名不能为空', 'error'); return }
    try { await request('/api/admin/software-type-categories', { method: 'POST', body: { name: categoryForm.name.trim() } }); notify('已创建', 'success'); closeCategoryDialog(); await loadSoftwareMetadata() }
    catch (e) { notify(e.message || '创建失败', 'error') }
  }
  function openCreateTypeDialog() { Object.assign(typeForm, defaultTypeForm()); showTypeDialog.value = true }
  function closeTypeDialog() { showTypeDialog.value = false }
  async function saveType() {
    if (!typeForm.category || !typeForm.name.trim()) { notify('分类和名称必填', 'error'); return }
    try {
      const url = typeForm.id ? `/api/admin/software-types/${typeForm.id}` : '/api/admin/software-types'
      await request(url, { method: typeForm.id ? 'PUT' : 'POST', body: typeForm })
      notify('已保存', 'success'); closeTypeDialog(); await loadSoftwareMetadata()
    } catch (e) { notify(e.message || '保存失败', 'error') }
  }

  // ── 标准管理 ──
  function openCreateStandardDialog(dt) { Object.assign(standardForm, defaultStandardForm()); if (dt) standardForm.documentType = dt; showStandardDialog.value = true }
  function closeStandardDialog() { showStandardDialog.value = false }
  async function saveStandard() {
    if (!standardForm.softwareTypeId) { notify('请选择软件', 'error'); return }
    try {
      const url = standardForm.id ? `/api/admin/parameter-standards/${standardForm.id}` : '/api/admin/parameter-standards'
      await request(url, { method: standardForm.id ? 'PUT' : 'POST', body: standardForm })
      notify('已保存', 'success'); closeStandardDialog(); await loadStandardDocuments()
    } catch (e) { notify(e.message || '保存失败', 'error') }
  }

  // ── 参数管理 ──
  function openCreateParameterDialog() { Object.assign(parameterForm, defaultParameterForm()); showParameterDialog.value = true }
  function closeParameterDialog() { showParameterDialog.value = false }
  async function saveParameter() {
    if (!parameterForm.code.trim() || !parameterForm.name.trim()) { notify('编码和名称必填', 'error'); return }
    try {
      const url = parameterForm.id ? `/api/admin/standard-parameters/${parameterForm.id}` : '/api/admin/standard-parameters'
      await request(url, { method: parameterForm.id ? 'PUT' : 'POST', body: { ...parameterForm, standardDocumentId: selectedStandard.value?.id } })
      notify('已保存', 'success'); closeParameterDialog(); await loadStandardParameters()
    } catch (e) { notify(e.message || '保存失败', 'error') }
  }
  function handleParamImportFileChange(e) { paramImportFile.value = e.target.files[0] || null }
  async function importParameters() {
    if (!paramImportFile.value) { notify('请选择文件', 'error'); return }
    paramImporting.value = true
    try {
      const fd = new FormData(); fd.append('file', paramImportFile.value)
      if (selectedStandard.value) fd.append('standardDocumentId', selectedStandard.value.id)
      const result = await request('/api/admin/standard-parameters/import', { method: 'POST', body: fd })
      paramImportResult.value = result; await loadStandardParameters(); notify('导入完成', 'success')
    } catch (e) { notify(e.message || '导入失败', 'error') }
    finally { paramImporting.value = false }
  }
  async function downloadParameterTemplate() {
    try { window.open('/api/admin/standard-parameters/template', '_blank') } catch { notify('下载失败', 'error') }
  }

  // ── 审核管理 ──
  async function loadReviews() {
    const q = reviewFilters.status ? `status=${reviewFilters.status}` : ''
    const data = await request(`/api/admin/reviews?${q}&page=${reviewPage.page}&size=${reviewPage.size}`)
    applyPage(reviewListPage, data); allReviews.value = data?.content || []
  }
  async function openReviewDetail(record) {
    selectedReview.value = record; selectedReviewDiff.value = ''; reviewComment.value = ''
    try { selectedReviewDiff.value = await request(`/api/admin/reviews/${record.id}/diff`) } catch {}
  }
  function closeReviewDetail() { selectedReview.value = null }
  async function reviewApprove(record) {
    try { await request(`/api/admin/reviews/${record.id}/approve`, { method: 'PUT', body: { comment: reviewComment.value } }); notify('已通过', 'success'); closeReviewDetail(); await loadReviews() }
    catch (e) { notify(e.message || '操作失败', 'error') }
  }
  async function reviewReject(record) {
    try { await request(`/api/admin/reviews/${record.id}/reject`, { method: 'PUT', body: { comment: reviewComment.value } }); notify('已驳回', 'success'); closeReviewDetail(); await loadReviews() }
    catch (e) { notify(e.message || '操作失败', 'error') }
  }

  // ── 修订历史 ──
  async function openRevisionHistory(doc, documentType) {
    revisionDocTitle.value = doc.title || doc.name || ''
    try { revisionList.value = await request(`/api/admin/revisions?documentId=${doc.id}&documentType=${documentType || 'STANDARD_DOCUMENT'}`) } catch { revisionList.value = [] }
    showRevisionModal.value = true
  }
  function closeRevisionModal() { showRevisionModal.value = false }

  // ── 用户管理 ──
  function openCreateUserDialog() { Object.assign(userForm, { username: '', displayName: '', password: '', role: '开发经理' }); userFormTarget.value = null; showUserDialog.value = true }
  function closeUserDialog() { showUserDialog.value = false }
  async function createUser() {
    if (!userForm.username.trim() || !userForm.password) { notify('账号和密码必填', 'error'); return }
    try { await request('/api/admin/users', { method: 'POST', body: userForm }); notify('已创建', 'success'); closeUserDialog(); await loadUsers() }
    catch (e) { notify(e.message || '创建失败', 'error') }
  }
  function openRoleDialog(user) { userFormTarget.value = user; userForm.role = user.role; showRoleDialog.value = true }
  function closeRoleDialog() { showRoleDialog.value = false }
  async function changeUserRole() {
    try { await request(`/api/admin/users/${userFormTarget.value.id}/role`, { method: 'PUT', body: { role: userForm.role } }); notify('已修改', 'success'); closeRoleDialog(); await loadUsers() }
    catch (e) { notify(e.message || '修改失败', 'error') }
  }
  async function deleteUserAccount(user) {
    confirm(`确定删除用户 ${user.username}？`, async () => {
      try { await request(`/api/admin/users/${user.id}`, { method: 'DELETE' }); notify('已删除', 'success'); await loadUsers() }
      catch (e) { notify(e.message || '删除失败', 'error') }
    })
  }

  function switchAdminSection(s) { adminSection.value = s }
  function changeAdminPage(page) { adminFilters.page = page; loadAdmin() }

  return {
    // State
    adminSection, showPassword, showImport, importing, importResult, showImportResultDialog,
    editing, uploading, uploadProgress, deleteTarget, deletingRelease,
    showTypeDialog, showCategoryDialog, softwareCategories, softwareTypes,
    showStandardDialog, showParameterDialog, showParamImportDialog, paramImporting, paramImportResult, paramImportFile,
    allParameterStandards, standardDocuments, standardParameters, selectedStandard,
    selectedReview, selectedReviewDiff, reviewComment, allReviews, showRevisionModal, revisionList, revisionDocTitle,
    showUserDialog, showRoleDialog, userFormTarget, userList, allRoles, systemSettings,
    adminFilters, typeFilters, standardFilters, parameterFilters, maintenanceDocumentFilters, reviewFilters, reviewPage,
    adminPage, typePage, standardPage, parameterPage, maintenanceDocumentPage, reviewListPage,
    releaseForm, importForm, passwordForm, categoryForm, typeForm, standardForm, parameterForm, userForm,
    // Functions
    loadAdmin, loadSoftwareTypes, loadSoftwareCategories, loadSoftwareMetadata,
    loadStandardDocuments, loadAllParameterStandards, loadStandardParameters, loadSystemSettings, saveSystemSettings,
    loadUsers, loadRoles, changePassword,
    startCreate, startEdit, cancelEdit, handleReleaseFileChange, saveRelease, togglePublish,
    openDeleteReleaseDialog, closeDeleteReleaseDialog, confirmDeleteRelease,
    openImportPage, closeImportPage, submitImport,
    openCreateCategoryDialog, closeCategoryDialog, saveCategory,
    openCreateTypeDialog, closeTypeDialog, saveType,
    openCreateStandardDialog, closeStandardDialog, saveStandard,
    openCreateParameterDialog, closeParameterDialog, saveParameter, handleParamImportFileChange, importParameters, downloadParameterTemplate,
    loadReviews, openReviewDetail, closeReviewDetail, reviewApprove, reviewReject,
    openRevisionHistory, closeRevisionModal,
    openCreateUserDialog, closeUserDialog, createUser, openRoleDialog, closeRoleDialog, changeUserRole, deleteUserAccount,
    switchAdminSection, changeAdminPage
  }
}
