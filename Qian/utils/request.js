const config = require('./config');
const session = require('./session');

/** 发请求，成功时返回 data；失败时默认弹提示并 reject。 */
function request(method, path, data, options) {
  const opts = options || {};
  const current = session.get();
  const header = {};
  if (current && current.token) {
    header.Authorization = 'Bearer ' + current.token;
  }
  return new Promise((resolve, reject) => {
    wx.request({
      url: config.baseURL + path,
      method,
      data,
      header,
      success(res) {
        const body = res.data || {};
        if (res.statusCode === 200 && body.code === 0) {
          resolve(body.data);
          return;
        }
        // 登录接口的 401 是账号密码错误，不是登录失效
        if (res.statusCode === 401 && path !== '/api/auth/login') {
          session.logout();
        }
        const error = new Error(body.message || '请求失败（' + res.statusCode + '）');
        error.status = res.statusCode;
        if (!opts.silent) {
          wx.showToast({ title: error.message, icon: 'none', duration: 2500 });
        }
        reject(error);
      },
      fail() {
        const error = new Error('网络连接失败，请检查后端是否启动');
        if (!opts.silent) {
          wx.showToast({ title: error.message, icon: 'none', duration: 2500 });
        }
        reject(error);
      }
    });
  });
}

module.exports = {
  get: (path, data, options) => request('GET', path, data, options),
  post: (path, data, options) => request('POST', path, data, options),
  put: (path, data, options) => request('PUT', path, data, options),
  del: (path, options) => request('DELETE', path, undefined, options)
};
