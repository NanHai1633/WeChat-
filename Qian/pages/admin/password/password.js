const api = require('../../../utils/request');
const session = require('../../../utils/session');
const { toast } = require('../../../utils/format');

Page({
  data: { oldPassword: '', newPassword: '' },

  onOld(e) {
    this.setData({ oldPassword: e.detail.value });
  },
  onNew(e) {
    this.setData({ newPassword: e.detail.value });
  },

  async onSubmit() {
    const { oldPassword, newPassword } = this.data;
    if (!oldPassword || !newPassword) {
      toast('请填写原密码和新密码');
      return;
    }
    try {
      await api.put('/api/admin/password', { oldPassword, newPassword });
      toast('修改成功，请重新登录');
      session.logout();
    } catch (e) {
      // 错误已由 request 提示
    }
  }
});
