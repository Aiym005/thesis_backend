const state = {
    dashboard: null,
    workflow: null,
    database: null,
    authUser: null,
    activeRole: null,
    activeUserId: null,
    page: "dashboard",
    topicMode: "list",
    researchMode: "list",
    selectedTopicId: null,
    lastTopicId: null,
    lastPlanId: null
};

const PLAN_WEEKS = 15;

const roleLabels = {
    STUDENT: "Оюутан",
    TEACHER: "Багш",
    DEPARTMENT: "Тэнхим"
};

const authRoot = document.getElementById("authRoot");
const appShell = document.getElementById("appShell");
const logoutButton = document.getElementById("logoutButton");
const pageRoot = document.getElementById("pageRoot");
const message = document.getElementById("message");
const searchInput = document.getElementById("searchInput");

document.querySelectorAll(".menu button").forEach(button => {
    button.addEventListener("click", () => setActivePage(button.dataset.page));
});

searchInput.addEventListener("input", () => render());
logoutButton.addEventListener("click", logout);

boot();

function boot() {
    const savedUser = sessionStorage.getItem("thesis.authUser");
    if (!savedUser) {
        renderAuthScreen();
        return;
    }

    try {
        const authUser = JSON.parse(savedUser);
        applyAuthenticatedUser(authUser);
        loadDashboard();
    } catch (error) {
        sessionStorage.removeItem("thesis.authUser");
        renderAuthScreen();
    }
}

async function loadDashboard() {
    const [dashboardResponse, workflowResponse, databaseResponse, notificationsResponse] = await Promise.all([
        fetch("/api/dashboard"),
        fetch("/api/verification/state"),
        fetch("/api/system/database").catch(() => null),
        fetch("/api/notifications").catch(() => null)
    ]);

    const dashboard = await dashboardResponse.json();
    const notifications = notificationsResponse && notificationsResponse.ok
        ? await notificationsResponse.json()
        : dashboard.notifications ?? [];

    state.dashboard = {
        ...dashboard,
        notifications
    };
    state.workflow = await workflowResponse.json();
    state.database = databaseResponse ? await databaseResponse.json() : null;
    state.activeUserId = state.authUser?.id ?? state.activeUserId;
    render();
}

function setActivePage(page, doRender = true) {
    state.page = page;
    document.querySelectorAll(".menu button").forEach(item => {
        item.classList.toggle("active", item.dataset.page === page);
    });
    if (doRender) render();
}

function render() {
    if (!state.dashboard || !state.workflow) return;
    authRoot.classList.add("hidden");
    appShell.classList.remove("hidden");
    const user = getActiveUser();
    document.getElementById("profileName").textContent = user ? `${user.firstName} ${user.lastName}` : "-";
    document.getElementById("profileRole").textContent = roleLabels[state.activeRole];
    document.getElementById("profileLogin").textContent = state.authUser?.username ?? "-";
    pageRoot.innerHTML = renderPage(user);
}

function renderPage(user) {
    if (state.page === "dashboard") return renderDashboard(user);
    if (state.page === "topics") return renderTopicsPage(user);
    if (state.page === "research") return renderResearchPage(user);
    return renderSettingsPage(user);
}

function renderDashboard(user) {
    if (state.activeRole === "STUDENT") {
        const approved = getApprovedTopic(user);
        const pendingRequests = state.dashboard.topics.filter(topic =>
            topic.ownerStudentId === user.id && !["APPROVED", "REJECTED", "SUPERSEDED"].includes(topic.status)
        );
        const rejectedRequests = state.dashboard.topics.filter(topic =>
            topic.ownerStudentId === user.id && topic.status === "REJECTED"
        );

        return `
            <h1>Миний судалгааны ажил</h1>
            <p class="page-sub">Сэдэв сонголт, баталгаажуулалт, 15 долоо хоногийн төлөвлөгөө энэ самбар дээр харагдана.</p>
            ${renderMetricCards([
                ["Батлагдсан сэдэв", approved ? "1" : "0"],
                ["Хүлээгдэж буй хүсэлт", String(pendingRequests.length)],
                ["Буцаагдсан хүсэлт", String(rejectedRequests.length)]
            ])}
            <div class="card">
                <h3>Батлагдсан сэдэв</h3>
                <table class="table">
                    <thead><tr><th>Сэдэв</th><th>Төлөв</th><th>Эхэлсэн</th><th>Дуусах</th><th>Удирдагч багш</th><th>Үйлдэл</th></tr></thead>
                    <tbody>
                    ${approved ? `<tr><td>${safeTopicTitle(approved)}</td><td class="status">${renderStatus(approved.status)}</td><td>${formatDateShort(approved.createdAt)}</td><td>${planEndDate()}</td><td>${safePersonName(approved.advisorTeacherName)}</td><td><button class="action-btn" onclick="openResearchDetail()">Харах</button></td></tr>` : `<tr><td colspan="6">Батлагдсан сэдэв алга.</td></tr>`}
                    </tbody>
                </table>
            </div>
            <div class="card">
                <h3>Миний сэдвийн хүсэлтүүд</h3>
                <table class="table">
                    <thead><tr><th>Сэдэв</th><th>Төлөв</th><th>Хүсэлт гаргасан</th><th>Удирдагч багш</th><th>Үйлдэл</th></tr></thead>
                    <tbody>
                    ${pendingRequests.length ? pendingRequests.map(topic => `<tr><td>${safeTopicTitle(topic)}</td><td>${renderStatus(topic.status)}</td><td>${formatDateShort(topic.updatedAt)}</td><td>${safePersonName(topic.advisorTeacherName)}</td><td><button class="action-btn" onclick="openTopicRequest(${topic.id})">Харах</button></td></tr>`).join("") : `<tr><td colspan="5">Хүлээгдэж буй хүсэлт алга.</td></tr>`}
                    </tbody>
                </table>
            </div>
            <div class="card">
                <h3>Буцаагдсан хүсэлтүүд</h3>
                <table class="table">
                    <thead><tr><th>Сэдэв</th><th>Төлөв</th><th>Шийдвэрлэсэн</th></tr></thead>
                    <tbody>
                    ${rejectedRequests.length ? rejectedRequests.map(topic => `<tr><td>${safeTopicTitle(topic)}</td><td>${renderStatus(topic.status)}</td><td>${formatDateShort(topic.updatedAt)}</td></tr>`).join("") : `<tr><td colspan="3">Буцаагдсан хүсэлт алга.</td></tr>`}
                    </tbody>
                </table>
            </div>
        `;
    }

    if (state.activeRole === "TEACHER") {
        const pendingTopics = state.workflow.topics.pendingTeacherApprovalTopics;
        const pendingPlans = state.workflow.plans.pendingTeacherApprovalPlans;
        const myCatalogPending = state.dashboard.topics.filter(topic =>
            topic.proposerId === user.id && topic.ownerStudentId == null && topic.status === "PENDING_DEPARTMENT_APPROVAL"
        );
        const myCatalogOpen = state.workflow.topics.availableTopics.filter(topic => topic.proposerId === user.id);
        const supervisedStudents = getTeacherSupervisedStudents(user.id);
        const teacherLoad = pendingTopics.length + pendingPlans.length + supervisedStudents.length;

        return `
            <h1>Багшийн самбар</h1>
            <p class="page-sub">Оюутны хүсэлтийг батлах, өөрийн сэдэв дэвшүүлэх, төлөвлөгөө хянах хэсэг.</p>
            ${renderMetricCards([
                ["Хүлээгдэж буй сэдэв", String(pendingTopics.length)],
                ["Тэнхимд хүлээгдэж буй миний сэдэв", String(myCatalogPending.length)],
                ["Нээлттэй болсон миний сэдэв", String(myCatalogOpen.length)],
                ["Хүлээгдэж буй төлөвлөгөө", String(pendingPlans.length)],
                ["Удирдаж буй оюутан", String(supervisedStudents.length)],
                ["Нийт ачаалал", String(teacherLoad)]
            ])}
            <div class="split-panels">
                <div class="card">
                    <h3>Оюутнаас ирсэн сэдвүүд</h3>
                    <table class="table">
                        <thead><tr><th>Сэдэв</th><th>Оюутан</th><th>Үйлдэл</th></tr></thead>
                        <tbody>
                        ${pendingTopics.length ? pendingTopics.map(topic => `<tr><td>${safeTopicTitle(topic)}</td><td>${safePersonName(topic.ownerStudentName)}</td><td><button class="action-btn" onclick="setActivePage('topics'); openTeacherTopic(${topic.id})">Шийдэх</button></td></tr>`).join("") : `<tr><td colspan="3">Хүлээгдэж буй сэдэв алга.</td></tr>`}
                        </tbody>
                    </table>
                </div>
                <div class="card">
                    <h3>Оюутнаас ирсэн төлөвлөгөө</h3>
                    <table class="table">
                        <thead><tr><th>Сэдэв</th><th>Оюутан</th><th>Үйлдэл</th></tr></thead>
                        <tbody>
                        ${pendingPlans.length ? pendingPlans.map(plan => `<tr><td>${plan.topicTitle}</td><td>${plan.studentName}</td><td><button class="action-btn" onclick="setActivePage('research'); openTeacherPlan(${plan.id})">Шийдэх</button></td></tr>`).join("") : `<tr><td colspan="3">Хүлээгдэж буй төлөвлөгөө алга.</td></tr>`}
                        </tbody>
                    </table>
                </div>
            </div>
        `;
    }

    const pendingTopics = state.workflow.topics.pendingDepartmentApprovalTopics;
    const teacherCatalogTopics = pendingTopics.filter(topic => topic.ownerStudentId == null);
    const studentOwnedTopics = pendingTopics.filter(topic => topic.ownerStudentId != null);
    const pendingPlans = state.workflow.plans.pendingDepartmentApprovalPlans;

    return `
        <h1>Тэнхимийн самбар</h1>
        <p class="page-sub">Багшийн дэвшүүлсэн сэдвийг catalog руу батлах, оюутны сонгосон сэдвийг эцэслэх, төлөвлөгөө батлах.</p>
        ${renderMetricCards([
            ["Catalog руу оруулах сэдэв", String(teacherCatalogTopics.length)],
            ["Эцэслэх оюутны сэдэв", String(studentOwnedTopics.length)],
            ["Хүлээгдэж буй төлөвлөгөө", String(pendingPlans.length)]
        ])}
        <div class="split-panels">
            <div class="card">
                <h3>Багшийн дэвшүүлсэн сэдвүүд</h3>
                <table class="table">
                    <thead><tr><th>Сэдэв</th><th>Багш</th><th>Үйлдэл</th></tr></thead>
                    <tbody>
                    ${teacherCatalogTopics.length ? teacherCatalogTopics.map(topic => `<tr><td>${safeTopicTitle(topic)}</td><td>${safePersonName(topic.proposerName)}</td><td><button class="action-btn" onclick="setActivePage('topics'); openDepartmentTopic(${topic.id})">Шийдэх</button></td></tr>`).join("") : `<tr><td colspan="3">Хүлээгдэж буй багшийн сэдэв алга.</td></tr>`}
                    </tbody>
                </table>
            </div>
            <div class="card">
                <h3>Оюутны сонгосон сэдвүүд</h3>
                <table class="table">
                    <thead><tr><th>Сэдэв</th><th>Оюутан</th><th>Үйлдэл</th></tr></thead>
                    <tbody>
                    ${studentOwnedTopics.length ? studentOwnedTopics.map(topic => `<tr><td>${safeTopicTitle(topic)}</td><td>${safePersonName(topic.ownerStudentName)}</td><td><button class="action-btn" onclick="setActivePage('topics'); openDepartmentTopic(${topic.id})">Шийдэх</button></td></tr>`).join("") : `<tr><td colspan="3">Хүлээгдэж буй оюутны сэдэв алга.</td></tr>`}
                    </tbody>
                </table>
            </div>
        </div>
        <div class="card">
            <h3>Төлөвлөгөөний хүсэлтүүд</h3>
            <table class="table">
                <thead><tr><th>Сэдэв</th><th>Оюутан</th><th>Үйлдэл</th></tr></thead>
                <tbody>
                ${pendingPlans.length ? pendingPlans.map(plan => `<tr><td>${plan.topicTitle}</td><td>${plan.studentName}</td><td><button class="action-btn" onclick="setActivePage('research'); openDepartmentPlan(${plan.id})">Шийдэх</button></td></tr>`).join("") : `<tr><td colspan="3">Хүлээгдэж буй төлөвлөгөө алга.</td></tr>`}
                </tbody>
            </table>
        </div>
    `;
}

function renderTopicsPage(user) {
    if (state.activeRole === "STUDENT") {
        if (state.topicMode === "detail") return renderStudentTopicDetail();
        if (state.topicMode === "request") return renderStudentTopicRequest(user);
        return renderStudentTopicList(user);
    }
    if (state.activeRole === "TEACHER") return renderTeacherTopics(user);
    return renderDepartmentTopics();
}

function renderStudentTopicList(user) {
    const topics = filterSearch(state.workflow.topics.availableTopics);
    const myPending = state.dashboard.topics.filter(topic =>
        topic.ownerStudentId === user.id && !["APPROVED", "SUPERSEDED"].includes(topic.status)
    );
    return `
        <h1>Сэдэв сонголт</h1>
        <p class="page-sub">Батлагдсан сэдвүүд энд гарч ирнэ. Өөрийн дэвшүүлсэн сэдвүүд доорх хүсэлтийн хэсэгт орно.</p>
        ${renderMetricCards([
            ["Сонгож болох сэдэв", String(state.workflow.topics.availableTopics.length)],
            ["Миний pending хүсэлт", String(myPending.length)]
        ])}
        <div class="card">
            <h3>Нээлттэй сэдвүүд</h3>
            <table class="table">
                <thead><tr><th>Сэдэв</th><th>Хөтөлбөр</th><th>Дэвшүүлэгч</th><th>Үйлдэл</th></tr></thead>
                <tbody>
                ${topics.length ? topics.map(topic => `<tr><td>${safeTopicTitle(topic)}</td><td>${topic.program}</td><td>${safePersonName(topic.proposerName)}</td><td><button class="action-btn" onclick="openTopicDetail(${topic.id})">Сонгох</button></td></tr>`).join("") : `<tr><td colspan="4">Нээлттэй сэдэв алга.</td></tr>`}
                </tbody>
            </table>
        </div>
        <div class="card">
            <h3>Миний дэвшүүлсэн болон сонгосон pending сэдвүүд</h3>
            <table class="table">
                <thead><tr><th>Сэдэв</th><th>Төлөв</th><th>Сүүлд шинэчлэгдсэн</th><th>Үйлдэл</th></tr></thead>
                <tbody>
                ${myPending.length ? myPending.map(topic => `<tr${highlightRow(topic.id)}><td>${renderNewBadge(topic.id)}${safeTopicTitle(topic)}</td><td>${renderStatus(topic.status)}</td><td>${formatDateShort(topic.updatedAt)}</td><td><button class="soft-btn" onclick="openTopicRequest(${topic.id})">Дэлгэрэнгүй</button></td></tr>`).join("") : `<tr><td colspan="4">Pending сэдэв алга.</td></tr>`}
                </tbody>
            </table>
        </div>
        <div class="card">
            <h3>Шинэ сэдэв дэвшүүлэх</h3>
            <div class="field-list">
                <div class="field"><label>Сэдвийн нэр</label><input id="customTopicTitle"></div>
                <div class="field"><label>Хөтөлбөр</label><input id="customTopicProgram" value="${escapeHtml(user.program ?? "B.SE")}"></div>
                <div class="essay"><label>Тайлбар</label><textarea id="customTopicDescription"></textarea></div>
                <div><button class="action-btn" onclick="submitCustomTopic()">Сэдэв дэвшүүлэх</button></div>
            </div>
        </div>
    `;
}

function renderStudentTopicDetail() {
    const topic = getSelectedTopic();
    if (!topic) return renderStudentTopicList(getActiveUser());
    return `
        <h1>Сэдэвтэй танилцах</h1>
        <p class="page-sub">Энэ сэдвийг сонговол эхлээд багш, дараа нь тэнхим батална.</p>
        <div class="card">
            <div class="topic-title">${safeTopicTitle(topic)}</div>
            <div class="detail-grid">
                <div><div class="mini">Хөтөлбөр</div><div>${topic.program}</div></div>
                    <div><div class="mini">Дэвшүүлэгч</div><div>${safePersonName(topic.proposerName)}</div></div>
                <div><div class="mini">Огноо</div><div>${formatDateShort(topic.createdAt)}</div></div>
            </div>
            <div class="mini">Тайлбар</div>
            <p>${safeText(topic.description, "Тайлбар оруулаагүй.")}</p>
            <div class="footer-actions">
                <button class="outline-btn" onclick="backToTopicList()">Буцах</button>
                <button class="action-btn" onclick="openTopicRequest(${topic.id})">Энэ сэдвийг сонгох</button>
            </div>
        </div>
    `;
}

function renderStudentTopicRequest(user) {
    const topic = getSelectedTopic();
    if (!topic) return renderStudentTopicList(user);
    const selectable = topic.status === "AVAILABLE";
    const editable = topic.proposerId === user.id && topic.proposerRole === "STUDENT" && !["APPROVED", "SUPERSEDED"].includes(topic.status);
    return `
        <h1>Сэдвийн хүсэлт</h1>
        <p class="page-sub">Шинэ хүсэлт үүсгэх эсвэл өмнөх хүсэлтийн төлөвийг харах хэсэг.</p>
        <div class="card">
            <div class="form-grid">
                <div class="field-list">
                    ${renderStudentIdentityFields(user)}
                    <div class="field"><label>Сэдэв</label><input id="student-topic-title" value="${escapeHtml(safeTopicTitle(topic))}" ${editable ? "" : "disabled"}></div>
                    <div class="field"><label>Хөтөлбөр</label><input id="student-topic-program" value="${escapeHtml(topic.program ?? user.program ?? "B.SE")}" ${editable ? "" : "disabled"}></div>
                    <div class="field"><label>Төлөв</label><input value="${renderStatus(topic.status)}" disabled></div>
                </div>
                <div class="essay-list">
                    <div class="essay"><label>Тайлбар</label><textarea id="student-topic-description" ${editable ? "" : "disabled"}>${escapeHtml(safeText(topic.description, "Тайлбар оруулаагүй."))}</textarea></div>
                    <div class="essay"><label>Сонирхол / үндэслэл</label><textarea id="projectsInput">Event-driven, layered architecture дээр ажиллах сонирхолтой.</textarea></div>
                    <div class="essay"><label>Ур чадвар</label><textarea id="skillsInput">Java, Spring Boot, JavaScript, PostgreSQL</textarea></div>
                </div>
            </div>
            <div class="footer-actions">
                <button class="outline-btn" onclick="backToTopicList()">Буцах</button>
                ${selectable ? `<button class="action-btn" onclick="claimTopic(${topic.id})">Сонгох хүсэлт илгээх</button>` : ""}
                ${editable ? `<button class="soft-btn" onclick="updateStudentTopic(${topic.id})">Засах</button><button class="outline-btn" onclick="deleteTopic(${topic.id}, 'STUDENT')">Устгах</button>` : `<button class="soft-btn" onclick="backToTopicList()">Хүсэлтийн жагсаалт руу буцах</button>`}
            </div>
        </div>
    `;
}

function renderTeacherTopics(user) {
    const pendingTopics = filterSearch(state.workflow.topics.pendingTeacherApprovalTopics);
    const myPendingCatalog = state.dashboard.topics.filter(topic =>
        topic.proposerId === user.id && topic.ownerStudentId == null && topic.status === "PENDING_DEPARTMENT_APPROVAL"
    );
    const myOpenCatalog = state.workflow.topics.availableTopics.filter(topic => topic.proposerId === user.id);
    const editableCatalog = state.dashboard.topics.filter(topic =>
        topic.proposerId === user.id && topic.proposerRole === "TEACHER" && topic.ownerStudentId == null && !["DELETED", "SUPERSEDED"].includes(topic.status)
    );
    const supervisedStudents = getTeacherSupervisedStudents(user.id);

    return `
        <h1>Сэдэв баталгаажуулалт</h1>
        <p class="page-sub">Шинэ сэдэв дэвшүүлэхэд эхлээд тэнхимийн жагсаалт руу орно. Тэнхим баталсны дараа оюутнуудад харагдана.</p>
        ${renderMetricCards([
            ["Оюутнаас ирсэн сэдэв", String(pendingTopics.length)],
            ["Тэнхим хүлээж буй миний сэдэв", String(myPendingCatalog.length)],
            ["Нээлттэй болсон миний сэдэв", String(myOpenCatalog.length)],
            ["Удирдаж буй оюутан", String(supervisedStudents.length)],
            ["Ачаалал", String(supervisedStudents.length + pendingTopics.length)]
        ])}
        <div class="card">
            <h3>Шинэ сэдэв дэвшүүлэх</h3>
            <div class="field-list">
                <div class="field"><label>Сэдвийн нэр</label><input id="teacherTopicTitle"></div>
                <div class="field"><label>Хөтөлбөр</label><input id="teacherTopicProgram" value="B.SE"></div>
                <div class="essay"><label>Тайлбар</label><textarea id="teacherTopicDescription"></textarea></div>
                <div><button class="action-btn" onclick="publishTeacherTopic()">Сэдэв дэвшүүлэх</button></div>
            </div>
        </div>
        <div class="card">
            <h3>Оюутнаас ирсэн approval хүсэлтүүд</h3>
            <table class="table">
                <thead><tr><th>Сэдэв</th><th>Оюутан</th><th>Тайлбар</th><th>Үйлдэл</th></tr></thead>
                <tbody>
                ${pendingTopics.length ? pendingTopics.map(topic => `<tr${highlightRow(topic.id)}><td>${renderNewBadge(topic.id)}${safeTopicTitle(topic)}</td><td>${safePersonName(topic.ownerStudentName)}</td><td><textarea id="teacher-topic-note-${topic.id}" style="min-height:72px;"></textarea></td><td><button class="action-btn" onclick="teacherTopicDecision(${topic.id}, true)">Батлах</button><button class="outline-btn" style="margin-top:8px;" onclick="teacherTopicDecision(${topic.id}, false)">Буцаах</button></td></tr>`).join("") : `<tr><td colspan="4">Approval хүсэлт алга.</td></tr>`}
                </tbody>
            </table>
        </div>
        <div class="card">
            <h3>Тэнхимийн баталгаажуулалт хүлээж буй миний сэдвүүд</h3>
            <table class="table">
                <thead><tr><th>Сэдэв</th><th>Төлөв</th><th>Огноо</th></tr></thead>
                <tbody>
                ${myPendingCatalog.length ? myPendingCatalog.map(topic => `<tr${highlightRow(topic.id)}><td>${renderNewBadge(topic.id)}${safeTopicTitle(topic)}</td><td>${renderStatus(topic.status)}</td><td>${formatDateShort(topic.createdAt)}</td></tr>`).join("") : `<tr><td colspan="3">Хүлээгдэж буй сэдэв алга.</td></tr>`}
                </tbody>
            </table>
        </div>
        <div class="card">
            <h3>Оюутанд харагдаж байгаа миний нээлттэй сэдвүүд</h3>
            <table class="table">
                <thead><tr><th>Сэдэв</th><th>Төлөв</th><th>Огноо</th></tr></thead>
                <tbody>
                ${myOpenCatalog.length ? myOpenCatalog.map(topic => `<tr${highlightRow(topic.id)}><td>${renderNewBadge(topic.id)}${safeTopicTitle(topic)}</td><td>${renderStatus(topic.status)}</td><td>${formatDateShort(topic.updatedAt)}</td></tr>`).join("") : `<tr><td colspan="3">Нээлттэй сэдэв алга.</td></tr>`}
                </tbody>
            </table>
        </div>
        <div class="card">
            <h3>Өөрийн сэдвүүдээ засах, устгах</h3>
            <table class="table">
                <thead><tr><th>Сэдэв</th><th>Хөтөлбөр</th><th>Тайлбар</th><th>Үйлдэл</th></tr></thead>
                <tbody>
                ${editableCatalog.length ? editableCatalog.map(topic => `<tr><td><input id="teacher-edit-title-${topic.id}" value="${escapeHtml(safeTopicTitle(topic))}"></td><td><input id="teacher-edit-program-${topic.id}" value="${escapeHtml(topic.program)}"></td><td><textarea id="teacher-edit-description-${topic.id}" style="min-height:72px;">${escapeHtml(safeText(topic.description, ""))}</textarea></td><td><button class="action-btn" onclick="updateTeacherTopic(${topic.id})">Засах</button><button class="outline-btn" style="margin-top:8px;" onclick="deleteTopic(${topic.id}, 'TEACHER')">Устгах</button></td></tr>`).join("") : `<tr><td colspan="4">Засах боломжтой сэдэв алга.</td></tr>`}
                </tbody>
            </table>
        </div>
        <div class="card">
            <h3>Удирдаж буй оюутнууд</h3>
            <table class="table">
                <thead><tr><th>Оюутан</th><th>Сэдэв</th><th>Төлөвлөгөө</th></tr></thead>
                <tbody>
                ${supervisedStudents.length ? supervisedStudents.map(item => `<tr><td>${safePersonName(item.studentName)}</td><td>${safeTopicTitle(item)}</td><td>${renderStatus(item.planStatus ?? "DRAFT")}</td></tr>`).join("") : `<tr><td colspan="3">Удирдаж буй оюутан алга.</td></tr>`}
                </tbody>
            </table>
        </div>
    `;
}

function renderDepartmentTopics() {
    const topics = filterSearch(state.workflow.topics.pendingDepartmentApprovalTopics);
    const teacherCatalogTopics = topics.filter(topic => topic.ownerStudentId == null);
    const studentOwnedTopics = topics.filter(topic => topic.ownerStudentId != null);
    const approvedCatalogTopics = state.dashboard.topics.filter(topic => topic.ownerStudentId == null && topic.status === "AVAILABLE");
    const teacherOptions = state.dashboard.users
        .filter(user => user.role === "TEACHER")
        .map(user => `<option value="${user.id}">${user.firstName} ${user.lastName}</option>`)
        .join("");

    return `
        <h1>Тэнхимийн сэдэв баталгаажуулалт</h1>
        <p class="page-sub">Багшийн дэвшүүлсэн сэдвийг open catalog руу оруулах, оюутны сонгосон сэдэвт advisor томилох хэсэг.</p>
        <div class="card">
            <h3>Батлагдсан сэдэв шууд үүсгэх</h3>
            <div class="field-list">
                <div class="field"><label>Сэдвийн нэр</label><input id="departmentTopicTitle"></div>
                <div class="field"><label>Хөтөлбөр</label><input id="departmentTopicProgram" value="B.SE"></div>
                <div class="essay"><label>Тайлбар</label><textarea id="departmentTopicDescription"></textarea></div>
                <div><button class="action-btn" onclick="publishDepartmentTopic()">Батлагдсан сэдэв үүсгэх</button></div>
            </div>
        </div>
        <div class="card">
            <h3>Catalog руу оруулах багшийн сэдвүүд</h3>
            <table class="table">
                <thead><tr><th>Сэдэв</th><th>Багш</th><th>Тайлбар</th><th>Үйлдэл</th></tr></thead>
                <tbody>
                ${teacherCatalogTopics.length ? teacherCatalogTopics.map(topic => `<tr${highlightRow(topic.id)}><td>${renderNewBadge(topic.id)}${safeTopicTitle(topic)}</td><td>${safePersonName(topic.proposerName)}</td><td><textarea id="department-topic-note-${topic.id}" style="min-height:72px;"></textarea></td><td><button class="action-btn" onclick="departmentTopicDecision(${topic.id}, true)">Catalog руу батлах</button><button class="outline-btn" style="margin-top:8px;" onclick="departmentTopicDecision(${topic.id}, false)">Буцаах</button></td></tr>`).join("") : `<tr><td colspan="4">Catalog руу оруулах сэдэв алга.</td></tr>`}
                </tbody>
            </table>
        </div>
        <div class="card">
            <h3>Эцэслэн батлах оюутны сэдвүүд</h3>
            <table class="table">
                <thead><tr><th>Сэдэв</th><th>Оюутан</th><th>Advisor</th><th>Тайлбар</th><th>Үйлдэл</th></tr></thead>
                <tbody>
                ${studentOwnedTopics.length ? studentOwnedTopics.map(topic => `<tr${highlightRow(topic.id)}><td>${renderNewBadge(topic.id)}${safeTopicTitle(topic)}</td><td>${safePersonName(topic.ownerStudentName)}</td><td><select id="advisor-${topic.id}">${teacherOptions}</select></td><td><textarea id="department-topic-note-${topic.id}" style="min-height:72px;"></textarea></td><td><button class="action-btn" onclick="departmentTopicDecision(${topic.id}, true)">Эцэслэн батлах</button><button class="outline-btn" style="margin-top:8px;" onclick="departmentTopicDecision(${topic.id}, false)">Буцаах</button></td></tr>`).join("") : `<tr><td colspan="5">Эцэслэх оюутны сэдэв алга.</td></tr>`}
                </tbody>
            </table>
        </div>
        <div class="card">
            <h3>Батлагдсан сэдвүүдийг засах, устгах</h3>
            <table class="table">
                <thead><tr><th>Сэдэв</th><th>Хөтөлбөр</th><th>Тайлбар</th><th>Үйлдэл</th></tr></thead>
                <tbody>
                ${approvedCatalogTopics.length ? approvedCatalogTopics.map(topic => `<tr><td><input id="department-edit-title-${topic.id}" value="${escapeHtml(safeTopicTitle(topic))}"></td><td><input id="department-edit-program-${topic.id}" value="${escapeHtml(topic.program)}"></td><td><textarea id="department-edit-description-${topic.id}" style="min-height:72px;">${escapeHtml(safeText(topic.description, ""))}</textarea></td><td><button class="action-btn" onclick="updateDepartmentTopic(${topic.id})">Засах</button><button class="outline-btn" style="margin-top:8px;" onclick="deleteTopic(${topic.id}, 'DEPARTMENT')">Устгах</button></td></tr>`).join("") : `<tr><td colspan="4">Батлагдсан сэдэв алга.</td></tr>`}
                </tbody>
            </table>
        </div>
    `;
}

function renderResearchPage(user) {
    if (state.activeRole === "STUDENT") {
        if (state.researchMode === "detail") return renderResearchDetail(user);
        return renderStudentResearchOverview(user);
    }
    if (state.activeRole === "TEACHER") return renderTeacherResearch();
    return renderDepartmentResearch();
}

function renderStudentResearchOverview(user) {
    const topic = getApprovedTopic(user);
    const plan = state.dashboard.plans.find(item => item.studentId === user.id);
    return `
        <h1>Миний судалгааны ажил</h1>
        <p class="page-sub">Сэдэв бүрэн батлагдсаны дараа 15 долоо хоногийн төлөвлөгөө энд үүсгэнэ.</p>
        ${renderMetricCards([
            ["Батлагдсан сэдэв", topic ? "1" : "0"],
            ["Төлөвлөгөө", plan ? renderStatus(plan.status) : "Үүсгээгүй"]
        ])}
        <div class="card">
            <table class="table">
                <thead><tr><th>Сэдэв</th><th>Төлөвлөгөө</th><th>Удирдагч багш</th><th>Үйлдэл</th></tr></thead>
                <tbody>
                ${topic ? `<tr><td>${safeTopicTitle(topic)}</td><td>${plan ? renderStatus(plan.status) : "Үүсгээгүй"}</td><td>${safePersonName(topic.advisorTeacherName)}</td><td><button class="action-btn" onclick="openResearchDetail()">Нээх</button></td></tr>` : `<tr><td colspan="4">Эхлээд сэдвээ бүрэн батлуулна.</td></tr>`}
                </tbody>
            </table>
        </div>
    `;
}

function renderResearchDetail(user) {
    const topic = getApprovedTopic(user);
    const plan = state.dashboard.plans.find(item => item.studentId === user.id);
    const reviews = state.dashboard.reviews.filter(review => review.planId === plan?.id);
    if (!topic) return renderStudentResearchOverview(user);

    return `
        <h1>Судалгааны ажлын дэлгэрэнгүй</h1>
        <p class="page-sub">Төлөвлөгөөг draft хадгалж, дараа нь багш руу илгээнэ.</p>
        <div class="card">
            <div class="two-col">
                <div class="info-panel">
                    <h3>Сэдэв</h3>
                    <div class="topic-title">${safeTopicTitle(topic)}</div>
                    <div class="mini">Удирдагч багш</div>
                    <div class="teacher-pill"><span class="dot"></span>${safePersonName(topic.advisorTeacherName)}</div>
                    <div class="mini">Тайлбар</div>
                    <p>${safeText(topic.description, "Тайлбар оруулаагүй.")}</p>
                </div>
                <div class="panel">
                    <h3>15 долоо хоногийн төлөвлөгөө</h3>
                    ${plan ? renderPlanBuilder(plan, topic.id) : renderEmptyPlanBuilder(topic.id)}
                </div>
            </div>
        </div>
        <div class="card">
            <h3>Approval түүх</h3>
            <table class="table">
                <thead><tr><th>Stage</th><th>Actor</th><th>Decision</th><th>Огноо</th></tr></thead>
                <tbody>
                ${renderApprovalRows(topic.approvals, plan?.approvals ?? [])}
                </tbody>
            </table>
        </div>
        <div class="card">
            <h3>Review</h3>
            <table class="table">
                <thead><tr><th>7 хоног</th><th>Багш</th><th>Оноо</th><th>Тайлбар</th></tr></thead>
                <tbody>
                ${reviews.length ? reviews.map(review => `<tr><td>${review.week}</td><td>${review.reviewerName}</td><td>${review.score}</td><td>${review.comment}</td></tr>`).join("") : `<tr><td colspan="4">Review алга.</td></tr>`}
                </tbody>
            </table>
        </div>
    `;
}

function renderTeacherResearch() {
    const pendingPlans = filterSearch(state.workflow.plans.pendingTeacherApprovalPlans);
    const approvedPlans = state.workflow.plans.approvedPlans;
    return `
        <h1>Төлөвлөгөө ба review</h1>
        <p class="page-sub">Оюутны төлөвлөгөөг баталж, батлагдсан төлөвлөгөөн дээр review бүртгэнэ.</p>
        ${renderMetricCards([
            ["Хүлээгдэж буй төлөвлөгөө", String(pendingPlans.length)],
            ["Батлагдсан төлөвлөгөө", String(approvedPlans.length)]
        ])}
        <div class="card">
            <h3>Хүлээгдэж буй төлөвлөгөө</h3>
            <table class="table">
                <thead><tr><th>Сэдэв</th><th>Оюутан</th><th>Тайлбар</th><th>Үйлдэл</th></tr></thead>
                <tbody>
                ${pendingPlans.length ? pendingPlans.map(plan => `<tr><td>${safeText(plan.topicTitle, "Сэдвийн нэр тодорхойгүй")}</td><td>${safePersonName(plan.studentName)}</td><td><textarea id="teacher-plan-note-${plan.id}" style="min-height:72px;"></textarea></td><td><button class="action-btn" onclick="teacherPlanDecision(${plan.id}, true)">Батлах</button><button class="outline-btn" style="margin-top:8px;" onclick="teacherPlanDecision(${plan.id}, false)">Буцаах</button></td></tr>`).join("") : `<tr><td colspan="4">Хүлээгдэж буй төлөвлөгөө алга.</td></tr>`}
                </tbody>
            </table>
        </div>
        <div class="card">${renderReviewDesk()}</div>
    `;
}

function renderDepartmentResearch() {
    const pendingPlans = filterSearch(state.workflow.plans.pendingDepartmentApprovalPlans);
    return `
        <h1>Тэнхимийн төлөвлөгөө баталгаажуулалт</h1>
        <p class="page-sub">Багшийн баталсан 15 долоо хоногийн төлөвлөгөөг эцэслэн батална.</p>
        <div class="card">
            <table class="table">
                <thead><tr><th>Сэдэв</th><th>Оюутан</th><th>Тайлбар</th><th>Үйлдэл</th></tr></thead>
                <tbody>
                ${pendingPlans.length ? pendingPlans.map(plan => `<tr><td>${safeText(plan.topicTitle, "Сэдвийн нэр тодорхойгүй")}</td><td>${safePersonName(plan.studentName)}</td><td><textarea id="department-plan-note-${plan.id}" style="min-height:72px;"></textarea></td><td><button class="action-btn" onclick="departmentPlanDecision(${plan.id}, true)">Батлах</button><button class="outline-btn" style="margin-top:8px;" onclick="departmentPlanDecision(${plan.id}, false)">Буцаах</button></td></tr>`).join("") : `<tr><td colspan="4">Хүлээгдэж буй төлөвлөгөө алга.</td></tr>`}
                </tbody>
            </table>
        </div>
    `;
}

function renderSettingsPage(user) {
    const notifications = state.dashboard.notifications.filter(item => item.userId === user.id).slice(0, 8);
    return `
        <h1>Тохиргоо</h1>
        <p class="page-sub">Нэвтрэх эрх, database connection болон workflow state-ийг эндээс шалгана.</p>
        <div class="split-panels">
            <div class="card">
                <h3>Хэрэглэгч</h3>
                <div class="field-list">
                    <div class="field"><label>Нэвтрэх эрх</label><input value="${state.authUser?.username ?? user.loginId ?? "-"}" disabled></div>
                    <div class="field"><label>Нэр</label><input value="${user.firstName} ${user.lastName}" disabled></div>
                    <div class="field"><label>Имэйл</label><input value="${user.email}" disabled></div>
                    <div class="field"><label>Тэнхим</label><input value="${user.departmentName}" disabled></div>
                    <div class="field"><label>Role</label><input value="${roleLabels[user.role]}" disabled></div>
                </div>
            </div>
            <div class="card">
                <h3>Workflow summary</h3>
                <table class="table">
                    <thead><tr><th>Metric</th><th>Value</th></tr></thead>
                    <tbody>
                    <tr><td>Available topics</td><td>${state.workflow.topics.availableTopics.length}</td></tr>
                    <tr><td>Pending teacher topic approvals</td><td>${state.workflow.topics.pendingTeacherApprovalTopics.length}</td></tr>
                    <tr><td>Pending department topic approvals</td><td>${state.workflow.topics.pendingDepartmentApprovalTopics.length}</td></tr>
                    <tr><td>Pending teacher plan approvals</td><td>${state.workflow.plans.pendingTeacherApprovalPlans.length}</td></tr>
                    <tr><td>Pending department plan approvals</td><td>${state.workflow.plans.pendingDepartmentApprovalPlans.length}</td></tr>
                    <tr><td>Approved plans</td><td>${state.workflow.plans.approvedPlans.length}</td></tr>
                    </tbody>
                </table>
            </div>
        </div>
        <div class="card">
            <h3>Database connection</h3>
            ${state.database ? `
                <table class="table">
                    <thead><tr><th>Property</th><th>Value</th></tr></thead>
                    <tbody>
                    <tr><td>Status</td><td class="status">${state.database.connected ? "CONNECTED" : "DISCONNECTED"}</td></tr>
                    <tr><td>Host</td><td>${state.database.host}</td></tr>
                    <tr><td>Port</td><td>${state.database.port}</td></tr>
                    <tr><td>Database</td><td>${state.database.database}</td></tr>
                    <tr><td>User</td><td>${state.database.username}</td></tr>
                    <tr><td>URL</td><td>${state.database.url}</td></tr>
                    <tr><td>Message</td><td>${state.database.message}</td></tr>
                    </tbody>
                </table>
            ` : `<p class="page-sub">Database status уншиж чадсангүй.</p>`}
        </div>
        <div class="card">
            <h3>Notifications</h3>
            <table class="table">
                <thead><tr><th>Гарчиг</th><th>Мэдээлэл</th><th>Огноо</th></tr></thead>
                <tbody>
                ${notifications.length ? notifications.map(item => `<tr><td>${item.title}</td><td>${item.message}</td><td>${formatDateShort(item.createdAt)}</td></tr>`).join("") : `<tr><td colspan="3">Notification алга.</td></tr>`}
                </tbody>
            </table>
        </div>
    `;
}

function renderPlanBuilder(plan, topicId) {
    return `
        <table class="table task-table">
            <thead><tr><th>7 хоног</th><th>Milestone</th><th>Deliverable</th><th>Focus</th></tr></thead>
            <tbody>
            ${plan.tasks.map(task => `<tr><td>${task.week}</td><td><input id="task-title-${task.week}" value="${escapeHtml(task.title)}"></td><td><input id="task-deliverable-${task.week}" value="${escapeHtml(task.deliverable)}"></td><td><input id="task-focus-${task.week}" value="${escapeHtml(task.focus)}"></td></tr>`).join("")}
            </tbody>
        </table>
        <div class="footer-actions">
            <button class="soft-btn" onclick="savePlan(${topicId})">Draft хадгалах</button>
            ${plan.status === "DRAFT" || plan.status === "REJECTED" ? `<button class="action-btn" onclick="submitPlan(${plan.id})">Багш руу илгээх</button>` : `<span class="status">${renderStatus(plan.status)}</span>`}
        </div>
    `;
}

function renderEmptyPlanBuilder(topicId) {
    const topic = getSelectedTopic() || getApprovedTopic(getActiveUser());
    const tasks = createDefaultTasks(topic?.title || "Thesis");
    return `
        <table class="table task-table">
            <thead><tr><th>7 хоног</th><th>Milestone</th><th>Deliverable</th><th>Focus</th></tr></thead>
            <tbody>
            ${tasks.map(task => `<tr><td>${task.week}</td><td><input id="task-title-${task.week}" value="${escapeHtml(task.title)}"></td><td><input id="task-deliverable-${task.week}" value="${escapeHtml(task.deliverable)}"></td><td><input id="task-focus-${task.week}" value="${escapeHtml(task.focus)}"></td></tr>`).join("")}
            </tbody>
        </table>
        <div class="footer-actions"><span></span><button class="action-btn" onclick="savePlan(${topicId})">Төлөвлөгөө хадгалах</button></div>
    `;
}

function renderReviewDesk() {
    const approvedPlans = state.workflow.plans.approvedPlans;
    const options = approvedPlans
        .map(plan => `<option value="${plan.id}">${plan.studentName} · ${plan.topicTitle}</option>`)
        .join("");
    return approvedPlans.length ? `
        <h3>Weekly review</h3>
        <div class="field-list">
            <div class="field"><label>Plan</label><select id="reviewPlanId">${options}</select></div>
            <div class="field"><label>7 хоног</label><input id="reviewWeek" type="number" min="1" max="${PLAN_WEEKS}" value="1"></div>
            <div class="field"><label>Оноо</label><input id="reviewScore" type="number" min="0" max="100" value="90"></div>
            <div class="essay"><label>Тайлбар</label><textarea id="reviewComment"></textarea></div>
            <div><button class="action-btn" onclick="submitReview()">Review бүртгэх</button></div>
        </div>
    ` : `<p class="page-sub">Review хийх батлагдсан төлөвлөгөө алга.</p>`;
}

function renderMetricCards(items) {
    return `
        <div class="split-panels">
            ${items.map(([label, value]) => `<div class="card"><div class="mini">${label}</div><div class="topic-title" style="margin-top:8px;">${value}</div></div>`).join("")}
        </div>
    `;
}

function renderApprovalRows(topicApprovals, planApprovals) {
    const rows = [
        ...topicApprovals.map(item => `<tr><td>${item.stage}</td><td>${item.actorName}</td><td>${item.approved ? "Approved" : "Rejected"}</td><td>${formatDateShort(item.decidedAt)}</td></tr>`),
        ...planApprovals.map(item => `<tr><td>${item.stage} / PLAN</td><td>${item.actorName}</td><td>${item.approved ? "Approved" : "Rejected"}</td><td>${formatDateShort(item.decidedAt)}</td></tr>`)
    ];
    return rows.length ? rows.join("") : `<tr><td colspan="4">Approval түүх алга.</td></tr>`;
}

function renderStudentIdentityFields(user) {
    return `
        <div class="field"><label>Овог нэр</label><input value="${user.firstName} ${user.lastName}" disabled></div>
        <div class="field"><label>Имэйл</label><input value="${user.email}" disabled></div>
        <div class="field"><label>Хөтөлбөр</label><input value="${user.program ?? "B.SE"}" disabled></div>
        <div class="field"><label>Тэнхим</label><input value="${user.departmentName}" disabled></div>
    `;
}

function getTeacherSupervisedStudents(teacherId) {
    return state.dashboard.topics
        .filter(topic => topic.advisorTeacherId === teacherId && topic.ownerStudentId != null && topic.status === "APPROVED")
        .map(topic => ({
            ...topic,
            planStatus: state.dashboard.plans.find(plan => plan.topicId === topic.id)?.status
        }));
}

function getActiveUser() {
    return state.dashboard.users.find(user => user.id === state.activeUserId);
}

function getApprovedTopic(user) {
    return state.dashboard.topics.find(topic => topic.ownerStudentId === user.id && topic.status === "APPROVED");
}

function getSelectedTopic() {
    return state.dashboard.topics.find(topic => topic.id === state.selectedTopicId);
}

function openTopicDetail(topicId) {
    state.selectedTopicId = topicId;
    state.topicMode = "detail";
    setActivePage("topics", false);
    render();
}

function openTopicRequest(topicId) {
    state.selectedTopicId = topicId;
    state.topicMode = "request";
    setActivePage("topics", false);
    render();
}

function openResearchDetail() {
    state.researchMode = "detail";
    setActivePage("research", false);
    render();
}

function openTeacherTopic(topicId) {
    state.selectedTopicId = topicId;
    state.topicMode = "list";
    setActivePage("topics", false);
    render();
}

function openDepartmentTopic(topicId) {
    state.selectedTopicId = topicId;
    state.topicMode = "list";
    setActivePage("topics", false);
    render();
}

function openTeacherPlan() {
    state.researchMode = "list";
    setActivePage("research", false);
    render();
}

function openDepartmentPlan() {
    state.researchMode = "list";
    setActivePage("research", false);
    render();
}

function backToTopicList() {
    state.selectedTopicId = null;
    state.topicMode = "list";
    render();
}

async function submitCustomTopic() {
    const user = getActiveUser();
    const title = document.getElementById("customTopicTitle")?.value?.trim() || "Шинэ дипломын сэдэв";
    const result = await postJson("/api/verification/topics/student-proposals", {
        studentId: user.id,
        title,
        description: document.getElementById("customTopicDescription")?.value || "Тайлбар оруулаагүй.",
        program: document.getElementById("customTopicProgram")?.value || "B.SE"
    }, () => `"${title}" сэдэв нэмэгдлээ. Одоо "Миний дэвшүүлсэн болон сонгосон pending сэдвүүд" хэсэгт харагдана.`);
    state.lastTopicId = result?.topic?.id ?? null;
    setActivePage("topics", false);
    state.topicMode = "list";
    render();
}

async function publishTeacherTopic() {
    const user = getActiveUser();
    const title = document.getElementById("teacherTopicTitle").value?.trim() || "Шинэ нээлттэй сэдэв";
    const result = await postJson("/api/verification/topics/teacher-proposals", {
        teacherId: user.id,
        title,
        description: document.getElementById("teacherTopicDescription").value || "Тайлбар оруулаагүй.",
        program: document.getElementById("teacherTopicProgram").value || "B.SE"
    }, () => `"${title}" сэдэв нэмэгдлээ. Одоо "Тэнхимийн баталгаажуулалт хүлээж буй миний сэдвүүд" хэсэгт харагдана.`);
    state.lastTopicId = result?.topic?.id ?? null;
    setActivePage("topics", false);
    render();
}

async function publishDepartmentTopic() {
    const user = getActiveUser();
    const title = document.getElementById("departmentTopicTitle").value?.trim() || "Шинэ батлагдсан сэдэв";
    const result = await postJson("/api/verification/topics/department-proposals", {
        departmentId: user.id,
        title,
        description: document.getElementById("departmentTopicDescription").value || "Тайлбар оруулаагүй.",
        program: document.getElementById("departmentTopicProgram").value || "B.SE"
    }, () => `"${title}" батлагдсан сэдэв нийтлэгдлээ.`);
    state.lastTopicId = result?.topic?.id ?? null;
    setActivePage("topics", false);
    render();
}

async function claimTopic(topicId) {
    const result = await postJson("/api/verification/topics/selections", {
        topicId,
        studentId: getActiveUser().id
    }, topic => `"${safeTopicTitle(topic)}" сэдэв сонгогдлоо. Одоо багшийн approval хүлээж байна.`);
    state.lastTopicId = result?.topic?.id ?? topicId;
    state.selectedTopicId = null;
    state.topicMode = "list";
    render();
}

async function updateStudentTopic(topicId) {
    const result = await postJson("/api/verification/topics/student-updates", {
        topicId,
        studentId: getActiveUser().id,
        title: document.getElementById("student-topic-title").value,
        description: document.getElementById("student-topic-description").value,
        program: document.getElementById("student-topic-program").value
    }, topic => `"${safeTopicTitle(topic)}" сэдэв шинэчлэгдлээ.`);
    state.lastTopicId = result?.topic?.id ?? topicId;
    render();
}

async function updateTeacherTopic(topicId) {
    const result = await postJson("/api/verification/topics/teacher-updates", {
        topicId,
        teacherId: getActiveUser().id,
        title: document.getElementById(`teacher-edit-title-${topicId}`).value,
        description: document.getElementById(`teacher-edit-description-${topicId}`).value,
        program: document.getElementById(`teacher-edit-program-${topicId}`).value
    }, topic => `"${safeTopicTitle(topic)}" сэдэв шинэчлэгдлээ.`);
    state.lastTopicId = result?.topic?.id ?? topicId;
    render();
}

async function updateDepartmentTopic(topicId) {
    const result = await postJson("/api/verification/topics/department-updates", {
        topicId,
        departmentId: getActiveUser().id,
        title: document.getElementById(`department-edit-title-${topicId}`).value,
        description: document.getElementById(`department-edit-description-${topicId}`).value,
        program: document.getElementById(`department-edit-program-${topicId}`).value
    }, topic => `"${safeTopicTitle(topic)}" батлагдсан сэдэв шинэчлэгдлээ.`);
    state.lastTopicId = result?.topic?.id ?? topicId;
    render();
}

async function deleteTopic(topicId, actorRole) {
    const result = await postJson("/api/verification/topics/deletions", {
        topicId,
        actorId: getActiveUser().id,
        actorRole
    }, () => "Сэдэв устгагдлаа.");
    state.lastTopicId = result?.topic?.id ?? null;
    if (state.selectedTopicId === topicId) {
        state.selectedTopicId = null;
        state.topicMode = "list";
    }
    render();
}

async function teacherTopicDecision(topicId, approved) {
    const result = await postJson("/api/verification/topics/teacher-approvals", {
        topicId,
        teacherId: getActiveUser().id,
        topicTitle: topicId ? safeTopicTitle(state.dashboard.topics.find(item => item.id === topicId)) : null,
        approved,
        note: document.getElementById(`teacher-topic-note-${topicId}`).value
    }, topic => approved
        ? `"${safeTopicTitle(topic)}" сэдэв тэнхимийн шат руу шилжлээ.`
        : `"${safeTopicTitle(topic)}" сэдэв буцаагдлаа.`);
    state.lastTopicId = result?.topic?.id ?? topicId;
    render();
}

async function departmentTopicDecision(topicId, approved) {
    const topic = state.dashboard.topics.find(item => item.id === topicId);
    const result = await postJson("/api/verification/topics/department-approvals", {
        topicId,
        departmentId: getActiveUser().id,
        approved,
        advisorTeacherId: topic && topic.ownerStudentId != null
            ? Number(document.getElementById(`advisor-${topicId}`).value)
            : null,
        note: document.getElementById(`department-topic-note-${topicId}`).value
    }, updatedTopic => approved
        ? (updatedTopic && updatedTopic.ownerStudentId == null
            ? `"${safeTopicTitle(updatedTopic)}" сэдэв catalog руу орлоо. Одоо оюутанд харагдана.`
            : `"${safeTopicTitle(updatedTopic)}" сэдэв эцэслэн батлагдлаа.`)
        : `"${safeTopicTitle(updatedTopic)}" сэдэв буцаагдлаа.`);
    state.lastTopicId = result?.topic?.id ?? topicId;
    render();
}

async function savePlan(topicId) {
    const result = await postJson("/api/verification/plans", {
        studentId: getActiveUser().id,
        topicId,
        tasks: collectTaskInputs()
    });
    state.lastPlanId = result?.plan?.id ?? null;
    state.researchMode = "detail";
    render();
}

async function submitPlan(planId) {
    const result = await postJson("/api/verification/plans/submit", {
        planId,
        studentId: getActiveUser().id
    });
    state.lastPlanId = result?.plan?.id ?? planId;
    render();
}

async function teacherPlanDecision(planId, approved) {
    const result = await postJson("/api/verification/plans/teacher-approvals", {
        planId,
        actorId: getActiveUser().id,
        approved,
        note: document.getElementById(`teacher-plan-note-${planId}`).value
    });
    state.lastPlanId = result?.plan?.id ?? planId;
    render();
}

async function departmentPlanDecision(planId, approved) {
    const result = await postJson("/api/verification/plans/department-approvals", {
        planId,
        actorId: getActiveUser().id,
        approved,
        note: document.getElementById(`department-plan-note-${planId}`).value
    });
    state.lastPlanId = result?.plan?.id ?? planId;
    render();
}

async function submitReview() {
    await postJson("/api/reviews", {
        planId: Number(document.getElementById("reviewPlanId").value),
        reviewerId: getActiveUser().id,
        week: Number(document.getElementById("reviewWeek").value),
        score: Number(document.getElementById("reviewScore").value),
        comment: document.getElementById("reviewComment").value
    });
}

async function postJson(url, body, successTextFactory = null) {
    try {
        const response = await fetch(url, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify(body)
        });
        if (!response.ok) {
            let data = { message: "Алдаа гарлаа." };
            try {
                data = await response.json();
            } catch (error) {
                // ignore parse failure
            }
            throw new Error(data.message || "Алдаа гарлаа.");
        }
        const payload = await response.json();
        await loadDashboard();
        const entity = payload?.topic ?? payload?.plan ?? null;
        showMessage(successTextFactory ? successTextFactory(entity) : "Үйлдэл амжилттай.", false);
        return payload;
    } catch (error) {
        showMessage(error.message, true);
        return null;
    }
}

function collectTaskInputs() {
    return Array.from({ length: PLAN_WEEKS }, (_, index) => {
        const week = index + 1;
        return {
            week,
            title: document.getElementById(`task-title-${week}`)?.value || `Week ${week}`,
            deliverable: document.getElementById(`task-deliverable-${week}`)?.value || "Deliverable",
            focus: document.getElementById(`task-focus-${week}`)?.value || "Focus"
        };
    });
}

function createDefaultTasks(topicTitle) {
    return Array.from({ length: PLAN_WEEKS }, (_, index) => ({
        week: index + 1,
        title: `${topicTitle} - Week ${index + 1}`,
        deliverable: index < 3 ? "Requirement / literature output" : index < 10 ? "Implementation increment" : "Testing / report",
        focus: index < 3 ? "Судалгаа ба framing" : index < 10 ? "Architecture, implementation" : "Testing, defense preparation"
    }));
}

function filterSearch(items) {
    const q = searchInput.value?.trim().toLowerCase();
    if (!q) return items;
    return items.filter(item => JSON.stringify(item).toLowerCase().includes(q));
}

function showMessage(text, isError) {
    message.textContent = text;
    message.className = `message show ${isError ? "error" : "success"}`;
}

function renderStatus(status) {
    const labels = {
        AVAILABLE: "Сонгож болно",
        PENDING_TEACHER_APPROVAL: "Багш хүлээж байна",
        PENDING_DEPARTMENT_APPROVAL: "Тэнхим хүлээж байна",
        APPROVED: "Батлагдсан",
        REJECTED: "Буцаагдсан",
        SUPERSEDED: "Хүчингүй болсон",
        DRAFT: "Draft"
    };
    return labels[status] ?? status;
}

function safeText(value, fallback) {
    return value === undefined || value === null || String(value).trim() === "" ? fallback : String(value);
}

function safeTopicTitle(topic) {
    return safeText(topic?.title, "Сэдвийн нэр тодорхойгүй");
}

function safePersonName(name) {
    return safeText(name, "-");
}

function renderNewBadge(entityId) {
    return state.lastTopicId === entityId || state.lastPlanId === entityId
        ? `<span style="display:inline-block;margin-right:8px;padding:2px 8px;border-radius:999px;background:#edf3ff;color:#2f57b8;font-size:11px;font-weight:700;">NEW</span>`
        : "";
}

function highlightRow(entityId) {
    return state.lastTopicId === entityId || state.lastPlanId === entityId
        ? ` style="background:#f7fbff;outline:1px solid #b7cdfd;"`
        : "";
}

function escapeHtml(value) {
    return String(value ?? "")
        .replaceAll("&", "&amp;")
        .replaceAll("<", "&lt;")
        .replaceAll(">", "&gt;")
        .replaceAll('"', "&quot;");
}

function formatDateShort(value) {
    return new Date(value).toLocaleDateString("mn-MN");
}

function planEndDate() {
    const date = new Date();
    date.setDate(date.getDate() + PLAN_WEEKS * 7);
    return date.toLocaleDateString("mn-MN");
}

function applyAuthenticatedUser(authUser) {
    state.authUser = authUser;
    state.activeUserId = authUser.id;
    state.activeRole = authUser.role.toUpperCase();
    state.page = "dashboard";
    state.topicMode = "list";
    state.researchMode = "list";
    state.selectedTopicId = null;
}

function logout() {
    sessionStorage.removeItem("thesis.authUser");
    state.dashboard = null;
    state.workflow = null;
    state.database = null;
    state.authUser = null;
    state.activeRole = null;
    state.activeUserId = null;
    appShell.classList.add("hidden");
    authRoot.classList.remove("hidden");
    renderAuthScreen("Системээс гарлаа.", false);
}

function renderAuthScreen(initialMessage = "", isError = false) {
    authRoot.innerHTML = `
        <div class="auth-card">
            <section class="auth-hero">
                <div>
                    <div class="logo-mark" style="border-color:white;color:white;">M</div>
                    <h1>Дипломын ажлыг удирдах систем</h1>
                    <p>Оюутан, багш, тэнхимийн хэрэглэгч эхлээд өөрийн нэвтрэх нэрээр бүртгүүлж, дараа нь үүсгэсэн нууц үгээрээ нэвтэрнэ.</p>
                </div>
                <div class="auth-points">
                    <div><strong>Оюутан</strong><br>СИСИ ID-аараа бүртгүүлнэ.</div>
                    <div><strong>Багш</strong><br>Ажилтны кодоороо бүртгүүлнэ.</div>
                    <div><strong>Тэнхим</strong><br>Тэнхимийн эрхээрээ бүртгүүлнэ.</div>
                </div>
            </section>
            <section class="auth-pane">
                <div>
                    <h2>Нэвтрэх</h2>
                    <p class="auth-sub">Хэрэв өмнө нь бүртгүүлээгүй бол доорх бүртгэлийн хэсгээр нэг удаа бүртгэл үүсгэнэ.</p>
                </div>
                <div id="authMessage" class="message ${initialMessage ? "show" : ""} ${isError ? "error" : "success"}">${escapeHtml(initialMessage)}</div>
                <form id="loginForm" class="auth-form">
                    <div>
                        <label class="auth-label" for="loginUsername">СИСИ ID / нэвтрэх нэр</label>
                        <input class="auth-input" id="loginUsername" placeholder="22b1num0027 эсвэл tch001" autocomplete="username">
                    </div>
                    <div>
                        <label class="auth-label" for="loginPassword">Нууц үг</label>
                        <input class="auth-input" id="loginPassword" type="password" placeholder="Нууц үгээ оруулна уу" autocomplete="current-password">
                    </div>
                    <div class="auth-actions">
                        <button class="action-btn" type="submit">Нэвтрэх</button>
                        <button class="soft-btn" type="button" id="registerToggle">Бүртгүүлэх</button>
                        <button class="link-btn" type="button" id="forgotToggle">Нууц үг сэргээх</button>
                    </div>
                </form>
                <div id="registerPanel" class="hidden">
                    <div class="auth-note">СИСИ ID, ажилтны код, эсвэл тэнхимийн нэвтрэх нэрээ ашиглаад шинэ нууц үг үүсгэнэ.</div>
                    <form id="registerForm" class="auth-form" style="margin-top:14px;">
                        <div>
                            <label class="auth-label" for="registerUsername">СИСИ ID / нэвтрэх нэр</label>
                            <input class="auth-input" id="registerUsername" placeholder="22b1num0027 эсвэл tch001" autocomplete="username">
                        </div>
                        <div>
                            <label class="auth-label" for="registerPassword">Шинэ нууц үг</label>
                            <input class="auth-input" id="registerPassword" type="password" placeholder="Хамгийн багадаа 6 тэмдэгт" autocomplete="new-password">
                        </div>
                        <div>
                            <label class="auth-label" for="registerConfirmPassword">Нууц үг давтах</label>
                            <input class="auth-input" id="registerConfirmPassword" type="password" placeholder="Нууц үгээ давтаж оруулна уу" autocomplete="new-password">
                        </div>
                        <div class="auth-actions">
                            <button class="soft-btn" type="submit">Бүртгэх</button>
                            <button class="link-btn" type="button" id="registerClose">Хаах</button>
                        </div>
                    </form>
                </div>
                <div id="forgotPanel" class="hidden">
                    <div class="auth-note">Сэргээх үед систем түр нууц үг үүсгээд буцаана.</div>
                    <form id="forgotForm" class="auth-form" style="margin-top:14px;">
                        <div>
                            <label class="auth-label" for="forgotUsername">СИСИ ID / нэвтрэх нэр</label>
                            <input class="auth-input" id="forgotUsername" placeholder="22b1num0027 эсвэл sisi-admin">
                        </div>
                        <div class="auth-actions">
                            <button class="soft-btn" type="submit">Сэргээх</button>
                            <button class="link-btn" type="button" id="forgotClose">Хаах</button>
                        </div>
                    </form>
                </div>
            </section>
        </div>
    `;

    document.getElementById("loginForm").addEventListener("submit", handleLogin);
    document.getElementById("registerToggle").addEventListener("click", () => {
        document.getElementById("registerPanel").classList.remove("hidden");
        document.getElementById("registerUsername").value = document.getElementById("loginUsername").value;
        document.getElementById("forgotPanel").classList.add("hidden");
    });
    document.getElementById("registerClose").addEventListener("click", () => {
        document.getElementById("registerPanel").classList.add("hidden");
    });
    document.getElementById("registerForm").addEventListener("submit", handleRegister);
    document.getElementById("forgotToggle").addEventListener("click", () => {
        document.getElementById("forgotPanel").classList.remove("hidden");
        document.getElementById("forgotUsername").value = document.getElementById("loginUsername").value;
        document.getElementById("registerPanel").classList.add("hidden");
    });
    document.getElementById("forgotClose").addEventListener("click", () => {
        document.getElementById("forgotPanel").classList.add("hidden");
    });
    document.getElementById("forgotForm").addEventListener("submit", handleForgotPassword);
}

async function handleLogin(event) {
    event.preventDefault();
    const username = document.getElementById("loginUsername").value.trim();
    const password = document.getElementById("loginPassword").value;

    try {
        const response = await fetch("/api/auth/login", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ username, password })
        });
        const payload = await response.json();
        if (!response.ok) {
            throw new Error(payload.message || "Нэвтрэх үед алдаа гарлаа.");
        }
        sessionStorage.setItem("thesis.authUser", JSON.stringify(payload.user));
        applyAuthenticatedUser(payload.user);
        await loadDashboard();
        showMessage(payload.message || "Амжилттай нэвтэрлээ.", false);
    } catch (error) {
        showAuthMessage(error.message || "Нэвтрэх үед алдаа гарлаа.", true);
    }
}

async function handleRegister(event) {
    event.preventDefault();
    const username = document.getElementById("registerUsername").value.trim();
    const password = document.getElementById("registerPassword").value;
    const confirmPassword = document.getElementById("registerConfirmPassword").value;

    try {
        const response = await fetch("/api/auth/register", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ username, password, confirmPassword })
        });
        const payload = await response.json();
        if (!response.ok) {
            throw new Error(payload.message || "Бүртгэх үед алдаа гарлаа.");
        }
        showAuthMessage(payload.message || "Бүртгэл амжилттай үүслээ.", false);
        document.getElementById("loginUsername").value = payload.username ?? username;
        document.getElementById("loginPassword").value = "";
        document.getElementById("registerPanel").classList.add("hidden");
        document.getElementById("loginPassword").focus();
    } catch (error) {
        showAuthMessage(error.message || "Бүртгэх үед алдаа гарлаа.", true);
    }
}

async function handleForgotPassword(event) {
    event.preventDefault();
    const username = document.getElementById("forgotUsername").value.trim();

    try {
        const response = await fetch("/api/auth/forgot-password", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ username })
        });
        const payload = await response.json();
        if (!response.ok) {
            throw new Error(payload.message || "Сэргээх үед алдаа гарлаа.");
        }
        showAuthMessage(payload.message, false);
        document.getElementById("loginUsername").value = payload.username ?? username;
        document.getElementById("loginPassword").focus();
    } catch (error) {
        showAuthMessage(error.message || "Сэргээх үед алдаа гарлаа.", true);
    }
}

function showAuthMessage(text, isError) {
    const authMessage = document.getElementById("authMessage");
    if (!authMessage) {
        return;
    }
    authMessage.textContent = text;
    authMessage.className = `message show ${isError ? "error" : "success"}`;
}
