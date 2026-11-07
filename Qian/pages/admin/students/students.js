const api = require('../../../utils/request');
const { confirm, prompt, toast, onTab } = require('../../../utils/format');

const PAGE_SIZE = 20;

Page({
  data: { students: [], total: 0, keyword: '', newName: '', newPassword: '', showCreate: false, loaded: false },

  onTab,

  onToggleCreate() {
    this.setData({ showCreate: !this.data.showCreate });
  },

  onLoad() {
    this.load(true);
  },

  onReachBottom() {
    if (this.data.students.length < this.data.total) {
      this.load(false);
    }
  },

  async load(reset) {
    const page = reset ? 1 : this.page + 1;
    try {
      const res = await api.get('/api/admin/students', { keyword: this.data.keyword, page, size: PAGE_SIZE });
      this.page = page;
      this.setData({
        students: reset ? res.records : this.data.students.concat(res.records),
        total: res.total,
        loaded: true
      });
    } catch (e) {
      this.setData({ loaded: true });
    }
  },

  onNewName(e) {
    this.setData({ newName: e.detail.value });
  },
  onNewPassword(e) {
    this.setData({ newPassword: e.detail.value });
  },
  onKeyword(e) {
    this.setData({ keyword: e.detail.value });
  },
  onSearch() {
    this.load(true);
  },

  async onCreate() {
    const { newName, newPassword } = this.data;
    try {
      await api.post('/api/admin/students', { username: newName.trim(), password: newPassword });
      toast('注册成功');
      this.setData({ newName: '', newPassword: '' });
      this.load(true);
    } catch (e) {
      // 错误已由 request 提示
    }
  },

  onOpen(e) {
    const { id, name } = e.currentTarget.dataset;
    wx.navigateTo({
      url: '/pages/admin/student-detail/student-detail?id=' + id + '&name=' + encodeURIComponent(name)
    });
  },

  async onReset(e) {
    const { id, name } = e.currentTarget.dataset;
    const password = await prompt('重置 ' + name + ' 的密码', '新密码（6-64 位）');
    if (!password) {
      return;
    }
    try {
      await api.put('/api/admin/students/' + id + '/password', { password });
      toast('密码已重置');
    } catch (err) {
      // 错误已由 request 提示
    }
  },

  async onDelete(e) {
    const { id, name } = e.currentTarget.dataset;
    if (!(await confirm('确定删除账号 ' + name + ' 吗？删除后不能登录，历史成绩会保留。'))) {
      return;
    }
    try {
      await api.del('/api/admin/students/' + id);
      toast('已删除');
      this.load(true);
    } catch (err) {
      // 错误已由 request 提示
    }
  }
});
