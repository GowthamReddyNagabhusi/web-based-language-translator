// Translations storage
let history = [];
const maxHistory = 50;
const loadingMessages = [
    'Thinking in another language...',
    'Consulting the language oracle...',
    'Weaving words across cultures...',
    'Unlocking linguistic secrets...',
    'Bridging the language gap...'
];
const suggestions = [
    '💡 Tip: Use Ctrl+Enter to quickly translate!',
    '🔀 Pro: Try copying text from web pages!',
    '🌍 Fun fact: This works for 8 languages!',
    '⚡ Speed: Translations happen instantly!',
    '✨ Magic: Emojis translate perfectly too!',
    '📱 Mobile: You can use this on phone too!',
    '🗣️ Accuracy: Works best with sentences!',
    '🚀 Quick: No account or signup needed!',
    '💬 Social: Share translations with friends!',
    '🎓 Learning: Great for language students!'
];
const celebrationEmojis = ['✨', '🎉', '🌟', '💫', '🎊', '🔥'];

function getRandomItem(arr) {
    return arr[Math.floor(Math.random() * arr.length)];
}

// Load and save history
function loadHistory() {
    const stored = localStorage.getItem('translationHistory');
    return stored ? JSON.parse(stored) : [];
}

function saveHistory() {
    localStorage.setItem('translationHistory', JSON.stringify(history.slice(0, maxHistory)));
}

function renderHistory() {
    const historyList = document.getElementById('historyList');
    historyList.innerHTML = '';
    
    if (history.length === 0) {
        historyList.innerHTML = '<li style="color: #999; font-style: italic; text-align: center; padding: 20px;">No translations yet...</li>';
        return;
    }

    [...history].reverse().slice(0, 30).forEach((item, idx) => {
        const li = document.createElement('li');
        const timeAgo = getTimeAgo(item.at);
        const displayInput = item.input.length > 30 ? item.input.substring(0, 27) + '...' : item.input;
        const displayOutput = item.output.length > 30 ? item.output.substring(0, 27) + '...' : item.output;
        li.innerHTML = `
            <strong>${displayInput}</strong><br>
            <span style="color: #667eea; font-size: 11px;">↓ ${displayOutput}</span>
            <span class="time">${timeAgo}</span>
        `;
        li.onclick = () => {
            document.getElementById('inputText').value = item.input;
            document.getElementById('language').value = item.lang;
            document.getElementById('output').textContent = item.output;
            document.getElementById('charCount').textContent = item.input.length;
        };
        historyList.appendChild(li);
    });
}

function getTimeAgo(timestamp) {
    const seconds = Math.floor((Date.now() - timestamp) / 1000);
    if (seconds < 60) return 'Just now';
    const minutes = Math.floor(seconds / 60);
    if (minutes < 60) return `${minutes}m ago`;
    const hours = Math.floor(minutes / 60);
    if (hours < 24) return `${hours}h ago`;
    const days = Math.floor(hours / 24);
    return `${days}d ago`;
}

// Show spinner
function showSpinner(show) {
    const spinner = document.getElementById('spinner');
    if (show) {
        spinner.classList.remove('hidden');
        const loadingMsg = getRandomItem(loadingMessages);
        spinner.innerHTML = `
            <div class="dot"></div>
            <p style="text-align: center; margin-top: 20px; color: white; font-weight: 600; font-size: 14px;">${loadingMsg}</p>
        `;
    } else {
        spinner.classList.add('hidden');
    }
}

// Main translate function
async function translate() {
    const text = document.getElementById('inputText').value.trim();
    const lang = document.getElementById('language').value;
    const outputEl = document.getElementById('output');

    if (!text) {
        outputEl.textContent = 'Please enter some text first!';
        return;
    }

    showSpinner(true);

    try {
        const response = await fetch('/translate', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ text, lang })
        });

        if (!response.ok) {
            throw new Error(`HTTP error! status: ${response.status}`);
        }

        const result = await response.text();
        outputEl.textContent = result;

        // Add to history
        const entry = {
            input: text,
            output: result,
            lang: lang,
            at: Date.now()
        };
        history.unshift(entry);
        saveHistory();
        renderHistory();

        // Show celebration
        outputEl.textContent = `${getRandomItem(celebrationEmojis)} ${result}`;
        setTimeout(() => {
            outputEl.textContent = result;
        }, 1500);

        // Show suggestion
        const suggestionEl = document.getElementById('suggestion');
        if (suggestionEl) {
            const suggestion = getRandomItem(suggestions);
            suggestionEl.textContent = suggestion;
            setTimeout(() => {
                suggestionEl.textContent = '';
            }, 5000);
        }
    } catch (error) {
        console.error('Translation error:', error);
        outputEl.textContent = `Error: ${error.message}`;
    } finally {
        showSpinner(false);
    }
}

// Template item click handler
function setupTemplateListeners() {
    const templates = document.querySelectorAll('.template-item');
    templates.forEach(template => {
        template.addEventListener('click', () => {
            const text = template.getAttribute('data-text');
            const lang = template.getAttribute('data-lang');
            document.getElementById('inputText').value = text;
            document.getElementById('language').value = lang;
            document.getElementById('charCount').textContent = text.length;
            // Auto-translate
            setTimeout(() => translate(), 100);
        });
    });
}

// Tab switching
function setupTabListeners() {
    const tabButtons = document.querySelectorAll('.tab-btn');
    const tabContents = document.querySelectorAll('.tab-content');
    
    tabButtons.forEach(btn => {
        btn.addEventListener('click', () => {
            // Remove active from all
            tabButtons.forEach(b => b.classList.remove('active'));
            tabContents.forEach(c => c.classList.remove('active'));
            
            // Add active to clicked tab
            btn.classList.add('active');
            const tabName = btn.getAttribute('data-tab');
            const targetTab = document.getElementById(tabName);
            if (targetTab) targetTab.classList.add('active');
        });
    });
}

// Event listeners
document.getElementById('translateBtn').addEventListener('click', translate);
document.getElementById('inputText').addEventListener('keyup', (e) => {
    document.getElementById('charCount').textContent = e.target.value.length;
    if (e.ctrlKey && e.key === 'Enter') {
        translate();
    }
});

document.getElementById('clearBtn').addEventListener('click', () => {
    document.getElementById('inputText').value = '';
    document.getElementById('output').textContent = 'Your translation will appear here...';
    document.getElementById('charCount').textContent = '0';
    const suggestion = getRandomItem(suggestions);
    const suggestionEl = document.getElementById('suggestion');
    if (suggestionEl) {
        suggestionEl.textContent = suggestion;
        setTimeout(() => suggestionEl.textContent = '', 5000);
    }
});

document.getElementById('copyOutput').addEventListener('click', () => {
    const output = document.getElementById('output').textContent;
    if (output.includes('will appear') || output.includes('Error')) return;
    navigator.clipboard.writeText(output).then(() => {
        const originalText = document.getElementById('copyOutput').textContent;
        document.getElementById('copyOutput').textContent = `${getRandomItem(celebrationEmojis)} Copied!`;
        setTimeout(() => {
            document.getElementById('copyOutput').textContent = originalText;
        }, 1500);
    });
});

document.getElementById('clearHistory').addEventListener('click', () => {
    if (confirm('Clear all translation history?')) {
        history = [];
        saveHistory();
        renderHistory();
    }
});

console.log('🌐 Translator loaded - ready to break language barriers!');

// Initialize
history = loadHistory();
renderHistory();
setupTemplateListeners();
setupTabListeners();