const api = require('../../../utils/request');
const {
  typeLabel, sortByType, statusInfo, optionsText, answerText, correctBlanksText, verdict, fileUrls, toast, onPreview
} = require('../../../utils/format');

const PENDING_URL = '/api/admin/submissions/pending';

function correctText(a) {
  if (a.type === 'FILL_BLANK') {
    return correctBlanksText(a.correctBlanks);
  }
  return answerText(a.type, a.correctAnswer);
}

Page({
  data: {
    detail: null,
    essays: [],
    others: [],
    showAuto: false,
    inProgress: false,
    graded: false,
    remaining: 0,
    finishText: '完成判卷',
    statusText: '',
    statusCls: ''
  },

  onPreview,

  onLoad(query) {
    this.submissionId = query.id;
    this.load();
  },

  async load() {
    try {
      const [detail, pending] = await Promise.all([
        api.get('/api/admin/submissions/' + this.submissionId),
        api.get(PENDING_URL, { page: 1, size: 1 })
      ]);
      const info = statusInfo(detail.status);
      const graded = detail.status === 'GRADED';
      const remaining = detail.status === 'SUBMITTED' ? pending.total - 1 : 0;
      const answers = sortByType(detail.answers).map((a, i) => {
        const v = verdict(a.correct, a.score);
        return {
          ...a,
          no: i + 1,
          typeText: typeLabel(a.type),
          optionsText: optionsText(a),
          studentText: answerText(a.type, a.studentAnswer, a.studentBlanks),
          correctText: a.type === 'ESSAY' ? '' : correctText(a),
          imageUrls: fileUrls(a.images),
          photoUrls: fileUrls(a.studentImages),
          verdictText: v.text,
          verdictCls: v.cls,
          scoreInput: a.score === null ? '' : String(a.score),
          commentInput: a.comment || '',
          halfScore: String(Number(a.maxScore) / 2)
        };
      });
      const essays = answers.filter((a) => a.type === 'ESSAY');
      this.setData({
        detail,
        essays,
        others: answers.filter((a) => a.type !== 'ESSAY'),
        showAuto: essays.length === 0,
        inProgress: detail.status === 'IN_PROGRESS',
        graded,
        remaining,
        finishText: graded ? '更新总分' : remaining > 0 ? '完成，判下一份' : '完成判卷',
        statusText: info.text,
        statusCls: info.cls
      });
      wx.pageScrollTo({ scrollTop: 0, duration: 0 });
    } catch (e) {
      // 错误已由 request 提示
    }
  },

  onScore(e) {
    this.setData({ ['essays[' + e.currentTarget.dataset.i + '].scoreInput']: e.detail.value });
  },

  onQuick(e) {
    const { i, value } = e.currentTarget.dataset;
    this.setData({ ['essays[' + i + '].scoreInput']: String(value) });
  },

  onComment(e) {
    this.setData({ ['essays[' + e.currentTarget.dataset.i + '].commentInput']: e.detail.value });
  },

  onToggleAuto() {
    this.setData({ showAuto: !this.data.showAuto });
  },

  /** 把已填分数的大题提交到后端，没填的跳过，可以下次接着判。 */
  async saveGrades() {
    const grades = [];
    for (const a of this.data.essays) {
      if (a.scoreInput === '') {
        continue;
      }
      const score = Number(a.scoreInput);
      if (Number.isNaN(score)) {
        toast('分数格式不正确');
        return false;
      }
      grades.push({ questionId: a.questionId, score, comment: a.commentInput });
    }
    if (grades.length > 0) {
      await api.put('/api/admin/submissions/' + this.submissionId + '/grades', { grades });
    }
    return true;
  },

  async onSave() {
    try {
      if (await this.saveGrades()) {
        toast('已保存');
        this.load();
      }
    } catch (e) {
      // 错误已由 request 提示
    }
  },

  /** 判完自动进入下一份待判的答卷，没有了就回工作台。 */
  async onFinish() {
    try {
      if (!(await this.saveGrades())) {
        return;
      }
      await api.post('/api/admin/submissions/' + this.submissionId + '/finish');
      if (this.data.graded) {
        toast('总分已更新');
        this.load();
        return;
      }
      const pending = await api.get(PENDING_URL, { page: 1, size: 1 });
      if (pending.records.length > 0) {
        toast('判卷完成，进入下一份');
        wx.redirectTo({ url: '/pages/admin/grade/grade?id=' + pending.records[0].submissionId });
      } else {
        toast('全部判完了');
        wx.reLaunch({ url: '/pages/admin/home/home' });
      }
    } catch (e) {
      // 错误已由 request 提示
    }
  }
});
