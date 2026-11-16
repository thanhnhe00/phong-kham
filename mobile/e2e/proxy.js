// Chuyển tiếp '/api/...' sang backend (mặc định http://localhost:8080). Dùng chung cho Metro và máy chủ kiểm thử.
const http = require('node:http');

const target = new URL(process.env.API_PROXY_TARGET ?? 'http://localhost:8080');

/** Trả true nếu đã xử lý yêu cầu (là đường dẫn /api) */
function proxyApi(req, res) {
  if (!req.url || !req.url.startsWith('/api/')) return false;
  const upstream = http.request(
    { hostname: target.hostname, port: target.port, path: req.url, method: req.method, headers: { ...req.headers, host: target.host } },
    (up) => {
      res.writeHead(up.statusCode ?? 502, up.headers);
      up.pipe(res);
    },
  );
  upstream.on('error', () => {
    res.writeHead(502, { 'Content-Type': 'application/json; charset=utf-8' });
    res.end(JSON.stringify({ message: `Không kết nối được backend tại ${target.origin}` }));
  });
  req.pipe(upstream);
  return true;
}

module.exports = { proxyApi };
