const MAX_LINES = 5000;
let lines = [];
let sink = null;

function levelOf(text) {
  if (/\b(ERROR|FATAL|Exception|Caused by)\b/.test(text)) return 'error';
  if (/\bWARN\b/.test(text)) return 'warn';
  return 'info';
}

function stamp(date) {
  const two = (n) => String(n).padStart(2, '0');
  return `${two(date.getHours())}:${two(date.getMinutes())}:${two(date.getSeconds())}`;
}

function write(source, text) {
  const time = stamp(new Date());
  const entries = String(text)
    .split(/\r?\n/)
    .filter((line) => line.trim())
    .map((line) => ({ time, source, level: levelOf(line), text: line }));
  if (!entries.length) return;
  lines = lines.concat(entries);
  if (lines.length > MAX_LINES) lines = lines.slice(-(MAX_LINES - 500));
  if (sink) sink(entries);
}

module.exports = {
  write,
  all: () => lines,
  clear: () => { lines = []; },
  setSink: (fn) => { sink = fn; },
};
