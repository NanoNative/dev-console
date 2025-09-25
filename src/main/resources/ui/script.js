async function fetchJson(url) {
    const response = await fetch(url);
    if (!response.ok) {
        throw new Error(`Failed to fetch ${url}: ${response.status}`);
    }
    return await response.json();
}

let charts = {};

async function loadData() {
    try {
        const [systemInfo, eventData, logData] = await Promise.all([
            fetchJson('/dev-console/system-info'),
            fetchJson('/dev-console/events'),
            fetchJson('/dev-console/logs')
        ]);

        document.getElementById("system").textContent = JSON.stringify(systemInfo, null, 2);
        document.getElementById("eventsData").textContent = JSON.stringify(eventData, null, 2);
        document.getElementById("logsData").textContent = JSON.stringify(logData, null, 2);

        // Update charts with current system info
        updateChartsWithSystemInfo(systemInfo);
    } catch (e) {
        console.error("Error loading data:", e);
    }
}

function updateChartsWithSystemInfo(systemInfo) {
    const timestamp = Date.now();

    // Memory Usage Chart
    if (charts.memory && systemInfo.usedMemory) {
        const memoryValue = parseFloat(systemInfo.usedMemory.replace(' MB', ''));
        charts.memory.addPoint(memoryValue, timestamp);
    }

    // Thread Count Chart
    if (charts.threads && systemInfo.threadsNano !== undefined && systemInfo.threadsActive !== undefined) {
        const totalThreads = systemInfo.threadsNano + systemInfo.threadsActive;
        charts.threads.addPoint(totalThreads, timestamp);
    }

    // Event Count Chart
    if (charts.events && systemInfo.totalEvents !== undefined) {
        // Ensure it's treated as an integer
        const eventCount = parseInt(systemInfo.totalEvents, 10) || 0;
        charts.events.addPoint(eventCount, timestamp);
    }

    // Heap Usage Chart
    if (charts.heap && systemInfo.heapMemory !== undefined) {
        const heapPercentage = systemInfo.heapMemory * 100;
        charts.heap.addPoint(heapPercentage, timestamp);
    }
}

function openTab(tabId) {
    document.querySelectorAll('.tab').forEach(tab => tab.classList.remove('active'));
    document.querySelectorAll('.tab-content').forEach(content => content.classList.remove('active'));

    document.querySelector(`.tab[data-tab="${tabId}"]`).classList.add('active');
    document.getElementById(tabId).classList.add('active');
}

document.addEventListener("DOMContentLoaded", () => {
    document.querySelectorAll('.tab').forEach(tab => {
        tab.addEventListener("click", () => openTab(tab.dataset.tab));
    });

    // Initialize charts
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
    setInterval(loadData, 5000);
});
