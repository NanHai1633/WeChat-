const config = require('./config');

const TYPE_LABELS = {
  SINGLE_CHOICE: '选择题',
  TRUE_FALSE: '判断题',
  FILL_BLANK: '填空题',
  ESSAY: '大题'
};
const TYPE_ORDER = Object.keys(TYPE_LABELS);
const TF_LABELS = { TRUE: '正确', FALSE: '错误' };

function typeLabel(type) {
  return TYPE_LABELS[type] || type;
}

/** 按题型分组排序，同题型内顺序不变，做题页和成绩页的题号才能对上。 */
function sortByType(questions) {
  return questions
    .map((q, i) => [q, i])
    .sort((a, b) => TYPE_ORDER.indexOf(a[0].type) - TYPE_ORDER.indexOf(b[0].type) || a[1] - b[1])
    .map(([q]) => q);
}

/** 列表上显示的做题状态，status 为空表示未开始。 */
function statusInfo(status) {
  switch (status) {
    case 'IN_PROGRESS':
      return { text: '作答中', cls: 'tag-blue' };
    case 'SUBMITTED':
      return { text: '待判卷', cls: 'tag-orange' };
    case 'GRADED':
      return { text: '已判完', cls: 'tag-green' };
    default:
      return { text: '未开始', cls: '' };
  }
}

function optionsOf(q) {
  if (q.type === 'SINGLE_CHOICE') {
    return ['A', 'B', 'C', 'D'].map((key) => ({ key, mark: key, text: q['option' + key] }));
  }
  if (q.type === 'TRUE_FALSE') {
    return [
      { key: 'TRUE', mark: '✓', text: '正确' },
      { key: 'FALSE', mark: '✗', text: '错误' }
    ];
  }
  return [];
}

/** 题干下方显示的选项文字，用于成绩页和老师端。 */
function optionsText(q) {
  return q.type === 'SINGLE_CHOICE'
    ? ['A', 'B', 'C', 'D'].map((k) => k + '. ' + q['option' + k]).join('\n')
    : '';
}

/** 把答案转成可读文字。 */
function answerText(type, answer, blanks) {
  if (type === 'FILL_BLANK') {
    if (!blanks || blanks.length === 0) return '未作答';
    return blanks.map((b, i) => '第' + (i + 1) + '空：' + (b || '（空）')).join('\n');
  }
  if (answer === null || answer === undefined || answer === '') return '未作答';
  return type === 'TRUE_FALSE' ? TF_LABELS[answer] || answer : answer;
}

function correctBlanksText(blanks) {
  return blanks.map((b, i) => '第' + (i + 1) + '空：' + b.join(' 或 ')).join('\n');
}

/** 单题判分结果：满分为对，0 分为错，其余为部分得分，没分数为待判。 */
function verdict(correct, score) {
  if (score === null || score === undefined) return { key: 'pending', text: '待判分', cls: 'tag-orange' };
  if (correct === true) return { key: 'right', text: '正确', cls: 'tag-green' };
  if (Number(score) > 0) return { key: 'partial', text: '部分正确', cls: 'tag-orange' };
  return { key: 'wrong', text: '错误', cls: 'tag-red' };
}

function fileUrl(name) {
  return config.baseURL + '/api/files/' + encodeURIComponent(name);
}

function fileUrls(names) {
  return (names || []).map(fileUrl);
}

function confirm(content, title) {
  return new Promise((resolve) => {
    wx.showModal({
      title: title || '请确认',
      content,
      success: (res) => resolve(!!res.confirm),
      fail: () => resolve(false)
    });
  });
}

function prompt(title, placeholder, initial) {
  return new Promise((resolve) => {
    wx.showModal({
      title,
      editable: true,
      placeholderText: placeholder || '',
      content: initial || '',
      success: (res) => resolve(res.confirm ? (res.content || '').trim() : null),
      fail: () => resolve(null)
    });
  });
}

function toast(title) {
  wx.showToast({ title, icon: 'none', duration: 2500 });
}

/** 页面共用的点图放大，图片上写 data-urls 和 data-current。 */
function onPreview(e) {
  const { urls, current } = e.currentTarget.dataset;
  wx.previewImage({ urls, current });
}

/** 管理员底部导航。 */
function onTab(e) {
  wx.reLaunch({ url: e.currentTarget.dataset.url });
}

module.exports = {
  TYPE_ORDER, typeLabel, sortByType, statusInfo, optionsOf, optionsText, answerText, correctBlanksText,
  verdict, fileUrl, fileUrls, confirm, prompt, toast, onPreview, onTab
};
