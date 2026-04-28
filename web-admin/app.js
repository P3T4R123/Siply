const STORAGE_KEY = "siply.webAdmin.payload";

const state = {
  app: null,
  db: null,
  auth: null,
  user: null,
  payload: null,
  cafeName: "",
  products: [],
  receipts: [],
  cart: new Map(),
  activeCategory: "all",
  productUnsubscribe: null,
  receiptUnsubscribe: null,
};

const els = {
  loginPanel: document.querySelector("#loginPanel"),
  adminPanel: document.querySelector("#adminPanel"),
  invitePayload: document.querySelector("#invitePayload"),
  connectButton: document.querySelector("#connectButton"),
  clearSessionButton: document.querySelector("#clearSessionButton"),
  loginStatus: document.querySelector("#loginStatus"),
  cafeName: document.querySelector("#cafeName"),
  refreshButton: document.querySelector("#refreshButton"),
  logoutButton: document.querySelector("#logoutButton"),
  tabs: Array.from(document.querySelectorAll(".tab")),
  tabPanels: Array.from(document.querySelectorAll(".tab-panel")),

  dashboardFrom: document.querySelector("#dashboardFrom"),
  dashboardTo: document.querySelector("#dashboardTo"),
  dashboardWaiter: document.querySelector("#dashboardWaiter"),
  todayDashboardButton: document.querySelector("#todayDashboardButton"),
  dashRevenue: document.querySelector("#dashRevenue"),
  dashReceipts: document.querySelector("#dashReceipts"),
  dashAverage: document.querySelector("#dashAverage"),
  dashHouse: document.querySelector("#dashHouse"),
  waiterChart: document.querySelector("#waiterChart"),
  categoryChart: document.querySelector("#categoryChart"),
  productChart: document.querySelector("#productChart"),
  hourChart: document.querySelector("#hourChart"),

  posSearchInput: document.querySelector("#posSearchInput"),
  categoryChips: document.querySelector("#categoryChips"),
  productCards: document.querySelector("#productCards"),
  cartLines: document.querySelector("#cartLines"),
  clearCartButton: document.querySelector("#clearCartButton"),
  houseNote: document.querySelector("#houseNote"),
  musicNote: document.querySelector("#musicNote"),
  cartTotal: document.querySelector("#cartTotal"),
  saveReceiptButton: document.querySelector("#saveReceiptButton"),

  receiptFrom: document.querySelector("#receiptFrom"),
  receiptTo: document.querySelector("#receiptTo"),
  receiptWaiter: document.querySelector("#receiptWaiter"),
  receiptNoteFilter: document.querySelector("#receiptNoteFilter"),
  exportReceiptsButton: document.querySelector("#exportReceiptsButton"),
  exportSalesButton: document.querySelector("#exportSalesButton"),
  resetReceiptsButton: document.querySelector("#resetReceiptsButton"),
  receiptsList: document.querySelector("#receiptsList"),

  productsCount: document.querySelector("#productsCount"),
  activeCount: document.querySelector("#activeCount"),
  stockValue: document.querySelector("#stockValue"),
  lowStockCount: document.querySelector("#lowStockCount"),
  negativeStockPanel: document.querySelector("#negativeStockPanel"),
  fixNegativeStockButton: document.querySelector("#fixNegativeStockButton"),
  newProductForm: document.querySelector("#newProductForm"),
  newCategory: document.querySelector("#newCategory"),
  newName: document.querySelector("#newName"),
  newPrice: document.querySelector("#newPrice"),
  newStock: document.querySelector("#newStock"),
  newEmoji: document.querySelector("#newEmoji"),
  searchInput: document.querySelector("#searchInput"),
  productsTable: document.querySelector("#productsTable"),
  toggleProcurementButton: document.querySelector("#toggleProcurementButton"),
  procurementPanel: document.querySelector("#procurementPanel"),
  procurementNote: document.querySelector("#procurementNote"),
  procurementTable: document.querySelector("#procurementTable"),
  saveProcurementButton: document.querySelector("#saveProcurementButton"),

  exportCatalogButton: document.querySelector("#exportCatalogButton"),
  importCatalogInput: document.querySelector("#importCatalogInput"),
  settingsInfo: document.querySelector("#settingsInfo"),
  downloadBackupButton: document.querySelector("#downloadBackupButton"),
  forgetWebCodeButton: document.querySelector("#forgetWebCodeButton"),
};

function setStatus(message, isError = false) {
  els.loginStatus.textContent = message;
  els.loginStatus.style.color = isError ? "var(--danger)" : "var(--muted)";
}

function parsePayload(raw) {
  const payload = JSON.parse(raw.trim());
  if (!payload.apiKey || !payload.projectId || !payload.cafeId || !payload.inviteCode) {
    throw new Error("Kod nema sve potrebne Firebase podatke.");
  }
  if ((payload.role || "waiter") !== "admin") {
    throw new Error("Ovaj kod nije web admin kod.");
  }
  return payload;
}

function firebaseConfig(payload) {
  return {
    apiKey: payload.apiKey,
    authDomain: `${payload.projectId}.firebaseapp.com`,
    projectId: payload.projectId,
    appId: payload.appId || undefined,
  };
}

function initFirebase(payload) {
  const appName = `siply-web-${payload.projectId}`;
  state.app = firebase.apps.find((app) => app.name === appName)
    || firebase.initializeApp(firebaseConfig(payload), appName);
  state.auth = firebase.auth(state.app);
  state.db = firebase.firestore(state.app);
}

async function connect(rawPayload) {
  els.connectButton.disabled = true;
  setStatus("Spajam web admin...");

  try {
    const payload = parsePayload(rawPayload);
    initFirebase(payload);

    const authResult = await state.auth.signInAnonymously();
    state.user = authResult.user;
    state.payload = payload;

    const cafeRef = cafeDoc();
    const inviteDoc = await cafeRef.collection("invites").doc(payload.inviteCode).get();
    if (!inviteDoc.exists || inviteDoc.data().active !== true || inviteDoc.data().role !== "admin") {
      throw new Error("Web admin kod nije valjan ili je istekao.");
    }

    const cafeSnapshot = await cafeRef.get();
    if (!cafeSnapshot.exists) {
      throw new Error("Kafić nije pronađen.");
    }

    await cafeRef.collection("members").doc(state.user.uid).set({
      uid: state.user.uid,
      name: "Web Admin",
      role: "admin",
      joinedAt: firebase.firestore.FieldValue.serverTimestamp(),
      inviteCode: payload.inviteCode,
    });

    localStorage.setItem(STORAGE_KEY, rawPayload.trim());
    state.cafeName = cafeSnapshot.data().name || "Siply kafić";
    els.cafeName.textContent = state.cafeName;
    els.settingsInfo.textContent = `Spojeno na ${state.cafeName}. Web korisnik: ${state.user.uid}`;
    setDefaultDates();
    showAdmin();
    subscribeProducts();
    subscribeReceipts();
  } catch (error) {
    console.error(error);
    setStatus(error.message || "Spajanje nije uspjelo.", true);
  } finally {
    els.connectButton.disabled = false;
  }
}

function showAdmin() {
  els.loginPanel.classList.add("hidden");
  els.adminPanel.classList.remove("hidden");
}

function showLogin() {
  els.adminPanel.classList.add("hidden");
  els.loginPanel.classList.remove("hidden");
}

function cafeDoc() {
  return state.db.collection("cafes").doc(state.payload.cafeId);
}

function productsCollection() {
  return cafeDoc().collection("catalogProducts");
}

function receiptsCollection() {
  return cafeDoc().collection("receipts");
}

function productRef(id) {
  return productsCollection().doc(id);
}

function subscribeProducts() {
  if (state.productUnsubscribe) state.productUnsubscribe();

  state.productUnsubscribe = productsCollection().onSnapshot((snapshot) => {
    state.products = snapshot.docs
      .map((doc) => ({ id: doc.id, ...doc.data() }))
      .sort((a, b) => {
        const categorySort = String(a.categoryName || "").localeCompare(String(b.categoryName || ""), "hr");
        if (categorySort !== 0) return categorySort;
        return String(a.name || "").localeCompare(String(b.name || ""), "hr");
      });
    renderAll();
  }, (error) => {
    console.error(error);
    setStatus("Ne mogu učitati katalog. Provjeri Firestore rules.", true);
  });
}

function subscribeReceipts() {
  if (state.receiptUnsubscribe) state.receiptUnsubscribe();

  state.receiptUnsubscribe = receiptsCollection()
    .orderBy("createdAt", "desc")
    .onSnapshot((snapshot) => {
      state.receipts = snapshot.docs.map((doc) => normalizeReceipt(doc));
      renderAll();
    }, (error) => {
      console.error(error);
      alert("Ne mogu učitati račune. Provjeri Firestore rules.");
    });
}

function normalizeReceipt(doc) {
  const data = doc.data();
  const items = Array.isArray(data.items) ? data.items.map((item) => ({
    productId: item.productId ?? null,
    cloudProductId: item.cloudProductId ?? "",
    name: String(item.name || ""),
    quantity: Number(item.quantity || 0),
    lineTotalCents: Number(item.lineTotalCents || 0),
  })) : [];

  return {
    id: doc.id,
    receiptNumber: String(data.receiptNumber || ""),
    waiterId: String(data.waiterId || ""),
    waiterName: String(data.waiterName || "Nepoznato"),
    role: String(data.role || ""),
    createdAt: Number(data.createdAt || 0),
    totalCents: Number(data.totalCents || 0),
    note: String(data.note || ""),
    items,
  };
}

function renderAll() {
  renderWaiterOptions();
  renderDashboard();
  renderPos();
  renderCart();
  renderReceipts();
  renderCatalog();
  renderProcurement();
}

function setDefaultDates() {
  const today = dateKey(new Date());
  [els.dashboardFrom, els.dashboardTo, els.receiptFrom, els.receiptTo].forEach((input) => {
    input.value = today;
  });
}

function renderWaiterOptions() {
  const waiters = uniqueWaiters();
  const html = [`<option value="all">Svi</option>`]
    .concat(waiters.map((name) => `<option value="${escapeAttr(name)}">${escapeHtml(name)}</option>`))
    .join("");

  const currentDashboard = els.dashboardWaiter.value || "all";
  const currentReceipt = els.receiptWaiter.value || "all";
  els.dashboardWaiter.innerHTML = html;
  els.receiptWaiter.innerHTML = html;
  els.dashboardWaiter.value = waiters.includes(currentDashboard) ? currentDashboard : "all";
  els.receiptWaiter.value = waiters.includes(currentReceipt) ? currentReceipt : "all";
}

function uniqueWaiters() {
  return Array.from(new Set(state.receipts.map((receipt) => receipt.waiterName).filter(Boolean)))
    .sort((a, b) => a.localeCompare(b, "hr"));
}

function renderDashboard() {
  const receipts = filteredReceipts({
    from: els.dashboardFrom.value,
    to: els.dashboardTo.value,
    waiter: els.dashboardWaiter.value,
    note: "all",
  });

  const total = sum(receipts.map((receipt) => receipt.totalCents));
  const houseTotal = sum(receipts.filter((receipt) => receipt.note.includes("Na račun kuće")).map((receipt) => receipt.totalCents));
  els.dashRevenue.textContent = formatEuro(total);
  els.dashReceipts.textContent = receipts.length.toString();
  els.dashAverage.textContent = formatEuro(receipts.length ? Math.round(total / receipts.length) : 0);
  els.dashHouse.textContent = formatEuro(houseTotal);

  renderBars(els.waiterChart, groupReceiptTotals(receipts, (receipt) => receipt.waiterName || "Nepoznato"), formatEuro);
  renderBars(els.categoryChart, groupItemTotals(receipts, categoryForItem), formatEuro);
  renderBars(els.productChart, groupItemTotals(receipts, (item) => item.name || "Artikl"), formatEuro, 10);
  renderBars(els.hourChart, groupReceiptTotals(receipts, (receipt) => `${new Date(receipt.createdAt).getHours().toString().padStart(2, "0")}:00`), formatEuro);
}

function renderBars(container, rows, formatter, limit = 12) {
  const visible = rows.slice(0, limit);
  if (visible.length === 0) {
    container.innerHTML = `<div class="empty">Nema podataka za odabrani period.</div>`;
    return;
  }
  const max = Math.max(...visible.map((row) => row.value), 1);
  container.innerHTML = visible.map((row) => `
    <div class="bar-row">
      <div class="bar-meta">
        <strong>${escapeHtml(row.label)}</strong>
        <span>${formatter(row.value)}</span>
      </div>
      <div class="bar-track"><div class="bar-fill" style="width:${Math.max(4, (row.value / max) * 100)}%"></div></div>
    </div>
  `).join("");
}

function renderPos() {
  const categories = ["all"].concat(Array.from(new Set(
    state.products
      .filter((product) => product.isActive !== false)
      .map((product) => product.categoryName || "Ostalo"),
  )).sort((a, b) => a.localeCompare(b, "hr")));

  if (!categories.includes(state.activeCategory)) state.activeCategory = "all";
  els.categoryChips.innerHTML = categories.map((category) => `
    <button class="chip ${state.activeCategory === category ? "active" : ""}" data-category="${escapeAttr(category)}">
      ${category === "all" ? "Sve" : escapeHtml(category)}
    </button>
  `).join("");

  const query = els.posSearchInput.value.trim().toLowerCase();
  const products = state.products.filter((product) => {
    if (product.isActive === false) return false;
    if (state.activeCategory !== "all" && product.categoryName !== state.activeCategory) return false;
    if (!query) return true;
    return `${product.categoryName || ""} ${product.name || ""}`.toLowerCase().includes(query);
  });

  if (products.length === 0) {
    els.productCards.innerHTML = `<div class="empty">Nema artikala za prikaz.</div>`;
    return;
  }

  els.productCards.innerHTML = products.map((product) => `
    <button class="product-card" data-product-id="${product.id}">
      <span class="product-emoji">${escapeHtml(product.emoji || "☕")}</span>
      <strong>${escapeHtml(product.name || "")}</strong>
      <small>${escapeHtml(product.categoryName || "")}</small>
      <span>${formatEuro(product.priceCents || 0)}</span>
      <em>Stanje: ${safeStock(product)}</em>
    </button>
  `).join("");
}

function renderCart() {
  const lines = Array.from(state.cart.values());
  if (lines.length === 0) {
    els.cartLines.innerHTML = `<div class="empty">Račun je prazan.</div>`;
  } else {
    els.cartLines.innerHTML = lines.map((line) => `
      <div class="cart-line" data-product-id="${line.product.id}">
        <div>
          <strong>${escapeHtml(line.product.name || "")}</strong>
          <span>${line.quantity} x ${formatEuro(line.product.priceCents || 0)}</span>
        </div>
        <div class="qty-actions">
          <button class="secondary small-btn" data-action="minus">-</button>
          <button class="secondary small-btn" data-action="plus">+</button>
          <button class="secondary small-btn" data-action="remove">x</button>
        </div>
      </div>
    `).join("");
  }
  els.cartTotal.textContent = formatEuro(cartTotalCents());
}

function renderReceipts() {
  const receipts = filteredReceipts({
    from: els.receiptFrom.value,
    to: els.receiptTo.value,
    waiter: els.receiptWaiter.value,
    note: els.receiptNoteFilter.value,
  });

  if (receipts.length === 0) {
    els.receiptsList.innerHTML = `<div class="empty">Nema računa za odabrani filter.</div>`;
    return;
  }

  els.receiptsList.innerHTML = receipts.map((receipt) => `
    <article class="receipt-card">
      <div class="receipt-head">
        <div>
          <strong>${escapeHtml(receipt.receiptNumber || receipt.id)}</strong>
          <span>${formatDateTime(receipt.createdAt)} • ${escapeHtml(receipt.waiterName)}</span>
        </div>
        <strong>${formatEuro(receipt.totalCents)}</strong>
      </div>
      ${receipt.note ? `<div class="note-pill">${escapeHtml(receipt.note)}</div>` : ""}
      <div class="receipt-items">
        ${receipt.items.map((item) => `
          <div>
            <span>${escapeHtml(item.name)}</span>
            <span>${item.quantity} kom • ${formatEuro(item.lineTotalCents)}</span>
          </div>
        `).join("")}
      </div>
    </article>
  `).join("");
}

function renderCatalog() {
  const query = els.searchInput.value.trim().toLowerCase();
  const visibleProducts = state.products.filter((product) => {
    if (!query) return true;
    return `${product.categoryName || ""} ${product.name || ""}`.toLowerCase().includes(query);
  });

  els.productsCount.textContent = state.products.length.toString();
  els.activeCount.textContent = state.products.filter((product) => product.isActive !== false).length.toString();
  const negativeProducts = state.products.filter((product) => rawStock(product) < 0);
  els.negativeStockPanel.classList.toggle("hidden", negativeProducts.length === 0);
  els.lowStockCount.textContent = state.products.filter((product) => safeStock(product) <= 5).length.toString();
  els.stockValue.textContent = formatEuro(
    state.products.reduce((total, product) => total + Number(product.priceCents || 0) * safeStock(product), 0),
  );

  if (visibleProducts.length === 0) {
    els.productsTable.innerHTML = `<div class="empty">Nema artikala za prikaz.</div>`;
    return;
  }

  const header = `
    <div class="table-header product-table-header">
      <span>Kategorija</span><span>Artikl</span><span>Cijena</span><span>Stanje</span><span>Emoji</span><span>Aktivan</span><span></span>
    </div>`;

  els.productsTable.innerHTML = header + visibleProducts.map((product) => productRow(product)).join("");
}

function productRow(product) {
  return `
    <div class="product-row" data-id="${product.id}">
      <input data-field="categoryName" value="${escapeAttr(product.categoryName || "")}">
      <input data-field="name" value="${escapeAttr(product.name || "")}">
      <input data-field="price" inputmode="decimal" value="${formatPriceInput(product.priceCents || 0)}">
      <input data-field="stock" inputmode="numeric" value="${Number(product.stockQuantityUnits || 0)}">
      <input data-field="emoji" value="${escapeAttr(product.emoji || "")}">
      <label class="active-cell"><input data-field="active" type="checkbox" ${product.isActive === false ? "" : "checked"}></label>
      <div class="row-actions">
        <button class="primary" data-action="save">Spremi</button>
        <button class="danger" data-action="delete">Obriši</button>
      </div>
    </div>`;
}

function renderProcurement() {
  if (els.procurementPanel.classList.contains("hidden")) return;
  if (state.products.length === 0) {
    els.procurementTable.innerHTML = `<div class="empty">Nema artikala za unos.</div>`;
    return;
  }

  const header = `<div class="table-header procurement-header"><span>Artikl</span><span>Trenutno</span><span>Ulaz robe</span><span>Novo stanje</span></div>`;
  els.procurementTable.innerHTML = header + state.products.map((product) => {
    const stock = safeStock(product);
    return `
      <div class="procurement-row" data-id="${product.id}" data-stock="${stock}">
        <strong>${escapeHtml(product.name || "")}<small>${escapeHtml(product.categoryName || "")}</small></strong>
        <span>${stock}</span>
        <input data-field="procurementQty" inputmode="numeric" placeholder="0">
        <span data-field="newStock">${stock}</span>
      </div>
    `;
  }).join("");
}

async function saveExistingProduct(row) {
  const id = row.dataset.id;
  const existing = state.products.find((product) => product.id === id);
  if (!existing) return;

  const categoryName = readField(row, "categoryName").trim();
  const name = readField(row, "name").trim();
  if (!categoryName || !name) {
    alert("Kategorija i artikl su obavezni.");
    return;
  }

  await productRef(id).set({
    categoryId: categoryIdFor(categoryName, existing.categoryId),
    categoryName,
    name,
    priceCents: parseEuroCents(readField(row, "price")),
    stockQuantityUnits: parseStock(readField(row, "stock")),
    stockUpdatedAt: Date.now(),
    emoji: readField(row, "emoji").trim() || "☕",
    accentColor: Number(existing.accentColor || 0),
    sortOrder: Number(existing.sortOrder || 1),
    isActive: row.querySelector('[data-field="active"]').checked,
    updatedAt: Date.now(),
  }, { merge: true });
}

async function deleteProduct(row) {
  const id = row.dataset.id;
  const existing = state.products.find((product) => product.id === id);
  if (!existing) return;
  if (!confirm(`Obrisati artikl "${existing.name}"?`)) return;
  await productRef(id).delete();
}

async function createProduct(event) {
  event.preventDefault();
  const categoryName = els.newCategory.value.trim();
  const name = els.newName.value.trim();
  if (!categoryName || !name) {
    alert("Kategorija i artikl su obavezni.");
    return;
  }

  await productsCollection().add({
    categoryId: categoryIdFor(categoryName),
    categoryName,
    name,
    priceCents: parseEuroCents(els.newPrice.value),
    stockQuantityUnits: parseStock(els.newStock.value),
    stockUpdatedAt: Date.now(),
    emoji: els.newEmoji.value.trim() || "☕",
    accentColor: 0,
    sortOrder: nextSortOrder(categoryName),
    isActive: true,
    createdByUid: state.user.uid,
    updatedAt: Date.now(),
  });

  event.target.reset();
}

function addToCart(productId) {
  const product = state.products.find((item) => item.id === productId);
  if (!product) return;
  const current = state.cart.get(productId);
  state.cart.set(productId, {
    product,
    quantity: current ? current.quantity + 1 : 1,
  });
  renderCart();
}

function updateCart(productId, action) {
  const current = state.cart.get(productId);
  if (!current) return;
  if (action === "plus") current.quantity += 1;
  if (action === "minus") current.quantity -= 1;
  if (action === "remove" || current.quantity <= 0) state.cart.delete(productId);
  renderCart();
}

async function saveReceipt() {
  const lines = Array.from(state.cart.values());
  if (lines.length === 0) {
    alert("Račun je prazan.");
    return;
  }

  const now = Date.now();
  const receiptNumber = `WEB-${compactDateTime(now)}`;
  const note = [els.houseNote.checked ? "Na račun kuće" : "", els.musicNote.checked ? "Muzika" : ""]
    .filter(Boolean)
    .join(" • ");
  const items = lines.map((line) => ({
    productId: null,
    cloudProductId: line.product.id,
    name: line.product.name || "",
    quantity: line.quantity,
    lineTotalCents: Number(line.product.priceCents || 0) * line.quantity,
  }));
  const totalCents = sum(items.map((item) => item.lineTotalCents));

  const batch = state.db.batch();
  batch.set(receiptsCollection().doc(), {
    receiptNumber,
    waiterId: state.user.uid,
    waiterName: "Web Admin",
    role: "admin",
    createdAt: now,
    totalCents,
    note,
    items,
  });
  lines.forEach((line) => {
    const currentStock = Number(line.product.stockQuantityUnits || 0);
    batch.set(productRef(line.product.id), {
      stockQuantityUnits: Math.max(0, currentStock - line.quantity),
      stockUpdatedAt: now,
      updatedAt: now,
    }, { merge: true });
  });

  els.saveReceiptButton.disabled = true;
  try {
    await batch.commit();
    state.cart.clear();
    els.houseNote.checked = false;
    els.musicNote.checked = false;
    renderCart();
  } finally {
    els.saveReceiptButton.disabled = false;
  }
}

async function saveProcurement() {
  const rows = Array.from(document.querySelectorAll(".procurement-row"));
  const updates = rows.map((row) => {
    const quantity = parseStock(row.querySelector('[data-field="procurementQty"]').value || "0");
    const product = state.products.find((item) => item.id === row.dataset.id);
    return { product, quantity };
  }).filter((row) => row.product && row.quantity > 0);

  if (updates.length === 0) {
    alert("Upiši barem jednu količinu za unos robe.");
    return;
  }

  const now = Date.now();
  const batch = state.db.batch();
  updates.forEach(({ product, quantity }) => {
    batch.set(productRef(product.id), {
      stockQuantityUnits: safeStock(product) + quantity,
      stockUpdatedAt: now,
      updatedAt: now,
    }, { merge: true });
  });
  await batch.commit();
  els.procurementNote.value = "";
  renderProcurement();
}

async function fixNegativeStocks() {
  const negativeProducts = state.products.filter((product) => rawStock(product) < 0);
  if (negativeProducts.length === 0) {
    alert("Nema negativnih stanja.");
    return;
  }
  if (!confirm(`Ispraviti ${negativeProducts.length} artikala s negativnim stanjem na 0?`)) return;

  const now = Date.now();
  const batch = state.db.batch();
  negativeProducts.forEach((product) => {
    batch.set(productRef(product.id), {
      stockQuantityUnits: 0,
      stockUpdatedAt: now,
      updatedAt: now,
    }, { merge: true });
  });
  await batch.commit();
}

async function resetAllReceipts() {
  if (!confirm("Ovo briše sve cloud račune za ovaj kafić. Nastaviti?")) return;
  if (!confirm("Još jednom potvrdi: želiš resetirati sve račune?")) return;

  els.resetReceiptsButton.disabled = true;
  try {
    while (true) {
      const snapshot = await receiptsCollection().limit(450).get();
      if (snapshot.empty) break;
      const batch = state.db.batch();
      snapshot.docs.forEach((doc) => batch.delete(doc.ref));
      await batch.commit();
    }
  } finally {
    els.resetReceiptsButton.disabled = false;
  }
}

function filteredReceipts({ from, to, waiter, note }) {
  const start = dateStartMs(from);
  const end = dateEndMs(to);
  return state.receipts.filter((receipt) => {
    if (start && receipt.createdAt < start) return false;
    if (end && receipt.createdAt > end) return false;
    if (waiter && waiter !== "all" && receipt.waiterName !== waiter) return false;
    if (note === "notes" && !receipt.note) return false;
    if (note === "house" && !receipt.note.includes("Na račun kuće")) return false;
    if (note === "music" && !receipt.note.includes("Muzika")) return false;
    return true;
  });
}

function groupReceiptTotals(receipts, labelFn) {
  const grouped = new Map();
  receipts.forEach((receipt) => {
    const label = labelFn(receipt);
    grouped.set(label, (grouped.get(label) || 0) + receipt.totalCents);
  });
  return sortGroup(grouped);
}

function groupItemTotals(receipts, labelFn) {
  const grouped = new Map();
  receipts.forEach((receipt) => {
    receipt.items.forEach((item) => {
      const label = labelFn(item);
      grouped.set(label, (grouped.get(label) || 0) + item.lineTotalCents);
    });
  });
  return sortGroup(grouped);
}

function sortGroup(grouped) {
  return Array.from(grouped.entries())
    .map(([label, value]) => ({ label, value }))
    .sort((a, b) => b.value - a.value || a.label.localeCompare(b.label, "hr"));
}

function categoryForItem(item) {
  const product = state.products.find((row) => row.name === item.name || row.id === item.cloudProductId);
  return product?.categoryName || "Ostalo";
}

function exportReceiptCsv() {
  const receipts = filteredReceipts({
    from: els.receiptFrom.value,
    to: els.receiptTo.value,
    waiter: els.receiptWaiter.value,
    note: els.receiptNoteFilter.value,
  });
  const rows = receipts.map((receipt) => [
    receipt.receiptNumber,
    formatDateTime(receipt.createdAt),
    receipt.waiterName,
    receipt.note,
    formatPlainEuro(receipt.totalCents),
    receipt.items.map((item) => `${item.name} x${item.quantity}`).join("; "),
  ]);
  downloadCsv("siply-racuni.csv", [["broj", "datum", "konobar", "oznaka", "ukupno_eur", "artikli"], ...rows]);
}

function exportSalesCsv() {
  const receipts = filteredReceipts({
    from: els.receiptFrom.value,
    to: els.receiptTo.value,
    waiter: els.receiptWaiter.value,
    note: els.receiptNoteFilter.value,
  });
  const grouped = new Map();
  receipts.forEach((receipt) => {
    receipt.items.forEach((item) => {
      const current = grouped.get(item.name) || { quantity: 0, total: 0 };
      current.quantity += item.quantity;
      current.total += item.lineTotalCents;
      grouped.set(item.name, current);
    });
  });
  const rows = Array.from(grouped.entries())
    .sort((a, b) => b[1].total - a[1].total)
    .map(([name, data]) => [name, data.quantity, formatPlainEuro(data.total)]);
  downloadCsv("siply-prodaja.csv", [["pice", "kolicina", "ukupno_eur"], ...rows]);
}

function exportCatalogCsv() {
  const rows = state.products.map((product) => [
    product.categoryName || "",
    product.name || "",
    formatPlainEuro(product.priceCents || 0),
    Number(product.stockQuantityUnits || 0),
    product.emoji || "",
    product.isActive === false ? "ne" : "da",
  ]);
  downloadCsv("siply-cjenik.csv", [["kategorija", "artikl", "cijena_eur", "stanje", "emoji", "aktivan"], ...rows]);
}

async function importCatalogCsv(file) {
  const text = await file.text();
  const rows = parseCsv(text).filter((row) => row.some((cell) => cell.trim()));
  const body = rows[0]?.[0]?.toLowerCase().includes("kategorija") ? rows.slice(1) : rows;
  const batch = state.db.batch();

  body.forEach((row) => {
    const [categoryName, name, price, stock, emoji, active] = row;
    if (!categoryName || !name) return;
    const existing = state.products.find((product) =>
      String(product.categoryName || "").trim().toLowerCase() === categoryName.trim().toLowerCase()
      && String(product.name || "").trim().toLowerCase() === name.trim().toLowerCase()
    );
    const ref = existing ? productRef(existing.id) : productsCollection().doc();
    batch.set(ref, {
      categoryId: categoryIdFor(categoryName, existing?.categoryId),
      categoryName: categoryName.trim(),
      name: name.trim(),
      priceCents: parseEuroCents(price || "0"),
      stockQuantityUnits: parseStock(stock || "0"),
      stockUpdatedAt: Date.now(),
      emoji: (emoji || "").trim() || "☕",
      accentColor: Number(existing?.accentColor || 0),
      sortOrder: Number(existing?.sortOrder || nextSortOrder(categoryName)),
      isActive: !["ne", "false", "0"].includes(String(active || "da").trim().toLowerCase()),
      createdByUid: existing?.createdByUid || state.user.uid,
      updatedAt: Date.now(),
    }, { merge: true });
  });

  await batch.commit();
  els.importCatalogInput.value = "";
}

function downloadBackup() {
  const backup = {
    exportedAt: new Date().toISOString(),
    cafeId: state.payload.cafeId,
    cafeName: state.cafeName,
    products: state.products,
    receipts: state.receipts,
  };
  downloadText("siply-backup.json", JSON.stringify(backup, null, 2), "application/json");
}

function downloadCsv(filename, rows) {
  const csv = rows.map((row) => row.map(csvCell).join(";")).join("\n");
  downloadText(filename, `\ufeff${csv}`, "text/csv;charset=utf-8");
}

function downloadText(filename, text, type) {
  const blob = new Blob([text], { type });
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = filename;
  document.body.appendChild(link);
  link.click();
  link.remove();
  URL.revokeObjectURL(url);
}

function csvCell(value) {
  return `"${String(value ?? "").replaceAll('"', '""')}"`;
}

function parseCsv(text) {
  const firstLine = text.split(/\r?\n/, 1)[0] || "";
  const delimiter = (firstLine.match(/;/g) || []).length > (firstLine.match(/,/g) || []).length ? ";" : ",";
  const rows = [];
  let row = [];
  let cell = "";
  let quoted = false;

  for (let index = 0; index < text.length; index += 1) {
    const char = text[index];
    const next = text[index + 1];
    if (char === '"' && quoted && next === '"') {
      cell += '"';
      index += 1;
    } else if (char === '"') {
      quoted = !quoted;
    } else if (char === delimiter && !quoted) {
      row.push(cell);
      cell = "";
    } else if ((char === "\n" || char === "\r") && !quoted) {
      if (char === "\r" && next === "\n") index += 1;
      row.push(cell);
      rows.push(row);
      row = [];
      cell = "";
    } else {
      cell += char;
    }
  }
  row.push(cell);
  rows.push(row);
  return rows;
}

function readField(row, field) {
  return row.querySelector(`[data-field="${field}"]`).value;
}

function categoryIdFor(categoryName, fallback) {
  const normalized = String(categoryName || "").trim().toLowerCase();
  const existing = state.products.find((product) => String(product.categoryName || "").trim().toLowerCase() === normalized);
  if (existing?.categoryId) return Number(existing.categoryId);
  if (fallback) return Number(fallback);
  return Math.max(0, ...state.products.map((product) => Number(product.categoryId || 0))) + 1;
}

function nextSortOrder(categoryName) {
  const normalized = String(categoryName || "").trim().toLowerCase();
  return Math.max(
    0,
    ...state.products
      .filter((product) => String(product.categoryName || "").trim().toLowerCase() === normalized)
      .map((product) => Number(product.sortOrder || 0)),
  ) + 1;
}

function parseEuroCents(value) {
  const normalized = String(value || "").replace("€", "").replace(",", ".").trim();
  const parsed = Number(normalized);
  if (!Number.isFinite(parsed) || parsed < 0) throw new Error("Cijena nije valjana.");
  return Math.round(parsed * 100);
}

function parseStock(value) {
  const parsed = Number.parseInt(String(value || "0").trim(), 10);
  if (!Number.isFinite(parsed) || parsed < 0) throw new Error("Stanje nije valjano.");
  return parsed;
}

function rawStock(product) {
  return Number(product.stockQuantityUnits || 0);
}

function safeStock(product) {
  return Math.max(0, rawStock(product));
}

function cartTotalCents() {
  return Array.from(state.cart.values())
    .reduce((total, line) => total + Number(line.product.priceCents || 0) * line.quantity, 0);
}

function sum(values) {
  return values.reduce((total, value) => total + Number(value || 0), 0);
}

function formatEuro(cents) {
  return new Intl.NumberFormat("hr-HR", { style: "currency", currency: "EUR" }).format(Number(cents || 0) / 100);
}

function formatPlainEuro(cents) {
  return (Number(cents || 0) / 100).toFixed(2).replace(".", ",");
}

function formatPriceInput(cents) {
  return (Number(cents || 0) / 100).toFixed(2).replace(".", ",");
}

function formatDateTime(millis) {
  if (!millis) return "";
  return new Intl.DateTimeFormat("hr-HR", {
    dateStyle: "short",
    timeStyle: "short",
  }).format(new Date(millis));
}

function compactDateTime(millis) {
  const date = new Date(millis);
  const pad = (value) => value.toString().padStart(2, "0");
  return `${date.getFullYear()}${pad(date.getMonth() + 1)}${pad(date.getDate())}-${pad(date.getHours())}${pad(date.getMinutes())}${pad(date.getSeconds())}`;
}

function dateKey(date) {
  const pad = (value) => value.toString().padStart(2, "0");
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;
}

function dateStartMs(value) {
  if (!value) return null;
  const [year, month, day] = value.split("-").map(Number);
  return new Date(year, month - 1, day, 0, 0, 0, 0).getTime();
}

function dateEndMs(value) {
  if (!value) return null;
  const [year, month, day] = value.split("-").map(Number);
  return new Date(year, month - 1, day, 23, 59, 59, 999).getTime();
}

function escapeHtml(value) {
  return String(value)
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;");
}

function escapeAttr(value) {
  return escapeHtml(value);
}

function switchTab(tabName) {
  els.tabs.forEach((tab) => tab.classList.toggle("active", tab.dataset.tab === tabName));
  els.tabPanels.forEach((panel) => panel.classList.toggle("hidden", panel.id !== `${tabName}Tab`));
}

els.connectButton.addEventListener("click", () => connect(els.invitePayload.value));
els.clearSessionButton.addEventListener("click", () => {
  localStorage.removeItem(STORAGE_KEY);
  els.invitePayload.value = "";
  setStatus("Spremljeni web admin kod je obrisan.");
});
els.logoutButton.addEventListener("click", async () => {
  localStorage.removeItem(STORAGE_KEY);
  if (state.productUnsubscribe) state.productUnsubscribe();
  if (state.receiptUnsubscribe) state.receiptUnsubscribe();
  if (state.auth) await state.auth.signOut();
  showLogin();
});
els.refreshButton.addEventListener("click", renderAll);
els.tabs.forEach((tab) => tab.addEventListener("click", () => switchTab(tab.dataset.tab)));
els.todayDashboardButton.addEventListener("click", () => {
  const today = dateKey(new Date());
  els.dashboardFrom.value = today;
  els.dashboardTo.value = today;
  renderDashboard();
});
[els.dashboardFrom, els.dashboardTo, els.dashboardWaiter].forEach((input) => input.addEventListener("input", renderDashboard));
[els.receiptFrom, els.receiptTo, els.receiptWaiter, els.receiptNoteFilter].forEach((input) => input.addEventListener("input", renderReceipts));
els.searchInput.addEventListener("input", renderCatalog);
els.posSearchInput.addEventListener("input", renderPos);
els.categoryChips.addEventListener("click", (event) => {
  const button = event.target.closest("[data-category]");
  if (!button) return;
  state.activeCategory = button.dataset.category;
  renderPos();
});
els.productCards.addEventListener("click", (event) => {
  const card = event.target.closest("[data-product-id]");
  if (!card) return;
  addToCart(card.dataset.productId);
});
els.cartLines.addEventListener("click", (event) => {
  const button = event.target.closest("[data-action]");
  if (!button) return;
  const row = button.closest("[data-product-id]");
  updateCart(row.dataset.productId, button.dataset.action);
});
els.clearCartButton.addEventListener("click", () => {
  state.cart.clear();
  renderCart();
});
els.saveReceiptButton.addEventListener("click", () => saveReceipt().catch((error) => {
  console.error(error);
  alert(error.message || "Spremanje računa nije uspjelo.");
}));
els.newProductForm.addEventListener("submit", (event) => createProduct(event).catch((error) => {
  console.error(error);
  alert(error.message || "Dodavanje artikla nije uspjelo.");
}));
els.productsTable.addEventListener("click", (event) => {
  const button = event.target.closest("[data-action]");
  if (!button) return;
  const row = button.closest(".product-row");
  button.disabled = true;
  const task = button.dataset.action === "delete" ? deleteProduct(row) : saveExistingProduct(row);
  task.catch((error) => {
    console.error(error);
    alert(error.message || "Akcija nije uspjela.");
  }).finally(() => {
    button.disabled = false;
  });
});
els.toggleProcurementButton.addEventListener("click", () => {
  els.procurementPanel.classList.toggle("hidden");
  renderProcurement();
});
els.procurementTable.addEventListener("input", (event) => {
  if (event.target.dataset.field !== "procurementQty") return;
  const row = event.target.closest(".procurement-row");
  const stock = Number(row.dataset.stock || 0);
  const qty = Number.parseInt(event.target.value || "0", 10) || 0;
  row.querySelector('[data-field="newStock"]').textContent = (stock + qty).toString();
});
els.saveProcurementButton.addEventListener("click", () => saveProcurement().catch((error) => {
  console.error(error);
  alert(error.message || "Unos robe nije uspio.");
}));
els.fixNegativeStockButton.addEventListener("click", () => fixNegativeStocks().catch((error) => {
  console.error(error);
  alert(error.message || "Ispravak negativnog stanja nije uspio.");
}));
els.exportReceiptsButton.addEventListener("click", exportReceiptCsv);
els.exportSalesButton.addEventListener("click", exportSalesCsv);
els.resetReceiptsButton.addEventListener("click", () => resetAllReceipts().catch((error) => {
  console.error(error);
  alert(error.message || "Reset računa nije uspio. Provjeri Firestore rules.");
}));
els.exportCatalogButton.addEventListener("click", exportCatalogCsv);
els.importCatalogInput.addEventListener("change", (event) => {
  const file = event.target.files?.[0];
  if (!file) return;
  importCatalogCsv(file).catch((error) => {
    console.error(error);
    alert(error.message || "Import cjenika nije uspio.");
  });
});
els.downloadBackupButton.addEventListener("click", downloadBackup);
els.forgetWebCodeButton.addEventListener("click", () => {
  localStorage.removeItem(STORAGE_KEY);
  alert("Web admin kod je obrisan iz browsera.");
});

const savedPayload = localStorage.getItem(STORAGE_KEY);
if (savedPayload) {
  els.invitePayload.value = savedPayload;
  connect(savedPayload);
}
