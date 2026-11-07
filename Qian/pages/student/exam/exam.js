const api = require('../../../utils/request');
const { MAX_IMAGES, pickAndUpload } = require('../../../utils/images');
const {
  TYPE_ORDER, typeLabel, sortByType, optionsOf, fileUrls, confirm, onPreview
} = require('../../../utils/format');

function isDone(q) {
  return q.type === 'FILL_BLANK'
    ? (q.myBlanks || []).some((b) => !!b)
    : !!(q.myAnswer && q.myAnswer.trim());
}

Page({
  data: {
    loaded: false,
    title: '',
    locked: false,
    total: 0,
    index: 0,
    current: null,
    options: [],
    blanks: [],
    essay: '',
    photoUrls: [],
    maxImages: MAX_IMAGES,
    isLast: false,
    doneCount: 0,
    percent: 0,
    tabs: [],
    saveText: '',
    saveCls: '',
    sheetOpen: false,
    groups: [],
    sheetNotice: '',
    sheetNoticeCls: '',
    canSubmit: false
  },

  onPreview,

  onLoad(query) {
    this.paperId = query.id;
    this.base = '/api/student/papers/' + query.id;
    this.questions = [];
    this.inflight = 0;
    // 保存请求排队发送，避免快速改答案时旧答案后到达覆盖新答案
    this.saveQueue = Promise.resolve();
    this.load();
  },

  onHide() {
    this.saveText();
  },

  onUnload() {
    this.saveText();
  },

  async load() {
    try {
      const [fetched, paper] = await Promise.all([
        api.get(this.base + '/questions'),
        api.get(this.base)
      ]);
      this.questions = sortByType(fetched).map((q) => ({
        ...q,
        draftBlanks: Array.from({ length: q.blankCount || 0 }, (_, i) => (q.myBlanks || [])[i] || ''),
        draftEssay: q.myAnswer || ''
      }));
      const locked = paper.status === 'SUBMITTED' || paper.status === 'GRADED';
      this.setData({ title: paper.title, locked, total: this.questions.length, loaded: true });
      if (this.questions.length > 0) {
        // 继续做题时从第一道没答的开始；已交卷查看时从头看
        this.show(locked ? 0 : Math.max(0, this.questions.findIndex((q) => !isDone(q))));
      }
    } catch (e) {
      this.setData({ loaded: true });
    }
  },

  show(i) {
    const q = this.questions[i];
    const selected = q.myAnswer;
    this.setData({
      index: i,
      isLast: i === this.questions.length - 1,
      current: {
        type: q.type,
        typeText: typeLabel(q.type),
        score: q.score,
        content: q.content,
        imageUrls: fileUrls(q.images)
      },
      options: optionsOf(q).map((o) => ({ ...o, selected: selected === o.key })),
      blanks: q.draftBlanks,
      essay: q.draftEssay,
      photoUrls: fileUrls(q.myImages)
    });
    this.refreshProgress();
  },

  refreshProgress() {
    const questions = this.questions;
    const doneCount = questions.filter(isDone).length;
    const currentType = questions[this.data.index].type;
    const tabs = TYPE_ORDER.filter((t) => questions.some((q) => q.type === t)).map((type) => {
      const ofType = questions.filter((q) => q.type === type);
      return {
        type,
        label: typeLabel(type),
        count: ofType.length,
        done: ofType.filter(isDone).length,
        first: questions.indexOf(ofType[0]),
        active: type === currentType
      };
    });
    this.setData({ doneCount, percent: Math.round((doneCount / questions.length) * 100), tabs });
  },

  send(q, body) {
    const url = this.base + '/questions/' + q.id + '/answer';
    this.inflight += 1;
    this.setData({ saveText: '保存中…', saveCls: '' });
    const task = this.saveQueue.then(() => api.put(url, body));
    this.saveQueue = task.then(() => {
      this.inflight -= 1;
      if (this.inflight === 0) this.setData({ saveText: '✓ 已保存', saveCls: 'save-ok' });
    }, () => {
      this.inflight -= 1;
      this.setData({ saveText: '保存失败', saveCls: 'save-error' });
    });
    return task;
  },

  async onChoose(e) {
    const key = e.currentTarget.dataset.key;
    const i = this.data.index;
    const q = this.questions[i];
    const previous = q.myAnswer;
    if (this.data.locked || key === previous) {
      return;
    }
    q.myAnswer = key;
    this.show(i);
    try {
      await this.send(q, { answer: key });
    } catch (err) {
      q.myAnswer = previous;
      if (this.data.index === i) this.show(i);
    }
  },

  onBlankInput(e) {
    this.questions[this.data.index].draftBlanks[e.currentTarget.dataset.i] = e.detail.value;
    this.scheduleSave();
  },

  onEssayInput(e) {
    this.questions[this.data.index].draftEssay = e.detail.value;
    this.scheduleSave();
  },

  onSaveText() {
    this.saveText();
  },

  scheduleSave() {
    clearTimeout(this.saveTimer);
    this.setData({ saveText: '正在输入…', saveCls: '' });
    this.saveTimer = setTimeout(() => this.saveText(), 800);
  },

  /** 保存当前题的文字答案，内容没变就不发请求。失败时返回 false。 */
  async saveText() {
    clearTimeout(this.saveTimer);
    const q = this.questions[this.data.index];
    if (!q || this.data.locked) {
      return true;
    }
    const old = { myAnswer: q.myAnswer, myBlanks: q.myBlanks };
    let body;
    if (q.type === 'FILL_BLANK') {
      const saved = Array.from({ length: q.blankCount }, (_, i) => (q.myBlanks || [])[i] || '');
      if (JSON.stringify(saved) === JSON.stringify(q.draftBlanks)) return true;
      q.myBlanks = q.draftBlanks.slice();
      body = { blanks: q.myBlanks };
    } else if (q.type === 'ESSAY') {
      if ((q.myAnswer || '') === q.draftEssay) return true;
      q.myAnswer = q.draftEssay;
      body = { answer: q.myAnswer };
    } else {
      return true;
    }
    this.refreshProgress();
    try {
      await this.send(q, body);
      return true;
    } catch (err) {
      Object.assign(q, old);
      this.refreshProgress();
      return false;
    }
  },

  /** 照片和当前文字一起保存，这样两边不会互相覆盖。 */
  async savePhotos(q, images) {
    clearTimeout(this.saveTimer);
    const old = { myAnswer: q.myAnswer, myImages: q.myImages };
    q.myAnswer = q.draftEssay;
    q.myImages = images;
    this.setData({ photoUrls: fileUrls(images) });
    this.refreshProgress();
    try {
      await this.send(q, { answer: q.draftEssay, images });
    } catch (err) {
      Object.assign(q, old);
      this.setData({ photoUrls: fileUrls(q.myImages) });
      this.refreshProgress();
    }
  },

  async onAddPhoto() {
    const q = this.questions[this.data.index];
    const names = await pickAndUpload('/api/student/files', q.myImages.length);
    if (names.length > 0) {
      await this.savePhotos(q, q.myImages.concat(names));
    }
  },

  onRemovePhoto(e) {
    const q = this.questions[this.data.index];
    this.savePhotos(q, q.myImages.filter((_, i) => i !== e.currentTarget.dataset.i));
  },

  async moveTo(i) {
    if (await this.saveText()) {
      this.show(i);
      wx.pageScrollTo({ scrollTop: 0, duration: 0 });
    }
  },

  onPrev() {
    if (this.data.index > 0) {
      this.moveTo(this.data.index - 1);
    }
  },

  onNext() {
    this.moveTo(this.data.index + 1);
  },

  onJump(e) {
    this.moveTo(e.currentTarget.dataset.i);
  },

  onBack() {
    wx.navigateBack();
  },

  /** 答题卡：看整篇的完成情况、跳题、交卷。 */
  async onOpenSheet() {
    await this.saveText();
    const questions = this.questions;
    const numbers = (list) => list.map((q) => questions.indexOf(q) + 1).join('、');
    const missing = questions.filter((q) => !isDone(q));
    const missingEssays = missing.filter((q) => q.type === 'ESSAY');
    let sheetNotice = '全部答完了，可以交卷';
    let sheetNoticeCls = 'notice-ok';
    if (missingEssays.length > 0) {
      sheetNotice = '第 ' + numbers(missingEssays) + ' 题是大题，必须填写结果才能交卷';
      sheetNoticeCls = 'notice-error';
    } else if (missing.length > 0) {
      sheetNotice = '第 ' + numbers(missing) + ' 题还没有作答，也可以直接交卷';
      sheetNoticeCls = '';
    }
    this.setData({
      sheetOpen: true,
      sheetNotice,
      sheetNoticeCls,
      canSubmit: missingEssays.length === 0,
      groups: TYPE_ORDER.filter((t) => questions.some((q) => q.type === t)).map((type) => ({
        type,
        label: typeLabel(type),
        cells: questions
          .map((q, i) => ({ i, type: q.type, done: isDone(q) }))
          .filter((c) => c.type === type)
      }))
    });
  },

  onCloseSheet() {
    this.setData({ sheetOpen: false });
  },

  // 拦住点击，点弹层内部不要关闭
  onSheetTap() {},

  onSheetJump(e) {
    this.setData({ sheetOpen: false });
    this.moveTo(e.currentTarget.dataset.i);
  },

  async onSubmit() {
    const missing = this.questions.filter((q) => !isDone(q)).length;
    this.setData({ sheetOpen: false });
    const message = (missing > 0 ? '还有 ' + missing + ' 题没有作答。' : '题目都答完了。') + '交卷后不能再修改答案。';
    if (!(await confirm(message, '确认交卷'))) {
      return;
    }
    try {
      await api.post(this.base + '/submit');
      wx.redirectTo({ url: '/pages/student/result/result?id=' + this.paperId });
    } catch (e) {
      // 错误已由 request 提示
    }
  }
});
