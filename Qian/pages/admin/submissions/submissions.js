const api = require('../../../utils/request');
const { statusInfo } = require('../../../utils/format');

const PAGE_SIZE = 20;

Page({
  data: {
    filters: [
      { value: '', label: '全部' },
      { value: 'SUBMITTED', label: '待判卷' },
      { value: 'GRADED', label: '已判完' }
    ],
    filter: '',
    rows: [],
    total: 0,
    loaded: false
  },

  onLoad(query) {
    this.paperId = query.paperId;
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
    const params = { page, size: PAGE_SIZE };
    if (this.data.filter) {
      params.status = this.data.filter;
    }
    try {
      const res = await api.get('/api/admin/papers/' + this.paperId + '/submissions', params);
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

  onFilter(e) {
    this.setData({ filter: e.currentTarget.dataset.value });
    this.load(true);
  },

  onOpen(e) {
    const id = e.currentTarget.dataset.id;
    if (id) {
      wx.navigateTo({ url: '/pages/admin/grade/grade?id=' + id });
    }
  }
});
