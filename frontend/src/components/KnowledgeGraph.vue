<template>
  <div class="workspace knowledge-graph">
    <!-- 工具栏 -->
    <div class="graph-toolbar">
      <input
        v-model="searchTerm"
        type="text"
        placeholder="搜索关键词..."
        class="search-input"
        @keyup.enter="focusNode"
      />
      <span class="node-count">节点: {{ graphData.nodes?.length || 0 }}</span>
      <span class="link-count">连接: {{ graphData.links?.length || 0 }}</span>
    </div>

    <!-- 3D 图形容器 -->
    <div ref="graphContainer" class="graph-container"></div>

    <!-- 节点详情面板 -->
    <div v-if="selectedNode" class="node-detail">
      <h4>{{ selectedNode.name }}</h4>
      <p>类型：{{ selectedNode.group === 'keyword' ? '关键词' : '文档来源' }}</p>
      <p>出现次数：{{ selectedNode.val }}</p>
      <p>关联节点：{{ selectedNode.neighbors?.length || 0 }} 个</p>
      <button class="ghost" @click="selectedNode = null">关闭</button>
    </div>

    <!-- 图例 -->
    <div class="graph-legend">
      <div><span class="legend-dot keyword"></span> 关键词</div>
      <div><span class="legend-dot document"></span> 文档来源</div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount, nextTick } from 'vue'
import { request } from '../api'
import ForceGraph3D from '3d-force-graph'

const graphContainer = ref(null)
const graphData = ref({ nodes: [], links: [] })
const selectedNode = ref(null)
const searchTerm = ref('')
let graph = null

onMounted(async () => {
  await loadGraphData()
  await nextTick()
  initGraph()
})

onBeforeUnmount(() => {
  if (graph) {
    graph._destructor?.()
  }
})

async function loadGraphData() {
  try {
    const data = await request('/api/knowledge/graph')
    if (data) {
      graphData.value = data
    }
  } catch (e) {
    console.error('Failed to load graph data:', e)
  }
}

function initGraph() {
  if (!graphContainer.value || !graphData.value.nodes.length) return

  const container = graphContainer.value
  const width = container.clientWidth
  const height = container.clientHeight

  try {
  graph = ForceGraph3D({ controlType: 'orbit' })(container)
    .width(width)
    .height(height)
    .backgroundColor('#0a0e1a')
    .graphData(graphData.value)
    .nodeLabel(node => `${node.name} (${node.val}次)`)
    .nodeColor(node => node.group === 'keyword' ? '#4a9eff' : '#4aff8e')
    .nodeVal(node => Math.max(node.val * 2, 3))
    .nodeResolution(8)
    .linkColor(() => 'rgba(255,255,255,0.15)')
    .linkWidth(link => Math.max(link.value * 0.5, 0.3))
    .linkDirectionalParticles(1)
    .linkDirectionalParticleWidth(1)
    .linkDirectionalParticleColor(() => '#4a9eff')
    .onNodeClick(node => {
      if (!node || node.x == null) return
      selectedNode.value = node
      const distance = 80
      const distRatio = 1 + distance / Math.sqrt(
        (node.x || 0) ** 2 + (node.y || 0) ** 2 + (node.z || 0) ** 2
      )
      graph.cameraPosition(
        { x: (node.x || 0) * distRatio, y: (node.y || 0) * distRatio, z: (node.z || 0) * distRatio },
        node,
        2000
      )
    })
    .onNodeHover(node => {
      container.style.cursor = node ? 'pointer' : null
    })

  // 球形布局：给节点初始位置
  const N = graphData.value.nodes.length
  const radius = Math.max(50, N * 2)
  graphData.value.nodes.forEach((node, i) => {
    const phi = Math.acos(-1 + (2 * i) / N)
    const theta = Math.sqrt(N * Math.PI) * phi
    node.x = radius * Math.cos(theta) * Math.sin(phi)
    node.y = radius * Math.sin(theta) * Math.sin(phi)
    node.z = radius * Math.cos(phi)
  })
  graph.graphData(graphData.value)

  // 力学参数
  graph.d3Force('charge').strength(-120)
  graph.d3Force('link').distance(60)
  } catch (e) {
    console.warn('3D graph init error:', e)
  }
}

function focusNode() {
  if (!searchTerm.value || !graph) return
  const term = searchTerm.value.toLowerCase()
  const node = graphData.value.nodes.find(n =>
    n.name.toLowerCase().includes(term)
  )
  if (node) {
    selectedNode.value = node
    try {
      const distance = 100
      const distRatio = 1 + distance / Math.sqrt(
        (node.x || 0) ** 2 + (node.y || 0) ** 2 + (node.z || 0) ** 2
      )
      graph.cameraPosition(
        { x: (node.x || 0) * distRatio, y: (node.y || 0) * distRatio, z: (node.z || 0) * distRatio },
        node,
        2000
      )
    } catch (e) {
      console.warn('Camera position error:', e)
    }
  }
}
</script>

<style scoped>
.knowledge-graph {
  position: relative;
  height: 100%;
  overflow: hidden;
}

.graph-container {
  width: 100%;
  height: 100%;
}

.graph-toolbar {
  position: absolute;
  top: 12px;
  left: 12px;
  z-index: 10;
  display: flex;
  gap: 12px;
  align-items: center;
}

.search-input {
  padding: 6px 12px;
  border: 1px solid #d8e1ec;
  border-radius: 6px;
  font-size: 14px;
  width: 200px;
  background: rgba(255,255,255,0.95);
}

.node-count, .link-count {
  font-size: 13px;
  color: #888;
  background: rgba(255,255,255,0.9);
  padding: 4px 8px;
  border-radius: 4px;
}

.node-detail {
  position: absolute;
  top: 12px;
  right: 12px;
  z-index: 10;
  background: rgba(255,255,255,0.96);
  border: 1px solid #d8e1ec;
  border-radius: 8px;
  padding: 16px;
  min-width: 200px;
  box-shadow: 0 4px 12px rgba(0,0,0,0.1);
}

.node-detail h4 {
  margin: 0 0 8px;
  font-size: 16px;
  color: #2356a5;
}

.node-detail p {
  margin: 4px 0;
  font-size: 13px;
  color: #526071;
}

.graph-legend {
  position: absolute;
  bottom: 12px;
  left: 12px;
  z-index: 10;
  background: rgba(255,255,255,0.9);
  padding: 8px 12px;
  border-radius: 6px;
  font-size: 13px;
}

.graph-legend div {
  display: flex;
  align-items: center;
  gap: 6px;
  margin: 2px 0;
}

.legend-dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  display: inline-block;
}

.legend-dot.keyword { background: #4a9eff; }
.legend-dot.document { background: #4aff8e; }
</style>
