import QRCode from 'qrcode';

/** Ma trận ô vuông của mã QR (true = ô đen), vẽ bằng react-native-svg. */
export function qrMatrix(text: string): boolean[][] {
  const { modules } = QRCode.create(text, { errorCorrectionLevel: 'M' });
  const n = modules.size;
  const rows: boolean[][] = [];
  for (let r = 0; r < n; r++) {
    const row: boolean[] = [];
    for (let c = 0; c < n; c++) row.push(Boolean(modules.get(r, c)));
    rows.push(row);
  }
  return rows;
}
