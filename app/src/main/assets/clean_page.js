// Hides site chrome on the ZTP page so only the availability content is visible.
(function () {
  var main = document.querySelector('main, [role=main], #main-content, .main-content, #content, #main');
  var html = document.documentElement;
  var body = document.body;

  function hide(el) {
    if (!el || el === html || el === body) return;
    if (main && (el.contains(main) || main.contains(el))) return;
    el.style.setProperty('display', 'none', 'important');
  }

  document.querySelectorAll(
    'header, footer, nav, aside, [role=banner], [role=contentinfo], [role=navigation], [role=search], ' +
    '[class*=breadcrumb], [id*=breadcrumb], [class*=cookie], [id*=cookie], [class*=skip-link]'
  ).forEach(hide);

  // Keep only the branch of the DOM that leads to the main content.
  if (main) {
    for (var el = main; el && el.parentElement && el !== body; el = el.parentElement) {
      Array.prototype.forEach.call(el.parentElement.children, function (sibling) {
        if (sibling !== el && sibling.tagName !== 'SCRIPT' && sibling.tagName !== 'STYLE') hide(sibling);
      });
    }
    main.style.setProperty('margin', '0', 'important');
  }
  window.scrollTo(0, 0);
})();
