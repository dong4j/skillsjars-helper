/*
 * SkillsJars Helper — Landing Page Interaction
 * ----------------------------------------------------------------------------
 * 模块作用 (why this file exists):
 *   纯 vanilla JS, 不依赖任何框架或 CDN. 提供 4 件事:
 *     1. Hero 标题逐词 stagger 入场 (用 CSS animation + JS 切词)
 *     2. 全站 reveal-on-scroll (IntersectionObserver, 替代 Framer Motion)
 *     3. Header 滚动态切换 (用 sentinel + IntersectionObserver, 0 scroll 监听)
 *     4. Benefit 卡片 cursor-follow gradient (CSS var --mx 跟随鼠标)
 *
 * 关键约束 (key constraints):
 *   - 所有交互都尊重 prefers-reduced-motion (浏览器层面已通过 @media 关停 CSS 动效,
 *     这里只需保证脚本不主动制造动画即可)
 *   - 不污染 window 全局
 *   - 老浏览器降级 (IntersectionObserver 不存在时直接显示, 不假死)
 */

(() => {
  'use strict';

  /* ----------------------------------------------------------------
   * 1. Hero 标题逐词切分
   * ---------------------------------------------------------------- */
  const splitHeroWords = () => {
    const title = document.querySelector('.hero-title');
    if (!title || title.dataset.split === 'true') return;
    title.dataset.split = 'true';

    // 把每个 child node 切到词级别, 但保留已有 <em> 高亮节点
    const walk = (node, wordIdxRef) => {
      const children = Array.from(node.childNodes);
      children.forEach((child) => {
        if (child.nodeType === Node.TEXT_NODE) {
          const text = child.nodeValue;
          if (!text || !text.trim()) return;
          const frag = document.createDocumentFragment();
          text.split(/(\s+)/).forEach((token) => {
            if (!token) return;
            if (/^\s+$/.test(token)) {
              frag.appendChild(document.createTextNode(token));
              return;
            }
            const span = document.createElement('span');
            span.className = 'word';
            span.style.animationDelay = `${wordIdxRef.value * 70}ms`;
            span.textContent = token;
            wordIdxRef.value += 1;
            frag.appendChild(span);
          });
          node.replaceChild(frag, child);
        } else if (child.nodeType === Node.ELEMENT_NODE) {
          // <em> 整体作为一个词处理 (保持渐变文字完整)
          if (child.tagName === 'EM') {
            child.classList.add('word');
            child.style.animationDelay = `${wordIdxRef.value * 70}ms`;
            wordIdxRef.value += 1;
            return;
          }
          walk(child, wordIdxRef);
        }
      });
    };

    walk(title, { value: 0 });
  };

  /* ----------------------------------------------------------------
   * 2. Reveal-on-scroll
   * ---------------------------------------------------------------- */
  const setupReveal = () => {
    const targets = document.querySelectorAll('.reveal, .reveal-stagger');
    if (!('IntersectionObserver' in window)) {
      // 老浏览器降级: 直接展示
      targets.forEach((el) => el.classList.add('is-in'));
      return;
    }

    const io = new IntersectionObserver(
      (entries) => {
        entries.forEach((entry) => {
          if (entry.isIntersecting) {
            entry.target.classList.add('is-in');
            io.unobserve(entry.target);
          }
        });
      },
      { threshold: 0.12, rootMargin: '0px 0px -40px 0px' }
    );

    targets.forEach((el) => io.observe(el));
  };

  /* ----------------------------------------------------------------
   * 3. Header scroll state
   * ---------------------------------------------------------------- */
  const setupHeader = () => {
    const header = document.querySelector('.site-header');
    if (!header) return;

    // 在 body 顶部插入一个看不见的 sentinel, 通过 IO 反向得到 "已离开顶部"
    const sentinel = document.createElement('div');
    sentinel.style.cssText = 'position:absolute;top:0;left:0;width:1px;height:1px;pointer-events:none;';
    document.body.prepend(sentinel);

    if (!('IntersectionObserver' in window)) return;

    const io = new IntersectionObserver(
      ([entry]) => {
        header.dataset.scrolled = entry.isIntersecting ? 'false' : 'true';
      },
      { threshold: 0 }
    );
    io.observe(sentinel);
  };

  /* ----------------------------------------------------------------
   * 4. Benefit cursor-follow gradient
   * ---------------------------------------------------------------- */
  const setupBenefitHover = () => {
    const benefits = document.querySelectorAll('.benefit');
    benefits.forEach((card) => {
      card.addEventListener('mousemove', (e) => {
        const rect = card.getBoundingClientRect();
        const x = ((e.clientX - rect.left) / rect.width) * 100;
        card.style.setProperty('--mx', `${x}%`);
      });
    });
  };

  /* ----------------------------------------------------------------
   * Boot
   * ---------------------------------------------------------------- */
  const boot = () => {
    splitHeroWords();
    setupReveal();
    setupHeader();
    setupBenefitHover();
  };

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', boot, { once: true });
  } else {
    boot();
  }
})();
