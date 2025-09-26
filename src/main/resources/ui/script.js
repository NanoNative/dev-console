// Fetch JSON from backend and throw on non-OK responses
async function fetchJson(url) {
    const response = await fetch(url);
    if (!response.ok) {
        throw new Error(`Failed to fetch ${url}: ${response.status}`);
    }
    return await response.json();
}

// Holds TinyChart instances for each chart canvas
let charts = {};

// Render System Info object into a compact two-column key→value grid inside target
function renderSystemKV(target, obj){
  if(!target) return;
  const wrap = document.createElement('div');
  wrap.className = 'kv';
  Object.keys(obj || {}).forEach(k=>{
    const kEl = document.createElement('div'); kEl.className='k'; kEl.textContent=k;
    const vEl = document.createElement('div'); vEl.className='v';
    const v = obj[k];
    if(Array.isArray(v)){
      const arr = document.createElement('div'); arr.className='arr';
      v.forEach(item=>{ const tag=document.createElement('span'); tag.className='tag'; tag.textContent=String(item); arr.appendChild(tag); });
      vEl.appendChild(arr);
    } else if (v && typeof v==='object'){
      const span=document.createElement('span'); span.textContent=JSON.stringify(v);
      vEl.appendChild(span);
    } else {
      const span=document.createElement('span'); span.textContent=String(v);
      vEl.appendChild(span);
    }
    wrap.appendChild(kEl); wrap.appendChild(vEl);
  });
  target.replaceChildren(wrap);
}

// Render arrays/objects as a list of rows (one record per line) inside target
function renderList(target, val){
  if(!target) return;
  const list = document.createElement('div');
  list.className = 'nano-list';
  if(Array.isArray(val)){
    val.forEach(item=>{
      const row = document.createElement('div');
      row.className = 'nano-row';
      row.textContent = (typeof item==='string') ? item : JSON.stringify(item);
      list.appendChild(row);
    });
  } else if (val && typeof val==='object'){
    Object.keys(val).forEach(k=>{
      const row = document.createElement('div');
      row.className='nano-row';
      const v = val[k];
      row.textContent = k + ': ' + (typeof v==='string' ? v : JSON.stringify(v));
      list.appendChild(row);
    });
  } else {
    const row = document.createElement('div');
    row.className='nano-row';
    row.textContent = String(val ?? '');
    list.appendChild(row);
  }
  target.replaceChildren(list);
}

// Load all datasets from BE, render System/Events/Logs, and update charts
async function loadData() {
    try {
        const [systemInfo, eventData, logData] = await Promise.all([
            fetchJson('/dev-console/system-info'),
            fetchJson('/dev-console/events'),
            fetchJson('/dev-console/logs')
        ]);

        renderSystemKV(document.getElementById("system"), systemInfo);
        renderList(document.getElementById("eventsData"), eventData);
        renderList(document.getElementById("logsData"), logData);

        // Update charts with current system info
        updateChartsWithSystemInfo(systemInfo);
    } catch (e) {
        console.error("Error loading data:", e);
    }
}

// Push latest System Info metrics into the corresponding charts
function updateChartsWithSystemInfo(systemInfo) {
    const timestamp = Date.now();

    if (charts.memory && systemInfo.usedMemory) {
        const memoryValue = parseFloat(systemInfo.usedMemory.replace(' MB', ''));
        charts.memory.addPoint(memoryValue, timestamp);
    }

    if (charts.threads && systemInfo.threadsNano !== undefined && systemInfo.threadsActive !== undefined) {
        const totalThreads = systemInfo.threadsNano + systemInfo.threadsActive;
        charts.threads.addPoint(totalThreads, timestamp);
    }

    if (charts.events && systemInfo.totalEvents !== undefined) {
        const eventCount = parseInt(systemInfo.totalEvents, 10) || 0;
        charts.events.addPoint(eventCount, timestamp);
    }

    if (charts.heap && systemInfo.heapMemory !== undefined) {
        const heapPercentage = systemInfo.heapMemory * 100;
        charts.heap.addPoint(heapPercentage, timestamp);
    }
}

// Activate a tab by id and show its content panel
function openTab(tabId) {
    document.querySelectorAll('.tab').forEach(tab => tab.classList.remove('active'));
    document.querySelectorAll('.tab-content').forEach(content => content.classList.remove('active'));

    document.querySelector(`.tab[data-tab="${tabId}"]`).classList.add('active');
    document.getElementById(tabId).classList.add('active');
}

// Initialize tabs, charts, initial data load, and auto-refresh on DOM ready
document.addEventListener("DOMContentLoaded", () => {
    document.querySelectorAll('.tab').forEach(tab => {
        tab.addEventListener("click", () => openTab(tab.dataset.tab));
    });

    const memoryCanvas = document.getElementById('memoryChart');
    const threadsCanvas = document.getElementById('threadsChart');
    const eventsCanvas = document.getElementById('eventsChart');
    const heapCanvas = document.getElementById('heapChart');

    if (memoryCanvas) {
        charts.memory = new TinyChart(memoryCanvas, {
            title: 'Memory Usage (MB)',
            lineColor: '#28a745',
            pointColor: '#28a745'
        });
    }

    if (threadsCanvas) {
        charts.threads = new TinyChart(threadsCanvas, {
            title: 'Thread Count',
            lineColor: '#ffc107',
            pointColor: '#ffc107',
            isInteger: true
        });
    }

    if (eventsCanvas) {
        charts.events = new TinyChart(eventsCanvas, {
            title: 'Total Events',
            lineColor: '#17a2b8',
            pointColor: '#17a2b8',
            isInteger: true
        });
    }

    if (heapCanvas) {
        charts.heap = new TinyChart(heapCanvas, {
            title: 'Heap Usage (%)',
            lineColor: '#dc3545',
            pointColor: '#dc3545'
        });
    }

    loadData();
    setInterval(loadData, 2000);
});

// Theme/pause/export wiring (toggle theme, pause auto-refresh, export events/logs)
(function(){
  const $ = (q, el=document)=>el.querySelector(q);

  // Set the theme button icon (🌿 for light, ☀️ for dark)
  function setThemeIcon(){
    const btn = $("#nanoThemeBtn"); if(!btn) return;
    const isLight = document.body.classList.contains('light');
    btn.textContent = isLight ?  "🌿": "☀️";
  }

  // Toggle between light and dark theme and persist the choice
  function toggleTheme(){
    document.body.classList.toggle('light');
    localStorage.setItem('nano_theme', document.body.classList.contains('light') ? 'light' : 'dark');
    setThemeIcon();
  }

  // Restore previously saved theme on load and set icon
  (function restoreTheme(){
    const saved = localStorage.getItem('nano_theme');
    if (saved === 'light') document.body.classList.add('light');
    setThemeIcon();
  })();

  // Install a wrapper around loadData to support pause/resume without touching setInterval
  let paused = false;
  function installPauseWrapper(){
    const fn = window.loadData;
    if (typeof fn === 'function' && !fn.__nanoWrapped){
      const wrapped = function(){ if (paused) return; return fn.apply(this, arguments); };
      wrapped.__nanoWrapped = true;
      window.loadData = wrapped;
    }
  }
  installPauseWrapper(); setTimeout(installPauseWrapper, 0); setTimeout(installPauseWrapper, 500);

  // Toggle pause/resume state and update the pause button label
  function togglePause(){
    paused = !paused;
    const btn = $("#nanoPauseBtn"); if (btn) btn.textContent = paused ? "▶ Resume" : "⏸ Pause";
  }

  // Download a given text as a file with the specified filename
  function downloadText(filename, text){
    const blob = new Blob([text || ""], { type: "text/plain;charset=utf-8" });
    const url = URL.createObjectURL(blob);
    const a = document.createElement("a"); a.href = url; a.download = filename; a.click();
    URL.revokeObjectURL(url);
  }

  // Export events and logs views to separate text files
  function doExport(){
    const eventsTxt = ($("#eventsData")?.innerText) || "";
    const logsTxt = ($("#logsData")?.innerText) || "";
    downloadText("events.txt", eventsTxt); downloadText("logs.txt", logsTxt);
  }

  // Wire up Theme, Pause, and Export buttons
  function wire(){
    $("#nanoThemeBtn")?.addEventListener("click", toggleTheme);
    $("#nanoPauseBtn")?.addEventListener("click", togglePause);
    $("#nanoExportBtn")?.addEventListener("click", doExport);
  }

  // Initialize button wiring when DOM is ready
  if (document.readyState === "loading") document.addEventListener("DOMContentLoaded", wire);
  else wire();
})();
