class TinyChart {
    constructor(canvas, options = {}) {
        this.canvas = canvas;
        this.ctx = canvas.getContext('2d');
        this.options = {
            padding: 40,
            gridColor: '#e0e0e0',
            lineColor: '#007bff',
            pointColor: '#ff6b6b',
            textColor: '#333',
            backgroundColor: '#fff',
            isInteger: false,
            ...options
        };
        this.data = [];
        this.maxPoints = options.maxPoints || 50;
        this.tooltip = null;
        this.setupCanvas();
        this.setupMouseEvents();
    }

    setupCanvas() {
        // Handle high DPI displays
        const rect = this.canvas.getBoundingClientRect();
        const dpr = window.devicePixelRatio || 1;

        this.canvas.width = rect.width * dpr;
        this.canvas.height = rect.height * dpr;

        this.ctx.scale(dpr, dpr);

        // Store display size for mouse calculations
        this.displayWidth = rect.width;
        this.displayHeight = rect.height;
    }

    setData(data) {
        this.data = data.slice(-this.maxPoints);
        this.draw();
    }

    addPoint(value, timestamp = Date.now()) {
        // Round to integer if specified
        const finalValue = this.options.isInteger ? Math.round(value) : value;
        this.data.push({ value: finalValue, timestamp });
        if (this.data.length > this.maxPoints) {
            this.data.shift();
        }
        this.draw();
    }

    setupMouseEvents() {
        this.canvas.addEventListener('mousemove', (e) => {
            const rect = this.canvas.getBoundingClientRect();
            // Use display coordinates, not canvas coordinates
            const mouseX = e.clientX - rect.left;
            const mouseY = e.clientY - rect.top;
            this.handleMouseMove(mouseX, mouseY);
        });

        this.canvas.addEventListener('mouseleave', () => {
            this.hideTooltip();
        });
    }

    handleMouseMove(mouseX, mouseY) {
        if (this.data.length === 0) return;

        const { padding } = this.options;
        const chartWidth = this.displayWidth - padding * 2;
        const chartHeight = this.displayHeight - padding * 2;

        const values = this.data.map(d => d.value);
        const minValue = Math.min(...values);
        const maxValue = Math.max(...values);
        const valueRange = maxValue - minValue || 1;

        let closestPoint = null;
        let minDistance = Infinity;

        this.data.forEach((point, index) => {
            // Use exact same calculation as in the draw() method
            let x;
            if (this.data.length === 1) {
                x = padding + chartWidth / 2;
            } else {
                x = padding + (chartWidth * index) / (this.data.length - 1);
            }
            const y = padding + chartHeight - ((point.value - minValue) / valueRange) * chartHeight;

            const distance = Math.sqrt((mouseX - x) ** 2 + (mouseY - y) ** 2);

            if (distance < 25 && distance < minDistance) {
                minDistance = distance;
                closestPoint = { point, x, y, index };
            }
        });

        if (closestPoint) {
            this.showTooltip(closestPoint.point, mouseX, mouseY);
            this.canvas.style.cursor = 'pointer';
        } else {
            this.hideTooltip();
            this.canvas.style.cursor = 'default';
        }
    }

    showTooltip(point, mouseX, mouseY) {
        if (!this.tooltip) {
            this.tooltip = document.createElement('div');
            this.tooltip.style.cssText = `
                position: fixed;
                background: rgba(0,0,0,0.9);
                color: white;
                padding: 10px 14px;
                border-radius: 8px;
                font-size: 13px;
                font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif;
                pointer-events: none;
                z-index: 10000;
                white-space: nowrap;
                box-shadow: 0 4px 12px rgba(0,0,0,0.3);
                border: 1px solid rgba(255,255,255,0.1);
            `;
            document.body.appendChild(this.tooltip);
        }

        const time = new Date(point.timestamp).toLocaleTimeString();
        const value = this.options.isInteger ?
            point.value.toString() :
            point.value.toFixed(2);

        this.tooltip.innerHTML = `
            <div style="font-weight: bold; margin-bottom: 4px;">${this.options.title || 'Value'}</div>
            <div><strong>Value:</strong> ${value}</div>
            <div><strong>Time:</strong> ${time}</div>
        `;

        const rect = this.canvas.getBoundingClientRect();
        const tooltipX = rect.left + mouseX + 15;
        const tooltipY = rect.top + mouseY - 10;

        // Keep tooltip in viewport
        const tooltipRect = this.tooltip.getBoundingClientRect();
        const viewportWidth = window.innerWidth;
        const viewportHeight = window.innerHeight;

        this.tooltip.style.left = Math.min(tooltipX, viewportWidth - tooltipRect.width - 10) + 'px';
        this.tooltip.style.top = Math.max(10, Math.min(tooltipY, viewportHeight - tooltipRect.height - 10)) + 'px';
        this.tooltip.style.display = 'block';
    }

    hideTooltip() {
        if (this.tooltip) {
            this.tooltip.style.display = 'none';
        }
        this.canvas.style.cursor = 'default';
    }

    draw() {
        if (this.data.length === 0) return;

        const { ctx, options } = this.options;
        const { padding } = this.options;

        // Use display dimensions for calculations
        const chartWidth = this.displayWidth - padding * 2;
        const chartHeight = this.displayHeight - padding * 2;

        // Clear canvas
        this.ctx.fillStyle = this.options.backgroundColor;
        this.ctx.fillRect(0, 0, this.displayWidth, this.displayHeight);

        // Find min/max values
        const values = this.data.map(d => d.value);
        const minValue = Math.min(...values);
        const maxValue = Math.max(...values);
        const valueRange = maxValue - minValue || 1;

        // Draw grid
        this.ctx.strokeStyle = this.options.gridColor;
        this.ctx.lineWidth = 1;

        // Horizontal grid lines
        for (let i = 0; i <= 4; i++) {
            const y = padding + (chartHeight * i) / 4;
            this.ctx.beginPath();
            this.ctx.moveTo(padding, y);
            this.ctx.lineTo(padding + chartWidth, y);
            this.ctx.stroke();

            // Y-axis labels
            this.ctx.fillStyle = this.options.textColor;
            this.ctx.font = '12px sans-serif';
            const labelValue = maxValue - (valueRange * i) / 4;
            const label = this.options.isInteger ?
                Math.round(labelValue).toString() :
                labelValue.toFixed(1);
            this.ctx.fillText(label, 5, y + 4);
        }

        // Vertical grid lines
        for (let i = 0; i <= 4; i++) {
            const x = padding + (chartWidth * i) / 4;
            this.ctx.beginPath();
            this.ctx.moveTo(x, padding);
            this.ctx.lineTo(x, padding + chartHeight);
            this.ctx.stroke();
        }

        // Draw line
        if (this.data.length > 1) {
            this.ctx.strokeStyle = this.options.lineColor;
            this.ctx.lineWidth = 2;
            this.ctx.beginPath();

            this.data.forEach((point, index) => {
                let x;
                if (this.data.length === 1) {
                    x = padding + chartWidth / 2;
                } else {
                    x = padding + (chartWidth * index) / (this.data.length - 1);
                }
                const y = padding + chartHeight - ((point.value - minValue) / valueRange) * chartHeight;

                if (index === 0) {
                    this.ctx.moveTo(x, y);
                } else {
                    this.ctx.lineTo(x, y);
                }
            });
            this.ctx.stroke();
        }

        // Draw points (always draw points, even for single data point)
        this.ctx.fillStyle = this.options.pointColor;
        this.data.forEach((point, index) => {
            let x;
            if (this.data.length === 1) {
                x = padding + chartWidth / 2;
            } else {
                x = padding + (chartWidth * index) / (this.data.length - 1);
            }
            const y = padding + chartHeight - ((point.value - minValue) / valueRange) * chartHeight;

            this.ctx.beginPath();
            this.ctx.arc(x, y, 5, 0, 2 * Math.PI);
            this.ctx.fill();

            // Add white border to make dots more visible
            this.ctx.strokeStyle = 'white';
            this.ctx.lineWidth = 2;
            this.ctx.stroke();
        });

        // Title
        if (this.options.title) {
            this.ctx.fillStyle = this.options.textColor;
            this.ctx.font = 'bold 14px sans-serif';
            this.ctx.textAlign = 'center';
            this.ctx.fillText(this.options.title, this.displayWidth / 2, 20);
            this.ctx.textAlign = 'left';
        }
    }

    // Cleanup method
    destroy() {
        if (this.tooltip && this.tooltip.parentNode) {
            this.tooltip.parentNode.removeChild(this.tooltip);
        }
    }
}
