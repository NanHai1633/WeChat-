const api = require('../../../utils/request');
const session = require('../../../utils/session');
const { onTab } = require('../../../utils/format');

const PAGE_SIZE = 20;

Page({
  data: { username: '', pending: [], pendingTotal: 0, paperTotal: 0, studentTotal: 0, loaded: false },

  onTab,

  onLoad() {
    const current = session.get();
    if (!current) {
      session.logout();
      return;
    }
    this.setData({ username: current.username });
  },

  onShow() {
    this.load(true);
  },

  async onPullDownRefresh() {
    await this.load(true);
    wx.stopPullDownRefresh();
  },

  onReachBottom() {
    if (this.data.pending.length < this.data.pendingTotal) {
      this.load(false);
    }
  },

  async load(reset) {
    const page = reset ? 1 : this.page + 1;
    try {
      const [pending, papers, students] = await Promise.all([
        api.get('/api/admin/submissions/pending', { page, size: PAGE_SIZE }),
        api.get('/api/admin/papers', { page: 1, size: 1 }),
        api.get('/api/admin/students', { page: 1, size: 1 })
      ]);
      this.page = page;
      this.setData({
        pending: reset ? pending.records : this.data.pending.concat(pending.records),
        pendingTotal: pending.total,
        paperTotal: papers.total,
        studentTotal: students.total,
        loaded: true
      });
    } catch (e) {
      this.setData({ loaded: true });
    }
  },

  onGrade(e) {
    wx.navigateTo({ url: '/pages/admin/grade/grade?id=' + e.currentTarget.dataset.id });
  },

  onPassword() {
    wx.navigateTo({ url: '/pages/admin/password/password' });
  },

  onLogout() {
    session.logout();
  }
});
