const api = require('../../../utils/request');
const {
  typeLabel, sortByType, optionsText, answerText, verdict, fileUrls, onPreview
} = require('../../../utils/format');

Page({
  data: {
    result: null,
    items: [],
    allItems: [],
    wrongItems: [],
    onlyWrong: false,
    rightCount: 0,
    partialCount: 0,
    wrongCount: 0
  },

  onPreview,

  onLoad(query) {
    this.paperId = query.id;
    this.load();
  },

  // 下拉刷新后判卷结果可能已经出来了
  async onPullDownRefresh() {
    await this.load();
    wx.stopPullDownRefresh();
  },

  async load() {
    try {
      const result = await api.get('/api/student/papers/' + this.paperId + '/result');
      const allItems = sortByType(result.items).map((item, i) => {
        const v = verdict(item.correct, item.score);
        return {
          ...item,
          no: i + 1,
          typeText: typeLabel(item.type),
          optionsText: optionsText(item),
          answerText: answerText(item.type, item.myAnswer, item.myBlanks),
          imageUrls: fileUrls(item.images),
          photoUrls: fileUrls(item.myImages),
          verdictKey: v.key,
          verdictText: v.text,
          verdictCls: v.cls
        };
      });
      const count = (key) => allItems.filter((item) => item.verdictKey === key).length;
      this.setData({
        result,
        allItems,
        wrongItems: allItems.filter((item) => item.verdictKey !== 'right'),
        items: allItems,
        onlyWrong: false,
        rightCount: count('right'),
        partialCount: count('partial'),
        wrongCount: count('wrong')
      });
    } catch (e) {
      // 错误已由 request 提示
    }
  },

  onFilter(e) {
    const onlyWrong = e.currentTarget.dataset.wrong;
    this.setData({ onlyWrong, items: onlyWrong ? this.data.wrongItems : this.data.allItems });
  },

  onReview() {
    wx.navigateTo({ url: '/pages/student/exam/exam?id=' + this.paperId });
  }
});
