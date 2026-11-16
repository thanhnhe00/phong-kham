// Cấu hình Metro cho app bệnh nhân.
// Bản web khi chạy thử (npx expo start --web) gọi '/api' cùng địa chỉ trang; Metro chuyển tiếp sang backend
// để trình duyệt không bị chặn khác cổng (CORS). Điện thoại thật gọi thẳng IP LAN trong app.json → extra.apiUrl.
const { getDefaultConfig } = require('expo/metro-config');
const { proxyApi } = require('./e2e/proxy');

const config = getDefaultConfig(__dirname);
const previous = config.server?.enhanceMiddleware;
config.server = {
  ...config.server,
  enhanceMiddleware: (middleware, server) => {
    const base = previous ? previous(middleware, server) : middleware;
    return (req, res, next) => (proxyApi(req, res) ? undefined : base(req, res, next));
  },
};

module.exports = config;
