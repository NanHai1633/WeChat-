const api = require('../../../utils/request');
const { MAX_IMAGES, pickAndUpload } = require('../../../utils/images');
const { fileUrls, toast, onPreview } = require('../../../utils/format');

const TYPES = [
  { value: 'SINGLE_CHOICE', label: '选择题' },
  { value: 'TRUE_FALSE', label: '判断题' },
  { value: 'FILL_BLANK', label: '填空题' },
  { value: 'ESSAY', label: '大题' }
];

function emptyOptions() {
  return ['A', 'B', 'C', 'D'].map((key) => ({ key, field: 'option' + key, value: '' }));
}

Page({
  data: {
    types: TYPES,
    isNew: true,
    type: 'SINGLE_CHOICE',
    content: '',
    optionKeys: emptyOptions(),
    answer: '',
    blanks: [''],
    score: '',
    imageUrls: [],
    maxImages: MAX_IMAGES,
    saving: false
  },

  onPreview,

  onLoad(query) {
    this.paperId = query.paperId;
    this.questionId = query.id;
    this.images = [];
    this.added = 0;
    this.setData({ isNew: !query.id });
    wx.setNavigationBarTitle({ title: query.id ? '修改题目' : '添加题目' });
    if (query.id) {
      this.loadQuestion();
    }
  },

  async loadQuestion() {
    try {
      const paper = await api.get('/api/admin/papers/' + this.paperId);
      const q = paper.questions.find((item) => String(item.id) === String(this.questionId));
      if (!q) {
        toast('题目不存在');
        return;
      }
      this.images = q.images;
      this.setData({
        type: q.type,
        content: q.content,
        answer: q.answer || '',
        score: String(q.score),
        blanks: q.blanks ? q.blanks.map((b) => b.join('|')) : [''],
        optionKeys: emptyOptions().map((o) => ({ ...o, value: q[o.field] || '' })),
        imageUrls: fileUrls(q.images)
      });
    } catch (e) {
      // 错误已由 request 提示
    }
  },

  onType(e) {
    // 切换题型时答案含义不同，要清空
    if (e.currentTarget.dataset.type !== this.data.type) {
      this.setData({ type: e.currentTarget.dataset.type, answer: '' });
    }
  },

  onField(e) {
    this.setData({ [e.currentTarget.dataset.field]: e.detail.value });
  },

  onOption(e) {
    const field = e.currentTarget.dataset.field;
    this.setData({
      optionKeys: this.data.optionKeys.map((o) => (o.field === field ? { ...o, value: e.detail.value } : o))
    });
  },

  onAnswer(e) {
    this.setData({ answer: e.currentTarget.dataset.value });
  },

  onBlank(e) {
    const blanks = this.data.blanks.slice();
    blanks[e.currentTarget.dataset.i] = e.detail.value;
    this.setData({ blanks });
  },

  onAddBlank() {
    this.setData({ blanks: this.data.blanks.concat('') });
  },

  onRemoveBlank(e) {
    const blanks = this.data.blanks.slice();
    blanks.splice(e.currentTarget.dataset.i, 1);
    this.setData({ blanks });
  },

  async onAddImage() {
    const names = await pickAndUpload('/api/admin/files', this.images.length);
    if (names.length > 0) {
      this.images = this.images.concat(names);
      this.setData({ imageUrls: fileUrls(this.images) });
    }
  },

  onRemoveImage(e) {
    this.images = this.images.filter((_, i) => i !== e.currentTarget.dataset.i);
    this.setData({ imageUrls: fileUrls(this.images) });
  },

  buildBody() {
    const { type, content, answer, score, optionKeys, blanks } = this.data;
    const body = { type, content, score: Number(score), images: this.images };
    if (type === 'SINGLE_CHOICE') {
      optionKeys.forEach((o) => {
        body[o.field] = o.value;
      });
    }
    if (type === 'FILL_BLANK') {
      body.blanks = blanks.map((b) => b.split('|').map((s) => s.trim()).filter((s) => s));
    } else {
      body.answer = answer;
    }
    return body;
  },

  /** 继续加题：保留题型和分值，其他清空。 */
  resetForNext() {
    this.images = [];
    this.setData({
      content: '',
      optionKeys: emptyOptions(),
      answer: '',
      blanks: [''],
      imageUrls: []
    });
    wx.pageScrollTo({ scrollTop: 0, duration: 0 });
  },

  async onSave(e) {
    if (this.data.saving) {
      return;
    }
    if (!this.data.score || Number.isNaN(Number(this.data.score))) {
      toast('请输入正确的分值');
      return;
    }
    const continueAdding = e.currentTarget.dataset.continue;
    const body = this.buildBody();
    this.setData({ saving: true });
    try {
      if (this.questionId) {
        await api.put('/api/admin/questions/' + this.questionId, body);
      } else {
        await api.post('/api/admin/papers/' + this.paperId + '/questions', { questions: [body] });
      }
      if (continueAdding) {
        this.added += 1;
        toast('已保存，这次加了 ' + this.added + ' 题');
        this.resetForNext();
      } else {
        toast('已保存');
        wx.navigateBack();
      }
    } catch (err) {
      // 错误已由 request 提示
    } finally {
      this.setData({ saving: false });
    }
  }
});
