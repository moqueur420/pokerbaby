"use strict";

const $ = (id) => document.getElementById(id);
const number = new Intl.NumberFormat("ru-RU");
const chips = (value) => number.format(value);
const streets = { PREFLOP: "Префлоп", FLOP: "Флоп", TURN: "Тёрн", RIVER: "Ривер", SHOWDOWN: "Вскрытие", COMPLETE: "Раздача завершена" };
const categories = { HIGH_CARD: "Старшая карта", ONE_PAIR: "Пара", TWO_PAIR: "Две пары", THREE_OF_A_KIND: "Тройка", STRAIGHT: "Стрит", FLUSH: "Флеш", FULL_HOUSE: "Фулл-хаус", FOUR_OF_A_KIND: "Каре", STRAIGHT_FLUSH: "Стрит-флеш" };
const actionNames = { FOLD: "Фолд", CHECK: "Чек", CALL: "Колл", BET: "Бет", RAISE: "Рейз" };
const suits = { s: "♠", h: "♥", d: "♦", c: "♣" };
let state = null;
let busy = false;
let range = null;
let renderedHistory = "";
let renderedBoard = "";
let replayData = null;
let replayIndex = 0;

function tableState() {
    return replayData ? replayData.steps[replayIndex].state : state;
}

function element(tag, className, text) {
    const node = document.createElement(tag);
    if (className) node.className = className;
    if (text !== undefined) node.textContent = text;
    return node;
}

function chipStacks(amount) {
    const stacks = element("span", "chip-stacks");
    stacks.setAttribute("aria-hidden", "true");
    if (!Number.isSafeInteger(amount) || amount <= 0) return stacks;
    let remaining = amount;
    for (const denomination of [5000, 1000, 500, 100, 25, 5, 1]) {
        const count = Math.floor(remaining / denomination);
        remaining %= denomination;
        if (!count) continue;
        const column = element("span", `chip-column chip-${denomination}`);
        // Bound the illustration's size; the adjacent amount always shows the exact total.
        for (let index = 0; index < Math.min(count, 5); index++) {
            column.append(element("span", "poker-chip"));
        }
        stacks.append(column);
        if (stacks.children.length === 3) break;
    }
    return stacks;
}

function renderSeatNote(id, amount, text, label) {
    const note = $(id);
    const showChips = amount > 0 && !tableState().complete;
    note.classList.toggle("has-wager", showChips);
    if (showChips) {
        note.replaceChildren(chipStacks(amount), element("strong", "bet-amount", chips(amount)));
        note.setAttribute("aria-label", `${label}: ${chips(amount)} фишек`);
    } else {
        note.textContent = text;
        note.removeAttribute("aria-label");
    }
}

function historyAvatar(isHuman) {
    const avatar = element("span", `event-avatar ${isHuman ? "you" : "bot"}`);
    avatar.setAttribute("aria-hidden", "true");
    const svg = document.createElementNS("http://www.w3.org/2000/svg", "svg");
    svg.setAttribute("viewBox", "0 0 24 24");
    svg.setAttribute("focusable", "false");
    const shapes = isHuman
        ? [["circle", { cx: 12, cy: 8, r: 3.5 }], ["path", { d: "M5 21v-2a7 7 0 0 1 14 0v2" }]]
        : [["rect", { x: 5, y: 6, width: 14, height: 14, rx: 3 }], ["path", { d: "M12 3v3M2 11v4m20-4v4M9 16h6M9 10v1m6-1v1" }]];
    for (const [tag, attributes] of shapes) {
        const shape = document.createElementNS("http://www.w3.org/2000/svg", tag);
        for (const [key, value] of Object.entries(attributes)) shape.setAttribute(key, String(value));
        svg.append(shape);
    }
    avatar.append(svg);
    return avatar;
}

function card(code, hidden = false) {
    const node = element("div", "card");
    if (hidden) {
        node.classList.add("back");
        node.setAttribute("aria-label", "Закрытая карта");
        return node;
    }
    if (!code) {
        node.classList.add("placeholder");
        node.textContent = "♠";
        node.setAttribute("aria-label", "Карта ещё не сдана");
        return node;
    }
    const rank = code[0] === "T" ? "10" : code[0];
    const suit = suits[code[1]];
    if (code[1] === "h" || code[1] === "d") node.classList.add("red");
    node.setAttribute("aria-label", `${rank} ${suit}`);
    node.append(element("span", "card-rank", rank), element("span", "card-small-suit", suit), element("span", "card-suit", suit));
    return node;
}

function labelButton(id, title, subtitle) {
    $(id).replaceChildren(document.createTextNode(title), element("span", "", subtitle));
}

function wagerValue() {
    return Number($("wager").value);
}

function validWager() {
    const value = wagerValue();
    return range && Number.isSafeInteger(value) && value >= range.min && value <= range.max;
}

function updateWager(value) {
    $("wager").value = String(value);
    $("wager-slider").value = String(value);
    updateRaiseButton();
}

function updateRaiseButton() {
    const type = state?.legal.bet.min ? "Бет" : "Рейз";
    labelButton("raise-button", type, range ? `До ${chips(wagerValue() || range.min)}` : "Недоступен");
    $("raise-button").disabled = busy || !validWager();
    $("wager").setAttribute("aria-invalid", String(Boolean(range && !validWager())));
    $("wager-chips").replaceChildren(...chipStacks(wagerValue()).children);
}

function lockControls() {
    const ready = state && !busy && !replayData;
    $("reset-button").disabled = !state || busy;
    $("next-button").disabled = !ready;
    $("fold-button").disabled = !ready || !state.legal.fold;
    $("call-button").disabled = !ready || !(state.legal.check || state.legal.callAmount > 0);
    $("wager").disabled = !ready || !range;
    $("wager-slider").disabled = !ready || !range || range.min === range.max;
    document.querySelectorAll("[data-preset]").forEach((button) => { button.disabled = !ready || !range; });
    $("table-stage").setAttribute("aria-busy", String(busy));
    updateRaiseButton();
    $("replay-open").disabled = !state || busy;
    $("hand-select").disabled = !state || busy;
    $("replay-exit").disabled = !state || busy;
    $("replay-slider").disabled = !state || busy;
    $("replay-prev").disabled = !state || busy || replayIndex === 0;
    $("replay-next").disabled = !state || busy || !replayData || replayIndex === replayData.steps.length - 1;
    $("resume-button").disabled = busy || !state || !replayData?.steps[replayIndex].canResume;
}

function render() {
    const s = tableState();
    range = s.legal.bet.min ? s.legal.bet : s.legal.raise.min ? s.legal.raise : null;
    $("blinds").textContent = `${chips(s.smallBlind)} / ${chips(s.bigBlind)}`;
    $("human-stack").textContent = chips(s.stacks[0]);
    $("bot-stack").textContent = chips(s.stacks[1]);
    $("human-dealer").hidden = s.buttonSeat !== 0;
    $("bot-dealer").hidden = s.buttonSeat !== 1;
    $("human-cards").replaceChildren(...(s.holeCards.length ? s.holeCards.map((code) => card(code)) : [card(null, true), card(null, true)]));
    $("bot-cards").replaceChildren(...(s.opponentCards.length ? s.opponentCards.map((code) => card(code)) : [card(null, true), card(null, true)]));
    const boardKey = `${s.handNumber}:${s.version}:${s.board.join(",")}`;
    if (renderedBoard !== boardKey) {
        // Keep existing board cards in place when only the betting has changed.
        const previous = $("board").dataset.cards;
        if (previous !== s.board.join(",") || $("board").children.length !== 5) {
            $("board").replaceChildren(...Array.from({ length: 5 }, (_, i) => card(s.board[i])));
            $("board").dataset.cards = s.board.join(",");
        }
        renderedBoard = boardKey;
    }
    $("pot").textContent = chips(s.complete ? s.result.pot : s.pot);
    $("pot-chips").replaceChildren(...chipStacks(s.complete ? s.result.pot : s.pot).children);
    $("pot-label").textContent = s.complete ? "РАЗЫГРАННЫЙ БАНК" : "ОБЩИЙ БАНК";
    $("street").textContent = streets[s.street];
    $("hand-label").textContent = `РАЗДАЧА № ${s.handNumber}`;
    $("human-seat").classList.toggle("complete", s.complete);
    $("human-tag").textContent = s.complete ? "ИГРОК" : "ВАШ ХОД";
    renderSeatNote("human-note", s.streetBets[0], s.complete ? (s.result.categories[0] ? categories[s.result.categories[0]] : "Раздача завершена") : "Ваш ход", "Ваша ставка");
    renderSeatNote("bot-note", s.streetBets[1], s.complete ? (s.result.categories[1] ? categories[s.result.categories[1]] : "Карты не раскрыты") : "Ждёт вашего решения", "Ставка бота");
    $("status").textContent = s.complete ? (s.matchOver ? "Матч завершён" : "Хорошая раздача. Продолжим?") : "Ваш ход";
    $("decision-hint").textContent = s.complete ? "Игра на условные фишки" : (s.legal.callAmount ? `Для колла: ${chips(s.legal.callAmount)}` : "Можно сделать чек");
    $("action-controls").hidden = s.complete || Boolean(replayData);
    $("hand-result").hidden = !s.complete || Boolean(replayData);
    $("replay-controls").hidden = !replayData;
    if (range) {
        for (const id of ["wager", "wager-slider"]) {
            $(id).min = String(range.min);
            $(id).max = String(range.max);
        }
        updateWager(range.min);
        $("wager-limits").textContent = `${chips(range.min)} — ${chips(range.max)}`;
    } else {
        $("wager").value = "";
        $("wager-slider").value = "0";
        $("wager-limits").textContent = s.complete ? "" : "Повышение недоступно";
    }
    labelButton("call-button", s.legal.check ? "Чек" : "Колл", s.legal.check ? "Без ставки" : chips(s.legal.callAmount));
    if (s.complete && !replayData) renderResult();
    if (replayData) renderReplay();
    renderHandSelect();
    renderHistory(s);
    lockControls();
}

function renderHandSelect() {
    const select = $("hand-select");
    const key = JSON.stringify(state.hands);
    if (select.dataset.hands === key) return;
    const selected = replayData ? replayData.handNumber : state.handNumber;
    select.replaceChildren(...state.hands.map((hand) => {
        const option = element("option", "", `№ ${hand.handNumber} · ${hand.complete ? "Завершена" : "В игре"}`);
        option.value = String(hand.handNumber);
        return option;
    }));
    select.value = String(selected);
    select.dataset.hands = key;
}

function replayEventText(event) {
    const player = event.seat === 0 ? "Вы" : "Бот";
    switch (event.type) {
        case "post_blind": return `${player}: блайнд ${chips(event.amount)}`;
        case "deal_hole": return "Сданы ваши закрытые карты";
        case "deal_hidden": return "Сданы закрытые карты бота";
        case "action": return `${player}: ${actionNames[event.action]}${event.to ? ` до ${chips(event.to)}` : ""}`;
        case "deal_board": return `Сданы общие карты · ${streets[event.street]}`;
        case "return_uncalled": return `${player}: возврат ${chips(event.amount)}`;
        case "showdown": return `${player}: ${categories[event.category]}`;
        case "payout": return `${player}: выплата ${chips(event.amount)}`;
        default: return "Начало раздачи · до постановки блайндов";
    }
}

function renderReplay() {
    const step = replayData.steps[replayIndex];
    $("replay-title").textContent = `Раздача № ${replayData.handNumber}`;
    $("replay-slider").max = String(replayData.steps.length - 1);
    $("replay-slider").value = String(replayIndex);
    $("replay-progress").textContent = `${replayIndex} / ${replayData.steps.length - 1}`;
    $("replay-event").textContent = replayEventText(step.event);
    $("status").textContent = "Просмотр истории";
    $("decision-hint").textContent = step.state.decisionPoint ? (step.state.actingSeat === 0 ? "Точка вашего решения" : "Точка решения бота") : "Событие раздачи";
    $("human-tag").textContent = step.canResume ? "ВАШЕ РЕШЕНИЕ" : "ИГРОК";
    $("replay-hint").textContent = step.canResume ? "Можно вернуться к этому ходу и выбрать другое действие."
        : step.state.complete ? (replayData.outcomeVerified ? "Результат проверен по истории раздачи." : "Для проверки результата недостаточно известных карт.")
        : "Продолжить игру можно в точке своего решения.";
    if (!step.state.complete) {
        if (!step.state.streetBets[0]) $("human-note").textContent = step.canResume ? "Ваше решение" : "Просмотр истории";
        if (!step.state.streetBets[1]) $("bot-note").textContent = "Просмотр истории";
    }
}

function moveReplay(index) {
    if (!replayData || busy) return;
    replayIndex = Math.max(0, Math.min(index, replayData.steps.length - 1));
    render();
}

function renderResult() {
    const result = state.result;
    const won = result.winnerSeat === 0;
    $("result-title").textContent = result.winnerSeat === -1 ? "Банк поделён поровну" : won ? "Вы забираете банк!" : "Банк забирает бот";
    const net = result.net;
    const netText = `Ваш итог: ${net > 0 ? "+" : ""}${chips(net)} фишек`;
    $("result-detail").textContent = `${result.showdown ? "Вскрытие" : "Соперник сбросил карты"}. ${netText}.`;
    if (!result.showdown && !won) $("result-detail").textContent = `Вы сбросили карты. ${netText}.`;
    if (state.matchOver) {
        $("result-title").textContent = state.stacks[0] > 0 ? "Матч за вами!" : "Этот матч за ботом";
        $("result-detail").textContent = "Можно начать новый матч со стеками по 10 000.";
    }
    $("next-button").textContent = state.matchOver ? "Сыграть ещё раз ↻" : "Следующая раздача →";
}

function renderHistory(view) {
    const key = `${view.handNumber}:${JSON.stringify(view.history)}`;
    if (key === renderedHistory) return;
    renderedHistory = key;
    const list = $("history-list");
    const nearBottom = list.scrollTop + list.clientHeight >= list.scrollHeight - 45;
    const newHand = list.dataset.hand !== String(view.handNumber);
    const fragment = document.createDocumentFragment();
    fragment.append(element("li", "history-street", "Префлоп"));
    for (const event of view.history) {
        if (event.type === "deal_board") {
            fragment.append(element("li", "history-street", streets[event.street]));
            const row = element("li", "history-row");
            row.append(element("span", "event-avatar", "♠"), element("span", "event-description", event.cards.map((code) => `${code[0] === "T" ? "10" : code[0]}${suits[code[1]]}`).join("  ")));
            fragment.append(row);
            continue;
        }
        if (event.type === "deal_hole") continue;
        const row = element("li", "history-row");
        const isHuman = event.seat === 0;
        const avatar = historyAvatar(isHuman);
        const content = element("div");
        content.append(element("div", "event-name", isHuman ? "Вы" : "Бот"));
        let description = "";
        let amount = "";
        switch (event.type) {
            case "post_blind": description = event.seat === view.buttonSeat ? "Малый блайнд" : "Большой блайнд"; amount = chips(event.amount); break;
            case "action": description = actionNames[event.action]; amount = event.to ? `до ${chips(event.to)}` : ""; break;
            case "showdown": description = `Вскрытие · ${categories[event.category]}`; break;
            case "return_uncalled": description = "Возврат ставки"; amount = `+${chips(event.amount)}`; break;
            case "payout": description = "Забирает банк"; amount = `+${chips(event.amount)}`; break;
        }
        content.append(element("div", "event-description", description));
        row.append(avatar, content, element("span", "event-amount", amount));
        fragment.append(row);
    }
    list.replaceChildren(fragment);
    list.dataset.hand = String(view.handNumber);
    $("history-count").textContent = view.history.filter((event) => event.type === "action").length;
    if (nearBottom || newHand) list.scrollTop = list.scrollHeight;
}

async function request(command, fields = {}) {
    if (busy) return;
    busy = true;
    $("error").hidden = true;
    $("retry-button").hidden = true;
    lockControls();
    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), 10000);
    try {
        const options = { signal: controller.signal, credentials: "same-origin" };
        if (command !== "state" && command !== "replay") {
            options.method = "POST";
            options.body = new URLSearchParams({ ...fields, version: String(state.version) });
        }
        const query = command === "replay" ? `?${new URLSearchParams(fields)}` : "";
        const response = await fetch(`/api/${command}${query}`, options);
        const payload = await response.json();
        if (!response.ok) {
            // Refresh after rejected or duplicated commands; never replay an uncertain wager.
            const latest = await fetch("/api/state", { signal: controller.signal, credentials: "same-origin" });
            if (latest.ok) { state = await latest.json(); replayData = null; render(); }
            throw new Error(payload.error || "Не удалось выполнить действие.");
        }
        if (command === "replay") {
            replayData = payload;
            replayIndex = 0;
        } else {
            state = payload;
            replayData = null;
        }
        render();
    } catch (error) {
        $("error").textContent = error instanceof TypeError || error.name === "AbortError" ? "Нет связи со столом. Проверьте, что сервер запущен, и переподключитесь." : error.message;
        $("error").hidden = false;
        $("retry-button").hidden = false;
        // A timed-out command may have reached the server. Read its state before allowing another.
        if (error instanceof TypeError || error.name === "AbortError") {
            state = null;
            replayData = null;
            range = null;
            $("status").textContent = "Связь со столом потеряна";
        }
    } finally {
        clearTimeout(timer);
        busy = false;
        lockControls();
    }
}

$("fold-button").addEventListener("click", () => request("action", { type: "FOLD" }));
$("call-button").addEventListener("click", () => request("action", { type: state.legal.check ? "CHECK" : "CALL" }));
$("raise-button").addEventListener("click", () => { if (validWager()) request("action", { type: state.legal.bet.min ? "BET" : "RAISE", to: String(wagerValue()) }); });
$("next-button").addEventListener("click", () => request(state.matchOver ? "new" : "next"));
$("retry-button").addEventListener("click", () => request("state"));
$("replay-open").addEventListener("click", () => request("replay", { hand: $("hand-select").value }));
$("replay-exit").addEventListener("click", () => { replayData = null; render(); });
$("replay-prev").addEventListener("click", () => moveReplay(replayIndex - 1));
$("replay-next").addEventListener("click", () => moveReplay(replayIndex + 1));
$("replay-slider").addEventListener("input", () => moveReplay(Number($("replay-slider").value)));
$("resume-button").addEventListener("click", () => $("resume-dialog").showModal());
$("resume-cancel").addEventListener("click", () => $("resume-dialog").close());
$("resume-confirm").addEventListener("click", () => {
    $("resume-dialog").close();
    if (replayData && replayData.steps[replayIndex].canResume) request("resume", { hand: String(replayData.handNumber), step: String(replayIndex) });
});
$("wager").addEventListener("input", () => { $("wager-slider").value = $("wager").value; updateRaiseButton(); });
$("wager-slider").addEventListener("input", () => updateWager(Number($("wager-slider").value)));
document.querySelectorAll("[data-preset]").forEach((button) => {
    button.addEventListener("click", () => {
        if (!range) return;
        const base = Math.max(...state.streetBets);
        const afterCallPot = state.pot + state.legal.callAmount;
        const sizes = { min: range.min, half: base + Math.floor(afterCallPot / 2), pot: base + afterCallPot, all: range.max };
        updateWager(Math.min(range.max, Math.max(range.min, sizes[button.dataset.preset])));
    });
});
$("help-button").addEventListener("click", () => $("help-dialog").showModal());
$("help-close").addEventListener("click", () => $("help-dialog").close());
$("reset-button").addEventListener("click", () => $("reset-dialog").showModal());
$("reset-cancel").addEventListener("click", () => $("reset-dialog").close());
$("reset-confirm").addEventListener("click", () => { $("reset-dialog").close(); request("new"); });
document.querySelectorAll("dialog").forEach((dialog) => {
    dialog.addEventListener("click", (event) => { if (event.target === dialog) { const box = dialog.getBoundingClientRect(); if (event.clientX < box.left || event.clientX > box.right || event.clientY < box.top || event.clientY > box.bottom) dialog.close(); } });
});
request("state");
