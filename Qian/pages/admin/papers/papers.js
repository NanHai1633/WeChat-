const api = require('../../../utils/request');
const { prompt, onTab } = require('../../../utils/format');

const PAGE_SIZE = 20;

Page({
  data: { papers: [], total: 0, loaded: false },

  onTab,

  onShow() {
    this.load(true);
  },

  onReachBottom() {
    if (this.data.papers.length < this.data.total) {
      this.load(false);
    }
  },

  async load(reset) {
    const page = reset ? 1 : this.page + 1;
    try {
      const res = await api.get('/api/admin/papers', { page, size: PAGE_SIZE });
      this.page = page;
      this.setData({
        papers: reset ? res.records : this.data.papers.concat(res.records),
        total: res.total,
        loaded: true
      });
    } catch (e) {
      this.setData({ loaded: true });
    }
  },

  onOpen(e) {
    wx.navigateTo({ url: '/pages/admin/paper-edit/paper-edit?id=' + e.currentTarget.dataset.id });
  },

  async onCreate() {
    const title = await prompt('新建试卷', '请输入试卷名称');
    if (!title) {
      return;
    }
    try {
      const paper = await api.post('/api/admin/papers', { title });
      wx.navigateTo({ url: '/pages/admin/paper-edit/paper-edit?id=' + paper.id });
    } catch (e) {
      // 错误已由 request 提示
    }
  }
});
