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

    <!-- 图形容器 -->
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
      <div><span class="legend-dot keyword"></span> 关键词（小）</div>
      <div><span class="legend-dot document"></span> 文档来源（大）</div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount, nextTick } from 'vue'
import { request } from '../api'
import ForceGraph from 'force-graph'

const graphContainer = ref(null)
const graphData = ref({ nodes: [], links: [] })
const selectedNode = ref(null)
const searchTerm = ref('')
let graph = null

onMounted(async () => {
  await loadGraphData()
  await nextTick()
  await new Promise(r => setTimeout(r, 100))
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
  const w = container.clientWidth || 800
  const h = container.clientHeight || 600

  try {
    // 2D 圆形初始布局
    const N = graphData.value.nodes.length
    const r = Math.max(100, N * 3)
    graphData.value.nodes.forEach((node, i) => {
      const angle = (2 * Math.PI * i) / N
      node.x = r * Math.cos(angle)
      node.y = r * Math.sin(angle)
    })

    graph = ForceGraph(container)
      .width(w)
      .height(h)
      .backgroundColor('#000000')
      .graphData(graphData.value)
      .nodeLabel(node => `${node.name} (${node.val}次)`)
      .nodeColor(() => '#ffffff')
      .nodeVal(node => node.group === 'keyword' ? Math.max(node.val * 0.5, 1.5) : Math.max(node.val * 2, 5))
      .linkColor(() => 'rgba(255,255,255,0.6)')
      .linkWidth(link => Math.max(link.value * 0.5, 0.3))
      .nodeCanvasObject((n, ctx) => {
        const r = Math.sqrt(n.val || 1) * 1.5
        ctx.beginPath()
        ctx.arc(n.x, n.y, r, 0, 2 * Math.PI)
        ctx.fillStyle = '#ffffff'
        ctx.fill()
      })
      .nodePointerAreaPaint((n, color, ctx) => {
        const r = Math.sqrt(n.val || 1) * 1.5 + 2
        ctx.fillStyle = color
        ctx.beginPath()
        ctx.arc(n.x, n.y, r, 0, 2 * Math.PI)
        ctx.fill()
      })
      .onNodeClick(node => {
        selectedNode.value = node
        graph.centerAt(node.x, node.y, 1000)
        graph.zoom(4, 1000)
      })
      .onNodeHover(node => {
        container.style.cursor = node ? 'pointer' : null
      })

    graph.d3Force('charge').strength(-300)
    graph.d3Force('link').distance(80)
    setTimeout(() => graph.zoomToFit(400, 50), 500)

    graph.d3Force('charge').strength(-300)
    graph.d3Force('link').distance(80)
    graph.zoomToFit(400, 50)
  } catch (e) {
    console.warn('graph init error:', e)
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
    graph.centerAt(node.x, node.y, 1000)
    graph.zoom(4, 1000)
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
  border: 1px solid #333;
  border-radius: 6px;
  font-size: 14px;
  width: 200px;
  background: rgba(30,30,30,0.95);
  color: #fff;
}

.search-input::placeholder { color: #888; }

.node-count, .link-count {
  font-size: 13px;
  color: #aaa;
  background: rgba(30,30,30,0.9);
  padding: 4px 8px;
  border-radius: 4px;
}

.node-detail {
  position: absolute;
  top: 12px;
  right: 12px;
  z-index: 10;
  background: rgba(20,20,20,0.96);
  border: 1px solid #333;
  border-radius: 8px;
  padding: 16px;
  min-width: 200px;
  box-shadow: 0 4px 12px rgba(0,0,0,0.4);
}

.node-detail h4 {
  margin: 0 0 8px;
  font-size: 16px;
  color: #fff;
}

.node-detail p {
  margin: 4px 0;
  font-size: 13px;
  color: #aaa;
}

.graph-legend {
  position: absolute;
  bottom: 12px;
  left: 12px;
  z-index: 10;
  background: rgba(20,20,20,0.9);
  padding: 8px 12px;
  border-radius: 6px;
  font-size: 13px;
  color: #aaa;
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

.legend-dot.keyword { background: #fff; width: 6px; height: 6px; }
.legend-dot.document { background: #fff; width: 12px; height: 12px; }
</style>
