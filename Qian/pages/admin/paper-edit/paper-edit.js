const api = require('../../../utils/request');
const {
  TYPE_ORDER, typeLabel, sortByType, optionsText, correctBlanksText, fileUrls, confirm, prompt, toast, onPreview
} = require('../../../utils/format');

const TF_LABELS = { TRUE: '正确', FALSE: '错误' };

function answerOf(q) {
  if (q.type === 'FILL_BLANK') {
    return correctBlanksText(q.blanks);
  }
  if (q.type === 'TRUE_FALSE') {
    return TF_LABELS[q.answer];
  }
  return q.answer || '（无）';
}

Page({
  data: { paper: null, groups: [], questionCount: 0, published: false },

  onPreview,

  onLoad(query) {
    this.paperId = query.id;
  },

  onShow() {
    this.load();
  },

  async load() {
    try {
      const paper = await api.get('/api/admin/papers/' + this.paperId);
      const questions = sortByType(paper.questions).map((q, i) => ({
        ...q,
        no: i + 1,
        answerText: answerOf(q),
        optionsText: optionsText(q),
        imageUrls: fileUrls(q.images)
      }));
      this.setData({
        paper,
        questionCount: questions.length,
        published: paper.status === 'PUBLISHED',
        groups: TYPE_ORDER
          .map((type) => ({ type, label: typeLabel(type), questions: questions.filter((q) => q.type === type) }))
          .filter((g) => g.questions.length > 0)
      });
    } catch (e) {
      // 错误已由 request 提示
    }
  },

  async onRename() {
    const title = await prompt('修改试卷名称', '请输入试卷名称', this.data.paper.title);
    if (!title) {
      return;
    }
    try {
      await api.put('/api/admin/papers/' + this.paperId, { title });
      this.load();
    } catch (e) {
      // 错误已由 request 提示
    }
  },

  async onToggle() {
    const status = this.data.published ? 'DRAFT' : 'PUBLISHED';
    try {
      await api.put('/api/admin/papers/' + this.paperId + '/status', { status });
      toast(status === 'PUBLISHED' ? '已发布，学生现在能看到了' : '已下架');
      this.load();
    } catch (e) {
      // 错误已由 request 提示
    }
  },

  onSubmissions() {
    wx.navigateTo({ url: '/pages/admin/submissions/submissions?paperId=' + this.paperId });
  },

  async onDelete() {
    if (!(await confirm('学生将看不到这篇试卷，已有成绩记录会保留。', '删除试卷'))) {
      return;
    }
    try {
      await api.del('/api/admin/papers/' + this.paperId);
      wx.navigateBack();
    } catch (e) {
      // 错误已由 request 提示
    }
  },

  onAddQuestion() {
    wx.navigateTo({ url: '/pages/admin/question-edit/question-edit?paperId=' + this.paperId });
  },

  onEditQuestion(e) {
    wx.navigateTo({
      url: '/pages/admin/question-edit/question-edit?paperId=' + this.paperId + '&id=' + e.currentTarget.dataset.id
    });
  },

  async onDeleteQuestion(e) {
    if (!(await confirm('确定删除这道题吗？', '删除题目'))) {
      return;
    }
    try {
      await api.del('/api/admin/questions/' + e.currentTarget.dataset.id);
      this.load();
    } catch (err) {
      // 错误已由 request 提示
    }
  }
});
