/**
 * 通知管理 composable
 * 统一的 toast 通知，替代各组件中的 alert() 和分散的 notice 逻辑
 */
import { ref } from 'vue'

const notice = ref('')
const confirmDialog = ref(null)

export function useNotify() {
  function notify(message, type = 'info') {
    notice.value = { message, type }
    const duration = type === 'error' ? 5000 : 3000
    window.setTimeout(() => {
      if (notice.value?.message === message) notice.value = ''
    }, duration)
  }

  function confirm(message, onConfirm) {
    confirmDialog.value = { message, onConfirm }
  }

  function handleConfirm() {
    if (confirmDialog.value?.onConfirm) confirmDialog.value.onConfirm()
    confirmDialog.value = null
  }

  function cancelConfirm() {
    confirmDialog.value = null
  }

  return { notice, notify, confirmDialog, confirm, handleConfirm, cancelConfirm }
}
