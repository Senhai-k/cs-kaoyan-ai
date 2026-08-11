export function downloadExport(filename: string, data: unknown, format: 'json' | 'csv') {
  const content = format === 'json'
    ? JSON.stringify(data, null, 2)
    : toCsv(Array.isArray(data) ? data : []);
  const blob = new Blob([content], { type: format === 'json' ? 'application/json;charset=utf-8' : 'text/csv;charset=utf-8' });
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = filename;
  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link);
  URL.revokeObjectURL(url);
}

export function toCsv(rows: unknown[]) {
  if (rows.length === 0) return '';
  const records = rows as Array<Record<string, unknown>>;
  const headers = Object.keys(records[0]);
  const escapeCell = (value: unknown) => `"${String(value ?? '').replace(/"/g, '""')}"`;
  return [
    headers.join(','),
    ...records.map((row) => headers.map((header) => escapeCell(row[header])).join(','))
  ].join('\n');
}

export function todayStamp() {
  const now = new Date();
  const year = now.getFullYear();
  const month = String(now.getMonth() + 1).padStart(2, '0');
  const day = String(now.getDate()).padStart(2, '0');
  return `${year}${month}${day}`;
}
