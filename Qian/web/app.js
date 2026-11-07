'use strict';

// ---------- 工具 ----------

/** 创建元素。文字都走 textNode，学生和老师输入的内容不会被当成 HTML 执行。 */
function h(tag, props, ...children) {
  const el = document.createElement(tag);
  for (const [key, value] of Object.entries(props || {})) {
    if (value === null || value === undefined || value === false) continue;
    if (key.startsWith('on')) el.addEventListener(key.slice(2), value);
    else if (key === 'class') el.className = value;
    else if (key === 'value' || key === 'disabled' || key === 'type') el[key] = value;
    else el.setAttribute(key, value === true ? '' : value);
  }
  children.flat(Infinity).forEach((child) => {
    if (child === null || child === undefined || child === false) return;
    el.append(child.nodeType ? child : document.createTextNode(String(child)));
  });
  return el;
}

const session = {
  get() {
    try {
      return JSON.parse(localStorage.getItem('session'));
    } catch (e) {
      return null;
    }
  },
  save(value) {
    localStorage.setItem('session', JSON.stringify(value));
  },
  clear() {
    localStorage.removeItem('session');
  }
};

let toastTimer;
function toast(message) {
  const el = document.getElementById('toast');
  el.textContent = message;
  el.hidden = false;
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => { el.hidden = true; }, 2500);
}

/** 发请求，成功时返回 data；失败时弹提示并抛错。 */
async function api(method, path, body, options) {
  const current = session.get();
  const headers = {};
  if (current) headers.Authorization = 'Bearer ' + current.token;
  const isForm = body instanceof FormData;
  if (body !== undefined && !isForm) headers['Content-Type'] = 'application/json';
  let res;
  try {
    res = await fetch(path, {
      method,
      headers,
      body: body === undefined || isForm ? body : JSON.stringify(body),
      keepalive: !!(options && options.keepalive)
    });
  } catch (e) {
    toast('网络连接失败，请检查后端是否启动');
    e.handled = true;
    throw e;
  }
  let json = {};
  try {
    json = await res.json();
  } catch (e) {
    // 非 JSON 响应按失败处理
  }
  if (res.ok && json.code === 0) return json.data;
  // 登录接口的 401 是账号密码错误，不是登录失效
  if (res.status === 401 && path !== '/api/auth/login') {
    session.clear();
    location.hash = '#/login';
  }
  const message = json.message || '请求失败（' + res.status + '）';
  toast(message);
  const error = new Error(message);
  error.handled = true;
  throw error;
}

function query(params) {
  const search = new URLSearchParams();
  Object.entries(params).forEach(([key, value]) => {
    if (value !== '' && value !== null && value !== undefined) search.set(key, value);
  });
  const text = search.toString();
  return text ? '?' + text : '';
}

/** 执行操作，错误已由 api 提示，这里不再往外抛。返回是否成功。 */
async function attempt(action) {
  try {
    await action();
    return true;
  } catch (e) {
    if (!e.handled) console.error(e);
    return false;
  }
}

function modal({ title, message, input, placeholder, value, danger, confirmText }) {
  return new Promise((resolve) => {
    const field = input
      ? h('input', { type: input === 'password' ? 'password' : 'text', placeholder, value: value || '', 'aria-label': title, class: 'mt' })
      : null;
    const close = (result) => {
      document.removeEventListener('keydown', onKey);
      mask.remove();
      resolve(result);
    };
    const ok = () => close(field ? field.value.trim() || null : true);
    const cancel = () => close(field ? null : false);
    const onKey = (e) => {
      if (e.key === 'Escape') cancel();
      if (e.key === 'Enter' && field) ok();
    };
    const okButton = h('button', { class: 'btn ' + (danger ? 'btn-danger-solid' : ''), onclick: ok }, confirmText || '确定');
    const mask = h('div', { class: 'modal-mask' },
      h('div', { class: 'modal', role: 'dialog', 'aria-modal': 'true', 'aria-label': title },
        h('h2', null, title),
        message && h('p', { class: 'muted mt pre' }, message),
        field,
        h('div', { class: 'modal-actions' },
          h('button', { class: 'btn btn-plain', onclick: cancel }, '取消'),
          okButton)));
    document.addEventListener('keydown', onKey);
    document.body.append(mask);
    (field || okButton).focus();
  });
}

const TYPE_LABELS = { SINGLE_CHOICE: '选择题', TRUE_FALSE: '判断题', FILL_BLANK: '填空题', ESSAY: '大题' };
const TYPE_ORDER = Object.keys(TYPE_LABELS);
const TF_LABELS = { TRUE: '正确', FALSE: '错误' };

/** 按题型分组排序，同题型内顺序不变，做题页和成绩页的题号才能对上。 */
function sortByType(questions) {
  return questions
    .map((q, i) => [q, i])
    .sort((a, b) => TYPE_ORDER.indexOf(a[0].type) - TYPE_ORDER.indexOf(b[0].type) || a[1] - b[1])
    .map(([q]) => q);
}

function statusTag(status) {
  const map = {
    IN_PROGRESS: ['作答中', 'tag-blue'],
    SUBMITTED: ['待判卷', 'tag-orange'],
    GRADED: ['已判完', 'tag-green']
  };
  const [text, cls] = map[status] || ['未开始', ''];
  return h('span', { class: 'tag ' + cls }, text);
}

function verdictOf(correct, score) {
  if (score === null || score === undefined) return 'pending';
  if (correct === true) return 'right';
  return Number(score) > 0 ? 'partial' : 'wrong';
}

function verdictTag(correct, score) {
  const [text, cls] = {
    pending: ['待判分', 'tag-orange'],
    right: ['正确', 'tag-green'],
    partial: ['部分正确', 'tag-orange'],
    wrong: ['错误', 'tag-red']
  }[verdictOf(correct, score)];
  return h('span', { class: 'tag ' + cls }, text);
}

function optionsOf(q) {
  if (q.type === 'SINGLE_CHOICE') {
    return ['A', 'B', 'C', 'D'].map((key) => ({ key, text: q['option' + key] }));
  }
  if (q.type === 'TRUE_FALSE') {
    return [{ key: 'TRUE', text: '正确', mark: '✓' }, { key: 'FALSE', text: '错误', mark: '✗' }];
  }
  return [];
}

function answerText(type, answer, blanks) {
  if (type === 'FILL_BLANK') {
    if (!blanks || blanks.length === 0) return '未作答';
    return blanks.map((b, i) => '第' + (i + 1) + '空：' + (b || '（空）')).join('\n');
  }
  if (answer === null || answer === undefined || answer === '') return '未作答';
  return type === 'TRUE_FALSE' ? TF_LABELS[answer] || answer : answer;
}

function optionList(q) {
  return q.type === 'SINGLE_CHOICE'
    && h('p', { class: 'muted pre mt' }, ['A', 'B', 'C', 'D'].map((k) => k + '. ' + q['option' + k]).join('\n'));
}

function progressBar(done, total) {
  const percent = total > 0 ? Math.round((done / total) * 100) : 0;
  return h('div', { class: 'progress', role: 'progressbar', 'aria-valuemin': 0, 'aria-valuemax': total, 'aria-valuenow': done },
    h('div', { class: 'progress-fill', style: 'width: ' + percent + '%' }));
}

const MAX_IMAGES = 9;
const MAX_IMAGE_BYTES = 5 * 1024 * 1024;

/** 在当前页面放大看图，点任意位置或按 Esc 关闭。 */
function openLightbox(src, alt) {
  const close = () => {
    document.removeEventListener('keydown', onKey);
    mask.remove();
  };
  const onKey = (e) => {
    if (e.key === 'Escape') close();
  };
  const closeButton = h('button', { class: 'lightbox-close', 'aria-label': '关闭大图' }, '×');
  const mask = h('div', { class: 'lightbox', role: 'dialog', 'aria-modal': 'true', 'aria-label': alt, onclick: close },
    closeButton,
    h('img', { src, alt }));
  document.addEventListener('keydown', onKey);
  document.body.append(mask);
  closeButton.focus();
}

function imageThumb(name, alt) {
  const src = '/api/files/' + encodeURIComponent(name);
  return h('a', {
    class: 'thumb',
    href: src,
    onclick: (e) => {
      e.preventDefault();
      openLightbox(src, alt);
    }
  }, h('img', { src, alt, loading: 'lazy' }));
}

/** 只读的图片列表，点击放大。 */
function imageGallery(names, altPrefix) {
  if (!names || names.length === 0) return null;
  return h('div', { class: 'gallery' }, names.map((name, i) => imageThumb(name, altPrefix + ' ' + (i + 1))));
}

/**
 * 本地选图上传。onChange 收到新的文件名列表，返回 false 或 reject 时撤销这次改动。
 */
function imageUploader({ images, uploadUrl, altPrefix, buttonText, onChange }) {
  let current = images.slice();
  const wrap = h('div');
  const status = h('span', { class: 'muted', role: 'status' });
  const input = h('input', {
    type: 'file',
    accept: 'image/jpeg,image/png,image/gif,image/webp',
    multiple: true,
    hidden: true,
    'aria-label': buttonText,
    onchange: () => onFiles([...input.files])
  });
  const button = h('button', { type: 'button', class: 'upload-tile', onclick: () => input.click() },
    h('span', { class: 'upload-plus', 'aria-hidden': 'true' }, '+'), buttonText);

  async function apply(next) {
    const previous = current;
    current = next;
    draw();
    if (onChange && (await onChange(next)) === false) {
      current = previous;
      draw();
    }
  }

  async function onFiles(files) {
    input.value = '';
    if (files.length === 0) return;
    if (current.length + files.length > MAX_IMAGES) {
      toast('最多只能放 ' + MAX_IMAGES + ' 张图片');
      return;
    }
    if (files.some((file) => file.size > MAX_IMAGE_BYTES)) {
      toast('图片不能超过 5MB');
      return;
    }
    button.disabled = true;
    const uploaded = [];
    await attempt(async () => {
      for (let i = 0; i < files.length; i += 1) {
        status.textContent = '正在上传 ' + (i + 1) + ' / ' + files.length;
        const form = new FormData();
        form.append('file', files[i]);
        const res = await api('POST', uploadUrl, form);
        uploaded.push(res.name);
      }
    });
    status.textContent = '';
    button.disabled = false;
    if (uploaded.length > 0) await apply(current.concat(uploaded));
  }

  function draw() {
    wrap.replaceChildren(
      h('div', { class: 'gallery' },
        current.map((name, i) => h('div', { class: 'thumb-edit' },
          imageThumb(name, altPrefix + ' ' + (i + 1)),
          h('button', {
            type: 'button',
            class: 'thumb-remove',
            'aria-label': '删除' + altPrefix + ' ' + (i + 1),
            onclick: () => apply(current.filter((_, index) => index !== i))
          }, '×'))),
        current.length < MAX_IMAGES && button),
      status,
      input);
  }
  draw();
  return { el: wrap, get images() { return current; } };
}

function backLink(href, text) {
  return h('a', { class: 'back', href }, '‹ ' + text);
}

function empty(text) {
  return h('div', { class: 'empty' }, text);
}

function bottomBar(...buttons) {
  return h('div', { class: 'bottom-bar' }, h('div', { class: 'bottom-bar-inner' }, buttons));
}

function pageHead(title, ...actions) {
  return h('div', { class: 'page-head' }, h('h1', { class: 'grow' }, title), actions);
}

function go(hash) {
  if (location.hash === hash) route();
  else location.hash = hash;
}

/** 带“加载更多”的分页列表。 */
function pagedList(fetchPage, renderRow, emptyText) {
  const list = h('div');
  const more = h('button', { class: 'btn btn-plain btn-block', hidden: true, onclick: () => attempt(load) }, '加载更多');
  const wrap = h('div', null, list, more);
  let page = 0;
  let shown = 0;
  const info = { total: 0 };
  async function load() {
    page += 1;
    const res = await fetchPage(page);
    res.records.forEach((row) => list.append(renderRow(row)));
    shown += res.records.length;
    info.total = res.total;
    more.hidden = shown >= res.total;
    if (shown === 0) list.replaceChildren(empty(emptyText));
  }
  return { wrap, info, load };
}

// ---------- 登录 ----------

async function loginView() {
  const username = h('input', { type: 'text', id: 'username', autocomplete: 'username', placeholder: '请输入账号' });
  const password = h('input', { type: 'password', id: 'password', autocomplete: 'current-password', placeholder: '请输入密码' });
  const submit = h('button', { class: 'btn btn-large btn-block', type: 'submit' }, '登录');

  async function onSubmit(e) {
    e.preventDefault();
    if (!username.value.trim() || !password.value) {
      toast('请输入账号和密码');
      return;
    }
    submit.disabled = true;
    await attempt(async () => {
      // 账号是学生还是管理员由后端判断，按返回的角色进入对应页面
      const res = await api('POST', '/api/auth/login', { username: username.value.trim(), password: password.value });
      session.save(res);
      go(res.role === 'ADMIN' ? '#/admin/home' : '#/student');
    });
    submit.disabled = false;
  }

  return h('div', { class: 'login-wrap' },
    h('div', { class: 'login-logo', 'aria-hidden': 'true' }, '题'),
    h('h1', { class: 'login-title' }, '做题系统'),
    h('form', { class: 'card', onsubmit: onSubmit },
      h('label', { class: 'field', for: 'username' }, '账号', username),
      h('label', { class: 'field', for: 'password' }, '密码', password),
      submit));
}

// ---------- 学生端 ----------

function studentPaperCard(row) {
  const graded = row.status === 'GRADED';
  const submitted = row.status === 'SUBMITTED';
  const answered = row.answeredCount || 0;
  const action = graded ? '查看成绩' : submitted ? '查看状态' : row.status === 'IN_PROGRESS' ? '继续做题' : '开始做题';
  return h('button', {
    class: 'card card-link',
    onclick: () => go((graded || submitted ? '#/student/result/' : '#/student/exam/') + row.paperId)
  },
  h('span', { class: 'row' }, h('span', { class: 'title grow' }, row.title), statusTag(row.status)),
  graded
    ? h('span', { class: 'card-score' }, h('b', null, row.totalScore), ' / ' + row.maxScore + ' 分')
    : !submitted && [
      h('span', { class: 'muted block' }, '共 ' + row.questionCount + ' 题，满分 ' + row.maxScore + ' 分，已答 ' + answered + ' 题'),
      progressBar(answered, row.questionCount)
    ],
  h('span', { class: 'card-action' }, action + ' ›'));
}

async function studentPapersView() {
  const rows = await api('GET', '/api/student/papers');
  const todo = rows.filter((r) => !r.status || r.status === 'IN_PROGRESS');
  const waiting = rows.filter((r) => r.status === 'SUBMITTED');
  const graded = rows.filter((r) => r.status === 'GRADED');
  const group = (title, list) => list.length > 0 && [
    h('h2', { class: 'section-title' }, title + '（' + list.length + '）'),
    list.map(studentPaperCard)
  ];
  return h('div', null,
    h('div', { class: 'hero' },
      h('div', { class: 'hero-title' }, '你好，' + session.get().username),
      h('div', { class: 'hero-sub' }, todo.length > 0 ? '还有 ' + todo.length + ' 篇题没有做完' : '题目都做完了')),
    rows.length === 0 && empty('暂时没有题目'),
    group('待完成', todo),
    group('等待老师判卷', waiting),
    group('已出成绩', graded));
}

async function studentExamView(paperId) {
  const base = '/api/student/papers/' + paperId;
  const [fetched, paper] = await Promise.all([api('GET', base + '/questions'), api('GET', base)]);
  const homeHref = '#/student';
  if (fetched.length === 0) {
    return h('div', null, backLink(homeHref, '题目列表'), empty('这篇还没有题目'));
  }
  const questions = sortByType(fetched);
  const locked = paper.status === 'SUBMITTED' || paper.status === 'GRADED';
  const types = TYPE_ORDER.filter((t) => questions.some((q) => q.type === t));
  questions.forEach((q) => {
    q.draftBlanks = Array.from({ length: q.blankCount || 0 }, (_, i) => (q.myBlanks || [])[i] || '');
    q.draftEssay = q.myAnswer || '';
  });

  // 继续做题时从第一道没答的开始；已交卷查看时从头看
  let index = locked ? 0 : Math.max(0, questions.findIndex((q) => !isDone(q)));
  let saveTimer;
  let inflight = 0;
  // 保存请求排队发送，避免快速改答案时旧答案后到达覆盖新答案
  let saveQueue = Promise.resolve();

  const saveState = h('span', { class: 'save-state', role: 'status' });
  const head = h('div', { class: 'exam-head' });
  const body = h('div');
  const barSlot = h('div');
  const container = h('div', null, head, body, barSlot);

  function isDone(q) {
    return q.type === 'FILL_BLANK'
      ? (q.myBlanks || []).some(Boolean)
      : !!(q.myAnswer && q.myAnswer.trim());
  }

  function setSave(text, cls) {
    saveState.textContent = text;
    saveState.className = 'save-state ' + (cls || '');
  }

  function send(q, body2) {
    const url = base + '/questions/' + q.id + '/answer';
    inflight += 1;
    setSave('保存中…');
    const task = saveQueue.then(() => api('PUT', url, body2, { keepalive: true }));
    saveQueue = task.then(() => {
      inflight -= 1;
      if (inflight === 0) setSave('已保存', 'save-ok');
    }, () => {
      inflight -= 1;
      setSave('保存失败', 'save-error');
    });
    return task;
  }

  /** 保存当前题的文字答案，内容没变就不发请求。失败时返回 false。 */
  async function saveText() {
    clearTimeout(saveTimer);
    const q = questions[index];
    if (locked) return true;
    let requestBody;
    const old = { myAnswer: q.myAnswer, myBlanks: q.myBlanks };
    if (q.type === 'FILL_BLANK') {
      const saved = Array.from({ length: q.blankCount }, (_, i) => (q.myBlanks || [])[i] || '');
      if (JSON.stringify(saved) === JSON.stringify(q.draftBlanks)) return true;
      q.myBlanks = q.draftBlanks.slice();
      requestBody = { blanks: q.myBlanks };
    } else if (q.type === 'ESSAY') {
      if ((q.myAnswer || '') === q.draftEssay) return true;
      q.myAnswer = q.draftEssay;
      requestBody = { answer: q.myAnswer };
    } else {
      return true;
    }
    drawHead();
    const ok = await attempt(() => send(q, requestBody));
    if (!ok) {
      Object.assign(q, old);
      drawHead();
    }
    return ok;
  }

  /** 照片和当前文字一起保存，这样两边不会互相覆盖。 */
  async function savePhotos(q, images) {
    clearTimeout(saveTimer);
    const old = { myAnswer: q.myAnswer, myImages: q.myImages };
    q.myAnswer = q.draftEssay;
    q.myImages = images;
    drawHead();
    const ok = await attempt(() => send(q, { answer: q.draftEssay, images }));
    if (!ok) {
      Object.assign(q, old);
      drawHead();
    }
    return ok;
  }

  function scheduleSave() {
    clearTimeout(saveTimer);
    setSave('正在输入…');
    saveTimer = setTimeout(saveText, 800);
  }

  async function moveTo(i) {
    if (await saveText()) {
      index = i;
      draw();
      window.scrollTo(0, 0);
    }
  }

  async function choose(q, key) {
    const previous = q.myAnswer;
    if (locked || key === previous) return;
    q.myAnswer = key;
    draw();
    if (!(await attempt(() => send(q, { answer: key })))) {
      q.myAnswer = previous;
      draw();
    }
  }

  /** 答题卡：看整篇的完成情况、跳题、交卷。 */
  async function openSheet() {
    await saveText();
    const missing = questions.map((q, i) => [q, i]).filter(([q]) => !isDone(q));
    const missingEssays = missing.filter(([q]) => q.type === 'ESSAY');
    const numbers = (list) => list.map(([, i]) => i + 1).join('、');
    const close = () => {
      document.removeEventListener('keydown', onKey);
      mask.remove();
    };
    const onKey = (e) => {
      if (e.key === 'Escape') close();
    };
    async function onSubmit() {
      close();
      const yes = await modal({
        title: '确认交卷',
        message: (missing.length > 0 ? '还有 ' + missing.length + ' 题没有作答。' : '题目都答完了。') + '交卷后不能再修改答案。',
        confirmText: '交卷'
      });
      if (yes && await attempt(() => api('POST', base + '/submit'))) {
        go('#/student/result/' + paperId);
      }
    }
    const mask = h('div', {
      class: 'sheet-mask',
      onclick: (e) => {
        if (e.target === mask) close();
      }
    }, h('div', { class: 'sheet', role: 'dialog', 'aria-modal': 'true', 'aria-label': '答题卡' },
      h('div', { class: 'row' },
        h('h2', { class: 'grow' }, '答题卡'),
        h('button', { class: 'btn-text', onclick: close }, '关闭')),
      h('div', { class: 'legend' },
        h('span', null, h('i', { class: 'dot dot-done' }), '已答'),
        h('span', null, h('i', { class: 'dot' }), '未答'),
        h('span', null, h('i', { class: 'dot dot-current' }), '当前')),
      h('div', { class: 'sheet-body' }, types.map((type) => [
        h('div', { class: 'sheet-group' }, TYPE_LABELS[type]),
        h('div', { class: 'cells' }, questions.map((q, i) => q.type === type && h('button', {
          class: 'cell' + (isDone(q) ? ' done' : '') + (i === index ? ' current' : ''),
          'aria-label': '第 ' + (i + 1) + ' 题' + (isDone(q) ? '，已答' : '，未答'),
          onclick: () => {
            close();
            moveTo(i);
          }
        }, i + 1)))
      ])),
      !locked && [
        missingEssays.length > 0
          ? h('p', { class: 'notice notice-error' }, '第 ' + numbers(missingEssays) + ' 题是大题，必须填写结果才能交卷')
          : missing.length > 0
            ? h('p', { class: 'notice' }, '第 ' + numbers(missing) + ' 题还没有作答，也可以直接交卷')
            : h('p', { class: 'notice notice-ok' }, '全部答完了，可以交卷'),
        h('button', { class: 'btn btn-large btn-block', disabled: missingEssays.length > 0, onclick: onSubmit }, '交卷')
      ]));
    document.addEventListener('keydown', onKey);
    document.body.append(mask);
  }

  function drawHead() {
    const done = questions.filter(isDone).length;
    const current = questions[index];
    head.replaceChildren(
      h('div', { class: 'row' },
        backLink(homeHref, '题目列表'),
        locked ? h('span', { class: 'tag tag-orange' }, '已交卷') : saveState),
      h('h1', { class: 'exam-title' }, paper.title),
      h('div', { class: 'exam-progress' },
        progressBar(done, questions.length),
        h('span', { class: 'muted' }, '已答 ' + done + ' / ' + questions.length)),
      types.length > 1 && h('div', { class: 'type-tabs' }, types.map((type) => {
        const ofType = questions.filter((q) => q.type === type);
        return h('button', {
          class: 'type-tab' + (current.type === type ? ' active' : ''),
          onclick: () => moveTo(questions.indexOf(ofType[0]))
        }, TYPE_LABELS[type], h('small', null, ofType.filter(isDone).length + '/' + ofType.length));
      })));
  }

  function answerArea(q) {
    if (q.type === 'FILL_BLANK') {
      return q.draftBlanks.map((value, i) => h('label', { class: 'field' }, '第 ' + (i + 1) + ' 空',
        h('input', {
          type: 'text',
          value,
          disabled: locked,
          placeholder: '请输入答案',
          oninput: (e) => { q.draftBlanks[i] = e.target.value; scheduleSave(); },
          onblur: saveText
        })));
    }
    if (q.type === 'ESSAY') {
      return [
        h('label', { class: 'field' }, locked ? '你的结果' : h('span', null, '你的结果 ', h('em', { class: 'required' }, '必填')),
          h('textarea', {
            value: q.draftEssay,
            disabled: locked,
            maxlength: 10000,
            placeholder: '请在这里写下结果',
            oninput: (e) => { q.draftEssay = e.target.value; scheduleSave(); },
            onblur: saveText
          })),
        locked
          ? q.myImages.length > 0 && [h('div', { class: 'field' }, '我的照片'), imageGallery(q.myImages, '我的照片')]
          : h('div', { class: 'field' }, '照片（选填）',
            imageUploader({
              images: q.myImages,
              uploadUrl: '/api/student/files',
              altPrefix: '我的照片',
              buttonText: '添加照片',
              onChange: (images) => savePhotos(q, images)
            }).el)
      ];
    }
    return optionsOf(q).map((o) => h('button', {
      class: 'option' + (q.myAnswer === o.key ? ' selected' : ''),
      disabled: locked,
      'aria-pressed': q.myAnswer === o.key ? 'true' : 'false',
      onclick: () => choose(q, o.key)
    }, h('span', { class: 'option-key' }, o.mark || o.key), h('span', { class: 'grow pre' }, o.text)));
  }

  function draw() {
    const q = questions[index];
    const last = index === questions.length - 1;
    drawHead();
    body.replaceChildren(h('div', { class: 'card question-card' },
      h('div', { class: 'row question-meta' },
        h('span', null, h('b', { class: 'question-no' }, index + 1), h('span', { class: 'muted' }, ' / ' + questions.length)),
        h('span', { class: 'tag tag-blue' }, TYPE_LABELS[q.type] + ' · ' + q.score + ' 分')),
      h('div', { class: 'question-text pre' }, q.content),
      imageGallery(q.images, '题目图片'),
      answerArea(q)));
    barSlot.replaceChildren(bottomBar(
      h('button', { class: 'btn btn-plain', disabled: index === 0, onclick: () => moveTo(index - 1) }, '上一题'),
      h('button', { class: 'btn btn-soft', onclick: openSheet }, '答题卡'),
      last
        ? (locked
          ? h('button', { class: 'btn', onclick: () => go(homeHref) }, '返回')
          : h('button', { class: 'btn', onclick: openSheet }, '去交卷'))
        : h('button', { class: 'btn', onclick: () => moveTo(index + 1) }, '下一题')));
  }

  // 离开页面或关闭标签页时保存还没发出去的文字
  const onPageHide = () => { saveText(); };
  window.addEventListener('pagehide', onPageHide);
  setCleanup(async () => {
    window.removeEventListener('pagehide', onPageHide);
    await saveText();
    await saveQueue;
  });

  draw();
  return container;
}

async function studentResultView(id) {
  const result = await api('GET', '/api/student/papers/' + id + '/result');
  if (result.status !== 'GRADED') {
    return h('div', null,
      backLink('#/student', '题目列表'),
      h('div', { class: 'card center result-wait' },
        h('div', { class: 'wait-icon', 'aria-hidden': 'true' }, '✓'),
        h('h1', null, '已交卷'),
        h('p', { class: 'muted mt' }, result.title),
        h('p', { class: 'muted' }, '老师判完大题后，这里会显示成绩'),
        h('button', { class: 'btn btn-plain mt', onclick: () => go('#/student/exam/' + id) }, '查看我的作答')));
  }

  const items = sortByType(result.items).map((item, i) => ({ ...item, no: i + 1, verdict: verdictOf(item.correct, item.score) }));
  const count = (v) => items.filter((item) => item.verdict === v).length;
  const list = h('div');
  const notRight = items.filter((item) => item.verdict !== 'right');
  let onlyWrong = false;

  function itemCard(item) {
    return h('div', { class: 'card' },
      h('div', { class: 'row' },
        h('span', { class: 'muted' }, '第 ' + item.no + ' 题 · ' + TYPE_LABELS[item.type]),
        verdictTag(item.correct, item.score)),
      h('div', { class: 'question-text pre mt' }, item.content),
      imageGallery(item.images, '题目图片'),
      optionList(item),
      h('div', { class: 'answer-block' },
        h('div', { class: 'muted' }, '我的答案'),
        h('div', { class: 'pre' }, answerText(item.type, item.myAnswer, item.myBlanks)),
        imageGallery(item.myImages, '我的照片')),
      h('div', { class: 'row mt' },
        h('span', { class: 'muted' }, '得分'),
        h('span', null, h('b', null, item.score), ' / ' + item.maxScore)),
      item.comment && h('p', { class: 'notice mt pre' }, '老师评语：' + item.comment));
  }

  const tabs = h('div', { class: 'tabs' });
  function draw() {
    tabs.replaceChildren(
      h('button', { class: 'tab' + (onlyWrong ? '' : ' active'), onclick: () => { onlyWrong = false; draw(); } }, '全部（' + items.length + '）'),
      h('button', { class: 'tab' + (onlyWrong ? ' active' : ''), onclick: () => { onlyWrong = true; draw(); } }, '错题（' + notRight.length + '）'));
    const shown = onlyWrong ? notRight : items;
    list.replaceChildren(...(shown.length > 0 ? shown.map(itemCard) : [empty('没有错题，全部答对了')]));
  }
  draw();

  return h('div', null,
    backLink('#/student', '题目列表'),
    h('div', { class: 'hero center' },
      h('div', { class: 'hero-sub' }, result.title),
      h('div', { class: 'hero-score' }, result.totalScore),
      h('div', { class: 'hero-sub' }, '满分 ' + result.maxScore + ' 分')),
    h('div', { class: 'stats' },
      h('div', { class: 'stat' }, h('b', { class: 'text-green' }, count('right')), '正确'),
      h('div', { class: 'stat' }, h('b', { class: 'text-orange' }, count('partial')), '部分正确'),
      h('div', { class: 'stat' }, h('b', { class: 'text-red' }, count('wrong')), '错误')),
    tabs,
    list);
}

// ---------- 管理员端 ----------

function pendingRow(row) {
  return h('button', { class: 'card card-link', onclick: () => go('#/admin/grade/' + row.submissionId) },
    h('span', { class: 'row' }, h('span', { class: 'title grow' }, row.username), h('span', { class: 'card-action inline' }, '去判卷 ›')),
    h('span', { class: 'muted block' }, row.title),
    h('span', { class: 'muted block' }, '交卷时间 ' + row.submittedAt));
}

async function adminHomeView() {
  const pending = pagedList(
    (page) => api('GET', '/api/admin/submissions/pending' + query({ page, size: 20 })),
    pendingRow,
    '没有待判的答卷');
  const [, papers, students] = await Promise.all([
    pending.load(),
    api('GET', '/api/admin/papers' + query({ page: 1, size: 1 })),
    api('GET', '/api/admin/students' + query({ page: 1, size: 1 }))
  ]);
  const tile = (value, label, href, cls) => h('a', { class: 'stat stat-link', href }, h('b', { class: cls }, value), label);
  return h('div', null,
    h('div', { class: 'stats' },
      tile(pending.info.total, '待判卷', '#/admin/home', pending.info.total > 0 ? 'text-orange' : ''),
      tile(papers.total, '试卷', '#/admin/papers'),
      tile(students.total, '学生', '#/admin/students')),
    h('h2', { class: 'section-title' }, '待判卷（' + pending.info.total + '）'),
    pending.wrap,
    h('div', { class: 'center mt' }, h('a', { class: 'link-muted', href: '#/admin/password' }, '修改我的密码')));
}

async function adminPapersView() {
  const list = pagedList(
    (page) => api('GET', '/api/admin/papers' + query({ page, size: 20 })),
    (paper) => h('button', { class: 'card card-link', onclick: () => go('#/admin/paper/' + paper.id) },
      h('span', { class: 'row' }, h('span', { class: 'title grow' }, paper.title),
        h('span', { class: 'tag ' + (paper.status === 'PUBLISHED' ? 'tag-green' : '') }, paper.status === 'PUBLISHED' ? '已发布' : '未发布')),
      h('span', { class: 'muted block' }, paper.questionCount + ' 题，满分 ' + paper.maxScore + ' 分')),
    '还没有试卷，点右上角新建');
  await list.load();

  async function onCreate() {
    const title = await modal({ title: '新建试卷', input: 'text', placeholder: '请输入试卷名称' });
    if (!title) return;
    await attempt(async () => {
      const paper = await api('POST', '/api/admin/papers', { title });
      go('#/admin/paper/' + paper.id);
    });
  }

  return h('div', null,
    pageHead('试卷', h('button', { class: 'btn btn-small', onclick: onCreate }, '+ 新建试卷')),
    list.wrap);
}

function questionAnswerText(q) {
  if (q.type === 'FILL_BLANK') return q.blanks.map((b, i) => '第' + (i + 1) + '空：' + b.join(' 或 ')).join('\n');
  if (q.type === 'TRUE_FALSE') return TF_LABELS[q.answer];
  return q.answer || '（无）';
}

async function adminPaperView(id) {
  const url = '/api/admin/papers/' + id;
  const paper = await api('GET', url);
  const published = paper.status === 'PUBLISHED';
  const reload = () => go('#/admin/paper/' + id);
  const questions = sortByType(paper.questions);

  async function onRename() {
    const title = await modal({ title: '修改试卷名称', input: 'text', value: paper.title });
    if (title && await attempt(() => api('PUT', url, { title }))) reload();
  }
  async function onToggle() {
    const status = published ? 'DRAFT' : 'PUBLISHED';
    if (await attempt(() => api('PUT', url + '/status', { status }))) {
      toast(published ? '已下架' : '已发布，学生现在能看到了');
      reload();
    }
  }
  async function onDelete() {
    const yes = await modal({ title: '删除试卷', message: '学生将看不到这篇试卷，已有成绩记录会保留。', danger: true, confirmText: '删除' });
    if (yes && await attempt(() => api('DELETE', url))) go('#/admin/papers');
  }
  async function onDeleteQuestion(qid) {
    const yes = await modal({ title: '删除题目', message: '确定删除这道题吗？', danger: true, confirmText: '删除' });
    if (yes && await attempt(() => api('DELETE', '/api/admin/questions/' + qid))) reload();
  }

  const questionCard = (q) => h('div', { class: 'card' },
    h('div', { class: 'row' },
      h('b', null, '第 ' + (questions.indexOf(q) + 1) + ' 题'),
      h('span', { class: 'muted' }, q.score + ' 分')),
    h('div', { class: 'question-text pre mt' }, q.content),
    imageGallery(q.images, '题目图片'),
    optionList(q),
    h('div', { class: 'answer-block' },
      h('div', { class: 'muted' }, '答案'),
      h('div', { class: 'pre' }, questionAnswerText(q))),
    h('div', { class: 'card-tools' },
      h('button', { class: 'btn-text', onclick: () => go('#/admin/paper/' + id + '/question/' + q.id) }, '修改'),
      h('button', { class: 'btn-text text-red', onclick: () => onDeleteQuestion(q.id) }, '删除')));

  return h('div', null,
    backLink('#/admin/papers', '试卷'),
    h('div', { class: 'card' },
      h('div', { class: 'row' }, h('h1', { class: 'title-large grow' }, paper.title),
        h('span', { class: 'tag ' + (published ? 'tag-green' : '') }, published ? '已发布' : '未发布')),
      h('p', { class: 'muted mt' }, questions.length + ' 题，满分 ' + paper.maxScore + ' 分'),
      !published && h('p', { class: 'notice mt' }, '学生还看不到这篇，题目加好后点“发布”'),
      h('div', { class: 'actions' },
        h('button', { class: 'btn ' + (published ? 'btn-plain' : ''), onclick: onToggle }, published ? '下架' : '发布'),
        h('button', { class: 'btn btn-plain', onclick: () => go('#/admin/paper/' + id + '/submissions') }, '学生做题情况')),
      h('div', { class: 'card-tools' },
        h('button', { class: 'btn-text', onclick: onRename }, '改名称'),
        h('button', { class: 'btn-text text-red', onclick: onDelete }, '删除试卷'))),
    questions.length === 0 && empty('还没有题目，点下方按钮添加'),
    TYPE_ORDER.map((type) => {
      const ofType = questions.filter((q) => q.type === type);
      return ofType.length > 0 && [
        h('h2', { class: 'section-title' }, TYPE_LABELS[type] + '（' + ofType.length + '）'),
        ofType.map(questionCard)
      ];
    }),
    bottomBar(h('button', { class: 'btn', onclick: () => go('#/admin/paper/' + id + '/question/new') }, '+ 添加题目')));
}

async function adminQuestionView(paperId, questionId) {
  const isNew = questionId === 'new';
  const state = { type: 'SINGLE_CHOICE', content: '', optionA: '', optionB: '', optionC: '', optionD: '', answer: '', blanks: [''], score: '' };
  let initialImages = [];
  if (!isNew) {
    const paper = await api('GET', '/api/admin/papers/' + paperId);
    const q = paper.questions.find((item) => String(item.id) === questionId);
    if (!q) throw new Error('题目不存在');
    Object.assign(state, {
      type: q.type,
      content: q.content,
      optionA: q.optionA || '',
      optionB: q.optionB || '',
      optionC: q.optionC || '',
      optionD: q.optionD || '',
      answer: q.answer || '',
      blanks: q.blanks ? q.blanks.map((b) => b.join('|')) : [''],
      score: String(q.score)
    });
    initialImages = q.images;
  }

  const makeUploader = (images) => imageUploader({
    images,
    uploadUrl: '/api/admin/files',
    altPrefix: '题目图片',
    buttonText: '添加图片'
  });
  // 上传组件放在 draw 外面，切换题型重绘时已选的图片不会丢
  let uploader = makeUploader(initialImages);
  let added = 0;
  let saving = false;
  const container = h('div');

  function choiceButtons(choices) {
    return h('div', { class: 'choice-group' }, choices.map(([value, label]) => h('button', {
      type: 'button',
      class: 'choice' + (state.answer === value ? ' active' : ''),
      'aria-pressed': state.answer === value ? 'true' : 'false',
      onclick: () => { state.answer = value; draw(); }
    }, label)));
  }

  function typeFields() {
    switch (state.type) {
      case 'SINGLE_CHOICE':
        return [
          ['A', 'B', 'C', 'D'].map((k) => h('label', { class: 'field' }, '选项 ' + k,
            h('input', { type: 'text', value: state['option' + k], maxlength: 1000, oninput: (e) => { state['option' + k] = e.target.value; } }))),
          h('div', { class: 'field' }, '正确答案', choiceButtons([['A', 'A'], ['B', 'B'], ['C', 'C'], ['D', 'D']]))
        ];
      case 'TRUE_FALSE':
        return h('div', { class: 'field' }, '正确答案', choiceButtons([['TRUE', '正确'], ['FALSE', '错误']]));
      case 'FILL_BLANK':
        return h('div', { class: 'field' }, '每个空的答案（多个正确答案用 | 隔开，如：北京|Beijing）',
          state.blanks.map((value, i) => h('div', { class: 'blank-row' },
            h('input', { type: 'text', value, placeholder: '第 ' + (i + 1) + ' 空的答案', 'aria-label': '第 ' + (i + 1) + ' 空的答案', oninput: (e) => { state.blanks[i] = e.target.value; } }),
            state.blanks.length > 1 && h('button', { type: 'button', class: 'btn-text text-red', onclick: () => { state.blanks.splice(i, 1); draw(); } }, '删除'))),
          h('button', { type: 'button', class: 'btn-text', onclick: () => { state.blanks.push(''); draw(); } }, '+ 增加一个空'));
      default:
        return h('label', { class: 'field' }, '参考答案（选填，只有老师能看到）',
          h('textarea', { value: state.answer, oninput: (e) => { state.answer = e.target.value; } }));
    }
  }

  /** 继续加题：保留题型和分值，其他清空。 */
  function resetForNext() {
    Object.assign(state, { content: '', optionA: '', optionB: '', optionC: '', optionD: '', answer: '', blanks: [''] });
    uploader = makeUploader([]);
    draw();
    window.scrollTo(0, 0);
    const first = container.querySelector('textarea');
    if (first) first.focus();
  }

  async function onSave(continueAdding) {
    const score = Number(state.score);
    if (saving) return;
    if (!state.score.trim() || Number.isNaN(score)) {
      toast('请输入正确的分值');
      return;
    }
    const body = { type: state.type, content: state.content, score, images: uploader.images };
    if (state.type === 'SINGLE_CHOICE') {
      ['optionA', 'optionB', 'optionC', 'optionD'].forEach((k) => { body[k] = state[k]; });
    }
    if (state.type === 'FILL_BLANK') {
      body.blanks = state.blanks.map((b) => b.split('|').map((s) => s.trim()).filter(Boolean));
    } else {
      body.answer = state.answer;
    }
    saving = true;
    const ok = await attempt(() => (isNew
      ? api('POST', '/api/admin/papers/' + paperId + '/questions', { questions: [body] })
      : api('PUT', '/api/admin/questions/' + questionId, body)));
    saving = false;
    if (!ok) return;
    if (continueAdding) {
      added += 1;
      toast('已保存，这次加了 ' + added + ' 题');
      resetForNext();
    } else {
      toast('已保存');
      go('#/admin/paper/' + paperId);
    }
  }

  function draw() {
    container.replaceChildren(
      backLink('#/admin/paper/' + paperId, '试卷详情'),
      pageHead(isNew ? '添加题目' : '修改题目'),
      h('div', { class: 'tabs' }, TYPE_ORDER.map((value) => h('button', {
        type: 'button',
        class: 'tab' + (state.type === value ? ' active' : ''),
        onclick: () => {
          if (state.type !== value) {
            // 切换题型时答案含义不同，要清空
            state.type = value;
            state.answer = '';
            draw();
          }
        }
      }, TYPE_LABELS[value]))),
      h('div', { class: 'card' },
        h('label', { class: 'field field-first' }, '题干',
          h('textarea', { value: state.content, placeholder: '请输入题目内容', oninput: (e) => { state.content = e.target.value; } })),
        h('div', { class: 'field' }, '题干图片（选填）', uploader.el),
        typeFields(),
        h('label', { class: 'field' }, '分值',
          h('input', { type: 'text', inputmode: 'decimal', value: state.score, placeholder: '例如 10', oninput: (e) => { state.score = e.target.value; } }))),
      isNew
        ? bottomBar(
          h('button', { class: 'btn btn-plain', onclick: () => onSave(false) }, '保存并返回'),
          h('button', { class: 'btn', onclick: () => onSave(true) }, '保存，继续加下一题'))
        : bottomBar(h('button', { class: 'btn', onclick: () => onSave(false) }, '保存')));
  }
  draw();
  return container;
}

function submissionRow(row, title) {
  return h('button', {
    class: 'card card-link',
    onclick: () => {
      if (row.submissionId) go('#/admin/grade/' + row.submissionId);
      else toast('还没有开始作答');
    }
  },
  h('span', { class: 'row' }, h('span', { class: 'title grow' }, title), statusTag(row.status)),
  row.status === 'GRADED' && h('span', { class: 'muted block' }, '总分 ' + row.totalScore));
}

async function adminSubmissionsView(paperId, filter) {
  const list = pagedList(
    (page) => api('GET', '/api/admin/papers/' + paperId + '/submissions' + query({ status: filter, page, size: 20 })),
    (row) => submissionRow(row, row.username),
    '没有符合条件的学生');
  await list.load();
  const filters = [['', '全部'], ['SUBMITTED', '待判卷'], ['GRADED', '已判完']];
  return h('div', null,
    backLink('#/admin/paper/' + paperId, '试卷详情'),
    pageHead('学生做题情况'),
    h('div', { class: 'tabs' }, filters.map(([value, label]) => h('button', {
      class: 'tab' + (filter === value ? ' active' : ''),
      onclick: () => go('#/admin/paper/' + paperId + '/submissions/' + value)
    }, label))),
    list.wrap);
}

async function adminStudentsView() {
  const keyword = new URLSearchParams(location.hash.split('?')[1] || '').get('keyword') || '';
  const list = pagedList(
    (page) => api('GET', '/api/admin/students' + query({ keyword, page, size: 20 })),
    (student) => h('div', { class: 'card' },
      h('button', { class: 'row-button', onclick: () => go('#/admin/student/' + student.id + '?name=' + encodeURIComponent(student.username)) },
        h('span', { class: 'title grow' }, student.username),
        h('span', { class: 'card-action inline' }, '做题情况 ›')),
      h('div', { class: 'muted' }, '注册于 ' + student.createdAt),
      h('div', { class: 'card-tools' },
        h('button', { class: 'btn-text', onclick: () => onReset(student) }, '重置密码'),
        h('button', { class: 'btn-text text-red', onclick: () => onDelete(student) }, '删除'))),
    '没有学生账号');
  await list.load();

  async function onReset(student) {
    const password = await modal({ title: '重置 ' + student.username + ' 的密码', input: 'password', placeholder: '新密码（6-64 位）' });
    if (password && await attempt(() => api('PUT', '/api/admin/students/' + student.id + '/password', { password }))) {
      toast('密码已重置');
    }
  }
  async function onDelete(student) {
    const yes = await modal({ title: '删除账号', message: '确定删除 ' + student.username + ' 吗？删除后不能登录，历史成绩会保留。', danger: true, confirmText: '删除' });
    if (yes && await attempt(() => api('DELETE', '/api/admin/students/' + student.id))) {
      toast('已删除');
      route();
    }
  }

  const newName = h('input', { type: 'text', placeholder: '2-32 位，不含空格' });
  const newPassword = h('input', { type: 'password', placeholder: '6-64 位字母、数字或符号', autocomplete: 'new-password' });
  const search = h('input', { type: 'text', value: keyword, placeholder: '搜索账号', 'aria-label': '搜索账号' });

  async function onCreate(e) {
    e.preventDefault();
    const ok = await attempt(() => api('POST', '/api/admin/students', { username: newName.value.trim(), password: newPassword.value }));
    if (ok) {
      toast('注册成功');
      route();
    }
  }
  function onSearch(e) {
    e.preventDefault();
    go('#/admin/students' + query({ keyword: search.value.trim() }));
  }

  return h('div', null,
    pageHead('学生（' + list.info.total + '）'),
    h('details', { class: 'card disclosure' },
      h('summary', null, '+ 注册学生'),
      h('form', { onsubmit: onCreate },
        h('label', { class: 'field' }, '账号', newName),
        h('label', { class: 'field' }, '密码', newPassword),
        h('div', { class: 'actions' }, h('button', { class: 'btn', type: 'submit' }, '注册')))),
    h('form', { class: 'search-row', onsubmit: onSearch },
      search,
      h('button', { class: 'btn btn-plain', type: 'submit' }, '搜索')),
    list.wrap);
}

async function adminStudentView(id) {
  const name = new URLSearchParams(location.hash.split('?')[1] || '').get('name') || '';
  const list = pagedList(
    (page) => api('GET', '/api/admin/students/' + id + '/submissions' + query({ page, size: 20 })),
    (row) => submissionRow(row, row.title),
    '还没有试卷');
  await list.load();
  return h('div', null, backLink('#/admin/students', '学生'), pageHead(name + ' 的做题情况'), list.wrap);
}

function correctText(a) {
  if (a.type === 'FILL_BLANK') return a.correctBlanks.map((b, i) => '第' + (i + 1) + '空：' + b.join(' 或 ')).join('\n');
  if (a.type === 'ESSAY' && !a.correctAnswer) return '（无）';
  return answerText(a.type, a.correctAnswer);
}

async function adminGradeView(id) {
  const url = '/api/admin/submissions/' + id;
  const [detail, pending] = await Promise.all([
    api('GET', url),
    api('GET', '/api/admin/submissions/pending' + query({ page: 1, size: 1 }))
  ]);
  const inProgress = detail.status === 'IN_PROGRESS';
  const graded = detail.status === 'GRADED';
  const answers = sortByType(detail.answers).map((a, i) => ({ ...a, no: i + 1 }));
  const essays = answers.filter((a) => a.type === 'ESSAY');
  const others = answers.filter((a) => a.type !== 'ESSAY');
  const drafts = new Map(essays.map((a) => [a.questionId, {
    score: a.score === null ? '' : String(a.score),
    comment: a.comment || ''
  }]));

  /** 提交已填分数的大题，没填的跳过，可以下次接着判。 */
  async function saveGrades() {
    const grades = [];
    for (const a of essays) {
      const d = drafts.get(a.questionId);
      if (d.score.trim() === '') continue;
      const score = Number(d.score);
      if (Number.isNaN(score)) {
        toast('分数格式不正确');
        throw Object.assign(new Error('bad score'), { handled: true });
      }
      grades.push({ questionId: a.questionId, score, comment: d.comment });
    }
    if (grades.length > 0) await api('PUT', url + '/grades', { grades });
  }

  async function onSave() {
    if (await attempt(saveGrades)) {
      toast('已保存');
      route();
    }
  }

  /** 判完自动进入下一份待判的答卷，没有了就回工作台。 */
  async function onFinish() {
    const ok = await attempt(async () => {
      await saveGrades();
      await api('POST', url + '/finish');
    });
    if (!ok) return;
    if (graded) {
      toast('总分已更新');
      route();
      return;
    }
    let next = null;
    await attempt(async () => {
      const res = await api('GET', '/api/admin/submissions/pending' + query({ page: 1, size: 1 }));
      next = res.records[0] || null;
    });
    if (next) {
      toast('判卷完成，进入下一份');
      go('#/admin/grade/' + next.submissionId);
    } else {
      toast('全部判完了');
      go('#/admin/home');
    }
  }

  function essayCard(a) {
    const d = drafts.get(a.questionId);
    const scoreInput = h('input', {
      type: 'text',
      inputmode: 'decimal',
      value: d.score,
      placeholder: '0 - ' + a.maxScore,
      'aria-label': '第 ' + a.no + ' 题得分',
      oninput: (e) => { d.score = e.target.value; }
    });
    const quick = (label, value) => h('button', {
      type: 'button',
      class: 'choice',
      onclick: () => {
        d.score = String(value);
        scoreInput.value = d.score;
      }
    }, label);
    return h('div', { class: 'card' },
      h('div', { class: 'row' },
        h('b', null, '第 ' + a.no + ' 题 · 大题（' + a.maxScore + ' 分）'),
        !inProgress && verdictTag(a.correct, a.score)),
      h('div', { class: 'question-text pre mt' }, a.content),
      imageGallery(a.images, '题目图片'),
      h('div', { class: 'answer-block answer-student' },
        h('div', { class: 'muted' }, '学生的结果'),
        h('div', { class: 'pre' }, answerText(a.type, a.studentAnswer, a.studentBlanks)),
        imageGallery(a.studentImages, '学生照片')),
      a.correctAnswer && h('div', { class: 'answer-block' },
        h('div', { class: 'muted' }, '参考答案'),
        h('div', { class: 'pre' }, a.correctAnswer)),
      !inProgress && [
        h('div', { class: 'field' }, '得分',
          h('div', { class: 'score-row' },
            scoreInput,
            quick('0 分', 0),
            quick('一半', Number(a.maxScore) / 2),
            quick('满分', a.maxScore))),
        h('label', { class: 'field' }, '评语（选填）',
          h('input', { type: 'text', maxlength: 1000, value: d.comment, oninput: (e) => { d.comment = e.target.value; } }))
      ]);
  }

  function autoCard(a) {
    return h('div', { class: 'auto-item' },
      h('div', { class: 'row' },
        h('span', { class: 'muted' }, '第 ' + a.no + ' 题 · ' + TYPE_LABELS[a.type]),
        !inProgress && verdictTag(a.correct, a.score)),
      h('div', { class: 'pre mt' }, a.content),
      imageGallery(a.images, '题目图片'),
      optionList(a),
      h('div', { class: 'muted pre mt' }, '学生答案：' + answerText(a.type, a.studentAnswer, a.studentBlanks)),
      h('div', { class: 'muted pre' }, '正确答案：' + correctText(a)),
      !inProgress && h('div', { class: 'muted' }, '得分 ' + a.score + ' / ' + a.maxScore));
  }

  const remaining = pending.total - (detail.status === 'SUBMITTED' ? 1 : 0);
  return h('div', null,
    backLink('#/admin/home', '工作台'),
    h('div', { class: 'card' },
      h('div', { class: 'row' }, h('h1', { class: 'title-large grow' }, detail.username), statusTag(detail.status)),
      h('p', { class: 'muted' }, detail.title),
      h('div', { class: 'grade-summary' },
        detail.autoScore !== null && h('span', null, '自动判分 ', h('b', null, detail.autoScore)),
        detail.totalScore !== null && h('span', null, '总分 ', h('b', null, detail.totalScore)),
        h('span', null, '满分 ', h('b', null, detail.maxScore))),
      detail.status === 'SUBMITTED' && remaining > 0 && h('p', { class: 'muted mt' }, '判完这份后，还有 ' + remaining + ' 份待判'),
      inProgress && h('p', { class: 'notice mt' }, '学生还在作答，交卷后才能判卷')),
    essays.map(essayCard),
    others.length > 0 && h('details', { class: 'card disclosure', open: essays.length === 0 },
      h('summary', null, '自动判分的题目（' + others.length + ' 题）'),
      others.map(autoCard)),
    !inProgress && essays.length > 0 && bottomBar(
      h('button', { class: 'btn btn-plain', onclick: onSave }, '先保存'),
      h('button', { class: 'btn', onclick: onFinish }, graded ? '更新总分' : (remaining > 0 ? '完成，判下一份' : '完成判卷'))));
}

async function adminPasswordView() {
  const oldPassword = h('input', { type: 'password', autocomplete: 'current-password' });
  const newPassword = h('input', { type: 'password', autocomplete: 'new-password' });
  async function onSubmit(e) {
    e.preventDefault();
    if (!oldPassword.value || !newPassword.value) {
      toast('请填写原密码和新密码');
      return;
    }
    const ok = await attempt(() => api('PUT', '/api/admin/password', { oldPassword: oldPassword.value, newPassword: newPassword.value }));
    if (ok) {
      session.clear();
      toast('修改成功，请重新登录');
      go('#/login');
    }
  }
  return h('div', null,
    backLink('#/admin/home', '工作台'),
    pageHead('修改密码'),
    h('form', { class: 'card', onsubmit: onSubmit },
      h('label', { class: 'field field-first' }, '原密码', oldPassword),
      h('label', { class: 'field' }, '新密码（6-64 位字母、数字或符号）', newPassword),
      h('div', { class: 'actions' }, h('button', { class: 'btn', type: 'submit' }, '确认修改'))));
}

// ---------- 路由 ----------

const ROUTES = [
  [/^\/student$/, 'STUDENT', () => studentPapersView()],
  [/^\/student\/exam\/(\d+)$/, 'STUDENT', (m) => studentExamView(m[1])],
  [/^\/student\/result\/(\d+)$/, 'STUDENT', (m) => studentResultView(m[1])],
  [/^\/admin\/home$/, 'ADMIN', () => adminHomeView()],
  [/^\/admin\/papers$/, 'ADMIN', () => adminPapersView()],
  [/^\/admin\/paper\/(\d+)$/, 'ADMIN', (m) => adminPaperView(m[1])],
  [/^\/admin\/paper\/(\d+)\/question\/(new|\d+)$/, 'ADMIN', (m) => adminQuestionView(m[1], m[2])],
  [/^\/admin\/paper\/(\d+)\/submissions(?:\/(\w*))?$/, 'ADMIN', (m) => adminSubmissionsView(m[1], m[2] || '')],
  [/^\/admin\/students$/, 'ADMIN', () => adminStudentsView()],
  [/^\/admin\/student\/(\d+)$/, 'ADMIN', (m) => adminStudentView(m[1])],
  [/^\/admin\/grade\/(\d+)$/, 'ADMIN', (m) => adminGradeView(m[1])],
  [/^\/admin\/password$/, 'ADMIN', () => adminPasswordView()]
];

// 手机上这几个一级页面显示底部导航，其他页面底部留给操作按钮
const TOP_LEVEL = ['/admin/home', '/admin/papers', '/admin/students'];

let cleanup = null;
let renderId = 0;

function setCleanup(fn) {
  cleanup = fn;
}

function drawTopbar(user, path) {
  const bar = document.getElementById('topbar');
  const showTabs = !!user && TOP_LEVEL.includes(path);
  document.body.classList.toggle('with-tabbar', showTabs);
  if (!user) {
    bar.hidden = true;
    return;
  }
  const link = (href, text, prefixes) => h('a', {
    class: 'nav-link' + (prefixes.some((p) => path.startsWith(p)) ? ' active' : ''),
    href
  }, text);
  bar.hidden = false;
  bar.replaceChildren(
    h('span', { class: 'brand' }, '做题系统'),
    ...(user.role === 'ADMIN'
      ? [h('nav', { class: 'nav', 'aria-label': '主导航' },
        link('#/admin/home', '工作台', ['/admin/home', '/admin/grade', '/admin/password']),
        link('#/admin/papers', '试卷', ['/admin/paper']),
        link('#/admin/students', '学生', ['/admin/student']))]
      : []),
    h('span', { class: 'spacer' }),
    h('span', { class: 'muted user-name' }, user.username),
    h('button', {
      class: 'btn-text',
      onclick: () => {
        session.clear();
        go('#/login');
      }
    }, '退出'));
}

async function route() {
  const id = ++renderId;
  if (cleanup) {
    const run = cleanup;
    cleanup = null;
    // 等答案保存完再加载下一页，否则下一页可能读到旧数据
    await run();
    if (id !== renderId) return;
  }
  const app = document.getElementById('app');
  const path = location.hash.replace(/^#/, '').split('?')[0] || '/';
  const user = session.get();

  if (!user) {
    if (path !== '/login') {
      location.hash = '#/login';
      return;
    }
    drawTopbar(null, path);
    app.replaceChildren(await loginView());
    return;
  }
  const home = user.role === 'ADMIN' ? '#/admin/home' : '#/student';
  const matched = ROUTES.find(([pattern]) => pattern.test(path));
  // 学生和管理员只能进入自己的页面
  if (!matched || matched[1] !== user.role) {
    location.hash = home;
    return;
  }
  drawTopbar(user, path);
  try {
    const view = await matched[2](path.match(matched[0]));
    // 加载期间用户已跳到别的页面时丢弃这次结果
    if (id === renderId) {
      app.replaceChildren(view);
      window.scrollTo(0, 0);
    }
  } catch (e) {
    if (id === renderId && session.get()) {
      app.replaceChildren(h('div', null, backLink(home, '返回首页'), empty(e.message || '页面加载失败')));
    }
  }
}

window.addEventListener('hashchange', route);
route();
