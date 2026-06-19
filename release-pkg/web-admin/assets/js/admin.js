/**
 * 家收纳 管理后台 JS
 */

// Toast 提示
function showToast(msg, type = 'info') {
    const toast = document.createElement('div');
    toast.className = 'toast toast-' + type;
    toast.textContent = msg;
    document.body.appendChild(toast);
    setTimeout(() => { toast.remove(); }, 3000);
}

// AJAX 请求
async function api(url, options = {}) {
    const defaults = {
        headers: { 'Content-Type': 'application/json' },
    };
    const config = { ...defaults, ...options };
    try {
        const resp = await fetch(url, config);
        const data = await resp.json();
        if (data.code !== 0 && data.code !== undefined) {
            showToast(data.msg || '请求失败', 'error');
            return null;
        }
        return data.data !== undefined ? data.data : data;
    } catch (e) {
        showToast('网络错误', 'error');
        return null;
    }
}

// POST JSON
async function postJSON(url, body) {
    return api(url, { method: 'POST', body: JSON.stringify(body) });
}

// 确认弹窗
function confirmAction(msg) {
    return confirm(msg);
}

// 格式化文件大小
function formatSize(bytes) {
    if (bytes < 1024) return bytes + ' B';
    if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB';
    return (bytes / (1024 * 1024)).toFixed(1) + ' MB';
}

// 格式化时间
function formatTime(ts) {
    if (!ts) return '-';
    const d = new Date(ts * 1000);
    return d.toLocaleString('zh-CN');
}

// 模态框控制
function showModal(id) {
    document.getElementById(id).classList.add('show');
}
function hideModal(id) {
    document.getElementById(id).classList.remove('show');
}

// 表单序列化
function serializeForm(form) {
    const data = {};
    const inputs = form.querySelectorAll('input, select, textarea');
    inputs.forEach(el => {
        if (el.name) {
            if (el.type === 'checkbox') {
                data[el.name] = el.checked ? 1 : 0;
            } else if (el.type === 'number') {
                data[el.name] = Number(el.value);
            } else {
                data[el.name] = el.value;
            }
        }
    });
    return data;
}

// 分页
function renderPagination(total, page, pageSize, callback) {
    const totalPages = Math.ceil(total / pageSize);
    if (totalPages <= 1) return '';
    let html = '<div class="pagination">';
    if (page > 1) html += `<a href="javascript:void(0)" onclick="${callback}(${page - 1})">上一页</a>`;
    for (let i = 1; i <= totalPages; i++) {
        if (i === page) {
            html += `<span class="active">${i}</span>`;
        } else if (Math.abs(i - page) <= 2 || i === 1 || i === totalPages) {
            html += `<a href="javascript:void(0)" onclick="${callback}(${i})">${i}</a>`;
        } else if (Math.abs(i - page) === 3) {
            html += '<span>...</span>';
        }
    }
    if (page < totalPages) html += `<a href="javascript:void(0)" onclick="${callback}(${page + 1})">下一页</a>`;
    html += '</div>';
    return html;
}

// 通用初始化
document.addEventListener('DOMContentLoaded', () => {
    // 点击弹窗外部关闭
    document.querySelectorAll('.modal-overlay').forEach(overlay => {
        overlay.addEventListener('click', (e) => {
            if (e.target === overlay) overlay.classList.remove('show');
        });
    });
});
