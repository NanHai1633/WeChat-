const api = require('../../utils/request');
const session = require('../../utils/session');
const { toast } = require('../../utils/format');

Page({
  data: { username: '', password: '', loading: false },

  onLoad() {
    const current = session.get();
    if (current) {
      wx.reLaunch({ url: session.homeOf(current.role) });
    }
  },

  onUsername(e) {
    this.setData({ username: e.detail.value });
  },
  onPassword(e) {
    this.setData({ password: e.detail.value });
  },

  async onLogin() {
    const { username, password } = this.data;
    if (!username.trim() || !password) {
      toast('请输入账号和密码');
      return;
    }
    this.setData({ loading: true });
    try {
      // 账号是学生还是管理员由后端判断，按返回的角色进入对应页面
      const res = await api.post('/api/auth/login', { username: username.trim(), password });
      session.save({ token: res.token, userId: res.userId, username: res.username, role: res.role });
      wx.reLaunch({ url: session.homeOf(res.role) });
    } catch (e) {
      // 错误已由 request 提示
    } finally {
      this.setData({ loading: false });
    }
  }
});
