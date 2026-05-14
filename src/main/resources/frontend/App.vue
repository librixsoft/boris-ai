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

    // Session management for chat memory
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

    const isError = (msg) => {
      return msg.content.includes('exceed_context_size_error') || msg.content.includes('Error: exceed_context_size_error')
    }

     const userInput = ref('')
     const isTyping = ref(false)
     const isClearingChat = ref(false)
     const chatEnabled = computed(() => selectedModel.value !== null && warmupComplete.value && !loadingModelId.value && !modelsBusy.value && !isClearingChat.value)
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
     const warmupComplete = ref(false)
     const warmupState = ref('idle')
     const warmupStatusMessage = ref('No model loaded')
     let warmupPollInterval = null

    const addDebugLog = (type, label, data) => {
      const id = Date.now() + Math.random()
      debugLogs.value.unshift({
        id,
        type, // 'request' or 'response'
        label,
        content: typeof data === 'string' ? data : JSON.stringify(data, null, 2),
        expanded: false,
        timestamp: new Date().toLocaleTimeString()
      })
      // Keep only last 10 logs for performance/space
      if (debugLogs.value.length > 10) debugLogs.value.pop()
    }

    // Guardar contextSize de cada modelo cargado (llama-server no lo devuelve)
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

      // Context Size Heuristics
      if (name.includes('3.1')) rec.contextSize = 32768
      else if (name.includes('llama-3')) rec.contextSize = 8192
      else if (name.includes('mistral') || name.includes('mixtral') || name.includes('nemo') || name.includes('codestral')) rec.contextSize = 32768
      else if (name.includes('phi-3')) rec.contextSize = 128000
      else if (name.includes('gemma-2')) rec.contextSize = 8192
      else if (name.includes('deepseek')) rec.contextSize = 16384

      // GPU Layers Heuristics
      if (sizeBytes > 0 && remainingVram > 0) {
        // If model fits in VRAM + some overhead
        if (sizeBytes * 1.2 < remainingVram) {
          rec.gpuLayers = 99 // Full offload
        } else if (sizeBytes * 0.6 < remainingVram) {
          rec.gpuLayers = 32 // Partial offload
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


    // Normalize model ID for comparison (remove .gguf extension if present)
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

    // Computed property to get context size from selected model
    const modelContextSize = computed(() => {
      if (!selectedModel.value) return 8096
      // Primero intentar obtener del Map local (donde guardamos el contextSize al cargar)
      if (modelContextSizes.value.has(selectedModel.value)) {
        return modelContextSizes.value.get(selectedModel.value)
      }
      const loaded = loadedModels.value.find(model => model.id === selectedModel.value)
      if (loaded && loaded.contextSize) return loaded.contextSize
      const available = availableModels.value.find(model => model.id === selectedModel.value)
      if (available && available.contextSize) return available.contextSize
      return 8096
    })

    // Computed property to calculate remaining tokens dynamically
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
        source.close() // Silencio total en la consola si el servidor no responde
        statusEventSource.value = null
      }
      
      statusEventSource.value = source
    }


    // Helper for API calls that updates isOnline status
    const api = async (url, options = {}) => {
      try {
        const response = await fetch(url, options)
        isOnline.value = true
        return response
      } catch (error) {
        // Only set offline if it's a network/connection error
        if (error.name !== 'AbortError') {
          isOnline.value = false
        }
        throw error
      }
    }

    const handleOffline = () => { isOnline.value = false }

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
       const model = availableModels.value.find(item => item.id === modelId)
       if (!model) return

       // Pre-execution and cleanup logic removed
       if (loadedModels.value.length > 0) {
         uiMessage.value = 'You must explicitly eject the current model before loading a new one.'
         setTimeout(() => { if (uiMessage.value.includes('must explicitly eject')) uiMessage.value = '' }, 4000)
         return
       }

        // Clear any existing warmup polling (e.g., from initial load)
        if (warmupPollInterval) {
          clearInterval(warmupPollInterval)
          warmupPollInterval = null
        }

        const resolvedId = buildNextLoadId(model.id)
        loadingModelId.value = modelId
        pendingChatRedirect.value = false
        warmupComplete.value = false
        warmupState.value = 'loading'
        warmupStatusMessage.value = `Starting load for ${model.id}...`
        uiMessage.value = `Starting load for ${model.id}...`
        
        // Switch to chat view immediately to show loading state
        selectedMenu.value = 'chat'
        
        try {
          const contextSize = model.contextSize || 8000
          const response = await api('/boris/v1/models/load', {
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
          })

          addDebugLog('request', `POST /boris/v1/models/load (custom)`, { id: resolvedId, model: model.id, contextSize })

          const result = await response.json()
          addDebugLog('response', `Load Model Config Response`, result)
          if (!response.ok || result.status === 'error') {
            throw new Error(result.message || 'Could not load model')
          }

          // Guardar contextSize del modelo cargado
          modelContextSizes.value.set(resolvedId, contextSize)

          model.configExpanded = false
          selectedModel.value = result.data?.id || result.id || resolvedId

          // Reset local chat/session state immediately while warmup finishes
          sessionId.value = null
          localStorage.removeItem('boris_session_id')
          messages.value = [
            { role: 'system', content: 'Model loading completed. Finishing warmup before chat is enabled...' }
          ]
          userInput.value = ''
          currentBotMessage.value = ''
          inputTokens.value = 0
          outputTokens.value = 0
          warmupState.value = 'warming'
          warmupStatusMessage.value = 'Model loaded. Finishing full warmup before chat is enabled...'
          uiMessage.value = 'Model loaded. Finishing full warmup before chat is enabled...'
          pendingChatRedirect.value = true

          // Refresh models and GPU info AFTER setting warming state
          await refreshModels()
          await fetchGpuMemoryInfo()

          // Poll warmup status every 500ms
          warmupPollInterval = setInterval(async () => {
            const complete = await checkWarmupStatus()
            if (complete) {
              clearInterval(warmupPollInterval)
              warmupPollInterval = null
              warmupComplete.value = true
              warmupState.value = 'ready'
              warmupStatusMessage.value = 'Warmup complete. Model ready for chat.'
              uiMessage.value = 'Model ready. Chat is now unlocked.'
              setTimeout(() => { if (uiMessage.value === 'Model ready. Chat is now unlocked.') uiMessage.value = '' }, 3000)

              // Force the user to explicitly start a new clean chat
              sessionId.value = null
              localStorage.removeItem('boris_session_id')
              messages.value = [
                { role: 'system', content: 'Model loaded successfully and ready. Click the "+" button below to initialize a new conversation.' }
              ]
              userInput.value = ''
              currentBotMessage.value = ''
              inputTokens.value = 0
              outputTokens.value = 0

              // Switch to chat view automatically only after explicit warmup confirmation
              if (pendingChatRedirect.value) {
                selectedMenu.value = 'chat'
                pendingChatRedirect.value = false
              }
            }
          }, 500)
      } catch (error) {
        console.error('Failed to load model:', error)
        pendingChatRedirect.value = false
        warmupState.value = 'error'
        warmupStatusMessage.value = error.message || 'Failed to load model'
        uiMessage.value = error.message || 'Failed to load model'
      } finally {
        loadingModelId.value = null
      }
    }

    const unloadModel = async (modelId) => {
      unloadingModelId.value = modelId
      uiMessage.value = ''
      try {
        // First clear model resources (KV cache, slots, temp memory) for this model
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

        // Limpiar contextSize del modelo descargado
        modelContextSizes.value.delete(modelId)

        await refreshModels()
        await fetchGpuMemoryInfo() // Update GPU memory info immediately after unloading
        
        if (selectedModel.value === modelId) {
          selectedModel.value = loadedModels.value[0]?.id || availableModels.value[0]?.id || null
          
           // Reset chat state when active model is ejected
           messages.value = [
             { role: 'system', content: 'You are Boris, a friendly, professional, and very capable intelligent assistant.' }
           ]
           userInput.value = ''
           currentBotMessage.value = ''
           inputTokens.value = 0
           outputTokens.value = 0
           // chatEnabled is computed automatically from selectedModel and warmupComplete
        }
        
        uiMessage.value = result.message || `Model ${modelId} unloaded successfully`
        // Don't clear unloadingModelId on success - button stays disabled since model is removed
      } catch (error) {
        console.error('Failed to unload model:', error)
        uiMessage.value = error.message || 'Failed to unload model'
        unloadingModelId.value = null // Re-enable button on error
      }
    }

    const chooseModel = (modelId) => {
      selectedModel.value = modelId
      selectedMenu.value = 'chat'
    }

    // Watch for menu changes to reset unloading state when entering models screen
    watch(selectedMenu, (newMenu) => {
      if (newMenu === 'models') {
        unloadingModelId.value = null // Enable eject buttons when entering models screen
      }
    })

    // Watch for model state changes to auto-update chat view
    watch([loadedModels, warmupComplete, warmupState], () => {
      // If user is in chat view and model state changes, force reactivity
      if (selectedMenu.value === 'chat') {
        // Vue will automatically re-render based on the reactive state changes
        // This ensures the UI updates without manual refresh
      }
    }, { deep: true })


    // Watch GPU memory info to track RAM usage when GPU is exceeded
    watch(gpuMemoryInfo, (newInfo) => {
      if (newInfo.remainingVram !== 'N/A') {
        // Parse remaining VRAM (format like "8.5 GB" or "-2.3 GB")
        const match = newInfo.remainingVram.match(/(-?\d+\.?\d*)\s*(GB|MB)?/i)
        if (match) {
          const value = parseFloat(match[1])
          const unit = (match[2] || 'GB').toUpperCase()
          let valueInGB = value
          if (unit === 'MB') {
            valueInGB = value / 1024
          }
          
          // If negative, track overflow in RAM
          if (valueInGB < 0) {
            ramGpuUsage.value = Math.abs(valueInGB)
          } else {
            // If GPU has free space, RAM usage should be 0
            ramGpuUsage.value = 0
          }
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
          body: JSON.stringify({ 
            all: isDeep,
            sessionId: oldSessionId,
            reinit: true
          })
        })
      } catch (error) {
        console.error('Failed to perform cleanup:', error)
      }

      sessionId.value = null
      localStorage.removeItem('boris_session_id')
      generateSessionId()

      messages.value = [
        { role: 'system', content: 'You are Boris, a friendly, professional, and very capable intelligent assistant.' }
      ]
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
        if (currentBotMessage.value) {
          messages.value.push({ role: 'assistant', content: currentBotMessage.value + '\n\n*[Response stopped by user]*' })
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

     const checkWarmupStatus = async () => {
       try {
         const response = await api('/api/models/warmup-status')
         if (response.ok) {
           const res = await response.json()
           const data = res.data || res
           console.log('🔍 [DEBUG] Warmup status response:', data)
           warmupComplete.value = data.warmupComplete === true
           warmupState.value = data.state || (warmupComplete.value ? 'ready' : 'warming')
           warmupStatusMessage.value = data.statusMessage || (warmupComplete.value ? 'Warmup complete. Model ready for chat.' : 'Warming up model...')
           console.log('🔍 [DEBUG] After update - warmupComplete:', warmupComplete.value, 'warmupState:', warmupState.value)
           return data.warmupComplete
         }
       } catch (error) {
         console.error('Failed to check warmup status:', error)
       }
       return false
     }

     // Poll warmup status on initial load if a model is already loaded but warmup not done
     const startWarmupPolling = () => {
       if (warmupPollInterval) clearInterval(warmupPollInterval)
       warmupPollInterval = setInterval(async () => {
         const complete = await checkWarmupStatus()
         if (complete) {
           clearInterval(warmupPollInterval)
           warmupPollInterval = null
           warmupComplete.value = true
           warmupState.value = 'ready'
           warmupStatusMessage.value = 'Warmup complete. Model ready for chat.'
           uiMessage.value = 'Model warmup complete. Ready for fast inference.'
           setTimeout(() => { if (uiMessage.value === 'Model warmup complete. Ready for fast inference.') uiMessage.value = '' }, 3000)
         }
       }, 500)
     }

      const downloadLlamaServer = async () => {
        if (llamaServerDownloading.value || llamaServerInstalled.value) return
        llamaServerDownloading.value = true
        uiMessage.value = 'Downloading llama server...'
        try {
          const response = await api('/boris/v1/llama-server/download', {
            method: 'POST'
          })
          if (response.ok) {
            const data = await response.json()
            if (data.status === 'success') {
              llamaServerInstalled.value = true
              uiMessage.value = 'Llama server downloaded and installed successfully'
              // Wait for UI to update before refreshing to ensure state is visible
              setTimeout(() => {
                window.location.reload()
              }, 1000)
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
          // Convert absolute path back to relative format with / prefix if under user.home
          const userHome = settings.value.userHome || ''
          let path = data.path
          if (userHome && path.startsWith(userHome)) {
            let relative = path.substring(userHome.length)
            // Remove leading slash or backslash
            if (relative.startsWith('\\') || relative.startsWith('/')) {
              relative = relative.substring(1)
            }
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
          : !warmupComplete.value
            ? 'Model is still warming up'
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
      
      // Create abort controller for this request
      currentAbortController.value = new AbortController()

      // Reset token counters
      inputTokens.value = 0
      outputTokens.value = 0

      await scrollToBottom()

      try {
        // Mark as agent task for cancellation support
        isAgentTask.value = true
        // Use chat completions endpoint for all requests
        const currentSessionId = getSessionId()
        console.log('🔍 [DEBUG] Sending sessionId:', currentSessionId)
        const response = await fetch('/boris/v1/chat/completions', {
          method: 'POST',
          headers: { 
            'Content-Type': 'application/json',
            'Accept': 'text/event-stream'
          },
          body: JSON.stringify({
            sessionId: currentSessionId,
            instruction: text,
            model: selectedModel.value
          }),
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

              if (data.status === 'error') throw new Error(data.message || 'Error in stream')
              
              const token = data.content || data.result || ''
              if (token) {
                fullContent += token
                currentBotMessage.value = fullContent
                await scrollToBottom()
              }
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
            const token = data.content || data.result || ''
            if (token) {
              fullContent += token
              currentBotMessage.value = fullContent
            }
          } catch (e) {}
        }

        messages.value.push({ 
          role: 'assistant', 
          content: fullContent,
          inputTokens: 0, 
          outputTokens: 0,
          contextSize: modelContextSize.value
        })
        currentBotMessage.value = ''
        isAgentTask.value = false
         // chatEnabled is computed: automatically true when model selected and warmup complete
      } catch (error) {
        if (error.name === 'AbortError') {
          console.log('Request aborted by user')
          if (currentBotMessage.value) {
            messages.value.push({ role: 'assistant', content: currentBotMessage.value + '\n\n*[Response stopped by user]*', inputTokens: inputTokens.value, outputTokens: outputTokens.value, contextSize: modelContextSize.value })
            currentBotMessage.value = ''
          }
        } else {
          console.error('Error:', error)
          
          // Specifically handle exceed_context_size_error
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
      if (tokens >= 1000) {
        return (tokens / 1000).toFixed(1) + 'k'
      }
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
        if (unit === 'MB') {
          valueInGB = value / 1024
        }
        // Never show below 0
        if (valueInGB < 0) {
          return '0 GB'
        }
        return gpuMemoryInfo.value.remainingVram
      }
      return gpuMemoryInfo.value.remainingVram
    })

    const renderMarkdown = (text) => marked.parse(text)

    onMounted(async () => {
      // Generate session ID on mount if not exists
      if (!sessionId.value) {
        generateSessionId()
      }

      await scrollToBottom()

      // Iniciar escucha de eventos en tiempo real (silencioso)
      initStatusStream()

      // Fetch initial state sequentially
      await fetchHardware()
      await fetchGpuMemoryInfo()
      await refreshModels()
      await fetchSettings()
      await checkLlamaServerStatus()
      await checkWarmupStatus() // sets warmupComplete

      // If a model is loaded, always start polling to ensure warmup status is up-to-date
      if (loadedModels.value.length > 0) {
        startWarmupPolling()
      }

      // Wait for server to be ready (preloaded models finished loading)
      // Blocks until backend is ready, then refreshes page to show loaded models
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
       if (warmupPollInterval) {
         clearInterval(warmupPollInterval)
         warmupPollInterval = null
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
      warmupComplete,
      warmupState,
      warmupStatusMessage,
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
      chatEnabled,
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
