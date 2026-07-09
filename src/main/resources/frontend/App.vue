<script>
import { ref, computed, nextTick, onMounted, onUnmounted, watch } from 'vue'
import { marked } from 'marked'
import { HalfCircleSpinner } from 'epic-spinners'
import appTemplate from './templates/app.html?raw'

export default {
  components: {
    HalfCircleSpinner
  },
  template: appTemplate,
  setup() {
    const messages = ref([
      { role: 'system', content: 'You are Boris, a friendly, professional, and very capable intelligent assistant.' }
    ])

    const sessionId = ref(localStorage.getItem('boris_session_id') || null)

    const generateSessionId = () => {
      const newId = crypto.randomUUID()
      localStorage.setItem('boris_session_id', newId)
      sessionId.value = newId
      return newId
    }

    const getSessionId = () => {
      if (!sessionId.value) {
        return generateSessionId()
      }
      return sessionId.value
    }

    const defaultSystemMessage = 'You are Boris, a friendly, professional, and very capable intelligent assistant.'

    const isError = (msg) => {
      return msg.content.includes('exceed_context_size_error') || msg.content.includes('Error: exceed_context_size_error')
    }

    const userInput = ref('')
    const isTyping = ref(false)
    const isClearingChat = ref(false)
    const chatEnabled = computed(() => selectedModel.value !== null && !loadingModelId.value && !modelsBusy.value && !isClearingChat.value)
    const chatContainer = ref(null)
    const currentBotMessage = ref('')
    const isOnline = ref(navigator.onLine)
    const gpuInfo = ref({ name: 'N/A', vram: 'N/A' })
    const gpuMemoryInfo = ref({ totalVram: 'N/A', usedVram: 'N/A', remainingVram: 'N/A' })
    const ramGpuUsage = ref(0)
    const originalRemainingVram = ref('N/A')

    // Token tracking
    const inputTokens = ref(0)
    const outputTokens = ref(0)
    const currentAbortController = ref(null)
    const isAgentTask = ref(false)

    // Map para persistir tokens de cada mensaje assistant a traves de recargas del historial
    // key: primeros 80 chars del content, value: { inputTokens, outputTokens, contextSize }
    const messageTokenMap = ref(new Map())
    const tokenMapKey = (content) => content ? content.substring(0, 80) : ''

    const availableModels = ref([])
    const loadedModels = ref([])
    const selectedModel = ref(null)
    const selectedMenu = ref('chat')
    const pendingChatRedirect = ref(false)
    const loadingModelId = ref(null)
    const unloadingModelId = ref(null)
    const modelsBusy = ref(false)
    const uiMessage = ref('')
    const debugLogs = ref([])

    const settings = ref({
      llamaServerPath: '',
      llamaServerCore: '',
      modelsDir: '',
      workspacePrefix: '',
      userHome: ''
    })

    const llamaServerInstalled = ref(false)
    const llamaServerDownloading = ref(false)

    const addDebugLog = (type, label, data) => {
      const id = Date.now() + Math.random()
      debugLogs.value.unshift({
        id,
        type,
        label,
        content: typeof data === 'string' ? data : JSON.stringify(data, null, 2),
        expanded: false,
        timestamp: new Date().toLocaleTimeString()
      })
      if (debugLogs.value.length > 10) debugLogs.value.pop()
    }

    const modelContextSizes = ref(new Map())

    const parseMemoryToBytes = (memStr) => {
      if (!memStr || memStr === 'N/A') return 0
      const match = memStr.match(/(\d+\.?\d*)\s*([GMK]B)/i)
      if (!match) return 0
      const value = parseFloat(match[1])
      const unit = match[2].toUpperCase()
      const multiplier = {
        'GB': 1024 * 1024 * 1024,
        'MB': 1024 * 1024,
        'KB': 1024
      }[unit] || 1
      return value * multiplier
    }

    const getRecommendedSettings = (modelName, sizeBytes) => {
      const name = (modelName || '').toLowerCase()
      const remainingVram = parseMemoryToBytes(gpuMemoryInfo.value.remainingVram)

      const rec = {
        contextSize: 8192,
        threads: 8,
        gpuLayers: 20,
        batchSize: 512,
        temperature: 0.7,
        maxTokens: 2048
      }

      if (name.includes('3.1')) rec.contextSize = 32768
      else if (name.includes('llama-3')) rec.contextSize = 8192
      else if (name.includes('mistral') || name.includes('mixtral') || name.includes('nemo') || name.includes('codestral')) rec.contextSize = 32768
      else if (name.includes('phi-3')) rec.contextSize = 128000
      else if (name.includes('gemma-2')) rec.contextSize = 8192
      else if (name.includes('deepseek')) rec.contextSize = 16384

      if (sizeBytes > 0 && remainingVram > 0) {
        if (sizeBytes * 1.2 < remainingVram) {
          rec.gpuLayers = 99
        } else if (sizeBytes * 0.6 < remainingVram) {
          rec.gpuLayers = 32
        }
      }

      return rec
    }

    const normalizeModel = (model) => {
      const status = model?.status?.value || model?.status || 'unknown'
      const id = model?.id || model?.name || 'unknown-model'
      const sizeBytes = model?.sizeBytes || 0

      const rec = getRecommendedSettings(id, sizeBytes)

      return {
        id,
        baseId: model?.baseId || id,
        instance: Number(model?.instance || 1),
        object: model?.object || 'model',
        ownedBy: model?.owned_by || model?.ownedBy || 'local',
        status,
        configExpanded: false,
        hasModifiedConfig: false,
        contextSize: model?.contextSize || rec.contextSize,
        threads: model?.threads || rec.threads,
        gpuLayers: model?.gpuLayers ?? rec.gpuLayers,
        batchSize: model?.batchSize || rec.batchSize,
        temperature: model?.temperature || rec.temperature,
        maxTokens: model?.maxTokens || model?.max_tokens || rec.maxTokens,
        size: model?.size || 'N/A',
        sizeBytes,
        raw: model
      }
    }

    const toggleModelConfig = (model) => {
      model.configExpanded = !model.configExpanded
      if (model.configExpanded && !model.hasModifiedConfig) {
        applyRecommendedSettings(model)
      }
    }

    const applyRecommendedSettings = (model) => {
      const rec = getRecommendedSettings(model.id, model.sizeBytes)
      model.contextSize = rec.contextSize
      model.threads = rec.threads
      model.gpuLayers = rec.gpuLayers
      model.batchSize = rec.batchSize
      model.temperature = rec.temperature
      model.maxTokens = rec.maxTokens
      model.hasModifiedConfig = true
      uiMessage.value = `Applied recommended settings for ${model.id}`
      setTimeout(() => { if (uiMessage.value.includes('Applied recommended')) uiMessage.value = '' }, 3000)
    }

    const buildNextLoadId = (baseId) => {
      const sameBaseModels = loadedModels.value.filter(model => model.baseId === baseId)
      const nextInstance = sameBaseModels.length + 1
      return nextInstance === 1 ? baseId : `${baseId}:${nextInstance}`
    }

    const countLoadedInstances = (baseId) => {
      if (!baseId) return 0
      const normalizedId = normalizeModelId(baseId)
      return loadedModels.value.filter(model => normalizeModelId(model.baseId) === normalizedId).length
    }

    const normalizeModelId = (id) => {
      if (!id) return ''
      return id.replace(/\.gguf$/i, '')
    }

    const currentModelLabel = computed(() => {
      if (!selectedModel.value) return 'No model selected'
      const loaded = loadedModels.value.find(model => model.id === selectedModel.value)
      if (loaded) return loaded.id
      const available = availableModels.value.find(model => model.id === selectedModel.value)
      return available ? available.id : selectedModel.value
    })

    const modelContextSize = computed(() => {
      if (!selectedModel.value) return 8096
      if (modelContextSizes.value.has(selectedModel.value)) {
        return modelContextSizes.value.get(selectedModel.value)
      }
      const loaded = loadedModels.value.find(model => model.id === selectedModel.value)
      if (loaded && loaded.contextSize) return loaded.contextSize
      const available = availableModels.value.find(model => model.id === selectedModel.value)
      if (available && available.contextSize) return available.contextSize
      return 8096
    })

    const remainingTokens = computed(() => {
      return modelContextSize.value - (inputTokens.value + outputTokens.value)
    })

    const statusEventSource = ref(null)

    const initStatusStream = () => {
      if (statusEventSource.value) return

      const source = new EventSource('/api/status/stream')

      source.onmessage = (event) => {
        if (event.data === 'UP') {
          isOnline.value = true
        } else if (event.data === 'DOWN') {
          isOnline.value = false
        }
      }

      source.onerror = () => {
        isOnline.value = false
        source.close()
        statusEventSource.value = null
      }

      statusEventSource.value = source
    }

    const api = async (url, options = {}) => {
      try {
        const response = await fetch(url, options)
        isOnline.value = true
        return response
      } catch (error) {
        if (error.name !== 'AbortError') {
          isOnline.value = false
        }
        throw error
      }
    }

    const fetchHardware = async () => {
      try {
        const response = await api('/api/hardware')
        if (response.ok) {
          const res = await response.json()
          gpuInfo.value = res.data || res
        }
      } catch (error) {
        console.error('Failed to fetch hardware info:', error)
      }
    }

    const fetchGpuMemoryInfo = async () => {
      try {
        const response = await api('/api/hardware/memory')
        if (response.ok) {
          const res = await response.json()
          gpuMemoryInfo.value = res.data || res
        }
      } catch (error) {
        console.error('Failed to fetch GPU memory info:', error)
      }
    }

    const fetchAvailableModels = async () => {
      const response = await api('/api/models/with-sizes')
      if (!response.ok) throw new Error('Could not fetch available models')
      const res = await response.json()
      const data = res.data || res
      const list = Array.isArray(data?.data) ? data.data : (Array.isArray(data) ? data : [])
      availableModels.value = list
        .filter(model => model?.id && !model.id.startsWith('ggml-vocab-'))
        .map(normalizeModel)
    }

    const fetchLoadedModels = async () => {
      const response = await api('/api/models/loaded')
      if (!response.ok) throw new Error('Could not fetch loaded models')
      const res = await response.json()
      const data = res.data || res
      const list = Array.isArray(data?.data) ? data.data : (Array.isArray(data) ? data : [])
      loadedModels.value = list
        .filter(model => model?.id && !model.id.startsWith('ggml-vocab-'))
        .map(normalizeModel)
    }

    const applyConversationMessages = (history = []) => {
      const normalized = Array.isArray(history)
        ? history
            .filter(msg => msg?.role && typeof msg?.content === 'string')
            .map(msg => {
              const base = { role: msg.role, content: msg.content, timestamp: msg.timestamp }
              // Reinyectar tokens guardados en el Map para cada mensaje assistant
              if (msg.role === 'assistant') {
                const saved = messageTokenMap.value.get(tokenMapKey(msg.content))
                if (saved) {
                  base.inputTokens = saved.inputTokens
                  base.outputTokens = saved.outputTokens
                  base.contextSize = saved.contextSize
                }
              }
              return base
            })
        : []

      messages.value = normalized.length > 0
        ? normalized
        : [{ role: 'system', content: defaultSystemMessage }]
    }

    const loadConversationHistory = async (targetSessionId = getSessionId()) => {
      try {
        const response = await api(`/boris/v1/conversations/${encodeURIComponent(targetSessionId)}`)
        if (!response.ok) throw new Error('Could not load conversation history')
        const result = await response.json()
        const session = result.data || {}
        applyConversationMessages(session.messages || [])
      } catch (error) {
        console.error('Failed to load conversation history:', error)
        applyConversationMessages([])
      }
    }

    const syncSelectedModel = () => {
      if (!selectedModel.value && loadedModels.value.length > 0) {
        selectedModel.value = loadedModels.value[0].id
        return
      }

      if (!selectedModel.value && availableModels.value.length > 0) {
        selectedModel.value = availableModels.value[0].id
        return
      }

      if (selectedModel.value) {
        const existsInLoaded = loadedModels.value.some(model => model.id === selectedModel.value)
        const existsInAvailable = availableModels.value.some(model => model.id === selectedModel.value)
        if (!existsInLoaded && !existsInAvailable) {
          selectedModel.value = loadedModels.value[0]?.id || availableModels.value[0]?.id || null
        }
      }
    }

    const refreshModels = async () => {
      modelsBusy.value = true
      uiMessage.value = ''
      try {
        await Promise.all([fetchAvailableModels(), fetchLoadedModels(), fetchGpuMemoryInfo()])
        syncSelectedModel()
      } catch (error) {
        console.error('Failed to refresh models:', error)
        uiMessage.value = error.message || 'Failed to refresh models'
      } finally {
        modelsBusy.value = false
      }
    }

    const scrollToBottom = async () => {
      await nextTick()
      if (chatContainer.value) {
        chatContainer.value.scrollTop = chatContainer.value.scrollHeight
      }
    }

    const setPrompt = (text) => {
      userInput.value = text
      sendMessage()
    }

    const loadModelWithConfig = async (modelId) => {
      console.log('🚀 [LOAD START] modelId:', modelId, 'loadingModelId before:', loadingModelId.value)
      const model = availableModels.value.find(item => item.id === modelId)
      if (!model) {
        console.warn('⚠️ [LOAD] Model not found in availableModels:', modelId)
        return
      }

      if (loadedModels.value.length > 0) {
        uiMessage.value = 'You must explicitly eject the current model before loading a new one.'
        setTimeout(() => { if (uiMessage.value.includes('must explicitly eject')) uiMessage.value = '' }, 4000)
        return
      }

      const resolvedId = buildNextLoadId(model.id)
      loadingModelId.value = modelId
      console.log('📦 [LOAD] loadingModelId set to:', modelId, 'resolvedId:', resolvedId)
      uiMessage.value = `Starting load for ${model.id}...`
      selectedMenu.value = 'chat'

      try {
        const contextSize = model.contextSize || 8000
        console.log('📦 [LOAD] About to call POST /boris/v1/models/load with timeout 2min')
        const timeoutPromise = new Promise((_, reject) => {
          setTimeout(() => reject(new Error('Load request timed out after 2 minutes')), 120000)
        })
        const response = await Promise.race([
          api('/boris/v1/models/load', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
              id: resolvedId,
              model: model.id,
              contextSize: contextSize,
              threads: model.threads || 4,
              gpuLayers: model.gpuLayers ?? 20,
              batchSize: model.batchSize || 64,
              temperature: model.temperature || 0.7,
              maxTokens: model.maxTokens || 1000
            })
          }),
          timeoutPromise
        ])
        console.log('📦 [LOAD] Response received. Status:', response.status, 'ok:', response.ok)

        addDebugLog('request', `POST /boris/v1/models/load (custom)`, { id: resolvedId, model: model.id, contextSize })

        const result = await response.json()
        addDebugLog('response', `Load Model Config Response`, result)
        if (!response.ok || result.status === 'error') {
          throw new Error(result.message || 'Could not load model')
        }

        modelContextSizes.value.set(resolvedId, contextSize)
        messageTokenMap.value.clear()

        model.configExpanded = false
        selectedModel.value = result.data?.id || result.id || resolvedId

        sessionId.value = null
        localStorage.removeItem('boris_session_id')
        messages.value = [
          { role: 'system', content: 'Model loaded successfully and ready. Click the "+" button below to initialize a new conversation.' }
        ]
        userInput.value = ''
        currentBotMessage.value = ''
        inputTokens.value = 0
        outputTokens.value = 0

        console.log('📦 [LOAD] About to refreshModels and fetchGpuMemoryInfo')
        await refreshModels()
        await fetchGpuMemoryInfo()
        uiMessage.value = 'Model loaded successfully'
        setTimeout(() => { if (uiMessage.value === 'Model loaded successfully') uiMessage.value = '' }, 3000)
        console.log('✅ [LOAD SUCCESS] Model loaded, loadingModelId will be cleared in finally')
      } catch (error) {
        console.error('❌ [LOAD ERROR] Failed to load model:', error)
        uiMessage.value = error.message || 'Failed to load model'
      } finally {
        console.log('📦 [LOAD FINALLY] Clearing loadingModelId')
        loadingModelId.value = null
      }
    }

    const unloadModel = async (modelId) => {
      unloadingModelId.value = modelId
      uiMessage.value = ''
      try {
        try {
          await api('/boris/v1/clearmodel', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ sessionId: getSessionId() })
          })
        } catch (clearError) {
          console.warn('Failed to clear model resources before unload:', clearError)
        }

        const response = await api('/boris/v1/models/unload', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ model: modelId })
        })

        addDebugLog('request', `POST /boris/v1/models/unload`, { model: modelId })

        const result = await response.json()
        addDebugLog('response', `Unload Model Response`, result)
        if (!response.ok || result.status === 'error') {
          throw new Error(result.message || 'Could not unload model')
        }

        modelContextSizes.value.delete(modelId)
        messageTokenMap.value.clear()

        await refreshModels()
        await fetchGpuMemoryInfo()

        if (selectedModel.value === modelId) {
          selectedModel.value = loadedModels.value[0]?.id || availableModels.value[0]?.id || null
          messages.value = [{ role: 'system', content: defaultSystemMessage }]
          userInput.value = ''
          currentBotMessage.value = ''
          inputTokens.value = 0
          outputTokens.value = 0
        }

        uiMessage.value = result.message || `Model ${modelId} unloaded successfully`
      } catch (error) {
        console.error('Failed to unload model:', error)
        uiMessage.value = error.message || 'Failed to unload model'
        unloadingModelId.value = null
      }
    }

    const chooseModel = (modelId) => {
      selectedModel.value = modelId
      selectedMenu.value = 'chat'
    }

    watch(selectedMenu, (newMenu) => {
      if (newMenu === 'models') {
        unloadingModelId.value = null
      }
    })

    watch([loadedModels], () => {}, { deep: true })

    watch(gpuMemoryInfo, (newInfo) => {
      if (newInfo.remainingVram !== 'N/A') {
        const match = newInfo.remainingVram.match(/(-?\d+\.?\d*)\s*(GB|MB)?/i)
        if (match) {
          const value = parseFloat(match[1])
          const unit = (match[2] || 'GB').toUpperCase()
          let valueInGB = value
          if (unit === 'MB') valueInGB = value / 1024
          ramGpuUsage.value = valueInGB < 0 ? Math.abs(valueInGB) : 0
        }
      }
    }, { deep: true })

    const newChat = async (isDeep = false) => {
      if (isClearingChat.value || loadingModelId.value) return

      const oldSessionId = sessionId.value
      isClearingChat.value = true
      uiMessage.value = isDeep ? 'Performing deep cleanup...' : 'Starting new chat...'

      try {
        await api('/boris/v1/clearmodel', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ all: isDeep, sessionId: oldSessionId, reinit: true })
        })
      } catch (error) {
        console.error('Failed to perform cleanup:', error)
      }

      sessionId.value = null
      localStorage.removeItem('boris_session_id')
      generateSessionId()
      messageTokenMap.value.clear()

      messages.value = [{ role: 'system', content: defaultSystemMessage }]
      userInput.value = ''
      currentBotMessage.value = ''
      inputTokens.value = 0
      outputTokens.value = 0
      uiMessage.value = isDeep ? 'Deep cleanup complete' : 'New chat ready'

      await refreshModels()
      isClearingChat.value = false
    }

    const stopResponse = async () => {
      if (currentAbortController.value) {
        currentAbortController.value.abort()
        currentAbortController.value = null
        isTyping.value = false
        isAgentTask.value = false

        try {
          await api('/boris/v1/agent/cancel', { method: 'POST' })
        } catch (error) {
          console.warn('Failed to notify backend about cancellation:', error)
        }

        if (currentBotMessage.value) {
          messages.value.push({
            role: 'assistant',
            content: currentBotMessage.value + '\n\n*[Response stopped by user]*',
            inputTokens: inputTokens.value,
            outputTokens: outputTokens.value,
            contextSize: modelContextSize.value
          })
          currentBotMessage.value = ''
        }
        uiMessage.value = 'Response stopped'
        setTimeout(() => uiMessage.value = '', 2000)
      }
    }

    const fetchSettings = async () => {
      try {
        const response = await api('/boris/v1/config/settings')
        if (response.ok) {
          const res = await response.json()
          const data = res.data || res
          settings.value = {
            llamaServerPath: data.llamaServerPath || '',
            llamaServerCore: data.llamaServerCore || '',
            modelsDir: data.modelsDir || '',
            workspacePrefix: data.workspacePrefix || '',
            userHome: data.userHome || ''
          }
        }
      } catch (error) {
        console.error('Failed to fetch settings:', error)
      }
    }

    const saveSettings = async () => {
      uiMessage.value = ''
      try {
        const response = await api('/boris/v1/config/settings', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify(settings.value)
        })
        if (response.ok) {
          uiMessage.value = 'Settings saved successfully'
          setTimeout(() => uiMessage.value = '', 3000)
        } else {
          throw new Error('Failed to save settings')
        }
      } catch (error) {
        console.error('Failed to save settings:', error)
        uiMessage.value = 'Error saving settings: ' + error.message
      }
    }

    const checkLlamaServerStatus = async () => {
      try {
        const response = await api('/boris/v1/llama-server/status')
        if (response.ok) {
          const data = await response.json()
          llamaServerInstalled.value = (data.data?.installed === true) || (data.installed === true)
        }
      } catch (error) {
        console.error('Failed to check llama server status:', error)
      }
    }

    const downloadLlamaServer = async () => {
      if (llamaServerDownloading.value || llamaServerInstalled.value) return
      llamaServerDownloading.value = true
      uiMessage.value = 'Downloading llama server...'
      try {
        const response = await api('/boris/v1/llama-server/download', { method: 'POST' })
        if (response.ok) {
          const data = await response.json()
          if (data.status === 'success') {
            llamaServerInstalled.value = true
            uiMessage.value = 'Llama server downloaded and installed successfully'
            setTimeout(() => window.location.reload(), 1000)
          } else {
            throw new Error(data.message || 'Download failed')
          }
        } else {
          throw new Error('Server returned an error')
        }
      } catch (error) {
        console.error('Failed to download llama server:', error)
        uiMessage.value = 'Error downloading llama server: ' + error.message
      } finally {
        llamaServerDownloading.value = false
        setTimeout(() => uiMessage.value = '', 5000)
      }
    }

    const browsePath = async (field, isFolder = false) => {
      try {
        const currentVal = settings.value[field] || ''
        const endpoint = isFolder ? '/boris/v1/config/browse-folder' : '/boris/v1/config/browse-file'
        const url = `${endpoint}?initialPath=${encodeURIComponent(currentVal)}`
        const response = await fetch(url)
        const data = await response.json()
        if (data.path && !data.path.startsWith('error:')) {
          const userHome = settings.value.userHome || ''
          let path = data.path
          if (userHome && path.startsWith(userHome)) {
            let relative = path.substring(userHome.length)
            if (relative.startsWith('\\') || relative.startsWith('/')) relative = relative.substring(1)
            path = '/' + relative
          }
          settings.value[field] = path
        }
      } catch (err) {
        console.error('Failed to browse path:', err)
      }
    }

    const sendMessage = async () => {
      const text = userInput.value.trim()
      if (!text || isTyping.value) return
      if (!chatEnabled.value) {
        uiMessage.value = loadingModelId.value
          ? 'Model is still loading'
          : isClearingChat.value
            ? 'Please wait, clearing previous chat'
            : 'Chat is not ready yet'
        return
      }
      if (!selectedModel.value) {
        uiMessage.value = 'Select or load a model before sending messages'
        selectedMenu.value = 'models'
        return
      }

      messages.value.push({ role: 'user', content: text })
      userInput.value = ''
      isTyping.value = true
      currentBotMessage.value = ''
      currentAbortController.value = new AbortController()
      inputTokens.value = 0
      outputTokens.value = 0

      await scrollToBottom()

      try {
        isAgentTask.value = true
        const currentSessionId = getSessionId()
        console.log('🔍 [DEBUG] Sending sessionId:', currentSessionId)

        const response = await fetch('/boris/v1/chat/completions', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json', 'Accept': 'text/event-stream' },
          body: JSON.stringify({ sessionId: currentSessionId, instruction: text, model: selectedModel.value }),
          signal: currentAbortController.value.signal
        })

        addDebugLog('request', `POST /boris/v1/chat/completions (Stream)`, { instruction: text })

        if (!response.ok) {
          const errorData = await response.json().catch(() => ({ message: 'Request failed' }))
          addDebugLog('response', `Error`, errorData)
          throw new Error(errorData.message || 'Request failed')
        }

        const reader = response.body.getReader()
        const decoder = new TextDecoder()
        let fullContent = ''
        let buffer = ''
        let pendingFirstToken = null

        const appendToken = async (token) => {
          if (!token) return
          fullContent += token
          currentBotMessage.value = fullContent
          await scrollToBottom()
        }

        const handleToken = async (token) => {
          if (!token) return
          if (pendingFirstToken === null) {
            pendingFirstToken = token
            return
          }
          if (pendingFirstToken !== false) {
            const firstToken = pendingFirstToken
            pendingFirstToken = false
            await appendToken(firstToken + token)
            return
          }
          await appendToken(token)
        }

        while (true) {
          const { done, value } = await reader.read()
          if (done) break

          const textChunk = decoder.decode(value, { stream: true })
          buffer += textChunk

          const lines = buffer.split('\n')
          buffer = lines.pop()

          for (const line of lines) {
            const trimmedLine = line.trim()
            if (!trimmedLine || !trimmedLine.startsWith('data:')) continue

            try {
              const jsonStr = trimmedLine.substring(trimmedLine.indexOf('{')).trim()
              const data = JSON.parse(jsonStr)

              if (data.status === 'tokens') {
                inputTokens.value = data.promptTokens || 0
                outputTokens.value = data.completionTokens || 0
                continue
              }

              if (data.status === 'error') throw new Error(data.message || 'Error in stream')

              const token = data.content || data.result || ''
              await handleToken(token)
            } catch (e) {
              console.warn('⚠️ [SSE] Parse error:', e)
            }
          }
        }

        // Final buffer flush
        if (buffer.trim().startsWith('data:')) {
          try {
            const jsonStr = buffer.substring(buffer.indexOf('{')).trim()
            const data = JSON.parse(jsonStr)
            if (data.status === 'tokens') {
              inputTokens.value = data.promptTokens || 0
              outputTokens.value = data.completionTokens || 0
            } else {
              const token = data.content || data.result || ''
              await handleToken(token)
            }
          } catch (e) {}
        }

        if (pendingFirstToken && pendingFirstToken !== false) {
          await appendToken(pendingFirstToken)
        }

        // Guardar tokens en el Map ANTES de recargar historial
        // applyConversationMessages los reinyectara automaticamente en cada mensaje
        const finalInput = inputTokens.value
        const finalOutput = outputTokens.value
        const finalContextSize = modelContextSize.value

        if (fullContent) {
          messageTokenMap.value.set(tokenMapKey(fullContent), {
            inputTokens: finalInput,
            outputTokens: finalOutput,
            contextSize: finalContextSize
          })
        }

        // Recargar historial — applyConversationMessages reinyecta tokens del Map en todos los mensajes
        await loadConversationHistory(currentSessionId)
        currentBotMessage.value = ''
        isAgentTask.value = false

      } catch (error) {
        if (error.name === 'AbortError') {
          console.log('Request aborted by user')
          if (currentBotMessage.value) {
            messages.value.push({
              role: 'assistant',
              content: currentBotMessage.value + '\n\n*[Response stopped by user]*',
              inputTokens: inputTokens.value,
              outputTokens: outputTokens.value,
              contextSize: modelContextSize.value
            })
            currentBotMessage.value = ''
          }
        } else {
          console.error('Error:', error)
          if (error.message.includes('exceed_context_size_error')) {
            const match = error.message.match(/exceed_context_size_error: (\d+) tokens requested, (\d+) available/)
            const requestedTokens = match ? match[1] : 'unknown'
            const availableTokens = match ? match[2] : 'unknown'
            messages.value.push({
              role: 'assistant',
              content: `**Error: exceed_context_size_error**\n\nYour message requires ${requestedTokens} tokens, but the model only has ${availableTokens} tokens available.\n\n**Solutions:**\n- Reduce the length of your conversation history\n- Load the model with a larger context size in the Models tab\n- Start a new chat with a clean history`
            })
          } else {
            messages.value.push({ role: 'assistant', content: 'Connection error. Make sure the Boris server is running.' })
          }
        }
      } finally {
        isTyping.value = false
        currentAbortController.value = null
        isAgentTask.value = false
        await scrollToBottom()
      }
    }

    const formatTokens = (tokens) => {
      if (tokens >= 1000) return (tokens / 1000).toFixed(1) + 'k'
      return tokens.toString()
    }

    const formatRamGpuUsage = computed(() => {
      if (ramGpuUsage.value === 0) return '0 GB'
      return ramGpuUsage.value.toFixed(2) + ' GB'
    })

    const displayGpuFree = computed(() => {
      if (gpuMemoryInfo.value.remainingVram === 'N/A') return 'N/A'
      const match = gpuMemoryInfo.value.remainingVram.match(/(-?\d+\.?\d*)\s*(GB|MB)?/i)
      if (match) {
        const value = parseFloat(match[1])
        const unit = (match[2] || 'GB').toUpperCase()
        let valueInGB = value
        if (unit === 'MB') valueInGB = value / 1024
        if (valueInGB < 0) return '0 GB'
        return gpuMemoryInfo.value.remainingVram
      }
      return gpuMemoryInfo.value.remainingVram
    })

    const renderMarkdown = (text) => marked.parse(text)

    onMounted(async () => {
      if (!sessionId.value) generateSessionId()

      await loadConversationHistory(sessionId.value)
      await scrollToBottom()

      initStatusStream()

      await fetchHardware()
      await fetchGpuMemoryInfo()
      await refreshModels()
      await fetchSettings()
      await checkLlamaServerStatus()

      const hasRefreshed = sessionStorage.getItem('boris_initial_ready')
      if (!hasRefreshed) {
        try {
          const response = await api('/boris/v1/llama-server/ready')
          if (response.ok) {
            const data = await response.json()
            if (data.status === 'success' || data.status === 'ready' || data.message === 'ready') {
              sessionStorage.setItem('boris_initial_ready', 'true')
              window.location.reload()
            }
          }
        } catch (error) {
          console.log('Server not ready yet:', error)
        }
      }
    })

    onUnmounted(() => {
      if (statusEventSource.value) {
        statusEventSource.value.close()
        statusEventSource.value = null
      }
    })

    return {
      messages,
      userInput,
      isTyping,
      chatEnabled,
      chatContainer,
      currentBotMessage,
      isOnline,
      gpuInfo,
      gpuMemoryInfo,
      ramGpuUsage,
      formatRamGpuUsage,
      displayGpuFree,
      availableModels,
      loadedModels,
      selectedModel,
      selectedMenu,
      loadingModelId,
      unloadingModelId,
      modelsBusy,
      uiMessage,
      currentModelLabel,
      countLoadedInstances,
      setPrompt,
      sendMessage,
      stopResponse,
      formatTokens,
      renderMarkdown,
      refreshModels,
      loadModelWithConfig,
      unloadModel,
      chooseModel,
      newChat,
      inputTokens,
      outputTokens,
      modelContextSize,
      remainingTokens,
      isError,
      debugLogs,
      sessionId,
      settings,
      saveSettings,
      browsePath,
      llamaServerInstalled,
      llamaServerDownloading,
      downloadLlamaServer,
      toggleModelConfig,
      applyRecommendedSettings
    }
  }
}
</script>