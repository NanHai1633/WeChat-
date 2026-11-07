const KEY = 'session';

function get() {
  return wx.getStorageSync(KEY) || null;
}

function save(session) {
  wx.setStorageSync(KEY, session);
}

function clear() {
  wx.removeStorageSync(KEY);
}

function homeOf(role) {
  return role === 'ADMIN' ? '/pages/admin/home/home' : '/pages/student/papers/papers';
}

function logout() {
  clear();
  wx.reLaunch({ url: '/pages/login/login' });
}

module.exports = { get, save, clear, homeOf, logout };
