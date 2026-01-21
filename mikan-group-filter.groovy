// ==UserScript==
// @name         「蜜柑计划」高级筛选器 (性能优化版)
// @namespace    https://www.wdssmq.com/
// @version      14.2.0
// @author       hypeling (性能优化 by Claude)
// @description  优化性能,减少DOM操作和重复计算
// @license      MIT
// @noframes
// @run-at       document-end
// @match        https://mikanani.me/Home/Bangumi/*
// @match        https://mikanime.tv/Home/Bangumi/*
// @grant        GM_getValue
// @grant        GM_setValue
// @grant        GM_setClipboard
// ==/UserScript==

/* eslint-disable */
/* jshint esversion: 6 */

(function () {
  'use strict';

  const gm_name = "Mikan_Group_Filter";
  const _log = (...args) => console.log(`[${gm_name}]|`, ...args);

  // --- 1. 全局状态 ---
  let processedTables = new Set();
  let regexCache = new Map();

  const _config = {
    data: {},
    dataDef: {
      langSC: true,
      langTC: false,
      langSCTC: false,
      res1080p: true,
      res2160p: false,
      res720p: false,
    },
    save: () => GM_setValue(gm_name + "_config", _config.data),
    load: () => { _config.data = GM_getValue(gm_name + "_config", _config.dataDef); },
  };
  _config.load();


  // --- 2. 工具函数 ---
  
  function throttle(func, wait) {
    let timeout = null;
    let previous = 0;
    
    return function(...args) {
      const now = Date.now();
      const remaining = wait - (now - previous);
      
      if (remaining <= 0) {
        if (timeout) {
          clearTimeout(timeout);
          timeout = null;
        }
        previous = now;
        func.apply(this, args);
      } else if (!timeout) {
        timeout = setTimeout(() => {
          previous = Date.now();
          timeout = null;
          func.apply(this, args);
        }, remaining);
      }
    };
  }

  function debounce(func, wait) {
    let timeout;
    return function(...args) {
      clearTimeout(timeout);
      timeout = setTimeout(() => func.apply(this, args), wait);
    };
  }

  function getConfigKey(config) {
    return JSON.stringify(config);
  }


  // --- 3. 筛选逻辑 ---

  function generateRegex(config) {
    const cacheKey = getConfigKey(config);
    if (regexCache.has(cacheKey)) {
      return regexCache.get(cacheKey);
    }

    const patterns = [];
    const resExcludeParts = [];

    if (config.langSCTC) {
      patterns.push(
        "简繁|繁简",
        "简体.*繁体|繁体.*简体",
        "CHS.*CHT|CHT.*CHS",
        "GB.*BIG5|BIG5.*GB"
      );
    }

    if (config.langSC && !config.langSCTC) {
      patterns.push(
        "(?=.*(简体|简中|简日|CHS|GB))(?!.*(繁|CHT|BIG5))"
      );
    } else if (config.langSC && config.langSCTC) {
      patterns.push(
        "简体|简中|简日|CHS|GB"
      );
    }

    if (config.langTC && !config.langSCTC) {
      patterns.push(
        "(?=.*(繁体|繁體|繁中|繁日|CHT|BIG5))(?!.*(简|CHS|GB))"
      );
    } else if (config.langTC && config.langSCTC) {
      patterns.push(
        "繁体|繁體|繁中|繁日|CHT|BIG5"
      );
    }

    const resParts = [];
    if (config.res1080p) {
      resParts.push("1080p|1920x1080|1080P");
      if (!config.res2160p) {
        resExcludeParts.push("2160p|4k|4K|3840x2160|2160P");
      }
    }
    if (config.res2160p) {
      resParts.push("2160p|4k|4K|3840x2160|2160P");
    }
    if (config.res720p) {
      resParts.push("720p|1280x720|720P");
      if (!config.res1080p) {
        resExcludeParts.push("1080p|1920x1080|1080P");
      }
      if (!config.res2160p) {
        resExcludeParts.push("2160p|4k|4K|3840x2160|2160P");
      }
    }

    if (patterns.length === 0 && resParts.length === 0) {
      regexCache.set(cacheKey, null);
      return null;
    }

    let regexStr = "";
    
    if (patterns.length > 0) {
      regexStr = `(?:${patterns.join("|")})`;
    }
    
    if (resParts.length > 0) {
      if (regexStr) {
        regexStr = `(?=.*(?:${regexStr}))(?=.*(?:${resParts.join("|")}))`;
      } else {
        regexStr = `(?=.*(?:${resParts.join("|")}))`;
      }
      
      if (resExcludeParts.length > 0) {
        regexStr += `(?!.*(?:${resExcludeParts.join("|")}))`;
      }
    }

    const regex = new RegExp(regexStr, "i");
    
    if (regexCache.size > 20) {
      const firstKey = regexCache.keys().next().value;
      regexCache.delete(firstKey);
    }
    regexCache.set(cacheKey, regex);
    
    return regex;
  }

  function applyFilterToTable(table, config) {
    const regex = generateRegex(config);
    const rows = table.querySelectorAll("tr");
    
    let matchCount = 0;
    let totalCount = 0;
    
    rows.forEach((tr, index) => {
      if (index === 0) return;
      const magnetLink = tr.querySelector(".magnet-link-wrap");
      if (!magnetLink) return;

      const title = magnetLink.textContent;
      const checkbox = tr.querySelector(".magnet-select-checkbox");

      if (checkbox) {
        totalCount++;
        const shouldCheck = !regex || regex.test(title);
        checkbox.checked = shouldCheck;
        if (shouldCheck) matchCount++;
      }
    });
    
    _log(`筛选完成: ${matchCount}/${totalCount} 项被选中`);
  }


  // --- 4. UI 创建 ---

  function createFilterPanel(groupContainer, episodeTableDiv, table) {
    const filterPanel = document.createElement("div");
    filterPanel.className = "group-filter-panel";
    filterPanel.style.cssText = "display: none; margin: 10px 0; padding: 10px; border: 1px solid #ddd; border-radius: 5px; background-color: #f9f9f9;";

    const localConfig = {
      langSC: true,
      langTC: false,
      langSCTC: false,
      res1080p: true,
      res2160p: false,
      res720p: false,
    };

    filterPanel.innerHTML = `
      <div style="display: flex; align-items: center; flex-wrap: wrap; gap: 15px;">
        <strong style="white-space: nowrap;">筛选条件:</strong>
        <fieldset class="filter-fieldset">
          <legend>语言</legend>
          <label><input type="checkbox" class="filter-opt" data-key="langSC" ${localConfig.langSC ? 'checked' : ''}> 简体</label>
          <label><input type="checkbox" class="filter-opt" data-key="langTC" ${localConfig.langTC ? 'checked' : ''}> 繁体</label>
          <label><input type="checkbox" class="filter-opt" data-key="langSCTC" ${localConfig.langSCTC ? 'checked' : ''}> 简繁</label>
        </fieldset>
        <fieldset class="filter-fieldset">
          <legend>分辨率</legend>
          <label><input type="checkbox" class="filter-opt" data-key="res1080p" ${localConfig.res1080p ? 'checked' : ''}> 1080p</label>
          <label><input type="checkbox" class="filter-opt" data-key="res2160p" ${localConfig.res2160p ? 'checked' : ''}> 4K</label>
          <label><input type="checkbox" class="filter-opt" data-key="res720p" ${localConfig.res720p ? 'checked' : ''}> 720p</label>
        </fieldset>
        <button class="apply-filter-btn filter-btn" style="margin-left: auto;">应用筛选</button>
      </div>
    `;

    // 插入到 .episode-table 之前（即字幕组标题之后）
    episodeTableDiv.parentNode.insertBefore(filterPanel, episodeTableDiv);

    const applyBtn = filterPanel.querySelector(".apply-filter-btn");
    applyBtn.addEventListener("click", () => {
      const currentConfig = {};
      filterPanel.querySelectorAll(".filter-opt").forEach(cb => {
        currentConfig[cb.dataset.key] = cb.checked;
        localConfig[cb.dataset.key] = cb.checked;
      });
      const configCopy = JSON.parse(JSON.stringify(currentConfig));
      applyFilterToTable(table, configCopy);
      applyBtn.textContent = "✓ 已应用";
      setTimeout(() => { applyBtn.textContent = "应用筛选"; }, 1500);
    });

    filterPanel._localConfig = localConfig;
    return filterPanel;
  }

  function createAndSetupButtonsForGroup(groupContainer, episodeTableDiv, table) {
    if (!groupContainer || groupContainer.querySelector('.group-controls')) return;

    const filterPanel = createFilterPanel(groupContainer, episodeTableDiv, table);

    const controls = document.createElement("div");
    controls.className = "group-controls";
    controls.style.cssText = "margin-left: auto; display: flex; gap: 10px; align-items: center;";

    const btnToggle = document.createElement("button");
    btnToggle.innerText = "批量选择";
    btnToggle.className = "filter-btn";

    const btnFilter = document.createElement("button");
    btnFilter.innerText = "筛选选项";
    btnFilter.className = "filter-btn";
    btnFilter.style.display = "none";

    const btnSelectAll = document.createElement("button");
    btnSelectAll.innerText = "全选";
    btnSelectAll.className = "filter-btn";
    btnSelectAll.style.display = "none";

    const btnSelectNone = document.createElement("button");
    btnSelectNone.innerText = "取消全选";
    btnSelectNone.className = "filter-btn";
    btnSelectNone.style.display = "none";

    const btnCopy = document.createElement("button");
    btnCopy.innerText = "复制选中项";
    btnCopy.className = "filter-btn filter-btn-primary";
    btnCopy.style.display = "none";

    controls.appendChild(btnToggle);
    controls.appendChild(btnFilter);
    controls.appendChild(btnSelectAll);
    controls.appendChild(btnSelectNone);
    controls.appendChild(btnCopy);

    groupContainer.style.display = "flex";
    groupContainer.style.alignItems = "center";
    groupContainer.appendChild(controls);

    let isSelectionMode = false;

    btnToggle.addEventListener("click", () => {
      isSelectionMode = !isSelectionMode;
      btnToggle.style.backgroundColor = isSelectionMode ? '#cce5ff' : '';
      btnFilter.style.display = isSelectionMode ? "" : "none";
      btnSelectAll.style.display = isSelectionMode ? "" : "none";
      btnSelectNone.style.display = isSelectionMode ? "" : "none";
      btnCopy.style.display = isSelectionMode ? "" : "none";
      filterPanel.style.display = "none";

      table.querySelectorAll(".select-th, .select-td").forEach(el => {
        el.style.display = isSelectionMode ? "" : "none";
      });

      if (isSelectionMode) {
        const defaultConfig = {
          langSC: true,
          langTC: false,
          langSCTC: false,
          res1080p: true,
          res2160p: false,
          res720p: false
        };
        const configCopy = JSON.parse(JSON.stringify(defaultConfig));
        applyFilterToTable(table, configCopy);
        _log("已自动应用默认筛选: 简体 + 1080p");
      }
    });

    btnFilter.addEventListener("click", () => {
      const isVisible = filterPanel.style.display !== "none";
      filterPanel.style.display = isVisible ? "none" : "block";
      btnFilter.style.backgroundColor = isVisible ? '' : '#cce5ff';
    });

    btnSelectAll.addEventListener("click", () => {
      table.querySelectorAll(".magnet-select-checkbox").forEach(cb => cb.checked = true);
    });

    btnSelectNone.addEventListener("click", () => {
      table.querySelectorAll(".magnet-select-checkbox").forEach(cb => cb.checked = false);
    });

    btnCopy.addEventListener("click", () => {
      const magnetList = [];
      table.querySelectorAll(".magnet-select-checkbox:checked").forEach(cb => {
        const magnet = cb.closest('tr').querySelector(".js-magnet")?.getAttribute("data-clipboard-text");
        if (magnet) magnetList.push(magnet.replace(/&tr=.+?(?=&|$)/g, ""));
      });

      btnCopy.innerText = magnetList.length > 0 ? `✓ 已复制 (${magnetList.length})` : "未选择任何项";
      if (magnetList.length > 0) GM_setClipboard(magnetList.join("\n"));
      setTimeout(() => { btnCopy.innerText = "复制选中项"; }, 2000);
    });
  }


  // --- 5. 表格处理 ---

  function processTable(table) {
    const tableId = table.getAttribute('data-processed-id');
    if (tableId && processedTables.has(tableId)) {
      return false;
    }

    // 修复：适配新的DOM结构
    // 新结构: .subgroup-text → .episode-table (div) → table
    const episodeTableDiv = table.closest('.episode-table');
    if (!episodeTableDiv) {
      // 兼容旧结构: .subgroup-text → table
      const groupContainer = table.previousElementSibling;
      if (!groupContainer || !groupContainer.matches('.subgroup-text')) {
        return false;
      }
      // 旧结构处理逻辑保持不变
      return processTableWithContainer(table, groupContainer, null);
    }

    const groupContainer = episodeTableDiv.previousElementSibling;
    if (!groupContainer || !groupContainer.matches('.subgroup-text')) {
      return false;
    }

    return processTableWithContainer(table, groupContainer, episodeTableDiv);
  }

  function processTableWithContainer(table, groupContainer, episodeTableDiv) {
    const newTableId = 'table_' + Math.random().toString(36).substr(2, 9);
    table.setAttribute('data-processed-id', newTableId);
    processedTables.add(newTableId);

    const headerRow = table.querySelector("tr");
    if (headerRow && !headerRow.querySelector(".select-th")) {
      const th = document.createElement("th");
      th.innerText = "选择";
      th.className = "select-th";
      th.style.cssText = "display: none; width: 40px; text-align: center;";
      headerRow.insertBefore(th, headerRow.firstChild);
    }

    const dataRows = table.querySelectorAll("tr");
    
    dataRows.forEach((tr, index) => {
      if (index === 0) return;
      if (tr.querySelector(".select-td")) return;

      const magnetLink = tr.querySelector(".magnet-link-wrap");
      if (!magnetLink) return;

      const td = document.createElement("td");
      td.className = "select-td";
      td.style.cssText = "display: none; text-align: center;";
      td.innerHTML = `<input type="checkbox" class="magnet-select-checkbox" style="vertical-align: middle;">`;

      tr.insertBefore(td, tr.firstChild);
    });

    createAndSetupButtonsForGroup(groupContainer, episodeTableDiv || table.parentNode, table);

    return true;
  }

  const throttledScan = throttle(() => {
    // 修复：同时支持新旧两种DOM结构
    const newStructureTables = document.querySelectorAll(".subgroup-text + .episode-table table");
    const oldStructureTables = document.querySelectorAll(".subgroup-text + table");
    
    const allTables = new Set([...newStructureTables, ...oldStructureTables]);
    let processedCount = 0;

    allTables.forEach(table => {
      if (processTable(table)) processedCount++;
    });

    if (processedCount > 0) {
      _log(`本次处理了 ${processedCount} 个字幕组。`);
    }

    return processedCount;
  }, 1000);

  function scanAllTables() {
    return throttledScan();
  }


  // --- 6. 自动展开 ---

  function autoExpand(callback, retryCount = 0) {
    const maxRetries = 3;
    const moreButtons = document.querySelectorAll(".js-expand-episode:not([style*='display: none'])");

    if (moreButtons.length === 0) {
      _log("展开完成,等待渲染稳定...");
      setTimeout(() => {
        const newCount = scanAllTables();

        if (newCount > 0 && retryCount < maxRetries) {
          _log(`发现新内容,继续扫描... (${retryCount + 1}/${maxRetries})`);
          setTimeout(() => autoExpand(callback, retryCount + 1), 1200);
        } else {
          callback();
        }
      }, 1200);
      return;
    }

    _log(`点击 ${moreButtons.length} 个展开按钮...`);
    moreButtons.forEach(btn => btn.click());
    setTimeout(() => autoExpand(callback, retryCount), 1000);
  }


  // --- 7. MutationObserver ---

  function setupMutationObserver() {
    const targetNode = document.querySelector(".central-container");
    if (!targetNode) return;

    const debouncedScan = debounce(() => {
      _log("检测到新表格,重新扫描...");
      scanAllTables();
    }, 800);

    const observer = new MutationObserver((mutations) => {
      let hasNewTables = false;

      for (const mutation of mutations) {
        if (mutation.type !== 'childList') continue;
        
        for (const node of mutation.addedNodes) {
          if (node.nodeType === 1) {
            if (node.matches && (node.matches("table") || node.matches(".episode-table"))) {
              hasNewTables = true;
              break;
            }
            if (node.querySelectorAll && (node.querySelectorAll("table").length > 0 || node.querySelectorAll(".episode-table").length > 0)) {
              hasNewTables = true;
              break;
            }
          }
        }
        
        if (hasNewTables) break;
      }

      if (hasNewTables) {
        debouncedScan();
      }
    });

    observer.observe(targetNode, {
      childList: true,
      subtree: true
    });

    _log("MutationObserver 已启动。");
  }


  // --- 8. 初始化 ---

  function init() {
    _log("脚本初始化 v14.2.0 (DOM结构兼容修复)...");

    autoExpand(() => {
      _log("内容加载完成。");
      setupMutationObserver();
    });
  }

  const style = document.createElement('style');
  style.textContent = `
    .filter-btn {
      cursor: pointer;
      padding: 4px 12px;
      border: 1px solid #ccc;
      border-radius: 3px;
      background-color: #fff;
      font-size: 12px;
      transition: all 0.2s;
      white-space: nowrap;
    }
    .filter-btn:hover {
      background-color: #f0f0f0;
      border-color: #999;
    }
    .filter-btn-primary {
      background-color: #007bff;
      color: white;
      border-color: #007bff;
    }
    .filter-btn-primary:hover {
      background-color: #0056b3;
      border-color: #0056b3;
    }
    .filter-fieldset {
      border: none;
      padding: 0;
      margin: 0;
      display: flex;
      gap: 10px;
    }
    .filter-fieldset legend {
      font-size: 0.9em;
      margin-bottom: 5px;
      padding: 0;
      font-weight: 600;
    }
    .filter-fieldset label {
      cursor: pointer;
      white-space: nowrap;
      display: flex;
      align-items: center;
      gap: 4px;
    }
  `;
  document.head.appendChild(style);

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init);
  } else {
    init();
  }

})();