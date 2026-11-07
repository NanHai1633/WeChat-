const api = require('../../../utils/request');
const { statusInfo } = require('../../../utils/format');

const PAGE_SIZE = 20;

Page({
  data: { name: '', rows: [], total: 0, loaded: false },

  onLoad(query) {
    this.studentId = query.id;
    this.setData({ name: decodeURIComponent(query.name || '') });
  },

  onShow() {
    this.load(true);
  },

  onReachBottom() {
    if (this.data.rows.length < this.data.total) {
      this.load(false);
    }
  },

  async load(reset) {
    const page = reset ? 1 : this.page + 1;
    try {
      const res = await api.get('/api/admin/students/' + this.studentId + '/submissions', { page, size: PAGE_SIZE });
      this.page = page;
      const rows = res.records.map((row) => {
        const info = statusInfo(row.status);
        return {
          ...row,
          statusText: info.text,
          statusCls: info.cls,
          scoreText: row.status === 'GRADED' ? '总分 ' + row.totalScore : ''
        };
      });
      this.setData({ rows: reset ? rows : this.data.rows.concat(rows), total: res.total, loaded: true });
    } catch (e) {
      this.setData({ loaded: true });
    }
  },

  onOpen(e) {
    const id = e.currentTarget.dataset.id;
    if (id) {
      wx.navigateTo({ url: '/pages/admin/grade/grade?id=' + id });
    }
  }
});
