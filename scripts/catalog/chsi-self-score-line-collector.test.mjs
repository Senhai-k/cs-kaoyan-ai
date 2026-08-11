import assert from 'node:assert/strict';
import test from 'node:test';
import { htmlToText, parseArticle, parseSelfScoringLinks } from './chsi-self-score-line-collector.mjs';

test('parses the requested year without bleeding into the next school', () => {
  const html = `var zhxList = [
    { yxmc: '甲大学', yearList: [{ year: '2026', url: 'https://a.example/2026' }, { year: '2025', url: 'https://a.example/2025' }] },
    { yxmc: '乙大学', yearList: [{ year: '2025', url: 'https://b.example/2025' }] }
  ];`;
  assert.deepEqual(parseSelfScoringLinks(html, 2026), [
    { schoolName: '甲大学', articleUrl: 'https://a.example/2026' },
  ]);
});

test('extracts article text and only official news images', () => {
  const html = `<html><head><title>甲大学2026年复试线</title></head><body>
    <span>2026年03月16日</span><span>来源：<a>甲大学</a></span>
    <div class="detail" id="article_dnull"><p>一、基本分数线</p>
    <p><img src="https://t1.chei.com.cn/news/img/1.png" alt="学术学位"></p>
    <img src="https://example.com/tracker.png"></div><div id="dzz"></div>
  </body></html>`;
  const article = parseArticle(html, 'https://yz.chsi.com.cn/article');
  assert.equal(article.title, '甲大学2026年复试线');
  assert.equal(article.publishedDate, '2026-03-16');
  assert.equal(article.sourceName, '甲大学');
  assert.equal(article.bodyText, '一、基本分数线');
  assert.deepEqual(article.images, [{
    index: 0,
    imageUrl: 'https://t1.chei.com.cn/news/img/1.png',
    alt: '学术学位',
  }]);
});

test('normalizes paragraphs and entities', () => {
  assert.equal(htmlToText('<p>工学&nbsp;08</p><p>总分&amp;单科</p>'), '工学 08\n总分&单科');
});
