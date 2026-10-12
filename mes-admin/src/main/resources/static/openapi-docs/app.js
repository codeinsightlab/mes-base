/* 无依赖、只读渲染器。唯一网络请求为读取 OpenAPI JSON，不存储任何凭证。 */
(() => {
  'use strict';
  const nav = document.getElementById('navigation');
  const content = document.getElementById('content');
  const search = document.getElementById('search');
  const methods = ['get', 'post', 'put', 'patch', 'delete', 'head', 'options'];
  let contract, entries = [], selected = 'start';
  const el = (tag, text, className) => {
    const node = document.createElement(tag);
    if (text !== undefined) node.textContent = String(text);
    if (className) node.className = className;
    return node;
  };
  const append = (parent, ...nodes) => nodes.forEach(node => parent.appendChild(node));
  const ref = value => {
    if (!value || !value.$ref) return value || {};
    if (!value.$ref.startsWith('#/')) return {description: '外部引用：' + value.$ref};
    const target = value.$ref.slice(2).split('/').reduce((node, key) =>
      node && node[key.replace(/~1/g, '/').replace(/~0/g, '~')], contract) || {};
    return {...target, ...value, $ref: undefined};
  };
  function schema(value, seen = new Set()) {
    if (!value) return {};
    if (value.$ref && seen.has(value.$ref)) return {type: 'object', description: '递归引用'};
    if (value.$ref) { seen = new Set(seen); seen.add(value.$ref); }
    const source = ref(value);
    if (!source.allOf) return source;
    return source.allOf.reduce((result, item) => {
      const part = schema(item, seen);
      return {...result, ...part, properties: {...result.properties, ...part.properties},
        required: [...new Set([...(result.required || []), ...(part.required || [])])]};
    }, {...source, allOf: undefined});
  }
  function typeOf(value) {
    const s = schema(value);
    if (s.oneOf || s.anyOf) return (s.oneOf || s.anyOf).map(item => typeOf(item)).join(' | ');
    const type = s.type === 'array' ? 'array<' + typeOf(s.items) + '>' : s.type || (s.properties ? 'object' : 'any');
    return type + (s.format ? ' · ' + s.format : '') + (s.nullable ? ' / null' : '');
  }
  function sample(value, depth = 0) {
    if (depth > 6) return null;
    const s = schema(value);
    if (s.example !== undefined) return s.example;
    if (s.default !== undefined) return s.default;
    if (s.enum && s.enum.length) return s.enum[0];
    if (s.oneOf || s.anyOf) return sample((s.oneOf || s.anyOf)[0], depth + 1);
    if (s.type === 'array') return [sample(s.items, depth + 1)];
    if (s.properties) return Object.fromEntries(Object.entries(s.properties).map(([key, item]) => [key, sample(item, depth + 1)]));
    if (s.nullable) return null;
    if (s.type === 'integer' || s.type === 'number') return 0;
    if (s.type === 'boolean') return false;
    return s.type === 'string' ? 'string' : null;
  }
  function inline(parent, text) {
    // Markdown 只支持安全文本、行内代码、强调和 http(s) 链接，不执行 HTML。
    const pattern = /(`[^`]+`|\*\*[^*]+\*\*|\[[^\]]+\]\(https?:\/\/[^\s)]+\))/g;
    let cursor = 0, match;
    while ((match = pattern.exec(text))) {
      append(parent, document.createTextNode(text.slice(cursor, match.index)));
      const token = match[0];
      if (token.startsWith('`')) append(parent, el('code', token.slice(1, -1)));
      else if (token.startsWith('**')) append(parent, el('strong', token.slice(2, -2)));
      else {
        const parts = /^\[([^\]]+)\]\(([^)]+)\)$/.exec(token);
        const link = el('a', parts[1]); link.href = parts[2]; link.rel = 'noopener noreferrer'; append(parent, link);
      }
      cursor = pattern.lastIndex;
    }
    append(parent, document.createTextNode(text.slice(cursor)));
  }
  function markdown(text) {
    const box = el('div', undefined, 'markdown');
    let list, codeLines;
    String(text || '').split('\n').forEach(line => {
      if (line.startsWith('```')) {
        if (codeLines) { append(box, codeBlock(codeLines.join('\n'), 'CODE')); codeLines = undefined; }
        else codeLines = [];
        return;
      }
      if (codeLines) { codeLines.push(line); return; }
      if (!line.trim()) { list = undefined; return; }
      if (/^[-*] /.test(line)) {
        if (!list) { list = el('ul'); append(box, list); }
        const item = el('li'); inline(item, line.slice(2)); append(list, item);
      } else {
        list = undefined;
        const heading = /^(#{1,6})\s+(.+)$/.exec(line);
        const node = el(heading ? 'h3' : 'p'); inline(node, heading ? heading[2] : line); append(box, node);
      }
    });
    if (codeLines) append(box, codeBlock(codeLines.join('\n'), 'CODE'));
    return box;
  }
  function codeBlock(value, label = 'JSON · 示例') {
    const box = el('div', undefined, 'code-block');
    const pre = el('pre');
    const text = typeof value === 'string' ? value : JSON.stringify(value, null, 2);
    const tokens = /("(?:\\.|[^"\\])*"\s*:|"(?:\\.|[^"\\])*"|\b(?:true|false|null)\b|\b-?\d+(?:\.\d+)?\b)/g;
    let cursor = 0, match;
    while ((match = tokens.exec(text))) {
      append(pre, document.createTextNode(text.slice(cursor, match.index)),
        el('span', match[0], match[0].endsWith(':') ? 'json-key' : match[0].startsWith('"') ? 'json-string' : 'json-literal'));
      cursor = tokens.lastIndex;
    }
    append(pre, document.createTextNode(text.slice(cursor)));
    append(box, el('div', label, 'code-label'), pre); return box;
  }
  function table(headers, rows) {
    const wrap = el('div', undefined, 'table-scroll'), table = el('table'), head = el('tr'), body = el('tbody');
    headers.forEach(title => append(head, el('th', title)));
    const thead = el('thead'); append(thead, head); append(table, thead, body);
    rows.forEach(values => { const row = el('tr'); values.forEach(value => { const cell = el('td');
      append(cell, value instanceof Node ? value : document.createTextNode(String(value))); append(row, cell); }); append(body, row); });
    append(wrap, table); return wrap;
  }
  function details(value, description) {
    const s = schema(value), box = el('div'); append(box, markdown(description || s.description));
    if (s.enum) append(box, el('small', 'enum: ' + JSON.stringify(s.enum)));
    if (s.example !== undefined) append(box, el('small', 'example: ' + JSON.stringify(s.example)));
    return box;
  }
  const requirement = required => el('span', required ? '必填' : '可选', required ? 'required' : 'optional');
  function fields(value, prefix = '', depth = 0) {
    const s = schema(value), rows = [];
    if (depth > 5) return rows;
    Object.entries(s.properties || {}).forEach(([name, item]) => {
      rows.push([prefix + name, typeOf(item), requirement((s.required || []).includes(name)), details(item)]);
      const child = schema(item);
      rows.push(...fields(child.type === 'array' ? child.items : child, prefix + name + (child.type === 'array' ? '[].' : '.'), depth + 1));
    });
    return rows;
  }
  function showBody(media, title) {
    Object.entries(media || {}).forEach(([mime, definition]) => {
      append(content, el('h3', title + ' · ' + mime));
      const shape = schema(definition.schema);
      const rows = shape.type === 'array' ? fields(shape.items, '[].') : fields(shape);
      if (rows.length) append(content, table(['字段', '类型', '必填', '说明 / 示例 / 枚举'], rows));
      else append(content, el('p', typeOf(definition.schema), 'muted'), markdown(schema(definition.schema).description));
      const examples = definition.examples ? Object.entries(definition.examples) : [];
      if (definition.example !== undefined) append(content, codeBlock(definition.example));
      else if (examples.length) examples.forEach(([name, value]) => {
        const example = ref(value);
        if (example.value !== undefined) append(content, codeBlock(example.value, name + ' · 示例'));
        else if (example.externalValue) append(content, el('p', '外部示例地址：' + example.externalValue, 'muted'));
      });
      else append(content, codeBlock(sample(definition.schema), 'JSON · 按 Schema 生成的结构示意'));
    });
  }
  function start() {
    append(content, el('p', 'MES Base / DEVELOPER REFERENCE', 'eyebrow'), el('h1', '让系统之间，连接更清晰。'),
      el('p', contract.info.description, 'description'),
      el('div', '先领取应用凭证，再申请访问 Token。每个接口仍需管理员单独授权。', 'notice'));
    const cards = el('div', undefined, 'cards');
    [['01', '领取应用凭证', '管理员创建应用，分配 appKey 与一次性 Secret。'],
      ['02', '获得接口授权', '应用启用后，由管理员配置允许调用的接口。'],
      ['03', '携带 Token 调用', '服务端申请 Token，使用标准 Bearer 请求头。']].forEach(([number, title, text]) => {
      const card = el('div', undefined, 'card'); append(card, el('small', number), el('h3', title), el('p', text)); append(cards, card);
    });
    append(content, cards, el('h2', '当前接口'), el('p', entries.length + ' 个接口 · ' + (contract.info.version || ''), 'muted'));
    append(content, table(['Method', '路径', '说明'], entries.map(entry => [el('span', entry.method.toUpperCase(), 'method ' + entry.method), entry.path, entry.operation.summary || entry.operation.operationId])));
    append(content, el('div', '本文档只展示接口契约，不执行请求、不领取 Token、不保存凭证。示例均为说明用途。', 'notice'));
  }
  function auth() {
    append(content, el('p', 'AUTHENTICATION', 'eyebrow'), el('h1', '认证与应用授权'),
      markdown('调用方应在服务端保存应用凭证。`appSecret` 仅用于申请 Token，业务请求携带访问 Token。'));
    Object.values(contract.components.securitySchemes || {}).forEach(scheme => {
      if (scheme.type === 'http' && scheme.scheme === 'bearer') append(content, markdown(scheme.description), codeBlock('Authorization: Bearer {access_token}', 'HTTP HEADER'));
      else append(content, markdown(scheme.description || scheme.type));
    });
    append(content, markdown('**注意事项**\n- Bearer 与 Token 之间必须有一个空格。\n- 后台用户 Token 不能替代外部访问凭证。\n- 应用禁用、重置 Secret 或撤销后，原 Token 无法继续使用。\n- 文档展示接口不代表当前应用有调用权限。\n- 业务范围授权与接口授权分别生效。'),
      el('div', '具体凭证申请与撤销的请求、响应见“应用认证”分类。文档页面不提供在线申请。', 'notice'));
  }
  function errors() {
    append(content, el('p', 'ERROR REFERENCE', 'eyebrow'), el('h1', '错误码与调用追踪'),
      markdown('失败请求使用对应的 HTTP 状态；响应中的 `errorCode` 表达稳定错误语义。排查问题时请提供 `requestId`，不要提交 Token 或 Secret。'));
    append(content, table(['HTTP', 'errorCode', '说明'], (contract['x-error-codes'] || []).map(error => [error.httpStatus, error.code, error.description])));
    append(content, el('div', '错误码直接来自服务端 OpenApiError 枚举。各接口的响应章节提供对应结构与示例。', 'notice'));
  }
  function operation(entry) {
    const op = entry.operation;
    append(content, el('p', (op.tags || ['API'])[0] + ' / API REFERENCE', 'eyebrow'), el('h1', op.summary || op.operationId || entry.path), markdown(op.description));
    const endpoint = el('div', undefined, 'endpoint'); append(endpoint, el('span', entry.method.toUpperCase(), 'method ' + entry.method), el('code', entry.path)); append(content, endpoint);
    if (op['x-api-code']) append(content, el('p', '接口授权标识：' + op['x-api-code'], 'permission'));
    const security = op.security === undefined ? contract.security : op.security;
    append(content, el('h2', '请求头'));
    const headers = [];
    (security || []).forEach(item => Object.keys(item).forEach(name => {
      const scheme = contract.components.securitySchemes[name];
      if (scheme && scheme.type === 'http' && scheme.scheme === 'bearer') headers.push(['Authorization', 'string', requirement(true), 'Bearer {access_token}']);
    }));
    const body = ref(op.requestBody);
    if (body.content) headers.push(['Content-Type', Object.keys(body.content).join(' | '), requirement(true), '请求体媒体类型']);
    const parameters = [...(entry.pathItem.parameters || []), ...(op.parameters || [])].map(ref);
    const parameterDetails = parameter => details({...parameter.schema,
      ...(parameter.example !== undefined ? {example: parameter.example} : {})}, parameter.description);
    parameters.filter(parameter => parameter.in === 'header').forEach(parameter => headers.push([parameter.name, typeOf(parameter.schema), requirement(parameter.required), parameterDetails(parameter)]));
    if (headers.length) append(content, table(['Header', '类型', '必填', '说明'], headers));
    else append(content, el('p', '此接口不要求访问 Token。', 'muted'));
    ['path', 'query', 'cookie'].forEach(location => {
      const items = parameters.filter(parameter => parameter.in === location);
      if (!items.length) return;
      append(content, el('h2', location.toUpperCase() + ' 参数'), table(['参数', '类型', '必填', '说明 / 示例 / 枚举'], items.map(parameter => [parameter.name, typeOf(parameter.schema), requirement(parameter.required || location === 'path'), parameterDetails(parameter)])));
    });
    if (body.content) {
      append(content, el('h2', '请求体' + (body.required ? ' · 必填' : ' · 可选')), markdown(body.description)); showBody(body.content, '结构');
    } else append(content, el('p', '无请求体。', 'muted'));
    append(content, el('h2', '响应'));
    Object.entries(op.responses || {}).forEach(([status, response]) => {
      const value = ref(response), title = el('h3', undefined, 'response-title');
      append(title, el('span', status, 'http-status'), el('span', value.description || '响应')); append(content, title);
      if (value.headers) append(content, table(['响应 Header', '类型', '说明'], Object.entries(value.headers).map(([name, header]) => [name, typeOf(ref(header).schema), ref(header).description || ''])));
      showBody(value.content, '响应内容');
    });
  }
  function choose(id) {
    selected = id; content.replaceChildren();
    if (id === 'auth') auth(); else if (id === 'errors') errors();
    else if (id === 'start') start(); else {
      const entry = entries.find(item => item.id === id);
      if (entry) operation(entry); else append(content, el('div', '未找到此接口，请从左侧选择。', 'notice'));
    }
    navigation();
  }
  function button(label, id, method) {
    const button = el('button', undefined, selected === id ? 'active' : ''); button.type = 'button';
    if (selected === id) button.setAttribute('aria-current', 'page');
    if (method) append(button, el('span', method.toUpperCase(), 'method ' + method));
    append(button, el('span', label)); button.addEventListener('click', () => { location.hash = encodeURIComponent(id); }); return button;
  }
  function navigation() {
    nav.replaceChildren(); append(nav, button('快速开始', 'start'), button('认证说明', 'auth'), button('错误码', 'errors'));
    const query = search.value.trim().toLowerCase(), groups = new Map();
    entries.filter(entry => [entry.path, entry.method, entry.operation.summary, entry.operation.description, entry.operation.operationId, entry.operation['x-api-code'], ...(entry.operation.tags || [])].join(' ').toLowerCase().includes(query)).forEach(entry => {
      const name = (entry.operation.tags || ['其他接口'])[0]; if (!groups.has(name)) groups.set(name, []); groups.get(name).push(entry);
    });
    groups.forEach((items, name) => { append(nav, el('h3', name + ' · ' + items.length)); items.forEach(entry => append(nav, button(entry.operation.summary || entry.operation.operationId || entry.path, entry.id, entry.method))); });
    if (!groups.size) append(nav, el('p', query ? '没有匹配的接口' : '暂无公开接口', 'empty'));
  }
  search.addEventListener('input', navigation);
  const hashSelection = () => { try { return decodeURIComponent(location.hash.slice(1)) || 'start'; } catch (_) { return 'start'; } };
  window.addEventListener('hashchange', () => { if (contract) choose(hashSelection()); });
  // 相对部署上下文取路径，兼容非根 context-path；不附带认证头，不发送业务请求。
  const context = location.pathname.replace(/\/openapi-docs\/(?:index\.html)?$/, '');
  fetch(context + '/v3/api-docs/open-api', {headers: {Accept: 'application/json'}, credentials: 'omit'})
    .then(response => { if (!response.ok) throw new Error('HTTP ' + response.status); return response.json(); })
    .then(api => {
      if (!api.openapi || !api.paths || !api.info) throw new Error('接口契约格式不正确');
      contract = api; contract.components = contract.components || {}; contract.components.securitySchemes = contract.components.securitySchemes || {};
      Object.entries(api.paths).forEach(([path, pathItem]) => methods.forEach(method => {
        if (pathItem[method]) entries.push({id: method + ':' + path, method, path, pathItem, operation: pathItem[method]});
      }));
      document.getElementById('version').textContent = api.info.version || '';
      choose(hashSelection());
    }).catch(error => {
      content.replaceChildren(el('h1', '文档暂时无法加载'), el('div', '无法读取接口契约：' + error.message + '。请检查服务状态后刷新页面。', 'notice error'));
      nav.replaceChildren(el('p', '接口契约加载失败', 'empty'));
    });
})();
