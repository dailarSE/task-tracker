/**
 * Инициализирует все обработчики событий для модальных окон.
 * - Открытие окон по кнопкам.
 * - Закрытие по "крестику".
 * - Закрытие по клику на фон (с защитой от "протягивания" мышки).
 */
function initializeModalHandlers() {
    const $modals = $('.modal');

    // Открытие окон
    $('#showRegisterModalBtn').on('click', () => {
        window.ui.clearFormErrors(window.ui.$registerModal); // Очищаем перед показом
        window.ui.$registerModal.css('display', 'flex');
    });
    $('#showLoginModalBtn').on('click', () => {
        window.ui.clearFormErrors(window.ui.$loginModal); // Очищаем перед показом
        window.ui.$loginModal.css('display', 'flex');
    });

    // Закрытие по "крестику"
    $('.close-modal-btn').on('click', function() {
        $(this).closest('.modal').css('display', 'none');
    });

    // Логика закрытия по клику на фон
    let mousedownOnBackdrop = false;
    $modals.on('mousedown', function(event) {
        if (event.target === this) {
            mousedownOnBackdrop = true;
        }
    });

    $modals.on('mouseup', function(event) {
        if (mousedownOnBackdrop && event.target === this) {
            $(this).css('display', 'none');
        }
        mousedownOnBackdrop = false;
    });
}

/**
 * Загружает и отображает задачи для аутентифицированного пользователя.
 */
function loadAndDisplayTasks() {
    window.tasks.loadAll()
        .fail((jqXHR) => {
            console.error("Failed to load tasks:", jqXHR.responseJSON);
            const problem = jqXHR.responseJSON;
            const message = problem?.detail || 'Could not load tasks. Please try refreshing the page.';
            window.ui.showToastNotification(message, 'error');
        });
}

/**
 * Точка входа в приложение. Инициализирует все обработчики и модули.
 */
$(document).ready(function() {
    // 1. Инициализация UI-компонентов (модальные окна)
    initializeModalHandlers();

    // 2. Инициализация обработчиков логики аутентификации
    setupAuthHandlers();

    // 3. Инициализация обработчиков логики задач
    setupTaskHandlers()

    // 4. Проверка начального состояния аутентификации и обновление UI
    window.ui.checkInitialAuthState();

    console.log("Task Tracker frontend initialized.");
});