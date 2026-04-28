const STORAGE_KEY = "siply.webAdmin.payload";

const state = {
  app: null,
  db: null,
  auth: null,
  user: null,
  payload: null,
  cafeName: "",
  products: [],
  unsubscribe: null,
};

const els = {
  loginPanel: document.querySelector("#loginPanel"),
  adminPanel: document.querySelector("#adminPanel"),
  invitePayload: document.querySelector("#invitePayload"),
  connectButton: document.querySelector("#connectButton"),
  clearSessionButton: document.querySelector("#clearSessionButton"),
  loginStatus: document.querySelector("#loginStatus"),
  cafeName: document.querySelector("#cafeName"),
  productsCount: document.querySelector("#productsCount"),
  activeCount: document.querySelector("#activeCount"),
  stockValue: document.querySelector("#stockValue"),
  newProductForm: document.querySelector("#newProductForm"),
  newCategory: document.querySelector("#newCategory"),
  newName: document.querySelector("#newName"),
  newPrice: document.querySelector("#newPrice"),
  newStock: document.querySelector("#newStock"),
  newEmoji: document.querySelector("#newEmoji"),
  productsTable: document.querySelector("#productsTable"),
  searchInput: document.querySelector("#searchInput"),
  refreshButton: document.querySelector("#refreshButton"),
  logoutButton: document.querySelector("#logoutButton"),
};

function setStatus(message, isError = false) {
  els.loginStatus.textContent = message;
  els.loginStatus.style.color = isError ? "#a33a32" : "#647067";
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

    const cafeRef = state.db.collection("cafes").doc(payload.cafeId);
    const inviteRef = cafeRef.collection("invites").doc(payload.inviteCode);
    const inviteDoc = await inviteRef.get();
    if (!inviteDoc.exists || inviteDoc.data().active !== true || inviteDoc.data().role !== "admin") {
      throw new Error("Web admin kod nije valjan ili je istekao.");
    }

    const cafeDoc = await cafeRef.get();
    if (!cafeDoc.exists) {
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
    state.cafeName = cafeDoc.data().name || "Siply kafić";
    els.cafeName.textContent = state.cafeName;
    showAdmin();
    subscribeProducts();
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

function subscribeProducts() {
  if (state.unsubscribe) {
    state.unsubscribe();
  }

  state.unsubscribe = state.db
    .collection("cafes")
    .doc(state.payload.cafeId)
    .collection("catalogProducts")
    .onSnapshot((snapshot) => {
      state.products = snapshot.docs
        .map((doc) => ({ id: doc.id, ...doc.data() }))
        .sort((a, b) => {
          const categorySort = String(a.categoryName || "").localeCompare(String(b.categoryName || ""), "hr");
          if (categorySort !== 0) return categorySort;
          return String(a.name || "").localeCompare(String(b.name || ""), "hr");
        });
      render();
    }, (error) => {
      console.error(error);
      setStatus("Ne mogu učitati katalog. Provjeri Firestore rules.", true);
    });
}

function render() {
  const query = els.searchInput.value.trim().toLowerCase();
  const visibleProducts = state.products.filter((product) => {
    if (!query) return true;
    return `${product.categoryName || ""} ${product.name || ""}`.toLowerCase().includes(query);
  });

  els.productsCount.textContent = state.products.length.toString();
  els.activeCount.textContent = state.products.filter((product) => product.isActive !== false).length.toString();
  els.stockValue.textContent = formatEuro(
    state.products.reduce((sum, product) => {
      return sum + Number(product.priceCents || 0) * Number(product.stockQuantityUnits || 0);
    }, 0),
  );

  if (visibleProducts.length === 0) {
    els.productsTable.innerHTML = `<div class="empty">Nema artikala za prikaz.</div>`;
    return;
  }

  const header = `
    <div class="table-header">
      <span>Kategorija</span>
      <span>Artikl</span>
      <span>Cijena</span>
      <span>Stanje</span>
      <span>Emoji</span>
      <span>Aktivan</span>
      <span></span>
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
      <label class="active-cell">
        <input data-field="active" type="checkbox" ${product.isActive === false ? "" : "checked"}>
      </label>
      <button class="primary" data-action="save">Spremi</button>
    </div>`;
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
    emoji: readField(row, "emoji").trim() || "🥤",
    accentColor: Number(existing.accentColor || 0),
    sortOrder: Number(existing.sortOrder || 1),
    isActive: row.querySelector('[data-field="active"]').checked,
    createdByUid: state.user.uid,
    updatedAt: Date.now(),
  }, { merge: true });
}

async function createProduct(event) {
  event.preventDefault();
  const categoryName = els.newCategory.value.trim();
  const name = els.newName.value.trim();
  if (!categoryName || !name) {
    alert("Kategorija i artikl su obavezni.");
    return;
  }

  const sortOrder = nextSortOrder(categoryName);
  await state.db
    .collection("cafes")
    .doc(state.payload.cafeId)
    .collection("catalogProducts")
    .add({
      categoryId: categoryIdFor(categoryName),
      categoryName,
      name,
      priceCents: parseEuroCents(els.newPrice.value),
      stockQuantityUnits: parseStock(els.newStock.value),
      stockUpdatedAt: Date.now(),
      emoji: els.newEmoji.value.trim() || "🥤",
      accentColor: 0,
      sortOrder,
      isActive: true,
      createdByUid: state.user.uid,
      updatedAt: Date.now(),
    });

  event.target.reset();
}

function productRef(id) {
  return state.db
    .collection("cafes")
    .doc(state.payload.cafeId)
    .collection("catalogProducts")
    .doc(id);
}

function readField(row, field) {
  return row.querySelector(`[data-field="${field}"]`).value;
}

function categoryIdFor(categoryName, fallback) {
  const normalized = categoryName.trim().toLowerCase();
  const existing = state.products.find((product) => String(product.categoryName || "").trim().toLowerCase() === normalized);
  if (existing?.categoryId) return Number(existing.categoryId);
  if (fallback) return Number(fallback);
  return Math.max(0, ...state.products.map((product) => Number(product.categoryId || 0))) + 1;
}

function nextSortOrder(categoryName) {
  const normalized = categoryName.trim().toLowerCase();
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
  if (!Number.isFinite(parsed) || parsed < 0) {
    throw new Error("Cijena nije valjana.");
  }
  return Math.round(parsed * 100);
}

function parseStock(value) {
  const parsed = Number.parseInt(String(value || "0").trim(), 10);
  if (!Number.isFinite(parsed) || parsed < 0) {
    throw new Error("Stanje nije valjano.");
  }
  return parsed;
}

function formatEuro(cents) {
  return new Intl.NumberFormat("hr-HR", {
    style: "currency",
    currency: "EUR",
  }).format(cents / 100);
}

function formatPriceInput(cents) {
  return (Number(cents || 0) / 100).toFixed(2).replace(".", ",");
}

function escapeAttr(value) {
  return String(value)
    .replaceAll("&", "&amp;")
    .replaceAll('"', "&quot;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;");
}

els.connectButton.addEventListener("click", () => connect(els.invitePayload.value));
els.clearSessionButton.addEventListener("click", () => {
  localStorage.removeItem(STORAGE_KEY);
  els.invitePayload.value = "";
  setStatus("Spremljeni web admin kod je obrisan.");
});
els.logoutButton.addEventListener("click", async () => {
  localStorage.removeItem(STORAGE_KEY);
  if (state.unsubscribe) state.unsubscribe();
  if (state.auth) await state.auth.signOut();
  showLogin();
});
els.refreshButton.addEventListener("click", render);
els.searchInput.addEventListener("input", render);
els.newProductForm.addEventListener("submit", (event) => {
  createProduct(event).catch((error) => {
    console.error(error);
    alert(error.message || "Dodavanje artikla nije uspjelo.");
  });
});
els.productsTable.addEventListener("click", (event) => {
  if (event.target.dataset.action !== "save") return;
  const row = event.target.closest(".product-row");
  event.target.disabled = true;
  saveExistingProduct(row)
    .catch((error) => {
      console.error(error);
      alert(error.message || "Spremanje artikla nije uspjelo.");
    })
    .finally(() => {
      event.target.disabled = false;
    });
});

const savedPayload = localStorage.getItem(STORAGE_KEY);
if (savedPayload) {
  els.invitePayload.value = savedPayload;
  connect(savedPayload);
}
