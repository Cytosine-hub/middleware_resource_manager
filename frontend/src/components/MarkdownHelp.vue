<template>
  <div v-if="show" class="modal-backdrop" @click.self="$emit('close')">
    <div class="help-panel">
      <div class="help-header">
        <h3>Markdown 语法参考</h3>
        <button class="ghost" @click="$emit('close')">关闭</button>
      </div>
      <div class="help-body">
        <table>
          <thead><tr><th>效果</th><th>语法</th></tr></thead>
          <tbody>
            <tr><td><strong>粗体</strong></td><td><code>**文字**</code></td></tr>
            <tr><td><em>斜体</em></td><td><code>*文字*</code></td></tr>
            <tr><td><code>行内代码</code></td><td><code>`代码`</code></td></tr>
            <tr><td><a href="#">链接</a></td><td><code>[文字](网址)</code></td></tr>
            <tr><td>图片</td><td><code>![alt](图片地址)</code></td></tr>
            <tr><td>删除线</td><td><code>~~文字~~</code></td></tr>
          </tbody>
        </table>
        <h4>标题</h4>
        <pre><code># 一级标题
## 二级标题
### 三级标题</code></pre>
        <h4>列表</h4>
        <pre><code>- 无序项目
- 无序项目
  - 缩进子项

1. 有序项目
2. 有序项目</code></pre>
        <h4>代码块</h4>
        <pre><code>```java
public static void main(String[] args) {
    System.out.println("Hello");
}
```</code></pre>
        <h4>表格</h4>
        <pre><code>| 列A | 列B | 列C |
|-----|-----|-----|
| A1  | B1  | C1  |
| A2  | B2  | C2  |</code></pre>
        <h4>引用</h4>
        <pre><code>> 这是一段引用文字
> 可以多行</code></pre>
        <h4>分隔线</h4>
        <pre><code>---</code></pre>
        <h4>占位符变量</h4>
        <pre><code>{{参数编码}}</code></pre>
        <p class="help-note">占位符会自动替换为对应标准参数的值，仅文档编辑支持。</p>
      </div>
    </div>
  </div>
</template>

<script setup>
defineProps({ show: Boolean })
defineEmits(['close'])
</script>

<style scoped>
.help-panel {
  background: #fff; border-radius: 10px; width: min(700px, 92vw); max-height: 85vh; overflow-y: auto; padding: 24px;
  box-shadow: 0 18px 48px rgba(23,35,59,0.28);
}
.help-header { display: flex; align-items: center; justify-content: space-between; margin-bottom: 16px; }
.help-header h3 { margin: 0; font-size: 18px; letter-spacing: 0; }
.help-body h4 { margin: 18px 0 6px; font-size: 14px; color: #2356a5; }
.help-body table { width: 100%; border-collapse: collapse; margin-bottom: 12px; }
.help-body th, .help-body td { padding: 8px 12px; border-bottom: 1px solid #e3e9f1; text-align: left; font-size: 13px; }
.help-body th { background: #f7f9fc; color: #526071; text-transform: none; }
.help-body code { background: #f0f3f7; padding: 1px 6px; border-radius: 3px; font-size: 12px; }
.help-body pre { background: #1a1a2e; color: #e0e0e0; padding: 14px; border-radius: 6px; overflow-x: auto; font-size: 12px; line-height: 1.6; margin: 6px 0; }
.help-body pre code { background: none; padding: 0; color: inherit; }
.help-note { color: #8896a7; font-size: 12px; margin: 8px 0 0; }
</style>
