const config = require('./config');
const session = require('./session');
const { toast } = require('./format');

const MAX_IMAGES = 9;
const MAX_IMAGE_BYTES = 5 * 1024 * 1024;

function chooseFiles(count) {
  return new Promise((resolve) => {
    wx.chooseMedia({
      count,
      mediaType: ['image'],
      sourceType: ['album', 'camera'],
      sizeType: ['compressed'],
      success: (res) => resolve(res.tempFiles),
      // 用户取消选择也会走这里，不需要提示
      fail: () => resolve([])
    });
  });
}

function uploadOne(uploadPath, filePath) {
  return new Promise((resolve, reject) => {
    wx.uploadFile({
      url: config.baseURL + uploadPath,
      filePath,
      name: 'file',
      header: { Authorization: 'Bearer ' + session.get().token },
      success(res) {
        let body = {};
        try {
          body = JSON.parse(res.data);
        } catch (e) {
          // 非 JSON 响应按失败处理
        }
        if (res.statusCode === 200 && body.code === 0) {
          resolve(body.data.name);
        } else {
          if (res.statusCode === 401) session.logout();
          reject(new Error(body.message || '上传失败（' + res.statusCode + '）'));
        }
      },
      fail: () => reject(new Error('上传失败，请检查网络'))
    });
  });
}

/** 选图并上传，返回新上传的文件名；取消或失败时返回空数组，已传成功的仍会返回。 */
async function pickAndUpload(uploadPath, currentCount) {
  const remaining = MAX_IMAGES - currentCount;
  if (remaining <= 0) {
    toast('最多只能放 ' + MAX_IMAGES + ' 张图片');
    return [];
  }
  const files = await chooseFiles(remaining);
  if (files.some((file) => file.size > MAX_IMAGE_BYTES)) {
    toast('图片不能超过 5MB');
    return [];
  }
  const names = [];
  try {
    for (let i = 0; i < files.length; i += 1) {
      wx.showLoading({ title: '上传 ' + (i + 1) + '/' + files.length, mask: true });
      names.push(await uploadOne(uploadPath, files[i].tempFilePath));
    }
    wx.hideLoading();
  } catch (e) {
    wx.hideLoading();
    toast(e.message);
  }
  return names;
}

module.exports = { MAX_IMAGES, pickAndUpload };
