const api = require('../../../utils/request');
const session = require('../../../utils/session');
const { statusInfo } = require('../../../utils/format');

function toRow(row) {
  const info = statusInfo(row.status);
  const graded = row.status === 'GRADED';
  const submitted = row.status === 'SUBMITTED';
  return {
    ...row,
    statusKey: row.status || '',
    statusText: info.text,
    statusCls: info.cls,
    graded,
    showProgress: !graded && !submitted,
    percent: row.questionCount > 0 ? Math.round((row.answeredCount / row.questionCount) * 100) : 0,
    action: graded ? '查看成绩' : submitted ? '查看状态' : row.status === 'IN_PROGRESS' ? '继续做题' : '开始做题'
  };
}

Page({
  data: { username: '', summary: '', groups: [], loaded: false },

  onLoad() {
    const current = session.get();
    if (!current) {
      session.logout();
      return;
    }
    this.setData({ username: current.username });
  },

  onShow() {
    this.load();
  },

  async onPullDownRefresh() {
    await this.load();
    wx.stopPullDownRefresh();
  },

  async load() {
    try {
      const rows = (await api.get('/api/student/papers')).map(toRow);
      const todo = rows.filter((r) => !r.status || r.status === 'IN_PROGRESS');
      const groups = [
        { title: '待完成', rows: todo },
        { title: '等待老师判卷', rows: rows.filter((r) => r.status === 'SUBMITTED') },
        { title: '已出成绩', rows: rows.filter((r) => r.status === 'GRADED') }
      ].filter((g) => g.rows.length > 0);
      this.setData({
        groups,
        summary: todo.length > 0 ? '还有 ' + todo.length + ' 篇题没有做完' : '题目都做完了',
        loaded: true
      });
    } catch (e) {
      this.setData({ loaded: true });
    }
  },

  onOpen(e) {
    const { id, status } = e.currentTarget.dataset;
    const finished = status === 'SUBMITTED' || status === 'GRADED';
    wx.navigateTo({
      url: (finished ? '/pages/student/result/result?id=' : '/pages/student/exam/exam?id=') + id
    });
  },

  onLogout() {
    session.logout();
  }
});
