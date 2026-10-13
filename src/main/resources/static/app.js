// 사용자 데이터 콘솔. 화면 상태는 state 하나로 관리하고, 서버 데이터는 캐시하지 않고 필요할 때 다시 조회한다.
// 데이터는 항상 textContent로 렌더링한다 (파싱 실패의 원본 행은 파일 원문이라 임의의 문자열일 수 있다).

const state = { tab: 'users', keyword: '', page: 0, size: 10 };

const $ = (id) => document.getElementById(id);
const salaryFormat = new Intl.NumberFormat('ko-KR');

/** 서버 에러 응답(ProblemDetail)을 담는다. 메시지는 서버의 한글 detail을 그대로 쓴다. */
class ApiError extends Error {
	constructor(message, status, code) {
		super(message);
		this.status = status;
		this.code = code;
	}
}

async function api(path, options = {}) {
	let response;
	try {
		response = await fetch(path, { headers: { Accept: 'application/json' }, ...options });
	} catch {
		throw new ApiError('서버에 연결할 수 없습니다.', 0, 'NETWORK_ERROR');
	}
	const isJson = (response.headers.get('Content-Type') ?? '').includes('json');
	const body = isJson ? await response.json() : null;
	if (response.ok) {
		return body;
	}
	if (body?.detail) {
		throw new ApiError(body.detail, response.status, body.code);
	}
	throw new ApiError(`요청을 처리할 수 없습니다 (HTTP ${response.status})`, response.status, 'HTTP_ERROR');
}

// ---------- 알림 ----------

function showMessage(text, isError = false) {
	const message = $('message');
	message.textContent = text;
	message.classList.toggle('error', isError);
	message.hidden = false;
}

function showError(error) {
	const suffix = error.code && error.code !== 'NETWORK_ERROR' && error.code !== 'HTTP_ERROR' ? ` (${error.code})` : '';
	showMessage(error.message + suffix, true);
}

function clearMessage() {
	$('message').hidden = true;
}

// ---------- 렌더링 공통 ----------

function cell(text, className) {
	const td = document.createElement('td');
	td.textContent = text;
	if (className) {
		td.className = className;
	}
	return td;
}

function emptyRow(tbody, columns, text) {
	const tr = document.createElement('tr');
	const td = cell(text, 'empty');
	td.colSpan = columns;
	tr.append(td);
	tbody.replaceChildren(tr);
}

function formatInstant(iso) {
	const date = new Date(iso);
	const pad = (n) => String(n).padStart(2, '0');
	return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} `
		+ `${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`;
}

// ---------- 사용자 목록·검색 ----------

// 빠르게 페이지를 넘길 때 늦게 도착한 이전 응답이 화면을 덮지 않도록 최신 요청만 반영한다
let listRequestSeq = 0;

async function loadUsers() {
	const seq = ++listRequestSeq;
	const params = new URLSearchParams({ page: state.page, size: state.size });
	let path = '/api/users';
	if (state.keyword) {
		params.set('name', state.keyword);
		path = '/api/users/search';
	}
	const table = $('user-table');
	table.setAttribute('aria-busy', 'true');
	try {
		const result = await api(`${path}?${params}`);
		if (seq !== listRequestSeq) {
			return;
		}
		// 재적재로 데이터가 줄어 현재 페이지가 범위를 벗어나면 마지막 페이지로 옮긴다
		if (result.content.length === 0 && state.page > 0 && result.totalPages > 0) {
			state.page = result.totalPages - 1;
			return loadUsers();
		}
		renderUsers(result);
	} catch (error) {
		if (seq === listRequestSeq) {
			showError(error);
		}
	} finally {
		if (seq === listRequestSeq) {
			table.removeAttribute('aria-busy');
		}
	}
}

function renderUsers(result) {
	const tbody = $('user-table').tBodies[0];
	if (result.content.length === 0) {
		emptyRow(tbody, 5, '결과가 없습니다.');
	} else {
		tbody.replaceChildren(...result.content.map(userRow));
	}
	$('list-caption').textContent = state.keyword
		? `'${state.keyword}' 검색 결과 ${result.totalElements}건`
		: `전체 ${result.totalElements}건`;
	const totalPages = Math.max(result.totalPages, 1);
	$('page-info').textContent = `${result.page + 1} / ${totalPages} 페이지`;
	$('prev').disabled = result.page <= 0;
	$('next').disabled = result.page + 1 >= result.totalPages;
}

function userRow(user) {
	const tr = document.createElement('tr');
	tr.dataset.id = user.id;
	tr.tabIndex = 0;
	tr.append(
		cell(user.id, 'num'),
		cell(user.name),
		cell(user.age, 'num'),
		cell(user.gender),
		cell(salaryFormat.format(user.salary), 'num'));
	return tr;
}

// ---------- 단건 조회 ----------

async function showDetail(id) {
	try {
		const user = await api(`/api/users/${encodeURIComponent(id)}`);
		renderDetail(user);
		clearMessage();
	} catch (error) {
		renderDetail(null);
		showError(error);
	}
}

function renderDetail(user) {
	const detail = $('detail');
	const empty = $('detail-empty');
	for (const row of $('user-table').tBodies[0].rows) {
		row.setAttribute('aria-selected', String(user !== null && row.dataset.id === String(user.id)));
	}
	if (!user) {
		detail.hidden = true;
		empty.textContent = '사용자를 찾을 수 없습니다.';
		empty.hidden = false;
		return;
	}
	const values = { ...user, salary: salaryFormat.format(user.salary) };
	for (const dd of detail.querySelectorAll('dd')) {
		dd.textContent = values[dd.dataset.field];
	}
	empty.hidden = true;
	detail.hidden = false;
}

// ---------- 파싱 실패·적재 상태 ----------

async function loadFailures() {
	try {
		const result = await api('/api/users/parse-failures');
		renderLoadStatus(result);
		renderFailures(result);
	} catch (error) {
		showError(error);
	}
}

function renderLoadStatus(result) {
	const status = $('load-status');
	const loaded = result.loadedAt !== null && result.loadedAt !== undefined;
	status.textContent = loaded ? `적재 시각 ${formatInstant(result.loadedAt)}` : '데이터가 적재되지 않았습니다';
	status.classList.toggle('warn', !loaded);
	$('failure-count').textContent = result.count > 0 ? String(result.count) : '';
}

function renderFailures(result) {
	const tbody = $('failure-table').tBodies[0];
	$('failure-caption').textContent = `마지막 적재에서 실패한 행 ${result.count}건`;
	if (result.failures.length === 0) {
		emptyRow(tbody, 4, '파싱 실패가 없습니다.');
		return;
	}
	tbody.replaceChildren(...result.failures.map((failure) => {
		const tr = document.createElement('tr');
		tr.append(
			cell(failure.lineNumber, 'num'),
			cell(failure.errorCode),
			cell(failure.message),
			cell(failure.rawLine, 'raw'));
		return tr;
	}));
}

// ---------- 재적재 ----------

async function reload() {
	const button = $('reload');
	button.disabled = true;
	try {
		const result = await api('/api/users/reload', { method: 'POST' });
		showMessage(`재적재 완료: 성공 ${result.successCount}건, 실패 ${result.failureCount}건`);
		await Promise.all([loadFailures(), state.tab === 'users' ? loadUsers() : Promise.resolve()]);
	} catch (error) {
		showError(error);
	} finally {
		button.disabled = false;
	}
}

// ---------- 탭 ----------

function selectTab(tab) {
	state.tab = tab;
	clearMessage();
	$('tab-users').setAttribute('aria-selected', String(tab === 'users'));
	$('tab-failures').setAttribute('aria-selected', String(tab === 'failures'));
	$('panel-users').hidden = tab !== 'users';
	$('panel-failures').hidden = tab !== 'failures';
	return tab === 'users' ? loadUsers() : loadFailures();
}

// ---------- 이벤트 연결 ----------

function bindEvents() {
	$('search-form').addEventListener('submit', (event) => {
		event.preventDefault();
		state.keyword = $('keyword').value.trim();
		state.page = 0;
		clearMessage();
		loadUsers();
	});
	$('search-reset').addEventListener('click', () => {
		$('keyword').value = '';
		state.keyword = '';
		state.page = 0;
		clearMessage();
		loadUsers();
	});
	$('id-form').addEventListener('submit', (event) => {
		event.preventDefault();
		showDetail($('user-id').value);
	});
	$('prev').addEventListener('click', () => {
		state.page -= 1;
		loadUsers();
	});
	$('next').addEventListener('click', () => {
		state.page += 1;
		loadUsers();
	});
	$('size').addEventListener('change', (event) => {
		state.size = Number(event.target.value);
		state.page = 0;
		loadUsers();
	});
	const tbody = $('user-table').tBodies[0];
	tbody.addEventListener('click', (event) => {
		const row = event.target.closest('tr[data-id]');
		if (row) {
			showDetail(row.dataset.id);
		}
	});
	tbody.addEventListener('keydown', (event) => {
		const row = event.target.closest('tr[data-id]');
		if (row && (event.key === 'Enter' || event.key === ' ')) {
			event.preventDefault();
			showDetail(row.dataset.id);
		}
	});
	$('tab-users').addEventListener('click', () => selectTab('users'));
	$('tab-failures').addEventListener('click', () => selectTab('failures'));
	$('reload').addEventListener('click', reload);
}

bindEvents();
loadUsers();
loadFailures();
